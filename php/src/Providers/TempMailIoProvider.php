<?php

declare(strict_types=1);

namespace TempMail\Providers;

use TempMail\HttpClient;
use TempMail\Models\Message;
use TempMail\Models\MessageDetail;
use TempMail\TempMailProvider;
use TempMail\Exceptions\NotFoundException;
use TempMail\Exceptions\TempMailException;

class TempMailIoProvider implements TempMailProvider
{
    private const BASE_URL = 'https://api.internal.temp-mail.io/api/v3';

    private HttpClient $http;
    private ?string $token = null;
    private ?string $currentEmail = null;

    public function __construct(?HttpClient $http = null)
    {
        $this->http = $http ?? new HttpClient();
    }

    /** @return array<string, string> */
    private function authHeaders(): array
    {
        $h = ['Content-Type' => 'application/json'];
        if ($this->token !== null) {
            $h['Authorization'] = "Bearer {$this->token}";
        }
        return $h;
    }

    /** Extract message list from response (list or wrapped object). */
    private function extractMessages(mixed $data): array
    {
        if (!is_array($data)) {
            return [];
        }
        return array_is_list($data) ? $data : ($data['messages'] ?? $data['mails'] ?? []);
    }

    public function generateEmail(): string
    {
        $res = $this->http->post(
            self::BASE_URL . '/email/new',
            ['min_name_length' => 6, 'max_name_length' => 12],
            $this->authHeaders(),
        );
        $data = $res['body'];
        if (!is_array($data)) {
            throw new TempMailException('temp-mail.io: unexpected response');
        }
        $this->currentEmail = $data['email'] ?? null;
        $this->token = $data['token'] ?? null;
        if ($this->currentEmail === null) {
            throw new TempMailException('temp-mail.io: missing email in response');
        }
        return $this->currentEmail;
    }

    public function getInbox(string $email): array
    {
        $res = $this->http->get(
            self::BASE_URL . '/email/' . $email . '/messages',
            $this->authHeaders(),
        );
        $messages = [];
        foreach ($this->extractMessages($res['body']) as $item) {
            $sender = $item['from'] ?? '';
            if (is_array($sender)) {
                $sender = $sender['address'] ?? $sender['name'] ?? '';
            }
            $messages[] = new Message(
                id: (string)($item['id'] ?? $item['uid'] ?? ''),
                sender: $sender,
                subject: $item['subject'] ?? '',
                date: $this->parseDate($item['created_at'] ?? $item['date'] ?? null),
            );
        }
        return $messages;
    }

    public function readMessage(string $email, string $messageId): MessageDetail
    {
        $res = $this->http->get(
            self::BASE_URL . '/email/' . $email . '/messages',
            $this->authHeaders(),
        );
        foreach ($this->extractMessages($res['body']) as $item) {
            $id = (string)($item['id'] ?? $item['uid'] ?? '');
            if ($id === $messageId) {
                $sender = $item['from'] ?? '';
                if (is_array($sender)) {
                    $sender = $sender['address'] ?? $sender['name'] ?? '';
                }
                $attachments = [];
                foreach ($item['attachments'] ?? [] as $a) {
                    $attachments[] = [
                        'name' => $a['filename'] ?? '',
                        'type' => $a['content_type'] ?? '',
                        'size' => $a['size'] ?? 0,
                    ];
                }
                return new MessageDetail(
                    id: $messageId,
                    sender: $sender,
                    subject: $item['subject'] ?? '',
                    date: $this->parseDate($item['created_at'] ?? $item['date'] ?? null),
                    bodyText: $item['body_text'] ?? $item['text'] ?? '',
                    bodyHtml: $item['body_html'] ?? $item['html'] ?? '',
                    attachments: $attachments,
                );
            }
        }
        throw new NotFoundException("temp-mail.io: message {$messageId} not found");
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
