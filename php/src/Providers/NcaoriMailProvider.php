<?php

declare(strict_types=1);

namespace TempMail\Providers;

use TempMail\HttpClient;
use TempMail\Models\Message;
use TempMail\Models\MessageDetail;
use TempMail\TempMailProvider;
use TempMail\Exceptions\TempMailException;

class NcaoriMailProvider implements TempMailProvider
{
    private const BASE = 'https://www.nca.my.id';
    private const DOMAINS = ['ncaori.my.id', 'nca.my.id'];

    private const WORDS = [
        'swift', 'crystal', 'storm', 'frost', 'shadow', 'ember', 'azure',
        'phantom', 'silver', 'iron', 'crimson', 'golden', 'neo', 'cosmic', 'lunar',
        'solar', 'dark', 'light', 'void', 'flux',
    ];

    private const WORDS2 = [
        'core', 'leaf', 'forge', 'wave', 'peak', 'gate', 'pulse',
        'blade', 'shard', 'drift', 'hive', 'node', 'edge', 'beacon', 'nova',
        'storm', 'cloud', 'moon', 'star', 'wind',
    ];

    private HttpClient $http;

    public function __construct(?HttpClient $http = null)
    {
        $this->http = $http ?? new HttpClient();
    }

    public function generateEmail(): string
    {
        $name = self::WORDS[array_rand(self::WORDS)] . '_' . self::WORDS2[array_rand(self::WORDS2)];
        $domain = self::DOMAINS[array_rand(self::DOMAINS)];
        return $name . '@' . $domain;
    }

    public function getInbox(string $email): array
    {
        $resp = $this->http->get(self::BASE . '/api/emails?recipient=' . urlencode($email));
        $data = $resp['body'];
        if (!is_array($data) || !isset($data['emails']) || !is_array($data['emails'])) {
            return [];
        }
        return array_values(array_map(function (array $m): Message {
            $date = isset($m['created_at']) ? new \DateTimeImmutable($m['created_at']) : new \DateTimeImmutable();
            $id = $m['id'] ?? '';
            $sender = $m['sender'] ?? 'unknown';
            $subject = $m['subject'] ?? '(no subject)';
            if (!empty($m['body_text']) || !empty($m['body_html'])) {
                return new MessageDetail(
                    id: $id,
                    sender: $sender,
                    subject: $subject,
                    date: $date,
                    bodyText: $m['body_text'] ?? '',
                    bodyHtml: $m['body_html'] ?? '',
                );
            }
            return new Message(id: $id, sender: $sender, subject: $subject, date: $date);
        }, $data['emails']));
    }

    public function readMessage(string $email, string $messageId): MessageDetail
    {
        throw new TempMailException('Ncaori Mail+ returns full message in getInbox(). Use getInbox() then filter by id.');
    }

    public function deleteEmail(string $email): bool
    {
        return true;
    }

    public function waitForEmail(string $email, int $timeout = 60, int $interval = 5): ?Message
    {
        $deadline = time() + $timeout;
        while (time() < $deadline) {
            $inbox = $this->getInbox($email);
            if (!empty($inbox)) {
                return $inbox[0];
            }
            sleep($interval);
        }
        return null;
    }
}
