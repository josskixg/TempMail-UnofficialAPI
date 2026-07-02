using System.Text.Json;
using System.Text.RegularExpressions;
using TempMail.Exceptions;
using TempMail.Models;

namespace TempMail.Providers;

public sealed class TenMinuteMailProvider(HttpClient httpClient) : ITempMailProvider
{
    private const string BaseUrl = "https://10minutemail.net";
    private string? _email;

    private static string DecodeCfEmail(string hex)
    {
        if (hex.Length < 4) return "";
        try
        {
            int k = Convert.ToInt32(hex[..2], 16);
            var sb = new System.Text.StringBuilder();
            for (int i = 2; i < hex.Length; i += 2)
            {
                int val = Convert.ToInt32(hex.Substring(i, 2), 16);
                sb.Append((char)(val ^ k));
            }
            return sb.ToString();
        }
        catch
        {
            return "";
        }
    }

    private static string StripHtml(string html)
    {
        return Regex.Replace(html, "<[^>]+>", "").Trim();
    }

    public async Task<string> GenerateEmailAsync(CancellationToken ct = default)
    {
        var html = await httpClient.GetAsync($"{BaseUrl}/", ct);
        var match = Regex.Match(html, "id=\"fe_text\"[^>]*value=\"([^\"]+)\"", RegexOptions.IgnoreCase);
        if (!match.Success)
        {
            throw new TempMailException("No address in response.");
        }
        _email = match.Groups[1].Value.Trim();
        return _email;
    }

    public async Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default)
    {
        var html = await httpClient.GetAsync($"{BaseUrl}/mailbox.ajax.php", ct);
        
        var rows = Regex.Matches(html, "<tr[^>]*>(.*?)</tr>", RegexOptions.Singleline | RegexOptions.IgnoreCase);
        if (rows.Count <= 1)
        {
            return Array.Empty<Message>();
        }

        var messages = new List<Message>();
        // Skip header row
        for (int i = 1; i < rows.Count; i++)
        {
            var rowHtml = rows[i].Groups[1].Value;
            var cells = Regex.Matches(rowHtml, "<td[^>]*>(.*?)</td>", RegexOptions.Singleline | RegexOptions.IgnoreCase);
            if (cells.Count < 3) continue;

            // Sender
            var cell0 = cells[0].Groups[1].Value;
            var cfMatch = Regex.Match(cell0, "data-cfemail=\"([^\"]+)\"", RegexOptions.IgnoreCase);
            var sender = cfMatch.Success ? DecodeCfEmail(cfMatch.Groups[1].Value) : StripHtml(cell0);

            var subject = StripHtml(cells[1].Groups[1].Value);

            // Date
            var cell2 = cells[2].Groups[1].Value;
            var titleMatch = Regex.Match(cell2, "title=\"([^\"]+)\"", RegexOptions.IgnoreCase);
            var dateStr = titleMatch.Success ? titleMatch.Groups[1].Value : StripHtml(cell2);

            if (!dateStr.Contains("UTC", StringComparison.OrdinalIgnoreCase))
            {
                dateStr += " UTC";
            }

            var date = DateTimeOffset.TryParse(dateStr, out var d) ? d : DateTimeOffset.UtcNow;

            // Mid
            var midMatch = Regex.Match(rowHtml, "mid=([^'&\"\\s>]+)", RegexOptions.IgnoreCase);
            if (!midMatch.Success) continue;
            var id = midMatch.Groups[1].Value;

            messages.Add(new Message(id, sender, subject, date));
        }

        return messages;
    }

    public async Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default)
    {
        // Parse composite messageId if it has a colon
        var mid = messageId.Contains(':') ? messageId.Split(':')[0] : messageId;

        var html = await httpClient.GetAsync($"{BaseUrl}/readmail.html?mid={Uri.EscapeDataString(mid)}", ct);

        var bodyMatch = Regex.Match(html, "class=\"mailinhtml\"[^>]*>(.*?)<div[^>]*style=\"clear:both;\"", RegexOptions.Singleline | RegexOptions.IgnoreCase);
        if (!bodyMatch.Success)
        {
            throw new NotFoundException($"Message {messageId} not found.");
        }

        var bodyHtml = bodyMatch.Groups[1].Value.Trim();

        // Decode CF email obfuscation in body
        bodyHtml = Regex.Replace(bodyHtml, "<(a|span)[^>]*class=\"__cf_email__\"[^>]*data-cfemail=\"([^\"]+)\"[^>]*>.*?<\\/\\1>", m => DecodeCfEmail(m.Groups[2].Value), RegexOptions.IgnoreCase);
        bodyHtml = Regex.Replace(bodyHtml, "href=\"\\/cdn-cgi\\/l\\/email-protection#([^\"]+)\"", m => $"href=\"mailto:{DecodeCfEmail(m.Groups[1].Value)}\"", RegexOptions.IgnoreCase);

        var bodyText = StripHtml(bodyHtml);

        // Subject
        var subject = "";
        var subMatch = Regex.Match(html, "<div class=\"mail_header\">.*?<h2[^>]*>(.*?)</h2>", RegexOptions.Singleline | RegexOptions.IgnoreCase);
        if (subMatch.Success)
        {
            subject = StripHtml(subMatch.Groups[1].Value);
        }

        // Sender
        var sender = "";
        var fromMatch = Regex.Match(html, "<span class=\"mail_from\">(.*?)</span>", RegexOptions.Singleline | RegexOptions.IgnoreCase);
        if (fromMatch.Success)
        {
            var fromHtml = fromMatch.Groups[1].Value;
            var cfFrom = Regex.Match(fromHtml, "data-cfemail=\"([^\"]+)\"", RegexOptions.IgnoreCase);
            sender = cfFrom.Success ? DecodeCfEmail(cfFrom.Groups[1].Value) : StripHtml(fromHtml);
        }

        return new MessageDetail(
            mid,
            sender,
            subject,
            DateTimeOffset.UtcNow,
            bodyText,
            bodyHtml,
            new List<JsonElement>()
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
}
