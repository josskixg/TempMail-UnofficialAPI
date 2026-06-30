package providers

import (
	"encoding/json"
	"fmt"
	"math/rand"
	"net/http"
	"regexp"
	"strings"
	"time"
)

var oseDomains = []string{
	"qzueos.com", "gaziw.com", "emailgenerator.xyz",
}

// OneSecEmail is the 1secemail.com provider (CSRF-protected web scraping).
type OneSecEmail struct {
	client *HttpClient
	csrf   string
	email  string
}

// NewOneSecEmail creates a new OneSecEmail provider.
func NewOneSecEmail(config map[string]string) *OneSecEmail {
	proxies := ParseProxies(config["proxies"])
	randomUA := config["randomUa"] == "true"
	return &OneSecEmail{
		client: NewHttpClient(proxies, randomUA, true),
	}
}

func (o *OneSecEmail) fetchCSRF() error {
	resp, err := o.client.Get("https://www.1secemail.com/")
	if err != nil {
		return err
	}
	body := string(resp)
	m := regexp.MustCompile(`<meta name="csrf-token" content="([^"]+)">`).FindStringSubmatch(body)
	if len(m) < 2 {
		return &TempMailError{Provider: "1secemail", Message: "CSRF token not found"}
	}
	o.csrf = m[1]
	return nil
}

func (o *OneSecEmail) ensureCSRF() error {
	if o.csrf != "" {
		return nil
	}
	return o.fetchCSRF()
}

func (o *OneSecEmail) postForm(urlPath string, data map[string]string) ([]byte, error) {
	if err := o.ensureCSRF(); err != nil {
		return nil, err
	}
	form := make(map[string]string)
	form["_token"] = o.csrf
	for k, v := range data {
		form[k] = v
	}
	body, _ := json.Marshal(form)
	req, _ := http.NewRequest(http.MethodPost, urlPath, strings.NewReader(string(body)))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-CSRF-TOKEN", o.csrf)
	req.Header.Set("x-xsrf-token", o.csrf)
	req.Header.Set("Referer", "https://www.1secemail.com/")
	return o.client.RawDo(req, body)
}

func randomOSEName() string {
	const chars = "abcdefghijklmnopqrstuvwxyz0123456789"
	b := make([]byte, 10)
	for i := range b {
		b[i] = chars[rand.Intn(len(chars))]
	}
	return string(b)
}

func (o *OneSecEmail) GenerateEmail() (string, error) {
	if err := o.ensureCSRF(); err != nil {
		return "", err
	}
	name := randomOSEName()
	domain := oseDomains[rand.Intn(len(oseDomains))]
	_, err := o.postForm("https://www.1secemail.com/change", map[string]string{"name": name, "domain": domain})
	if err != nil {
		return "", err
	}
	o.email = name + "@" + domain
	return o.email, nil
}

func (o *OneSecEmail) GetInbox(email string) ([]Message, error) {
	resp, err := o.postForm("https://www.1secemail.com/get_messages", map[string]string{})
	if err != nil {
		return nil, err
	}

	var rawItems []json.RawMessage
	// Try as array first
	if err := json.Unmarshal(resp, &rawItems); err != nil {
		// Try as object, look for array fields
		var obj map[string]json.RawMessage
		if err2 := json.Unmarshal(resp, &obj); err2 != nil {
			return nil, &TempMailError{Provider: "1secemail", Message: "parse error: " + err.Error()}
		}
		// Common key names for message arrays
		for _, key := range []string{"messages", "data", "emails", "inbox"} {
			if v, ok := obj[key]; ok {
				if err3 := json.Unmarshal(v, &rawItems); err3 == nil {
					break
				}
			}
		}
		if len(rawItems) == 0 {
			return []Message{}, nil
		}
	}

	var items []struct {
		ID         string `json:"id"`
		From       string `json:"from"`
		FromEmail  string `json:"from_email"`
		Subject    string `json:"subject"`
		ReceivedAt string `json:"receivedAt"`
	}
	for _, ri := range rawItems {
		var it struct {
			ID         string `json:"id"`
			From       string `json:"from"`
			FromEmail  string `json:"from_email"`
			Subject    string `json:"subject"`
			ReceivedAt string `json:"receivedAt"`
		}
		if err := json.Unmarshal(ri, &it); err != nil {
			continue
		}
		items = append(items, it)
	}

	msgs := make([]Message, 0, len(items))
	for _, it := range items {
		sender := it.FromEmail
		if sender == "" {
			sender = it.From
		}
		if sender == "" {
			sender = "unknown"
		}
		dt, _ := parseOSEDate(it.ReceivedAt)
		msgs = append(msgs, Message{
			ID:      it.ID,
			Sender:  sender,
			Subject: firstOfOse(it.Subject, "(no subject)"),
			Date:    dt,
		})
	}
	return msgs, nil
}

func (o *OneSecEmail) ReadMessage(messageID string) (*MessageDetail, error) {
	if err := o.ensureCSRF(); err != nil {
		return nil, err
	}
	resp, err := o.client.Get(fmt.Sprintf("https://www.1secemail.com/view/%s", messageID))
	if err != nil {
		return nil, err
	}
	html := string(resp)
	text := stripOSEHTML(html)

	var sender, subject string
	date := time.Now()
	if m := regexp.MustCompile(`From:\s*([^<\n]+)`).FindStringSubmatch(html); len(m) > 1 {
		sender = strings.TrimSpace(m[1])
	}
	if sender == "" {
		sender = "unknown"
	}
	if m := regexp.MustCompile(`Subject:\s*([^<\n]+)`).FindStringSubmatch(html); len(m) > 1 {
		subject = strings.TrimSpace(m[1])
	}
	if subject == "" {
		subject = "(no subject)"
	}
	if m := regexp.MustCompile(`(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})`).FindStringSubmatch(html); len(m) > 1 {
		date, _ = parseOSEDate(m[1])
	}
	return &MessageDetail{
		Message: Message{
			ID:      messageID,
			Sender:  sender,
			Subject: subject,
			Date:    date,
		},
		BodyText: text,
		BodyHTML: html,
	}, nil
}

func (o *OneSecEmail) DeleteEmail(email string) (bool, error) {
	return true, nil
}

func (o *OneSecEmail) WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	return waitForEmail(o, email, timeout, interval)
}

func parseOSEDate(s string) (time.Time, error) {
	return time.Parse("2006-01-02 15:04:05", s)
}

func stripOSEHTML(s string) string {
	re := regexp.MustCompile(`<[^>]+>`)
	s = re.ReplaceAllString(s, "")
	s = strings.ReplaceAll(s, "\n", " ")
	s = regexp.MustCompile(`\s+`).ReplaceAllString(s, " ")
	return strings.TrimSpace(s)
}

func firstOfOse(vals ...string) string {
	for _, v := range vals {
		if v != "" {
			return v
		}
	}
	return ""
}
