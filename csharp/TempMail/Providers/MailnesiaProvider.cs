using System.Text.RegularExpressions;
using TempMail.Exceptions;
using TempMail.Models;

namespace TempMail.Providers;

public sealed class MailnesiaProvider : ITempMailProvider
{
    private readonly HttpClient _httpClient;
    private const string BaseUrl = "https://mailnesia.com";

    private string? _username;

    // ponytail: regex HTML parsing — HtmlAgilityPack not available
    private static readonly Regex RowRegex = new(@"<tr[^>]*>(.*?)</tr>", RegexOptions.Compiled | RegexOptions.Singleline);
    private static readonly Regex CellRegex = new(@"<td[^>]*>(.*?)</td>", RegexOptions.Compiled | RegexOptions.Singleline);
    private static readonly Regex HrefRegex = new(@"href=""([^""]*)""", RegexOptions.Compiled);
    private static readonly Regex MessageDivRegex = new(@"<div[^>]*id=""message""[^>]*>(.*?)</div>", RegexOptions.Compiled | RegexOptions.Singleline | RegexOptions.IgnoreCase);

    public MailnesiaProvider(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public Task<string> GenerateEmailAsync(CancellationToken ct = default)
    {
        _username = RandomString(10);
        return Task.FromResult($"{_username}@mailnesia.com");
    }

    public async Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default)
    {
        var username = email.Contains('@') ? email.Split('@')[0] : email;
        var headers = GetHeadersWithIPRotation();
        var html = await _httpClient.GetAsync($"{BaseUrl}/mailbox/{username}", h =>
        {
            foreach (var kv in headers)
            {
                h.TryAddWithoutValidation(kv.Key, kv.Value);
            }
        }, ct);
        var messages = new List<Message>();
        foreach (Match row in RowRegex.Matches(html))
        {
            var cells = CellRegex.Matches(row.Groups[1].Value);
            if (cells.Count < 3) continue;
            var sender = StripHtml(cells[0].Groups[1].Value);
            var subject = StripHtml(cells[1].Groups[1].Value);
            var timeStr = StripHtml(cells[2].Groups[1].Value);
            var msgId = "";
            var hrefMatch = HrefRegex.Match(row.Groups[1].Value);
            if (hrefMatch.Success)
            {
                var href = hrefMatch.Groups[1].Value;
                msgId = href.Contains('/') ? href.TrimEnd('/').Split('/').Last() : "";
            }
            if (string.IsNullOrEmpty(sender) && string.IsNullOrEmpty(subject)) continue;
            messages.Add(new Message(msgId, sender, subject, ParseDate(timeStr)));
        }
        return messages;
    }

    public async Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default)
    {
        if (_username is null) throw new TempMailException("No email. Call GenerateEmailAsync first.");
        var headers = GetHeadersWithIPRotation();
        var html = await _httpClient.GetAsync($"{BaseUrl}/mailbox/{_username}/{messageId}", h =>
        {
            foreach (var kv in headers)
            {
                h.TryAddWithoutValidation(kv.Key, kv.Value);
            }
        }, ct);
        var msgMatch = MessageDivRegex.Match(html);
        var bodyHtml = msgMatch.Success ? msgMatch.Groups[1].Value : html;
        var bodyText = StripHtml(bodyHtml);
        return new MessageDetail(messageId, "", "", DateTimeOffset.UtcNow, bodyText, bodyHtml, []);
    }

    public Task<bool> DeleteEmailAsync(string email, CancellationToken ct = default)
    {
        _username = null;
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

    private static string StripHtml(string html) => Regex.Replace(html, @"<[^>]+>", "").Trim();

    private static DateTimeOffset ParseDate(string? s) =>
        !string.IsNullOrEmpty(s) && DateTimeOffset.TryParse(s, out var d) ? d : DateTimeOffset.UtcNow;

    private static string RandomString(int length)
    {
        const string chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        return new string(Enumerable.Repeat(chars, length).Select(s => s[Random.Shared.Next(s.Length)]).ToArray());
    }

    private static string GenerateRandomIp()
    {
        var r = Random.Shared;
        return $"{r.Next(1, 255)}.{r.Next(256)}.{r.Next(256)}.{r.Next(1, 255)}";
    }

    private static Dictionary<string, string> GetHeadersWithIPRotation()
    {
        var ip = GenerateRandomIp();
        return new Dictionary<string, string>
        {
            { "X-Forwarded-For", ip },
            { "X-Real-IP", ip },
            { "CF-Connecting-IP", ip },
            { "True-Client-IP", ip }
        };
    }
}
