package providers

import (
	"regexp"
	"strconv"
	"strings"
	"time"
)

const tenMinuteMailBaseURL = "https://10minutemail.net"

// TenMinuteMail is the 10minutemail.net provider (HTML scraping public mailbox).
type TenMinuteMail struct {
	client *HttpClient
	email  string
}

// NewTenMinuteMail creates a new TenMinuteMail provider.
func NewTenMinuteMail(config map[string]string) *TenMinuteMail {
	proxies := ParseProxies(config["proxies"])
	randomUA := config["randomUa"] == "true"
	return &TenMinuteMail{client: NewHttpClient(proxies, randomUA, true)}
}

// Decode Cloudflare obfuscated email address
func decodeCfEmail(hex string) string {
	if len(hex) < 4 {
		return ""
	}
	k, err := strconv.ParseInt(hex[0:2], 16, 64)
	if err != nil {
		return ""
	}
	var builder strings.Builder
	for i := 2; i < len(hex); i += 2 {
		val, err := strconv.ParseInt(hex[i:i+2], 16, 64)
		if err != nil {
			continue
		}
		builder.WriteByte(byte(val ^ k))
	}
	return builder.String()
}

func (t *TenMinuteMail) GenerateEmail() (string, error) {
	body, err := t.client.Get(tenMinuteMailBaseURL + "/")
	if err != nil {
		return "", &TempMailError{Provider: "10minutemail", Message: err.Error()}
	}
	html := string(body)
	re := regexp.MustCompile(`(?i)id="fe_text"[^>]*value="([^"]+)"`)
	match := re.FindStringSubmatch(html)
	if len(match) < 2 {
		return "", &TempMailError{Provider: "10minutemail", Message: "no address in response"}
	}
	t.email = strings.TrimSpace(match[1])
	return t.email, nil
}

func (t *TenMinuteMail) GetInbox(email string) ([]Message, error) {
	body, err := t.client.Get(tenMinuteMailBaseURL + "/mailbox.ajax.php")
	if err != nil {
		return nil, &TempMailError{Provider: "10minutemail", Message: err.Error()}
	}
	html := string(body)
	
	rowRe := regexp.MustCompile(`(?is)<tr[^>]*>(.*?)</tr>`)
	cellRe := regexp.MustCompile(`(?is)<td[^>]*>(.*?)</td>`)
	cfRe := regexp.MustCompile(`(?i)data-cfemail="([^"]+)"`)
	titleRe := regexp.MustCompile(`(?i)title="([^"]+)"`)
	midRe := regexp.MustCompile(`(?i)mid=([^'&"\s>]+)`)

	rows := rowRe.FindAllStringSubmatch(html, -1)
	if len(rows) <= 1 {
		return []Message{}, nil
	}

	var msgs []Message
	// Skip header row
	for i := 1; i < len(rows); i++ {
		rowHtml := rows[i][1]
		cells := cellRe.FindAllStringSubmatch(rowHtml, -1)
		if len(cells) < 3 {
			continue
		}

		// Sender
		sender := ""
		cfMatch := cfRe.FindStringSubmatch(cells[0][1])
		if len(cfMatch) >= 2 {
			sender = decodeCfEmail(cfMatch[1])
		} else {
			sender = StripHTML(cells[0][1])
		}

		subject := StripHTML(cells[1][1])

		// Date
		dateStr := ""
		dateMatch := titleRe.FindStringSubmatch(cells[2][1])
		if len(dateMatch) >= 2 {
			dateStr = dateMatch[1]
		} else {
			dateStr = StripHTML(cells[2][1])
		}
		
		// Parse date (usually in UTC)
		if !strings.Contains(strings.ToLower(dateStr), "utc") {
			dateStr += " UTC"
		}
		parsedDate, err := time.Parse("2006-01-02 15:04:05 MST", dateStr)
		if err != nil {
			parsedDate = time.Now()
		}

		// Message ID
		midMatch := midRe.FindStringSubmatch(rowHtml)
		if len(midMatch) < 2 {
			continue
		}
		id := midMatch[1]

		msgs = append(msgs, Message{
			ID:      id,
			Sender:  sender,
			Subject: subject,
			Date:    parsedDate,
		})
	}

	return msgs, nil
}

func (t *TenMinuteMail) ReadMessage(messageID string) (*MessageDetail, error) {
	// composite messageID handling
	mid := messageID
	if idx := strings.Index(messageID, ":"); idx >= 0 {
		mid = messageID[:idx]
	}

	body, err := t.client.Get(tenMinuteMailBaseURL + "/readmail.html?mid=" + mid)
	if err != nil {
		return nil, &TempMailError{Provider: "10minutemail", Message: err.Error()}
	}
	html := string(body)

	bodyRe := regexp.MustCompile(`(?is)class="mailinhtml"[^>]*>(.*?)<div[^>]*style="clear:both;"`)
	bodyMatch := bodyRe.FindStringSubmatch(html)
	if len(bodyMatch) < 2 {
		return nil, &NotFoundError{Provider: "10minutemail", Resource: "message " + messageID}
	}

	bodyHtml := strings.TrimSpace(bodyMatch[1])

	// Decode Cloudflare obfuscated email links inside body
	cfLinkRe := regexp.MustCompile(`(?i)<(?:a|span)[^>]*class="__cf_email__"[^>]*data-cfemail="([^"]+)"[^>]*>.*?<\/(?:a|span)>`)
	bodyHtml = cfLinkRe.ReplaceAllStringFunc(bodyHtml, func(m string) string {
		cfMatch := cfLinkRe.FindStringSubmatch(m)
		if len(cfMatch) >= 2 {
			return decodeCfEmail(cfMatch[1])
		}
		return m
	})

	cfProtectionRe := regexp.MustCompile(`(?i)href="/cdn-cgi/l/email-protection#([^"]+)"`)
	bodyHtml = cfProtectionRe.ReplaceAllStringFunc(bodyHtml, func(m string) string {
		cfMatch := cfProtectionRe.FindStringSubmatch(m)
		if len(cfMatch) >= 2 {
			return `href="mailto:` + decodeCfEmail(cfMatch[1]) + `"`
		}
		return m
	})

	bodyText := StripHTML(bodyHtml)

	// Extract Subject
	subjectRe := regexp.MustCompile(`(?is)<div class="mail_header">.*?<h2[^>]*>(.*?)</h2>`)
	subjectMatch := subjectRe.FindStringSubmatch(html)
	subject := ""
	if len(subjectMatch) >= 2 {
		subject = StripHTML(subjectMatch[1])
	}

	// Extract Sender
	fromRe := regexp.MustCompile(`(?is)<span class="mail_from">(.*?)</span>`)
	fromMatch := fromRe.FindStringSubmatch(html)
	sender := ""
	if len(fromMatch) >= 2 {
		cfRe := regexp.MustCompile(`(?i)data-cfemail="([^"]+)"`)
		cfFrom := cfRe.FindStringSubmatch(fromMatch[1])
		if len(cfFrom) >= 2 {
			sender = decodeCfEmail(cfFrom[1])
		} else {
			sender = StripHTML(fromMatch[1])
		}
	}

	return &MessageDetail{
		Message: Message{
			ID:      mid,
			Sender:  sender,
			Subject: subject,
			Date:    time.Now(),
		},
		BodyText: bodyText,
		BodyHTML: bodyHtml,
	}, nil
}

func (t *TenMinuteMail) DeleteEmail(email string) (bool, error) {
	t.email = ""
	return true, nil
}

func (t *TenMinuteMail) WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	return waitForEmail(t, email, timeout, interval)
}
