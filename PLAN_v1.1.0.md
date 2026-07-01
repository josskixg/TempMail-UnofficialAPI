# Implementation Plan - Version 1.1.0 (10 New Confidential Providers)

This plan details the roadmap and strategy for adding 10 new disposable email providers to the TempMail Unofficial API suite across all 7 supported languages.

## 🎯 Target Version: v1.1.0

The goal of this release is to expand the selection of providers, improving fallback resilience if one or more services experience downtime or implement strict Cloudflare protections.

## 🌐 Planned Providers (Confidential - TBA)

To prevent premature rate-limiting and protect the integration pipeline during active development, the specific target domains are redacted until the official v1.1.0 release.

| # | Service (Codename) | proposed Engine | Estimated Difficulty | Integration Details |
|---|--------------------|-----------------|----------------------|---------------------|
| 1 | **Provider Alpha**  | HTML Scraping / API | ⚡ Medium | Scrapes inbox table; parses unique redirect URLs directly from message bodies. |
| 2 | **Provider Beta**   | REST API (JSON) | ✅ Easy | Calls clean JSON REST endpoints for mailbox creation and query retrieval. |
| 3 | **Provider Gamma**  | REST API | ⚡ Medium | Uses stable public REST developer endpoints with JSON payload responses. |
| 4 | **Provider Delta**  | HTML Scraping | 🔴 Hard | Employs cookie session state tracking and custom anti-bot token bypasses. |
| 5 | **Provider Epsilon**| HTML Scraping / Token | 🔴 Hard | Requires initial handshake token parsing and customized browser user-agents. |
| 6 | **Provider Zeta**   | HTML Scraping | ⚡ Medium | Parses custom internal JSON data blocks embedded inside loaded scripts. |
| 7 | **Provider Eta**    | HTML Scraping | ✅ Easy | Straightforward list scraping from public user-facing directories. |
| 8 | **Provider Theta**  | REST / Scraping | ⚡ Medium | Automates email forward setups or quick temporary mailbox allocations. |
| 9 | **Provider Iota**   | REST API (JSON) | ✅ Easy | Fast JSON endpoints, popular for automated testing scripts. |
| 10| **Provider Kappa**  | REST API | ✅ Easy | Calls direct JSON endpoints supporting temporary multi-language sessions. |

## 🛠️ Implementation Strategy & Architecture

### 1. File Path & Class Registration Mapping
For each language, the new providers will be placed in the respective provider modules:

- **Go**: `go/providers/`
- **Python**: `python/tempmail_wrapper/providers/`
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

### 4. E2E Test Suites Expansion
- Update E2E test suites inside each language directory to register and test all 15 providers sequentially.
