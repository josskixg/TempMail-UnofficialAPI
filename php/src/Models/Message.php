<?php

declare(strict_types=1);

namespace TempMail\Models;

class Message
{
    public function __construct(
        public readonly string $id,
        public readonly string $sender,
        public readonly string $subject,
        public readonly \DateTimeImmutable $date,
    ) {}
}
