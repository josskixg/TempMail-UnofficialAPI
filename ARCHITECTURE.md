# Architecture

## Project Structure

```
TempMail-UnofficialAPI/
├── python/tempmail_wrapper/    # Python implementation
├── go/                         # Go implementation
├── javascript/src/             # JavaScript implementation (ES Modules)
├── java/src/                   # Java implementation (Maven)
├── php/src/                    # PHP implementation (Composer)
├── rust/src/                   # Rust implementation (Cargo)
├── csharp/TempMail/            # C# implementation (.NET)
├── AGENTS.md                   # AI agent usage guidelines
└── License                     # Apache 2.0
```

Each language directory is self-contained with its own build system, tests, and README.

## Per-Language Package Layout

Using Python as the reference (other languages follow the same logical structure):

```
tempmail_wrapper/
├── __init__.py          # Public exports
├── base.py              # Abstract provider interface (TempMailProvider)
├── models.py            # Data models (Message, MessageDetail)
├── factory.py           # Factory function (create_provider)
├── exceptions.py        # Error hierarchy
└── providers/
    ├── mailtm.py      # Mail.tm (REST)
    ├── guerrillamail.py # GuerrillaMail (REST)
    ├── yopmail.py       # YOPmail (HTML scraping)
    └── dropmail.py      # Dropmail.me (GraphQL)
```

Equivalent structure in each language:

| Component | Python | Go | JavaScript | Java | PHP | Rust | C# |
|-----------|--------|----|-----------|------|-----|------|-----|
| Interface | `base.py` | `tempmail.go` | `base.js` | Interface class | Abstract class | `lib.rs` trait | Interface |
| Models | `models.py` | `models.go` | `models.js` | Model classes | Model classes | `models.rs` | Model classes |
| Factory | `factory.py` | `NewProvider()` | `index.js` | Factory class | Factory class | Factory fn | Factory class |
| Errors | `exceptions.py` | `errors.go` | `errors.js` | Exception classes | Exception classes | `error.rs` | Exception classes |
| Providers | `providers/` | `providers/` | `src/providers/` | Provider classes | Provider classes | `providers/` | Provider classes |

## Common Interface Contract

All providers implement the same five methods:

| Method | Signature | Description |
|--------|-----------|-------------|
| `generate_email` | `() -> str` | Generates or assigns a temporary email address |
| `get_inbox` | `(email: str) -> list[Message]` | Lists messages in the inbox |
| `read_message` | `(message_id: str) -> MessageDetail` | Fetches full message content |
| `delete_email` | `(email: str) -> bool` | Deletes the mailbox |
| `wait_for_email` | `(email, timeout, interval, ...) -> Message?` | Polls inbox until a matching message arrives |

`wait_for_email` has default behavior (polling loop) defined in the base class/trait when possible, so providers only need to implement the core four methods.

## Data Models

### Message

Represents an inbox listing entry (no body).

| Field | Type | Description |
|-------|------|-------------|
| `id` | `str` | Provider-assigned message identifier |
| `sender` | `str` | Sender email address |
| `subject` | `str` | Message subject line |
| `date` | `datetime` | Timestamp of receipt |

### MessageDetail

Extends Message with full content. Inherits all Message fields.

| Field | Type | Description |
|-------|------|-------------|
| `body_text` | `str` | Plain text body |
| `body_html` | `str` | HTML body (may be HTML-rendered from plain text) |
| `attachments` | `list[dict]` | List of attachment metadata (name, content_type, size, id) |

## Provider Pattern

Providers are categorized by how they communicate with the upstream service:

| Method | Providers | Notes |
|--------|-----------|-------|
| **REST API** | Mail.tm, GuerrillaMail | Standard HTTP GET/POST to JSON endpoints. |
| **GraphQL** | Dropmail.me | Queries and mutations over a GraphQL endpoint. |
| **HTML Scraping** | YOPmail | Parse HTML pages with BeautifulSoup (Python) or equivalent DOM parser. |

Each provider handles its own:

- Endpoint URL construction
- Response parsing and normalization to `Message`/`MessageDetail`
- Error detection and mapping to the language's error hierarchy
- Retry/backoff on transient network failures

## Factory Pattern

A single factory function creates providers by name:

```python
# Python
from tempmail_wrapper import create_provider
provider = create_provider("mail.tm")
```

```go
// Go
provider, err := tempmail.NewProvider("mailtm", nil)
```

Supported names: `mail.tm`, `guerrillamail`, `yopmail`, `dropmail`.

The factory maps names to provider classes via a registry dictionary/switch, keeping provider imports internal.

## E2E Test Structure

Tests live alongside each language implementation:

```
python/tests/          # pytest
go/*_test.go           # go test
javascript/tests/      # Jest (or similar)
java/src/test/         # JUnit
php/tests/             # PHPUnit
rust/tests/            # cargo test
csharp/TempMail.Tests/ # xUnit
```

Each test suite covers:

- All 5 providers for the generate → inbox → read → delete cycle.
- Error paths: unknown message ID, deleted email.
- `wait_for_email` with a real email send via the Resend API.

Tests run against live provider APIs. See [TESTING.md](TESTING.md) for details on rate limits and test tooling.
