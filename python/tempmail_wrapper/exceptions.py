"""Custom exceptions for tempmail_wrapper."""


class TempMailError(Exception):
    """Base exception for all tempmail errors."""

    def __init__(self, message: str, provider: str | None = None):
        self.provider = provider
        super().__init__(f"[{provider}] {message}" if provider else message)


class RateLimitError(TempMailError):
    """Raised when API rate limit is hit."""

    def __init__(self, message: str = "Rate limit exceeded", provider: str | None = None, retry_after: int | None = None):
        self.retry_after = retry_after
        super().__init__(message, provider)


class NotFoundError(TempMailError):
    """Raised when requested resource is not found."""

    def __init__(self, message: str = "Resource not found", provider: str | None = None):
        super().__init__(message, provider)
