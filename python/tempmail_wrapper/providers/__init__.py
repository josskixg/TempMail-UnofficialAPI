"""Provider implementations."""

from .dropmail import DropmailProvider
from .email_temp import EmailTempProvider
from .emailfake import EmailfakeProvider
from .generator_email import GeneratorEmailProvider
from .guerrilla_mail import GuerrillaMailProvider
from .mail_tm import MailTmProvider
from .mailnesia import MailnesiaProvider
from .one_sec_email import OneSecEmailProvider
from .temp_mail_io import TempMailIoProvider
from .tempmail_lol import TempmailLolProvider
from .tempmail_plus import TempmailPlusProvider
from .tempmailc import TempmailcProvider
from .ten_minute_mail import TenMinuteMailProvider
from .yopmail import YopmailProvider
from .ncaori_mail import NcaoriMailProvider
from .zoromail import ZoromailProvider

__all__ = [
    "DropmailProvider",
    "EmailTempProvider",
    "EmailfakeProvider",
    "GeneratorEmailProvider",
    "GuerrillaMailProvider",
    "MailTmProvider",
    "MailnesiaProvider",
    "OneSecEmailProvider",
    "TempMailIoProvider",
    "TempmailLolProvider",
    "TempmailPlusProvider",
    "TempmailcProvider",
    "TenMinuteMailProvider",
    "YopmailProvider",
    "NcaoriMailProvider",
    "ZoromailProvider",
]
