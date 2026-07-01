<?php

declare(strict_types=1);

namespace TempMail;

use TempMail\Providers\MailTmProvider;
use TempMail\Providers\GuerrillaMailProvider;
use TempMail\Providers\YOPmailProvider;
use TempMail\Providers\DropmailProvider;
use TempMail\Providers\OneSecEmailProvider;
use TempMail\Providers\NcaoriMailProvider;

class TempMailFactory
{
    /**
     * Create a provider instance by name.
     *
     * Supported providers: mailtm, guerrilla, yopmail, dropmail
     *
     * All providers work without API keys.
     *
     * Config options (HttpClient config format):
     * - 'proxies' => ['http://proxy1:8080', ...] — proxy list for rotation
     * - 'random_ua' => bool — randomize User-Agent (default: true)
     * - 'use_cookies' => bool — enable cookie jar (default: true)
     * - 'default_headers' => [...] — default headers per request
     *
     * @throws \InvalidArgumentException
     */
    public static function create(string $name, array $config = []): TempMailProvider
    {
        $http = new \TempMail\HttpClient($config);

        return match (strtolower($name)) {
            'mailtm' => new \TempMail\Providers\MailTmProvider($http),
            'guerrilla', 'guerrillamail' => new \TempMail\Providers\GuerrillaMailProvider($http),
            'yopmail' => new \TempMail\Providers\YOPmailProvider($http),
            'dropmail', 'dropmail.me' => new \TempMail\Providers\DropmailProvider($http),
            '1secemail' => new \TempMail\Providers\OneSecEmailProvider($http),
            'ncaori', 'ncaorimail', 'nca.my.id' => new \TempMail\Providers\NcaoriMailProvider($http),
            default => throw new \InvalidArgumentException("Unknown provider: $name"),
        };
    }
}
