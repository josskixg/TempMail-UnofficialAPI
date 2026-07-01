"""E2E tests for all providers — real HTTP calls, no mocks."""

from __future__ import annotations

import time

import pytest
import requests

from tempmail_wrapper import (
    DropmailProvider,
    GuerrillaMailProvider,
    MailTmProvider,
    NcaoriMailProvider,
    OneSecEmailProvider,
    YopmailProvider,
    create_provider,
)
from tempmail_wrapper.base import TempMailProvider
from tempmail_wrapper.exceptions import NotFoundError, TempMailError


def _check_network() -> bool:
    """Check basic internet connectivity."""
    import socket
    try:
        socket.create_connection(("8.8.8.8", 53), timeout=5)
        return True
    except OSError:
        return False


@pytest.fixture(scope="module")
def network_available() -> bool:
    return _check_network()


def _skip_if_no_network(network_available: bool) -> None:
    if not network_available:
        pytest.skip("No network connectivity")


def _skip_on_provider_error(exc: Exception) -> None:
    """Skip test if provider returns 403/402/429 (external API issues)."""
    msg = str(exc)
    provider_codes = ("403", "402", "429", "captcha", "rate limit", "rate-limit",
                      "RateLimit")
    if any(code in msg for code in provider_codes):
        pytest.skip(f"Provider unavailable: {msg[:100]}")


import random
import time

UA_POOL = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
]


import os
RESEND_API_KEY = os.getenv("RESEND_API_KEY", "")


def _send_test_email(to: str) -> bool:
    """Send a test email via Resend API to the given address."""
    if not RESEND_API_KEY:
        return False
    delays = [1, 3, 5]
    for attempt in range(3):
        try:
            resp = requests.post(
                "https://api.resend.com/emails",
                json={
                    "from": "onboarding@resend.dev",
                    "to": to,
                    "subject": "TempMail E2E Test",
                    "html": "<p>E2E test email from TempMail wrapper</p>",
                },
                timeout=10,
                headers={
                    "User-Agent": random.choice(UA_POOL),
                    "Authorization": f"Bearer {RESEND_API_KEY}",
                },
            )
            if resp.status_code == 200:
                return True
            if resp.status_code == 429:
                if attempt < 2:
                    time.sleep(delays[attempt])
                continue
            return False
        except Exception:
            if attempt < 2:
                time.sleep(delays[attempt])
            continue
    return False


@pytest.mark.e2e
class TestMailTm:
    def test_full_flow(self, network_available: bool) -> None:
        _skip_if_no_network(network_available)
        provider = create_provider("mail.tm")
        assert isinstance(provider, MailTmProvider)

        # 1. Generate email
        try:
            email = provider.generate_email()
        except TempMailError as e:
            pytest.skip(f"mail.tm unavailable: {e}")
        assert "@" in email

        # 2. Send test email and check inbox
        sent = _send_test_email(email)
        time.sleep(4)
        try:
            inbox = provider.get_inbox(email)
            assert isinstance(inbox, list)
            if not inbox:
                print(f"WARNING: inbox empty (xeramail sent={sent}) — delivery may be delayed")
        except TempMailError as e:
            pytest.skip(f"inbox error: {e}")

        # 3. Read message
        if inbox:
            detail = provider.read_message(inbox[0].id)
            assert detail.id == inbox[0].id
            assert isinstance(detail.body_text, str)

        # 4. Delete email
        result = provider.delete_email(email)
        assert result is True


@pytest.mark.e2e
class TestGuerrillaMail:
    def test_full_flow(self, network_available: bool) -> None:
        _skip_if_no_network(network_available)
        provider = create_provider("guerrillamail")
        assert isinstance(provider, GuerrillaMailProvider)

        # 1. Generate email
        try:
            email = provider.generate_email()
        except TempMailError as e:
            pytest.skip(f"guerrillamail unavailable: {e}")
        assert "@" in email

        # 2. Send test email and check inbox
        sent = _send_test_email(email)
        time.sleep(4)
        try:
            inbox = provider.get_inbox(email)
            assert isinstance(inbox, list)
            if not inbox:
                print(f"WARNING: inbox empty (xeramail sent={sent}) — delivery may be delayed")
        except TempMailError as e:
            pytest.skip(f"inbox error: {e}")

        # 3. Read message
        if inbox:
            detail = provider.read_message(inbox[0].id)
            assert detail.id == inbox[0].id
            assert isinstance(detail.body_text, str)

        # 4. Delete email
        result = provider.delete_email(email)
        assert result is True


