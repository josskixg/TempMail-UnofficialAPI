"""tempmail.lol provider implementation (REST API, token-based)."""

from __future__ import annotations

from datetime import datetime

from ..base import TempMailProvider
from ..exceptions import NotFoundError, TempMailError
from ..http_client import HttpClient
from ..models import Message, MessageDetail


class TempmailLolProvider(TempMailProvider):
    """Provider for tempmail.lol — REST API, token-based, no auth."""

    BASE_URL = "https://api.tempmail.lol/v2"

    def __init__(
        self,
        proxies: list[str] | None = None,
        random_ua: bool = True,
        **kwargs,
    ) -> None:
        self._http = HttpClient(proxies=proxies, random_ua=random_ua, use_cookies=False)
        self._token: str | None = None
        self._email: str | None = None

    @property
    def name(self) -> str:
        return "tempmail.lol"

    def generate_email(self) -> str:
        resp = self._http.post(
            f"{self.BASE_URL}/inbox/create",
            timeout=30,
            headers={"Content-Type": "application/json"},
        )
        if resp.status_code not in (200, 201):
            raise TempMailError(
                f"Failed to create email: {resp.status_code}", self.name
            )
        data = resp.json()
        self._email = data.get("address")
        self._token = data.get("token")
        if not self._email or not self._token:
            raise TempMailError("Missing address or token in response", self.name)
        return self._email

    def get_inbox(self, email: str) -> list[Message]:
        if not self._token:
            raise TempMailError("No token — call generate_email() first", self.name)
        resp = self._http.get(
            f"{self.BASE_URL}/inbox",
            params={"token": self._token},
            timeout=30,
        )
        if resp.status_code != 200:
            raise TempMailError(f"Inbox error: {resp.status_code}", self.name)
        data = resp.json()
        if data.get("expired"):
            raise TempMailError("Token expired", self.name)
        messages = []
        for item in data.get("emails", []):
            date_str = item.get("date", item.get("createdAt", ""))
            try:
                date = datetime.fromisoformat(date_str.replace("Z", "+00:00"))
            except (ValueError, AttributeError):
                date = datetime.now()
            messages.append(
                Message(
                    id=str(item.get("_id", item.get("id", item.get("uid", "")))),
                    sender=item.get("from", item.get("sender", "")),
                    subject=item.get("subject", ""),
                    date=date,
                )
            )
        return messages

    def read_message(self, message_id: str) -> MessageDetail:
        if not self._token:
            raise TempMailError("No token — call generate_email() first", self.name)
        # tempmail.lol returns full emails in the inbox response
        resp = self._http.get(
            f"{self.BASE_URL}/inbox",
            params={"token": self._token},
            timeout=30,
        )
        if resp.status_code != 200:
            raise NotFoundError(f"Cannot read inbox: {resp.status_code}", self.name)
        data = resp.json()
        for item in data.get("emails", []):
            if str(item.get("_id", item.get("id", item.get("uid", "")))) == str(message_id):
                date_str = item.get("date", item.get("createdAt", ""))
                try:
                    date = datetime.fromisoformat(date_str.replace("Z", "+00:00"))
                except (ValueError, AttributeError):
                    date = datetime.now()
                return MessageDetail(
                    id=str(message_id),
                    sender=item.get("from", item.get("sender", "")),
                    subject=item.get("subject", ""),
                    date=date,
                    body_text=item.get("body", item.get("text", "")),
                    body_html=item.get("html", ""),
                    attachments=[],
                )
        raise NotFoundError(f"Message {message_id} not found", self.name)

    def delete_email(self, email: str) -> bool:
        self._token = None
        self._email = None
        return True
