using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using TempMail.Exceptions;
using TempMail.Models;

namespace TempMail.Providers;

public sealed class DropmailProvider : ITempMailProvider
{
    private const string TokenUrl = "https://dropmail.me/api/token/generate";
    private const string PaddleOcrUrl = "https://mamamacjdjj-padle-predict.hf.space/predict";
    private static readonly JsonSerializerOptions JsonOpts = new() { PropertyNameCaseInsensitive = true };

    private readonly HttpClient _http;
    // Session client uses the same CookieContainer as _http for steps 1,2,4,5.
    private readonly System.Net.Http.HttpClient _sessionClient;
    private readonly List<Func<byte[], string>>? _captchaSolvers;

    private string? _token;
    private string? _sessionId;
    private string? _addressId;
    private string? _email;
    private List<DropmailMessage>? _cachedMails;

    public DropmailProvider(HttpClient http, List<Func<byte[], string>>? captchaSolvers = null)
    {
        _http = http;
        _captchaSolvers = captchaSolvers;
        // Share the same CookieContainer so captcha session cookies are preserved.
        var handler = new System.Net.Http.HttpClientHandler
        {
            CookieContainer = http.Cookies,
            UseCookies = true,
        };
        _sessionClient = new System.Net.Http.HttpClient(handler);
    }

    /// <summary>
    /// Built-in PaddleOCR solver via HuggingFace space.
    /// Tries up to 3 times, returns trimmed text on success or null.
    /// </summary>
    public static string? PaddleOcrSolver(byte[] imgBytes)
    {
        for (int attempt = 0; attempt < 3; attempt++)
        {
            try
            {
                using var client = new System.Net.Http.HttpClient();
                var boundary = "----TempMail" + Guid.NewGuid().ToString("N");
                using var content = new MultipartFormDataContent(boundary);
                content.Add(new ByteArrayContent(imgBytes), "file", "cap.png");
                var resp = client.PostAsync(PaddleOcrUrl, content).GetAwaiter().GetResult();
                var json = resp.Content.ReadAsStringAsync().GetAwaiter().GetResult();
                using var doc = JsonDocument.Parse(json);
                if (doc.RootElement.TryGetProperty("results", out var results) && results.GetArrayLength() > 0)
                {
                    var first = results[0];
                    if (first.TryGetProperty("confidence", out var conf) && conf.GetDouble() >= 0.7)
                    {
                        var text = first.GetProperty("text").GetString()?.Trim();
                        if (!string.IsNullOrWhiteSpace(text)) return text;
                    }
                }
            }
            catch { /* ignore */ }
        }
        return null;
    }

    public async Task<string> GenerateEmailAsync(CancellationToken ct = default)
    {
        _token = await ObtainTokenAsync(ct);

        var mutation = @"mutation { introduceSession { id addresses { id address restoreKey } } }";
        var sessionData = await GraphQLAsync<DropmailSessionResponse>(mutation, ct);

        _sessionId = sessionData?.IntroduceSession?.Id
            ?? throw new TempMailException("Failed to create session.");

        var firstAddress = sessionData.IntroduceSession.Addresses?.FirstOrDefault()
            ?? throw new TempMailException("No address returned.");

        _addressId = firstAddress.Id;
        _email = firstAddress.Address;
        return _email!;
    }

    private async Task<string> ObtainTokenAsync(CancellationToken ct)
    {
        var payload1d = new { type = "af", lifetime = "1d" };

        // Step 1: try 1d via session client (cookies stored for captcha flow)
        var resp1d = await _sessionClient.PostAsJsonAsync(TokenUrl, payload1d, JsonOpts, ct);

        if (resp1d.IsSuccessStatusCode)
        {
            var data = await resp1d.Content.ReadFromJsonAsync<DropmailTokenResponse>(JsonOpts, ct)
                ?? throw new TempMailException("Failed to parse token response.");
            return data.Token ?? throw new TempMailException("No token in response.");
        }

        if ((int)resp1d.StatusCode == 402)
        {
            DropmailCaptchaResponse? capResp = null;
            try { capResp = await resp1d.Content.ReadFromJsonAsync<DropmailCaptchaResponse>(JsonOpts, ct); }
            catch { /* ignore */ }

            if (capResp?.Captcha != null)
            {
                var solved = await SolveCaptchaAndGetTokenAsync(capResp.Captcha, ct);
                if (solved != null) return solved;
            }

            Console.Error.WriteLine("Dropmail: captcha solve failed, retrying with 1d token");
            // Fallback: retry 1d (session may differ, but best effort)
            var fallback = await _sessionClient.PostAsJsonAsync(TokenUrl, payload1d, JsonOpts, ct);
            if (!fallback.IsSuccessStatusCode)
                throw new TempMailException($"Failed to generate token (fallback): {fallback.StatusCode}");
            var fallbackData = await fallback.Content.ReadFromJsonAsync<DropmailTokenResponse>(JsonOpts, ct)
                ?? throw new TempMailException("Failed to parse fallback token response.");
            return fallbackData.Token ?? throw new TempMailException("No token in fallback response.");
        }

        throw new TempMailException($"Failed to generate token: {resp1d.StatusCode}");
    }

