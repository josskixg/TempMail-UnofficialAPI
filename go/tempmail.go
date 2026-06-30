package tempmail

import (
	"fmt"
	"strings"
	"time"

	"github.com/josskixg/TempMail-UnofficialAPI/go/providers"
)

// TempMailProvider is the common interface for all temp-mail services.
type TempMailProvider interface {
	GenerateEmail() (string, error)
	GetInbox(email string) ([]Message, error)
	ReadMessage(messageID string) (*MessageDetail, error)
	DeleteEmail(email string) (bool, error)
	WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error)
}

// NewProvider creates a TempMailProvider by name.
// Supported names: "mailtm", "guerrillamail", "yopmail", "dropmail".
// The config map accepts provider-specific keys such as "apiKey" or "token",
// plus anti-429 options:
//   - "proxies": comma-separated list of proxy URLs (e.g. "http://proxy1:8080,http://proxy2:8080")
//   - "randomUa": "true" or "false" to enable User-Agent rotation
func NewProvider(name string, config map[string]string) (TempMailProvider, error) {
	if config == nil {
		config = make(map[string]string)
	}
	switch strings.ToLower(name) {
	case "mailtm":
		return providers.NewMailTM(config), nil
	case "guerrillamail":
		return providers.NewGuerrillaMail(config), nil
	case "yopmail":
		return providers.NewYOPmail(config), nil
	case "dropmail":
		return providers.NewDropmail(config), nil
	case "1secemail":
		return providers.NewOneSecEmail(config), nil
	default:
		return nil, fmt.Errorf("unknown provider: %s", name)
	}
}
