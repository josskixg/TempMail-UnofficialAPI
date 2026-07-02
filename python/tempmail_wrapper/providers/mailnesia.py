"""mailnesia.com provider implementation (HTML scraping, public mailbox)."""

from __future__ import annotations

import random
import re
import string
from datetime import datetime

from bs4 import BeautifulSoup

from ..base import TempMailProvider
from ..exceptions import NotFoundError, TempMailError
from ..http_client import HttpClient
from ..models import Message, MessageDetail


class MailnesiaProvider(TempMailProvider):
    """Provider for mailnesia.com — public mailbox, no auth, HTML scraping with IP rotation."""

    BASE_URL = "https://mailnesia.com"

    def __init__(
        self,
        proxies: list[str] | None = None,
        random_ua: bool = True,
        **kwargs,
    ) -> None:
        self._http = HttpClient(proxies=proxies, random_ua=random_ua, use_cookies=True)
        self._email: str | None = None
        self._username: str | None = None

    @property
    def name(self) -> str:
        return "mailnesia"

    def _generate_random_ip(self) -> str:
        """Generate a random IP address for header rotation."""
        return f"{random.randint(1, 254)}.{random.randint(0, 255)}.{random.randint(0, 255)}.{random.randint(1, 254)}"

    def _get_headers_with_ip_rotation(self) -> dict[str, str]:
        """Generate headers with rotated IP to bypass 403."""
        ip = self._generate_random_ip()
        return {
            "X-Forwarded-For": ip,
            "X-Real-IP": ip,
            "CF-Connecting-IP": ip,
            "True-Client-IP": ip,
        }

    def generate_email(self) -> str:
        self._username = "".join(
            random.choices(string.ascii_lowercase + string.digits, k=10)
        )
        self._email = f"{self._username}@mailnesia.com"
        return self._email

    def get_inbox(self, email: str) -> list[Message]:
        username = email.split("@")[0] if "@" in email else email
        headers = self._get_headers_with_ip_rotation()
        resp = self._http.get(f"{self.BASE_URL}/mailbox/{username}", timeout=30, headers=headers)
        if resp.status_code != 200:
            raise TempMailError(f"Failed to load mailbox: {resp.status_code}", self.name)
        soup = BeautifulSoup(resp.text, "html.parser")
        messages = []
        # Mailnesia uses a table for the inbox
        for row in soup.find_all("tr"):
            cells = row.find_all("td")
            if len(cells) < 3:
                continue
            sender = cells[0].get_text(strip=True)
            subject = cells[1].get_text(strip=True)
            time_str = cells[2].get_text(strip=True)
            # Look for a link with message ID
            link = row.find("a")
            msg_id = ""
            if link:
                href = link.get("href", "")
                msg_id = href.rstrip("/").split("/")[-1]
            try:
                date = datetime.strptime(time_str, "%Y-%m-%d %H:%M:%S")
            except ValueError:
                date = datetime.now()
            if sender or subject:
                messages.append(
                    Message(id=msg_id or subject, sender=sender, subject=subject, date=date)
                )
        return messages

    def read_message(self, message_id: str) -> MessageDetail:
        if not self._username:
            raise TempMailError("No email generated — call generate_email() first", self.name)
        # Mailnesia message URL: /mailbox/{username}/{message_id}
        url = f"{self.BASE_URL}/mailbox/{self._username}/{message_id}"
        headers = self._get_headers_with_ip_rotation()
        resp = self._http.get(url, timeout=30, headers=headers)
        if resp.status_code != 200:
            raise NotFoundError(f"Message {message_id} not found", self.name)
        soup = BeautifulSoup(resp.text, "html.parser")
        # The message body is typically in a specific div
        body_div = soup.find("div", id="message") or soup.find("div", class_=re.compile(r"message|body|mail"))
        body_html = str(body_div) if body_div else resp.text
        body_text = body_div.get_text(separator="\n", strip=True) if body_div else soup.get_text(strip=True)
        return MessageDetail(
            id=message_id,
            sender="",
            subject="",
            date=datetime.now(),
            body_text=body_text,
            body_html=body_html,
            attachments=[],
        )

    def delete_email(self, email: str) -> bool:
        self._email = None
        self._username = None
        return True
