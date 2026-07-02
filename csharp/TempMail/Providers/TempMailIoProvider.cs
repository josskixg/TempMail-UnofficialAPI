using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using TempMail.Exceptions;
using TempMail.Models;

namespace TempMail.Providers;

public sealed class TempMailIoProvider(HttpClient httpClient) : ITempMailProvider
{
    private const string BaseUrl = "https://api.internal.temp-mail.io/api/v3";
    private static readonly JsonSerializerOptions JsonOpts = new() { PropertyNameCaseInsensitive = true };

    private string? _email;
    private string? _token;

    public async Task<string> GenerateEmailAsync(CancellationToken ct = default)
    {
        var createRes = await httpClient.PostAsJsonAsync($"{BaseUrl}/email/new", new { min_name_length = 6, max_name_length = 12 }, JsonOpts, ct);
        var data = await createRes.Content.ReadFromJsonAsync<TempMailIoNew>(JsonOpts, ct)
            ?? throw new TempMailException("Failed to parse create response.");
        _email = data.Email ?? throw new TempMailException("Missing email in response.");
        _token = data.Token;
        if (_token is not null)
            httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", _token);
        return _email;
    }

    public async Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default)
    {
        var items = await FetchMessages(email, ct);
        return items.Select(item => new Message(
            GetStr(item, "id") ?? GetStr(item, "uid") ?? "",
            GetSender(item),
            GetStr(item, "subject") ?? "",
            ParseDate(GetStr(item, "created_at") ?? GetStr(item, "date"))
        )).ToList();
    }

    public async Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default)
    {
        if (_email is null) throw new TempMailException("No email. Call GenerateEmailAsync first.");
        var items = await FetchMessages(_email, ct);
        var item = items.FirstOrDefault(e => (GetStr(e, "id") ?? GetStr(e, "uid") ?? "") == messageId);
        if (item.ValueKind == JsonValueKind.Undefined)
            throw new NotFoundException($"Message {messageId} not found.");
        return new MessageDetail(
            messageId,
            GetSender(item),
            GetStr(item, "subject") ?? "",
            ParseDate(GetStr(item, "created_at") ?? GetStr(item, "date")),
            GetStr(item, "body_text") ?? GetStr(item, "text") ?? "",
            GetStr(item, "body_html") ?? GetStr(item, "html") ?? "",
            GetAttachments(item)
        );
    }

    public Task<bool> DeleteEmailAsync(string email, CancellationToken ct = default)
    {
        _token = null;
        _email = null;
        httpClient.DefaultRequestHeaders.Authorization = null;
        return Task.FromResult(true);
    }

    public async Task<Message?> WaitForEmailAsync(string email, TimeSpan timeout, TimeSpan interval, CancellationToken ct = default)
    {
        using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        cts.CancelAfter(timeout);
        while (!cts.Token.IsCancellationRequested)
        {
            var inbox = await GetInboxAsync(email, cts.Token);
            if (inbox.Count > 0) return inbox[0];
            await Task.Delay(interval, cts.Token);
        }
        return null;
    }

    // ponytail: response may be a bare array or {messages:[...]}; parse raw to handle both shapes
    private async Task<List<JsonElement>> FetchMessages(string email, CancellationToken ct)
    {
        var raw = await httpClient.GetAsync($"{BaseUrl}/email/{email}/messages", ct);
        using var doc = JsonDocument.Parse(raw);
        var root = doc.RootElement;
        if (root.ValueKind == JsonValueKind.Array)
            return root.EnumerateArray().ToList();
        if (root.TryGetProperty("messages", out var m))
            return m.EnumerateArray().ToList();
        if (root.TryGetProperty("mails", out var ml))
            return ml.EnumerateArray().ToList();
        return [];
    }

    private static string? GetStr(JsonElement e, string name) =>
        e.TryGetProperty(name, out var p) && p.ValueKind == JsonValueKind.String ? p.GetString() : null;

    private static string GetSender(JsonElement item)
    {
        if (!item.TryGetProperty("from", out var f)) return "";
        if (f.ValueKind == JsonValueKind.String) return f.GetString() ?? "";
        if (f.TryGetProperty("address", out var a)) return a.GetString() ?? "";
        if (f.TryGetProperty("name", out var n)) return n.GetString() ?? "";
        return "";
    }

    private static List<JsonElement> GetAttachments(JsonElement item)
    {
        var list = new List<JsonElement>();
        if (!item.TryGetProperty("attachments", out var atts)) return list;
        foreach (var a in atts.EnumerateArray())
        {
            var obj = new
            {
                filename = GetStr(a, "filename") ?? "",
                content_type = GetStr(a, "content_type") ?? "",
                size = a.TryGetProperty("size", out var sz) && sz.TryGetInt32(out var s) ? s : 0
            };
            list.Add(JsonSerializer.SerializeToElement(obj));
        }
        return list;
    }

    private static DateTimeOffset ParseDate(string? s) =>
        !string.IsNullOrEmpty(s) && DateTimeOffset.TryParse(s, out var d) ? d : DateTimeOffset.UtcNow;

    private sealed class TempMailIoNew
    {
        [JsonPropertyName("email")] public string? Email { get; set; }
        [JsonPropertyName("token")] public string? Token { get; set; }
    }
}
