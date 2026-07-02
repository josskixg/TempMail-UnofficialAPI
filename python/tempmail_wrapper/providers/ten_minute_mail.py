"""10minutemail.net provider implementation (HTML scraping public mailbox)."""

from __future__ import annotations

import re
from datetime import datetime

from ..base import TempMailProvider
from ..exceptions import NotFoundError, TempMailError
from ..http_client import HttpClient
from ..models import Message, MessageDetail


def decode_cf_email(hex_str: str) -> str:
    if len(hex_str) < 4:
        return ""
    try:
        k = int(hex_str[0:2], 16)
        return "".join(
            chr(int(hex_str[i:i+2], 16) ^ k)
            for i in range(2, len(hex_str), 2)
        )
    except ValueError:
        return ""


def strip_html(html: str) -> str:
    return re.sub(r"<[^>]+>", "", html).strip()


class TenMinuteMailProvider(TempMailProvider):
    """Provider for 10minutemail.net — HTML scraping public mailbox."""

    BASE_URL = "https://10minutemail.net"

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
        return "10minutemail"

    def generate_email(self) -> str:
        resp = self._http.get(f"{self.BASE_URL}/", timeout=30)
        if resp.status_code != 200:
            raise TempMailError(f"Failed to get email: {resp.status_code}", self.name)
        html = resp.text
        match = re.search(r'id="fe_text"[^>]*value="([^"]+)"', html, re.IGNORECASE)
        if not match:
            raise TempMailError("No address in response", self.name)
        self._email = match.group(1).strip()
        return self._email

    def get_inbox(self, email: str) -> list[Message]:
        resp = self._http.get(f"{self.BASE_URL}/mailbox.ajax.php", timeout=30)
        if resp.status_code != 200:
            raise TempMailError(f"Inbox error: {resp.status_code}", self.name)
        html = resp.text

        rows = re.findall(r"<tr[^>]*>(.*?)</tr>", html, re.DOTALL | re.IGNORECASE)
        if len(rows) <= 1:
            return []

        messages = []
        # Skip header row
        for row in rows[1:]:
            cells = re.findall(r"<td[^>]*>(.*?)</td>", row, re.DOTALL | re.IGNORECASE)
            if len(cells) < 3:
                continue

            # Extract sender
            sender = ""
            cf_match = re.search(r'data-cfemail="([^"]+)"', cells[0], re.IGNORECASE)
            if cf_match:
                sender = decode_cf_email(cf_match.group(1))
            else:
                sender = strip_html(cells[0])

            subject = strip_html(cells[1])

            # Extract Date
            date_str = ""
            date_match = re.search(r'title="([^"]+)"', cells[2], re.IGNORECASE)
            if date_match:
                date_str = date_match.group(1)
            else:
                date_str = strip_html(cells[2])

            try:
                if "utc" not in date_str.lower():
                    date_str += " UTC"
                # e.g., 2026-07-02 15:45:54 UTC
                date = datetime.strptime(date_str, "%Y-%m-%d %H:%M:%S %Z")
            except Exception:
                date = datetime.now()

            # Message ID
            mid_match = re.search(r"mid=([^'&\"\s>]+)", row, re.IGNORECASE)
            if not mid_match:
                continue
            msg_id = mid_match.group(1)

            messages.append(
                Message(
                    id=msg_id,
                    sender=sender,
                    subject=subject,
                    date=date,
                )
            )
        return messages

    def read_message(self, message_id: str) -> MessageDetail:
        # composite message_id handling (e.g. welcome:email)
        mid = message_id.split(":")[0] if ":" in message_id else message_id

        resp = self._http.get(f"{self.BASE_URL}/readmail.html?mid={mid}", timeout=30)
        if resp.status_code != 200:
            raise NotFoundError(f"Cannot read message: {resp.status_code}", self.name)
        html = resp.text

        body_match = re.search(
            r'class="mailinhtml"[^>]*>(.*?)<div[^>]*style="clear:both;"',
            html,
            re.DOTALL | re.IGNORECASE,
        )
        if not body_match:
            raise NotFoundError(f"Message {message_id} not found", self.name)

        body_html = body_match.group(1).strip()

        # Decode Cloudflare obfuscated email links inside body
        body_html = re.sub(
            r'<(?:a|span)[^>]*class="__cf_email__"[^>]*data-cfemail="([^"]+)"[^>]*>.*?</(?:a|span)>',
            lambda m: decode_cf_email(m.group(1)),
            body_html,
            flags=re.IGNORECASE,
        )
        body_html = re.sub(
            r'href="/cdn-cgi/l/email-protection#([^"]+)"',
            lambda m: f'href="mailto:{decode_cf_email(m.group(1))}"',
            body_html,
            flags=re.IGNORECASE,
        )

        body_text = strip_html(body_html)

        # Subject
        subject_match = re.search(
            r'<div class="mail_header">.*?<h2[^>]*>(.*?)</h2>',
            html,
            re.DOTALL | re.IGNORECASE,
        )
        subject = strip_html(subject_match.group(1)) if subject_match else ""

        # Sender
        from_match = re.search(
            r'<span class="mail_from">(.*?)</span>',
            html,
            re.DOTALL | re.IGNORECASE,
        )
        sender = ""
        if from_match:
            cf_from = re.search(
                r'data-cfemail="([^"]+)"', from_match.group(1), re.IGNORECASE
            )
            sender = (
                decode_cf_email(cf_from.group(1))
                if cf_from
                else strip_html(from_match.group(1))
            )

        return MessageDetail(
            id=mid,
            sender=sender,
            subject=subject,
            date=datetime.now(),
            body_text=body_text,
            body_html=body_html,
            attachments=[],
        )

    def delete_email(self, email: str) -> bool:
        self._email = None
        return True
