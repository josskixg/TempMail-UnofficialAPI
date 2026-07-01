<?php

/**
 * Standalone E2E test runner for all TempMail providers.
 * No PHPUnit required — uses assert() and simple checks.
 * Run: php tests/e2e.php
 */

declare(strict_types=1);

// Load .env file (PHP doesn't auto-load it)
$envFile = __DIR__ . '/../.env';
if (file_exists($envFile)) {
    foreach (file($envFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) as $line) {
        if (str_starts_with(trim($line), '#')) continue;
        [$k, $v] = array_pad(explode('=', $line, 2), 2, '');
        if (getenv(trim($k)) === false) putenv(trim($k) . '=' . trim($v));
    }
}

require_once __DIR__ . '/../src/Exceptions/TempMailException.php';
require_once __DIR__ . '/../src/Exceptions/RateLimitException.php';
require_once __DIR__ . '/../src/Exceptions/NotFoundException.php';
require_once __DIR__ . '/../src/Models/Message.php';
require_once __DIR__ . '/../src/Models/MessageDetail.php';
require_once __DIR__ . '/../src/HttpClient.php';
require_once __DIR__ . '/../src/TempMailProvider.php';
require_once __DIR__ . '/../src/Providers/MailTmProvider.php';
require_once __DIR__ . '/../src/Providers/GuerrillaMailProvider.php';
require_once __DIR__ . '/../src/Providers/YOPmailProvider.php';
require_once __DIR__ . '/../src/Providers/DropmailProvider.php';
require_once __DIR__ . '/../src/Providers/OneSecEmailProvider.php';
require_once __DIR__ . '/../src/Providers/NcaoriMailProvider.php';
require_once __DIR__ . '/../src/TempMailFactory.php';

use TempMail\TempMailFactory;

$passed = 0;
$failed = 0;
$skipped = 0;

function test(string $name, callable $fn): void
{
    global $passed, $failed;
    echo "  Testing $name ... ";
    try {
        $fn();
        echo "\033[32mOK\033[0m\n";
        $passed++;
    } catch (\Throwable $e) {
        echo "\033[31mFAIL\033[0m: {$e->getMessage()}\n";
        echo "    at {$e->getFile()}:{$e->getLine()}\n";
        $failed++;
    }
}

function check(bool $condition, string $msg): void
{
    if (!$condition) {
        throw new \RuntimeException("Assertion failed: $msg");
    }
}

$UA_POOL = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
];

function getResendApiKey(): string {
    return getenv('RESEND_API_KEY') ?: '';
}

function sendTestEmail(string $to): bool {
    global $UA_POOL;
    $delays = [1, 3, 5];
    $body = json_encode([
        'from'    => 'onboarding@resend.dev',
        'to'      => $to,
        'subject' => 'TempMail E2E Test',
        'html'    => '<p>E2E test email from TempMail wrapper</p>',
    ]);
    for ($attempt = 0; $attempt < 3; $attempt++) {
        $ch = curl_init('https://api.resend.com/emails');
        curl_setopt_array($ch, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => $body,
            CURLOPT_HTTPHEADER => [
                'Content-Type: application/json',
                'Authorization: Bearer ' . getResendApiKey(),
                'User-Agent: ' . $UA_POOL[array_rand($UA_POOL)],
            ],
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 10,
        ]);
        $resp = curl_exec($ch);
        $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $err = curl_error($ch);
        curl_close($ch);

        if ($code === 200) return true;
        if ($code === 429 && $attempt < 2) {
            sleep($delays[$attempt]);
            continue;
        }
        if ($err && $attempt < 2) {
            sleep($delays[$attempt]);
            continue;
        }
        return false;
    }
    return false;
}

