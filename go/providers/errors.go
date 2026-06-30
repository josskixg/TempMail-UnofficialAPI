package providers

import (
	"fmt"
	"net/http"
)

// TempMailError is a generic error returned by providers.
type TempMailError struct {
	Provider   string
	StatusCode int
	Message    string
}

func (e *TempMailError) Error() string {
	if e.StatusCode != 0 {
		return fmt.Sprintf("%s: %s (HTTP %d)", e.Provider, e.Message, e.StatusCode)
	}
	return fmt.Sprintf("%s: %s", e.Provider, e.Message)
}

// RateLimitError indicates the API rate limit was exceeded.
type RateLimitError struct {
	Provider   string
	StatusCode int
	RetryAfter int
}

func (e *RateLimitError) Error() string {
	if e.RetryAfter > 0 {
		return fmt.Sprintf("%s: rate limit exceeded, retry after %ds (HTTP %d)", e.Provider, e.RetryAfter, e.StatusCode)
	}
	return fmt.Sprintf("%s: rate limit exceeded (HTTP %d)", e.Provider, e.StatusCode)
}

// NotFoundError indicates the requested resource does not exist.
type NotFoundError struct {
	Provider string
	Resource string
}

func (e *NotFoundError) Error() string {
	return fmt.Sprintf("%s: %s not found", e.Provider, e.Resource)
}

// CheckResponse maps common HTTP status codes to typed errors.
func CheckResponse(provider string, resp *http.Response, body string) error {
	switch {
	case resp.StatusCode == http.StatusNotFound:
		return &NotFoundError{Provider: provider, Resource: "resource"}
	case resp.StatusCode == http.StatusTooManyRequests:
		return &RateLimitError{Provider: provider, StatusCode: resp.StatusCode}
	case resp.StatusCode >= 400:
		return &TempMailError{Provider: provider, StatusCode: resp.StatusCode, Message: body}
	default:
		return nil
	}
}
