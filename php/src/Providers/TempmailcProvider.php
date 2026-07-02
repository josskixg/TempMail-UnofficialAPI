<?php

declare(strict_types=1);

namespace TempMail\Providers;

use TempMail\HttpClient;
use TempMail\Models\Message;
use TempMail\Models\MessageDetail;
use TempMail\TempMailProvider;
use TempMail\Exceptions\NotFoundException;
use TempMail\Exceptions\TempMailException;

class TempmailcProvider implements TempMailProvider
{
    private const BASE_URL = 'https://tempmailc.com/api/v1';

    private HttpClient $http;
    private ?string $currentEmail = null;

    public function __construct(?HttpClient $http = null)
    {
        $this->http = $http ?? new HttpClient();
    }

    public function generateEmail(): string
    {
        $res = $this->http->get(self::BASE_URL . '/new');
        $data = $res['body'];
        if (!is_array($data) || !($data['ok'] ?? false)) {
            throw new TempMailException('tempmailc: API returned not ok');
        }
        $this->currentEmail = $data['email'] ?? null;
        if ($this->currentEmail === null) {
            throw new TempMailException('tempmailc: no email in response');
        }
        return $this->currentEmail;
    }

    public function getInbox(string $email): array
    {
        $res = $this->http->get(self::BASE_URL . '/inbox?' . http_build_query(['email' => $email]));
        $data = $res['body'];
        $messages = [];
        foreach ($data['messages'] ?? [] as $item) {
            $messages[] = new Message(
                id: (string)($item['id'] ?? $item['msg_id'] ?? ''),
                sender: $item['from'] ?? $item['from_mail'] ?? '',
                subject: $item['subject'] ?? '',
                date: $this->parseDate($item['date'] ?? $item['time'] ?? null),
            );
        }
        return $messages;
    }

    public function readMessage(string $email, string $messageId): MessageDetail
    {
        $res = $this->http->get(self::BASE_URL . '/message?' . http_build_query([
            'msg_id' => $messageId,
            'email' => $email,
        ]));
        $data = $res['body'];
        if (!is_array($data)) {
            throw new NotFoundException("tempmailc: message {$messageId} not found");
        }
        return new MessageDetail(
            id: (string)($data['id'] ?? $messageId),
            sender: $data['from'] ?? $data['from_mail'] ?? '',
            subject: $data['subject'] ?? '',
            date: $this->parseDate($data['date'] ?? $data['time'] ?? null),
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
