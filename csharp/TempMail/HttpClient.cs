using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using TempMail.Exceptions;

namespace TempMail;

public sealed class HttpClient
{
    private static readonly string[] UaPool = [
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:130.0) Gecko/20100101 Firefox/130.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.7; rv:131.0) Gecko/20100101 Firefox/131.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.6; rv:130.0) Gecko/20100101 Firefox/130.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.5; rv:129.0) Gecko/20100101 Firefox/129.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.4; rv:128.0) Gecko/20100101 Firefox/128.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.3; rv:127.0) Gecko/20100101 Firefox/127.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36 Edg/127.0.0.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_4) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0",
        "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0",
        "Mozilla/5.0 (X11; Linux x86_64; rv:129.0) Gecko/20100101 Firefox/129.0",
        "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0",
        "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 OPR/116.0.0.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 OPR/115.0.0.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 OPR/114.0.0.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 OPR/116.0.0.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 OPR/115.0.0.0",
        "Mozilla/5.0 (iPad; CPU OS 17_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (iPad; CPU OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_2) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0",
    ];

    private static readonly int[] RetryDelays = [1, 3, 5];
    private const int MaxAttempts = 4;

    private readonly bool _randomUA;
    private readonly CookieContainer _cookieContainer;
    private readonly Random _random = new();

    public System.Net.Http.HttpClient Client { get; }

    public HttpClient(List<string>? proxies = null, bool randomUA = true, bool useCookies = true)
    {
        _randomUA = randomUA;
        _cookieContainer = new CookieContainer();

        var handler = new HttpClientHandler { CookieContainer = _cookieContainer };

        if (proxies is { Count: > 0 })
        {
            handler.UseProxy = true;
            handler.Proxy = new WebProxy(proxies[0]);
        }

        Client = new System.Net.Http.HttpClient(handler);
        Client.Timeout = TimeSpan.FromMinutes(2);
    }

    public CookieContainer Cookies => _cookieContainer;

    public void Dispose() => Client.Dispose();

    // JSON GET with retry
    public async Task<T?> GetFromJsonAsync<T>(string url, JsonSerializerOptions? options = null, CancellationToken ct = default)
    {
        return await RetryAsync(async token =>
        {
            using var req = new HttpRequestMessage(HttpMethod.Get, url);
            SetUA(req);
            using var res = await Client.SendAsync(req, token);
            Check429(res, url);
            res.EnsureSuccessStatusCode();
            return await res.Content.ReadFromJsonAsync<T>(options, token);
        }, ct);
    }

    // JSON POST returning HttpResponseMessage
    public async Task<HttpResponseMessage> PostAsJsonAsync<TValue>(string url, TValue value, JsonSerializerOptions? options = null, CancellationToken ct = default)
    {
        HttpResponseMessage? response = null;
        for (int attempt = 0; attempt < MaxAttempts; attempt++)
        {
            try
            {
                response?.Dispose();
                using var req = new HttpRequestMessage(HttpMethod.Post, url)
                {
                    Content = JsonContent.Create(value, options: options)
                };
                SetUA(req);
                response = await Client.SendAsync(req, ct);
                Check429(response, url);
                response.EnsureSuccessStatusCode();
                return response;
            }
            catch (RateLimitException) when (attempt < RetryDelays.Length)
            {
                response?.Dispose();
                response = null;
                await Task.Delay(TimeSpan.FromSeconds(RetryDelays[attempt]), ct);
            }
            catch
            {
                response?.Dispose();
                throw;
            }
        }
        throw new RateLimitException($"Max retries exceeded for {url}");
    }

    // String GET
    public async Task<string> GetAsync(string url, CancellationToken ct = default)
    {
        return await RetryAsync(async token =>
        {
            using var req = new HttpRequestMessage(HttpMethod.Get, url);
            SetUA(req);
            using var res = await Client.SendAsync(req, token);
            Check429(res, url);
            res.EnsureSuccessStatusCode();
            return await res.Content.ReadAsStringAsync(token);
        }, ct);
    }

    // String GET with header configuration
    public async Task<string> GetAsync(string url, Action<HttpRequestHeaders> configureHeaders, CancellationToken ct = default)
    {
        return await RetryAsync(async token =>
        {
            using var req = new HttpRequestMessage(HttpMethod.Get, url);
            SetUA(req);
            configureHeaders(req.Headers);
            using var res = await Client.SendAsync(req, token);
            Check429(res, url);
            res.EnsureSuccessStatusCode();
            return await res.Content.ReadAsStringAsync(token);
        }, ct);
    }

    // String POST with body string
    public async Task<string> PostAsync(string url, string body, CancellationToken ct = default)
    {
        using var content = new StringContent(body, Encoding.UTF8, "application/json");
        return await RetryAsync(async token =>
        {
            using var req = new HttpRequestMessage(HttpMethod.Post, url) { Content = content };
            SetUA(req);
            using var res = await Client.SendAsync(req, token);
            Check429(res, url);
            res.EnsureSuccessStatusCode();
            return await res.Content.ReadAsStringAsync(token);
        }, ct);
    }

    // String POST with HttpContent
    public async Task<string> PostAsync(string url, HttpContent content, CancellationToken ct = default)
    {
        return await RetryAsync(async token =>
        {
            using var req = new HttpRequestMessage(HttpMethod.Post, url) { Content = content };
            SetUA(req);
            using var res = await Client.SendAsync(req, token);
            Check429(res, url);
            res.EnsureSuccessStatusCode();
            return await res.Content.ReadAsStringAsync(token);
        }, ct);
    }

    // String POST with HttpContent and header configuration
    public async Task<string> PostAsync(string url, HttpContent content, Action<HttpRequestHeaders> configureHeaders, CancellationToken ct = default)
    {
        return await RetryAsync(async token =>
        {
            using var req = new HttpRequestMessage(HttpMethod.Post, url) { Content = content };
            SetUA(req);
            configureHeaders(req.Headers);
            using var res = await Client.SendAsync(req, token);
            Check429(res, url);
            res.EnsureSuccessStatusCode();
            return await res.Content.ReadAsStringAsync(token);
        }, ct);
    }

    // DELETE with retry
    public async Task<HttpResponseMessage> DeleteAsync(string url, CancellationToken ct = default)
    {
        HttpResponseMessage? response = null;
        for (int attempt = 0; attempt < MaxAttempts; attempt++)
        {
            try
            {
                response?.Dispose();
                using var req = new HttpRequestMessage(HttpMethod.Delete, url);
                SetUA(req);
                response = await Client.SendAsync(req, ct);
                Check429(response, url);
                response.EnsureSuccessStatusCode();
                return response;
            }
            catch (RateLimitException) when (attempt < RetryDelays.Length)
            {
                response?.Dispose();
                response = null;
                await Task.Delay(TimeSpan.FromSeconds(RetryDelays[attempt]), ct);
            }
            catch
            {
                response?.Dispose();
                throw;
            }
        }
        throw new RateLimitException($"Max retries exceeded for {url}");
    }

    // Expose DefaultRequestHeaders for provider-level persistent headers
    public HttpRequestHeaders DefaultRequestHeaders => Client.DefaultRequestHeaders;

    private async Task<T> RetryAsync<T>(Func<CancellationToken, Task<T>> fn, CancellationToken ct)
    {
        for (int attempt = 0; attempt < MaxAttempts; attempt++)
        {
            try
            {
                return await fn(ct);
            }
            catch (RateLimitException) when (attempt < RetryDelays.Length)
            {
                await Task.Delay(TimeSpan.FromSeconds(RetryDelays[attempt]), ct);
            }
        }
        throw new RateLimitException("Max retries exceeded");
    }

    private static void Check429(HttpResponseMessage res, string url)
    {
        if ((int)res.StatusCode == 429) throw new RateLimitException($"HTTP 429 from {url}");
    }

    private void SetUA(HttpRequestMessage req)
    {
        if (!_randomUA) return;
        req.Headers.UserAgent.ParseAdd(UaPool[_random.Next(UaPool.Length)]);
    }
}
