"""tempmail.plus provider implementation (REST API, email-based query)."""

from __future__ import annotations

import random
import string
from datetime import datetime

from ..base import TempMailProvider
from ..exceptions import NotFoundError, TempMailError
from ..http_client import HttpClient
from ..models import Message, MessageDetail


class TempmailPlusProvider(TempMailProvider):
    """Provider for tempmail.plus — REST API, no auth, email as query param."""

    BASE_URL = "https://tempmail.plus"
    DOMAIN = "mailto.plus"

    def __init__(
        self,
        proxies: list[str] | None = None,
        random_ua: bool = True,
        **kwargs,
    ) -> None:
        self._http = HttpClient(proxies=proxies, random_ua=random_ua, use_cookies=True)
        self._email: str | None = None

    @property
    def name(self) -> str:
        return "tempmail.plus"

    def generate_email(self) -> str:
        username = "".join(
            random.choices(string.ascii_lowercase + string.digits, k=10)
        )
        self._email = f"{username}@{self.DOMAIN}"
        return self._email

    def get_inbox(self, email: str) -> list[Message]:
        resp = self._http.get(
            f"{self.BASE_URL}/api/mails",
            params={"email": email},
            timeout=30,
        )
        if resp.status_code != 200:
            raise TempMailError(f"Inbox error: {resp.status_code}", self.name)
        data = resp.json()
        if not data.get("result", True):
            raise TempMailError("API returned error", self.name)
        messages = []
        for item in data.get("mail_list", []):
            time_str = item.get("time", "")
            try:
                date = datetime.strptime(time_str, "%Y-%m-%d %H:%M:%S")
            except (ValueError, AttributeError):
                date = datetime.now()
            messages.append(
                Message(
                    id=str(item.get("mail_id", "")),
                    sender=item.get("from_mail", ""),
                    subject=item.get("subject", ""),
                    date=date,
                )
            )
        return messages

    def read_message(self, message_id: str) -> MessageDetail:
        if not self._email:
            raise TempMailError("No email — call generate_email() first", self.name)
        resp = self._http.get(
            f"{self.BASE_URL}/api/mails/{message_id}",
            params={"email": self._email},
            timeout=30,
        )
        if resp.status_code != 200:
            raise NotFoundError(f"Message {message_id} not found", self.name)
        data = resp.json()
        date_str = data.get("date", "")
        try:
            # RFC 2822 format: "Wed, 1 Jul 2026 08:00:36 +0000"
            from email.utils import parsedate_to_datetime

            date = parsedate_to_datetime(date_str)
        except (ValueError, AttributeError, TypeError):
            date = datetime.now()
        return MessageDetail(
            id=str(data.get("mail_id", message_id)),
            sender=data.get("from_mail", data.get("from", "")),
            subject=data.get("subject", ""),
            date=date,
            body_text=data.get("text", ""),
            body_html=data.get("html", ""),
            attachments=[
                {
                    "filename": a.get("filename", ""),
                    "content_type": a.get("content_type", ""),
                    "size": a.get("size", 0),
                }
                for a in data.get("attachments", [])
            ],
        )

    def delete_email(self, email: str) -> bool:
        self._email = None
        return True
