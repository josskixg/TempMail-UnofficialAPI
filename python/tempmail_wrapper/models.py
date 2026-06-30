"""Data models for tempmail_wrapper."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime


@dataclass
class Message:
    """Represents an email message in the inbox."""

    id: str
    sender: str
    subject: str
    date: datetime


@dataclass
class MessageDetail(Message):
    """Extended message with body and attachments."""

    body_text: str = ""
    body_html: str = ""
    attachments: list[dict[str, str | int]] = field(default_factory=list)
