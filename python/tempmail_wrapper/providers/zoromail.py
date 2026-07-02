"""zoromail.com provider implementation (REST API, no auth)."""

from __future__ import annotations

import random
import string
from datetime import datetime

from ..base import TempMailProvider
from ..exceptions import NotFoundError, TempMailError
from ..http_client import HttpClient
from ..models import Message, MessageDetail


class ZoromailProvider(TempMailProvider):
    """Provider for zoromail.com — clean REST API, no auth required."""

    BASE_URL = "https://zoromail.com/public_api.php/v1"

    def __init__(
        self,
        proxies: list[str] | None = None,
        random_ua: bool = True,
        **kwargs,
    ) -> None:
        self._http = HttpClient(proxies=proxies, random_ua=random_ua, use_cookies=False)
        self._email: str | None = None

    @property
    def name(self) -> str:
        return "zoromail"

    def _api(self, method: str, path: str, **kwargs) -> dict | list:
        resp = self._http.request(
            method, f"{self.BASE_URL}{path}", timeout=30, **kwargs
        )
        if resp.status_code >= 400:
            raise TempMailError(
                f"API error: {resp.status_code} {resp.text[:200]}", self.name
            )
        data = resp.json()
        if not data.get("success"):
            err = data.get("error", "unknown error")
            raise TempMailError(f"API returned error: {err}", self.name)
        return data.get("data")

    def generate_email(self) -> str:
        domains = self._api("GET", "/domains")
        if not domains:
            raise TempMailError("No domains available", self.name)
        domain = random.choice(domains)
        username = "".join(
            random.choices(string.ascii_lowercase + string.digits, k=10)
        )
        data = self._api(
            "POST",
            "/emails",
            json={"username": username, "domain": domain},
            headers={"Content-Type": "application/json"},
        )
        self._email = data["email"]
        return self._email

    def get_inbox(self, email: str) -> list[Message]:
        data = self._api("GET", f"/emails/{email}/messages")
        messages = []
        for item in data:
            date_str = item.get("date", "")
            try:
                date = datetime.fromisoformat(date_str.replace("Z", "+00:00"))
            except (ValueError, AttributeError):
                date = datetime.now()
            messages.append(
                Message(
                    id=str(item["id"]),
                    sender=item.get("from", ""),
                    subject=item.get("subject", ""),
                    date=date,
                )
            )
        return messages

    def read_message(self, message_id: str) -> MessageDetail:
        data = self._api("GET", f"/messages/{message_id}")
        date_str = data.get("date", "")
        try:
            date = datetime.fromisoformat(date_str.replace("Z", "+00:00"))
        except (ValueError, AttributeError):
            date = datetime.now()
        return MessageDetail(
            id=str(data.get("id", message_id)),
            sender=data.get("from", ""),
            subject=data.get("subject", ""),
            date=date,
            body_text=data.get("text", data.get("body_text", "")),
            body_html=data.get("html", data.get("body_html", "")),
            attachments=[],
        )

    def delete_email(self, email: str) -> bool:
        self._email = None
        return True
