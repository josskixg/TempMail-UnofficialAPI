using System.Net;
using System.Text.RegularExpressions;
using TempMail.Exceptions;
using TempMail.Models;

namespace TempMail.Providers;

public sealed class YOPmailProvider : ITempMailProvider
{
    private readonly HttpClient _httpClient;
    private string? _yp;
    private string? _yj;
    private string? _v;
    private string? _username;

    private static readonly Regex YpRegex = new(@"name=""yp"" id=""yp"" value=""([^""]+)""", RegexOptions.Compiled);
    private static readonly Regex VersionRegex = new(@"/ver/([0-9.]+)/webmail\.js", RegexOptions.Compiled);
    private static readonly Regex YjRegex = new(@"value\+'\&yj\=([0-9a-zA-Z]*)\&v\='", RegexOptions.Compiled);
    private static readonly Regex MailRowRegex = new(@"id=""m([^""]+)"".*?<span[^>]*class=""lmh"">([^<]+)</span>.*?<span[^>]*class=""lms"">([^<]+)</span>.*?<span[^>]*class=""lmd"">([^<]+)</span>", RegexOptions.Compiled | RegexOptions.Singleline);
    private static readonly Regex MailContentRegex = new(@"<div[^>]*id=""mail""[^>]*>(.*?)</div>", RegexOptions.Compiled | RegexOptions.Singleline | RegexOptions.IgnoreCase);

    public YOPmailProvider(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<string> GenerateEmailAsync(CancellationToken ct = default)
    {
        _username = GenerateRandomString(10);
        var email = $"{_username}@yopmail.com";

        // Step 1: GET https://yopmail.com/en/ -> extract yp and v
        var html1 = await _httpClient.GetAsync("https://yopmail.com/en/", ct);
        _yp = YpRegex.Match(html1).Groups[1].Value;
        _v = VersionRegex.Match(html1).Groups[1].Value;

        if (string.IsNullOrEmpty(_yp) || string.IsNullOrEmpty(_v))
            throw new TempMailException("Failed to extract initial yp or v tokens.");

        // Step 2: GET https://yopmail.com/en/?login={username} -> extract new yp
        var html2 = await _httpClient.GetAsync($"https://yopmail.com/en/?login={_username}", ct);
        var newyp = YpRegex.Match(html2).Groups[1].Value;
        if (!string.IsNullOrEmpty(newyp)) _yp = newyp;

        // Step 3: POST https://yopmail.com/en/ with form body
        var content = new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["login"] = _username,
            ["id"] = "",
            ["yp"] = _yp
        });
        await _httpClient.PostAsync("https://yopmail.com/en/", content, ct);

        // Step 4: GET https://yopmail.com/ver/{v}/webmail.js -> extract yj
        var js = await _httpClient.GetAsync($"https://yopmail.com/ver/{_v}/webmail.js", ct);
        _yj = YjRegex.Match(js).Groups[1].Value;

        // Step 5: Set cookie ytime = HH:mm
        var ytime = DateTime.UtcNow.ToString("HH:mm");
        _httpClient.Cookies.Add(new Uri("https://yopmail.com"), new Cookie("ytime", ytime));

        return email;
    }

    public async Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default)
    {
        EnsureSession();
        var user = email.Split('@')[0];
        var url = $"https://yopmail.com/en/inbox?login={user}&p=1&d=&ctrl=&yp={_yp}&yj={_yj}&v={_v}&r_c=&id=&ad=0";

        var html = await _httpClient.GetAsync(url, ct);
        var messages = new List<Message>();

        foreach (Match match in MailRowRegex.Matches(html))
        {
            if (match.Success)
            {
                messages.Add(new Message(
                    match.Groups[1].Value,
                    match.Groups[2].Value.Trim(),
                    match.Groups[3].Value.Trim(),
                    ParseDate(match.Groups[4].Value.Trim())
                ));
            }
        }

        return messages;
    }

    public async Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default)
    {
        EnsureSession();
        var url = $"https://yopmail.com/en/mail?b={_username}&id={messageId}&yp={_yp}&yj={_yj}&v={_v}";

        var html = await _httpClient.GetAsync(url, ct);
        var bodyMatch = MailContentRegex.Match(html);
        var body = bodyMatch.Success ? bodyMatch.Groups[1].Value : html;

        // Ponytail: basic extraction, no full HTML parse needed
        return new MessageDetail(
            messageId,
            "",
            "",
            DateTimeOffset.UtcNow,
            StripHtml(body),
            body,
            []
        );
    }

    public Task<bool> DeleteEmailAsync(string email, CancellationToken ct = default)
    {
        // No delete API for YOPmail
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

    private void EnsureSession()
    {
        if (_yp is null || _yj is null || _v is null || _username is null)
            throw new TempMailException("No session. Call GenerateEmailAsync first.");
    }

    private static string GenerateRandomString(int length)
    {
        const string chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        var random = new Random();
        return new string(Enumerable.Repeat(chars, length).Select(s => s[random.Next(s.Length)]).ToArray());
    }

    private static DateTimeOffset ParseDate(string dateStr)
    {
        // Ponytail: YOPmail dates are relative or simple formats, fallback to UtcNow
        if (DateTimeOffset.TryParse(dateStr, out var dto)) return dto;
        return DateTimeOffset.UtcNow;
    }

    private static string StripHtml(string html)
    {
        return Regex.Replace(html, "<[^>]+>", "").Trim();
    }
}
