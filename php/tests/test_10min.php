<?php
declare(strict_types=1);

require_once __DIR__ . '/../src/Exceptions/TempMailException.php';
require_once __DIR__ . '/../src/Exceptions/RateLimitException.php';
require_once __DIR__ . '/../src/Exceptions/NotFoundException.php';
require_once __DIR__ . '/../src/Models/Message.php';
require_once __DIR__ . '/../src/Models/MessageDetail.php';
require_once __DIR__ . '/../src/HttpClient.php';
require_once __DIR__ . '/../src/TempMailProvider.php';
require_once __DIR__ . '/../src/TempMailFactory.php';

foreach (glob(__DIR__ . '/../src/Providers/*.php') as $file) {
    require_once $file;
}

use TempMail\TempMailFactory;

$provider = TempMailFactory::create('10minutemail');
echo "Generating email...\n";
$email = $provider->generateEmail();
echo "Generated email: $email\n";

echo "Fetching inbox...\n";
$inbox = $provider->getInbox($email);
echo "Inbox count: " . count($inbox) . "\n";
foreach ($inbox as $msg) {
    echo "Message ID: {$msg->id}, Sender: {$msg->sender}, Subject: {$msg->subject}\n";
    echo "Reading message...\n";
    $detail = $provider->readMessage($email, $msg->id);
    echo "Read message subject: {$detail->subject}\n";
    echo "Read message bodyText: " . substr($detail->bodyText, 0, 100) . "...\n";
}
echo "Done!\n";
