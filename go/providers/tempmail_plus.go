package providers

import (
	"encoding/json"
	"fmt"
	"net/url"
	"time"
)

const tempmailPlusBaseURL = "https://tempmail.plus"

// TempmailPlus is the tempmail.plus provider (REST API, no auth).
type TempmailPlus struct {
	client *HttpClient
	email  string
}

// NewTempmailPlus creates a new TempmailPlus provider.
func NewTempmailPlus(config map[string]string) *TempmailPlus {
	proxies := ParseProxies(config["proxies"])
	randomUA := config["randomUa"] == "true"
	return &TempmailPlus{client: NewHttpClient(proxies, randomUA, true)}
}

func (t *TempmailPlus) GenerateEmail() (string, error) {
	t.email = randName(10) + "@mailto.plus"
	return t.email, nil
}

func (t *TempmailPlus) GetInbox(email string) ([]Message, error) {
	endpoint := fmt.Sprintf("%s/api/mails?email=%s", tempmailPlusBaseURL, url.QueryEscape(email))
	body, err := t.client.Get(endpoint)
	if err != nil {
		return nil, &TempMailError{Provider: "tempmail.plus", Message: err.Error()}
	}
	var resp struct {
		// result is optional; only an explicit false means error (matches Python default True).
		Result   *bool `json:"result"`
		MailList []struct {
			MailID   json.RawMessage `json:"mail_id"`
			FromMail string          `json:"from_mail"`
			Subject  string          `json:"subject"`
			Time     string          `json:"time"`
		} `json:"mail_list"`
	}
	if e := json.Unmarshal(body, &resp); e != nil {
		return nil, &TempMailError{Provider: "tempmail.plus", Message: "parse error: " + e.Error()}
	}
	if resp.Result != nil && !*resp.Result {
		return nil, &TempMailError{Provider: "tempmail.plus", Message: "API returned error"}
	}
	msgs := make([]Message, len(resp.MailList))
	for i, it := range resp.MailList {
		msgs[i] = Message{
			ID:      rawString(it.MailID),
			Sender:  it.FromMail,
			Subject: it.Subject,
			Date:    parseMailDate(it.Time),
		}
	}
	return msgs, nil
}

func (t *TempmailPlus) ReadMessage(messageID string) (*MessageDetail, error) {
	if t.email == "" {
		return nil, &TempMailError{Provider: "tempmail.plus", Message: "no email — call GenerateEmail() first"}
	}
	endpoint := fmt.Sprintf("%s/api/mails/%s?email=%s",
		tempmailPlusBaseURL, url.QueryEscape(messageID), url.QueryEscape(t.email))
	body, err := t.client.Get(endpoint)
	if err != nil {
		return nil, &TempMailError{Provider: "tempmail.plus", Message: err.Error()}
	}
	var m struct {
		MailID   json.RawMessage `json:"mail_id"`
		FromMail string          `json:"from_mail"`
		From     string          `json:"from"`
		Subject  string          `json:"subject"`
		Text     string          `json:"text"`
		HTML     string          `json:"html"`
		Date     string          `json:"date"`
		Attachments []struct {
			Filename    string `json:"filename"`
			ContentType string `json:"content_type"`
			Size        int    `json:"size"`
		} `json:"attachments"`
	}
	if e := json.Unmarshal(body, &m); e != nil {
		return nil, &TempMailError{Provider: "tempmail.plus", Message: "parse error: " + e.Error()}
	}
	from := m.FromMail
	if from == "" {
		from = m.From
	}
	id := rawString(m.MailID)
	if id == "" {
		id = messageID
	}
	detail := &MessageDetail{
		Message: Message{
			ID:      id,
			Sender:  from,
			Subject: m.Subject,
			Date:    parseMailDate(m.Date),
		},
		BodyText: m.Text,
		BodyHTML: m.HTML,
	}
	for _, a := range m.Attachments {
		detail.Attachments = append(detail.Attachments, map[string]any{
			"filename":     a.Filename,
			"content_type": a.ContentType,
			"size":         a.Size,
		})
	}
	return detail, nil
}

func (t *TempmailPlus) DeleteEmail(email string) (bool, error) {
	t.email = ""
	return true, nil
}

func (t *TempmailPlus) WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	return waitForEmail(t, email, timeout, interval)
}
