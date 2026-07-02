"""emailfake.com provider implementation (HTML scraping with surl cookie)."""

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


class EmailfakeProvider(TempMailProvider):
    """Provider for emailfake.com — surl cookie + channel URL, HTML scraping."""

    BASE_URL = "https://emailfake.com"
    COOKIE_DOMAIN = ".emailfake.com"

    def __init__(
        self,
        proxies: list[str] | None = None,
        random_ua: bool = True,
        **kwargs,
    ) -> None:
        self._http = HttpClient(proxies=proxies, random_ua=random_ua, use_cookies=True)
        self._email: str | None = None
        self._domain: str | None = None
        self._username: str | None = None

    @property
    def name(self) -> str:
        return "emailfake"

    def _get_domains(self) -> list[str]:
        """Fetch available domains from the page."""
        import random as _r
        ch = _r.randint(1, 9)
        resp = self._http.get(f"{self.BASE_URL}/channel{ch}/", timeout=30)
        if resp.status_code != 200:
            raise TempMailError(f"Failed to load page: {resp.status_code}", self.name)
        soup = BeautifulSoup(resp.text, "html.parser")
        # Domains are in option tags or in the page text after @
        domains = []
        for opt in soup.find_all("option"):
            val = opt.get("value", "").strip()
            if "." in val and " " not in val and "@" not in val:
                domains.append(val)
        if not domains:
            # Fallback: parse from text - look for domain-like strings
            for text in soup.stripped_strings:
                if re.match(r"^[a-z0-9-]+\.[a-z.]{2,}$", text) and "emailfake" not in text:
                    domains.append(text)
        if not domains:
            raise TempMailError("No domains found on page", self.name)
        return list(set(domains))

    def generate_email(self) -> str:
        domains = self._get_domains()
        self._domain = random.choice(domains)
        self._username = "".join(
            random.choices(string.ascii_lowercase + string.digits, k=10)
        )
        self._email = f"{self._username}@{self._domain}"
        # Set surl cookie: surl={domain}/{username}
        self._http.session.cookies.set(
            "surl", f"{self._domain}/{self._username}", domain=self.COOKIE_DOMAIN
        )
        return self._email

    def get_inbox(self, email: str) -> list[Message]:
        if not self._domain or not self._username:
            raise TempMailError("No email generated — call generate_email() first", self.name)
        ch = random.randint(1, 9)
        resp = self._http.get(f"{self.BASE_URL}/channel{ch}/", timeout=30)
        if resp.status_code != 200:
            raise TempMailError(f"Failed to load inbox: {resp.status_code}", self.name)
        soup = BeautifulSoup(resp.text, "html.parser")
        email_table = soup.find("div", id="email-table")
        if not email_table:
            return []
        messages = []
        for item in email_table.find_all("div", class_=re.compile(r"list-group-item")):
            # Skip the outer container if it has nested list-group-items
            if item.find("div", class_=re.compile(r"list-group-item")):
                continue
            
            from_div = item.find("div", class_=re.compile(r"from_div"))
            subj_div = item.find("div", class_=re.compile(r"subj_div"))
            time_div = item.find("div", class_=re.compile(r"time_div"))
            
            sender = from_div.get_text(strip=True) if from_div else ""
            subject = subj_div.get_text(strip=True) if subj_div else ""
            time_str = time_div.get_text(strip=True) if time_div else ""
            
            # Use subject+time as message ID since there's no hash in the list view
            msg_id = f"{subject}_{time_str}".replace(" ", "_")
            
            try:
                date = datetime.strptime(time_str, "%Y-%m-%d %H:%M:%S")
            except ValueError:
                date = datetime.now()
            
            if sender or subject:  # Only add if we found actual content
                messages.append(
                    Message(id=msg_id, sender=sender, subject=subject, date=date)
                )
        return messages

    def read_message(self, message_id: str) -> MessageDetail:
        if not self._domain or not self._username:
            raise TempMailError("No email generated — call generate_email() first", self.name)
        url = f"{self.BASE_URL}/{self._domain}/{self._username}/{message_id}"
        resp = self._http.get(url, timeout=30)
        if resp.status_code != 200:
            raise NotFoundError(f"Message {message_id} not found", self.name)
        soup = BeautifulSoup(resp.text, "html.parser")
        msg_div = soup.find("div", id="message")
        if not msg_div:
            raise NotFoundError(f"Message {message_id} body not found", self.name)
        body_html = str(msg_div)
        body_text = msg_div.get_text(separator="\n", strip=True)
        # Try to extract sender/subject from the page
        from_div = soup.find("div", class_=re.compile(r"from_div"))
        subj_div = soup.find("div", class_=re.compile(r"subj_div"))
        time_div = soup.find("div", class_=re.compile(r"time_div"))
        sender = from_div.get_text(strip=True) if from_div else ""
        subject = subj_div.get_text(strip=True) if subj_div else ""
        time_str = time_div.get_text(strip=True) if time_div else ""
        try:
            date = datetime.strptime(time_str, "%Y-%m-%d %H:%M:%S")
        except ValueError:
            date = datetime.now()
        return MessageDetail(
            id=message_id,
            sender=sender,
            subject=subject,
            date=date,
            body_text=body_text,
            body_html=body_html,
            attachments=[],
        )

    def delete_email(self, email: str) -> bool:
        self._email = None
        self._domain = None
        self._username = None
        return True
