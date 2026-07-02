using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using TempMail.Exceptions;
using TempMail.Models;

namespace TempMail.Providers;

public sealed class TempmailcProvider(HttpClient httpClient) : ITempMailProvider
{
    private const string BaseUrl = "https://tempmailc.com/api/v1";
    private static readonly JsonSerializerOptions JsonOpts = new() { PropertyNameCaseInsensitive = true };

    private string? _email;

    public async Task<string> GenerateEmailAsync(CancellationToken ct = default)
    {
        var data = await httpClient.GetFromJsonAsync<TempmailcNew>($"{BaseUrl}/new", JsonOpts, ct)
            ?? throw new TempMailException("Failed to create email.");
        if (!data.Ok) throw new TempMailException("API returned not ok.");
        _email = data.Email ?? throw new TempMailException("No email in response.");
        return _email;
    }

    public async Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default)
    {
        var data = await httpClient.GetFromJsonAsync<TempmailcInbox>($"{BaseUrl}/inbox?email={Uri.EscapeDataString(email)}", JsonOpts, ct)
            ?? throw new TempMailException("Failed to fetch inbox.");
        return (data.Messages ?? []).Select(m => new Message(
            m.Id ?? m.MsgId ?? "",
            m.From ?? m.FromMail ?? "",
            m.Subject ?? "",
            ParseDate(m.Date ?? m.Time)
        )).ToList();
    }

    public async Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default)
    {
        if (_email is null) throw new TempMailException("No email. Call GenerateEmailAsync first.");
        var data = await httpClient.GetFromJsonAsync<TempmailcMessageDetail>(
            $"{BaseUrl}/message?msg_id={Uri.EscapeDataString(messageId)}&email={Uri.EscapeDataString(_email)}", JsonOpts, ct)
            ?? throw new NotFoundException($"Message {messageId} not found.");
        return new MessageDetail(
            data.Id ?? messageId,
            data.From ?? data.FromMail ?? "",
            data.Subject ?? "",
            ParseDate(data.Date ?? data.Time),
            data.Text ?? data.BodyText ?? "",
            data.Html ?? data.BodyHtml ?? "",
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

    private static DateTimeOffset ParseDate(string? s) =>
        !string.IsNullOrEmpty(s) && DateTimeOffset.TryParse(s, out var d) ? d : DateTimeOffset.UtcNow;

    private sealed class TempmailcNew
    {
        [JsonPropertyName("ok")] public bool Ok { get; set; }
        [JsonPropertyName("email")] public string? Email { get; set; }
    }

    private sealed class TempmailcInbox
    {
        [JsonPropertyName("messages")] public List<TempmailcMessage>? Messages { get; set; }
    }

    private class TempmailcMessage
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("msg_id")] public string? MsgId { get; set; }
        [JsonPropertyName("from")] public string? From { get; set; }
        [JsonPropertyName("from_mail")] public string? FromMail { get; set; }
        [JsonPropertyName("subject")] public string? Subject { get; set; }
        [JsonPropertyName("date")] public string? Date { get; set; }
        [JsonPropertyName("time")] public string? Time { get; set; }
    }

    private sealed class TempmailcMessageDetail : TempmailcMessage
    {
        [JsonPropertyName("text")] public string? Text { get; set; }
        [JsonPropertyName("body_text")] public string? BodyText { get; set; }
        [JsonPropertyName("html")] public string? Html { get; set; }
        [JsonPropertyName("body_html")] public string? BodyHtml { get; set; }
    }
}
