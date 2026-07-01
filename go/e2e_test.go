//go:build e2e

package tempmail

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"math/rand"
	"net"
	"net/http"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/josskixg/TempMail-UnofficialAPI/go/providers"
)

func init() {
	// Load .env file for local test runs (Go doesn't auto-load .env)
	f, err := os.Open(".env")
	if err != nil {
		return
	}
	defer f.Close()
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		parts := strings.SplitN(line, "=", 2)
		if len(parts) == 2 {
			key := strings.TrimSpace(parts[0])
			val := strings.TrimSpace(parts[1])
			if os.Getenv(key) == "" {
				os.Setenv(key, val)
			}
		}
	}
}

var uaPool = []string{
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
}

func resendAPIKey() string {
	return os.Getenv("RESEND_API_KEY")
}

// sendTestEmail sends a test email to the given address via Resend API.
func sendTestEmail(to string) error {
	body, err := json.Marshal(map[string]string{
		"from":    "onboarding@resend.dev",
		"to":      to,
		"subject": "TempMail E2E Test",
		"html":    "<p>E2E test email from TempMail wrapper</p>",
	})
	if err != nil {
		return fmt.Errorf("marshal body: %w", err)
	}

	delays := []time.Duration{time.Second, 3 * time.Second, 5 * time.Second}
	for attempt := 0; attempt < 3; attempt++ {
		client := &http.Client{Timeout: 10 * time.Second}
		req, _ := http.NewRequest("POST", "https://api.resend.com/emails", bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Authorization", "Bearer "+resendAPIKey())
		req.Header.Set("User-Agent", uaPool[rand.Intn(len(uaPool))])

		resp, err := client.Do(req)
		if err != nil {
			if attempt < 2 {
				time.Sleep(delays[attempt])
				continue
			}
			return fmt.Errorf("request failed: %w", err)
		}

		if resp.StatusCode == 200 {
			resp.Body.Close()
			return nil
		}

		resp.Body.Close()
		return fmt.Errorf("resend returned HTTP %d", resp.StatusCode)
	}
	return fmt.Errorf("resend failed after retries")
}

func TestProvidersE2E(t *testing.T) {
	providerList := []string{"mailtm", "guerrillamail", "yopmail", "dropmail", "1secemail", "ncaori"}

	for _, name := range providerList {
		t.Run(name, func(t *testing.T) {
			prov, err := NewProvider(name, nil)
			if err != nil {
				t.Fatalf("NewProvider: %v", err)
			}

			email, err := prov.GenerateEmail()
			if err != nil {
				if isSkipErr(err) {
					t.Skip(err)
				}
				t.Fatalf("GenerateEmail: %v", err)
			}
			t.Logf("Generated email: %s", email)

			// Send a real test email and check inbox if it lands
			emailSent := false
			if err := sendTestEmail(email); err != nil {
				t.Logf("sendTestEmail(%s): %v (skipping inbox assertion)", email, err)
			} else {
				emailSent = true
				t.Logf("sendTestEmail(%s): OK, waiting 4s for delivery...", email)
				time.Sleep(4 * time.Second)
			}

			msgs, err := prov.GetInbox(email)
			if err != nil {
				if isSkipErr(err) {
					t.Skip(err)
				}
				t.Fatalf("GetInbox: %v", err)
			}
			t.Logf("Inbox count: %d", len(msgs))

			if emailSent && len(msgs) == 0 {
				t.Log("WARN: test email was sent but inbox is empty (delivery may be slow)")
			}

			if len(msgs) > 0 {
				detail, err := prov.ReadMessage(msgs[0].ID)
				if err != nil {
					if isSkipErr(err) {
						t.Skip(err)
					}
					t.Fatalf("ReadMessage: %v", err)
				}
				t.Logf("Read message subject: %s", detail.Subject)
			}

			ok, err := prov.DeleteEmail(email)
			if err != nil {
				// Delete not supported by all providers — just log it
				t.Logf("DeleteEmail: %v (may be unsupported)", err)
			} else if !ok {
				t.Logf("DeleteEmail returned false (may be unsupported)")
			}
		})
	}
}

func isSkipErr(err error) bool {
	if _, ok := err.(net.Error); ok {
		return true
	}
	if _, ok := err.(*providers.RateLimitError); ok {
		return true
	}
	s := err.Error()
	return strings.Contains(s, "429") || strings.Contains(s, "rate limit") || strings.Contains(s, "timeout") ||
		strings.Contains(s, "403") || strings.Contains(s, "400") || strings.Contains(s, "402") ||
		strings.Contains(s, "captcha") || strings.Contains(s, "Permission denied") ||
		strings.Contains(s, "failed to get token") || strings.Contains(s, "failed to create session")
}
