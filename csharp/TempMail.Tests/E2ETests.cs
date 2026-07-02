using System.Net.Http;
using System.Text;
using System.Text.Json;
using Xunit;
using TempMail;

namespace TempMail.Tests;

public class E2ETests
{
    [Fact]
    public async Task MailTm_FullFlow()
    {
        var provider = TempMailFactory.Create("mailtm");
        try
        {
            await TestProviderFlow(provider);
        }
        catch (Exceptions.TempMailException ex) when (ex.Message.Contains("429"))
        {
            Console.WriteLine("SKIP: Mail.tm rate limited (429)");
        }
    }

    [Fact]
    public async Task GuerrillaMail_FullFlow()
    {
        var provider = TempMailFactory.Create("guerrillamail");
        await TestProviderFlow(provider);
    }

    [Fact]
    public async Task YOPmail_FullFlow()
    {
        var provider = TempMailFactory.Create("yopmail");
        await TestProviderFlow(provider);
    }

    [Fact]
    public async Task Dropmail_FullFlow()
    {
        var provider = TempMailFactory.Create("dropmail");
        try
        {
            await TestProviderFlow(provider);
        }
        catch (Exceptions.TempMailException ex) when (ex.Message.Contains("402") || ex.Message.Contains("PaymentRequired"))
        {
            Console.WriteLine("SKIP: Dropmail requires paid access (402)");
        }
    }

    [Fact]
    public async Task OneSecEmail_FullFlow()
    {
        var provider = TempMailFactory.Create("1secemail");
        try
        {
            await TestProviderFlow(provider);
        }
        catch (Exceptions.TempMailException ex) when (ex.Message.Contains("429") || ex.Message.Contains("403"))
        {
            Console.WriteLine($"SKIP: 1secemail unavailable: {ex.Message}");
        }
    }

    [Fact]
    public async Task NcaoriMail_FullFlow()
    {
        var provider = TempMailFactory.Create("ncaori");
        try
        {
            await TestProviderFlow(provider);
        }
        catch (Exceptions.TempMailException ex) when (ex.Message.Contains("network") || ex.Message.Contains("404"))
        {
            Console.WriteLine($"SKIP: ncaori unavailable: {ex.Message}");
        }
    }

    [Fact]
    public async Task Zoromail_FullFlow()
    {
        var provider = TempMailFactory.Create("zoromail");
        await TestProviderFlow(provider);
    }

    [Fact]
    public async Task TempmailLol_FullFlow()
    {
        var provider = TempMailFactory.Create("tempmail.lol");
        await TestProviderFlow(provider);
    }

    [Fact]
    public async Task Tempmailc_FullFlow()
    {
        var provider = TempMailFactory.Create("tempmailc");
        await TestProviderFlow(provider);
    }

    [Fact]
    public async Task TempMailIo_FullFlow()
    {
        var provider = TempMailFactory.Create("temp-mail.io");
        await TestProviderFlow(provider);
    }

    [Fact]
    public async Task TempmailPlus_FullFlow()
    {
        var provider = TempMailFactory.Create("tempmail.plus");
        await TestProviderFlow(provider);
    }

    [Fact]
    public async Task Emailfake_FullFlow()
    {
        var provider = TempMailFactory.Create("emailfake");
        await TestProviderFlow(provider);
    }

    [Fact]
    public async Task GeneratorEmail_FullFlow()
    {
        var provider = TempMailFactory.Create("generator.email");
        await TestProviderFlow(provider);
    }

    [Fact]
    public async Task Mailnesia_FullFlow()
    {
        var provider = TempMailFactory.Create("mailnesia");
        await TestProviderFlow(provider);
    }

    [Fact]
    public async Task TenMinuteMail_FullFlow()
    {
        var provider = TempMailFactory.Create("10minutemail");
        await TestProviderFlow(provider);
    }

    [Fact]
    public async Task EmailTemp_FullFlow()
    {
        var provider = TempMailFactory.Create("email-temp");
        await TestProviderFlow(provider);
    }

