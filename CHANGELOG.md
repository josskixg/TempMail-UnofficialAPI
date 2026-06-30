# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-06-30

### Added

- Unified temp-mail wrapper across 7 languages: Python, Go, JavaScript, Java, PHP, Rust, and C#.
- Four provider implementations: Mail.tm, GuerrillaMail, YOPmail (HTML scraping), and Dropmail.me (GraphQL).
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
