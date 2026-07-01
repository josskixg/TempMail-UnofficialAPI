"""tempmail_wrapper — A unified Python wrapper for 4 temporary email services."""

from .base import TempMailProvider
from .exceptions import NotFoundError, RateLimitError, TempMailError
from .factory import create_provider
from .http_client import HttpClient
from .models import Message, MessageDetail
from .providers import (
    DropmailProvider,
    GuerrillaMailProvider,
    MailTmProvider,
    NcaoriMailProvider,
    OneSecEmailProvider,
    YopmailProvider,
)

__version__ = "0.1.0"

__all__ = [
    # Factory
    "create_provider",
    # Base
    "TempMailProvider",
    # HTTP Client
    "HttpClient",
    # Models
    "Message",
    "MessageDetail",
    # Exceptions
    "TempMailError",
    "RateLimitError",
    "NotFoundError",
    # Providers
    "MailTmProvider",
    "GuerrillaMailProvider",
    "YopmailProvider",
    "DropmailProvider",
    "NcaoriMailProvider",
    "OneSecEmailProvider",
]
