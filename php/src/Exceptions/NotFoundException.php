<?php

declare(strict_types=1);

namespace TempMail\Exceptions;

class NotFoundException extends TempMailException
{
    public function __construct(
        string $message = 'Resource not found',
        array $context = [],
    ) {
        parent::__construct($message, 404, context: $context);
    }
}
