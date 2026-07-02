package providers

import (
	"bytes"
	"encoding/json"
	"math/rand"
	"strings"
	"time"
)

const zoromailBaseURL = "https://zoromail.com/public_api.php/v1"

// Zoromail is the zoromail.com provider (REST API, no auth).
type Zoromail struct {
	client *HttpClient
	email  string
}

// NewZoromail creates a new Zoromail provider.
func NewZoromail(config map[string]string) *Zoromail {
	proxies := ParseProxies(config["proxies"])
	randomUA := config["randomUa"] == "true"
	return &Zoromail{client: NewHttpClient(proxies, randomUA, false)}
}

// call unwraps the {success,data,error} envelope shared by all zoromail endpoints.
func (z *Zoromail) call(method, path string, body []byte) (json.RawMessage, error) {
	endpoint := zoromailBaseURL + path
	var raw []byte
	var err error
	if method == "GET" {
		raw, err = z.client.Get(endpoint)
	} else {
		raw, err = z.client.Post(endpoint, "application/json", bytes.NewReader(body))
	}
	if err != nil {
		return nil, &TempMailError{Provider: "zoromail", Message: err.Error()}
	}
	var resp struct {
		Success bool            `json:"success"`
		Data    json.RawMessage `json:"data"`
		Error   string          `json:"error"`
	}
	if e := json.Unmarshal(raw, &resp); e != nil {
		return nil, &TempMailError{Provider: "zoromail", Message: "parse error: " + e.Error()}
	}
	if !resp.Success {
		msg := resp.Error
		if msg == "" {
			msg = "unknown error"
		}
		return nil, &TempMailError{Provider: "zoromail", Message: msg}
	}
	return resp.Data, nil
}

func (z *Zoromail) GenerateEmail() (string, error) {
	data, err := z.call("GET", "/domains", nil)
	if err != nil {
		return "", err
	}
	var domains []string
	if e := json.Unmarshal(data, &domains); e != nil || len(domains) == 0 {
		return "", &TempMailError{Provider: "zoromail", Message: "no domains available"}
	}
	domain := domains[rand.Intn(len(domains))]
	payload, _ := json.Marshal(map[string]string{"username": randName(10), "domain": domain})
	edata, err := z.call("POST", "/emails", payload)
	if err != nil {
		return "", err
	}
	var created struct {
		Email string `json:"email"`
	}
	if e := json.Unmarshal(edata, &created); e != nil || created.Email == "" {
		return "", &TempMailError{Provider: "zoromail", Message: "no email in response"}
	}
	z.email = created.Email
	return z.email, nil
}

func (z *Zoromail) GetInbox(email string) ([]Message, error) {
	data, err := z.call("GET", "/emails/"+email+"/messages", nil)
	if err != nil {
		return nil, err
	}
	var items []struct {
		ID      json.RawMessage `json:"id"`
		From    string          `json:"from"`
		Subject string          `json:"subject"`
		Date    string          `json:"date"`
	}
	if e := json.Unmarshal(data, &items); e != nil {
		return nil, &TempMailError{Provider: "zoromail", Message: "parse error: " + e.Error()}
	}
	msgs := make([]Message, len(items))
	for i, it := range items {
		msgs[i] = Message{
			ID:      rawString(it.ID),
			Sender:  it.From,
			Subject: it.Subject,
			Date:    parseMailDate(it.Date),
		}
	}
	return msgs, nil
}

func (z *Zoromail) ReadMessage(messageID string) (*MessageDetail, error) {
	data, err := z.call("GET", "/messages/"+messageID, nil)
	if err != nil {
		return nil, err
	}
	var m struct {
		ID      json.RawMessage `json:"id"`
		From    string          `json:"from"`
		Subject string          `json:"subject"`
		Date    string          `json:"date"`
		Text    string          `json:"text"`
		HTML    string          `json:"html"`
	}
	if e := json.Unmarshal(data, &m); e != nil {
		return nil, &TempMailError{Provider: "zoromail", Message: "parse error: " + e.Error()}
	}
	return &MessageDetail{
		Message: Message{
			ID:      rawString(m.ID),
			Sender:  m.From,
			Subject: m.Subject,
			Date:    parseMailDate(m.Date),
		},
		BodyText: m.Text,
		BodyHTML: m.HTML,
	}, nil
}

func (z *Zoromail) DeleteEmail(email string) (bool, error) {
	z.email = ""
	return true, nil
}

func (z *Zoromail) WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	return waitForEmail(z, email, timeout, interval)
}

// --- shared helpers used by the other v1.1.0 providers ---

// parseMailDate tries common mail timestamp layouts, falling back to now.
func parseMailDate(s string) time.Time {
	s = strings.TrimSpace(s)
	if s == "" {
		return time.Now()
	}
	for _, layout := range []string{
		time.RFC3339,
		"2006-01-02T15:04:05",
		"2006-01-02 15:04:05",
		time.RFC1123Z,
		"Mon, 2 Jan 2006 15:04:05 -0700",
	} {
		if t, err := time.Parse(layout, s); err == nil {
			return t
		}
	}
	return time.Now()
}

// rawString renders a json.RawMessage token as a string (strips quotes),
// handling IDs that arrive as either JSON strings or numbers.
func rawString(b json.RawMessage) string {
	s := strings.TrimSpace(string(b))
	s = strings.Trim(s, `"`)
	return s
}

// randName returns a random lowercase-alphanumeric string of length n.
func randName(n int) string {
	const chars = "abcdefghijklmnopqrstuvwxyz0123456789"
	b := make([]byte, n)
	for i := range b {
		b[i] = chars[rand.Intn(len(chars))]
	}
	return string(b)
}

// senderFrom extracts a sender from a "from" field that may be a plain
// string or an object like {"address": "...", "name": "..."}.
func senderFrom(b json.RawMessage) string {
	s := strings.TrimSpace(string(b))
	if s == "" || s == "null" {
		return ""
	}
	if s[0] == '{' {
		var obj struct {
			Address string `json:"address"`
			Name    string `json:"name"`
		}
		if json.Unmarshal(b, &obj) == nil {
			if obj.Address != "" {
				return obj.Address
			}
			return obj.Name
		}
	}
	return strings.Trim(s, `"`)
}
