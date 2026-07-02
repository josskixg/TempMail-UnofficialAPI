"""Data models for tempmail_wrapper."""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from datetime import datetime


def _strip_html(html: str) -> str:
    """Strip HTML tags and normalize whitespace to plain text."""
    # Remove style/script blocks entirely
    text = re.sub(r"<(style|script)[^>]*>.*?</\1>", "", html, flags=re.DOTALL | re.IGNORECASE)
    # Replace common block elements with newlines
    text = re.sub(r"<(br\s*/?|/p|/div|/tr|/li|/h\d)>", "\n", text, flags=re.IGNORECASE)
    # Strip remaining tags
    text = re.sub(r"<[^>]+>", "", text)
    # Decode common HTML entities
    text = (text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", '"')
            .replace("&#39;", "'")
            .replace("&nbsp;", " "))
    # Collapse blank lines and strip
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


@dataclass
class Message:
    """Represents an email message summary in the inbox."""

    id: str
    sender: str
    subject: str
    date: datetime
    preview: str = ""
    has_attachments: bool = False


@dataclass
class MessageDetail(Message):
    """Extended message with full body, headers, and metadata.

    Fields are auto-normalized after provider returns:
    - If only body_html is set, body_text is auto-stripped from it.
    - If only body_text is set, is_html stays False.
    - body_preview is always auto-filled from body_text (max 200 chars).
    - is_html is auto-computed from body_html being non-empty.
    - content_type is auto-inferred if not set by provider.
    """

    body_text: str = ""
    body_html: str = ""
    body_preview: str = ""
    content_type: str = "text/plain"
    raw: str = ""
    headers: dict[str, str] = field(default_factory=dict)
    cc: list[str] = field(default_factory=list)
    reply_to: str = ""
    message_id: str = ""
    size: int = 0
    is_html: bool = False
    attachments: list[dict[str, str | int]] = field(default_factory=list)

    def __post_init__(self) -> None:
        self._normalize()

    def _normalize(self) -> None:
        """Auto-fill derived fields from raw provider data."""
        has_text = bool(self.body_text.strip())
        has_html = bool(self.body_html.strip())

        if has_html and not has_text:
            self.body_text = _strip_html(self.body_html)

        self.is_html = has_html
        self.has_attachments = bool(self.attachments)

        if has_html and self.body_text:
            self.content_type = "multipart/alternative"
        elif has_html:
            self.content_type = "text/html"
        else:
            self.content_type = "text/plain"

        if not self.body_preview:
            self.body_preview = self.body_text[:200].strip()

        if self.message_id and "message-id" not in {k.lower() for k in self.headers}:
            self.headers["Message-ID"] = self.message_id
