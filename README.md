<p align="center">
  <img src="./banner.svg" alt="TempMail Unofficial API Wrapper" width="800">
</p>

# 📬 TempMail Unofficial API — Multi-Language Wrappers

<p align="center">
  <strong>v1.1.0</strong> — Released 2026-07-02 &nbsp;|&nbsp; <a href="./RELEASE_NOTES.md">Release Notes</a> &nbsp;|&nbsp; <a href="./CHANGELOG.md">Changelog</a> &nbsp;|&nbsp; <a href="./docs/plan/PLAN_v1.1.0.md">v1.1.0 Plan</a>
</p>

[🇬🇧 English](./README.md) | [🇮🇩 Bahasa Indonesia](./README.id.md) | [🇨🇳 中文](./README.cn.md)

---

Collection of **unofficial wrappers** for various Temporary Email services, written in **7 programming languages**. One repo, one goal: programmatically create and manage disposable emails with ease.

## 🎯 Supported Languages

| Language | Folder | Package Manager | Status |
|----------|--------|-----------------|--------|
| Go | [`/go`](./go) | `go get` | ✅ Done |
| Python | [`/python`](./python) | `pip` | ✅ Done |
| Java | [`/java`](./java) | `Maven` / `Gradle` | ✅ Done |
| PHP | [`/php`](./php) | `Composer` | ✅ Done |
| JavaScript | [`/javascript`](./javascript) | `npm` / `yarn` | ✅ Done |
| Rust | [`/rust`](./rust) | `cargo` | ✅ Done |
| C# | [`/csharp`](./csharp) | `NuGet` | ✅ Done |

## 🌐 Supported TempMail Services

The library wrapper currently supports **16 temporary email services**:

