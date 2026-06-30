"""guerrillamail.com provider implementation."""

from __future__ import annotations

from datetime import datetime

from ..base import TempMailProvider
from ..exceptions import NotFoundError, RateLimitError, TempMailError
from ..http_client import HttpClient
from ..models import Message, MessageDetail


class GuerrillaMailProvider(TempMailProvider):
    """Provider for guerrillamail.com — uses session cookie."""

    BASE_URL = "https://api.guerrillamail.com/ajax.php"

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
        return "guerrillamail"

    def _request(self, func: str, params: dict[str, str] | None = None) -> dict:
        params = params or {}
        params["f"] = func
        resp = self._http.get(self.BASE_URL, params=params, timeout=30)
        if resp.status_code == 429:
            raise RateLimitError("Rate limit exceeded", self.name)
        if resp.status_code != 200:
            raise TempMailError(f"API error: {resp.status_code}", self.name)
        return resp.json()

    def generate_email(self) -> str:
        data = self._request("get_email_address")
        self._email = data.get("email_addr")
        if not self._email:
            raise TempMailError("Failed to generate email", self.name)
        return self._email

    def get_inbox(self, email: str) -> list[Message]:
        data = self._request("get_email_list", {"offset": "0"})
        messages = []
        for item in data.get("list", []):
            msg = Message(
                id=str(item["mail_id"]),
                sender=item.get("mail_from", ""),
                subject=item.get("mail_subject", ""),
                date=datetime.fromtimestamp(int(item.get("mail_timestamp", 0))),
            )
            messages.append(msg)
        return messages

    def read_message(self, message_id: str) -> MessageDetail:
        data = self._request("fetch_email", {"email_id": message_id})
        if not data or "error" in data:
            raise NotFoundError(f"Message {message_id} not found", self.name)

        attachments = []
        if isinstance(data.get("attachments"), list):
            for att in data["attachments"]:
                attachments.append({
                    "filename": att.get("filename", ""),
                    "content_type": att.get("content_type", ""),
                    "size": att.get("size", 0),
                })

        return MessageDetail(
            id=str(data.get("mail_id", message_id)),
            sender=data.get("mail_from", ""),
            subject=data.get("mail_subject", ""),
            date=datetime.fromtimestamp(int(data.get("mail_timestamp", 0))),
            body_text=data.get("mail_body", ""),
            body_html=data.get("mail_body_html", ""),
            attachments=attachments,
        )

    def delete_email(self, email: str) -> bool:
        self._http = HttpClient(
            proxies=self._http.proxies,
            random_ua=self._http.random_ua,
            use_cookies=True,
        )
        self._email = None
        return True
