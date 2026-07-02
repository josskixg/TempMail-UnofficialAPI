# AI Agent Instructions (AGENTS.md)

Welcome, agent! This document serves as the guide for context, development rules, patterns, and instructions for maintaining the **TempMail-UnofficialAPI** codebase.

---

## 1. Project Overview

This repository is a multi-language SDK wrapper for **16 temporary email providers** across **7 programming languages**:
- Go
- Rust
- Python
- JavaScript (Node.js)
- PHP
- Java
- C# (.NET 8.0)

### Supported Providers
1. `mailtm`
2. `guerrillamail`
3. `yopmail`
4. `dropmail`
5. `1secemail`
6. `ncaori`
7. `zoromail`
8. `tempmail.lol`
9. `tempmailc`
10. `temp-mail.io`
11. `tempmail.plus`
12. `emailfake`
13. `generator.email`
14. `mailnesia`
15. `10minutemail`
16. `email-temp`

---

## 2. Directory Structure

```
TempMail-UnofficialAPI/
├── go/             # Go module & E2E tests
├── rust/           # Rust crate & E2E tests
├── python/         # Python package & pytest E2E tests
├── javascript/     # Node.js module & built-in test runner
├── php/            # Composer package & E2E script
├── java/           # Maven project & JUnit/exec E2E tests
├── csharp/         # MSBuild Solution & xUnit tests
├── docs/           # Documentation
│   └── plan/       # Release and migration plans
├── CHANGELOG.md    # Global changelog
├── RELEASE_NOTES.md# Release notes summary
└── AGENTS.md       # (This file) Rules & instructions
```

---

## 3. Development Guidelines & Rules

To maintain high code quality and consistency across all 7 languages, follow these rules:

### 3.1 Model Consistency & Normalization
*   **Normalized Models**: All language packages must expose `Message` and `MessageDetail` structures (or equivalents) with consistent fields:
    *   `id` / `uid`: A string representing the unique identifier of the message.
    *   `sender` / `from`: The sender's email address.
    *   `subject`: The subject line.
    *   `date` / `createdAt`: Date/time structure (normalized to UTC DateTime / DateTimeOffset).
    *   `body_text` / `text`: Plaintext message content.
    *   `body_html` / `html`: HTML message content.
*   **Flexible Parsing**: Some API providers return IDs or dates as numbers (epoch timestamps, integer IDs) or strings. Use dynamic JSON parsing (e.g., `JsonElement` in C#, `json.RawMessage` in Go, dynamic types in Python/JS) to prevent deserialization errors.
*   **Tempmail.lol Key Fallback**: For `tempmail.lol`, check `_id` first, falling back to `id` and `uid`.
*   **Regex constraints**: Go's native `regexp` package does not support backreferences (e.g., `\1`). Do not use expressions like `<(style|script)[^>]*>.*?</\1>` in Go; implement manual token-stripping or custom logic instead.

### 3.2 Provider Quirks
*   **Ncaori Mail**: Has no single-message retrieval endpoint. `readMessage` is expected to fail or throw an exception. The E2E tests must bypass/skip `readMessage` assertions for this provider.
*   **YOPmail, Mailnesia & 10minutemail**: Scraping-based public mailboxes. YOPmail is scraped via standard selectors. Mailnesia is bypassed using IP rotation headers. 10minutemail uses `10minutemail.net` (bypassing `10minutemail.com`) and requires decoding inline Cloudflare-obfuscated email links.
*   **Dropmail**: GraphQL endpoint with OCR fallback.

### 3.3 Test Environment
*   **Resend Integration**: Integration/E2E tests send real emails using the Resend API to verify delivery.
*   **Sender Address**: Always use `"onboarding@rokupusu.web.id"`. Do not use the default resend domain.
*   **Environment Variables**: All tests load the API key from `RESEND_API_KEY` (configured via a `.env` file at the root or language folder).
*   **Graceful Soft-Skips**: E2E tests must not fail if Resend rate-limits requests (HTTP 429) or if third-party providers return Cloudflare blocks (HTTP 403). Instead, log a warning and skip the assertions.

---

## 4. How to Run E2E Tests

### JavaScript
```bash
cd javascript/
npm install
node --test tests/e2e.test.js
```

### Python
```bash
cd python/
pip install -r requirements.txt
pytest -v tests/test_e2e.py --e2e
```

### PHP
```bash
cd php/
composer install
php tests/e2e.php
```

### Go
```bash
cd go/
go test -v -tags=e2e ./...
```

### Rust
```bash
cd rust/
cargo test --test e2e -- --ignored
```

### Java
```bash
cd java/
mvn test-compile exec:java -Dexec.mainClass=com.tempmail.E2ETest -Dexec.classpathScope=test
```

### C#
```bash
cd csharp/
dotnet test --verbosity normal
```

---

## 5. Changelog & Report Rules
*   When updating `TEST_REPORT.md` files:
    *   Append new test runs to the history log at the bottom of the file (newest first).
    *   Preserve historical logs for audit.
*   Version releases must update:
    *   Root `CHANGELOG.md`
    *   Root `RELEASE_NOTES.md`
    *   Root `UPGRADE.md`
    *   Subdirectories' `README.md` versions and links.
