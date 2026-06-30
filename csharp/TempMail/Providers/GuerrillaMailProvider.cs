using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using TempMail.Exceptions;
using TempMail.Models;

namespace TempMail.Providers;

public sealed class GuerrillaMailProvider : ITempMailProvider
{
    private readonly HttpClient _httpClient;
    private const string BaseUrl = "https://api.guerrillamail.com/ajax.php";
    private static readonly JsonSerializerOptions JsonOpts = new() { PropertyNameCaseInsensitive = true };
    private string? _sid;

    public GuerrillaMailProvider(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<string> GenerateEmailAsync(CancellationToken ct = default)
    {
        var url = $"{BaseUrl}?f=get_email_address&lang=en";
        var res = await _httpClient.GetFromJsonAsync<GuerrillaInit>(url, JsonOpts, ct)
            ?? throw new TempMailException("Failed to get email address.");

        _sid = res.SidToken;
        return res.EmailAddr ?? throw new TempMailException("No email address returned.");
    }

    public async Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default)
    {
        EnsureSession();
        var url = $"{BaseUrl}?f=get_email_list&offset=0&sid_token={_sid}";
        var res = await _httpClient.GetFromJsonAsync<GuerrillaInbox>(url, JsonOpts, ct)
            ?? throw new TempMailException("Failed to fetch inbox.");

        return res.List?.Select(m => new Message(
            m.MailId?.ToString() ?? "",
            m.MailFrom ?? "",
            m.MailSubject ?? "",
            DateTimeOffset.FromUnixTimeSeconds(m.MailTimestamp ?? 0)
        )).ToList() ?? [];
    }

    public async Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default)
    {
        EnsureSession();
        var url = $"{BaseUrl}?f=fetch_email&email_id={messageId}&sid_token={_sid}";
        var m = await _httpClient.GetFromJsonAsync<GuerrillaMessage>(url, JsonOpts, ct)
            ?? throw new NotFoundException($"Message {messageId} not found.");

        return new MessageDetail(
            m.MailId?.ToString() ?? "",
            m.MailFrom ?? "",
            m.MailSubject ?? "",
            DateTimeOffset.FromUnixTimeSeconds(m.MailTimestamp ?? 0),
            m.MailBody ?? "",
            m.MailBody ?? "", // GuerrillaMail doesn't separate text/html
            []
        );
    }

    public async Task<bool> DeleteEmailAsync(string email, CancellationToken ct = default)
    {
        EnsureSession();
        var url = $"{BaseUrl}?f=forget_me&sid_token={_sid}";
        await _httpClient.DeleteAsync(url, ct);
        return true;
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

    private void EnsureSession()
    {
        if (_sid is null) throw new TempMailException("No session. Call GenerateEmailAsync first.");
    }

    private sealed class GuerrillaInit
    {
        [JsonPropertyName("email_addr")] public string? EmailAddr { get; set; }
        [JsonPropertyName("sid_token")] public string? SidToken { get; set; }
    }

    private sealed class GuerrillaInbox
    {
        [JsonPropertyName("list")] public GuerrillaMessage[]? List { get; set; }
    }

    private sealed class GuerrillaMessage
    {
        [JsonPropertyName("mail_id")] public long? MailId { get; set; }
        [JsonPropertyName("mail_from")] public string? MailFrom { get; set; }
        [JsonPropertyName("mail_subject")] public string? MailSubject { get; set; }
        [JsonPropertyName("mail_body")] public string? MailBody { get; set; }
        [JsonPropertyName("mail_timestamp")] public long? MailTimestamp { get; set; }
    }
}
