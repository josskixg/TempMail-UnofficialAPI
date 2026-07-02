"""Factory function to create provider instances."""

from __future__ import annotations

from typing import Any

from .base import TempMailProvider
from .providers import (
    DropmailProvider,
    EmailTempProvider,
    EmailfakeProvider,
    GeneratorEmailProvider,
    GuerrillaMailProvider,
    MailTmProvider,
    MailnesiaProvider,
    NcaoriMailProvider,
    OneSecEmailProvider,
    TempMailIoProvider,
    TempmailLolProvider,
    TempmailPlusProvider,
    TempmailcProvider,
    TenMinuteMailProvider,
    YopmailProvider,
    ZoromailProvider,
)

_PROVIDERS: dict[str, type[TempMailProvider]] = {
    # v1.0.0 providers
    "mail.tm": MailTmProvider,
    "guerrillamail": GuerrillaMailProvider,
    "yopmail": YopmailProvider,
    "dropmail": DropmailProvider,
    "1secemail": OneSecEmailProvider,
    "ncaori": NcaoriMailProvider,
    # v1.1.0 providers
    "email-temp": EmailTempProvider,
    "emailfake": EmailfakeProvider,
    "generator.email": GeneratorEmailProvider,
    "zoromail": ZoromailProvider,
    "tempmail.lol": TempmailLolProvider,
    "tempmailc": TempmailcProvider,
    "temp-mail.io": TempMailIoProvider,
    "tempmail.plus": TempmailPlusProvider,
    "mailnesia": MailnesiaProvider,
    "10minutemail": TenMinuteMailProvider,
}


def create_provider(
    name: str,
    proxies: list[str] | None = None,
    random_ua: bool = True,
    **config: Any,
) -> TempMailProvider:
    """Create a tempmail provider by name.

    Args:
        name: Provider name (mail.tm, guerrillamail, yopmail, dropmail, etc.).
        proxies: Optional list of proxy URLs for rotation.
        random_ua: Whether to rotate User-Agent per request (default: True).
        **config: Provider-specific configuration.

    Returns:
        Configured TempMailProvider instance.

    Raises:
        ValueError: If provider name is unknown.
    """
    name_lower = name.lower()
    if name_lower not in _PROVIDERS:
        raise ValueError(f"Unknown provider: {name}. Available: {', '.join(_PROVIDERS.keys())}")

    cls = _PROVIDERS[name_lower]
    return cls(proxies=proxies, random_ua=random_ua, **config)
