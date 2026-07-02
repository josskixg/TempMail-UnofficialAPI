<?php

declare(strict_types=1);

namespace TempMail\Models;

class MessageDetail extends Message
{
    public readonly bool $isHtml;
    public readonly string $contentType;
    public readonly string $bodyPreview;

    /**
     * @param array<array{name: string, type: string, size: int, url?: string}> $attachments
     * @param array<string, string> $headers
     * @param string[] $cc
     */
    public function __construct(
        string $id,
        string $sender,
        string $subject,
        \DateTimeImmutable $date,
        public readonly string $bodyText = '',
        public readonly string $bodyHtml = '',
        public readonly array $attachments = [],
        public readonly string $bodyPreviewInput = '',
        public readonly string $contentTypeInput = '',
        public readonly string $raw = '',
        public readonly array $headers = [],
        public readonly array $cc = [],
        public readonly string $replyTo = '',
        public readonly string $messageId = '',
        public readonly int $size = 0,
    ) {
        // Normalize: strip HTML → text if only HTML provided
        $hasHtml = trim($bodyHtml) !== '';
        $hasText = trim($bodyText) !== '';

        $resolvedText = $hasText ? $bodyText : ($hasHtml ? self::stripHtml($bodyHtml) : '');

        parent::__construct(
            $id, $sender, $subject, $date,
            preview: '',
            hasAttachments: count($attachments) > 0,
        );

        // Re-assign via late binding pattern (readonly workaround via init)
        $this->isHtml = $hasHtml;

        $this->contentType = match(true) {
            $hasHtml && $resolvedText !== '' => 'multipart/alternative',
            $hasHtml                          => 'text/html',
            default                           => ($contentTypeInput !== '' ? $contentTypeInput : 'text/plain'),
        };

        $this->bodyPreview = $bodyPreviewInput !== ''
            ? $bodyPreviewInput
            : mb_substr(trim($resolvedText), 0, 200);
    }

    /** Strip HTML tags to plain text. */
    public static function stripHtml(string $html): string
    {
        if ($html === '') {
            return '';
        }
        // Remove style/script blocks
        $s = preg_replace('#<(style|script)[^>]*>.*?</\1>#is', '', $html) ?? $html;
        // Replace block elements with newlines
        $s = preg_replace('#<(br\s*/?|/p|/div|/tr|/li|/h\d)>#i', "\n", $s) ?? $s;
        // Strip remaining tags
        $s = strip_tags($s);
        // Decode entities
        $s = html_entity_decode($s, ENT_QUOTES | ENT_HTML5, 'UTF-8');
        // Collapse blank lines
        $s = (string) preg_replace('/\n{3,}/', "\n\n", $s);
        return trim($s);
    }
}
