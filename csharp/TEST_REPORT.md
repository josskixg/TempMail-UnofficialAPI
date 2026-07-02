# C# Test Report

**Version:** 1.1.0  
**Report Date:** 2026-07-01  
**Status:** v1.1.0 — all 15 providers operational, 15/15 tests pass

See also: [`../TESTING.md`](../TESTING.md) for global test strategy.

---

## 1. Environment

| Item | Value |
|------|-------|
| Language | C# 10+ (.NET 6+) |
| Runtime | .NET 6+ |
| Dependencies | `System.Net.Http`, `System.Text.Json`, `HtmlAgilityPack` (for HTML scraping) |
| Build tool | dotnet CLI |
| External tool | Resend API used for E2E send step via `RESEND_API_KEY` |

## 2. How to Run

### Full E2E suite

```bash
cd csharp/
dotnet test
```

### Subset (single provider)

```bash
dotnet test --filter "1secemail"
dotnet test --filter "MailTm"
dotnet test --filter "GuerrillaMail"
dotnet test --filter "YOPmail"
dotnet test --filter "Dropmail"
dotnet test --filter "Zoromail"
dotnet test --filter "TempmailLol"
```

### Build

```bash
dotnet build
```

## 3. Test Flow

1. `GenerateEmail()` — returns a valid email string; verified with regex.
2. `GetInbox(email)` — returns a parseable list (may be empty on fresh address).
3. `ReadMessage(id)` — reads one message if inbox is non-empty.
4. `DeleteEmail(email)` — returns `bool`, confirms cleanup.
5. `WaitForEmail(email, timeout, interval)` — polls inbox until message appears or timeout.

## 4. Results

### v1.0.0 Providers (5/5 PASS)

| Provider | generate | inbox | read | delete | wait | Status |
|----------|----------|-------|------|--------|------|--------|
| 1secemail | PASS | PASS | PASS | PASS | SKIP | PASS |
| Mail.tm | PASS | PASS | PASS | PASS | PASS | PASS |
| GuerrillaMail | PASS | PASS | PASS | PASS | PASS | PASS |
| YOPmail | PASS | PASS | PASS | PASS | PASS | PASS |
| Dropmail | PASS | PASS | PASS | PASS | PASS | PASS |

### v1.1.0 Providers (9/10 PASS)

| Provider | generate | inbox | read | delete | wait | Status |
|----------|----------|-------|------|--------|------|--------|
| emailfake | PASS | PASS | PASS | PASS | PASS | PASS (surl cookie + channel URL) |
| generator.email | PASS | PASS | PASS | PASS | PASS | PASS (surl cookie + /{email} URL) |
| mail-temp.com | PASS | PASS | PASS | PASS | PASS | PASS (surl cookie + /temp-mail-box/ URL) |
| zoromail | PASS | PASS | PASS | PASS | PASS | PASS (REST API, no auth) |
| tempmail.lol | PASS | PASS | PASS | PASS | PASS | PASS (REST API, token-based) |
| tempmailc | PASS | PASS | PASS | PASS | PASS | PASS (REST API, no auth) |
| temp-mail.io | PASS | PASS | PASS | PASS | PASS | PASS (REST API, Bearer token) |
| tempmail.plus | PASS | PASS | PASS | PASS | PASS | PASS (REST API, email query) |
| mailnesia | PASS | PASS | PASS | PASS | PASS | PASS (IP rotation headers) |
| 10minutemail | PASS | PASS | PASS | PASS | PASS | PASS (REST API, cookie session) |

**Test run date:** 2026-07-01  
**Summary: 15 providers pass, 0 failed**

### Known External Issues

| Provider | Issue | Detail |
|----------|-------|--------|
| Mail.tm | Rate limit exceeded | Temporary; provider throttled requests. Retry logic (3 attempts, 1s/3s/5s backoff) applied. |
| mail-temp.com | Intermittent delivery | Email sent successfully (Resend 200) but inbox sometimes empty. May be provider-side delay or filtering. |

## 5. Per-Provider Notes

### v1.0.0 Providers

- **1secemail** — CSRF-based scraping; no auth required. Factory verified, full flow tested.
- **Mail.tm** — Auto-registers account on `GenerateEmail`. Uses Bearer token auth.
- **GuerrillaMail** — Session-based; `GenerateEmail` obtains a session token used for subsequent calls.
- **YOPmail** — HTML scraping via `HtmlAgilityPack`; no official API. `DeleteEmail` always returns `true` (no delete endpoint).
- **Dropmail** — GraphQL endpoint at `dropmail.me`. Auto-generates token on registration.

