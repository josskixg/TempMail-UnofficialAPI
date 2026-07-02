<?php

declare(strict_types=1);

namespace TempMail\Providers;

use TempMail\HttpClient;
use TempMail\Models\Message;
use TempMail\Models\MessageDetail;
use TempMail\TempMailProvider;
use TempMail\Exceptions\NotFoundException;
use TempMail\Exceptions\TempMailException;

class TempmailLolProvider implements TempMailProvider
{
    private const BASE_URL = 'https://api.tempmail.lol/v2';

    private HttpClient $http;
    private ?string $token = null;
    private ?string $currentEmail = null;

    public function __construct(?HttpClient $http = null)
    {
        $this->http = $http ?? new HttpClient();
    }

    public function generateEmail(): string
    {
        $res = $this->http->post(self::BASE_URL . '/inbox/create', null, ['Content-Type' => 'application/json']);
        $data = $res['body'];
        if (!is_array($data)) {
            throw new TempMailException('tempmail.lol: unexpected response');
        }
        $this->currentEmail = $data['address'] ?? null;
        $this->token = $data['token'] ?? null;
        if ($this->currentEmail === null || $this->token === null) {
            throw new TempMailException('tempmail.lol: missing address or token');
        }
        return $this->currentEmail;
    }

    public function getInbox(string $email): array
    {
        if ($this->token === null) {
            throw new TempMailException('tempmail.lol: no token — call generateEmail() first');
        }
        $res = $this->http->get(self::BASE_URL . '/inbox?' . http_build_query(['token' => $this->token]));
        $data = $res['body'];
        if (!is_array($data)) {
            throw new TempMailException('tempmail.lol: unexpected response');
        }
        if ($data['expired'] ?? false) {
            throw new TempMailException('tempmail.lol: token expired');
        }
        $messages = [];
        foreach ($data['emails'] ?? [] as $item) {
            $messages[] = new Message(
                id: (string)($item['_id'] ?? $item['id'] ?? $item['uid'] ?? ''),
                sender: $item['from'] ?? $item['sender'] ?? '',
                subject: $item['subject'] ?? '',
                date: $this->parseDate($item['date'] ?? $item['createdAt'] ?? null),
            );
        }
        return $messages;
    }

    public function readMessage(string $email, string $messageId): MessageDetail
    {
        if ($this->token === null) {
            throw new TempMailException('tempmail.lol: no token — call generateEmail() first');
        }
        $res = $this->http->get(self::BASE_URL . '/inbox?' . http_build_query(['token' => $this->token]));
        $data = $res['body'];
        foreach ($data['emails'] ?? [] as $item) {
            $id = (string)($item['_id'] ?? $item['id'] ?? $item['uid'] ?? '');
            if ($id === $messageId) {
                return new MessageDetail(
                    id: $messageId,
                    sender: $item['from'] ?? $item['sender'] ?? '',
                    subject: $item['subject'] ?? '',
                    date: $this->parseDate($item['date'] ?? $item['createdAt'] ?? null),
                    bodyText: $item['body'] ?? $item['text'] ?? '',
                    bodyHtml: $item['html'] ?? '',
                );
            }
        }
        throw new NotFoundException("tempmail.lol: message {$messageId} not found");
    }

    public function deleteEmail(string $email): bool
    {
        $this->token = null;
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
