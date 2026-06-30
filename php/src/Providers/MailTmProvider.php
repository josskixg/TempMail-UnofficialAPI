<?php

declare(strict_types=1);

namespace TempMail\Providers;

use TempMail\HttpClient;
use TempMail\Models\Message;
use TempMail\Models\MessageDetail;
use TempMail\TempMailProvider;

class MailTmProvider implements TempMailProvider
{
    private const BASE_URL = 'https://api.mail.tm';

    private HttpClient $http;
    private ?string $token = null;
    private ?string $currentEmail = null;

    public function __construct(?HttpClient $http = null)
    {
        $this->http = $http ?? new HttpClient();
    }

    public function generateEmail(): string
    {
        // HttpClient handles anti-429 retries internally
        return $this->generateEmailCore();
    }

    private function generateEmailCore(): string
    {
        $domainsRes = $this->http->get(self::BASE_URL . '/domains');
        $domain = $domainsRes['body']['hydra:member'][0]['domain'];

        $username = bin2hex(random_bytes(8));
        $email = "$username@$domain";
        $password = bin2hex(random_bytes(12));

        $this->http->post(self::BASE_URL . '/accounts', [
            'address' => $email,
            'password' => $password,
        ]);

        $authRes = $this->http->post(self::BASE_URL . '/token', [
            'address' => $email,
            'password' => $password,
        ]);

        $this->token = $authRes['body']['token'];
        $this->currentEmail = $email;
        $this->http->setDefaultHeader('Authorization', "Bearer {$this->token}");

        return $email;
    }

    public function getInbox(string $email): array
    {
        $res = $this->http->get(self::BASE_URL . '/messages');

        return array_map(fn(array $m) => new Message(
            id: $m['id'],
            sender: $m['from']['address'] ?? 'unknown',
            subject: $m['subject'],
            date: new \DateTimeImmutable($m['createdAt']),
        ), $res['body']['hydra:member'] ?? []);
    }

    public function readMessage(string $email, string $messageId): MessageDetail
    {
        $res = $this->http->get(self::BASE_URL . "/messages/$messageId");
        $m = $res['body'];

        $attachments = array_map(fn(array $a) => [
            'name' => $a['filename'] ?? 'unknown',
            'type' => $a['contentType'] ?? 'application/octet-stream',
            'size' => $a['size'] ?? 0,
            'url' => self::BASE_URL . "/sources/{$m['id']}",
        ], $m['attachments'] ?? []);

        return new MessageDetail(
            id: $m['id'],
            sender: $m['from']['address'] ?? 'unknown',
            subject: $m['subject'],
            date: new \DateTimeImmutable($m['createdAt']),
            bodyText: $m['text'] ?? '',
            bodyHtml: $m['html'][0] ?? '',
            attachments: $attachments,
        );
    }

    public function deleteEmail(string $email): bool
    {
        try {
            $this->http->delete(self::BASE_URL . '/accounts/me');
        } catch (\Throwable) {
            // Best-effort: 404 means already gone, treat as success
        }
        $this->token = null;
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
}
