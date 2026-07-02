using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using TempMail.Exceptions;
using TempMail.Models;

namespace TempMail.Providers;

public sealed class ZoromailProvider(HttpClient httpClient) : ITempMailProvider
{
    private const string BaseUrl = "https://zoromail.com/public_api.php/v1";
    private static readonly JsonSerializerOptions JsonOpts = new() { PropertyNameCaseInsensitive = true };

    private string? _email;

    public async Task<string> GenerateEmailAsync(CancellationToken ct = default)
    {
        var domainsResp = await httpClient.GetFromJsonAsync<ZoromailResponse<List<string>>>($"{BaseUrl}/domains", JsonOpts, ct)
            ?? throw new TempMailException("Failed to fetch domains.");
        var domains = Unwrap(domainsResp, "Failed to fetch domains.");
        if (domains.Count == 0) throw new TempMailException("No domains available.");

        var domain = domains[Random.Shared.Next(domains.Count)];
        var username = RandomString(10);
        var createRes = await httpClient.PostAsJsonAsync($"{BaseUrl}/emails", new { username, domain }, JsonOpts, ct);
        var created = await createRes.Content.ReadFromJsonAsync<ZoromailResponse<ZoromailEmail>>(JsonOpts, ct)
            ?? throw new TempMailException("Failed to parse create response.");
        _email = Unwrap(created, "Failed to create email.").Email
            ?? throw new TempMailException("No email in response.");
        return _email;
    }

    public async Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default)
    {
        var resp = await httpClient.GetFromJsonAsync<ZoromailResponse<List<ZoromailMessage>>>($"{BaseUrl}/emails/{email}/messages", JsonOpts, ct)
            ?? throw new TempMailException("Failed to fetch inbox.");
        return (Unwrap(resp, "Failed to fetch inbox.")).Select(m => new Message(
            m.Id ?? "",
            m.From ?? "",
            m.Subject ?? "",
            ParseDate(m.Date)
        )).ToList();
    }

    public async Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default)
    {
        var resp = await httpClient.GetFromJsonAsync<ZoromailResponse<ZoromailMessageDetail>>($"{BaseUrl}/messages/{messageId}", JsonOpts, ct)
            ?? throw new NotFoundException($"Message {messageId} not found.");
        var m = Unwrap(resp, $"Message {messageId} not found.");
        return new MessageDetail(
            m.Id ?? messageId,
            m.From ?? "",
            m.Subject ?? "",
            ParseDate(m.Date),
            m.Text ?? "",
            m.Html ?? "",
            []
        );
    }

    public Task<bool> DeleteEmailAsync(string email, CancellationToken ct = default)
    {
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

    // ponytail: unwrap {success,data,error} envelope; HTTP errors handled by EnsureSuccessStatusCode in HttpClient
    private static T Unwrap<T>(ZoromailResponse<T> r, string err) where T : class
    {
        if (!r.Success) throw new TempMailException(r.Error ?? err);
        return r.Data ?? throw new TempMailException(err);
    }

    private static DateTimeOffset ParseDate(string? s) =>
        !string.IsNullOrEmpty(s) && DateTimeOffset.TryParse(s, out var d) ? d : DateTimeOffset.UtcNow;

    private static string RandomString(int length)
    {
        const string chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        return new string(Enumerable.Repeat(chars, length).Select(s => s[Random.Shared.Next(s.Length)]).ToArray());
    }

    private sealed class ZoromailResponse<T>
    {
        [JsonPropertyName("success")] public bool Success { get; set; }
        [JsonPropertyName("data")] public T? Data { get; set; }
        [JsonPropertyName("error")] public string? Error { get; set; }
    }

    private sealed class ZoromailEmail
    {
        [JsonPropertyName("email")] public string? Email { get; set; }
    }

    private class ZoromailMessage
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("from")] public string? From { get; set; }
        [JsonPropertyName("subject")] public string? Subject { get; set; }
        [JsonPropertyName("date")] public string? Date { get; set; }
    }

    private class ZoromailMessageDetail : ZoromailMessage
    {
        [JsonPropertyName("text")] public string? Text { get; set; }
        [JsonPropertyName("html")] public string? Html { get; set; }
    }
}
