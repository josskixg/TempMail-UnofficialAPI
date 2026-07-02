<?php

declare(strict_types=1);

namespace TempMail\Providers;

use TempMail\HttpClient;
use TempMail\Models\Message;
use TempMail\Models\MessageDetail;
use TempMail\TempMailProvider;
use TempMail\Exceptions\NotFoundException;
use TempMail\Exceptions\TempMailException;

class TenMinuteMailProvider implements TempMailProvider
{
    private const BASE_URL = 'https://10minutemail.net';

    private HttpClient $http;
    private ?string $currentEmail = null;

    public function __construct(?HttpClient $http = null)
    {
        $this->http = $http ?? new HttpClient(randomUA: true, useCookies: true);
    }

    private function decodeCfEmail(string $hex): string
    {
        if (strlen($hex) < 4) {
            return '';
        }
        $k = (int)hexdec(substr($hex, 0, 2));
        $str = '';
        for ($i = 2; $i < strlen($hex); $i += 2) {
            $str .= chr((int)hexdec(substr($hex, $i, 2)) ^ $k);
        }
        return $str;
    }

    public function generateEmail(): string
    {
        $res = $this->http->get(self::BASE_URL . '/');
        $html = is_string($res['body']) ? $res['body'] : '';

        if (!preg_match('/id="fe_text"[^>]*value="([^"]+)"/i', $html, $match)) {
            throw new TempMailException('10minutemail: no address in response');
        }

        $this->currentEmail = trim($match[1]);
        return $this->currentEmail;
    }

    public function getInbox(string $email): array
    {
        $res = $this->http->get(self::BASE_URL . '/mailbox.ajax.php');
        $html = is_string($res['body']) ? $res['body'] : '';

        if (!preg_match_all('/<tr[^>]*>(.*?)<\/tr>/is', $html, $rows)) {
            return [];
        }

        $messages = [];
        // Skip header row
        for ($i = 1; $i < count($rows[1]); $i++) {
            $rowHtml = $rows[1][$i];
            if (!preg_match_all('/<td[^>]*>(.*?)<\/td>/is', $rowHtml, $cells)) {
                continue;
            }
            $tds = $cells[1];
            if (count($tds) < 3) {
                continue;
            }

            // Extract Sender
            $sender = '';
            if (preg_match('/data-cfemail="([^"]+)"/i', $tds[0], $cf)) {
                $sender = $this->decodeCfEmail($cf[1]);
            } else {
                $sender = trim(strip_tags($tds[0]));
            }

            $subject = trim(strip_tags($tds[1]));

            // Extract Date
            $dateStr = '';
            if (preg_match('/title="([^"]+)"/i', $tds[2], $title)) {
                $dateStr = $title[1];
            } else {
                $dateStr = trim(strip_tags($tds[2]));
            }

            if (!str_contains(strtolower($dateStr), 'utc')) {
                $dateStr .= ' UTC';
            }

            // Extract mid
            if (!preg_match('/mid=([^\'"\\s>]+)/i', $rowHtml, $mid)) {
                continue;
            }
            $id = $mid[1];

            $messages[] = new Message(
                id: $id,
                sender: $sender,
                subject: $subject,
                date: $this->parseDate($dateStr),
            );
        }

        return $messages;
    }

    public function readMessage(string $email, string $messageId): MessageDetail
    {
        // Parse composite messageId if it has a colon
        $mid = str_contains($messageId, ':') ? explode(':', $messageId, 2)[0] : $messageId;

        $res = $this->http->get(self::BASE_URL . '/readmail.html?mid=' . urlencode($mid));
        $html = is_string($res['body']) ? $res['body'] : '';

        if (!preg_match('/class="mailinhtml"[^>]*>(.*?)<div[^>]*style="clear:both;"/is', $html, $bodyMatch)) {
            throw new NotFoundException("10minutemail: message {$messageId} not found");
        }

        $bodyHtml = trim($bodyMatch[1]);

        // Decode CF email obfuscation in body html
        $bodyHtml = preg_replace_callback('/<(a|span)[^>]*class="__cf_email__"[^>]*data-cfemail="([^"]+)"[^>]*>.*?<\/\\1>/i', function ($m) {
            return $this->decodeCfEmail($m[2]);
        }, $bodyHtml);

        $bodyHtml = preg_replace_callback('/href="\/cdn-cgi\/l\/email-protection#([^"]+)"/i', function ($m) {
            return 'href="mailto:' . $this->decodeCfEmail($m[1]) . '"';
        }, $bodyHtml);

        $bodyText = trim(strip_tags($bodyHtml));

        // Extract Subject
        $subject = '';
        if (preg_match('/<div class="mail_header">.*?<h2[^>]*>(.*?)<\/h2>/is', $html, $subMatch)) {
            $subject = trim(strip_tags($subMatch[1]));
        }

        // Extract Sender
        $sender = '';
        if (preg_match('/<span class="mail_from">(.*?)<\/span>/is', $html, $fromMatch)) {
            if (preg_match('/data-cfemail="([^"]+)"/i', $fromMatch[1], $cf)) {
                $sender = $this->decodeCfEmail($cf[1]);
            } else {
                $sender = trim(strip_tags($fromMatch[1]));
            }
        }

        return new MessageDetail(
            id: $mid,
            sender: $sender,
            subject: $subject,
            date: new \DateTimeImmutable(),
            bodyText: $bodyText,
            bodyHtml: $bodyHtml,
            attachments: [],
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
