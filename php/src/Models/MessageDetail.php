<?php

declare(strict_types=1);

namespace TempMail\Models;

class MessageDetail extends Message
{
    /**
     * @param array<array{name: string, type: string, size: int, url?: string}> $attachments
     */
    public function __construct(
        string $id,
        string $sender,
        string $subject,
        \DateTimeImmutable $date,
        public readonly string $bodyText,
        public readonly string $bodyHtml,
        public readonly array $attachments = [],
    ) {
        parent::__construct($id, $sender, $subject, $date);
    }
}