| # | Service | Website | API Type | Auth | Difficulty | Status |
|---|---------|---------|----------|------|------------|--------|
| 1 | Mail.tm | [mail.tm](https://mail.tm) | REST+JSON | Bearer Token | ✅ Easy | Active |
| 2 | GuerrillaMail | [guerrillamail.com](https://www.guerrillamail.com) | REST | Session Token | ⚡ Medium | Active |
| 3 | YOPmail | [yopmail.com](https://yopmail.com) | HTML Scraping | None | ⚡ Medium | Active |
| 4 | Dropmail | [dropmail.me](https://dropmail.me) | GraphQL | Token (auto) | ✅ Easy | Active |
| 5 | 1secemail | [1secemail.com](https://www.1secemail.com) | REST | None | ✅ Easy | Active |
| 6 | Ncaori Mail+ | [nca.my.id](https://www.nca.my.id) | REST+JSON | None | ✅ Easy | Active |
| 7 | Zoromail | [zoromail.com](https://zoromail.com) | REST+JSON | None | ✅ Easy | Active |
| 8 | Tempmail.lol | [tempmail.lol](https://tempmail.lol) | REST+JSON | Token | ✅ Easy | Active |
| 9 | Tempmailc | [tempmailc.com](https://tempmailc.com) | REST+JSON | None | ✅ Easy | Active |
| 10 | Temp-mail.io | [temp-mail.io](https://temp-mail.io) | REST+JSON | Bearer Token | ⚡ Medium | Active |
| 11 | Tempmail.plus | [tempmail.plus](https://tempmail.plus) | REST+JSON | None | ✅ Easy | Active |
| 12 | Emailfake | [emailfake.com](https://emailfake.com) | HTML Scraping | Cookie | ⚡ Medium | Active |
| 13 | Generator.email | [generator.email](https://generator.email) | HTML Scraping | Cookie | ⚡ Medium | Active |
| 14 | Mailnesia | [mailnesia.com](https://mailnesia.com) | HTML Scraping | None | ⚡ Medium | Active |
| 15 | 10minutemail | [10minutemail.net](https://10minutemail.net) | HTML Scraping | Cookie Session | ⚡ Medium | Active (via 10minutemail.net) |
| 16 | Email-temp | [email-temp.com](https://email-temp.com) | HTML Scraping | Cookie | ⚡ Medium | Active |

## 📁 Project Structure

```
TempMail-UnofficialAPI/
├── go/                   # Go wrapper
├── python/               # Python wrapper
├── java/                 # Java wrapper
├── php/                  # PHP wrapper
├── javascript/           # Node.js / JavaScript wrapper
├── rust/                 # Rust wrapper
├── csharp/               # C# / .NET wrapper
├── docs/
│   └── plan/
│       └── PLAN_v1.1.0.md  # v1.1.0 implementation plan
├── README.md             # English (default)
├── README.id.md          # Bahasa Indonesia
├── README.cn.md          # 中文
├── RELEASE_NOTES.md      # Per-version release notes
├── CHANGELOG.md          # Full change history
├── LICENSE               # Apache 2.0
└── NOTICE                # Attribution & disclaimer
```

## 🚀 Quick Start

Each language has its own README. Click the folder above for installation details and usage examples.

### General Example (Pseudocode)

```
// 1. Generate a temporary email
email = tempmail.generate()
// → "random123@mail.tm"

// 2. Check inbox
messages = tempmail.get_inbox(email)

// 3. Read a message
if messages.length > 0:
    content = tempmail.read_message(messages[0].id)

// 4. Delete (optional, auto-expire works too)
tempmail.delete(email)
```

## ⚡ API Interface (All Languages)

All wrappers implement a consistent interface:

| Method | Description | Return |
|--------|-------------|--------|
| `generate_email()` | Generate a temporary email address | `string` (email address) |
| `get_inbox(email)` | Retrieve list of messages | `[]Message` |
| `read_message(id)` | Read message content | `MessageDetail` |
| `delete_email(email)` | Delete email (cleanup) | `bool` |
| `wait_for_email(email, timeout)` | Poll for new messages | `Message` or `null` |

## 📦 Data Model

### Message
- `id` — Unique message identifier
- `from` — Sender address
- `subject` — Email subject
- `date` — Received timestamp

### MessageDetail (extends Message)
- `body_text` — Plain text email body
- `body_html` — HTML email body (if available)
- `attachments` — List of attachment metadata

## 🛡️ Disclaimer

> **⚠️ IMPORTANT**
>
> - This project is **UNOFFICIAL** — not affiliated with any tempmail service.
> - API endpoints may change at any time without notice.
> - Use for **testing, development, or personal automation** only.
> - Do not use for spam, fraud, or any illegal activity.
> - Some services have rate limits — use responsibly.

## 🤝 Contributing

Want to add a language? Fix a bug? Go ahead:

1. Fork this repo at [github.com/josskixg/TempMail-UnofficialAPI](https://github.com/josskixg/TempMail-UnofficialAPI/fork)
2. Create a branch: `feat/add-kotlin-wrapper`
3. Commit & push
4. Open a Pull Request

### Contribution Guidelines

- Follow the existing interface structure
- Add usage examples in the per-language README
- Never hardcode API keys (use environment variables)
- Test before submitting a PR

## 🗺️ Roadmap

v1.1.0 shipped with 16 services across 7 languages. Planned for upcoming releases:

- **Improved YOPmail & Scraping resilience** — anti-bot bypass improvements and headless support
- **WebSocket support** for Dropmail.me and Mail.tm (real-time inbox subscription)
- **More languages** — Kotlin, Swift, and Ruby wrappers
- **CLI tool** — unified command-line tool to manage temporary mailboxes across all providers directly from your terminal

Contributions welcome — see [CONTRIBUTING.md](./CONTRIBUTING.md) and open a [PR](https://github.com/josskixg/TempMail-UnofficialAPI/pulls) or [Issue](https://github.com/josskixg/TempMail-UnofficialAPI/issues).

## 📄 License

Apache License 2.0 — see [LICENSE](./LICENSE) and [NOTICE](./NOTICE).

---

<p align="center">
  <strong>🌟 Star this repo if it helps your project!</strong><br>
  Built with 🫠 by the community, for the community.
</p>
