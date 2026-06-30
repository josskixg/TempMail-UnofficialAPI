package providers

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"
)

// YOPmail uses web scraping to interact with yopmail.com.
type YOPmail struct {
	client *HttpClient
}

func NewYOPmail(config map[string]string) *YOPmail {
	proxies := ParseProxies(config["proxies"])
	randomUA := config["randomUa"] == "true"
	return &YOPmail{
		client: NewHttpClient(proxies, randomUA, true), // cookie session critical for yopmail
	}
}

func (y *YOPmail) GenerateEmail() (string, error) {
	b := make([]byte, 5)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return fmt.Sprintf("%s@yopmail.com", hex.EncodeToString(b)), nil
}

type yopmailSession struct {
	user string
	yp   string
	yj   string
	v    string
}

func (y *YOPmail) initSession(email string) (*yopmailSession, error) {
	user := strings.SplitN(email, "@", 2)[0]
	sess := &yopmailSession{user: user}

	// 1. GET https://yopmail.com/en/
	html1, err := y.client.Get("https://yopmail.com/en/")
	if err != nil {
		return nil, err
	}

	ypRe := regexp.MustCompile(`name="yp" id="yp" value="([^"]+)"`)
	if m := ypRe.FindSubmatch(html1); len(m) > 1 {
		sess.yp = string(m[1])
	}
	vRe := regexp.MustCompile(`/ver/([0-9.]+)/webmail.js`)
	if m := vRe.FindSubmatch(html1); len(m) > 1 {
		sess.v = string(m[1])
	}

	// 2. GET https://yopmail.com/en/?login={username}
	html2, err := y.client.Get(fmt.Sprintf("https://yopmail.com/en/?login=%s", user))
	if err != nil {
		return nil, err
	}

	if m := ypRe.FindSubmatch(html2); len(m) > 1 {
		sess.yp = string(m[1])
	}

	// 3. POST https://yopmail.com/en/
	form := url.Values{}
	form.Set("login", user)
	form.Set("id", "")
	form.Set("yp", sess.yp)
	_, err = y.client.Post("https://yopmail.com/en/", "application/x-www-form-urlencoded", strings.NewReader(form.Encode()))
	if err != nil {
		return nil, err
	}

	// 4. GET https://yopmail.com/ver/{v}/webmail.js to extract yj token
	if sess.v != "" {
		js, err := y.client.Get(fmt.Sprintf("https://yopmail.com/ver/%s/webmail.js", sess.v))
		if err != nil {
			return nil, err
		}

		// New format (2025+): yj hardcoded as &yj=XXXX in JS string
		yjRe := regexp.MustCompile(`[&]yj=([0-9a-zA-Z]+)`)
		if m := yjRe.FindSubmatch(js); len(m) > 1 {
			sess.yj = string(m[1])
		}
		// Old format: value+'&yj=XXXX&v=
		if sess.yj == "" {
			yjReOld := regexp.MustCompile(`value\+'\\&yj=([0-9a-zA-Z]*)\\&v=`)
			if m := yjReOld.FindSubmatch(js); len(m) > 1 {
				sess.yj = string(m[1])
			}
		}
	}

	return sess, nil
}

func (y *YOPmail) setYTimeCookie(req *http.Request) {
	now := time.Now().UTC()
	req.AddCookie(&http.Cookie{
		Name:  "ytime",
		Value: fmt.Sprintf("%02d:%02d", now.Hour(), now.Minute()),
	})
}

func (y *YOPmail) GetInbox(email string) ([]Message, error) {
	sess, err := y.initSession(email)
	if err != nil {
		return nil, &TempMailError{Provider: "yopmail", Message: err.Error()}
	}

	inboxURL := fmt.Sprintf("https://yopmail.com/en/inbox?login=%s&p=1&d=&ctrl=&yp=%s&yj=%s&v=%s&r_c=&id=&ad=0",
		sess.user, url.QueryEscape(sess.yp), url.QueryEscape(sess.yj), url.QueryEscape(sess.v))

	req, _ := http.NewRequest(http.MethodGet, inboxURL, nil)
	y.setYTimeCookie(req)
	htmlBody, err := y.client.RawDo(req, nil)
	if err != nil {
		return nil, &TempMailError{Provider: "yopmail", Message: err.Error()}
	}

	var msgs []Message
	blocks := strings.Split(string(htmlBody), `<div class="m`)
	idRe := regexp.MustCompile(`id="(m[^"]+)"`)
	senderRe := regexp.MustCompile(`class="lmf"[^>]*>(.*?)</span>`)
	subjectRe := regexp.MustCompile(`class="lms"[^>]*>(.*?)</span>`)
	tagRe := regexp.MustCompile(`<[^>]+>`)

	for _, block := range blocks[1:] {
		msg := Message{Date: time.Now().UTC()}
		if m := idRe.FindStringSubmatch(block); len(m) > 1 {
			msg.ID = fmt.Sprintf("%s:%s", sess.user, m[1])
		} else {
			continue
		}
		if m := senderRe.FindStringSubmatch(block); len(m) > 1 {
			msg.Sender = cleanAngleBrackets(strings.TrimSpace(tagRe.ReplaceAllString(m[1], "")))
		}
		if m := subjectRe.FindStringSubmatch(block); len(m) > 1 {
			msg.Subject = strings.TrimSpace(tagRe.ReplaceAllString(m[1], ""))
		}
		msgs = append(msgs, msg)
	}

	return msgs, nil
}

func (y *YOPmail) ReadMessage(messageID string) (*MessageDetail, error) {
	parts := strings.SplitN(messageID, ":", 2)
	if len(parts) != 2 {
		return nil, &TempMailError{Provider: "yopmail", Message: "invalid message ID"}
	}
	user, mailID := parts[0], parts[1]

	sess, err := y.initSession(fmt.Sprintf("%s@yopmail.com", user))
	if err != nil {
		return nil, &TempMailError{Provider: "yopmail", Message: err.Error()}
	}

	mailURL := fmt.Sprintf("https://yopmail.com/en/mail?b=%s&id=%s&yp=%s&yj=%s&v=%s",
		user, mailID, url.QueryEscape(sess.yp), url.QueryEscape(sess.yj), url.QueryEscape(sess.v))

	req, _ := http.NewRequest(http.MethodGet, mailURL, nil)
	y.setYTimeCookie(req)
	htmlBody, err := y.client.RawDo(req, nil)
	if err != nil {
		return nil, &TempMailError{Provider: "yopmail", Message: err.Error()}
	}

	detail := &MessageDetail{
		Message: Message{ID: messageID},
	}

	bodyRe := regexp.MustCompile(`(?s)id="mail"[^>]*>(.*?)</div>`)
	tagRe := regexp.MustCompile(`<[^>]+>`)
	if m := bodyRe.FindSubmatch([]byte(htmlBody)); len(m) > 1 {
		detail.BodyHTML = strings.TrimSpace(string(m[1]))
		detail.BodyText = strings.TrimSpace(tagRe.ReplaceAllString(string(m[1]), ""))
	}

	return detail, nil
}

func (y *YOPmail) DeleteEmail(email string) (bool, error) {
	return true, nil
}

func (y *YOPmail) WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	return waitForEmail(y, email, timeout, interval)
}

// cleanAngleBrackets strips "<" and ">" from sender strings.
func cleanAngleBrackets(s string) string {
	s = strings.TrimSpace(s)
	if idx := strings.Index(s, "<"); idx >= 0 {
		end := strings.Index(s, ">")
		if end > idx {
			return s[idx+1 : end]
		}
	}
	return s
}
