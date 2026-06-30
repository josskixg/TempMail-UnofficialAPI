<?php

declare(strict_types=1);

namespace TempMail\Providers;

use TempMail\HttpClient;
use TempMail\Models\Message;
use TempMail\Models\MessageDetail;
use TempMail\TempMailProvider;
use TempMail\Exceptions\NotFoundException;
use TempMail\Exceptions\TempMailException;

class DropmailProvider implements TempMailProvider
{
    private const PADDLE_OCR_URL = 'https://mamamacjdjj-padle-predict.hf.space/predict';

    private HttpClient $http;
    /** @var array<callable>|null */
    private ?array $captchaSolvers;
    private ?string $token = null;
    private ?string $sessionId = null;
    private ?string $addressId = null;
    private ?string $currentEmail = null;

    public function __construct(?HttpClient $http = null, ?array $captchaSolvers = null)
    {
        $this->http = $http ?? new HttpClient(['use_cookies' => true]);
        $this->captchaSolvers = $captchaSolvers;
    }

    /**
     * Built-in PaddleOCR solver via HuggingFace space.
     * Tries up to 3 times, returns trimmed text on success or null.
     */
    public static function paddleOcrSolver(string $imgBytes): ?string
    {
        for ($attempt = 0; $attempt < 3; $attempt++) {
            try {
                $boundary = '----TempMail' . bin2hex(random_bytes(8));
                $body = "--{$boundary}\r\n";
                $body .= "Content-Disposition: form-data; name=\"file\"; filename=\"cap.png\"\r\n";
                $body .= "Content-Type: image/png\r\n\r\n";
                $body .= $imgBytes . "\r\n";
                $body .= "--{$boundary}--\r\n";

                $ch = curl_init();
                curl_setopt_array($ch, [
                    CURLOPT_URL            => self::PADDLE_OCR_URL,
                    CURLOPT_POST           => true,
                    CURLOPT_RETURNTRANSFER => true,
                    CURLOPT_TIMEOUT        => 30,
                    CURLOPT_HTTPHEADER     => ["Content-Type: multipart/form-data; boundary={$boundary}"],
                    CURLOPT_POSTFIELDS     => $body,
                ]);
                $resp = curl_exec($ch);
                curl_close($ch);

                if ($resp === false) continue;
                $data = json_decode($resp, true);
                if (!is_array($data)) continue;
                $results = $data['results'] ?? [];
                if (!empty($results) && ($results[0]['confidence'] ?? 0) >= 0.7) {
                    $text = trim($results[0]['text'] ?? '');
                    if ($text !== '') return $text;
                }
            } catch (\Throwable $e) {
                // ignore
            }
        }
        return null;
    }

    public function generateEmail(): string
    {
        // Step 1: try 1d token via the shared HttpClient (cookies stored in its jar)
        $cookieFile = $this->http->getCookieFile();
        $res1d = $this->curlDropmail(
            'POST',
            'https://dropmail.me/api/token/generate',
            json_encode(['type' => 'af', 'lifetime' => '1d']),
            'application/json',
            $cookieFile,
        );

        if ($res1d['status'] === 200) {
            $this->token = $res1d['body']['token']
                ?? throw new TempMailException('Dropmail: missing token in response');
        } elseif ($res1d['status'] === 402) {
            // Captcha required — attempt 90d flow
            $captcha = $res1d['body']['captcha'] ?? [];
            $solved = $this->solveCaptchaAndGetToken($captcha, $cookieFile);
            if ($solved !== null) {
                $this->token = $solved;
            } else {
                // Fallback: retry 1d via the HttpClient (may get new session/IP)
                trigger_error('Dropmail: captcha solve failed, retrying with 1d token', E_USER_WARNING);
                $fallback = $this->http->post('https://dropmail.me/api/token/generate', [
                    'type' => 'af',
                    'lifetime' => '1d',
                ]);
                $this->token = $fallback['body']['token']
                    ?? throw new TempMailException('Dropmail: missing token in fallback response');
            }
        } else {
            throw new TempMailException('Dropmail: token generation failed, HTTP ' . $res1d['status']);
        }

        // Create session and get address via GraphQL
        $result = $this->graphql('mutation { introduceSession { id addresses { id address restoreKey } } }');

        $session = $result['data']['introduceSession']
            ?? throw new TempMailException('Dropmail: missing introduceSession in response');

        $this->sessionId = $session['id'];

        if (empty($session['addresses'])) {
            throw new TempMailException('Dropmail: no addresses returned');
        }

        $firstAddr = $session['addresses'][0];
        $this->addressId = $firstAddr['id'];
        $this->currentEmail = $firstAddr['address'];

        return $this->currentEmail;
    }

