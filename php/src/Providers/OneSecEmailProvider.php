<?php

declare(strict_types=1);

namespace TempMail\Providers;

use TempMail\HttpClient;
use TempMail\Models\Message;
use TempMail\Models\MessageDetail;
use TempMail\TempMailProvider;
use TempMail\Exceptions\NotFoundException;
use TempMail\Exceptions\TempMailException;

class OneSecEmailProvider implements TempMailProvider
{
    private const BASE = 'https://www.1secemail.com';

    private const DOMAINS = [
        'qzueos.com', 'gaziw.com', 'emailgenerator.xyz',
    ];

    private HttpClient $http;
    private ?string $csrf = null;
    private ?string $currentEmail = null;

    public function __construct(?HttpClient $http = null)
    {
        $this->http = $http ?? new HttpClient();
    }

    public function generateEmail(): string
    {
        $this->ensureCSRF();
        $name = substr(str_shuffle('abcdefghijklmnopqrstuvwxyz0123456789'), 0, 10);
        $domain = self::DOMAINS[array_rand(self::DOMAINS)];
        $this->postForm('/change', ['name' => $name, 'domain' => $domain]);
        $this->currentEmail = $name . '@' . $domain;
        return $this->currentEmail;
    }

    private function ensureCSRF(): void
    {
        if ($this->csrf !== null) return;
        $resp = $this->http->get(self::BASE . '/');
        $html = $resp['body'];
        if (!preg_match('/<meta name="csrf-token" content="([^"]+)">/', $html, $m)) {
            throw new TempMailException('CSRF token not found on 1secemail page');
        }
        $this->csrf = $m[1];
    }

    private function postForm(string $path, array $data = []): array
    {
        $this->ensureCSRF();
        return $this->http->post(
            self::BASE . $path,
            ['_token' => $this->csrf, ...$data],
            ['X-CSRF-TOKEN' => $this->csrf, 'x-xsrf-token' => $this->csrf, 'Referer' => self::BASE . '/']
        );
    }

    public function getInbox(string $email): array
    {
        $result = $this->postForm('/get_messages');
        $messages = $result['body'] ?? [];
        if (!is_array($messages)) return [];

        return array_values(array_map(fn($m) => new Message(
            id: is_array($m) ? ($m['id'] ?? '') : '',
            sender: is_array($m) ? ($m['from_email'] ?? $m['from'] ?? 'unknown') : 'unknown',
            subject: is_array($m) ? ($m['subject'] ?? '(no subject)') : '(no subject)',
            date: is_array($m) && isset($m['receivedAt']) ? new \DateTimeImmutable($m['receivedAt']) : new \DateTimeImmutable(),
        ), $messages));
    }

    public function readMessage(string $email, string $messageId): MessageDetail
    {
        try {
            $this->ensureCSRF();
            $resp = $this->http->get(self::BASE . '/view/' . $messageId, [
                'X-CSRF-TOKEN' => $this->csrf,
                'Referer'      => self::BASE . '/',
            ]);
        } catch (\Throwable) {
            // ponytail: /view/{id} may return 404 if message expired or id format differs; return empty detail
            return new MessageDetail(
                id: $messageId, sender: 'unknown', subject: '(unavailable)',
                date: new \DateTimeImmutable(), bodyText: '', bodyHtml: '',
            );
        }
        $html = $resp['body'];
        $text = trim(preg_replace('/\s+/', ' ', strip_tags($html)));

        $sender  = preg_match('/From:\s*([^<\n]+)/', $html, $m) ? trim($m[1]) : 'unknown';
        $subject = preg_match('/Subject:\s*([^<\n]+)/', $html, $m) ? trim($m[1]) : '(no subject)';
        $date    = preg_match('/(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})/', $html, $m)
            ? new \DateTimeImmutable($m[1]) : new \DateTimeImmutable();

        return new MessageDetail(
            id: $messageId, sender: $sender, subject: $subject,
            date: $date, bodyText: $text, bodyHtml: $html,
        );
    }

    public function deleteEmail(string $email): bool
    {
        return true;
    }

    public function waitForEmail(string $email, int $timeout = 60, int $interval = 5): ?Message
    {
        $end = time() + $timeout;
        while (time() < $end) {
            $inbox = $this->getInbox($email);
            if (!empty($inbox)) return $inbox[0];
            sleep($interval);
        }
        return null;
    }
}
