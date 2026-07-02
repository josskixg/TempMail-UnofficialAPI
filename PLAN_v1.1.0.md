# Implementation Plan - Version 1.1.0 (10 New Providers)

This plan details the roadmap and strategy for adding 10 new disposable email providers to the TempMail Unofficial API suite across all 7 supported languages.

## 🎯 Target Version: v1.1.0

The goal of this release is to expand the selection of providers from 5 to 15, improving fallback resilience if one or more services experience downtime or implement strict Cloudflare protections.

## 🌐 Implemented Providers

| # | Service | Website | API Type | Auth | Difficulty | Python | Other Languages |
|---|---------|---------|----------|------|------------|--------|-----------------|
| 1 | **emailfake** | emailfake.com | HTML Scraping (surl cookie + Socket.IO) | Cookie (surl) | ⚡ Medium | ✅ Done | Pending |
| 2 | **generator.email** | generator.email | HTML Scraping (surl cookie + Socket.IO) | Cookie (surl) | ⚡ Medium | ✅ Done | Pending |
| 3 | **zoromail** | zoromail.com | REST API (JSON) | None | ✅ Easy | ✅ Done | Pending |
| 4 | **tempmail.lol** | tempmail.lol | REST API (JSON, token-based) | Token | ✅ Easy | ✅ Done | Pending |
| 5 | **tempmailc** | tempmailc.com | REST API (JSON) | None | ✅ Easy | ✅ Done | Pending |
| 6 | **temp-mail.io** | temp-mail.io | REST API (JSON, Bearer) | Bearer Token | ⚡ Medium | ✅ Done | Pending |
| 7 | **tempmail.plus** | tempmail.plus | REST API (email query param) | None | ✅ Easy | ✅ Done | Pending |
| 8 | **mailnesia** | mailnesia.com | HTML Scraping (public mailbox) | None | ✅ Easy | ✅ Done | Pending |
| 9 | **10minutemail** | 10minutemail.com | REST API (cookie session) | Cookie | ✅ Easy | ✅ Done | Pending |
| 10 | **email-temp** | email-temp.com | HTML Scraping (surl cookie + Socket.IO) | Cookie (surl) | ⚡ Medium | ✅ Done | Pending |

### Provider Details

#### 1. emailfake.com — HTML Scraping with surl Cookie
- **URL Pattern**: `https://emailfake.com/channel{1-9}/` with cookie `surl={domain}/{username}`
- **Inbox**: `<div id="email-table">` containing `<a class="e7m list-group-item">` tags
- **Message fields**: `from_div_45g45gg` (sender), `subj_div_45g45gg` (subject), `time_div_45g45gg` (date)
- **Read message**: GET `/{domain}/{username}/{message_hash}` → `<div id="message">` contains body
- **Real-time**: Socket.IO at `wss://emailfake.com` path `/socket.io`, emit `watch_for_my_email`, listen `new_email`
- **Delete**: POST `//emailfake.com/del_mail.php` with encoded `delll` parameter

#### 2. generator.email — Same Backend as emailfake.com
- Same gasmurl pattern, Socket.IO, and del_mail.php endpoints
- Different domain list (e.g., `getmails.top`, `contactpage.online`, `gmail-xsniper.site`)
- Same implementation pattern as emailfake

#### 3. zoromail.com — Clean REST API
- Base: `https://zoromail.com/public_api.php/v1`
- `GET /domains` → list of domains
- `POST /emails` `{username, domain}` → `{email}`
- `GET /emails/{email}/messages` → message list
- `GET /messages/{id}` → full message
- `DELETE /messages/{id}` → delete
- Response envelope: `{success, data, error}`

#### 4. tempmail.lol — REST API with Token
- `POST https://api.tempmail.lol/v2/inbox/create` → `{address, token}`
- `GET https://api.tempmail.lol/v2/inbox?token={token}` → `{emails: [...], expired: bool}`
- Inbox returns full email content (no separate read endpoint needed)

