package providers

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/url"
	"time"
)

const tempmailLolBaseURL = "https://api.tempmail.lol/v2"

// TempmailLol is the tempmail.lol provider (REST API, token-based).
type TempmailLol struct {
	client *HttpClient
	token  string
	email  string
}

// NewTempmailLol creates a new TempmailLol provider.
func NewTempmailLol(config map[string]string) *TempmailLol {
	proxies := ParseProxies(config["proxies"])
	randomUA := config["randomUa"] == "true"
	return &TempmailLol{client: NewHttpClient(proxies, randomUA, false)}
}

func (t *TempmailLol) GenerateEmail() (string, error) {
	body, err := t.client.Post(tempmailLolBaseURL+"/inbox/create", "application/json", bytes.NewReader(nil))
	if err != nil {
		return "", &TempMailError{Provider: "tempmail.lol", Message: err.Error()}
	}
	var resp struct {
		Address string `json:"address"`
		Token   string `json:"token"`
	}
	if e := json.Unmarshal(body, &resp); e != nil {
		return "", &TempMailError{Provider: "tempmail.lol", Message: "parse error: " + e.Error()}
	}
	if resp.Address == "" || resp.Token == "" {
		return "", &TempMailError{Provider: "tempmail.lol", Message: "missing address or token"}
	}
	t.token = resp.Token
	t.email = resp.Address
	return t.email, nil
}

func (t *TempmailLol) GetInbox(email string) ([]Message, error) {
	items, err := t.fetchInbox()
	if err != nil {
		return nil, err
	}
	msgs := make([]Message, len(items))
	for i, it := range items {
		from := it.From
		if from == "" {
			from = it.Sender
		}
		id := rawString(it.RawID)
		if id == "" || id == "null" {
			id = rawString(it.ID)
		}
		if id == "" || id == "null" {
			id = rawString(it.UID)
		}
		msgs[i] = Message{
			ID:      id,
			Sender:  from,
			Subject: it.Subject,
			Date:    parseLolDate(it.Date),
		}
	}
	return msgs, nil
}

func (t *TempmailLol) ReadMessage(messageID string) (*MessageDetail, error) {
	// No separate read endpoint; the inbox already contains full bodies.
	items, err := t.fetchInbox()
	if err != nil {
		return nil, err
	}
	for _, it := range items {
		id := rawString(it.RawID)
		if id == "" || id == "null" {
			id = rawString(it.ID)
		}
		if id == "" || id == "null" {
			id = rawString(it.UID)
		}
		if id == messageID {
			from := it.From
			if from == "" {
				from = it.Sender
			}
			text := it.Body
			if text == "" {
				text = it.Text
			}
			return &MessageDetail{
				Message: Message{
					ID:      messageID,
					Sender:  from,
					Subject: it.Subject,
					Date:    parseLolDate(it.Date),
				},
				BodyText: text,
				BodyHTML: it.HTML,
			}, nil
		}
	}
	return nil, &NotFoundError{Provider: "tempmail.lol", Resource: "message " + messageID}
}

func (t *TempmailLol) DeleteEmail(email string) (bool, error) {
	t.token = ""
	t.email = ""
	return true, nil
}

func (t *TempmailLol) WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	return waitForEmail(t, email, timeout, interval)
}

func (t *TempmailLol) fetchInbox() ([]tempmailLolEmail, error) {
	if t.token == "" {
		return nil, &TempMailError{Provider: "tempmail.lol", Message: "no token — call GenerateEmail() first"}
	}
	endpoint := fmt.Sprintf("%s/inbox?token=%s", tempmailLolBaseURL, url.QueryEscape(t.token))
	body, err := t.client.Get(endpoint)
	if err != nil {
		return nil, &TempMailError{Provider: "tempmail.lol", Message: err.Error()}
	}
	var resp struct {
		Expired bool              `json:"expired"`
		Emails  []tempmailLolEmail `json:"emails"`
	}
	if e := json.Unmarshal(body, &resp); e != nil {
		return nil, &TempMailError{Provider: "tempmail.lol", Message: "parse error: " + e.Error()}
	}
	if resp.Expired {
		return nil, &TempMailError{Provider: "tempmail.lol", Message: "token expired"}
	}
	return resp.Emails, nil
}

type tempmailLolEmail struct {
	RawID   json.RawMessage `json:"_id"`
	ID      json.RawMessage `json:"id"`
	UID     json.RawMessage `json:"uid"`
	From    string          `json:"from"`
	Sender  string          `json:"sender"`
	Subject string          `json:"subject"`
	Date    json.RawMessage `json:"date"`
	Body    string          `json:"body"`
	Text    string          `json:"text"`
	HTML    string          `json:"html"`
}

func parseLolDate(raw json.RawMessage) time.Time {
	if len(raw) == 0 {
		return time.Now()
	}
	var num float64
	if err := json.Unmarshal(raw, &num); err == nil {
		// If it's a timestamp (seconds or milliseconds)
		if num > 1e11 { // Milliseconds
			return time.Unix(int64(num)/1000, 0)
		}
		return time.Unix(int64(num), 0)
	}
	var str string
	if err := json.Unmarshal(raw, &str); err == nil {
		return parseMailDate(str)
	}
	return time.Now()
}
