<?php

declare(strict_types=1);

namespace TempMail\Providers;

use TempMail\HttpClient;
use TempMail\Models\Message;
use TempMail\Models\MessageDetail;
use TempMail\TempMailProvider;
use TempMail\Exceptions\NotFoundException;
use TempMail\Exceptions\TempMailException;

class TempmailPlusProvider implements TempMailProvider
{
    private const BASE_URL = 'https://tempmail.plus';
    private const DOMAIN = 'mailto.plus';

    private HttpClient $http;
    private ?string $currentEmail = null;

    public function __construct(?HttpClient $http = null)
    {
        $this->http = $http ?? new HttpClient();
    }

    public function generateEmail(): string
    {
        $username = bin2hex(random_bytes(5)); // 10 chars
        $this->currentEmail = "{$username}@" . self::DOMAIN;
        return $this->currentEmail;
    }

    public function getInbox(string $email): array
    {
        $res = $this->http->get(self::BASE_URL . '/api/mails?' . http_build_query(['email' => $email]));
        $data = $res['body'];
        if (is_array($data) && ($data['result'] ?? true) === false) {
            throw new TempMailException('tempmail.plus: API returned error');
        }
        $messages = [];
        foreach ($data['mail_list'] ?? [] as $item) {
            $messages[] = new Message(
                id: (string)($item['mail_id'] ?? ''),
                sender: $item['from_mail'] ?? '',
                subject: $item['subject'] ?? '',
                date: $this->parseDate($item['time'] ?? null),
            );
        }
        return $messages;
    }

    public function readMessage(string $email, string $messageId): MessageDetail
    {
        $res = $this->http->get(self::BASE_URL . "/api/mails/{$messageId}?" . http_build_query(['email' => $email]));
        $data = $res['body'];
        if (!is_array($data)) {
            throw new NotFoundException("tempmail.plus: message {$messageId} not found");
        }
        $attachments = [];
        foreach ($data['attachments'] ?? [] as $a) {
            $attachments[] = [
                'name' => $a['filename'] ?? '',
                'type' => $a['content_type'] ?? '',
                'size' => $a['size'] ?? 0,
            ];
        }
        return new MessageDetail(
            id: (string)($data['mail_id'] ?? $messageId),
            sender: $data['from_mail'] ?? $data['from'] ?? '',
            subject: $data['subject'] ?? '',
            date: $this->parseDate($data['date'] ?? null),
            bodyText: $data['text'] ?? '',
            bodyHtml: $data['html'] ?? '',
            attachments: $attachments,
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
