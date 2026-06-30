<?php

declare(strict_types=1);

namespace TempMail\Providers;

use TempMail\HttpClient;
use TempMail\Models\Message;
use TempMail\Models\MessageDetail;
use TempMail\TempMailProvider;

class GuerrillaMailProvider implements TempMailProvider
{
    private const BASE_URL = 'https://api.guerrillamail.com/ajax.php';

    private HttpClient $http;

    public function __construct(?HttpClient $http = null)
    {
        $this->http = $http ?? new HttpClient();
        // Cookie jar enabled by HttpClient default; explicit for clarity
    }

    public function generateEmail(): string
    {
        $res = $this->http->get(self::BASE_URL . '?f=get_email_address&lang=en');
        return $res['body']['email_addr'];
    }

    public function getInbox(string $email): array
    {
        // GuerrillaMail uses session cookie, email param is ignored after init
        $res = $this->http->get(self::BASE_URL . '?f=get_email_list&offset=0&lang=en');
        $list = $res['body']['list'] ?? [];

        return array_map(fn(array $m) => new Message(
            id: (string) $m['mail_id'],
            sender: $m['mail_from'],
            subject: $m['mail_subject'],
            date: \DateTimeImmutable::createFromFormat('U', (string) $m['mail_timestamp']) ?: new \DateTimeImmutable(),
        ), $list);
    }

    public function readMessage(string $email, string $messageId): MessageDetail
    {
        $res = $this->http->get(self::BASE_URL . "?f=fetch_email&email_id=$messageId&lang=en");
        $m = $res['body'];

        return new MessageDetail(
            id: (string) $m['mail_id'],
            sender: $m['mail_from'],
            subject: $m['mail_subject'],
            date: \DateTimeImmutable::createFromFormat('U', (string) $m['mail_timestamp']) ?: new \DateTimeImmutable(),
            bodyText: strip_tags($m['mail_body'] ?? ''),
            bodyHtml: $m['mail_body'] ?? '',
            attachments: [], // GuerrillaMail API doesn't expose attachment downloads directly
        );
    }

    public function deleteEmail(string $email): bool
    {
        // GuerrillaMail auto-expires; no explicit delete API
        return true;
    }

    public function waitForEmail(string $email, int $timeout = 60, int $interval = 5): ?Message
    {
        $deadline = time() + $timeout;
        while (time() < $deadline) {
            $checkRes = $this->http->get(self::BASE_URL . "?f=check_email&seq=1&lang=en");
            $list = $checkRes['body']['list'] ?? [];
            if (!empty($list)) {
                return new Message(
                    id: (string) $list[0]['mail_id'],
                    sender: $list[0]['mail_from'],
                    subject: $list[0]['mail_subject'],
                    date: \DateTimeImmutable::createFromFormat('U', (string) $list[0]['mail_timestamp'])
                        ?: new \DateTimeImmutable(),
                );
            }
            sleep($interval);
        }
        return null;
    }
}
