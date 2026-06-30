<?php

declare(strict_types=1);

namespace TempMail\Exceptions;

class RateLimitException extends TempMailException
{
    public function __construct(
        string $message = 'Rate limit exceeded',
        public readonly ?int $retryAfter = null,
        array $context = [],
    ) {
        parent::__construct($message, 429, context: $context);
    }
}
