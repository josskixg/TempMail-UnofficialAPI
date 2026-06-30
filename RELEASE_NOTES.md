# Release Notes

## v1.0.0

**Release date:** 2026-06-27 (updated 2026-06-30)

## Summary

TempMail-UnofficialAPI: a unified wrapper for temporary email services across 7 programming languages. Four providers, zero API keys, real E2E tests.

## What's Included

- **4 providers**: Mail.tm, GuerrillaMail, YOPmail (HTML scraping), Dropmail.me (GraphQL).
- **7 languages**: Python, Go, JavaScript, Java, PHP, Rust, C#.
- **Common interface**: `generate_email`, `get_inbox`, `read_message`, `delete_email`, `wait_for_email` — identical contract across all languages.
- **Factory pattern**: create any provider by name, no manual imports needed.
- **E2E tests**: every language/provider combination tested against real APIs.
- **CLI demo** (Python): interactive comparison of all providers.
- **Dropmail captcha solver chain**: Custom captcha solver functions for Dropmail.me — integrate external services (2captcha, anti-captcha), manual input workflows, or custom OCR solutions across all 7 languages.

## Highlights

- **Zero API keys required.** All providers are accessed through their public endpoints or public-facing pages.
- **Real E2E tests, not mocks.** Tests send actual emails via `swaks` and verify the full inbox cycle.
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
