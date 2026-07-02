# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-07-01

### Added

- **10 new provider implementations** across all 7 languages:
  - emailfake.com (HTML scraping with surl cookie)
  - generator.email (HTML scraping with surl cookie)
  - mail-temp.com (HTML scraping with surl cookie)
  - zoromail.com (REST API, no auth)
  - tempmail.lol (REST API with token)
  - tempmailc.com (REST API, no auth)
  - temp-mail.io (REST API with Bearer token)
  - tempmail.plus (REST API, no auth)
  - mailnesia.com (HTML scraping - blocked by 403)
  - 10minutemail.com (REST API with cookie session)
- **Total providers: 15** (5 from v1.0.0 + 10 new)
- **9/10 new providers operational** (mailnesia blocked by Cloudflare 403)
- Full E2E test coverage for all new providers in all 7 languages
- Resend API integration for test email delivery (verified domain: rokupusu.web.id)

### Provider Implementation Details

| Provider | Backend Type | Auth Method | Inbox URL Pattern |
|----------|-------------|-------------|-------------------|
| emailfake | HTML Scraping | surl cookie | /channel{1-9}/ |
| generator.email | HTML Scraping | surl cookie | /{email} |
| mail-temp.com | HTML Scraping | surl cookie | /temp-mail-box/ |
| zoromail | REST API | None | /public_api.php/v1 |
| tempmail.lol | REST API | Token | /v2/inbox |
| tempmailc | REST API | None | /api/v1 |
| temp-mail.io | REST API | Bearer Token | /api/v3 |
| tempmail.plus | REST API | None | /api/mails |
| mailnesia | HTML Scraping | None | /mailbox/{username} |
| 10minutemail | REST API | Cookie Session | /session/address |

### Known Issues

- **mailnesia.com**: Returns 403 Forbidden on `/mailbox/{username}` endpoint. Homepage accessible (200 OK) but mailbox endpoint blocked by Cloudflare. Session cookies do not bypass the restriction.

## [1.0.0] - 2026-06-30

### Added

- Unified temp-mail wrapper across 7 languages: Python, Go, JavaScript, Java, PHP, Rust, and C#.
- Five provider implementations: Mail.tm, GuerrillaMail, YOPmail (HTML scraping), Dropmail.me (GraphQL), and 1secemail.
- Common interface contract: `generate_email`, `get_inbox`, `read_message`, `delete_email`, `wait_for_email`.
- Data models: `Message` (id, sender, subject, date) and `MessageDetail` (body_text, body_html, attachments).
- Factory pattern for provider creation by name — no API keys required.
- Real E2E tests for every language/provider combination (no mocks).
- Error hierarchy per language for consistent failure handling.
- Per-language READMEs with usage examples and quick start guides.
- CLI demo (Python) for interactive provider comparison.
- Apache 2.0 license.
- **Dropmail captcha solver chain**: Custom captcha solver functions for Dropmail.me — integrate external services (2captcha, anti-captcha), manual input workflows, or custom OCR solutions across all 7 languages.

### Supported Providers

| Provider | Method | Free | Send Limit |
|----------|--------|------|------------|
| Mail.tm | REST API | Yes | 6/min |
| GuerrillaMail | REST API | Yes | Unlimited |
| YOPmail | HTML Scraping | Yes | Unlimited |
| Dropmail.me | GraphQL | Yes | Unlimited |

### Supported Languages

| Language | Package |
|----------|---------|
| Python | `tempmail_wrapper/` |
| Go | `go/` |
| JavaScript | `javascript/` |
| Java | `java/` |
| PHP | `php/` |
| Rust | `rust/` |
| C# | `csharp/` |

[1.0.0]: https://github.com/josskixg/TempMail-UnofficialAPI/releases/tag/v1.0.0
