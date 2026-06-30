using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using TempMail.Exceptions;
using TempMail.Models;

namespace TempMail.Providers;

public sealed class OneSecEmailProvider : ITempMailProvider
{
    private const string BASE = "https://www.1secemail.com";
    private static readonly JsonSerializerOptions JsonOpts = new() { PropertyNameCaseInsensitive = true };
    private static readonly string[] DOMAINS = {
        "qzueos.com", "gaziw.com", "emailgenerator.xyz",
    };
    private static readonly string CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private readonly HttpClient _httpClient;
    private string? _csrf;
    private string? _email;
    private readonly Random _random;

    public OneSecEmailProvider(HttpClient httpClient)
    {
        _httpClient = httpClient;
        _random = new Random();
    }

    public async Task<string> GenerateEmailAsync(CancellationToken ct = default)
    {
        await EnsureCSRFAsync(ct);
        var sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.Append(CHARS[_random.Next(CHARS.Length)]);
        var name = sb.ToString();
        var domain = DOMAINS[_random.Next(DOMAINS.Length)];
        await PostFormAsync("/change", new Dictionary<string, string> { ["name"] = name, ["domain"] = domain }, ct);
        _email = $"{name}@{domain}";
        return _email!;
    }

    private async Task EnsureCSRFAsync(CancellationToken ct)
    {
        if (_csrf != null) return;
        var html = await _httpClient.GetAsync(BASE + "/", ct);
        var csrfMatch = System.Text.RegularExpressions.Regex.Match(html, @"<meta name=""csrf-token"" content=""([^""]+)"">");
        if (!csrfMatch.Success) throw new TempMailException("CSRF token not found on 1secemail page");
        _csrf = csrfMatch.Groups[1].Value;
    }

    private async Task<string> PostFormAsync(string path, Dictionary<string, string> data, CancellationToken ct)
    {
        await EnsureCSRFAsync(ct);
        var body = new Dictionary<string, string> { ["_token"] = _csrf! };
        foreach (var kv in data) body[kv.Key] = kv.Value;
        var content = new StringContent(JsonSerializer.Serialize(body, JsonOpts), Encoding.UTF8, "application/json");
        return await _httpClient.PostAsync(BASE + path, content, headers =>
        {
            headers.TryAddWithoutValidation("X-CSRF-TOKEN", _csrf);
            headers.TryAddWithoutValidation("x-xsrf-token", _csrf);
            headers.TryAddWithoutValidation("Referer", $"{BASE}/");
        }, ct);
    }

    public async Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default)
    {
        var json = await PostFormAsync("/get_messages", new Dictionary<string, string>(), ct);
        List<OneSecMailItem>? items = null;
        try
        {
            // ponytail: API sometimes returns object instead of array — try array first, fallback to empty
            using var doc = JsonDocument.Parse(json);
            if (doc.RootElement.ValueKind == JsonValueKind.Array)
                items = JsonSerializer.Deserialize<List<OneSecMailItem>>(json, JsonOpts);
        }
        catch { /* non-array response — return empty */ }
        if (items == null) return Array.Empty<Message>();
        return items.Select(m => new Message(
            m.Id ?? "",
            m.FromEmail ?? m.From ?? "",
            m.Subject ?? "",
            DateTimeOffset.Parse(m.ReceivedAt ?? DateTime.UtcNow.ToString("yyyy-MM-dd HH:mm:ss"))
        )).ToList().AsReadOnly();
    }

    public async Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default)
    {
        await EnsureCSRFAsync(ct);
        var html = await _httpClient.GetAsync(BASE + "/view/" + messageId, headers =>
        {
            headers.TryAddWithoutValidation("X-CSRF-TOKEN", _csrf);
            headers.TryAddWithoutValidation("Referer", $"{BASE}/");
        }, ct);

        var text = System.Text.RegularExpressions.Regex.Replace(html, "<[^>]+>", "")
            .Replace("\n", " ").Trim();
        text = System.Text.RegularExpressions.Regex.Replace(text, @"\s+", " ");

        var sender = ExtractRegex(html, @"From:\s*([^<\n]+)", "unknown");
        var subject = ExtractRegex(html, @"Subject:\s*([^<\n]+)", "(no subject)");
        var dateStr = System.Text.RegularExpressions.Regex.Match(html, @"(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})");
        var date = dateStr.Success ? DateTimeOffset.Parse(dateStr.Groups[1].Value) : DateTimeOffset.UtcNow;

        return new MessageDetail(messageId, sender, subject, date, text, html, new List<JsonElement>());
    }

    public Task<bool> DeleteEmailAsync(string email, CancellationToken ct = default)
    {
        return Task.FromResult(true);
    }

    public async Task<Message?> WaitForEmailAsync(string email, TimeSpan timeout, TimeSpan interval, CancellationToken ct = default)
    {
        var end = DateTime.UtcNow + timeout;
        while (DateTime.UtcNow < end)
        {
            var inbox = await GetInboxAsync(email, ct);
            if (inbox.Count > 0) return inbox[0];
            await Task.Delay(interval, ct);
        }
        return null;
    }

    private static string ExtractRegex(string html, string pattern, string fallback)
    {
        var m = System.Text.RegularExpressions.Regex.Match(html, pattern);
        return m.Success ? m.Groups[1].Value.Trim() : fallback;
    }

    private class OneSecMailItem
    {
        [JsonPropertyName("id")]
        public string? Id { get; set; }
        [JsonPropertyName("from")]
        public string? From { get; set; }
        [JsonPropertyName("from_email")]
        public string? FromEmail { get; set; }
        [JsonPropertyName("subject")]
        public string? Subject { get; set; }
        [JsonPropertyName("receivedAt")]
        public string? ReceivedAt { get; set; }
    }
}