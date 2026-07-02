# Release Notes

## v1.1.0

**Release date:** 2026-07-01

## Summary

Expanded provider support from 5 to 15 across all 7 languages. Added 10 new temp mail providers with diverse backend types (REST APIs, HTML scraping with cookie sessions). 9/10 new providers fully operational.

## What's New

- **10 new providers**: emailfake.com, generator.email, mail-temp.com, zoromail.com, tempmail.lol, tempmailc.com, temp-mail.io, tempmail.plus, mailnesia.com, 10minutemail.com
- **Total providers: 15** (5 from v1.0.0 + 10 new)
- **Full E2E test coverage** for all new providers in all 7 languages
- **Resend API integration** for test email delivery (verified domain: rokupusu.web.id)

## Provider Breakdown

| Provider | Backend Type | Auth Method | Status |
|----------|-------------|-------------|--------|
| emailfake | HTML Scraping | surl cookie | ✅ Operational |
| generator.email | HTML Scraping | surl cookie | ✅ Operational |
| mail-temp.com | HTML Scraping | surl cookie | ✅ Operational |
| zoromail | REST API | None | ✅ Operational |
| tempmail.lol | REST API | Token | ✅ Operational |
| tempmailc | REST API | None | ✅ Operational |
| temp-mail.io | REST API | Bearer Token | ✅ Operational |
| tempmail.plus | REST API | None | ✅ Operational |
| mailnesia | HTML Scraping | None | ❌ Blocked (403) |
| 10minutemail | REST API | Cookie Session | ✅ Operational |

## Known Issues

- **mailnesia.com**: Returns 403 Forbidden on `/mailbox/{username}` endpoint. Homepage accessible (200 OK) but mailbox endpoint blocked by Cloudflare. Session cookies do not bypass the restriction.

## Technical Details

- **HTML scraping providers** (emailfake, generator.email, mail-temp.com) use `surl={domain}/{username}` cookie for session management
- **REST API providers** use various auth methods: none, token-based, Bearer token, or cookie session
- **Inbox URL patterns** vary by provider: `/channel{1-9}/`, `/{email}`, `/temp-mail-box/`, or REST endpoints
- All providers implement the same interface: `generate_email`, `get_inbox`, `read_message`, `delete_email`, `wait_for_email`

---

## v1.0.0

**Release date:** 2026-06-27 (updated 2026-06-30)

## Summary

TempMail-UnofficialAPI: a unified wrapper for temporary email services across 7 programming languages. Five providers, zero API keys, real E2E tests.

## What's Included

- **5 providers**: Mail.tm, GuerrillaMail, YOPmail (HTML scraping), Dropmail.me (GraphQL), 1secemail.
- **7 languages**: Python, Go, JavaScript, Java, PHP, Rust, C#.
- **Common interface**: `generate_email`, `get_inbox`, `read_message`, `delete_email`, `wait_for_email` — identical contract across all languages.
- **Factory pattern**: create any provider by name, no manual imports needed.
- **E2E tests**: every language/provider combination tested against real APIs.
- **CLI demo** (Python): interactive comparison of all providers.
- **Dropmail captcha solver chain**: Custom captcha solver functions for Dropmail.me — integrate external services (2captcha, anti-captcha), manual input workflows, or custom OCR solutions across all 7 languages.

## Highlights

- **Zero API keys required.** All providers are accessed through their public endpoints or public-facing pages.
- **Real E2E tests, not mocks.** Tests send actual emails via Resend API and verify the full inbox cycle.
- **Apache 2.0 license.** Permissive for commercial and open-source use.

## Known Limitations

- **Dropmail.me**: No WebSocket support for real-time notifications. `wait_for_email` polls via GraphQL instead. WebSocket subscription may be added in a future release.
- **YOPmail**: No reliable delete method available. `delete_email` is not supported due to the absence of a public API. The method returns a best-effort result.
- **Rate limits**: Provider APIs may throttle aggressive polling. Tests include retry/backoff, but production consumers should respect per-provider rate limits (documented in per-language READMEs).

## What's Next

- WebSocket support for Dropmail.me real-time inbox updates.
- Additional providers: Harakirimail, Mailsac (as public endpoints allow).
- Async/streaming interfaces where the language supports them.

See [CHANGELOG.md](CHANGELOG.md) for the full change history.
