using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using TempMail.Exceptions;
using TempMail.Models;

namespace TempMail.Providers;

public sealed class MailTmProvider(HttpClient httpClient) : ITempMailProvider
{
    private const string BaseUrl = "https://api.mail.tm";
    private static readonly JsonSerializerOptions JsonOpts = new() { PropertyNameCaseInsensitive = true };

    private string? _token;
    private string? _accountId;
    private string? _password;

public async Task<string> GenerateEmailAsync(CancellationToken ct = default)
{
    var domains = await httpClient.GetFromJsonAsync<MailTmPage<MailTmDomain>>($"{BaseUrl}/domains", JsonOpts, ct)
        ?? throw new TempMailException("Failed to fetch domains.");

    var domain = domains.HydraMember?.FirstOrDefault()?.DomainName
        ?? throw new TempMailException("No domain available.");

    var address = $"temp_{Guid.NewGuid().ToString("N")[..8]}@{domain}";
    _password = Guid.NewGuid().ToString("N");

    var accountPayload = new { address, password = _password };
    var createRes = await httpClient.PostAsJsonAsync($"{BaseUrl}/accounts", accountPayload, JsonOpts, ct);
    if (!createRes.IsSuccessStatusCode)
        throw new TempMailException($"Failed to create account: {createRes.StatusCode}");

    var account = await createRes.Content.ReadFromJsonAsync<MailTmAccount>(JsonOpts, ct)
        ?? throw new TempMailException("Failed to parse account.");
    _accountId = account.Id;

    // Login to get token
    var loginPayload = new { address, password = _password };
    var loginRes = await httpClient.PostAsJsonAsync($"{BaseUrl}/token", loginPayload, JsonOpts, ct);
    if (!loginRes.IsSuccessStatusCode)
        throw new TempMailException($"Login failed: {loginRes.StatusCode}");

    var tokenData = await loginRes.Content.ReadFromJsonAsync<MailTmToken>(JsonOpts, ct)
        ?? throw new TempMailException("Failed to parse token.");
    _token = tokenData.Token;
    httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", _token);

    return address;
}

    public async Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default)
    {
        EnsureAuth();
        var messages = await httpClient.GetFromJsonAsync<MailTmPage<MailTmMessage>>($"{BaseUrl}/messages", JsonOpts, ct)
            ?? throw new TempMailException("Failed to fetch inbox.");

        return messages.HydraMember?.Select(m => new Message(
            m.Id ?? "",
            m.From?.Address ?? "",
            m.Subject ?? "",
            m.CreatedAt ?? DateTimeOffset.MinValue
        )).ToList() ?? [];
    }

    public async Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default)
    {
        EnsureAuth();
        var m = await httpClient.GetFromJsonAsync<MailTmMessageDetail>($"{BaseUrl}/messages/{messageId}", JsonOpts, ct)
            ?? throw new NotFoundException($"Message {messageId} not found.");

        var attachments = m.Attachments?.Select(a => JsonSerializer.SerializeToElement(a, JsonOpts)).ToList() ?? [];

        return new MessageDetail(
            m.Id ?? "",
            m.From?.Address ?? "",
            m.Subject ?? "",
            m.CreatedAt ?? DateTimeOffset.MinValue,
            m.Text ?? "",
            m.Html ?? "",
            attachments
        );
    }

    public async Task<bool> DeleteEmailAsync(string email, CancellationToken ct = default)
    {
        if (_accountId is null) return false;
        var res = await httpClient.DeleteAsync($"{BaseUrl}/accounts/{_accountId}", ct);
        return res.IsSuccessStatusCode;
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

    private void EnsureAuth()
    {
        if (_token is null) throw new TempMailException("Not authenticated. Call GenerateEmailAsync first.");
    }

    private sealed class MailTmDomain
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("domain")] public string? DomainName { get; set; }
    }

    private sealed class MailTmAccount
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("address")] public string? Address { get; set; }
    }

    private sealed class MailTmToken
    {
        [JsonPropertyName("token")] public string? Token { get; set; }
    }

    private sealed class MailTmAddress
    {
        [JsonPropertyName("address")] public string? Address { get; set; }
    }

    private class MailTmMessage
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("from")] public MailTmAddress? From { get; set; }
        [JsonPropertyName("subject")] public string? Subject { get; set; }
        [JsonPropertyName("createdAt")] public DateTimeOffset? CreatedAt { get; set; }
    }

    private class MailTmMessageDetail : MailTmMessage
    {
        [JsonPropertyName("text")] public string? Text { get; set; }
        [JsonPropertyName("html")] public string? Html { get; set; }
        [JsonPropertyName("attachments")] public object[]? Attachments { get; set; }
    }

    private sealed class MailTmPage<T>
    {
        [JsonPropertyName("hydra:member")] public T[]? HydraMember { get; set; }
    }
}
