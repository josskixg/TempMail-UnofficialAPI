"""Abstract base class for all tempmail providers."""

from __future__ import annotations

import time
from abc import ABC, abstractmethod

from .models import Message, MessageDetail


class TempMailProvider(ABC):
    """Base class that all tempmail providers must implement."""

    @property
    @abstractmethod
    def name(self) -> str:
        """Provider name."""
        ...

    @abstractmethod
    def generate_email(self) -> str:
        """Generate a new temporary email address.

        Returns:
            The generated email address.
        """
        ...

    @abstractmethod
    def get_inbox(self, email: str) -> list[Message]:
        """Get inbox messages for the given email.

        Args:
            email: The email address to check.

        Returns:
            List of Message objects.
        """
        ...

    @abstractmethod
    def read_message(self, message_id: str) -> MessageDetail:
        """Read a specific message by ID.

        Args:
            message_id: The message identifier.

        Returns:
            MessageDetail object with full content.
        """
        ...

    @abstractmethod
    def delete_email(self, email: str) -> bool:
        """Delete a temporary email address.

        Args:
            email: The email address to delete.

        Returns:
            True if deleted successfully.
        """
        ...

    def wait_for_email(
        self,
        email: str,
        timeout: int = 60,
        interval: int = 5,
        sender: str | None = None,
        subject_contains: str | None = None,
    ) -> Message | None:
        """Wait for an email matching the criteria.

        Args:
            email: The email address to monitor.
            timeout: Maximum seconds to wait.
            interval: Seconds between checks.
            sender: Optional sender filter.
            subject_contains: Optional subject substring filter.

        Returns:
            Matching Message or None if timeout.
        """
        deadline = time.time() + timeout
        while time.time() < deadline:
            messages = self.get_inbox(email)
            for msg in messages:
                if sender and sender.lower() not in msg.sender.lower():
                    continue
                if subject_contains and subject_contains.lower() not in msg.subject.lower():
                    continue
                return msg
            time.sleep(interval)
        return None
