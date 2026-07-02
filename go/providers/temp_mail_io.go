package providers

import (
	"bytes"
	"encoding/json"
	"net/http"
	"time"
)

const tempMailIoBaseURL = "https://api.internal.temp-mail.io/api/v3"

// TempMailIo is the temp-mail.io provider (REST API, Bearer token).
type TempMailIo struct {
	client *HttpClient
	token  string
	email  string
}

// NewTempMailIo creates a new TempMailIo provider.
func NewTempMailIo(config map[string]string) *TempMailIo {
	proxies := ParseProxies(config["proxies"])
	randomUA := config["randomUa"] == "true"
	return &TempMailIo{client: NewHttpClient(proxies, randomUA, false)}
}

// authReq builds a request with the Bearer token and routes it through HttpClient.
func (t *TempMailIo) authReq(method, path string, body []byte) ([]byte, error) {
	req, _ := http.NewRequest(method, tempMailIoBaseURL+path, bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	if t.token != "" {
		req.Header.Set("Authorization", "Bearer "+t.token)
	}
	return t.client.RawDo(req, body)
}

func (t *TempMailIo) GenerateEmail() (string, error) {
	payload, _ := json.Marshal(map[string]int{"min_name_length": 6, "max_name_length": 12})
	body, err := t.authReq(http.MethodPost, "/email/new", payload)
	if err != nil {
		return "", &TempMailError{Provider: "temp-mail.io", Message: err.Error()}
	}
	var resp struct {
		Email string `json:"email"`
		Token string `json:"token"`
	}
	if e := json.Unmarshal(body, &resp); e != nil || resp.Email == "" {
		return "", &TempMailError{Provider: "temp-mail.io", Message: "no email in response"}
	}
	t.email = resp.Email
	t.token = resp.Token
	return t.email, nil
}

func (t *TempMailIo) GetInbox(email string) ([]Message, error) {
	items, err := t.fetchMessages()
	if err != nil {
		return nil, err
	}
	msgs := make([]Message, len(items))
	for i, it := range items {
		id := rawString(it.ID)
		if id == "" {
			id = rawString(it.UID)
		}
		date := it.CreatedAt
		if date == "" {
			date = it.Date
		}
		msgs[i] = Message{ID: id, Sender: senderFrom(it.From), Subject: it.Subject, Date: parseMailDate(date)}
	}
	return msgs, nil
}

func (t *TempMailIo) ReadMessage(messageID string) (*MessageDetail, error) {
	// No dedicated read endpoint; the messages list already carries full bodies.
	items, err := t.fetchMessages()
	if err != nil {
		return nil, err
	}
	for _, it := range items {
		id := rawString(it.ID)
		if id == "" {
			id = rawString(it.UID)
		}
		if id == messageID {
			date := it.CreatedAt
			if date == "" {
				date = it.Date
			}
			text := it.BodyText
			if text == "" {
				text = it.Text
			}
			html := it.BodyHTML
			if html == "" {
				html = it.HTML
			}
			detail := &MessageDetail{
				Message: Message{
					ID:      messageID,
					Sender:  senderFrom(it.From),
					Subject: it.Subject,
					Date:    parseMailDate(date),
				},
				BodyText: text,
				BodyHTML: html,
			}
			for _, a := range it.Attachments {
				detail.Attachments = append(detail.Attachments, map[string]any{
					"filename":     a.Filename,
					"content_type": a.ContentType,
					"size":         a.Size,
				})
			}
			return detail, nil
		}
	}
	return nil, &NotFoundError{Provider: "temp-mail.io", Resource: "message " + messageID}
}

func (t *TempMailIo) DeleteEmail(email string) (bool, error) {
	t.token = ""
	t.email = ""
	return true, nil
}

func (t *TempMailIo) WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	return waitForEmail(t, email, timeout, interval)
}

// fetchMessages returns the raw message list; the response is either a JSON
// array or an object wrapping it under "messages".
func (t *TempMailIo) fetchMessages() ([]tempMailIoItem, error) {
	if t.email == "" {
		return nil, &TempMailError{Provider: "temp-mail.io", Message: "no email — call GenerateEmail() first"}
	}
	body, err := t.authReq(http.MethodGet, "/email/"+t.email+"/messages", nil)
	if err != nil {
		return nil, &TempMailError{Provider: "temp-mail.io", Message: err.Error()}
	}
	var items []tempMailIoItem
	if e := json.Unmarshal(body, &items); e != nil {
		var wrap struct {
			Messages []tempMailIoItem `json:"messages"`
		}
		if e2 := json.Unmarshal(body, &wrap); e2 == nil {
			return wrap.Messages, nil
		}
		return nil, &TempMailError{Provider: "temp-mail.io", Message: "parse error: " + e.Error()}
	}
	return items, nil
}

type tempMailIoItem struct {
	ID          json.RawMessage `json:"id"`
	UID         json.RawMessage `json:"uid"`
	From        json.RawMessage `json:"from"`
	Subject     string          `json:"subject"`
	CreatedAt   string          `json:"created_at"`
	Date        string          `json:"date"`
	BodyText    string          `json:"body_text"`
	Text        string          `json:"text"`
	BodyHTML    string          `json:"body_html"`
	HTML        string          `json:"html"`
	Attachments []struct {
		Filename    string `json:"filename"`
		ContentType string `json:"content_type"`
		Size        int    `json:"size"`
	} `json:"attachments"`
}
