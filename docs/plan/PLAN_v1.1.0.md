# Implementation Plan — v1.1.0 (11 New Providers)

> **Status: ✅ COMPLETED** — Released 2026-07-02
>
> All 11 providers have been implemented across all 7 languages. E2E tests pass. See [RELEASE_NOTES.md](../RELEASE_NOTES.md) for final results.

This document details the implementation roadmap and strategy used to add 11 new disposable email providers to the TempMail Unofficial API suite across all 7 supported languages (Go, Python, JavaScript, Java, PHP, Rust, C#).

---

## 🎯 Target Version: v1.1.0

**Goal**: Expand provider support from 5 (v1.0.0) to 16, improving fallback resilience when one or more services experience downtime or strict Cloudflare protections.

---

## 🌐 Implemented Providers

| # | Service | Website | API Type | Auth | Difficulty | Status |
|---|---------|---------|----------|------|------------|--------|
| 1 | **emailfake** | emailfake.com | HTML Scraping (surl cookie) | Cookie (surl) | ⚡ Medium | ✅ Done |
| 2 | **generator.email** | generator.email | HTML Scraping (surl cookie) | Cookie (surl) | ⚡ Medium | ✅ Done |
| 3 | **email-temp** | email-temp.com | HTML Scraping (surl cookie) | Cookie (surl) | ⚡ Medium | ✅ Done |
| 4 | **zoromail** | zoromail.com | REST API (JSON) | None | ✅ Easy | ✅ Done |
| 5 | **tempmail.lol** | tempmail.lol | REST API (JSON, token-based) | Token | ✅ Easy | ✅ Done |
| 6 | **tempmailc** | tempmailc.com | REST API (JSON) | None | ✅ Easy | ✅ Done |
| 7 | **temp-mail.io** | temp-mail.io | REST API (JSON, Bearer) | Bearer Token | ⚡ Medium | ✅ Done |
| 8 | **tempmail.plus** | tempmail.plus | REST API (email query param) | None | ✅ Easy | ✅ Done |
| 9 | **mailnesia** | mailnesia.com | HTML Scraping (public mailbox) | None | ✅ Easy | ✅ Done (rate-limited externally) |
| 10 | **10minutemail** | 10minutemail.com | REST API (cookie session) | Cookie | ⚡ Medium | ✅ Done (Cloudflare-guarded externally) |
| 11 | **ncaori** | nca.my.id | REST API (JSON) | None | ✅ Easy | ✅ Done |

---

## 📖 Provider API Details

### 1. emailfake.com — HTML Scraping with surl Cookie
- **URL Pattern**: `https://emailfake.com/channel{1-9}/` with cookie `surl={domain}/{username}`
- **Inbox**: `<div id="email-table">` containing `<a class="e7m list-group-item">` tags
- **Message fields**: `from_div_45g45gg` (sender), `subj_div_45g45gg` (subject), `time_div_45g45gg` (date)
- **Read message**: GET `/{domain}/{username}/{message_hash}` → `<div id="message">` contains body
- **Delete**: POST `//emailfake.com/del_mail.php` with encoded `delll` parameter

### 2. generator.email — Same Backend as emailfake.com
- Same surl cookie pattern and del_mail.php endpoints
- Different domain list (e.g., `getmails.top`, `contactpage.online`)
- Same implementation pattern as emailfake

### 3. email-temp.com — Same Backend as emailfake.com
- Same family as emailfake.com and generator.email
- URL: `https://email-temp.com/channel{1-9}/` with cookie `surl={domain}/{username}`
- Different domain list from emailfake/generator.email

### 4. zoromail.com — Clean REST API
- Base: `https://zoromail.com/public_api.php/v1`
- `GET /domains` → list of domains
- `POST /emails` `{username, domain}` → `{email}`
- `GET /emails/{email}/messages` → message list
- `GET /messages/{id}` → full message
- `DELETE /messages/{id}` → delete
- Response envelope: `{success, data, error}`

### 5. tempmail.lol — REST API with Token
- `POST https://api.tempmail.lol/v2/inbox/create` → `{address, token}`
- `GET https://api.tempmail.lol/v2/inbox?token={token}` → `{emails: [...], expired: bool}`
- Inbox returns full email content (no separate read endpoint needed)

### 6. tempmailc.com — REST API, No Auth
- `GET /api/v1/domains` → `{ok, domains, count}`
- `GET /api/v1/new` → `{ok, email}` (or with `?domain=X`)
- `GET /api/v1/inbox?email={email}` → `{ok, email, count, messages}`
- `GET /api/v1/message?msg_id={id}&email={email}` → message detail

### 7. temp-mail.io — Internal REST API
- `GET https://api.internal.temp-mail.io/api/v3/domains` → domains list
- `POST /api/v3/email/new` `{min_name_length, max_name_length}` → `{email, token}`
- `GET /api/v3/email/{email}/messages` → message array (Bearer auth)
- Note: Uses free internal API, NOT the paid `api.temp-mail.io`

### 8. tempmail.plus — REST API with Email Query
- `GET https://tempmail.plus/api/mails?email={email}` → `{count, mail_list, ...}`
- `GET https://tempmail.plus/api/mails/{mail_id}?email={email}` → message detail
- No creation needed — any email at `mailto.plus` domain works

### 9. mailnesia.com — Public Mailbox Scraping
- `GET https://mailnesia.com/mailbox/{username}` → HTML page with inbox
- No auth, no cookies, public mailbox
- Anyone can view any mailbox by username
- **Note**: Rate-limited by Cloudflare at sequential/rapid requests

### 10. 10minutemail.com — REST API (Hidden)
- `GET /session/address` → `{address}` (cookie-based session)
- `GET /messages/messagesAfter/{timestamp}` → message array
- `GET /messages/messageCount` → `{messageCount}`
- Note: API endpoints discovered from JavaScript source analysis, not publicly documented
- **Note**: Actively guarded by Cloudflare — soft-skipped in CI tests

### 11. ncaori (nca.my.id) — Custom REST API
- Custom-built service by the repo owner
- `GET /api/domains` → list of available domains
- `POST /api/mailbox` `{username, domain}` → `{email}`
- `GET /api/emails?email={email}` → message list
- `GET /api/emails/{id}` → message detail
- No auth required

---

## 🛠️ Implementation Strategy & Architecture

### 1. File Path & Class Registration Mapping

For each language, the new providers are placed in the respective provider modules:

| Language | Provider Path | Factory Registration |
|----------|--------------|---------------------|
| Go | `go/providers/` | `go/tempmail.go` |
| Python | `python/tempmail_wrapper/providers/` | `factory.py`, `providers/__init__.py` |
| JavaScript | `javascript/src/providers/` | `javascript/src/index.js` |
| Java | `java/src/main/java/com/tempmail/providers/` | `TempMailFactory.java` |
| PHP | `php/src/Providers/` | `TempMailFactory.php` |
| Rust | `rust/src/providers/` | `rust/src/lib.rs` |
| C# | `csharp/TempMail/Providers/` | `TempMailFactory.cs` |

### 2. Common Interface Compliance

All 11 new providers implement the unified factory contract:

- `generate_email` / `GenerateEmail`
- `get_inbox` / `GetInbox`
- `read_message` / `ReadMessage`
- `delete_email` / `DeleteEmail`
- `wait_for_email` / `WaitForEmail` (standard core polling algorithm)

### 3. Error and Rate-Limit Handling

- **`isSkipErr` / skip logic**: If a provider's external service returns a network error, 403, or rate-limit response during E2E testing, the test is soft-skipped (not failed), keeping CI green.
- **Anti-Bot Bypass**: Customized HTTP headers mirroring modern evergreen browsers.
- **surl Cookie**: `emailfake`, `generator.email`, and `email-temp` use `surl={domain}/{username}` cookie for session identification.

### 4. E2E Test Results (v1.1.0 Final)

| Language | Passed | Skipped | Failed | Command |
|----------|--------|---------|--------|---------|
| JavaScript | 32 | 0 | 0 | `node --test tests/e2e.test.js` |
| Python | 18 | 0 | 0 | `pytest -v tests/test_e2e.py --e2e` |
| PHP | 58 | 2 | 0 | `vendor/bin/phpunit --testsuite=e2e` |
| Rust | 16 | 0 | 0 | `cargo test --test e2e -- --ignored` |
| Go | 14 | 2 | 0 | `go test -v -tags=e2e ./...` |
| C# | 16 | 0 | 0 | `dotnet test` |
| Java | 57 | 10 | 0 | `mvn test-compile exec:java -Dexec.mainClass=com.tempmail.E2ETest` |

**Skipped tests** are soft-fails caused by external Cloudflare protection or rate-limiting on `mailnesia.com` and `10minutemail.com`.

---

## 🐛 Build Issues Resolved During Implementation

- **Rust (`ncaori_mail.rs`)**: Fixed mutability and lifetime of closure `pick`; resolved type mismatch (`MessageDetail` vs `Message`) in inbox mapping.
- **Rust (`mailnesia.rs`)**: Fixed missing `get_with_headers` method by using `.client.inner().get()` builder request directly.
- **Java (`MailnesiaProvider.java`)**: Added missing `java.util.Map` and `java.util.HashMap` imports; renamed `getWithHeaders` call to `get`.
- **C# (`MailnesiaProvider.cs`)**: Implemented missing `GetHeadersWithIPRotation()` and `GenerateRandomIp()` helper methods; fixed `GetAsync` headers handler configuration.

---

See [RELEASE_NOTES.md](../RELEASE_NOTES.md) for the full v1.1.0 release summary.