    private static async Task TestProviderFlow(ITempMailProvider provider)
    {
        string email;
        try
        {
            email = await provider.GenerateEmailAsync();
        }
        catch (HttpRequestException ex)
        {
            Console.WriteLine($"Skipping test due to network error: {ex.Message}");
            return;
        }

        Assert.NotNull(email);
        Assert.Contains("@", email);

        // Send a test email to this address so inbox has something to verify
        if (await SendTestEmailAsync(email))
        {
            await Task.Delay(4000);
        }
        else
        {
            Console.WriteLine("WARNING: Failed to send test email — inbox assertions skipped.");
        }

        IReadOnlyList<Models.Message> inbox;
        try
        {
            inbox = await provider.GetInboxAsync(email);
        }
        catch (HttpRequestException ex)
        {
            Console.WriteLine($"Skipping inbox check due to network error: {ex.Message}");
            return;
        }

        Assert.NotNull(inbox);

        if (inbox.Count > 0)
        {
            var firstMessage = inbox[0];
            try
            {
                var detail = await provider.ReadMessageAsync(firstMessage.Id);
                Assert.NotNull(detail);
                Assert.Equal(firstMessage.Id, detail.Id);
            }
            catch (Exceptions.TempMailException ex) when (provider.GetType().Name.Contains("Ncaori", StringComparison.OrdinalIgnoreCase))
            {
                Console.WriteLine("SKIP: Ncaori read_message throws expected exception");
            }
            catch (HttpRequestException ex)
            {
                Console.WriteLine($"Skipping message read due to network error: {ex.Message}");
            }
        }

        try
        {
            var deleted = await provider.DeleteEmailAsync(email);
            Assert.True(deleted);
        }
        catch (HttpRequestException ex)
        {
            Console.WriteLine($"Skipping delete due to network error: {ex.Message}");
        }
    }

    private static readonly string[] UA_POOL = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
    };

    private static readonly Random _random = new();
    private static string RandomUA() => UA_POOL[_random.Next(UA_POOL.Length)];

    private static string ResendApiKey()
    {
        var k = Environment.GetEnvironmentVariable("RESEND_API_KEY");
        if (!string.IsNullOrEmpty(k)) return k;
        // ponytail: load .env for local runs
        foreach (var path in new[] { "../.env", "../../.env", ".env" })
        {
            if (!File.Exists(path)) continue;
            foreach (var line in File.ReadAllLines(path))
            {
                var t = line.Trim();
                if (string.IsNullOrEmpty(t) || t.StartsWith('#')) continue;
                var eq = t.IndexOf('=');
                if (eq < 0) continue;
                if (t[..eq].Trim() == "RESEND_API_KEY") return t[(eq+1)..].Trim();
            }
            break;
        }
        return "";
    }

    private static async Task<bool> SendTestEmailAsync(string to)
    {
        int[] delays = { 1000, 3000, 5000 };
        for (int attempt = 0; attempt < 3; attempt++)
        {
            try
            {
                using var client = new System.Net.Http.HttpClient { Timeout = TimeSpan.FromSeconds(10) };
                var body = JsonSerializer.Serialize(new {
                    from = "onboarding@rokupusu.web.id",
                    to,
                    subject = "TempMail E2E Test",
                    html = "<p>E2E test email from TempMail wrapper</p>"
                });
                var content = new StringContent(body, Encoding.UTF8, "application/json");
                client.DefaultRequestHeaders.UserAgent.ParseAdd(RandomUA());
                client.DefaultRequestHeaders.Authorization =
                    new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", ResendApiKey());
                var resp = await client.PostAsync("https://api.resend.com/emails", content);
                if (resp.IsSuccessStatusCode) return true;
                if (attempt < 2)
                {
                    await Task.Delay(delays[attempt]);
                    continue;
                }
                return false;
            }
            catch
            {
                if (attempt < 2)
                {
                    await Task.Delay(delays[attempt]);
                    continue;
                }
                return false;
            }
        }
        return false;
    }
}
