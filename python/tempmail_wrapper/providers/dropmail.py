"""dropmail.me provider implementation (GraphQL)."""

from __future__ import annotations

import logging
import warnings
from collections.abc import Callable
from datetime import datetime

import requests

from ..base import TempMailProvider
from ..exceptions import NotFoundError, TempMailError
from ..http_client import HttpClient
from ..models import Message, MessageDetail

logger = logging.getLogger(__name__)

PADDLE_OCR_URL = "https://mamamacjdjj-padle-predict.hf.space/predict"


def paddle_ocr_solver(img_bytes: bytes) -> str | None:
    """Solve captcha via PaddleOCR HuggingFace space (3 retries)."""
    for attempt in range(3):
        try:
            ocr_resp = requests.post(
                PADDLE_OCR_URL,
                files={"file": ("cap.png", img_bytes, "image/png")},
                timeout=20,
            )
            ocr_data = ocr_resp.json()
            results = ocr_data.get("results", [])
            if results:
                best = results[0]
                confidence = best.get("confidence", 0)
                if confidence >= 0.7:
                    text = best.get("text", "").strip()
                    if text:
                        return text
                logger.debug(
                    "Dropmail: OCR attempt %d confidence %.3f < 0.7, retrying",
                    attempt + 1, confidence,
                )
        except Exception as exc:
            logger.debug("Dropmail: OCR attempt %d failed: %s", attempt + 1, exc)
    return None


