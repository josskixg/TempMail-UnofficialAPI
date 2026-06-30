"""mail.tm provider implementation."""

from __future__ import annotations

from datetime import datetime

from ..base import TempMailProvider
from ..exceptions import NotFoundError, RateLimitError, TempMailError
from ..http_client import HttpClient
from ..models import Message, MessageDetail


class MailTmProvider(TempMailProvider):
    """Provider for mail.tm — uses Bearer token auth."""

    BASE_URL = "https://api.mail.tm"

    def __init__(
        self,
        proxies: list[str] | None = None,
        random_ua: bool = True,
        **kwargs,
    ) -> None:
        self._http = HttpClient(proxies=proxies, random_ua=random_ua, use_cookies=True)
        self._token: str | None = None
        self._email: str | None = None
        self._password: str = "TempMail123!"

    @property
    def name(self) -> str:
        return "mail.tm"

    def _headers(self) -> dict[str, str]:
        headers = {"Content-Type": "application/json"}
        if self._token:
            headers["Authorization"] = f"Bearer {self._token}"
        return headers

    def _handle_error(self, resp) -> None:
        if resp.status_code == 429:
            raise RateLimitError("Rate limit exceeded", self.name)
        if resp.status_code == 404:
            raise NotFoundError("Resource not found", self.name)
        if resp.status_code >= 400:
            raise TempMailError(f"API error: {resp.status_code} {resp.text}", self.name)

    def _get_domain(self) -> str:
        resp = self._http.get(f"{self.BASE_URL}/domains", headers=self._headers(), timeout=30)
        self._handle_error(resp)
        domains = resp.json().get("hydra:member", [])
        if not domains:
            raise TempMailError("No domains available", self.name)
        return domains[0]["domain"]

    def _generate_email_core(self) -> str:
        domain = self._get_domain()
        import random as _r
        import string

        local = "".join(_r.choices(string.ascii_lowercase + string.digits, k=10))
        email = f"{local}@{domain}"

        payload = {"address": email, "password": self._password}
        resp = self._http.post(
            f"{self.BASE_URL}/accounts", json=payload, headers=self._headers(), timeout=30
        )
        self._handle_error(resp)

        auth_resp = self._http.post(
            f"{self.BASE_URL}/token",
            json={"address": email, "password": self._password},
            headers=self._headers(),
            timeout=30,
        )
        self._handle_error(auth_resp)

        self._token = auth_resp.json()["token"]
        self._email = email
        return email

    def generate_email(self) -> str:
        import time

        retry_delays = [1, 3, 5]
        for attempt in range(len(retry_delays) + 1):
            try:
                return self._generate_email_core()
            except RateLimitError:
                if attempt < len(retry_delays):
                    time.sleep(retry_delays[attempt])
                else:
                    raise

    def get_inbox(self, email: str) -> list[Message]:
        resp = self._http.get(
            f"{self.BASE_URL}/messages", headers=self._headers(), timeout=30
        )
        self._handle_error(resp)
        return [
            Message(
                id=m["id"],
                sender=m.get("from", {}).get("address", ""),
                subject=m.get("subject", ""),
                date=datetime.fromisoformat(
                    m.get("createdAt", "2000-01-01T00:00:00+00:00").replace("Z", "+00:00")
                ),
            )
            for m in resp.json().get("hydra:member", [])
        ]

    def read_message(self, message_id: str) -> MessageDetail:
        resp = self._http.get(
            f"{self.BASE_URL}/messages/{message_id}",
            headers=self._headers(),
            timeout=30,
        )
        self._handle_error(resp)
        data = resp.json()

        attachments = [
            {
                "filename": att.get("filename", ""),
                "content_type": att.get("contentType", ""),
                "size": att.get("size", 0),
            }
            for att in data.get("attachments", [])
        ]

        date_str = data.get("createdAt", "2000-01-01T00:00:00+00:00").replace("Z", "+00:00")
        return MessageDetail(
            id=data.get("id", message_id),
            sender=data.get("from", {}).get("address", ""),
            subject=data.get("subject", ""),
            date=datetime.fromisoformat(date_str),
            body_text=data.get("text", ""),
            body_html=data.get("html", [None])[0] if data.get("html") else "",
            attachments=attachments,
        )

    def delete_email(self, email: str) -> bool:
        self._token = None
        self._email = None
        return True
