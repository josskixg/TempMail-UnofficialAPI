package com.tempmail.http;

import com.tempmail.errors.NotFoundException;
import com.tempmail.errors.RateLimitException;
import com.tempmail.errors.TempMailException;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Anti-429 HTTP client with UA rotation, retry backoff, and cookie management.
 * Wraps java.net.http.HttpClient (Java 11+).
 */
public class HttpClient {

    private static final String[] UA_POOL = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Safari/605.1.15",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Safari/605.1.15",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 12_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.3 Safari/605.1.15",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0",
        "Mozilla/5.0 (X11; Linux x86_64; rv:129.0) Gecko/20100101 Firefox/129.0",
        "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0",
        "Mozilla/5.0 (X11; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0",
        "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0",
        "Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:126.0) Gecko/20100101 Firefox/126.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:130.0) Gecko/20100101 Firefox/130.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.0; rv:130.0) Gecko/20100101 Firefox/130.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 13.6; rv:129.0) Gecko/20100101 Firefox/129.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 OPR/115.0.0.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 OPR/115.0.0.0",
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/130.0.0.0 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (iPad; CPU OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/129.0.0.0 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-A536B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; V2230) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; moto g 5G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36",
    };

    private static final int MAX_RETRIES = 3;
    private static final int[] BACKOFF_MS = {1000, 3000, 5000};

    private final java.net.http.HttpClient client;
    private final CookieManager cookieManager;
    private final boolean randomUA;
    private final Random random = new Random();

    public HttpClient() {
        this(true, true);
    }

    public HttpClient(boolean randomUA, boolean useCookies) {
        this.randomUA = randomUA;
        this.cookieManager = useCookies ? new CookieManager(null, CookiePolicy.ACCEPT_ALL) : null;
        var builder = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15));
        if (useCookies) {
            builder.cookieHandler(this.cookieManager);
        }
        this.client = builder.build();
    }

    /**
     * GET with automatic retry (3 attempts, 1s/3s/5s backoff).
     * @return response body
     * @throws TempMailException on persistent failure
     */
    public String get(String url) throws TempMailException {
        return request("GET", url, null, null);
    }

    /**
     * GET with additional headers.
     * @return response body
     * @throws TempMailException on persistent failure
     */
    public String get(String url, Map<String, String> headers) throws TempMailException {
        return request("GET", url, null, headers);
    }

    /**
     * POST with automatic retry (3 attempts, 1s/3s/5s backoff).
     * @param url target URL
     * @param body request body (null for empty)
     * @return response body
     * @throws TempMailException on persistent failure
     */
    public String post(String url, String body) throws TempMailException {
        return request("POST", url, body, null);
    }

    /**
     * POST with additional headers.
     * @return response body
     * @throws TempMailException on persistent failure
     */
    public String post(String url, String body, Map<String, String> headers) throws TempMailException {
        return request("POST", url, body, headers);
    }

    /**
     * DELETE with automatic retry.
     * @return response body
     * @throws TempMailException on persistent failure
     */
    public String delete(String url, Map<String, String> headers) throws TempMailException {
        return request("DELETE", url, null, headers);
    }

    private String request(String method, String url, String body, Map<String, String> extraHeaders) throws TempMailException {
        TempMailException lastEx = null;
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                return doRequest(method, url, body, extraHeaders);
            } catch (RateLimitException e) {
                lastEx = e;
                int waitMs = e.getRetryAfterSeconds() > 0 ? e.getRetryAfterSeconds() * 1000 : BACKOFF_MS[i];
                sleep(waitMs);
            } catch (TempMailException e) {
                lastEx = e;
                if (e instanceof NotFoundException) {
                    throw e; // 404 doesn't benefit from retry
                }
                sleep(BACKOFF_MS[i]);
            }
        }
        throw lastEx != null ? lastEx : new TempMailException("Request failed: " + method + " " + url, 0);
    }

    private String doRequest(String method, String url, String body, Map<String, String> extraHeaders) throws TempMailException {
        try {
            var uri = URI.create(url);
            var builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Accept", "application/json, text/html, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(Duration.ofSeconds(30));

            if (randomUA) {
                builder.header("User-Agent", randomUA());
            }
            if (extraHeaders != null) {
                for (var entry : extraHeaders.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }

            HttpRequest request;
            if ("POST".equalsIgnoreCase(method)) {
                String contentType = (body != null && body.trim().startsWith("{"))
                        ? "application/json"
                        : "application/x-www-form-urlencoded";
                builder.header("Content-Type", contentType);
                var bp = body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody();
                request = builder.POST(bp).build();
            } else {
                request = builder.GET().build();
            }

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            String respBody = response.body();

            if (code == 429) {
                String ra = response.headers().firstValue("Retry-After").orElse(null);
                int retrySec = parseRetryAfter(ra);
                throw new RateLimitException("Rate limited: " + url, retrySec);
            }
            if (code == 404) {
                throw new NotFoundException("Not found: " + url);
            }
            if (code >= 400) {
                throw new TempMailException(method + " " + url + " -> HTTP " + code + ": " + truncate(respBody, 200), code);
            }
            return respBody;
        } catch (RateLimitException | NotFoundException e) {
            throw e;
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg == null || msg.isEmpty()) msg = e.getClass().getSimpleName();
            throw new TempMailException("IO error: " + msg, 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TempMailException("Interrupted", 0);
        }
    }

    private String randomUA() {
        return UA_POOL[random.nextInt(UA_POOL.length)];
    }

    private int parseRetryAfter(String header) {
        if (header == null || header.isEmpty()) return -1;
        try {
            return Integer.parseInt(header.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /** CookieManager for providers that need manual cookie access. */
    public CookieManager getCookieManager() {
        return cookieManager;
    }
}