class DropmailProvider(TempMailProvider):
    """Provider for dropmail.me — GraphQL API, no auth required."""

    TOKEN_URL = "https://dropmail.me/api/token/generate"

    def __init__(
        self,
        proxies: list[str] | None = None,
        random_ua: bool = True,
        solvers: list[Callable[[bytes], str | None]] | None = None,
        **kwargs,
    ) -> None:
        self._http = HttpClient(proxies=proxies, random_ua=random_ua, use_cookies=True)
        self._solvers = solvers
        self._token: str | None = None
        self._graphql_url: str | None = None
        self._session_id: str | None = None
        self._address_id: str | None = None
        self._email: str | None = None

    @property
    def name(self) -> str:
        return "dropmail"

    def _gql(self, query: str, variables: dict | None = None) -> dict:
        if not self._token:
            raise TempMailError("No token. Call generate_email() first.", self.name)
        payload: dict = {"query": query}
        if variables:
            payload["variables"] = variables
        resp = self._http.post(self._graphql_url, json=payload, timeout=30)  # type: ignore[arg-type]
        if resp.status_code != 200:
            raise TempMailError(f"GraphQL error: {resp.status_code} {resp.text}", self.name)
        data = resp.json()
        if "errors" in data:
            raise TempMailError(f"GraphQL errors: {data['errors']}", self.name)
        return data.get("data", {})

    def _solve_captcha_and_get_token(self, captcha: dict) -> str | None:
        """Download captcha image (same session), solve via PaddleOCR, submit solution.
        Returns 90d token on success, None on failure (caller falls back to 1d).
        """
        v = captcha.get("v", "3")
        nonce = captcha.get("nonce", "")
        key = captcha.get("key", "")
        sig = captcha.get("_sig", "")

        img_url = (
            f"https://dropmail.me/captcha/image.png"
            f"?_r=0&v={v}&nonce={nonce}&key={key}&_sig={sig}"
        )

        # Step 2: download image — SAME session (cookies matter)
        img_resp = self._http.get(img_url, timeout=15)
        if img_resp.status_code != 200:
            logger.warning("Dropmail: captcha image download failed (%s)", img_resp.status_code)
            return None
        img_bytes = img_resp.content

        # Step 3: solve via solver chain
        text = None
        solvers = self._solvers if self._solvers is not None else [paddle_ocr_solver]
        for solver in solvers:
            try:
                result = solver(img_bytes)
                if result and result.strip():
                    text = result.strip()
                    break
            except Exception as exc:
                logger.debug("Dropmail: solver error: %s", exc)

        if not text:
            logger.warning("Dropmail: all solvers failed, falling back to 1d token")
            return None

        # Step 4: submit solution — SAME session
        solution_resp = self._http.post(
            "https://dropmail.me/captcha/solution",
            data={"response": text, "v": v, "nonce": nonce, "key": key, "_sig": sig},
            timeout=15,
        )
        try:
            sol_data = solution_resp.json()
        except Exception:
            logger.warning("Dropmail: captcha solution response not JSON, falling back")
            return None

        if sol_data.get("result") != "correct":
            logger.warning(
                "Dropmail: captcha solution rejected (%s), falling back to 1d token",
                sol_data,
            )
            return None

        # Step 5: retry token generation with 90d — SAME session
        token_resp = self._http.post(
            self.TOKEN_URL,
            json={"type": "af", "lifetime": "90d"},
            timeout=30,
        )
        if token_resp.status_code != 200:
            logger.warning(
                "Dropmail: 90d token generation failed (%s) after captcha solve, falling back",
                token_resp.status_code,
            )
            return None

        token = token_resp.json().get("token")
        if not token:
            logger.warning("Dropmail: no token in 90d response, falling back")
            return None

        return token

    def generate_email(self) -> str:
        # Step 1: try 1d token (fast, no captcha)
        resp = self._http.post(
            self.TOKEN_URL,
            json={"type": "af", "lifetime": "1d"},
            timeout=30,
        )

        if resp.status_code == 200:
            data = resp.json()
            self._token = data.get("token")
            if not self._token:
                raise TempMailError("No token in response", self.name)
        elif resp.status_code == 402:
            # Captcha required — try 90d flow
            try:
                captcha_data = resp.json().get("captcha", {})
                self._token = self._solve_captcha_and_get_token(captcha_data)
            except Exception as exc:
                logger.warning("Dropmail: captcha flow error: %s", exc)
                self._token = None

            if not self._token:
                # Fallback: request 1d token again on fresh session
                warnings.warn(
                    "Dropmail: captcha solve failed, retrying with 1d token",
                    stacklevel=2,
                )
                resp2 = self._http.post(
                    self.TOKEN_URL,
                    json={"type": "af", "lifetime": "1d"},
                    timeout=30,
                )
                if resp2.status_code != 200:
                    raise TempMailError(
                        f"Token generation failed: {resp2.status_code}", self.name
                    )
                self._token = resp2.json().get("token")
                if not self._token:
                    raise TempMailError("No token in response", self.name)
        else:
            raise TempMailError(f"Token generation failed: {resp.status_code}", self.name)

        self._graphql_url = f"https://dropmail.me/api/graphql/{self._token}"

        mutation = """
            mutation {
                introduceSession {
                    id
                    addresses {
                        id
                        address
                        restoreKey
                    }
                }
            }
        """
        data = self._gql(mutation)
        session = data.get("introduceSession")
        if not session:
            raise TempMailError("Failed to create session", self.name)

        self._session_id = session["id"]
        addresses = session.get("addresses", [])
        if not addresses:
            raise TempMailError("No address generated", self.name)

        addr = addresses[0]
        self._address_id = addr["id"]
        self._email = addr["address"]
        return self._email

    def get_inbox(self, email: str) -> list[Message]:
        query = """
            query($sid: ID!) {
                session(id: $sid) {
                    mails {
                        id
                        fromAddr
                        headerSubject
                        receivedAt
                    }
                }
            }
        """
        data = self._gql(query, {"sid": self._session_id})
        session_data = data.get("session", {})
        messages = []
        for mail in session_data.get("mails", []):
            received = mail.get("receivedAt", "2000-01-01T00:00:00Z")
            if received.endswith("Z"):
                received = received[:-1] + "+00:00"
            try:
                date = datetime.fromisoformat(received)
            except ValueError:
                date = datetime.now()
            messages.append(
                Message(
                    id=mail["id"],
                    sender=mail.get("fromAddr", ""),
                    subject=mail.get("headerSubject", ""),
                    date=date,
                )
            )
        return messages

    def read_message(self, message_id: str) -> MessageDetail:
        query = """
            query($sid: ID!) {
                session(id: $sid) {
                    mails {
                        id
                        fromAddr
                        headerSubject
                        receivedAt
                        text
                        html
                        attachments {
                            name
                            mime
                            rawSize
                        }
                    }
                }
            }
        """
        data = self._gql(query, {"sid": self._session_id})
        session_data = data.get("session", {})

        for mail in session_data.get("mails", []):
            if mail["id"] == message_id:
                received = mail.get("receivedAt", "2000-01-01T00:00:00Z")
                if received.endswith("Z"):
                    received = received[:-1] + "+00:00"
                try:
                    date = datetime.fromisoformat(received)
                except ValueError:
                    date = datetime.now()

                attachments = [
                    {
                        "filename": att.get("name", ""),
                        "content_type": att.get("mime", ""),
                        "size": att.get("rawSize", 0),
                    }
                    for att in mail.get("attachments", [])
                ]

                return MessageDetail(
                    id=mail["id"],
                    sender=mail.get("fromAddr", ""),
                    subject=mail.get("headerSubject", ""),
                    date=date,
                    body_text=mail.get("text", ""),
                    body_html=mail.get("html", ""),
                    attachments=attachments,
                )

        raise NotFoundError(f"Message {message_id} not found", self.name)

    def delete_email(self, email: str) -> bool:
        if not self._address_id:
            return True

        mutation = """
            mutation($input: DeleteAddressInput!) {
                deleteAddress(input: $input) {
                    id
                }
            }
        """
        try:
            self._gql(mutation, {"input": {"addressId": self._address_id}})
        except TempMailError:
            pass

        self._token = None
        self._graphql_url = None
        self._session_id = None
        self._address_id = None
        self._email = None
        return True
