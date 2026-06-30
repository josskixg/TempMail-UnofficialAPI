"""1secemail.com provider implementation (web scraping with CSRF)."""

from __future__ import annotations

import random
import re
import string
from datetime import datetime
from html import unescape

from ..base import TempMailProvider
from ..exceptions import NotFoundError, TempMailError
from ..http_client import HttpClient
from ..models import Message, MessageDetail

DOMAINS = [
    "qzueos.com", "gaziw.com", "emailgenerator.xyz",
]


class OneSecEmailProvider(TempMailProvider):
    """Provider for 1secemail.com — CSRF-protected web scraping."""

    BASE = "https://www.1secemail.com"

    def __init__(
        self,
        proxies: list[str] | None = None,
        random_ua: bool = True,
        **kwargs,
    ) -> None:
        self._http = HttpClient(proxies=proxies, random_ua=random_ua, use_cookies=True)
        self._csrf: str | None = None
        self._email: str | None = None

    @property
    def name(self) -> str:
        return "1secemail"

    def _fetch_csrf(self) -> str:
        resp = self._http.get(f"{self.BASE}/", timeout=30)
        if resp.status_code != 200:
            raise TempMailError(f"Failed to load page: {resp.status_code}", self.name)
        m = re.search(r'<meta name="csrf-token" content="([^"]+)">', resp.text)
        if not m:
            raise TempMailError("CSRF token not found", self.name)
        return m.group(1)

    def _ensure_csrf(self) -> None:
        if self._csrf:
            return
        self._csrf = self._fetch_csrf()

    def _post(self, url: str, data: dict) -> dict:
        self._ensure_csrf()
        resp = self._http.post(
            url,
            json={"_token": self._csrf, **data},
            headers={
                "X-CSRF-TOKEN": self._csrf,
                "x-xsrf-token": self._csrf,
                "Referer": f"{self.BASE}/",
            },
            timeout=30,
        )
        if resp.status_code != 200:
            raise TempMailError(f"POST failed: {resp.status_code}", self.name)
        try:
            return resp.json()
        except Exception:
            return {}

    def generate_email(self) -> str:
        self._ensure_csrf()
        name = "".join(random.choices(string.ascii_lowercase + string.digits, k=10))
        domain = random.choice(DOMAINS)
        self._post(f"{self.BASE}/change", {"name": name, "domain": domain})
        self._email = f"{name}@{domain}"
        return self._email

    def get_inbox(self, email: str) -> list[Message]:
        self._ensure_csrf()
        data = self._post(f"{self.BASE}/get_messages", {})
        if not isinstance(data, list):
            # API might return { "messages": [...] } or { "data": [...] }
            if isinstance(data, dict):
                data = data.get("messages", data.get("data", []))
            else:
                return []
        msgs = []
        for m in data:
            date_str = m.get("receivedAt", "")
            try:
                date = datetime.strptime(date_str, "%Y-%m-%d %H:%M:%S")
            except (ValueError, TypeError):
                date = datetime.now()
            msgs.append(Message(
                id=m.get("id", ""),
                sender=m.get("from_email", m.get("from", "unknown")),
                subject=m.get("subject", "(no subject)"),
                date=date,
            ))
        return msgs

    def read_message(self, message_id: str) -> MessageDetail:
        self._ensure_csrf()
        resp = self._http.get(
            f"{self.BASE}/view/{message_id}",
            headers={"X-CSRF-TOKEN": self._csrf, "Referer": f"{self.BASE}/"},
            timeout=30,
        )
        if resp.status_code != 200:
            raise NotFoundError(f"Message {message_id} not found", self.name)
        html = resp.text
        text = re.sub(r"<[^>]+>", "", unescape(html))
        text = re.sub(r"\s+", " ", text).strip()
        sender_match = re.search(r"From:\s*([^<\n]+)", html)
        subject_match = re.search(r"Subject:\s*([^<\n]+)", html)
        try:
            date_match = re.search(r"(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})", html)
            date = datetime.strptime(date_match.group(1), "%Y-%m-%d %H:%M:%S") if date_match else datetime.now()
        except ValueError:
            date = datetime.now()
        return MessageDetail(
            message=Message(
                id=message_id,
                sender=sender_match.group(1).strip() if sender_match else "unknown",
                subject=subject_match.group(1).strip() if subject_match else "(no subject)",
                date=date,
            ),
            body_text=text,
            body_html=html,
        )

    def delete_email(self, email: str) -> bool:
        return True
