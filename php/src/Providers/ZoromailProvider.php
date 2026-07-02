<?php

declare(strict_types=1);

namespace TempMail\Providers;

use TempMail\HttpClient;
use TempMail\Models\Message;
use TempMail\Models\MessageDetail;
use TempMail\TempMailProvider;
use TempMail\Exceptions\TempMailException;

class ZoromailProvider implements TempMailProvider
{
    private const BASE_URL = 'https://zoromail.com/public_api.php/v1';

    private HttpClient $http;
    private ?string $currentEmail = null;

    public function __construct(?HttpClient $http = null)
    {
        $this->http = $http ?? new HttpClient();
    }

    /** Call zoromail API; checks {success, data, error} envelope. */
    private function api(string $method, string $path, ?array $body = null): mixed
    {
        $res = $method === 'GET'
            ? $this->http->get(self::BASE_URL . $path)
            : $this->http->post(self::BASE_URL . $path, $body ?? []);

        $data = $res['body'];
        if (!is_array($data) || !($data['success'] ?? false)) {
            throw new TempMailException('zoromail: ' . (is_array($data) ? ($data['error'] ?? 'unknown error') : 'unexpected response'));
        }
        return $data['data'] ?? null;
    }

    public function generateEmail(): string
    {
        $domains = $this->api('GET', '/domains');
        if (!is_array($domains) || empty($domains)) {
            throw new TempMailException('zoromail: no domains available');
        }
        $domain = $domains[array_rand($domains)];
        $username = bin2hex(random_bytes(5)); // 10 chars
        $data = $this->api('POST', '/emails', ['username' => $username, 'domain' => $domain]);
        if (!is_array($data) || !isset($data['email'])) {
            throw new TempMailException('zoromail: no email in response');
        }
        $this->currentEmail = $data['email'];
        return $this->currentEmail;
    }

    public function getInbox(string $email): array
    {
        $data = $this->api('GET', '/emails/' . urlencode($email) . '/messages');
        $messages = [];
        foreach ($data ?? [] as $item) {
            $messages[] = new Message(
                id: (string)($item['id'] ?? ''),
                sender: $item['from'] ?? '',
                subject: $item['subject'] ?? '',
                date: $this->parseDate($item['date'] ?? null),
            );
        }
        return $messages;
    }

    public function readMessage(string $email, string $messageId): MessageDetail
    {
        $data = $this->api('GET', '/messages/' . urlencode($messageId));
        if (!is_array($data)) {
            throw new TempMailException('zoromail: invalid message response');
        }
        return new MessageDetail(
            id: (string)($data['id'] ?? $messageId),
            sender: $data['from'] ?? '',
            subject: $data['subject'] ?? '',
            date: $this->parseDate($data['date'] ?? null),
            bodyText: $data['text'] ?? $data['body_text'] ?? '',
            bodyHtml: $data['html'] ?? $data['body_html'] ?? '',
        );
    }

    public function deleteEmail(string $email): bool
    {
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
