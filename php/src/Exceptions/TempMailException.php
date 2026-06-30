<?php

declare(strict_types=1);

namespace TempMail\Exceptions;

class TempMailException extends \RuntimeException
{
    public function __construct(
        string $message = '',
        int $code = 0,
        ?\Throwable $previous = null,
        public readonly array $context = [],
    ) {
        parent::__construct($message, $code, $previous);
    }
}