    private async Task<string?> SolveCaptchaAndGetTokenAsync(DropmailCaptcha captcha, CancellationToken ct)
    {
        var v     = captcha.V     ?? "3";
        var nonce = captcha.Nonce ?? "";
        var key   = captcha.Key   ?? "";
        var sig   = captcha.Sig   ?? "";

        // Step 2: download image — same CookieContainer (_sessionClient)
        var imgUrl = $"https://dropmail.me/captcha/image.png?_r=0"
                   + $"&v={Uri.EscapeDataString(v)}&nonce={Uri.EscapeDataString(nonce)}"
                   + $"&key={Uri.EscapeDataString(key)}&_sig={Uri.EscapeDataString(sig)}";
        var imgResp = await _sessionClient.GetAsync(imgUrl, ct);
        if (!imgResp.IsSuccessStatusCode) return null;
        var imgBytes = await imgResp.Content.ReadAsByteArrayAsync(ct);

        // Step 3: run solver chain
        string? ocrText = null;
        var solvers = _captchaSolvers is { Count: > 0 } ? _captchaSolvers : new List<Func<byte[], string>> { PaddleOcrSolver };
        foreach (var solver in solvers)
        {
            try
            {
                var result = solver(imgBytes);
                if (!string.IsNullOrWhiteSpace(result))
                {
                    ocrText = result.Trim();
                    break;
                }
            }
            catch { /* ignore */ }
        }
        if (string.IsNullOrWhiteSpace(ocrText)) return null;

        // Step 4: submit solution — same session, form-encoded
        var formFields = new System.Net.Http.FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["response"] = ocrText,
            ["v"]        = v,
            ["nonce"]    = nonce,
            ["key"]      = key,
            ["_sig"]     = sig,
        });
        var solResp = await _sessionClient.PostAsync("https://dropmail.me/captcha/solution", formFields, ct);
        SolutionResponse? solData = null;
        try { solData = await solResp.Content.ReadFromJsonAsync<SolutionResponse>(JsonOpts, ct); }
        catch { return null; }
        if (solData?.Result != "correct") return null;

        // Step 5: retry 90d token — same session
        var payload90d = new { type = "af", lifetime = "90d" };
        var tokenResp = await _sessionClient.PostAsJsonAsync(TokenUrl, payload90d, JsonOpts, ct);
        if (!tokenResp.IsSuccessStatusCode) return null;
        var tokenData = await tokenResp.Content.ReadFromJsonAsync<DropmailTokenResponse>(JsonOpts, ct);
        return tokenData?.Token;
    }

    public async Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default)
    {
        EnsureSession();
        var query = $@"query {{ session(id: ""{_sessionId}"") {{ mails {{ id fromAddr headerSubject receivedAt text html attachments }} }} }}";
        var data = await GraphQLAsync<DropmailInboxResponse>(query, ct);

        _cachedMails = data?.Session?.Mails ?? [];

        return _cachedMails.Select(m => new Message(
            m.Id ?? "",
            m.FromAddr ?? "",
            m.HeaderSubject ?? "",
            m.ReceivedAt ?? DateTimeOffset.UtcNow
        )).ToList();
    }

    public async Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default)
    {
        EnsureSession();

        if (_cachedMails == null)
            await GetInboxAsync(_email ?? "", ct);

        var mail = _cachedMails?.FirstOrDefault(m => m.Id == messageId)
            ?? throw new TempMailException($"Message {messageId} not found.");

        return new MessageDetail(
            mail.Id ?? "",
            mail.FromAddr ?? "",
            mail.HeaderSubject ?? "",
            mail.ReceivedAt ?? DateTimeOffset.UtcNow,
            mail.BodyText ?? "",
            mail.BodyHtml ?? "",
            mail.Attachments ?? []
        );
    }

    public async Task<bool> DeleteEmailAsync(string email, CancellationToken ct = default)
    {
        if (_addressId == null) return true;
        EnsureSession();
        try
        {
            var mutation = $@"mutation {{ deleteAddress(input: {{ addressId: ""{_addressId}"" }}) {{ id }} }}";
            await GraphQLAsync<object>(mutation, ct);
        }
        catch { /* best-effort */ }
        _token = null; _sessionId = null; _addressId = null; _email = null; _cachedMails = null;
        return true;
    }

    public async Task<Message?> WaitForEmailAsync(string email, TimeSpan timeout, TimeSpan interval, CancellationToken ct = default)
    {
        var deadline = DateTimeOffset.UtcNow + timeout;
        while (DateTimeOffset.UtcNow < deadline)
        {
            var inbox = await GetInboxAsync(email, ct);
            if (inbox.Count > 0) return inbox[0];
            await Task.Delay(interval, ct);
        }
        return null;
    }

    private async Task<T?> GraphQLAsync<T>(string query, CancellationToken ct)
    {
        EnsureToken();
        var payload = new { query };
        var res = await _http.Client.PostAsJsonAsync($"https://dropmail.me/api/graphql/{_token}", payload, JsonOpts, ct);
        if (!res.IsSuccessStatusCode)
            throw new TempMailException($"GraphQL request failed: {res.StatusCode}");

        using var doc = await JsonDocument.ParseAsync(await res.Content.ReadAsStreamAsync(ct), cancellationToken: ct);
        if (doc.RootElement.TryGetProperty("errors", out var errEl) && errEl.GetArrayLength() > 0)
            throw new TempMailException($"GraphQL error: {errEl[0].GetProperty("message").GetString()}");

        if (doc.RootElement.TryGetProperty("data", out var dataEl))
            return dataEl.Deserialize<T>(JsonOpts);

        return default;
    }

    private void EnsureToken()
    {
        if (_token == null) throw new TempMailException("Not authenticated. Call GenerateEmailAsync() first.");
    }

    private void EnsureSession()
    {
        EnsureToken();
        if (_sessionId == null) throw new TempMailException("No session. Call GenerateEmailAsync() first.");
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    private sealed class DropmailTokenResponse
    {
        [JsonPropertyName("token")] public string? Token { get; set; }
    }

    private sealed class DropmailCaptcha
    {
        [JsonPropertyName("v")]     public string? V     { get; set; }
        [JsonPropertyName("nonce")] public string? Nonce { get; set; }
        [JsonPropertyName("key")]   public string? Key   { get; set; }
        [JsonPropertyName("_sig")]  public string? Sig   { get; set; }
    }

    private sealed class DropmailCaptchaResponse
    {
        [JsonPropertyName("captcha")] public DropmailCaptcha? Captcha { get; set; }
    }

    private sealed class SolutionResponse
    {
        [JsonPropertyName("result")] public string? Result { get; set; }
    }

    private sealed class DropmailSessionResponse
    {
        [JsonPropertyName("introduceSession")] public DropmailSession? IntroduceSession { get; set; }
    }

    private sealed class DropmailSession
    {
        [JsonPropertyName("id")]        public string?             Id        { get; set; }
        [JsonPropertyName("addresses")] public List<DropmailAddr>? Addresses { get; set; }
    }

    private sealed class DropmailAddr
    {
        [JsonPropertyName("id")]         public string? Id         { get; set; }
        [JsonPropertyName("address")]    public string? Address    { get; set; }
        [JsonPropertyName("restoreKey")] public string? RestoreKey { get; set; }
    }

    private sealed class DropmailInboxResponse
    {
        [JsonPropertyName("session")] public DropmailSessionMails? Session { get; set; }
    }

    private sealed class DropmailSessionMails
    {
        [JsonPropertyName("mails")] public List<DropmailMessage>? Mails { get; set; }
    }

    private sealed class DropmailMessage
    {
        [JsonPropertyName("id")]            public string?              Id            { get; set; }
        [JsonPropertyName("fromAddr")]      public string?              FromAddr      { get; set; }
        [JsonPropertyName("headerSubject")] public string?              HeaderSubject { get; set; }
        [JsonPropertyName("receivedAt")]    public DateTimeOffset?      ReceivedAt    { get; set; }
        [JsonPropertyName("text")]          public string?              BodyText      { get; set; }
        [JsonPropertyName("html")]          public string?              BodyHtml      { get; set; }
        [JsonPropertyName("attachments")]   public List<JsonElement>?   Attachments   { get; set; }
    }
}
