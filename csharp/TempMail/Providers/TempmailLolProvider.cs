using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using TempMail.Exceptions;
using TempMail.Models;

namespace TempMail.Providers;

public sealed class TempmailLolProvider(HttpClient httpClient) : ITempMailProvider
{
    private const string BaseUrl = "https://api.tempmail.lol/v2";
    private static readonly JsonSerializerOptions JsonOpts = new() { PropertyNameCaseInsensitive = true };

    private string? _token;
    private string? _email;

    public async Task<string> GenerateEmailAsync(CancellationToken ct = default)
    {
        var createRes = await httpClient.PostAsJsonAsync($"{BaseUrl}/inbox/create", new { }, JsonOpts, ct);
        var data = await createRes.Content.ReadFromJsonAsync<TempmailLolCreate>(JsonOpts, ct)
            ?? throw new TempMailException("Failed to parse create response.");
        _email = data.Address ?? throw new TempMailException("Missing address in response.");
        _token = data.Token ?? throw new TempMailException("Missing token in response.");
        return _email;
    }

    public async Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default)
    {
        if (_token is null) throw new TempMailException("No token. Call GenerateEmailAsync first.");
        var inbox = await httpClient.GetFromJsonAsync<TempmailLolInbox>($"{BaseUrl}/inbox?token={Uri.EscapeDataString(_token)}", JsonOpts, ct)
            ?? throw new TempMailException("Failed to fetch inbox.");
        if (inbox.Expired) throw new TempMailException("Token expired.");
        return (inbox.Emails ?? []).Select(e => new Message(
            GetIdOrUid(e.RawId, e.Id, e.Uid),
            e.From ?? e.Sender ?? "",
            e.Subject ?? "",
            ParseLolDate(e.Date, e.CreatedAt)
        )).ToList();
    }

    public async Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default)
    {
        if (_token is null) throw new TempMailException("No token. Call GenerateEmailAsync first.");
        // tempmail.lol returns full emails in the inbox response
        var inbox = await httpClient.GetFromJsonAsync<TempmailLolInbox>($"{BaseUrl}/inbox?token={Uri.EscapeDataString(_token)}", JsonOpts, ct)
            ?? throw new TempMailException("Failed to fetch inbox.");
        var e = (inbox.Emails ?? []).FirstOrDefault(x => GetIdOrUid(x.RawId, x.Id, x.Uid) == messageId)
            ?? throw new NotFoundException($"Message {messageId} not found.");
        return new MessageDetail(
            messageId,
            e.From ?? e.Sender ?? "",
            e.Subject ?? "",
            ParseLolDate(e.Date, e.CreatedAt),
            e.Body ?? e.Text ?? "",
            e.Html ?? "",
            []
        );
    }

    public Task<bool> DeleteEmailAsync(string email, CancellationToken ct = default)
    {
        _token = null;
        _email = null;
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

    private static DateTimeOffset ParseLolDate(JsonElement dateEl, JsonElement createdAtEl)
    {
        var el = dateEl.ValueKind != JsonValueKind.Undefined ? dateEl : createdAtEl;
        if (el.ValueKind == JsonValueKind.Number)
        {
            var val = el.GetInt64();
            if (val > 1e11) // milliseconds
            {
                return DateTimeOffset.FromUnixTimeMilliseconds(val);
            }
            return DateTimeOffset.FromUnixTimeSeconds(val);
        }
        if (el.ValueKind == JsonValueKind.String)
        {
            var s = el.GetString();
            if (!string.IsNullOrEmpty(s) && DateTimeOffset.TryParse(s, out var d)) return d;
        }
        return DateTimeOffset.UtcNow;
    }

    private static string GetIdOrUid(string? rawId, JsonElement idEl, JsonElement uidEl)
    {
        if (!string.IsNullOrEmpty(rawId)) return rawId;
        string id = GetStringOrNumber(idEl);
        if (string.IsNullOrEmpty(id) || id == "null")
        {
            id = GetStringOrNumber(uidEl);
        }
        return id;
    }

    private static string GetStringOrNumber(JsonElement el)
    {
        return el.ValueKind switch
        {
            JsonValueKind.Number => el.GetInt64().ToString(),
            JsonValueKind.String => el.GetString() ?? "",
            _ => ""
        };
    }

    private sealed class TempmailLolCreate
    {
        [JsonPropertyName("address")] public string? Address { get; set; }
        [JsonPropertyName("token")] public string? Token { get; set; }
    }

    private sealed class TempmailLolInbox
    {
        [JsonPropertyName("emails")] public List<TempmailLolEmail>? Emails { get; set; }
        [JsonPropertyName("expired")] public bool Expired { get; set; }
    }

    private sealed class TempmailLolEmail
    {
        [JsonPropertyName("_id")] public string? RawId { get; set; }
        [JsonPropertyName("id")] public JsonElement Id { get; set; }
        [JsonPropertyName("uid")] public JsonElement Uid { get; set; }
        [JsonPropertyName("from")] public string? From { get; set; }
        [JsonPropertyName("sender")] public string? Sender { get; set; }
        [JsonPropertyName("subject")] public string? Subject { get; set; }
        [JsonPropertyName("date")] public JsonElement Date { get; set; }
        [JsonPropertyName("createdAt")] public JsonElement CreatedAt { get; set; }
        [JsonPropertyName("body")] public string? Body { get; set; }
        [JsonPropertyName("text")] public string? Text { get; set; }
        [JsonPropertyName("html")] public string? Html { get; set; }
    }
}
