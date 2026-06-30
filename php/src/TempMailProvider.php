<?php

declare(strict_types=1);

namespace TempMail;

use TempMail\Models\Message;
use TempMail\Models\MessageDetail;

interface TempMailProvider
{
    /** Generate a random temporary email address. */
    public function generateEmail(): string;

    /** Get inbox messages for an email address. @return Message[] */
    public function getInbox(string $email): array;

    /** Read full message content by ID. */
    public function readMessage(string $email, string $messageId): MessageDetail;

    /** Delete an email address / mailbox. */
    public function deleteEmail(string $email): bool;

    /** Block until a new email arrives or timeout. Returns null on timeout. */
    public function waitForEmail(string $email, int $timeout = 60, int $interval = 5): ?Message;
}
