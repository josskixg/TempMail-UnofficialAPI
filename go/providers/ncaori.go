package providers

import (
	"encoding/json"
	"fmt"
	"math/rand"
	"sync"
	"time"
)

const ncaoriBaseURL = "https://www.nca.my.id"

var ncaoriDomains = []string{"ncaori.my.id", "nca.my.id"}

var ncaoriWords = []string{"swift", "crystal", "storm", "frost", "shadow", "ember", "azure",
	"phantom", "silver", "iron", "crimson", "golden", "neo", "cosmic", "lunar",
	"solar", "dark", "light", "void", "flux"}

var ncaoriWords2 = []string{"core", "leaf", "forge", "wave", "peak", "gate", "pulse",
	"blade", "shard", "drift", "hive", "node", "edge", "beacon", "nova",
	"storm", "cloud", "moon", "star", "wind"}

func ncaoriRandomName() string {
	return fmt.Sprintf("%s_%s", ncaoriWords[rand.Intn(len(ncaoriWords))], ncaoriWords2[rand.Intn(len(ncaoriWords2))])
}

type NcaoriMail struct {
	client  *HttpClient
	msgBody map[string]ncaoriEmailItem
	mu      sync.RWMutex
}

func NewNcaoriMail(config map[string]string) *NcaoriMail {
	proxies := ParseProxies(config["proxies"])
	randomUA := config["randomUa"] == "true"
	return &NcaoriMail{
		client:  NewHttpClient(proxies, randomUA, false),
		msgBody: make(map[string]ncaoriEmailItem),
	}
}

type ncaoriInboxResponse struct {
	Emails []ncaoriEmailItem `json:"emails"`
}

type ncaoriEmailItem struct {
	ID        string `json:"id"`
	Sender    string `json:"sender"`
	Subject   string `json:"subject"`
	BodyText  string `json:"body_text"`
	BodyHTML  string `json:"body_html"`
	CreatedAt string `json:"created_at"`
}

func (n *NcaoriMail) GenerateEmail() (string, error) {
	name := ncaoriRandomName()
	domain := ncaoriDomains[rand.Intn(len(ncaoriDomains))]
	return name + "@" + domain, nil
}

func (n *NcaoriMail) GetInbox(email string) ([]Message, error) {
	body, err := n.client.Get(ncaoriBaseURL + "/api/emails?recipient=" + email)
	if err != nil {
		return nil, &TempMailError{Provider: "ncaori", Message: err.Error()}
	}

	var resp ncaoriInboxResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, &TempMailError{Provider: "ncaori", Message: "parse error: " + err.Error()}
	}

	msgs := make([]Message, len(resp.Emails))
	n.mu.Lock()
	for i, m := range resp.Emails {
		ts := time.Now()
		if m.CreatedAt != "" {
			parsed, err := time.Parse(time.RFC3339, m.CreatedAt)
			if err == nil {
				ts = parsed
			}
		}
		msgs[i] = Message{
			ID:      m.ID,
			Sender:  m.Sender,
			Subject: m.Subject,
			Date:    ts,
		}
		n.msgBody[m.ID] = m
	}
	n.mu.Unlock()
	return msgs, nil
}

func (n *NcaoriMail) ReadMessage(messageID string) (*MessageDetail, error) {
	n.mu.RLock()
	m, ok := n.msgBody[messageID]
	n.mu.RUnlock()
	if !ok {
		return nil, &NotFoundError{Provider: "ncaori", Resource: messageID}
	}
	ts := time.Now()
	if m.CreatedAt != "" {
		parsed, err := time.Parse(time.RFC3339, m.CreatedAt)
		if err == nil {
			ts = parsed
		}
	}
	return &MessageDetail{
		Message: Message{
			ID:      m.ID,
			Sender:  m.Sender,
			Subject: m.Subject,
			Date:    ts,
		},
		BodyText: m.BodyText,
		BodyHTML: m.BodyHTML,
	}, nil
}

func (n *NcaoriMail) DeleteEmail(email string) (bool, error) {
	return true, nil
}

func (n *NcaoriMail) WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	return waitForEmail(n, email, timeout, interval)
}
