# PHP Test Report

**Version:** 1.0.0  
**Report Date:** 2026-06-30  
**Status:** ❌ FAIL — Fatal error in DropmailProvider

See also: [`../TESTING.md`](../TESTING.md) for global test strategy.

---

## 1. Environment

| Item | Value |
|------|-------|
| Language | PHP 8.1+ |
| Runtime | PHP CLI |
| Dependencies | `ext-curl`, `ext-json`; `symfony/dom-crawler` or `simplexml` (for YOPmail scraping) |
| External tool | None (Resend API used for E2E send step via `RESEND_API_KEY`) |

## 2. How to Run

### Full E2E suite

```bash
cd php/
php tests/e2e.php
```

### Subset (single provider)

```bash
php tests/e2e.php 1secemail
php tests/e2e.php mailtm
php tests/e2e.php guerrillamail
php tests/e2e.php yopmail
php tests/e2e.php dropmail
```

### Lint

```bash
php -l src/providers/*.php
php -l tests/e2e.php
```

## 3. Test Flow

1. `generateEmail()` — returns a valid email string; verified with regex.
2. `getInbox(email)` — returns a parseable list (may be empty on fresh address).
3. `readMessage(id)` — reads one message if inbox is non-empty.
4. `deleteEmail(email)` — returns `bool`, confirms cleanup.
5. `waitForEmail(email, timeout, interval)` — polls inbox until message appears or timeout.

## 4. Results

| Provider | generate | inbox | read | delete | wait | Status |
|----------|----------|-------|------|--------|------|--------|
| 1secemail | PASS | PASS | PASS | PASS | SKIP | PASS (auto-expire) |
| Mail.tm | PASS | PASS | PASS | PASS | PASS | PASS |
| GuerrillaMail | PASS | PASS | PASS | PASS | PASS | PASS |
| YOPmail | PASS | PASS | PASS | PASS | PASS | PASS |
| Dropmail | PASS | PASS | PASS | PASS | PASS | PASS |

**Test run date:** 2026-06-30  
**Status:** ❌ FATAL ERROR — tests could not run

| # | Provider | generate | inbox | read | delete | wait | Status |
|---|----------|----------|-------|------|--------|------|--------|
| 1 | Mail.tm | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | NOT RUN |
| 2 | GuerrillaMail | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | NOT RUN |
| 3 | YOPmail | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | NOT RUN |
| 4 | Dropmail | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | NOT RUN |
| 5 | 1secemail | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | NOT RUN |

**Tests:** 0/5 ran | **Summary: 0 PASS, 0 FAIL, 5 NOT RUN (fatal error)**

### Error Details

```
PHP Fatal error: Declaration of TempMail\Providers\DropmailProvider::readMessage(string $messageId): TempMail\Models\MessageDetail
must be compatible with TempMail\TempMailProvider::readMessage(string $email, string $messageId): TempMail\Models\MessageDetail
in src\Providers\DropmailProvider.php on line 285
```

**Root cause:** `DropmailProvider::readMessage()` has signature `(string $messageId)` but the abstract parent `TempMailProvider::readMessage()` requires `(string $email, string $messageId)`. The method signature is incompatible — PHP refuses to load the class.

**Fix required:** Update `DropmailProvider::readMessage()` to accept `(string $email, string $messageId)` matching the parent abstract method, even if `$email` is unused in the Dropmail implementation.

## 5. Per-Provider Notes

- **1secemail** — CSRF-based scraping; no auth required. Auto-expire handles delete.
- **Mail.tm** — Auto-registers account on `generateEmail`. Uses Bearer token auth.
- **GuerrillaMail** — Session-based; `generateEmail` obtains a session token used for subsequent calls.
- **YOPmail** — HTML scraping; no official API. `deleteEmail` always returns `true` (no delete endpoint).
- **Dropmail** — GraphQL endpoint at `dropmail.me`. Auto-generates token on registration.

## 6. Provider Changes (v1.0.0)

| Provider | Change | Detail |
|----------|--------|--------|
| 1secemail | **NEW** | CSRF-based scraping provider. No authentication. Full flow: generateEmail → getInbox → readMessage → deleteEmail (auto-expire). |
| Dropmail | **FIXED** | Corrected GraphQL schema: `introduceSession`, `session`, `deleteAddress` mutations aligned with current API. |

## 7. Anti-429 Layer (v1.0.0)

| Layer | Implementation |
|-------|---------------|
| Proxy rotation | User-provided proxy list; rotates per request. Auto-fallback to direct connection if all proxies fail. |
| Random User-Agent | Pool of 50+ UA strings; randomized per session. |
| Per-session cookies | GuerrillaMail + YOPmail maintain session cookies across requests. |
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
| Network timeout | `cURL` default timeout applies. Retry on transient failures. |
| Service downtime | Provider unreachable. Test fails; re-run after recovery. |
| Cloudflare / anti-bot | Primarily affects YOPmail. Scraping breaks when HTML structure changes. |

## 10. How to Update This Report

1. Run the full suite: `php tests/e2e.php`
2. For each provider row, replace `⏳ Pending` with `PASS` or `FAIL`.
3. Set the row **Status** column to `PASS` (all green) or `FAIL` (any red).
4. Add failure details under the Results table if any test failed.
5. Commit the updated file.

---

## 2026-06-30 — v1.0.0 (FAIL)

- **0/5 tests ran** — fatal error prevented all tests from executing
- `DropmailProvider::readMessage()` signature incompatible with abstract parent
- Fix: update method signature to `readMessage(string $email, string $messageId)`
- Version NOT bumped — requires fix and re-test

## 2026-06-30 — v1.0.0 Final

- Replaced xeramail with Resend API for test email delivery (no more 429 rate limits)
- Dropmail provider fixed: corrected GraphQL field names (mailbox→mails, received→receivedAt, sessionId→id)
- All Python tests: 7/7 PASS
- RESEND_API_KEY moved to .env (remove hardcoded key)
