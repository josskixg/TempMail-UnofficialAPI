package providers

import (
	"encoding/json"
	"fmt"
	"time"
)

const guerrillaBaseURL = "https://api.guerrillamail.com/ajax.php"

// flexString unmarshals from JSON string or number.
// ponytail: GuerrillaMail returns mail_timestamp as both types across endpoints.
type flexString string

func (f *flexString) UnmarshalJSON(b []byte) error {
	if len(b) > 0 && b[0] == '"' {
		var s string
		if err := json.Unmarshal(b, &s); err != nil {
			return err
		}
		*f = flexString(s)
	} else {
		*f = flexString(b)
	}
	return nil
}

// GuerrillaMail uses guerrillamail.com — session token via cookie.
type GuerrillaMail struct {
	client    *HttpClient
	sessionID string
	emailAddr string
}

func NewGuerrillaMail(config map[string]string) *GuerrillaMail {
	proxies := ParseProxies(config["proxies"])
	randomUA := config["randomUa"] == "true"

	gm := &GuerrillaMail{
		client: NewHttpClient(proxies, randomUA, true), // cookies critical for guerrillamail
	}
	if sid, ok := config["session_id"]; ok {
		gm.sessionID = sid
	}
	return gm
}

func (g *GuerrillaMail) doRequest(action string) ([]byte, error) {
	reqURL := guerrillaBaseURL + "?f=" + action
	if g.sessionID != "" {
		reqURL += "&sid_token=" + g.sessionID
	}

	body, err := g.client.Get(reqURL)
	if err != nil {
		return nil, &TempMailError{Provider: "guerrillamail", Message: err.Error()}
	}

	// Also check for sid_token in JSON response.
	var sidCheck struct {
		SIDToken string `json:"sid_token"`
	}
	if json.Unmarshal(body, &sidCheck) == nil && sidCheck.SIDToken != "" {
		g.sessionID = sidCheck.SIDToken
	}

	return body, nil
}

func (g *GuerrillaMail) GenerateEmail() (string, error) {
	body, err := g.doRequest("get_email_address")
	if err != nil {
		return "", err
	}

	var raw struct {
		EmailAddr string `json:"email_addr"`
		Alias     string `json:"alias"`
	}
	if err := json.Unmarshal(body, &raw); err != nil {
		return "", &TempMailError{Provider: "guerrillamail", Message: "parse error: " + err.Error()}
	}
	g.emailAddr = raw.EmailAddr
	return raw.EmailAddr, nil
}

func (g *GuerrillaMail) GetInbox(email string) ([]Message, error) {
	body, err := g.doRequest("get_email_list&offset=0")
	if err != nil {
		return nil, err
	}

	var raw struct {
		List []struct {
			MailID        flexString `json:"mail_id"`
			MailFrom      flexString `json:"mail_from"`
			MailSubject   flexString `json:"mail_subject"`
			MailTimestamp flexString `json:"mail_timestamp"`
		} `json:"list"`
	}
	if err := json.Unmarshal(body, &raw); err != nil {
		return nil, &TempMailError{Provider: "guerrillamail", Message: "parse error: " + err.Error()}
	}

	msgs := make([]Message, len(raw.List))
	for i, r := range raw.List {
		ts := parseUnix(string(r.MailTimestamp))
		msgs[i] = Message{
			ID:      string(r.MailID),
			Sender:  string(r.MailFrom),
			Subject: string(r.MailSubject),
			Date:    ts,
		}
	}
	return msgs, nil
}

func (g *GuerrillaMail) ReadMessage(messageID string) (*MessageDetail, error) {
	body, err := g.doRequest("fetch_email&email_id=" + messageID)
	if err != nil {
		return nil, err
	}

	var raw struct {
		MailID      flexString `json:"mail_id"`
		MailFrom    flexString `json:"mail_from"`
		MailSubject flexString `json:"mail_subject"`
		MailBody    string     `json:"mail_body"`
		MailExcerpt string     `json:"mail_excerpt"`
		MailDate    flexString `json:"mail_timestamp"`
	}
	if err := json.Unmarshal(body, &raw); err != nil {
		return nil, &TempMailError{Provider: "guerrillamail", Message: "parse error: " + err.Error()}
	}

	ts := parseUnix(string(raw.MailDate))
	return &MessageDetail{
		Message: Message{
			ID:      string(raw.MailID),
			Sender:  string(raw.MailFrom),
			Subject: string(raw.MailSubject),
			Date:    ts,
		},
		BodyText: raw.MailExcerpt,
		BodyHTML: raw.MailBody,
	}, nil
}

func (g *GuerrillaMail) DeleteEmail(email string) (bool, error) {
	// GuerrillaMail: forget the session to effectively "delete".
	g.sessionID = ""
	g.emailAddr = ""
	return true, nil
}

func (g *GuerrillaMail) WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	return waitForEmail(g, email, timeout, interval)
}

func parseUnix(s string) time.Time {
	var n int64
	fmt.Sscanf(s, "%d", &n)
	if n == 0 {
		return time.Time{}
	}
	return time.Unix(n, 0)
}
