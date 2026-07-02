"""tempmail_wrapper — A unified Python wrapper for temporary email services."""

from .base import TempMailProvider
from .exceptions import NotFoundError, RateLimitError, TempMailError
from .factory import create_provider
from .http_client import HttpClient
from .models import Message, MessageDetail
from .providers import (
    DropmailProvider,
    EmailTempProvider,
    EmailfakeProvider,
    GeneratorEmailProvider,
    GuerrillaMailProvider,
    MailTmProvider,
    MailnesiaProvider,
    NcaoriMailProvider,
    OneSecEmailProvider,
    TempMailIoProvider,
    TempmailLolProvider,
    TempmailPlusProvider,
    TempmailcProvider,
    TenMinuteMailProvider,
    YopmailProvider,
    ZoromailProvider,
)

__version__ = "1.1.0"

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
    # v1.0.0 Providers
    "MailTmProvider",
    "GuerrillaMailProvider",
    "YopmailProvider",
    "DropmailProvider",
    "NcaoriMailProvider",
    "OneSecEmailProvider",
    # v1.1.0 Providers
    "EmailTempProvider",
    "EmailfakeProvider",
    "GeneratorEmailProvider",
    "ZoromailProvider",
    "TempmailLolProvider",
    "TempmailcProvider",
    "TempMailIoProvider",
    "TempmailPlusProvider",
    "MailnesiaProvider",
    "TenMinuteMailProvider",
]
