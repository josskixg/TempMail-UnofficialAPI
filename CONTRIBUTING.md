# Contributing

Thanks for considering contributing to TempMail-UnofficialAPI. This document covers how to add providers, languages, and tests.

Please read our [Code of Conduct](CODE_OF_CONDUCT.md) before participating.

## Project Structure

Each language lives in its own top-level directory (`python/`, `go/`, `javascript/`, etc.) and follows the same interface contract. See [ARCHITECTURE.md](ARCHITECTURE.md) for the full layout.

## Adding a New Provider

Every provider must implement the common interface:

| Method | Description |
|--------|-------------|
| `generate_email()` | Generate or assign a new temporary email address |
| `get_inbox(email)` | Return a list of `Message` objects |
| `read_message(message_id)` | Return a `MessageDetail` with body and attachments |
| `delete_email(email)` | Delete the mailbox; return `True`/`true` on success |
| `wait_for_email(email, timeout, interval)` | Poll `get_inbox` until a matching message arrives |

### Steps (Python example)

1. Create `python/tempmail_wrapper/providers/newprovider.py`.
2. Subclass `TempMailProvider` from `base.py` and implement all abstract methods.
3. Register the provider in `factory.py` under `_PROVIDERS`.
4. Add `__init__.py` export in `providers/`.
5. Write E2E tests in `python/tests/`.
6. Repeat for each language, following the same contract.

### Provider types

- **REST API**: Mail.tm, GuerrillaMail — standard HTTP calls.
- **GraphQL**: Dropmail.me — query-based mutations.
- **HTML Scraping**: YOPmail — BeautifulSoup/DOM parsing with fallback regex.

Pick the method that matches the service's public interface. Prefer REST > GraphQL > Scraping in terms of stability.

## Adding a New Language

1. Create a new top-level directory (e.g., `kotlin/`).
2. Implement the interface contract using idiomatic patterns for that language.
3. Include data models (`Message`, `MessageDetail`) and an error hierarchy.
4. Add a factory function for provider creation by name.
5. Write E2E tests covering all 16 providers.
6. Add a `README.md` inside the language directory.

Use the existing Python or Go implementation as a reference for behavior parity.

## Adding E2E Tests

All tests are end-to-end against real provider APIs. No mocks. See [TESTING.md](TESTING.md) for the full testing guide.

The required test flow:

1. Generate email
2. Send a test message (via the Resend API or equivalent)
3. Poll inbox until the message arrives
4. Read the message and verify subject, sender, and body
5. Delete the email

## Pull Request Process

1. Fork the repository at [github.com/josskixg/TempMail-UnofficialAPI](https://github.com/josskixg/TempMail-UnofficialAPI).
2. Create a feature branch from `main`.
3. Implement changes following the patterns above.
4. Ensure E2E tests pass for the affected language(s).
5. Update the relevant per-language `README.md` if API surface changes.
6. Submit a pull request with a clear description of what was added or changed.

Pull requests should:

- Cover one feature or provider per PR.
- Pass existing E2E tests without regression.
- Follow the idiomatic style of the target language.
- Not introduce new dependencies without justification.

## Reporting Bugs

Open a [GitHub Issue](https://github.com/josskixg/TempMail-UnofficialAPI/issues/new) with:

- The language and provider affected.
- Steps to reproduce.
- Expected vs actual behavior.

For security issues, see [SECURITY.md](SECURITY.md).