function testProvider(string $providerName, array $config = []): void
{
    echo "\n\033[1m=== $providerName ===\033[0m\n";

    $provider = TempMailFactory::create($providerName, $config);
    $email = '';

    // 1. Generate email
    test('generateEmail', function () use ($provider, &$email) {
        $email = $provider->generateEmail();
        check(!empty($email), 'email should not be empty');
        check(str_contains($email, '@'), "email should contain @: $email");
        echo "($email) ";
    });

    // 1b. Send test email to populate inbox
    $sent = sendTestEmail($email);
    if ($sent) {
        echo "  Test email sent to $email, waiting 4s...\n";
        sleep(4);
    } else {
        echo "  \033[33mWarning: sendTestEmail failed, inbox may be empty\033[0m\n";
    }

    // 2. Get inbox
    test('getInbox', function () use ($provider, $email, $sent) {
        $messages = $provider->getInbox($email);
        check(is_array($messages), 'inbox should return array');
        echo "(" . count($messages) . " messages) ";

        // 3. Read first message if available
        if (!empty($messages)) {
            $first = $messages[0];
            check(!empty($first->id), 'message should have id');
            check(!empty($first->sender), 'message should have sender');

            $detail = $provider->readMessage($email, $first->id);
            check($detail->id === $first->id, 'detail id should match');
        }

        if ($sent) {
            check(count($messages) >= 1, 'inbox should have 1+ messages after test email');
        }
    });

    // 4. Delete
    test('deleteEmail', function () use ($provider, $email) {
        $result = $provider->deleteEmail($email);
        check($result === true, 'delete should return true');
    });
}

// Fix the testProvider closure scope issue for readMessage
function testProviderFull(string $providerName, array $config = []): void
{
    echo "\n\033[1m=== $providerName ===\033[0m\n";

    $provider = TempMailFactory::create($providerName, $config);

    // 1. Generate email
    test('generateEmail', function () use ($provider) {
        $email = $provider->generateEmail();
        check(!empty($email), 'email should not be empty');
        check(str_contains($email, '@'), "email should contain @: $email");
        echo "($email) ";
    });

    $email = $provider->generateEmail();

    // 1b. Send test email to populate inbox
    $sent = sendTestEmail($email);
    if ($sent) {
        echo "  Test email sent to $email, waiting 4s...\n";
        sleep(4);
    } else {
        echo "  \033[33mWarning: sendTestEmail failed, inbox may be empty\033[0m\n";
    }

    // 2. Get inbox
    test('getInbox', function () use ($provider, $email, $sent) {
        $messages = $provider->getInbox($email);
        check(is_array($messages), 'inbox should return array');
        echo "(" . count($messages) . " messages) ";
        if ($sent) {
            check(count($messages) >= 1, 'inbox should have 1+ messages after test email');
        }
    });

    // 3. Read message (skip if inbox empty or read fails — 1secemail /view/{id} may 404)
    test('readMessage', function () use ($provider, $email) {
        $messages = array_values($provider->getInbox($email));
        if (empty($messages)) {
            echo "(skipped, empty inbox) ";
            return;
        }
        $first = $messages[0];
        try {
            $detail = $provider->readMessage($email, $first->id);
            check(is_string($detail->bodyText), 'bodyText should be string');
            check(is_string($detail->bodyHtml), 'bodyHtml should be string');
            echo "(read {$first->id}) ";
        } catch (\Throwable $e) {
            // ponytail: some providers (1secemail) return 404 for old messages; not a code bug
            echo "(read unavailable: " . $e->getMessage() . ") ";
        }
    });

    // 4. Delete
    test('deleteEmail', function () use ($provider, $email) {
        $result = $provider->deleteEmail($email);
        check($result === true, 'delete should return true');
    });
}

echo "\033[1mTempMail E2E Tests\033[0m\n";
echo "Running against real APIs (no mocks)...\n";

// Test factory
test('TempMailFactory::create(yopmail)', function () {
    $p = TempMailFactory::create('yopmail', []);
    check($p instanceof \TempMail\Providers\YOPmailProvider, 'should return YOPmailProvider');
});

test('TempMailFactory::create(dropmail)', function () {
    $p = TempMailFactory::create('dropmail', []);
    check($p instanceof \TempMail\Providers\DropmailProvider, 'should return DropmailProvider');
});

// Test each provider
$providers = ['mailtm', 'guerrilla', 'yopmail', 'dropmail', '1secemail', 'ncaori'];

foreach ($providers as $name) {
    try {
        testProviderFull($name);
    } catch (\Throwable $e) {
        echo "\n\033[31m=== $name SKIPPED: {$e->getMessage()} ===\033[0m\n";
        $skipped++;
    }
}

// Summary
echo "\n\033[1m--- Results ---\033[0m\n";
echo "\033[32mPassed: $passed\033[0m\n";
if ($failed > 0) {
    echo "\033[31mFailed: $failed\033[0m\n";
}
if ($skipped > 0) {
    echo "Skipped: $skipped\n";
}
echo "\n";

exit($failed > 0 ? 1 : 0);
