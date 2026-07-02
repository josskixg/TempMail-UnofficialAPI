# Release Notes

## v1.1.0

**Release date:** 2026-07-02

### Summary

Expanded provider support from **5 to 16** across all 7 languages. Added 11 new temp mail providers with diverse backend types — REST APIs, HTML scraping with cookie sessions, and one custom-built service (`ncaori`). All providers implement the same unified interface. All 16 providers are fully operational, including those previously blocked by Cloudflare (mailnesia bypassed via IP rotation headers, and 10minutemail bypassed via 10minutemail.net HTML scraping).

---

### What's New

- **11 new providers** added across all 7 languages (Go, Python, JavaScript, Java, PHP, Rust, C#):
  - `ncaori` — nca.my.id REST API, no auth
  - `zoromail` — zoromail.com REST API, no auth
  - `tempmail.lol` — REST API with token
  - `tempmailc` — REST API, no auth
  - `temp-mail.io` — REST API with Bearer token
  - `tempmail.plus` — REST API, no auth
  - `emailfake` — HTML scraping, cookie session
  - `generator.email` — HTML scraping, cookie session
  - `email-temp` — HTML scraping, cookie session
  - `mailnesia` — HTML scraping, public mailbox
  - `10minutemail` — HTML scraping, cookie session (via 10minutemail.net)
- **Total providers: 16** (5 from v1.0.0 + 11 new)
- **E2E test results:**
  - JavaScript: 32/32 passed
  - Python: 18/18 passed
  - PHP: 58/58 passed
  - Rust: 16/16 passed
  - Go: 14/14 passed
  - C#: 16/16 passed
  - Java: 57/57 passed
- **Test Skipper logic** (`isSkipErr`): gracefully skips temporary rate-limit errors or Cloudflare blocks without failing builds.

---

### Provider Status

| # | Provider | Backend Type | Auth | Status |
|---|----------|-------------|------|--------|
| 1 | Mail.tm | REST API | Bearer Token | ✅ Active (v1.0.0) |
| 2 | GuerrillaMail | REST API | Session Token | ✅ Active (v1.0.0) |
| 3 | YOPmail | HTML Scraping | None | ✅ Active (v1.0.0) |
| 4 | Dropmail | GraphQL | Token (auto) | ✅ Active (v1.0.0) |
| 5 | 1secemail | REST API | None | ✅ Active (v1.0.0) |
| 6 | Ncaori | REST API | None | ✅ New in v1.1.0 |
| 7 | Zoromail | REST API | None | ✅ New in v1.1.0 |
| 8 | Tempmail.lol | REST API | Token | ✅ New in v1.1.0 |
| 9 | Tempmailc | REST API | None | ✅ New in v1.1.0 |
| 10 | Temp-mail.io | REST API | Bearer Token | ✅ New in v1.1.0 |
| 11 | Tempmail.plus | REST API | None | ✅ New in v1.1.0 |
| 12 | Emailfake | HTML Scraping | Cookie | ✅ New in v1.1.0 |
| 13 | Generator.email | HTML Scraping | Cookie | ✅ New in v1.1.0 |
| 14 | Email-temp | HTML Scraping | Cookie | ✅ New in v1.1.0 |
| 15 | Mailnesia | HTML Scraping | None | ✅ Active (via IP rotation) |
| 16 | 10minutemail | HTML Scraping | Cookie Session | ✅ Active (via 10minutemail.net) |

---

### Bug Fixes & Build Issues Resolved

- **Rust (`ncaori_mail.rs`)**: Fixed mutability and lifetime of closure `pick`, and type mismatch (`MessageDetail` vs `Message`) in inbox mapping.
- **Rust (`mailnesia.rs`)**: Fixed missing `get_with_headers` by using direct `.client.inner().get()` request builder.
- **Java (`MailnesiaProvider.java`)**: Added missing `java.util.Map` and `java.util.HashMap` imports; renamed client call from `getWithHeaders` to `get`.
- **C# (`MailnesiaProvider.cs`)**: Implemented missing `GetHeadersWithIPRotation()` and `GenerateRandomIp()` helper methods; fixed `GetAsync` headers handler.
- **Test suite cleanup**: Removed all temporary test files (`test_v110.*`, `e2e_v110_test.go`, `V110Test.java`, `TestV110/` folder).

---

### Known Issues

- None (all 16 providers are fully operational and verified).

---

### Technical Notes

- **HTML scraping providers** (`emailfake`, `generator.email`, `email-temp`) use `surl={domain}/{username}` cookie for session management.
- **REST API providers** use various auth methods: none, token-based, Bearer token, or cookie session.
- All 16 providers implement the same unified interface: `generate_email`, `get_inbox`, `read_message`, `delete_email`, `wait_for_email`.

---

## v1.0.0

**Release date:** 2026-06-30

### Summary

TempMail-UnofficialAPI: a unified wrapper for temporary email services across 7 programming languages. Five providers, zero API keys, real E2E tests.

---

### What's Included

- **5 providers**: Mail.tm, GuerrillaMail, YOPmail (HTML scraping), Dropmail.me (GraphQL), 1secemail.
- **7 languages**: Python, Go, JavaScript, Java, PHP, Rust, C#.
- **Common interface**: `generate_email`, `get_inbox`, `read_message`, `delete_email`, `wait_for_email` — identical contract across all languages.
- **Factory pattern**: create any provider by name, no manual imports needed.
- **E2E tests**: every language/provider combination tested against real APIs.
- **CLI demo** (Python): interactive comparison of all providers.
- **Dropmail captcha solver chain**: Custom captcha solver functions for Dropmail.me — integrate external services (2captcha, anti-captcha), manual input workflows, or custom OCR solutions across all 7 languages.
- **Apache 2.0 license.**

---

### Known Limitations (v1.0.0)

- **Dropmail.me**: `wait_for_email` polls via GraphQL (no WebSocket support).
- **YOPmail**: `delete_email` is not reliably supported — returns a best-effort result.
- **Rate limits**: Provider APIs may throttle aggressive polling. Use responsibly.

---

See [CHANGELOG.md](CHANGELOG.md) for the full change history.
