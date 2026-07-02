<?php

declare(strict_types=1);

namespace TempMail\Providers;

use TempMail\HttpClient;
use TempMail\Models\Message;
use TempMail\Models\MessageDetail;
use TempMail\TempMailProvider;
use TempMail\Exceptions\NotFoundException;
use TempMail\Exceptions\TempMailException;

class EmailfakeProvider implements TempMailProvider
{
    private const BASE_URL = 'https://emailfake.com';
    private const COOKIE_DOMAIN = '.emailfake.com';
    private const SITE_NAME = 'emailfake';

    private HttpClient $http;
    private ?string $domain = null;
    private ?string $username = null;
    private ?string $currentEmail = null;

    public function __construct(?HttpClient $http = null)
    {
        $this->http = $http ?? new HttpClient();
    }

    public function generateEmail(): string
    {
        $domains = $this->getDomains();
        $this->domain = $domains[array_rand($domains)];
        $this->username = bin2hex(random_bytes(5)); // 10 chars
        $this->currentEmail = "{$this->username}@{$this->domain}";
        $this->setSurlCookie();
        return $this->currentEmail;
    }

    public function getInbox(string $email): array
    {
        if ($this->domain === null || $this->username === null) {
            throw new TempMailException(self::SITE_NAME . ': no email generated — call generateEmail() first');
        }
        $ch = random_int(1, 9);
        $res = $this->http->get(self::BASE_URL . "/channel{$ch}/");
        $html = is_string($res['body']) ? $res['body'] : '';
        return $this->parseInbox($html);
    }

    public function readMessage(string $email, string $messageId): MessageDetail
    {
        if ($this->domain === null || $this->username === null) {
            throw new TempMailException(self::SITE_NAME . ': no email generated — call generateEmail() first');
        }
        $url = self::BASE_URL . "/{$this->domain}/{$this->username}/{$messageId}";
        $res = $this->http->get($url);
        $html = is_string($res['body']) ? $res['body'] : '';

        $xpath = $this->loadXpath($html);

        $msgDiv = $xpath->query('//div[@id="message"]')->item(0);
        if ($msgDiv === null) {
            throw new NotFoundException(self::SITE_NAME . ": message {$messageId} not found");
        }

        $bodyHtml = $msgDiv->ownerDocument->saveHTML($msgDiv);
        $bodyText = trim($msgDiv->textContent);

        $sender = $this->findDivText($xpath, 'from_div');
        $subject = $this->findDivText($xpath, 'subj_div');
        $timeStr = $this->findDivText($xpath, 'time_div');

        return new MessageDetail(
            id: $messageId,
            sender: $sender ?: 'unknown',
            subject: $subject ?: '(no subject)',
            date: $this->parseDate($timeStr),
            bodyText: $bodyText,
            bodyHtml: $bodyHtml,
        );
    }

    public function deleteEmail(string $email): bool
    {
        $this->domain = null;
        $this->username = null;
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

    /** @return string[] */
    private function getDomains(): array
    {
        $ch = random_int(1, 9);
        $res = $this->http->get(self::BASE_URL . "/channel{$ch}/");
        $html = is_string($res['body']) ? $res['body'] : '';

        $domains = [];
        // Primary: parse <option> tags
        if (preg_match_all('/<option[^>]*value="([^"]+)"/i', $html, $m)) {
            foreach ($m[1] as $val) {
                $val = trim($val);
                if (str_contains($val, '.') && !str_contains($val, ' ') && !str_contains($val, '@')) {
                    $domains[] = $val;
                }
            }
        }
        // Fallback: find domain-like strings in text
        if (empty($domains)) {
            $text = strip_tags($html);
            if (preg_match_all('/\b([a-z0-9][a-z0-9-]*\.[a-z]{2,}(?:\.[a-z]{2,})?)\b/i', $text, $m)) {
                foreach ($m[1] as $d) {
                    $d = strtolower($d);
                    if (!str_contains($d, self::SITE_NAME)) {
                        $domains[] = $d;
                    }
                }
            }
        }
        $domains = array_values(array_unique($domains));
        if (empty($domains)) {
            throw new TempMailException(self::SITE_NAME . ': no domains found');
        }
        return $domains;
    }

    /** @return Message[] */
    private function parseInbox(string $html): array
    {
        $xpath = $this->loadXpath($html);

        $table = $xpath->query('//div[@id="email-table"]')->item(0);
        if ($table === null) {
            return [];
        }

        $messages = [];
        $links = $xpath->query('.//a[contains(@class, "list-group-item")]', $table);
        foreach ($links as $link) {
            $href = $link->getAttribute('href');
            $parts = explode('/', rtrim($href, '/'));
            $msgId = !empty($parts) ? end($parts) : '';
            if (strlen($msgId) < 10) {
                continue;
            }

            $sender = '';
            $subject = '';
            $timeStr = '';
            foreach ($xpath->query('.//div', $link) as $div) {
                $class = $div->getAttribute('class');
                if (str_contains($class, 'from')) {
                    $sender = trim($div->textContent);
                } elseif (str_contains($class, 'subj')) {
                    $subject = trim($div->textContent);
                } elseif (str_contains($class, 'time')) {
                    $timeStr = trim($div->textContent);
                }
            }

            $messages[] = new Message(
                id: $msgId,
                sender: $sender ?: 'unknown',
                subject: $subject ?: '(no subject)',
                date: $this->parseDate($timeStr),
            );
        }
        return $messages;
    }

    private function findDivText(\DOMXPath $xpath, string $classPattern): string
    {
        $nodes = $xpath->query("//div[contains(@class, '{$classPattern}')]");
        return $nodes->length > 0 ? trim($nodes->item(0)->textContent) : '';
    }

    /** Load HTML into DOMXPath with UTF-8 encoding. */
    private function loadXpath(string $html): \DOMXPath
    {
        libxml_use_internal_errors(true);
        $doc = new \DOMDocument();
        $doc->loadHTML('<?xml encoding="UTF-8">' . $html);
        libxml_clear_errors();
        return new \DOMXPath($doc);
    }

    /** Write surl cookie to the cookie jar (mirrors Python session.cookies.set). */
    private function setSurlCookie(): void
    {
        $value = "{$this->domain}/{$this->username}";
        $file = $this->http->getCookieFile();
        if ($file !== null) {
            // ponytail: Netscape cookie file format — domain, subdomain, path, secure, expires, name, value
            $line = sprintf("%s\tTRUE\t/\tFALSE\t%d\tsurl\t%s\n",
                self::COOKIE_DOMAIN, time() + 86400, $value);
            @file_put_contents($file, $line, FILE_APPEND);
        } else {
            $this->http->setDefaultHeader('Cookie', 'surl=' . $value);
        }
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
