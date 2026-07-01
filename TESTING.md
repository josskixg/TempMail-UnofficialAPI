# Testing

## Approach

All tests are **end-to-end** against real provider APIs. There are no mocks or stubs. This ensures each implementation actually works with the live service, not just a recorded response.

The trade-off: tests depend on network availability and provider uptime. See [Handling Rate Limits](#handling-rate-limits) below.

## Test Flow

Every test follows this sequence:

1. **Generate** — create a temporary email address.
2. **Send** (Optional) — deliver a test message to that address via the Resend API (requires `RESEND_API_KEY`).
3. **Poll** — call `get_inbox` (or `wait_for_email`) to check for the test message (relaxed if no email was sent).
4. **Read** — fetch message details and verify content (if available).
5. **Delete** — call `delete_email` to clean up.

Each step has a retry/backoff wrapper to account for provider latency.

## Per-Language Commands

### Python

```bash
cd python/
uv sync
uv run pytest tests/ --verbose
```

### Go

```bash
cd go/
go test -v -run E2E
```

### JavaScript

```bash
cd javascript/
npm install
npm test
```

### Java

```bash
cd java/
mvn test
```

### PHP

```bash
cd php/
composer install
./vendor/bin/phpunit
```

### Rust

```bash
cd rust/
cargo test -- --nocapture
```

### C#

```bash
cd csharp/TempMail.Tests/
dotnet test
```

## Expected Environment Setup

- No external CLI tools (like `swaks`) are required.
- The E2E tests send test emails via the **Resend API** and require a valid `RESEND_API_KEY` environment variable.
- If `RESEND_API_KEY` is not present, the test suites will gracefully skip the actual email sending and inbox check steps (assertions on inbox contents are relaxed, and tests still pass successfully by validating the remaining actions like generating and deleting).
- To enable the full testing loop:
  1. Sign up on [resend.com](https://resend.com).
  2. Create an API key.
  3. Place it in a `.env` file under the directory of the language wrapper you are testing, e.g. `RESEND_API_KEY=re_1234567890`.

## Handling Rate Limits

Provider APIs may rate-limit or throttle requests. Tests handle this by:

- **Retry with backoff** — failed API calls are retried 3 times with exponential delay (1s, 3s, 5s).
- **Polling intervals** — `wait_for_email` uses a configurable interval (default 5 seconds) to avoid hammering the inbox endpoint.
- **Send delay** — tests wait 10 seconds after sending a message via Resend before first polling the inbox.
- **Sequential execution** — provider tests run one at a time (not parallel) within each language to avoid hitting rate caps.

If a provider is down during a test run, that provider's tests will fail. Re-run after the provider recovers. There is no skip mechanism by design — a failing test means the integration is broken and should be investigated.

## Adding New Tests

When adding a new provider test:

1. Follow the generate → send → poll → read → delete flow.
2. Use the existing retry/backoff helpers in the language's test suite.
3. Test both success and error paths (unknown message ID, deleted email, etc.).
4. Keep test timeouts reasonable: 60 seconds for `wait_for_email`, 30 seconds for retry loops.

When adding a new language:

1. Port the full test suite (all 5 providers) from the reference implementation.
2. Use the language-native test framework (pytest, testing, Jest, JUnit, PHPUnit, cargo test, xUnit).
3. Ensure tests can run from a single command (see above).

## CI Considerations

E2E tests require network access. In CI:

- Provide `RESEND_API_KEY` as a secret or environment variable to enable full delivery testing.
- Allow sufficient timeouts (provider responses can take 10-30 seconds).
- Run language test suites sequentially to avoid cross-language rate limit collisions.
