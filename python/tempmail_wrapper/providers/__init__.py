"""Provider implementations."""

from .dropmail import DropmailProvider
from .guerrilla_mail import GuerrillaMailProvider
from .mail_tm import MailTmProvider
from .one_sec_email import OneSecEmailProvider
from .yopmail import YopmailProvider
from .ncaori_mail import NcaoriMailProvider

__all__ = [
    "DropmailProvider",
    "GuerrillaMailProvider",
    "MailTmProvider",
    "OneSecEmailProvider",
    "YopmailProvider",
    "NcaoriMailProvider",
]
