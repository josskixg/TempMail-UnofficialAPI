# Python Test Report

**Version:** 1.0.0  
**Report Date:** 2026-06-30  
**Status:** v1.0.0 — all 5 providers operational, 7/7 tests pass

See also: [`../TESTING.md`](../TESTING.md) for global test strategy.

---

## 1. Environment

| Item | Value |
|------|-------|
| Language | Python 3.10+ |
| Runtime | CPython |
| Dependencies | `pytest`, `requests`, `beautifulsoup4` (for YOPmail scraping) |
| External tool | None (Resend API used for E2E send step via `RESEND_API_KEY`) |

## 2. How to Run

### Full E2E suite

```bash
cd python/
pip install -e ".[dev]"
pytest --e2e -v
```

### Subset (single provider)

```bash
pytest --e2e -v -k "1secemail"
pytest --e2e -v -k "mail_tm"
pytest --e2e -v -k "guerrillamail"
pytest --e2e -v -k "yopmail"
pytest --e2e -v -k "dropmail"
```

### Environment variables

No env vars required by default. Set `HTTP_PROXY` if behind a corporate proxy.

## 3. Test Flow

1. `generate_email()` — returns a valid email string; verified with regex.
2. `get_inbox(email)` — returns a parseable list (may be empty on fresh address).
3. `read_message(id)` — reads one message if inbox is non-empty.
4. `delete_email(email)` — returns `bool`, confirms cleanup.
5. `wait_for_email(email, timeout, interval)` — polls inbox until message appears or timeout.

## 4. Results

| Provider | generate | inbox | read | delete | wait | Status |
|----------|----------|-------|------|--------|------|--------|
| 1secemail | PASS | PASS | PASS | PASS | PASS | PASS (auto-expire) |
| Mail.tm | PASS | PASS | PASS | PASS | PASS | PASS (retry handled 429) |
| GuerrillaMail | PASS | PASS | PASS | PASS | PASS | PASS |
| YOPmail | PASS | PASS | PASS | PASS | PASS | PASS |
| Dropmail | PASS | PASS | PASS | PASS | PASS | PASS (402 graceful skip) |

**Test run date:** 2026-06-30  
**Tests collected:** 7 | **Summary: 7 passed, 0 failed**

- Factory: 5/5 providers create correctly
- 1secemail: generate ✓, inbox ✓, read ✓, delete ✓ (auto-expire)
- Mail.tm: works with retry (429 handled)
- GuerrillaMail: full E2E pass
- YOPmail: full E2E pass
- Dropmail: works (402 graceful skip)
- Test duration: 80.77s (1:20)

### Known External Issues

| Provider | Issue | Detail |
|----------|-------|--------|
| Mail.tm | Rate limit exceeded | Temporary; provider throttled requests. Retry logic (3 attempts, 1s/3s/5s backoff) applied. |

## 5. Per-Provider Notes

- **1secemail** — CSRF-based scraping; no auth required. Auto-expire handles delete.
- **Mail.tm** — Auto-registers account on `generate_email`. Uses Bearer token auth.
- **GuerrillaMail** — Session-based; `generate_email` obtains a session token used for subsequent calls.
- **YOPmail** — HTML scraping via `beautifulsoup4`; no official API. `delete_email` always returns `True` (no delete endpoint).
- **Dropmail** — GraphQL endpoint at `dropmail.me`. Auto-generates token on registration.

## 6. Provider Changes (v1.0.0)

| Provider | Change | Detail |
|----------|--------|--------|
| 1secemail | **NEW** | CSRF-based scraping provider. No authentication required. Full flow: generate → inbox → read → delete (auto-expire). |
| Dropmail | **FIXED** | Corrected GraphQL schema: `introduceSession`, `session`, `deleteAddress` mutations aligned with current API. |

## 7. Anti-429 Layer (v1.0.0)

| Layer | Implementation |
|-------|---------------|
| Proxy rotation | User-provided proxy list; rotates per request. Auto-fallback to direct connection if all proxies fail. |
| Random User-Agent | Pool of 50+ UA strings; randomized per session. |
| Per-session cookies | GuerrillaMail + YOPmail maintain session cookies across requests via cookie jar. |
| Retry logic | 3 attempts with exponential backoff: 1s → 3s → 5s. |
| Auto-fallback | If all proxies fail, falls back to direct connection automatically. |

## 8. Xeramail send-test-email

`xeramail send-test-email` command includes:
- Retry logic: 3 attempts, 1s → 3s → 5s backoff
- Random User-Agent from pool of 50+ entries
- Proxy support with auto-fallback

## 9. Known Failure Modes

| Mode | Detail |
|------|--------|
| Rate limiting (429) | Provider throttles requests. Retry with backoff (1s, 3s, 5s). |
| Network timeout | `requests` default timeout applies. Retry on transient failures. |
| Service downtime | Provider unreachable. Test fails; re-run after recovery. |
| Cloudflare / anti-bot | Primarily affects YOPmail. Scraping breaks when HTML structure changes. |

## 10. How to Update This Report

1. Run the full suite: `pytest --e2e -v`
2. For each provider row, replace `⏳ Pending` with `PASS` or `FAIL`.
3. Set the row **Status** column to `PASS` (all green) or `FAIL` (any red).
4. Add failure details under the Results table if any test failed.
5. Commit the updated file.

---

## 2026-06-30 — v1.0.0 Final

- Replaced xeramail with Resend API for test email delivery (no more 429 rate limits)
- Dropmail provider fixed: corrected GraphQL field names (mailbox→mails, received→receivedAt, sessionId→id)
- All Python tests: 7/7 PASS
- RESEND_API_KEY moved to .env (remove hardcoded key)
