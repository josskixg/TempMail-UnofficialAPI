package providers

import (
	"fmt"
	"math/rand"
	"net/http"
	"regexp"
	"strings"
	"time"
)

// gasmurlBackend is the shared scraping backend for emailfake.com,
// generator.email, and email-temp.com — all run the same "gasmurl" software
// and differ only in base URL and cookie domain. Each provider is a thin
// embedding wrapper (see emailfake.go / generator_email.go / email_temp.go).
type gasmurlBackend struct {
	client       *HttpClient
	name         string
	baseURL      string
	cookieDomain string
	domain       string
	username     string
	email        string
}

func newGasmurlBackend(config map[string]string, name, baseURL, cookieDomain string) *gasmurlBackend {
	proxies := ParseProxies(config["proxies"])
	randomUA := config["randomUa"] == "true"
	return &gasmurlBackend{
		client:       NewHttpClient(proxies, randomUA, true),
		name:         name,
		baseURL:      baseURL,
		cookieDomain: cookieDomain,
	}
}

func (g *gasmurlBackend) GenerateEmail() (string, error) {
	domains, err := g.getDomains()
	if err != nil {
		return "", err
	}
	g.domain = domains[rand.Intn(len(domains))]
	g.username = randName(10)
	g.email = g.username + "@" + g.domain
	// surl cookie routes the mailbox: surl={domain}/{username}
	g.client.SetCookie(g.baseURL, &http.Cookie{
		Name:   "surl",
		Value:  g.domain + "/" + g.username,
		Path:   "/",
		Domain: g.cookieDomain,
	})
	return g.email, nil
}

func (g *gasmurlBackend) getDomains() ([]string, error) {
	ch := rand.Intn(9) + 1
	body, err := g.client.Get(fmt.Sprintf("%s/channel%d/", g.baseURL, ch))
	if err != nil {
		return nil, &TempMailError{Provider: g.name, Message: err.Error()}
	}
	html := string(body)
	seen := map[string]bool{}
	var domains []string
	for _, m := range gasmurlOptionRe.FindAllStringSubmatch(html, -1) {
		val := strings.TrimSpace(m[1])
		if strings.Contains(val, ".") && !strings.Contains(val, " ") &&
			!strings.Contains(val, "@") && !seen[val] {
			seen[val] = true
			domains = append(domains, val)
		}
	}
	if len(domains) == 0 {
		// Fallback: look for domain-like strings in text content
		for _, m := range gasmurlDomainTextRe.FindAllStringSubmatch(html, -1) {
			val := strings.TrimSpace(m[1])
			if !seen[val] && !strings.Contains(val, g.name) {
				seen[val] = true
				domains = append(domains, val)
			}
		}
	}
	if len(domains) == 0 {
		return nil, &TempMailError{Provider: g.name, Message: "no domains found on page"}
	}
	return domains, nil
}

func (g *gasmurlBackend) GetInbox(email string) ([]Message, error) {
	if g.domain == "" || g.username == "" {
		return nil, &TempMailError{Provider: g.name, Message: "no email generated — call GenerateEmail() first"}
	}
	ch := rand.Intn(9) + 1
	body, err := g.client.Get(fmt.Sprintf("%s/channel%d/", g.baseURL, ch))
	if err != nil {
		return nil, &TempMailError{Provider: g.name, Message: err.Error()}
	}
	section := extractDivContent(string(body), "email-table")
	if section == "" {
		return nil, nil
	}
	var msgs []Message
	for _, m := range gasmurlAnchorRe.FindAllStringSubmatch(section, -1) {
		attrs := m[1]
		inner := m[2]
		if !strings.Contains(attrs, "list-group-item") {
			continue
		}
		hm := gasmurlHrefRe.FindStringSubmatch(attrs)
		if len(hm) < 2 {
			continue
		}
		msgID := lastPathSegment(hm[1])
		if len(msgID) < 10 {
			continue
		}
		msgs = append(msgs, Message{
			ID:      msgID,
			Sender:  stripHTML(divText(inner, gasmurlFromRe)),
			Subject: stripHTML(divText(inner, gasmurlSubjRe)),
			Date:    parseMailDate(stripHTML(divText(inner, gasmurlTimeRe))),
		})
	}
	return msgs, nil
}

