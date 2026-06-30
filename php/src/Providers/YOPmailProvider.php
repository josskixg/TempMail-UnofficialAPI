<?php

declare(strict_types=1);

namespace TempMail\Providers;

use TempMail\HttpClient;
use TempMail\Models\Message;
use TempMail\Models\MessageDetail;
use TempMail\TempMailProvider;
use TempMail\Exceptions\TempMailException;

class YOPmailProvider implements TempMailProvider
{
    private HttpClient $http;
    private ?string $yp = null;
    private ?string $yj = null;
    private ?string $version = null;
    private ?string $currentUser = null;

    public function __construct(?HttpClient $http = null)
    {
        $this->http = $http ?? new HttpClient([
            'default_headers' => [
                'Accept' => 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                'Accept-Language' => 'en-US,en;q=0.5',
            ],
        ]);
        // cookie jar auto-enabled by HttpClient default
    }

    public function generateEmail(): string
    {
        $chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
        $username = '';
        for ($i = 0; $i < 10; $i++) {
            $username .= $chars[random_int(0, strlen($chars) - 1)];
        }

        $email = $username . '@yopmail.com';
        $this->initSession($username);

        return $email;
    }

    public function getInbox(string $email): array
    {
        $username = $this->getUsername($email);
        $this->ensureSession($username);

        $query = http_build_query([
            'login' => $username,
            'p' => 1,
            'd' => '',
            'ctrl' => '',
            'yp' => $this->yp,
            'yj' => $this->yj,
            'v' => $this->version,
            'r_c' => '',
            'id' => '',
            'ad' => 0,
        ]);

        $res = $this->http->get("https://yopmail.com/en/inbox?$query");
        $html = $res['body'];

        return $this->parseInbox($html);
    }

    public function readMessage(string $email, string $messageId): MessageDetail
    {
        $username = $this->getUsername($email);
        $this->ensureSession($username);

        $query = http_build_query([
            'b' => $username,
            'id' => $messageId,
            'yp' => $this->yp,
            'yj' => $this->yj,
            'v' => $this->version,
        ]);

        $res = $this->http->get("https://yopmail.com/en/mail?$query");
        $html = $res['body'];

        // Extract body from div#mail
        $bodyHtml = '';
        if (preg_match('/<div[^>]*id="mail"[^>]*>(.*?)<\/div>/si', $html, $matches)) {
            $bodyHtml = trim($matches[1]);
        }

        // Try to extract subject and sender from the mail page
        $subject = '';
        if (preg_match('/<span[^>]*class="ellipsis"[^>]*>(.*?)<\/span>/si', $html, $matches)) {
            $subject = html_entity_decode(trim(strip_tags($matches[1])), ENT_QUOTES, 'UTF-8');
        }

        $sender = '';
        if (preg_match('/<span[^>]*class="icdf"[^>]*>.*?<b[^>]*>(.*?)<\/b>/si', $html, $matches)) {
            $sender = html_entity_decode(trim(strip_tags($matches[1])), ENT_QUOTES, 'UTF-8');
        }

        return new MessageDetail(
            id: $messageId,
            sender: $sender,
            subject: $subject,
            date: new \DateTimeImmutable(),
            bodyText: strip_tags($bodyHtml),
            bodyHtml: $bodyHtml,
            attachments: [],
        );
    }

    public function deleteEmail(string $email): bool
    {
        // YOPmail has no delete API; mailbox auto-expires
        return true;
    }

    public function waitForEmail(string $email, int $timeout = 60, int $interval = 5): ?Message
    {
        $deadline = time() + $timeout;
        while (time() < $deadline) {
            $messages = $this->getInbox($email);
            if (!empty($messages)) {
                return $messages[0];
            }
            sleep($interval);
        }
        return null;
    }

    private function initSession(string $username): void
    {
        // Step 1: GET homepage -> extract yp token and version
        $res = $this->http->get('https://yopmail.com/en/');
        $html = $res['body'];

        $yp = $this->extractYp($html);
        $version = $this->extractVersion($html);

        // Step 2: GET ?login={username} -> extract new yp
        $res = $this->http->get("https://yopmail.com/en/?login=" . urlencode($username));
        $html = $res['body'];
        $newYp = $this->extractYp($html);
        if ($newYp !== '') {
            $yp = $newYp;
        }

        // Step 3: POST login form
        $formBody = http_build_query([
            'login' => $username,
            'id' => '',
            'yp' => $yp,
        ]);
        $this->http->request('POST', 'https://yopmail.com/en/', $formBody, [
            'Content-Type' => 'application/x-www-form-urlencoded',
        ]);

        // Step 4: GET webmail.js -> extract yj token
        $jsUrl = "https://yopmail.com/ver/{$version}/webmail.js";
        $res = $this->http->get($jsUrl);
        $js = $res['body'];
        $yj = $this->extractYj($js);

        // Step 5: Set ytime cookie via cookie jar
        $this->setYtimeCookie();

        $this->yp = $yp;
        $this->yj = $yj;
        $this->version = $version;
        $this->currentUser = $username;
    }