    /**
     * Steps 2-5 of the captcha flow.
     * All Dropmail calls share $cookieFile; PaddleOCR uses a fresh curl handle.
     *
     * @param array $captcha captcha fields from 402 response
     * @param string|null $cookieFile shared cookie file path
     * @return string|null 90d token on success, null on failure
     */
    private function solveCaptchaAndGetToken(array $captcha, ?string $cookieFile): ?string
    {
        $v     = $captcha['v']    ?? '3';
        $nonce = $captcha['nonce'] ?? '';
        $key   = $captcha['key']   ?? '';
        $sig   = $captcha['_sig']  ?? '';

        // Step 2: download captcha image — same cookie jar
        $imgUrl = sprintf(
            'https://dropmail.me/captcha/image.png?_r=0&v=%s&nonce=%s&key=%s&_sig=%s',
            urlencode($v), urlencode($nonce), urlencode($key), urlencode($sig),
        );
        $imgRes = $this->curlDropmail('GET', $imgUrl, null, null, $cookieFile, true);
        if ($imgRes['status'] !== 200 || empty($imgRes['raw'])) {
            return null;
        }
        $imgBytes = $imgRes['raw'];

        // Step 3: run solver chain
        $ocrText = null;
        $solvers = $this->captchaSolvers ?? [self::class . '::paddleOcrSolver'];
        foreach ($solvers as $solver) {
            try {
                $result = $solver($imgBytes);
                if (is_string($result) && trim($result) !== '') {
                    $ocrText = trim($result);
                    break;
                }
            } catch (\Throwable $e) { /* ignore */ }
        }
        if ($ocrText === null) return null;

        // Step 4: submit solution — same cookie jar, form-encoded
        $formBody = http_build_query([
            'response' => $ocrText,
            'v'        => $v,
            'nonce'    => $nonce,
            'key'      => $key,
            '_sig'     => $sig,
        ]);
        $solRes = $this->curlDropmail(
            'POST',
            'https://dropmail.me/captcha/solution',
            $formBody,
            'application/x-www-form-urlencoded',
            $cookieFile,
        );
        if (($solRes['body']['result'] ?? '') !== 'correct') return null;

        // Step 5: retry token generation with 90d — same cookie jar
        $tokenRes = $this->curlDropmail(
            'POST',
            'https://dropmail.me/api/token/generate',
            json_encode(['type' => 'af', 'lifetime' => '90d']),
            'application/json',
            $cookieFile,
        );
        if ($tokenRes['status'] !== 200) return null;
        return $tokenRes['body']['token'] ?? null;
    }

    /**
     * Raw curl call that shares the cookie file (Dropmail session).
     * Returns ['status' => int, 'body' => array, 'raw' => string].
     */
    private function curlDropmail(
        string $method,
        string $url,
        ?string $body,
        ?string $contentType,
        ?string $cookieFile,
        bool $rawResponse = false,
    ): array {
        $ch = curl_init();
        $opts = [
            CURLOPT_URL            => $url,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT        => 30,
            CURLOPT_FOLLOWLOCATION => true,
            CURLOPT_SSL_VERIFYPEER => true,
        ];
        if ($cookieFile !== null) {
            $opts[CURLOPT_COOKIEJAR]  = $cookieFile;
            $opts[CURLOPT_COOKIEFILE] = $cookieFile;
        }
        $headers = [];
        if ($contentType !== null) {
            $headers[] = 'Content-Type: ' . $contentType;
        }
        if ($headers) {
            $opts[CURLOPT_HTTPHEADER] = $headers;
        }
        if ($method === 'POST') {
            $opts[CURLOPT_POST]       = true;
            $opts[CURLOPT_POSTFIELDS] = $body ?? '';
        }
        curl_setopt_array($ch, $opts);
        $response = curl_exec($ch);
        $status   = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        if ($response === false) {
            return ['status' => 0, 'body' => [], 'raw' => ''];
        }
        if ($rawResponse) {
            return ['status' => $status, 'body' => [], 'raw' => $response];
        }
        $decoded = json_decode($response, true);
        return ['status' => $status, 'body' => is_array($decoded) ? $decoded : [], 'raw' => $response];
    }