#### 5. tempmailc.com — REST API, No Auth
- `GET /api/v1/domains` → `{ok, domains, count}`
- `GET /api/v1/new` → `{ok, email}` (or with `?domain=X`)
- `GET /api/v1/inbox?email={email}` → `{ok, email, count, messages}`
- `GET /api/v1/message?msg_id={id}&email={email}` → message detail

#### 6. temp-mail.io — Internal REST API
- `GET https://api.internal.temp-mail.io/api/v3/domains` → domains list
- `POST /api/v3/email/new` `{min_name_length, max_name_length}` → `{email, token}`
- `GET /api/v3/email/{email}/messages` → message array (Bearer auth)
- Note: Uses free internal API, NOT the paid `api.temp-mail.io`

#### 7. tempmail.plus — REST API with Email Query
- `GET https://tempmail.plus/api/mails?email={email}` → `{count, mail_list, ...}`
- `GET https://tempmail.plus/api/mails/{mail_id}?email={email}` → message detail
- No creation needed — any email at `mailto.plus` domain works
- mail_list items: `mail_id`, `from_mail`, `from_name`, `subject`, `time`

#### 8. mailnesia.com — Public Mailbox Scraping
- `GET https://mailnesia.com/mailbox/{username}` → HTML page with inbox
- No auth, no cookies, public mailbox
- Anyone can view any mailbox by username

#### 9. 10minutemail.com — REST API (Hidden)
- `GET /session/address` → `{address}` (cookie-based session)
- `GET /messages/messagesAfter/{timestamp}` → message array
- `GET /messages/messageCount` → `{messageCount}`
- WebSocket: `wss://10minutemail.com/ws/messages`
- Note: API endpoints discovered from JavaScript analysis, not publicly documented

#### 10. email-temp.com — Same Backend as emailfake.com
- Same family as emailfake.com and generator.email
- URL: `https://email-temp.com/channel{1-9}/` with cookie `surl={domain}/{username}`
- Different domain list from emailfake/generator.email
- Same implementation pattern

## 🛠️ Implementation Strategy & Architecture

### 1. File Path & Class Registration Mapping
For each language, the new providers will be placed in the respective provider modules:

- **Go**: `go/providers/`
- **Python**: `python/tempmail_wrapper/providers/` ✅ Done
- **JavaScript**: `javascript/src/providers/`
- **Java**: `java/src/main/java/com/tempmail/providers/`
- **PHP**: `php/src/Providers/`
- **Rust**: `rust/src/providers/`
- **C#**: `csharp/TempMail/Providers/`

### 2. Common Interface Compliance
All 10 new providers must inherit from/implement the unified factory contracts:
- `generate_email` / `GenerateEmail`
- `get_inbox` / `GetInbox`
- `read_message` / `ReadMessage`
- `delete_email` / `DeleteEmail`
- `wait_for_email` / `WaitForEmail` (uses the standard core polling algorithm)

### 3. Error and Rate-Limit Handling
- **Proxy Support**: Integrates standard proxy configurations for HTML Scraping engines to circumvent IP rate-limiting.
- **Anti-Bot Bypass**: Utilizes customized HTTP headers mirroring modern evergreen browsers.
- **surl Cookie**: emailfake/generator.email/email-temp use `surl={domain}/{username}` cookie for session identification.

### 4. E2E Test Suites Expansion
- Update E2E test suites inside each language directory to register and test all 15 providers sequentially.
- SMTP-based testing: Send test emails directly to provider MX servers via `smtplib` for reliable E2E verification.

### 5. Python Implementation Status (Complete)
All 10 providers implemented and tested in Python:
- Provider files: `python/tempmail_wrapper/providers/`
- Registration: `factory.py`, `providers/__init__.py`, `__init__.py` updated
- Test scripts: `test_v110.py` (basic), `test_smtp_v110.py` (SMTP E2E)
- Factory names: `emailfake`, `generator.email`, `zoromail`, `tempmail.lol`, `tempmailc`, `temp-mail.io`, `tempmail.plus`, `mailnesia`, `10minutemail`, `email-temp`