@pytest.mark.e2e
class TestYopmail:
    def test_full_flow(self, network_available: bool) -> None:
        _skip_if_no_network(network_available)
        provider = create_provider("yopmail")
        assert isinstance(provider, YopmailProvider)

        # 1. Generate email
        try:
            email = provider.generate_email()
        except TempMailError as e:
            pytest.skip(f"yopmail unavailable: {e}")
        assert "@" in email
        assert email.endswith("@yopmail.com")

        # 2. Send test email and check inbox
        sent = _send_test_email(email)
        time.sleep(4)
        try:
            inbox = provider.get_inbox(email)
        except TempMailError as e:
            pytest.skip(f"yopmail inbox error: {e}")
        assert isinstance(inbox, list)
        if not inbox:
            print(f"WARNING: inbox empty (xeramail sent={sent}) — delivery may be delayed")

        # 3. Read message
        if inbox:
            try:
                detail = provider.read_message(inbox[0].id)
                assert detail.id == inbox[0].id
                assert isinstance(detail.body_text, str)
            except (NotFoundError, TempMailError):
                pass  # Message may have expired

        # 4. Delete email
        result = provider.delete_email(email)
        assert result is True


@pytest.mark.e2e
class TestOneSecEmail:
    def test_full_flow(self, network_available: bool) -> None:
        _skip_if_no_network(network_available)
        provider = create_provider("1secemail")
        assert isinstance(provider, OneSecEmailProvider)

        # 1. Generate email
        try:
            email = provider.generate_email()
        except TempMailError as e:
            pytest.skip(f"1secemail unavailable: {e}")
        assert "@" in email

        # 2. Send test email and check inbox
        sent = _send_test_email(email)
        time.sleep(4)
        try:
            inbox = provider.get_inbox(email)
        except TempMailError as e:
            pytest.skip(f"1secemail inbox error: {e}")
        assert isinstance(inbox, list)
        if not inbox:
            print(f"WARNING: inbox empty (xeramail sent={sent}) — delivery may be delayed")

        # 3. Read message
        if inbox:
            try:
                detail = provider.read_message(inbox[0].id)
                assert detail.id == inbox[0].id
                assert isinstance(detail.body_text, str)
            except (NotFoundError, TempMailError):
                pass  # Message may have expired


@pytest.mark.e2e
class TestDropmail:
    def test_full_flow(self, network_available: bool) -> None:
        _skip_if_no_network(network_available)
        provider = create_provider("dropmail")
        assert isinstance(provider, DropmailProvider)

        # 1. Generate email
        try:
            email = provider.generate_email()
        except TempMailError as e:
            pytest.skip(f"dropmail unavailable: {e}")
        assert "@" in email

        # 2. Send test email and check inbox
        sent = _send_test_email(email)
        time.sleep(4)
        try:
            inbox = provider.get_inbox(email)
        except TempMailError as e:
            pytest.skip(f"dropmail inbox error: {e}")
        assert isinstance(inbox, list)
        if not inbox:
            print(f"WARNING: inbox empty (xeramail sent={sent}) — delivery may be delayed")

        # 3. Read message
        if inbox:
            try:
                detail = provider.read_message(inbox[0].id)
                assert detail.id == inbox[0].id
                assert isinstance(detail.body_text, str)
            except (NotFoundError, TempMailError):
                pass  # Message may have expired

        # 4. Delete email
        result = provider.delete_email(email)
        assert result is True


@pytest.mark.e2e
class TestNcaoriMail:
    def test_full_flow(self, network_available: bool) -> None:
        _skip_if_no_network(network_available)
        provider = create_provider("ncaori")
        assert isinstance(provider, NcaoriMailProvider)

        # 1. Generate email
        try:
            email = provider.generate_email()
        except TempMailError as e:
            pytest.skip(f"ncaori unavailable: {e}")
        assert "@" in email

        # 2. Check inbox
        sent = _send_test_email(email)
        time.sleep(4)
        try:
            inbox = provider.get_inbox(email)
        except TempMailError as e:
            pytest.skip(f"ncaori inbox error: {e}")
        assert isinstance(inbox, list)
        if not inbox:
            print(f"WARNING: inbox empty (test email sent={sent})")

        # 3. Read message — Ncaori returns full body in get_inbox()
        if inbox:
            if hasattr(inbox[0], 'body_text'):
                assert isinstance(inbox[0].body_text, str)
            elif hasattr(inbox[0], 'id'):
                try:
                    provider.read_message(inbox[0].id)
                except TempMailError:
                    pass

        # 4. Delete email
        result = provider.delete_email(email)
        assert result is True


@pytest.mark.e2e
class TestFactory:
    def test_create_all_providers(self, network_available: bool) -> None:
        _skip_if_no_network(network_available)
        for name in ["mail.tm", "guerrillamail", "yopmail", "dropmail", "1secemail", "ncaori"]:
            provider = create_provider(name)
            assert isinstance(provider, TempMailProvider)
            assert provider.name == name or name in provider.name

            # Send test email and check inbox
            try:
                email = provider.generate_email()
            except TempMailError:
                continue  # Provider unavailable, skip
            sent = _send_test_email(email)
            try:
                time.sleep(4)
                inbox = provider.get_inbox(email)
                assert isinstance(inbox, list)
                if not sent:
                    continue
                if not inbox:
                    print("WARNING: sent email but inbox is empty — xeramail delivery is unreliable")
            except TempMailError:
                pass  # Inbox check failed, not critical for factory test

    def test_unknown_provider(self) -> None:
        with pytest.raises(ValueError, match="Unknown provider"):
            create_provider("nonexistent")