    public function getInbox(string $email): array
    {
        $this->ensureSession();

        $query = <<<GQL
        query {
            session(id: "{$this->sessionId}") {
                mails {
                    id
                    fromAddr
                    headerSubject
                    receivedAt
                    text
                    html
                    attachments {
                        id
                        name
                        mime
                        rawSize
                    }
                }
            }
        }
        GQL;

        $result = $this->graphql($query);
        $mails  = $result['data']['session']['mails'] ?? [];

        return array_map(static function (array $mail): Message {
            return new Message(
                id: $mail['id'],
                sender: $mail['fromAddr'] ?? '',
                subject: $mail['headerSubject'] ?? '',
                date: new \DateTimeImmutable($mail['receivedAt'] ?? 'now'),
            );
        }, $mails);
    }

    public function readMessage(string $email, string $messageId): MessageDetail
    {
        $this->ensureSession();

        $query = <<<GQL
        query {
            session(id: "{$this->sessionId}") {
                mails {
                    id
                    fromAddr
                    headerSubject
                    receivedAt
                    text
                    html
                    attachments {
                        id
                        name
                        mime
                        rawSize
                    }
                }
            }
        }
        GQL;

        $result = $this->graphql($query);
        $mails  = $result['data']['session']['mails'] ?? [];

        foreach ($mails as $mail) {
            if ($mail['id'] === $messageId) {
                return new MessageDetail(
                    id: $mail['id'],
                    sender: $mail['fromAddr'] ?? '',
                    subject: $mail['headerSubject'] ?? '',
                    date: new \DateTimeImmutable($mail['receivedAt'] ?? 'now'),
                    bodyText: $mail['text'] ?? '',
                    bodyHtml: $mail['html'] ?? '',
                    attachments: array_map(static fn($a) => [
                        'filename'     => $a['name'] ?? '',
                        'content_type' => $a['mime'] ?? '',
                        'size'         => $a['rawSize'] ?? 0,
                    ], $mail['attachments'] ?? []),
                );
            }
        }

        throw new NotFoundException("Message {$messageId} not found");
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

    public function deleteEmail(string $email): bool
    {
        if ($this->addressId === null) return true;
        $this->ensureSession();
        try {
            $this->graphql("mutation { deleteAddress(input: { addressId: \"{$this->addressId}\" }) { id } }");
        } catch (\Throwable) { /* best-effort */ }
        $this->token = null;
        $this->sessionId = null;
        $this->addressId = null;
        $this->currentEmail = null;
        return true;
    }

    private function graphql(string $query): array
    {
        $this->ensureToken();

        $url = 'https://dropmail.me/api/graphql/' . urlencode($this->token);
        $res = $this->http->post($url, ['query' => $query]);

        if (isset($res['body']['errors']) && !empty($res['body']['errors'])) {
            $msg = $res['body']['errors'][0]['message'] ?? 'Unknown GraphQL error';
            throw new TempMailException("Dropmail GraphQL: $msg");
        }

        return $res['body'];
    }

    private function ensureToken(): void
    {
        if ($this->token === null) {
            throw new \RuntimeException('Not authenticated. Call generateEmail() first.');
        }
    }

    private function ensureSession(): void
    {
        $this->ensureToken();
        if ($this->sessionId === null) {
            throw new \RuntimeException('No session. Call generateEmail() first.');
        }
    }
}