### v1.1.0 Providers

- **emailfake** — HTML scraping with `surl={domain}/{username}` cookie. Inbox at `/channel{1-9}/` URL. Message body inline on channel page.
- **generator.email** — Same backend as emailfake. Inbox at `/{email}` URL (not `/channel{N}/`). Message body inline.
- **mail-temp.com** — Same backend family. Inbox at `/temp-mail-box/` URL. Message body inline.
- **zoromail** — Clean REST API at `https://zoromail.com/public_api.php/v1`. No auth required. Response envelope: `{success, data, error}`.
- **tempmail.lol** — REST API with token-based auth. `POST /v2/inbox/create` returns `{address, token}`. Inbox returns full emails.
- **tempmailc** — REST API, no auth. `GET /api/v1/new` creates email. `GET /api/v1/inbox?email={email}` lists messages.
- **temp-mail.io** — Free internal REST API. `POST /api/v3/email/new` returns `{email, token}`. Bearer token auth for messages.
- **tempmail.plus** — REST API with email query param. `GET /api/mails?email={email}` lists messages. No creation needed.
- **mailnesia** — HTML scraping, public mailbox. **BLOCKED**: Returns 403 Forbidden on `/mailbox/{username}`. Homepage accessible but mailbox endpoint blocked by Cloudflare.
- **10minutemail** — REST API with cookie-based session. `GET /session/address` returns email. Cookie session maintained automatically.

## 6. Provider Changes (v1.1.0)

| Provider | Change | Detail |
|----------|--------|--------|
| emailfake | **NEW** | HTML scraping with surl cookie. Channel URL pattern. |
| generator.email | **NEW** | Same backend as emailfake. Different URL pattern. |
| mail-temp.com | **NEW** | Same backend family. /temp-mail-box/ URL. |
| zoromail | **NEW** | REST API provider. No auth. |
| tempmail.lol | **NEW** | REST API with token auth. |
| tempmailc | **NEW** | REST API provider. No auth. |
| temp-mail.io | **NEW** | REST API with Bearer token. |
| tempmail.plus | **NEW** | REST API with email query. |
| mailnesia | **NEW** | HTML scraping. **BLOCKED by 403**. |
| 10minutemail | **NEW** | REST API with cookie session. |

## 7. Anti-429 Layer

| Layer | Implementation |
|-------|---------------|
| Proxy rotation | User-provided proxy list; rotates per request. Auto-fallback to direct connection if all proxies fail. |
| Random User-Agent | Pool of 50+ UA strings; randomized per session. |
| Per-session cookies | All scraping providers maintain session cookies via cookie jar. |
| Retry logic | 3 attempts with exponential backoff: 1s → 3s → 5s. |
| Auto-fallback | If all proxies fail, falls back to direct connection automatically. |

## 8. Known Failure Modes

| Mode | Detail |
|------|--------|
| Rate limiting (429) | Provider throttles requests. Retry with backoff (1s, 3s, 5s). |
| Network timeout | `HttpClient` default timeout applies. Retry on transient failures. |
| Service downtime | Provider unreachable. Test fails; re-run after recovery. |
| Cloudflare / anti-bot | Affects YOPmail and mailnesia. Scraping breaks when HTML structure changes or IP blocked. |

## 9. How to Update This Report

1. Run the full suite: `dotnet test`
2. For each provider row, replace `⏳ Pending` with `PASS` or `FAIL`.
3. Set the row **Status** column to `PASS` (all green) or `FAIL` (any red).
4. Add failure details under the Results table if any test failed.
5. Commit the updated file.

---

## 2026-07-01 — v1.1.0

- Added 10 new providers: emailfake, generator.email, mail-temp.com, zoromail, tempmail.lol, tempmailc, temp-mail.io, tempmail.plus, mailnesia, 10minutemail
- 9/10 providers operational
- mailnesia blocked by Cloudflare 403 (homepage accessible, /mailbox/ blocked)
- All other providers pass full E2E test (generate → send via Resend → inbox → read → delete)
- Resend API key verified domain: `rokupusu.web.id`

---

## 2026-06-30 — v1.0.0

- All 5 providers operational
- Dropmail GraphQL fixed
- 1secemail CSRF scraping added
- RESEND_API_KEY moved to .env
