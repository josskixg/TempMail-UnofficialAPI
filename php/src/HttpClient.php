<?php

declare(strict_types=1);

namespace TempMail;

use TempMail\Exceptions\RateLimitException;
use TempMail\Exceptions\NotFoundException;
use TempMail\Exceptions\TempMailException;

class HttpClient
{
    private const UA_POOL = [
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 13_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:130.0) Gecko/20100101 Firefox/130.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 14.1; rv:133.0) Gecko/20100101 Firefox/133.0',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 14.0; rv:132.0) Gecko/20100101 Firefox/132.0',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 13.6; rv:131.0) Gecko/20100101 Firefox/131.0',
        'Mozilla/5.0 (X11; Linux x86_64; rv:133.0) Gecko/20100101 Firefox/133.0',
        'Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0',
        'Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 13_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
        'Mozilla/5.0 (X11; Linux x86_64; rv:129.0) Gecko/20100101 Firefox/129.0',
        'Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0',
        'Mozilla/5.0 (X11; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36 Edg/127.0.0.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 OPR/115.0.0.0',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 OPR/114.0.0.0',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 13_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    ];

    private array $proxies = [];
    private bool $randomUA;
    private array $defaultHeaders = [];
    private ?string $cookieFile = null;
    private int $proxyIdx = 0;

    public function __construct(array $configOrHeaders = [])
    {
        // Detect: legacy callers pass raw headers, new callers use config keys
        $hasConfigKeys = array_key_exists('proxies', $configOrHeaders)
            || array_key_exists('random_ua', $configOrHeaders)
            || array_key_exists('use_cookies', $configOrHeaders)
            || array_key_exists('default_headers', $configOrHeaders);

        if ($hasConfigKeys) {
            $this->proxies = $configOrHeaders['proxies'] ?? [];
            $this->randomUA = $configOrHeaders['random_ua'] ?? true;
            if (($configOrHeaders['use_cookies'] ?? true)) {
                $this->enableCookieJar();
            }
            foreach ($configOrHeaders['default_headers'] ?? [] as $name => $value) {
                $this->defaultHeaders[$name] = $value;
            }
        } else {
            // Legacy: treat $configOrHeaders as raw defaultHeaders
            $this->defaultHeaders = $configOrHeaders;
            $this->randomUA = true;
            $this->enableCookieJar();
        }
    }

    public function enableCookieJar(): void
    {
        if ($this->cookieFile === null) {
            $this->cookieFile = sys_get_temp_dir() . '/tempmail_cookies_' . uniqid() . '.txt';
            // ponytail: tempnam() creates the file immediately; string path is cleaner for destructor
        }
    }

    public function getCookieFile(): ?string
    {
        return $this->cookieFile;
    }

    public function setDefaultHeader(string $name, string $value): void
    {
        $this->defaultHeaders[$name] = $value;
    }

    /**
     * @param array|string|null $body Array for JSON body, string for raw body (e.g. form-encoded)
     * @return array{status: int, body: array|string, headers: array<string, string>}
     */
    public function request(
        string $method,
        string $url,
        array|string|null $body = null,
        array $headers = [],
    ): array {
        $delays = [1, 3, 5];
        $maxRetries = count($delays);

        for ($attempt = 0; $attempt <= $maxRetries; $attempt++) {
            $ch = curl_init();

            $mergedHeaders = array_merge($this->defaultHeaders, $headers);

            // Random UA each attempt
            if ($this->randomUA) {
                $mergedHeaders['User-Agent'] = self::UA_POOL[array_rand(self::UA_POOL)];
            }

            $headerLines = [];
            foreach ($mergedHeaders as $name => $value) {
                $headerLines[] = "$name: $value";
            }

            $opts = [
                CURLOPT_URL => $url,
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_HEADER => true,
                CURLOPT_TIMEOUT => 30,
                CURLOPT_FOLLOWLOCATION => true,
                CURLOPT_HTTPHEADER => $headerLines,
                CURLOPT_SSL_VERIFYPEER => true,
                CURLOPT_ENCODING => '', // Accept gzip/deflate
            ];

            // Rotate proxy each attempt
            if (!empty($this->proxies)) {
                $proxy = $this->proxies[$this->proxyIdx % count($this->proxies)];
                $this->proxyIdx++;
                $opts[CURLOPT_PROXY] = $proxy;
            }

            if ($this->cookieFile !== null) {
                $opts[CURLOPT_COOKIEJAR] = $this->cookieFile;
                $opts[CURLOPT_COOKIEFILE] = $this->cookieFile;
            }

            $method = strtoupper($method);
            if ($method === 'POST') {
                $opts[CURLOPT_POST] = true;
                if ($body !== null) {
                    if (is_string($body)) {
                        $opts[CURLOPT_POSTFIELDS] = $body;
                    } else {
                        $jsonBody = json_encode($body, JSON_THROW_ON_ERROR);
                        $opts[CURLOPT_POSTFIELDS] = $jsonBody;
                        $hasContentType = false;
                        foreach ($headerLines as $line) {
                            if (stripos($line, 'content-type') === 0) {
                                $hasContentType = true;
                                break;
                            }
                        }
                        if (!$hasContentType) {
                            $headerLines[] = 'Content-Type: application/json';
                            $opts[CURLOPT_HTTPHEADER] = $headerLines;
                        }
                    }
                }
            } elseif ($method === 'DELETE') {
                $opts[CURLOPT_CUSTOMREQUEST] = 'DELETE';
            } elseif ($method === 'PATCH') {
                $opts[CURLOPT_CUSTOMREQUEST] = 'PATCH';
                if ($body !== null) {
                    $opts[CURLOPT_POSTFIELDS] = is_string($body) ? $body : json_encode($body, JSON_THROW_ON_ERROR);
                }
            }

            curl_setopt_array($ch, $opts);

            $response = curl_exec($ch);
            if ($response === false) {
                $error = curl_error($ch);
                curl_close($ch);
                if ($attempt < $maxRetries) {
                    sleep($delays[$attempt]);
                    continue;
                }
                throw new TempMailException("cURL error: $error");
            }

            $status = curl_getinfo($ch, CURLINFO_HTTP_CODE);
            $headerSize = curl_getinfo($ch, CURLINFO_HEADER_SIZE);
            curl_close($ch);

            $rawHeaders = substr((string) $response, 0, $headerSize);
            $responseBody = substr((string) $response, $headerSize);
            $parsedHeaders = $this->parseHeaders($rawHeaders);

            // 429 -> retry with backoff
            if ($status === 429) {
                if ($attempt < $maxRetries) {
                    sleep($delays[$attempt]);
                    continue;
                }
                $retryAfter = isset($parsedHeaders['retry-after']) ? (int) $parsedHeaders['retry-after'] : null;
                throw new RateLimitException(retryAfter: $retryAfter, context: ['body' => $responseBody]);
            }

            if ($status === 404) {
                throw new NotFoundException(context: ['body' => $responseBody]);
            }

            if ($status >= 400) {
                throw new TempMailException(
                    "HTTP $status: $responseBody",
                    $status,
                    context: ['headers' => $parsedHeaders],
                );
            }

            $decoded = json_decode($responseBody, true);
            $parsedBody = json_last_error() === JSON_ERROR_NONE ? $decoded : $responseBody;

            return [
                'status' => $status,
                'body' => $parsedBody,
                'headers' => $parsedHeaders,
            ];
        }

        throw new TempMailException('request failed after retries'); // ponytail: unreachable
    }

    public function get(string $url, array $headers = []): array
    {
        return $this->request('GET', $url, null, $headers);
    }

    public function post(string $url, array|string|null $body = null, array $headers = []): array
    {
        return $this->request('POST', $url, $body, $headers);
    }

    public function delete(string $url, array $headers = []): array
    {
        return $this->request('DELETE', $url, null, $headers);
    }

    private function parseHeaders(string $raw): array
    {
        $headers = [];
        foreach (explode("\r\n", $raw) as $line) {
            if (str_contains($line, ':')) {
                [$name, $value] = explode(':', $line, 2);
                $headers[strtolower(trim($name))] = trim($value);
            }
        }
        return $headers;
    }

    public function __destruct()
    {
        if ($this->cookieFile !== null && file_exists($this->cookieFile)) {
            @unlink($this->cookieFile);
        }
    }
}
