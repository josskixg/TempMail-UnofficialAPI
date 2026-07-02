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
├── docs/                       # Web documentation + plan files
├── RELEASE_NOTES.md            # Per-version release notes
├── CHANGELOG.md                # Full change history
└── LICENSE                     # Apache 2.0
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
    ├── mailtm.py        # Mail.tm (REST)
    ├── guerrillamail.py  # GuerrillaMail (REST)
    ├── yopmail.py        # YOPmail (HTML scraping)
    ├── dropmail.py       # Dropmail.me (GraphQL)
    └── ...              # 12 more providers
```

Equivalent structure in each language:

| Component | Python | Go | JavaScript | Java | PHP | Rust | C# |
|-----------|--------|----|-----------|------|-----|------|-----|
| Interface | `base.py` | `tempmail.go` | `base.js` | Abstract class | Abstract class | `lib.rs` trait | Interface |
| Models | `models.py` | `providers/models.go` | `models.js` | Model classes | Model classes | `models.rs` | Model classes |
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

Represents an inbox listing entry (summary, no body).

| Field | Type | Description |
|-------|------|-------------|
| `id` | `str` | Provider-assigned message identifier |
| `sender` | `str` | Sender email address |
| `subject` | `str` | Message subject line |
| `date` | `datetime` | Timestamp of receipt |
| `preview` | `str` | Short plain-text preview (max 100 chars), HTML-stripped |
| `has_attachments` | `bool` | True if inbox data indicates attachment presence |

### MessageDetail

Extends Message with full content and metadata. All derived fields are **auto-normalized** by the model layer — providers simply populate what they have.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `body_text` | `str` | `""` | Plain text body. Auto-stripped from `body_html` if provider only returns HTML. |
| `body_html` | `str` | `""` | Raw HTML body as-is from provider. |
| `body_preview` | `str` | `""` | First 200 chars of `body_text`, auto-filled. |
| `content_type` | `str` | `"text/plain"` | Auto-inferred: `text/plain`, `text/html`, or `multipart/alternative`. |
| `is_html` | `bool` | `False` | Auto-computed: `True` if `body_html` is non-empty. |
| `raw` | `str` | `""` | Raw MIME string if provider supplies it. |
| `headers` | `dict[str,str]` | `{}` | Email headers (From, To, Message-ID, etc.). |
| `cc` | `list[str]` | `[]` | CC recipients. |
| `reply_to` | `str` | `""` | Reply-To address. |
| `message_id` | `str` | `""` | Message-ID header value. |
| `size` | `int` | `0` | Email size in bytes. |
| `has_attachments` | `bool` | `False` | Auto-set from attachments list. |
| `attachments` | `list[dict]` | `[]` | Attachment metadata: `{name, content_type, size, id}`. |

### Normalization Rules

After `read_message()` returns, the model layer auto-fills derived fields:

```
1. body_html set, body_text empty  → strip HTML tags → fill body_text
2. body_text set, body_html empty  → content_type = "text/plain", is_html = False
3. both set                        → content_type = "multipart/alternative", is_html = True
4. body_preview                    → always body_text[:200] (plain text, never HTML)
5. is_html                         → auto-computed, never set manually by providers
```

Providers do **not** need to implement HTML stripping — they only populate `body_text` and/or `body_html` with whatever the upstream API returns.

## Provider Pattern

Providers are categorized by how they communicate with the upstream service:

| Method | Providers | Notes |
|--------|-----------|-------|
| **REST API** | Mail.tm, GuerrillaMail, ncaori, zoromail, tempmail.lol, tempmailc, temp-mail.io, tempmail.plus, 10minutemail | Standard HTTP GET/POST to JSON endpoints. |
| **GraphQL** | Dropmail.me | Queries and mutations over a GraphQL endpoint. |
| **HTML Scraping** | YOPmail, emailfake, generator.email, email-temp.com, mailnesia | Parse HTML pages with BeautifulSoup (Python) or equivalent DOM parser. |

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
provider, err := tempmail.NewProvider("mail.tm", nil)
```

Supported names: `mail.tm`, `guerrillamail`, `yopmail`, `dropmail`, `1secemail`, `ncaori`, `zoromail`, `tempmail.lol`, `tempmailc`, `temp-mail.io`, `tempmail.plus`, `emailfake`, `generator.email`, `email-temp`, `mailnesia`, `10minutemail`.

The factory maps names to provider classes via a registry dictionary/switch, keeping provider imports internal.

## E2E Test Structure

Tests live alongside each language implementation:

```
python/tests/          # pytest
go/*_test.go           # go test
javascript/tests/      # node --test
java/src/test/         # JUnit / exec:java
php/tests/             # PHPUnit
rust/tests/            # cargo test --test e2e
csharp/TempMail.Tests/ # dotnet test
```

Each test suite covers:

- All 16 providers for the generate → inbox → read → delete cycle.
- Error paths: unknown message ID, deleted email.
- `wait_for_email` with a real email send via the Resend API.

Tests run against live provider APIs. See [TESTING.md](TESTING.md) for details on rate limits and test tooling.
