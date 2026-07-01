"""Ncaori Mail+ provider — REST API on www.nca.my.id."""

from __future__ import annotations

import random
from datetime import datetime

from ..base import TempMailProvider
from ..exceptions import TempMailError
from ..http_client import HttpClient
from ..models import Message, MessageDetail

DOMAINS = ["ncaori.my.id", "nca.my.id"]

WORDS = [
    "swift", "crystal", "storm", "frost", "shadow", "ember", "azure",
    "phantom", "silver", "iron", "crimson", "golden", "neo", "cosmic", "lunar",
    "solar", "dark", "light", "void", "flux",
]

WORDS2 = [
    "core", "leaf", "forge", "wave", "peak", "gate", "pulse",
    "blade", "shard", "drift", "hive", "node", "edge", "beacon", "nova",
    "storm", "cloud", "moon", "star", "wind",
]


class NcaoriMailProvider(TempMailProvider):
    """Provider for nca.my.id — internal REST API, no auth needed."""

    BASE = "https://www.nca.my.id"

    def __init__(
        self,
        proxies: list[str] | None = None,
        random_ua: bool = True,
        **kwargs,
    ) -> None:
        self._http = HttpClient(proxies=proxies, random_ua=random_ua, use_cookies=False)

    @property
    def name(self) -> str:
        return "ncaori"

    def _random_name(self) -> str:
        return f"{random.choice(WORDS)}_{random.choice(WORDS2)}"

    def generate_email(self) -> str:
        name = self._random_name()
        domain = random.choice(DOMAINS)
        return f"{name}@{domain}"

    def get_inbox(self, email: str) -> list[Message]:
        resp = self._http.get(
            f"{self.BASE}/api/emails?recipient={email}",
            timeout=30,
        )
        if resp.status_code != 200:
            raise TempMailError(f"Inbox fetch failed: HTTP {resp.status_code}", self.name)
        data = resp.json()
        if not isinstance(data, dict) or "emails" not in data:
            return []
        raw = data["emails"]
        if not isinstance(raw, list):
            return []
        msgs = []
        for m in raw:
            date_str = m.get("created_at", "")
            try:
                date = datetime.fromisoformat(date_str.replace("Z", "+00:00")) if date_str else datetime.now()
            except (ValueError, TypeError):
                date = datetime.now()
            body_text = m.get("body_text", "") or ""
            body_html = m.get("body_html", "") or ""
            if body_text or body_html:
                msgs.append(MessageDetail(
                    id=m.get("id", ""),
                    sender=m.get("sender", "unknown"),
                    subject=m.get("subject", "(no subject)"),
                    date=date,
                    body_text=body_text,
                    body_html=body_html,
                ))
            else:
                msgs.append(Message(
                    id=m.get("id", ""),
                    sender=m.get("sender", "unknown"),
                    subject=m.get("subject", "(no subject)"),
                    date=date,
                ))
        return msgs

    def read_message(self, message_id: str) -> MessageDetail:
        """Ncaori Mail+ returns full body in get_inbox()."""
        raise TempMailError(
            "Ncaori Mail+ returns full message body in get_inbox(). Use get_inbox() then filter by id.",
            self.name,
        )

    def delete_email(self, email: str) -> bool:
        """Emails auto-expire after 48h. No delete endpoint."""
        return True
