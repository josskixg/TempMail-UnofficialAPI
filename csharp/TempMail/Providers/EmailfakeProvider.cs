using System.Net;
using System.Text.RegularExpressions;
using TempMail.Exceptions;
using TempMail.Models;

namespace TempMail.Providers;

// ponytail: non-sealed base — generator.email and email-temp share identical logic, differ only by URL
public class EmailfakeProvider : ITempMailProvider
{
    protected readonly HttpClient _httpClient;
    protected virtual string BaseUrl => "https://emailfake.com";
    protected virtual string SiteName => "emailfake";

    private string? _email;
    private string? _domain;
    private string? _username;

    // ponytail: regex HTML parsing — HtmlAgilityPack not available; nested </div> may truncate message body
    private static readonly Regex OptionRegex = new(@"<option[^>]*value=""([^""]+)""", RegexOptions.Compiled);
    private static readonly Regex DomainFallbackRegex = new(@">([a-z0-9-]+\.[a-z.]{2,})<", RegexOptions.Compiled);
    private static readonly Regex AnchorRegex = new(@"(<a[^>]*list-group-item[^>]*>)(.*?)(</a>)", RegexOptions.Compiled | RegexOptions.Singleline);
    private static readonly Regex HrefRegex = new(@"href=""([^""]*)""", RegexOptions.Compiled);
    private static readonly Regex InboxFromRegex = new(@"<div[^>]*class=""[^""]*from[^""]*""[^>]*>(.*?)</div>", RegexOptions.Compiled | RegexOptions.Singleline);
    private static readonly Regex InboxSubjRegex = new(@"<div[^>]*class=""[^""]*subj[^""]*""[^>]*>(.*?)</div>", RegexOptions.Compiled | RegexOptions.Singleline);
    private static readonly Regex InboxTimeRegex = new(@"<div[^>]*class=""[^""]*time[^""]*""[^>]*>(.*?)</div>", RegexOptions.Compiled | RegexOptions.Singleline);
    private static readonly Regex MessageDivRegex = new(@"<div[^>]*id=""message""[^>]*>(.*?)</div>", RegexOptions.Compiled | RegexOptions.Singleline | RegexOptions.IgnoreCase);
    private static readonly Regex ReadFromRegex = new(@"<div[^>]*class=""[^""]*from_div[^""]*""[^>]*>(.*?)</div>", RegexOptions.Compiled | RegexOptions.Singleline);
    private static readonly Regex ReadSubjRegex = new(@"<div[^>]*class=""[^""]*subj_div[^""]*""[^>]*>(.*?)</div>", RegexOptions.Compiled | RegexOptions.Singleline);
    private static readonly Regex ReadTimeRegex = new(@"<div[^>]*class=""[^""]*time_div[^""]*""[^>]*>(.*?)</div>", RegexOptions.Compiled | RegexOptions.Singleline);

    public EmailfakeProvider(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<string> GenerateEmailAsync(CancellationToken ct = default)
    {
        var html = await _httpClient.GetAsync($"{BaseUrl}/channel{Random.Shared.Next(1, 10)}/", ct);
        var domains = ParseDomains(html);
        if (domains.Count == 0) throw new TempMailException("No domains found on page.");
        _domain = domains[Random.Shared.Next(domains.Count)];
        _username = RandomString(10);
        _email = $"{_username}@{_domain}";
        // ponytail: Add throws on duplicate surl; re-generation needs a fresh provider instance
        try { _httpClient.Cookies.Add(new Uri(BaseUrl), new Cookie("surl", $"{_domain}/{_username}")); }
        catch (CookieException) { }
        return _email;
    }

    public async Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default)
    {
        EnsureSession();
        var html = await _httpClient.GetAsync($"{BaseUrl}/channel{Random.Shared.Next(1, 10)}/", ct);
        var messages = new List<Message>();
        foreach (Match m in AnchorRegex.Matches(html))
        {
            var openTag = m.Groups[1].Value;
            var inner = m.Groups[2].Value;
            var href = HrefRegex.Match(openTag).Groups[1].Value;
            var msgId = href.Contains('/') ? href.TrimEnd('/').Split('/').Last() : "";
            if (msgId.Length < 10) continue;
            var sender = StripHtml(InboxFromRegex.Match(inner).Groups[1].Value);
            var subject = StripHtml(InboxSubjRegex.Match(inner).Groups[1].Value);
            var timeStr = StripHtml(InboxTimeRegex.Match(inner).Groups[1].Value);
            messages.Add(new Message(msgId, sender, subject, ParseDate(timeStr)));
        }
        return messages;
    }

    public async Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default)
    {
        EnsureSession();
        var html = await _httpClient.GetAsync($"{BaseUrl}/{_domain}/{_username}/{messageId}", ct);
        var msgMatch = MessageDivRegex.Match(html);
        if (!msgMatch.Success) throw new NotFoundException($"Message {messageId} not found.");
        var bodyHtml = msgMatch.Groups[1].Value;
        var bodyText = StripHtml(bodyHtml);
        var sender = StripHtml(ReadFromRegex.Match(html).Groups[1].Value);
        var subject = StripHtml(ReadSubjRegex.Match(html).Groups[1].Value);
        var timeStr = StripHtml(ReadTimeRegex.Match(html).Groups[1].Value);
        return new MessageDetail(messageId, sender, subject, ParseDate(timeStr), bodyText, bodyHtml, []);
    }

    public Task<bool> DeleteEmailAsync(string email, CancellationToken ct = default)
    {
        _email = null;
        _domain = null;
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

    private List<string> ParseDomains(string html)
    {
        var domains = OptionRegex.Matches(html)
            .Cast<Match>()
            .Select(m => m.Groups[1].Value.Trim())
            .Where(v => v.Contains('.') && !v.Contains(' ') && !v.Contains('@'))
            .Distinct()
            .ToList();
        if (domains.Count == 0)
        {
            domains = DomainFallbackRegex.Matches(html)
                .Cast<Match>()
                .Select(m => m.Groups[1].Value)
                .Where(v => !v.Contains(SiteName))
                .Distinct()
                .ToList();
        }
        return domains;
    }

    private void EnsureSession()
    {
        if (_domain is null || _username is null)
            throw new TempMailException("No email generated. Call GenerateEmailAsync first.");
    }

    private static string StripHtml(string html) => Regex.Replace(html, @"<[^>]+>", "").Trim();

    private static DateTimeOffset ParseDate(string? s) =>
        !string.IsNullOrEmpty(s) && DateTimeOffset.TryParse(s, out var d) ? d : DateTimeOffset.UtcNow;

    private static string RandomString(int length)
    {
        const string chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        return new string(Enumerable.Repeat(chars, length).Select(s => s[Random.Shared.Next(s.Length)]).ToArray());
    }
}