func (g *gasmurlBackend) ReadMessage(messageID string) (*MessageDetail, error) {
	if g.domain == "" || g.username == "" {
		return nil, &TempMailError{Provider: g.name, Message: "no email generated — call GenerateEmail() first"}
	}
	u := fmt.Sprintf("%s/%s/%s/%s", g.baseURL, g.domain, g.username, messageID)
	body, err := g.client.Get(u)
	if err != nil {
		return nil, &TempMailError{Provider: g.name, Message: err.Error()}
	}
	html := string(body)
	bodyHTML := extractDivContent(html, "message")
	bodyText := stripHTML(bodyHTML)
	if bodyHTML == "" {
		bodyText = stripHTML(html)
	}
	return &MessageDetail{
		Message: Message{
			ID:      messageID,
			Sender:  stripHTML(divText(html, gasmurlFromDivRe)),
			Subject: stripHTML(divText(html, gasmurlSubjDivRe)),
			Date:    parseMailDate(stripHTML(divText(html, gasmurlTimeDivRe))),
		},
		BodyText: bodyText,
		BodyHTML: bodyHTML,
	}, nil
}

func (g *gasmurlBackend) DeleteEmail(email string) (bool, error) {
	g.email = ""
	g.domain = ""
	g.username = ""
	return true, nil
}

func (g *gasmurlBackend) WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	return waitForEmail(g, email, timeout, interval)
}

// --- shared HTML helpers (regexp-based; consistent with yopmail.go/onesecemail.go) ---

var (
	gasmurlOptionRe     = regexp.MustCompile(`<option[^>]*value="([^"]+)"`)
	gasmurlDomainTextRe = regexp.MustCompile(`>([a-z0-9-]+\.[a-z.]{2,})<`)
	gasmurlAnchorRe     = regexp.MustCompile(`(?s)<a\b([^>]*)>(.*?)</a>`)
	gasmurlHrefRe       = regexp.MustCompile(`href="([^"]*)"`)
	gasmurlFromRe       = regexp.MustCompile(`(?s)<div[^>]*class="[^"]*from[^"]*"[^>]*>(.*?)</div>`)
	gasmurlSubjRe       = regexp.MustCompile(`(?s)<div[^>]*class="[^"]*subj[^"]*"[^>]*>(.*?)</div>`)
	gasmurlTimeRe       = regexp.MustCompile(`(?s)<div[^>]*class="[^"]*time[^"]*"[^>]*>(.*?)</div>`)
	gasmurlFromDivRe    = regexp.MustCompile(`(?s)<div[^>]*class="[^"]*from_div[^"]*"[^>]*>(.*?)</div>`)
	gasmurlSubjDivRe    = regexp.MustCompile(`(?s)<div[^>]*class="[^"]*subj_div[^"]*"[^>]*>(.*?)</div>`)
	gasmurlTimeDivRe    = regexp.MustCompile(`(?s)<div[^>]*class="[^"]*time_div[^"]*"[^>]*>(.*?)</div>`)
)

// divText returns capture group 1 of re against html, or "".
func divText(html string, re *regexp.Regexp) string {
	m := re.FindStringSubmatch(html)
	if len(m) < 2 {
		return ""
	}
	return m[1]
}

// lastPathSegment returns the part after the last "/" in a URL path.
func lastPathSegment(href string) string {
	href = strings.TrimRight(href, "/")
	if i := strings.LastIndex(href, "/"); i >= 0 {
		return href[i+1:]
	}
	return href
}

// stripHTML removes tags and collapses whitespace.
func stripHTML(s string) string {
	s = tagRe.ReplaceAllString(s, " ")
	s = wsRe.ReplaceAllString(s, " ")
	return strings.TrimSpace(s)
}

var (
	tagRe = regexp.MustCompile(`<[^>]+>`)
	wsRe  = regexp.MustCompile(`\s+`)
)

// extractDivContent returns the inner HTML of the first <div ... id="<id>">,
// tracking nested <div>/</div> pairs so deeply-nested mail bodies are kept.
// ponytail: hand-rolled depth counter avoids truncating nested bodies that a
// naive lazy regex would; upgrade to goquery if scraping targets grow.
func extractDivContent(html, id string) string {
	startRe := regexp.MustCompile(`(?s)<div[^>]*\bid="` + regexp.QuoteMeta(id) + `"[^>]*>`)
	loc := startRe.FindStringIndex(html)
	if loc == nil {
		return ""
	}
	s := html[loc[1]:]
	depth := 1
	pos := 0
	for pos < len(s) {
		o := divOpenRe.FindStringIndex(s[pos:])
		c := divCloseRe.FindStringIndex(s[pos:])
		if c == nil {
			break
		}
		if o != nil && o[0] < c[0] {
			depth++
			pos += o[1]
		} else {
			depth--
			if depth == 0 {
				return s[:pos+c[0]]
			}
			pos += c[1]
		}
	}
	return s
}

var (
	divOpenRe  = regexp.MustCompile(`<div\b`)
	divCloseRe = regexp.MustCompile(`</div>`)
)
