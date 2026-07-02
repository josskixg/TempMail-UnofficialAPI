# Security Policy

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Report privately via **[GitHub Security Advisories](https://github.com/josskixg/TempMail-UnofficialAPI/security/advisories/new)** or open a private issue at [github.com/josskixg/TempMail-UnofficialAPI](https://github.com/josskixg/TempMail-UnofficialAPI) with the subject line `[SECURITY]`. Include:

- A description of the vulnerability.
- Steps to reproduce or a proof of concept.
- The affected language(s) and provider(s).

You will receive an acknowledgment within **3 business days**.

## Scope

The following are considered in scope:

- Injection or data leakage through provider API parsing (HTML scraping, GraphQL, REST).
- Credential or token exposure in error messages, logs, or test output.
- Denial of service through crafted provider responses.
- Dependency vulnerabilities in any of the seven language implementations.

The following are known issues, not vulnerabilities:

- Provider APIs changing their public endpoints without notice (breakage, not a security issue).
- Rate limiting by upstream providers (documented in [TESTING.md](TESTING.md)).
- Temporary email services being inherently untrusted by third parties.

## Response Timeline

| Phase | Timeframe |
|-------|-----------|
| Acknowledgment | 3 business days |
| Initial assessment | 7 business days |
| Fix and release | 30 days for critical, 90 days for moderate |

## Credits

Reporters who provide valid security findings will be credited in the release notes and [CHANGELOG.md](CHANGELOG.md) (with permission).

## Supported Versions

Only the latest released version receives security patches. Pin your dependency to the latest minor version to stay current.

| Version | Supported |
|---------|-----------|
| 1.1.x | Yes |
| 1.0.x | Yes |
| < 1.1 | No |

