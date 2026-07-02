package providers

import (
	"encoding/json"
	"fmt"
	"net/url"
	"time"
)

const tempmailcBaseURL = "https://tempmailc.com/api/v1"

// Tempmailc is the tempmailc.com provider (REST API, no auth).
type Tempmailc struct {
	client *HttpClient
	email  string
}

// NewTempmailc creates a new Tempmailc provider.
func NewTempmailc(config map[string]string) *Tempmailc {
	proxies := ParseProxies(config["proxies"])
	randomUA := config["randomUa"] == "true"
	return &Tempmailc{client: NewHttpClient(proxies, randomUA, false)}
}

func (t *Tempmailc) GenerateEmail() (string, error) {
	body, err := t.client.Get(tempmailcBaseURL + "/new")
	if err != nil {
		return "", &TempMailError{Provider: "tempmailc", Message: err.Error()}
	}
	var resp struct {
		OK    bool   `json:"ok"`
		Email string `json:"email"`
	}
	if e := json.Unmarshal(body, &resp); e != nil {
		return "", &TempMailError{Provider: "tempmailc", Message: "parse error: " + e.Error()}
	}
	if !resp.OK || resp.Email == "" {
		return "", &TempMailError{Provider: "tempmailc", Message: "API returned not ok"}
	}
	t.email = resp.Email
	return t.email, nil
}

func (t *Tempmailc) GetInbox(email string) ([]Message, error) {
	endpoint := fmt.Sprintf("%s/inbox?email=%s", tempmailcBaseURL, url.QueryEscape(email))
	body, err := t.client.Get(endpoint)
	if err != nil {
		return nil, &TempMailError{Provider: "tempmailc", Message: err.Error()}
	}
	var resp struct {
		Messages []struct {
			ID       json.RawMessage `json:"id"`
			MsgID    json.RawMessage `json:"msg_id"`
			From     string          `json:"from"`
			FromMail string          `json:"from_mail"`
			Subject  string          `json:"subject"`
			Date     string          `json:"date"`
			Time     string          `json:"time"`
		} `json:"messages"`
	}
	if e := json.Unmarshal(body, &resp); e != nil {
		return nil, &TempMailError{Provider: "tempmailc", Message: "parse error: " + e.Error()}
	}
	msgs := make([]Message, len(resp.Messages))
	for i, it := range resp.Messages {
		id := rawString(it.ID)
		if id == "" {
			id = rawString(it.MsgID)
		}
		from := it.From
		if from == "" {
			from = it.FromMail
		}
		date := it.Date
		if date == "" {
			date = it.Time
		}
		msgs[i] = Message{ID: id, Sender: from, Subject: it.Subject, Date: parseMailDate(date)}
	}
	return msgs, nil
}

func (t *Tempmailc) ReadMessage(messageID string) (*MessageDetail, error) {
	if t.email == "" {
		return nil, &TempMailError{Provider: "tempmailc", Message: "no email — call GenerateEmail() first"}
	}
	endpoint := fmt.Sprintf("%s/message?msg_id=%s&email=%s",
		tempmailcBaseURL, url.QueryEscape(messageID), url.QueryEscape(t.email))
	body, err := t.client.Get(endpoint)
	if err != nil {
		return nil, &TempMailError{Provider: "tempmailc", Message: err.Error()}
	}
	var m struct {
		ID       json.RawMessage `json:"id"`
		From     string          `json:"from"`
		FromMail string          `json:"from_mail"`
		Subject  string          `json:"subject"`
		Date     string          `json:"date"`
		Time     string          `json:"time"`
		Text     string          `json:"text"`
		BodyText string          `json:"body_text"`
		HTML     string          `json:"html"`
		BodyHTML string          `json:"body_html"`
	}
	if e := json.Unmarshal(body, &m); e != nil {
		return nil, &TempMailError{Provider: "tempmailc", Message: "parse error: " + e.Error()}
	}
	from := m.From
	if from == "" {
		from = m.FromMail
	}
	date := m.Date
	if date == "" {
		date = m.Time
	}
	text := m.Text
	if text == "" {
		text = m.BodyText
	}
	html := m.HTML
	if html == "" {
		html = m.BodyHTML
	}
	id := rawString(m.ID)
	if id == "" {
		id = messageID
	}
	return &MessageDetail{
		Message: Message{
			ID:      id,
			Sender:  from,
			Subject: m.Subject,
			Date:    parseMailDate(date),
		},
		BodyText: text,
		BodyHTML: html,
	}, nil
}

func (t *Tempmailc) DeleteEmail(email string) (bool, error) {
	t.email = ""
	return true, nil
}

func (t *Tempmailc) WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	return waitForEmail(t, email, timeout, interval)
}
