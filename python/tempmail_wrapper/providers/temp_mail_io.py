"""temp-mail.io provider implementation (REST API, token-based internal API)."""

from __future__ import annotations

import random
import string
from datetime import datetime

from ..base import TempMailProvider
from ..exceptions import NotFoundError, TempMailError
from ..http_client import HttpClient
from ..models import Message, MessageDetail


class TempMailIoProvider(TempMailProvider):
    """Provider for temp-mail.io — free internal REST API, Bearer token."""

    BASE_URL = "https://api.internal.temp-mail.io/api/v3"

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
        return "temp-mail.io"

    def _headers(self) -> dict[str, str]:
        h = {"Content-Type": "application/json"}
        if self._token:
            h["Authorization"] = f"Bearer {self._token}"
        return h

    def generate_email(self) -> str:
        resp = self._http.post(
            f"{self.BASE_URL}/email/new",
            json={"min_name_length": 6, "max_name_length": 12},
            headers=self._headers(),
            timeout=30,
        )
        if resp.status_code != 200:
            raise TempMailError(
                f"Failed to create email: {resp.status_code} {resp.text[:200]}",
                self.name,
            )
        data = resp.json()
        self._email = data.get("email")
        self._token = data.get("token")
        if not self._email:
            raise TempMailError("Missing email in response", self.name)
        return self._email

    def get_inbox(self, email: str) -> list[Message]:
        resp = self._http.get(
            f"{self.BASE_URL}/email/{email}/messages",
            headers=self._headers(),
            timeout=30,
        )
        if resp.status_code != 200:
            raise TempMailError(f"Inbox error: {resp.status_code}", self.name)
        data = resp.json()
        if not isinstance(data, list):
            data = data.get("messages", data.get("mails", []))
        messages = []
        for item in data:
            date_str = item.get("created_at", item.get("date", ""))
            try:
                date = datetime.fromisoformat(date_str.replace("Z", "+00:00"))
            except (ValueError, AttributeError):
                date = datetime.now()
            sender = item.get("from", "")
            if isinstance(sender, dict):
                sender = sender.get("address", sender.get("name", ""))
            messages.append(
                Message(
                    id=str(item.get("id", item.get("uid", ""))),
                    sender=sender,
                    subject=item.get("subject", ""),
                    date=date,
                )
            )
        return messages

    def read_message(self, message_id: str) -> MessageDetail:
        # temp-mail.io messages endpoint returns full message list
        # Individual message read: GET /api/v3/message/{id}
        if not self._email:
            raise TempMailError("No email — call generate_email() first", self.name)
        resp = self._http.get(
            f"{self.BASE_URL}/email/{self._email}/messages",
            headers=self._headers(),
            timeout=30,
        )
        if resp.status_code != 200:
            raise NotFoundError(f"Cannot read inbox: {resp.status_code}", self.name)
        data = resp.json()
        if not isinstance(data, list):
            data = data.get("messages", data.get("mails", []))
        for item in data:
            if str(item.get("id", item.get("uid", ""))) == str(message_id):
                date_str = item.get("created_at", item.get("date", ""))
                try:
                    date = datetime.fromisoformat(date_str.replace("Z", "+00:00"))
                except (ValueError, AttributeError):
                    date = datetime.now()
                sender = item.get("from", "")
                if isinstance(sender, dict):
                    sender = sender.get("address", sender.get("name", ""))
                return MessageDetail(
                    id=str(message_id),
                    sender=sender,
                    subject=item.get("subject", ""),
                    date=date,
                    body_text=item.get("body_text", item.get("text", "")),
                    body_html=item.get("body_html", item.get("html", "")),
                    attachments=[
                        {
                            "filename": a.get("filename", ""),
                            "content_type": a.get("content_type", ""),
                            "size": a.get("size", 0),
                        }
                        for a in item.get("attachments", [])
                    ],
                )
        raise NotFoundError(f"Message {message_id} not found", self.name)

    def delete_email(self, email: str) -> bool:
        self._token = None
        self._email = None
        return True
