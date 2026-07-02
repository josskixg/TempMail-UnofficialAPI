package providers

import (
	"fmt"
	"math/rand"
	"regexp"
	"strings"
	"time"
)

const mailnesiaBaseURL = "https://mailnesia.com"

// Mailnesia is the mailnesia.com provider (public mailbox, HTML scraping with IP rotation).
type Mailnesia struct {
	client   *HttpClient
	email    string
	username string
}

// NewMailnesia creates a new Mailnesia provider.
func NewMailnesia(config map[string]string) *Mailnesia {
	proxies := ParseProxies(config["proxies"])
	randomUA := config["randomUa"] == "true"
	return &Mailnesia{client: NewHttpClient(proxies, randomUA, true)}
}

func (m *Mailnesia) generateRandomIP() string {
	return fmt.Sprintf("%d.%d.%d.%d",
		rand.Intn(254)+1,
		rand.Intn(256),
		rand.Intn(256),
		rand.Intn(254)+1)
}

func (m *Mailnesia) getHeadersWithIPRotation() map[string]string {
	ip := m.generateRandomIP()
	return map[string]string{
		"X-Forwarded-For":  ip,
		"X-Real-IP":        ip,
		"CF-Connecting-IP": ip,
		"True-Client-IP":   ip,
	}
}

func (m *Mailnesia) GenerateEmail() (string, error) {
	m.username = randName(10)
	m.email = m.username + "@mailnesia.com"
	return m.email, nil
}

func (m *Mailnesia) GetInbox(email string) ([]Message, error) {
	username := email
	if i := strings.Index(email, "@"); i > 0 {
		username = email[:i]
	}
	headers := m.getHeadersWithIPRotation()
	body, err := m.client.GetWithHeaders(mailnesiaBaseURL+"/mailbox/"+username, headers)
	if err != nil {
		return nil, &TempMailError{Provider: "mailnesia", Message: err.Error()}
	}
	html := string(body)
	var msgs []Message
	for _, row := range mailnesiaRowRe.FindAllStringSubmatch(html, -1) {
		cells := mailnesiaCellRe.FindAllStringSubmatch(row[1], -1)
		if len(cells) < 3 {
			continue
		}
		sender := stripHTML(cells[0][1])
		subject := stripHTML(cells[1][1])
		timeStr := stripHTML(cells[2][1])
		if sender == "" && subject == "" {
			continue
		}
		msgID := ""
		if lm := mailnesiaLinkRe.FindStringSubmatch(row[1]); len(lm) > 1 {
			msgID = lastPathSegment(lm[1])
		}
		msgs = append(msgs, Message{ID: msgID, Sender: sender, Subject: subject, Date: parseMailDate(timeStr)})
	}
	return msgs, nil
}

func (m *Mailnesia) ReadMessage(messageID string) (*MessageDetail, error) {
	if m.username == "" {
		return nil, &TempMailError{Provider: "mailnesia", Message: "no email — call GenerateEmail() first"}
	}
	headers := m.getHeadersWithIPRotation()
	body, err := m.client.GetWithHeaders(mailnesiaBaseURL+"/mailbox/"+m.username+"/"+messageID, headers)
	if err != nil {
		return nil, &TempMailError{Provider: "mailnesia", Message: err.Error()}
	}
	html := string(body)
	bodyHTML := extractDivContent(html, "message")
	bodyText := stripHTML(bodyHTML)
	if bodyHTML == "" {
		bodyText = stripHTML(html)
	}
	return &MessageDetail{
		Message:  Message{ID: messageID, Date: time.Now()},
		BodyText: bodyText,
		BodyHTML: bodyHTML,
	}, nil
}

func (m *Mailnesia) DeleteEmail(email string) (bool, error) {
	m.email = ""
	m.username = ""
	return true, nil
}

func (m *Mailnesia) WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	return waitForEmail(m, email, timeout, interval)
}

var (
	mailnesiaRowRe  = regexp.MustCompile(`(?s)<tr[^>]*>(.*?)</tr>`)
	mailnesiaCellRe = regexp.MustCompile(`(?s)<td[^>]*>(.*?)</td>`)
	mailnesiaLinkRe = regexp.MustCompile(`href="([^"]*)"`)
)
