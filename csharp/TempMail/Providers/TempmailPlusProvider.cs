using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using TempMail.Exceptions;
using TempMail.Models;

namespace TempMail.Providers;

public sealed class TempmailPlusProvider(HttpClient httpClient) : ITempMailProvider
{
    private const string BaseUrl = "https://tempmail.plus";
    private const string Domain = "mailto.plus";
    private static readonly JsonSerializerOptions JsonOpts = new() { PropertyNameCaseInsensitive = true };

    private string? _email;

    public Task<string> GenerateEmailAsync(CancellationToken ct = default)
    {
        var username = RandomString(10);
        _email = $"{username}@{Domain}";
        return Task.FromResult(_email);
    }

    public async Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default)
    {
        var data = await httpClient.GetFromJsonAsync<TempmailPlusInbox>($"{BaseUrl}/api/mails?email={Uri.EscapeDataString(email)}", JsonOpts, ct)
            ?? throw new TempMailException("Failed to fetch inbox.");
        if (!data.Result) throw new TempMailException("API returned error.");
        return (data.MailList ?? []).Select(m => new Message(
            GetMailId(m.MailId),
            m.FromMail ?? "",
            m.Subject ?? "",
            ParseDate(m.Time)
        )).ToList();
    }

    public async Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default)
    {
        if (_email is null) throw new TempMailException("No email. Call GenerateEmailAsync first.");
        var data = await httpClient.GetFromJsonAsync<TempmailPlusMailDetail>(
            $"{BaseUrl}/api/mails/{Uri.EscapeDataString(messageId)}?email={Uri.EscapeDataString(_email)}", JsonOpts, ct)
            ?? throw new NotFoundException($"Message {messageId} not found.");
        var resolvedMailId = GetMailId(data.MailId);
        return new MessageDetail(
            string.IsNullOrEmpty(resolvedMailId) ? messageId : resolvedMailId,
            data.FromMail ?? data.From ?? "",
            data.Subject ?? "",
            ParseDate(data.Date),
            data.Text ?? "",
            data.Html ?? "",
            data.Attachments?.Select(a => JsonSerializer.SerializeToElement(a, JsonOpts)).ToList() ?? []
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

    private static string RandomString(int length)
    {
        const string chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        return new string(Enumerable.Repeat(chars, length).Select(s => s[Random.Shared.Next(s.Length)]).ToArray());
    }

    private static string GetMailId(JsonElement el)
    {
        return el.ValueKind switch
        {
            JsonValueKind.Number => el.GetInt64().ToString(),
            JsonValueKind.String => el.GetString() ?? "",
            _ => ""
        };
    }

    private sealed class TempmailPlusInbox
    {
        [JsonPropertyName("result")] public bool Result { get; set; } = true;
        [JsonPropertyName("mail_list")] public List<TempmailPlusMail>? MailList { get; set; }
    }

    private sealed class TempmailPlusMail
    {
        [JsonPropertyName("mail_id")] public JsonElement MailId { get; set; }
        [JsonPropertyName("from_mail")] public string? FromMail { get; set; }
        [JsonPropertyName("subject")] public string? Subject { get; set; }
        [JsonPropertyName("time")] public string? Time { get; set; }
    }

    private sealed class TempmailPlusMailDetail
    {
        [JsonPropertyName("mail_id")] public JsonElement MailId { get; set; }
        [JsonPropertyName("from_mail")] public string? FromMail { get; set; }
        [JsonPropertyName("from")] public string? From { get; set; }
        [JsonPropertyName("subject")] public string? Subject { get; set; }
        [JsonPropertyName("date")] public string? Date { get; set; }
        [JsonPropertyName("text")] public string? Text { get; set; }
        [JsonPropertyName("html")] public string? Html { get; set; }
        [JsonPropertyName("attachments")] public object[]? Attachments { get; set; }
    }
}
