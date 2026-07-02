"""tempmailc.com provider implementation (REST API, no auth)."""

from __future__ import annotations

from datetime import datetime

from ..base import TempMailProvider
from ..exceptions import NotFoundError, TempMailError
from ..http_client import HttpClient
from ..models import Message, MessageDetail


class TempmailcProvider(TempMailProvider):
    """Provider for tempmailc.com — REST API, no auth, no cookies."""

    BASE_URL = "https://tempmailc.com/api/v1"

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
        return "tempmailc"

    def generate_email(self) -> str:
        resp = self._http.get(f"{self.BASE_URL}/new", timeout=30)
        if resp.status_code != 200:
            raise TempMailError(f"Failed to create email: {resp.status_code}", self.name)
        data = resp.json()
        if not data.get("ok"):
            raise TempMailError("API returned not ok", self.name)
        self._email = data["email"]
        return self._email

    def get_inbox(self, email: str) -> list[Message]:
        resp = self._http.get(
            f"{self.BASE_URL}/inbox",
            params={"email": email},
            timeout=30,
        )
        if resp.status_code != 200:
            raise TempMailError(f"Inbox error: {resp.status_code}", self.name)
        data = resp.json()
        messages = []
        for item in data.get("messages", []):
            date_str = item.get("date", item.get("time", ""))
            try:
                date = datetime.fromisoformat(date_str)
            except (ValueError, AttributeError):
                date = datetime.now()
            messages.append(
                Message(
                    id=str(item.get("id", item.get("msg_id", ""))),
                    sender=item.get("from", item.get("from_mail", "")),
                    subject=item.get("subject", ""),
                    date=date,
                )
            )
        return messages

    def read_message(self, message_id: str) -> MessageDetail:
        if not self._email:
            raise TempMailError("No email — call generate_email() first", self.name)
        resp = self._http.get(
            f"{self.BASE_URL}/message",
            params={"msg_id": message_id, "email": self._email},
            timeout=30,
        )
        if resp.status_code != 200:
            raise NotFoundError(f"Message {message_id} not found", self.name)
        data = resp.json()
        date_str = data.get("date", data.get("time", ""))
        try:
            date = datetime.fromisoformat(date_str)
        except (ValueError, AttributeError):
            date = datetime.now()
        return MessageDetail(
            id=str(data.get("id", message_id)),
            sender=data.get("from", data.get("from_mail", "")),
            subject=data.get("subject", ""),
            date=date,
            body_text=data.get("text", data.get("body_text", "")),
            body_html=data.get("html", data.get("body_html", "")),
            attachments=[],
        )

    def delete_email(self, email: str) -> bool:
        self._email = None
        return True