    private function ensureSession(string $username): void
    {
        if ($this->currentUser === $username && $this->yp !== null && $this->yj !== null) {
            return;
        }
        $this->initSession($username);
    }

    private function extractYp(string $html): string
    {
        if (preg_match('/name="yp"\s+id="yp"\s+value="([^"]+)"/', $html, $m)) {
            return $m[1];
        }
        // Fallback: look for hidden input with id=yp
        if (preg_match('/id="yp"[^>]*value="([^"]+)"/', $html, $m)) {
            return $m[1];
        }
        if (preg_match('/name="yp"[^>]*value="([^"]+)"/', $html, $m)) {
            return $m[1];
        }
        return '';
    }

    private function extractVersion(string $html): string
    {
        if (preg_match('/\/ver\/([0-9.]+)\/webmail\.js/', $html, $m)) {
            return $m[1];
        }
        return '1.0'; // fallback
    }

    private function extractYj(string $js): string
    {
        // ponytail: naive regex, yopmail may change obfuscation
        if (preg_match("/value\s*\\+?\s*'&yj=([0-9a-zA-Z]*)&v='/", $js, $m)) {
            return $m[1];
        }
        // Fallback: broader search for yj token pattern
        if (preg_match('/yj=([0-9a-zA-Z]{10,})/', $js, $m)) {
            return $m[1];
        }
        return '';
    }

    private function setYtimeCookie(): void
    {
        $cookieFile = $this->http->getCookieFile();
        if ($cookieFile === null) {
            return;
        }
        $time = gmdate('H:i');
        $expiry = time() + 86400;
        // Netscape cookie format: domain flag path secure expiry name value
        $line = ".yopmail.com\tTRUE\t/\tFALSE\t{$expiry}\tytime\t{$time}\n";
        file_put_contents($cookieFile, $line, FILE_APPEND);
    }

    /**
     * @return Message[]
     */
    private function parseInbox(string $html): array
    {
        $messages = [];
        // YOPmail inbox has divs with class "m" for each message
        // Each contains: message ID in onclick, sender, subject, date
        if (preg_match_all('/<div[^>]*class="m"[^>]*>(.*?)<\/div>\s*<\/div>/si', $html, $blocks)) {
            foreach ($blocks[1] as $i => $block) {
                $messages[] = $this->parseMessageBlock($block, $blocks[0][$i]);
            }
        }

        // Alternative parsing: look for message links with IDs
        if (empty($messages) && preg_match_all('/id="ml(\d+)"[^>]*>(.*?)<\/a>/si', $html, $matches, PREG_SET_ORDER)) {
            foreach ($matches as $match) {
                $id = $match[1];
                $block = $match[2];
                $sender = '';
                $subject = '';

                if (preg_match('/<span[^>]*class="lmf"[^>]*>(.*?)<\/span>/si', $block, $sm)) {
                    $sender = trim(strip_tags($sm[1]));
                }
                if (preg_match('/<span[^>]*class="lms"[^>]*>(.*?)<\/span>/si', $block, $sum)) {
                    $subject = trim(strip_tags($sum[1]));
                }

                $messages[] = new Message(
                    id: $id,
                    sender: html_entity_decode($sender, ENT_QUOTES, 'UTF-8') ?: 'unknown',
                    subject: html_entity_decode($subject, ENT_QUOTES, 'UTF-8') ?: '(no subject)',
                    date: new \DateTimeImmutable(),
                );
            }
        }

        return $messages;
    }

    private function parseMessageBlock(string $innerHtml, string $fullHtml): Message
    {
        $id = '';
        if (preg_match('/id="ml(\d+)"/', $fullHtml, $m)) {
            $id = $m[1];
        } elseif (preg_match('/mail\?b=[^&]+&id=(\d+)/', $fullHtml, $m)) {
            $id = $m[1];
        }

        $sender = '';
        if (preg_match('/<span[^>]*class="lmf"[^>]*>(.*?)<\/span>/si', $innerHtml, $m)) {
            $sender = trim(strip_tags($m[1]));
        }

        $subject = '';
        if (preg_match('/<span[^>]*class="lms"[^>]*>(.*?)<\/span>/si', $innerHtml, $m)) {
            $subject = trim(strip_tags($m[1]));
        }

        return new Message(
            id: $id ?: uniqid('yop_'),
            sender: html_entity_decode($sender, ENT_QUOTES, 'UTF-8') ?: 'unknown',
            subject: html_entity_decode($subject, ENT_QUOTES, 'UTF-8') ?: '(no subject)',
            date: new \DateTimeImmutable(),
        );
    }

    private function getUsername(string $email): string
    {
        $parts = explode('@', $email, 2);
        return $parts[0];
    }
}
