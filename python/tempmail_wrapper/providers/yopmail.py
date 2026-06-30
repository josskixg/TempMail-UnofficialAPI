"""yopmail.com provider implementation (HTML scraping)."""

from __future__ import annotations

import random
import re
import string
from datetime import datetime, timezone

from bs4 import BeautifulSoup

from ..base import TempMailProvider
from ..exceptions import NotFoundError, TempMailError
from ..http_client import HttpClient
from ..models import Message, MessageDetail


class YopmailProvider(TempMailProvider):
    """Provider for yopmail.com — no auth, HTML scraping."""

    BASE_URL = "https://yopmail.com"

    def __init__(
        self,
        proxies: list[str] | None = None,
        random_ua: bool = True,
        **kwargs,
    ) -> None:
        self._http = HttpClient(proxies=proxies, random_ua=random_ua, use_cookies=True)
        self._yp: str = ""
        self._yj: str = ""
        self._v: str = ""
        self._last_email: str | None = None
        self._initialized: bool = False

    @property
    def name(self) -> str:
        return "yopmail"

    @property
    def _session(self):
        return self._http.session

    def _init_session(self, username: str) -> None:
        """Follow the exact 5-step session initialization flow."""
        # Step 1: GET /en/ -> extract yp and v
        resp = self._http.get(f"{self.BASE_URL}/en/", timeout=30)
        if resp.status_code != 200:
            raise TempMailError(f"Failed to load yopmail: {resp.status_code}", self.name)

        yp_match = re.search(r'name="yp" id="yp" value="([^"]+)"', resp.text)
        if yp_match:
            self._yp = yp_match.group(1)

        v_match = re.search(r'/ver/([0-9.]+)/webmail\.js', resp.text)
        if v_match:
            self._v = v_match.group(1)
        else:
            self._v = "5.0"

        # Step 2: GET /en/?login={username} -> extract new yp
        resp = self._http.get(f"{self.BASE_URL}/en/?login={username}", timeout=30)
        yp_match = re.search(r'name="yp" id="yp" value="([^"]+)"', resp.text)
        if yp_match:
            self._yp = yp_match.group(1)

        # Step 3: POST /en/ with form body
        resp = self._http.post(
            f"{self.BASE_URL}/en/",
            data={"login": username, "id": "", "yp": self._yp},
            timeout=30,
        )
        if resp.status_code != 200:
            raise TempMailError(f"Login failed: {resp.status_code}", self.name)

        # Step 4: GET /ver/{v}/webmail.js -> extract yj
        resp = self._http.get(f"{self.BASE_URL}/ver/{self._v}/webmail.js", timeout=30)
        yj_match = re.search(r"value\+'\&yj\=([0-9a-zA-Z]*)\&v\='", resp.text)
        if yj_match:
            self._yj = yj_match.group(1)

        # Step 5: Set cookie ytime
        now = datetime.now(timezone.utc)
        ytime = f"{now.hour:02d}:{now.minute:02d}"
        self._http.session.cookies.set("ytime", ytime, domain=".yopmail.com")

        self._initialized = True

    def _ensure_session(self, email: str) -> str:
        """Ensure session is initialized for the given email, return username."""
        username = email.split("@")[0]
        if not self._initialized or self._last_email != email:
            self._init_session(username)
            self._last_email = email
        return username

    def generate_email(self) -> str:
        local = "".join(random.choices(string.ascii_lowercase + string.digits, k=10))
        email = f"{local}@yopmail.com"
        self._ensure_session(email)
        return email

    def get_inbox(self, email: str) -> list[Message]:
        username = self._ensure_session(email)
        resp = self._http.get(
            f"{self.BASE_URL}/en/inbox",
            params={
                "login": username,
                "p": "1",
                "d": "",
                "ctrl": "",
                "yp": self._yp,
                "yj": self._yj,
                "v": self._v,
                "r_c": "",
                "id": "",
                "ad": "0",
            },
            timeout=30,
        )
        if resp.status_code != 200:
            raise TempMailError(f"Inbox fetch failed: {resp.status_code}", self.name)

        soup = BeautifulSoup(resp.text, "html.parser")
        messages: list[Message] = []

        for div in soup.select("div.m"):
            raw_id = div.get("id", "")
            mail_id = str(raw_id) if raw_id else ""
            if not mail_id:
                continue
            sender_el = div.select_one("span.lmf")
            sender = sender_el.get_text(strip=True) if sender_el else ""
            subject_el = div.select_one("span.lms")
            subject = subject_el.get_text(strip=True) if subject_el else ""

            messages.append(Message(
                id=mail_id,
                sender=sender,
                subject=subject,
                date=datetime.now(),
            ))

        return messages

    def read_message(self, message_id: str) -> MessageDetail:
        if not self._last_email:
            raise TempMailError("No email context. Call get_inbox(email) first.", self.name)

        username = self._last_email.split("@")[0]
        resp = self._http.get(
            f"{self.BASE_URL}/en/mail",
            params={
                "b": username,
                "id": message_id,
                "yp": self._yp,
                "yj": self._yj,
                "v": self._v,
            },
            timeout=30,
        )
        if resp.status_code != 200:
            raise NotFoundError(f"Message {message_id} not found", self.name)

        soup = BeautifulSoup(resp.text, "html.parser")
        mail_div = soup.select_one("div#mail")
        if not mail_div:
            raise NotFoundError(f"Message {message_id} body not found", self.name)

        body_html = str(mail_div)
        body_text = mail_div.get_text(separator="\n", strip=True)

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
        # YOPmail has no delete API — mails expire automatically
        return True
