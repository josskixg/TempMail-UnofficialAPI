<?php

declare(strict_types=1);

namespace TempMail\Providers;

use TempMail\HttpClient;
use TempMail\Models\Message;
use TempMail\Models\MessageDetail;
use TempMail\TempMailProvider;
use TempMail\Exceptions\TempMailException;

class MailnesiaProvider implements TempMailProvider
{
    private const BASE_URL = 'https://mailnesia.com';

    private HttpClient $http;
    private ?string $username = null;
    private ?string $currentEmail = null;

    public function __construct(?HttpClient $http = null)
    {
        $this->http = $http ?? new HttpClient();
    }

    public function generateEmail(): string
    {
        $this->username = bin2hex(random_bytes(5)); // 10 chars
        $this->currentEmail = "{$this->username}@mailnesia.com";
        return $this->currentEmail;
    }

    public function getInbox(string $email): array
    {
        $username = explode('@', $email, 2)[0];
        $headers = $this->getHeadersWithIPRotation();
        $res = $this->http->get(self::BASE_URL . '/mailbox/' . urlencode($username), $headers);
        $html = is_string($res['body']) ? $res['body'] : '';

        $xpath = $this->loadXpath($html);

        $messages = [];
        foreach ($xpath->query('//tr') as $row) {
            $cells = $xpath->query('./td', $row);
            if ($cells->length < 3) {
                continue;
            }
            $sender = trim($cells->item(0)->textContent);
            $subject = trim($cells->item(1)->textContent);
            $timeStr = trim($cells->item(2)->textContent);

            // Look for a link with message ID
            $msgId = '';
            $links = $xpath->query('.//a', $row);
            if ($links->length > 0) {
                $href = $links->item(0)->getAttribute('href');
                $parts = explode('/', rtrim($href, '/'));
                $msgId = !empty($parts) ? end($parts) : '';
            }

            if ($sender !== '' || $subject !== '') {
                $messages[] = new Message(
                    id: $msgId ?: uniqid('mn_', true),
                    sender: $sender ?: 'unknown',
                    subject: $subject ?: '(no subject)',
                    date: $this->parseDate($timeStr),
                );
            }
        }
        return $messages;
    }

    public function readMessage(string $email, string $messageId): MessageDetail
    {
        $username = explode('@', $email, 2)[0];
        $url = self::BASE_URL . '/mailbox/' . urlencode($username) . '/' . urlencode($messageId);
        $headers = $this->getHeadersWithIPRotation();
        $res = $this->http->get($url, $headers);
        $html = is_string($res['body']) ? $res['body'] : '';

        $xpath = $this->loadXpath($html);

        // Find message body div
        $msgDiv = $xpath->query('//div[@id="message"]')->item(0);
        if ($msgDiv === null) {
            // Fallback: any div with class containing message/body/mail
            $msgDiv = $xpath->query('//div[contains(@class, "message") or contains(@class, "body") or contains(@class, "mail")]')->item(0);
        }

        $bodyHtml = $msgDiv !== null ? $msgDiv->ownerDocument->saveHTML($msgDiv) : $html;
        $bodyText = $msgDiv !== null ? trim($msgDiv->textContent) : strip_tags($html);

        return new MessageDetail(
            id: $messageId,
            sender: '',
            subject: '',
            date: new \DateTimeImmutable(),
            bodyText: $bodyText,
            bodyHtml: $bodyHtml,
        );
    }

    public function deleteEmail(string $email): bool
    {
        $this->username = null;
        $this->currentEmail = null;
        return true;
    }

    public function waitForEmail(string $email, int $timeout = 60, int $interval = 5): ?Message
    {
        $deadline = time() + $timeout;
        while (time() < $deadline) {
            if (!empty($inbox = $this->getInbox($email))) {
                return $inbox[0];
            }
            sleep($interval);
        }
        return null;
    }

    private function loadXpath(string $html): \DOMXPath
    {
        libxml_use_internal_errors(true);
        $doc = new \DOMDocument();
        $doc->loadHTML('<?xml encoding="UTF-8">' . $html);
        libxml_clear_errors();
        return new \DOMXPath($doc);
    }

    private function generateRandomIP(): string
    {
        return sprintf('%d.%d.%d.%d',
            random_int(1, 254),
            random_int(0, 255),
            random_int(0, 255),
            random_int(1, 254)
        );
    }

    private function getHeadersWithIPRotation(): array
    {
        $ip = $this->generateRandomIP();
        return [
            'X-Forwarded-For' => $ip,
            'X-Real-IP' => $ip,
            'CF-Connecting-IP' => $ip,
            'True-Client-IP' => $ip,
        ];
    }

    private function parseDate(?string $str): \DateTimeImmutable
    {
        if ($str !== null && $str !== '') {
            try {
                return new \DateTimeImmutable($str);
            } catch (\Throwable) {}
        }
        return new \DateTimeImmutable();
    }
}
