# Upgrade Guide

## v1.0.0 (2026-06-30)

This is the initial public release. There is no prior version to upgrade from.

If you were using an early development draft or pre-release branch, note the following changes that occurred before the 1.0.0 release.

### Provider Renames

Two providers were renamed during development. If your code references the old names, update accordingly:

| Old Name | New Name | Reason |
|----------|----------|--------|
| TempMail.org | YOPmail | TempMail.org was rebranded; YOPmail is the underlying service. |
| Mailtrap | Dropmail.me | Mailtrap requires API keys and is not a free temp-mail service; replaced with Dropmail.me which offers free GraphQL access. |

**Affected code**: factory function calls and provider name strings.

```
# Before (draft)
create_provider("tempmail.org")
create_provider("mailtrap")

# After (v1.0.0)
create_provider("yopmail")
create_provider("dropmail")
```

### Migrating from API-Key-Based Wrappers

If you were using a temp-mail library that requires API keys (e.g., the official Mailtrap API, RapidAPI wrappers), migration to this project is straightforward:

1. **Remove API key configuration.** This wrapper requires no keys. Delete any environment variable or config file that stored tokens.

2. **Replace provider instantiation.** Swap the old client setup for the factory pattern:

   ```python
   # Before (API-key-based)
   client = MailtrapClient(api_key=os.environ["MAILTRAP_KEY"])
   inbox = client.get_inbox(inbox_id=12345)

   # After (no keys)
   from tempmail_wrapper import create_provider
   provider = create_provider("dropmail")
   email = provider.generate_email()
   inbox = provider.get_inbox(email)
   ```

3. **Update data model references.** This project uses `Message` (id, sender, subject, date) and `MessageDetail` (body_text, body_html, attachments). Field names may differ from API-key-based libraries.

4. **Adjust for provider differences.** Each temp-mail service has its own behavior around mailbox lifetime, message retention, and deletion support. Check the [provider comparison table](README.md#providers-and-features) for details.

### Interface Changes

The five-method contract (`generate_email`, `get_inbox`, `read_message`, `delete_email`, `wait_for_email`) was established early in development and has not changed since. If you used a pre-release draft with a different method name, update to match the current interface documented in [ARCHITECTURE.md](ARCHITECTURE.md).

## v1.1.0 (2026-07-02)

### Upgrading from v1.0.0

No breaking changes. All existing provider factory names from v1.0.0 continue to work without modification.

### New Providers Available

11 new providers are registered in the factory. You can start using them immediately:

`python
# Python examples � same pattern applies to all languages
provider = create_provider("ncaori")
provider = create_provider("zoromail")
provider = create_provider("tempmail.lol")
provider = create_provider("tempmailc")
provider = create_provider("temp-mail.io")
provider = create_provider("tempmail.plus")
provider = create_provider("emailfake")
provider = create_provider("generator.email")
provider = create_provider("email-temp")
provider = create_provider("mailnesia")
provider = create_provider("10minutemail")
`

### Known Limitations of New Providers

- **mailnesia**: May return 403 on rapid sequential requests (Cloudflare rate-limiting).
- **10minutemail**: Protected by Cloudflare  requests may be blocked without cookie/session handling.

### Interface

The five-method interface contract (generate_email, get_inbox, read_message, delete_email, wait_for_email) is unchanged. All 11 new providers implement the same contract.