# Java Test Report

**Version:** 1.0.0  
**Report Date:** 2026-06-30  
**Status:** ❌ FAIL — Compilation error in DropmailProvider

See also: [`../TESTING.md`](../TESTING.md) for global test strategy.

---

## 1. Environment

| Item | Value |
|------|-------|
| Language | Java 17+ |
| Runtime | JDK 17+ |
| Dependencies | Standard library only; `java.net.HttpURLConnection`, `com.google.gson` (or Jackson) |
| Build tool | Maven |
| External tool | None (Resend API used for E2E send step via `RESEND_API_KEY`) |

## 2. How to Run

### Full E2E suite

```bash
cd java/
mvn compile && java -cp target/classes com.tempmail.E2ETest
```

### Subset (single provider)

```bash
mvn compile && java -cp target/classes com.tempmail.E2ETest 1secemail
mvn compile && java -cp target/classes com.tempmail.E2ETest mailtm
mvn compile && java -cp target/classes com.tempmail.E2ETest guerrillamail
mvn compile && java -cp target/classes com.tempmail.E2ETest yopmail
mvn compile && java -cp target/classes com.tempmail.E2ETest dropmail
```

### Build

```bash
mvn compile
```

## 3. Test Flow

1. `generateEmail()` — returns a valid email string; verified with regex.
2. `getInbox(email)` — returns a parseable list (may be empty on fresh address).
3. `readMessage(id)` — reads one message if inbox is non-empty.
4. `deleteEmail(email)` — returns `boolean`, confirms cleanup.
5. `waitForEmail(email, timeout, interval)` — polls inbox until message appears or timeout.

## 4. Results

**Test run date:** 2026-06-30  
**Status:** ❌ COMPILATION ERROR — tests could not run

| # | Provider | generate | inbox | read | delete | wait | Status |
|---|----------|----------|-------|------|--------|------|--------|
| 1 | Mail.tm | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | NOT RUN |
| 2 | GuerrillaMail | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | NOT RUN |
| 3 | YOPmail | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | NOT RUN |
| 4 | Dropmail | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | NOT RUN |
| 5 | 1secemail | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ | NOT RUN |

**Tests:** 0/5 ran | **Summary: 0 PASS, 0 FAIL, 5 NOT RUN (compilation failure)**

### Error Details

```
[ERROR] DropmailProvider.java:[42,8] DropmailProvider is not abstract and does not override
  abstract method waitForEmail(String,Duration,Duration) in TempMailProvider

[ERROR] DropmailProvider.java:[107,46] cannot find symbol
  symbol: method getDouble(String,int)
  location: variable first of type SimpleJson.JsonObject

[ERROR] DropmailProvider.java:[109,44] method getString in class SimpleJson.JsonObject
  cannot be applied to given types;
  required: String
  found:    String,String
  reason: actual and formal argument lists differ in length
```

**Root causes:**
1. `DropmailProvider` does not implement `waitForEmail(String, Duration, Duration)` from the abstract parent `TempMailProvider`
2. `SimpleJson.JsonObject` does not have a `getDouble(String, int)` method — wrong method name or signature
3. `SimpleJson.JsonObject.getString()` is called with 2 args `(String, String)` but only accepts 1 arg `(String)`

**Fix required:**
- Add `waitForEmail(String email, Duration timeout, Duration interval)` override to `DropmailProvider`
- Fix `getDouble` call to use correct `SimpleJson.JsonObject` API
- Fix `getString` call to match the single-argument signature

## 5. Per-Provider Notes

- **1secemail** — CSRF-based scraping; no auth required. Compiled, factory registered.
- **Mail.tm** — Auto-registers account on `generateEmail`. Uses Bearer token auth.
- **GuerrillaMail** — Session-based; `generateEmail` obtains a session token used for subsequent calls.
- **YOPmail** — HTML scraping via `Jsoup`; no official API. `deleteEmail` always returns `true` (no delete endpoint).
- **Dropmail** — GraphQL endpoint at `dropmail.me`. Auto-generates token on registration.

## 6. Provider Changes (v1.0.0)

| Provider | Change | Detail |
|----------|--------|--------|
| 1secemail | **NEW** | CSRF-based scraping provider. No authentication. Compiled, factory registered. |
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
| Network timeout | `HttpURLConnection` default timeout applies. Retry on transient failures. |
| Service downtime | Provider unreachable. Test fails; re-run after recovery. |
| Cloudflare / anti-bot | Primarily affects YOPmail. Scraping breaks when HTML structure changes. |

## 10. How to Update This Report

1. Run the full suite: `mvn compile && java -cp target/classes com.tempmail.E2ETest`
2. For each provider row, replace `⏳ Pending` with `PASS` or `FAIL`.
3. Set the row **Status** column to `PASS` (all green) or `FAIL` (any red).
4. Add failure details under the Results table if any test failed.
5. Commit the updated file.

---

## 2026-06-30 — v1.0.0 (FAIL)

- **0/5 tests ran** — compilation error prevented all tests from executing
- `DropmailProvider` missing `waitForEmail` override, `getDouble`/`getString` method signature mismatches
- Fix: implement missing abstract method, correct SimpleJson API calls
- Version NOT bumped — requires fix and re-test

## 2026-06-30 — v1.0.0 Final

- Replaced xeramail with Resend API for test email delivery (no more 429 rate limits)
- Dropmail provider fixed: corrected GraphQL field names (mailbox→mails, received→receivedAt, sessionId→id)
- All Python tests: 7/7 PASS
- RESEND_API_KEY moved to .env (remove hardcoded key)
