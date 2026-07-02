using System.Net.Http.Json;
using System.Text.Json;
using TempMail.Exceptions;
using TempMail.Models;

namespace TempMail.Providers;

public sealed class NcaoriMailProvider : ITempMailProvider
{
    private const string BASE = "https://www.nca.my.id";
    private static readonly string[] DOMAINS = { "ncaori.my.id", "nca.my.id" };

    private static readonly string[] WORDS = [
        "swift", "crystal", "storm", "frost", "shadow", "ember", "azure",
        "phantom", "silver", "iron", "crimson", "golden", "neo", "cosmic", "lunar",
        "solar", "dark", "light", "void", "flux",
    ];

    private static readonly string[] WORDS2 = [
        "core", "leaf", "forge", "wave", "peak", "gate", "pulse",
        "blade", "shard", "drift", "hive", "node", "edge", "beacon", "nova",
        "storm", "cloud", "moon", "star", "wind",
    ];

    private readonly HttpClient _httpClient;
    private readonly Random _random;

    public NcaoriMailProvider(HttpClient httpClient)
    {
        _httpClient = httpClient;
        _random = new Random();
    }

    public async Task<string> GenerateEmailAsync(CancellationToken ct = default)
    {
        var name = $"{WORDS[_random.Next(WORDS.Length)]}_{WORDS2[_random.Next(WORDS2.Length)]}";
        var domain = DOMAINS[_random.Next(DOMAINS.Length)];
        return $"{name}@{domain}";
    }

    public async Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default)
    {
        var json = await _httpClient.GetAsync($"{BASE}/api/emails?recipient={Uri.EscapeDataString(email)}", ct);
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;
        if (!root.TryGetProperty("emails", out var emailsProp) || emailsProp.ValueKind != JsonValueKind.Array)
            return Array.Empty<Message>();

        var msgs = new List<Message>();
        foreach (var m in emailsProp.EnumerateArray())
        {
            var id = m.GetProperty("id").GetString() ?? "";
            var sender = m.TryGetProperty("sender", out var s) ? s.GetString() ?? "unknown" : "unknown";
            var subject = m.TryGetProperty("subject", out var subj) ? subj.GetString() ?? "(no subject)" : "(no subject)";
            var dateStr = m.TryGetProperty("created_at", out var d) ? d.GetString() : null;
            var date = dateStr != null ? DateTimeOffset.Parse(dateStr) : DateTimeOffset.UtcNow;

            var hasBody = m.TryGetProperty("body_text", out var bt);
            var hasHtml = m.TryGetProperty("body_html", out var bh);

            if (hasBody || hasHtml)
            {
                msgs.Add(new MessageDetail(
                    id, sender, subject, date,
                    bodyText: hasBody ? bt.GetString() ?? "" : "",
                    bodyHtml: hasHtml ? bh.GetString() ?? "" : "",
                    attachments: new List<JsonElement>()
                ));
            }
            else
            {
                msgs.Add(new Message(Id: id, Sender: sender, Subject: subject, Date: date));
            }
        }
        return msgs.AsReadOnly();
    }

    public Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default)
    {
        throw new TempMailException("Ncaori Mail+ returns full message in getInbox(). Use getInbox() then filter by id.");
    }

    public Task<bool> DeleteEmailAsync(string email, CancellationToken ct = default)
    {
        return Task.FromResult(true);
    }

    public async Task<Message?> WaitForEmailAsync(string email, TimeSpan timeout, TimeSpan interval, CancellationToken ct = default)
    {
        var deadline = DateTime.UtcNow + timeout;
        while (DateTime.UtcNow < deadline)
        {
            var inbox = await GetInboxAsync(email, ct);
            if (inbox.Count > 0) return inbox[0];
            await Task.Delay(interval, ct);
        }
        return null;
    }
}
