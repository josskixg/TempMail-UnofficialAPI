package providers

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

const mailTMBaseURL = "https://api.mail.tm"

// MailTM uses mail.tm — bearer token auth via account creation.
type MailTM struct {
	client *HttpClient
	token  string
	config map[string]string
}

func NewMailTM(config map[string]string) *MailTM {
	proxies := ParseProxies(config["proxies"])
	randomUA := config["randomUa"] == "true"

	return &MailTM{
		client: NewHttpClient(proxies, randomUA, false),
		config: config,
		token:  config["token"],
	}
}

func (m *MailTM) GenerateEmail() (string, error) {
	password := m.config["password"]
	if password == "" {
		password = "TempPass123!"
	}

	// Get an available domain.
	domBody, err := m.client.Get(mailTMBaseURL + "/domains")
	if err != nil {
		return "", &TempMailError{Provider: "mailtm", Message: err.Error()}
	}

	var domains struct {
		HydraMember []struct {
			ID     string `json:"@id"`
			Domain string `json:"domain"`
		} `json:"hydra:member"`
	}
	if err := json.Unmarshal(domBody, &domains); err != nil || len(domains.HydraMember) == 0 {
		return "", &TempMailError{Provider: "mailtm", Message: "no domains available"}
	}
	domain := domains.HydraMember[0].Domain

	// Create random address.
	addr := fmt.Sprintf("tmp_%d@%s", time.Now().UnixNano()%1e9, domain)
	payload := map[string]string{
		"address":  addr,
		"password": password,
	}
	b, _ := json.Marshal(payload)

	_, err = m.client.Post(mailTMBaseURL+"/accounts", "application/json", bytes.NewReader(b))
	if err != nil {
		return "", &TempMailError{Provider: "mailtm", Message: err.Error()}
	}

	// Obtain token.
	loginPayload := map[string]string{
		"address":  addr,
		"password": password,
	}
	lb, _ := json.Marshal(loginPayload)
	tokenBody, err := m.client.Post(mailTMBaseURL+"/token", "application/json", bytes.NewReader(lb))
	if err == nil {
		var tokenData struct {
			Token string `json:"token"`
		}
		json.Unmarshal(tokenBody, &tokenData)
		if tokenData.Token != "" {
			m.token = tokenData.Token
		}
	}

	return addr, nil
}

func (m *MailTM) GetInbox(email string) ([]Message, error) {
	req, _ := http.NewRequest(http.MethodGet, mailTMBaseURL+"/messages", nil)
	req.Header.Set("Authorization", "Bearer "+m.token)
	req.Header.Set("Accept", "application/ld+json")

	body, err := m.client.RawDo(req, nil)
	if err != nil {
		return nil, &TempMailError{Provider: "mailtm", Message: err.Error()}
	}

	var raw struct {
		HydraMember []struct {
			ID   string `json:"@id"`
			From struct {
				Address string `json:"address"`
			} `json:"from"`
			Subject        string `json:"subject"`
			HasAttachments bool   `json:"hasAttachments"`
			CreatedAt      string `json:"createdAt"`
		} `json:"hydra:member"`
	}
	if err := json.Unmarshal(body, &raw); err != nil {
		return nil, &TempMailError{Provider: "mailtm", Message: "parse error: " + err.Error()}
	}

	msgs := make([]Message, len(raw.HydraMember))
	for i, r := range raw.HydraMember {
		t, _ := time.Parse(time.RFC3339, r.CreatedAt)
		idParts := strings.Split(r.ID, "/")
		id := idParts[len(idParts)-1]
		msgs[i] = Message{
			ID:      id,
			Sender:  r.From.Address,
			Subject: r.Subject,
			Date:    t,
		}
	}
	return msgs, nil
}

func (m *MailTM) ReadMessage(messageID string) (*MessageDetail, error) {
	req, _ := http.NewRequest(http.MethodGet, mailTMBaseURL+"/messages/"+messageID, nil)
	req.Header.Set("Authorization", "Bearer "+m.token)
	req.Header.Set("Accept", "application/ld+json")

	body, err := m.client.RawDo(req, nil)
	if err != nil {
		return nil, &TempMailError{Provider: "mailtm", Message: err.Error()}
	}

	var raw struct {
		ID   string `json:"@id"`
		From struct {
			Address string `json:"address"`
		} `json:"from"`
		Subject     string   `json:"subject"`
		Text        string   `json:"text"`
		HTML        []string `json:"html"`
		CreatedAt   string   `json:"createdAt"`
		Attachments []struct {
			Filename string `json:"filename"`
			MIMEType string `json:"mimeType"`
			Size     int    `json:"size"`
		} `json:"attachments"`
	}
	if err := json.Unmarshal(body, &raw); err != nil {
		return nil, &TempMailError{Provider: "mailtm", Message: "parse error: " + err.Error()}
	}

	t, _ := time.Parse(time.RFC3339, raw.CreatedAt)
	idParts := strings.Split(raw.ID, "/")
	id := idParts[len(idParts)-1]
	htmlBody := strings.Join(raw.HTML, "\n")

	detail := &MessageDetail{
		Message: Message{
			ID:      id,
			Sender:  raw.From.Address,
			Subject: raw.Subject,
			Date:    t,
		},
		BodyText: raw.Text,
		BodyHTML: htmlBody,
	}
	for _, a := range raw.Attachments {
		detail.Attachments = append(detail.Attachments, map[string]any{
			"filename":     a.Filename,
			"content_type": a.MIMEType,
			"size":         a.Size,
		})
	}
	return detail, nil
}

func (m *MailTM) DeleteEmail(email string) (bool, error) {
	return false, &TempMailError{Provider: "mailtm", Message: "mailbox deletion not supported, delete individual messages"}
}

func (m *MailTM) WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	return waitForEmail(m, email, timeout, interval)
}
