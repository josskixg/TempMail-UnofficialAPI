package providers

import (
	"regexp"
	"strings"
)

var (
	rexStyleOpen  = regexp.MustCompile(`(?i)<(style|script)[^>]*>`)
	rexBlock      = regexp.MustCompile(`(?i)<(?:br\s*/?|/p|/div|/tr|/li|/h\d)>`)
	rexTags       = regexp.MustCompile(`<[^>]+>`)
	rexBlankLines = regexp.MustCompile(`\n{3,}`)
)

// StripHTML removes HTML tags and returns plain text.
func StripHTML(html string) string {
	// Remove style/script block contents by stripping between open and close tags manually
	// We process tag by tag to avoid unsupported backreference regex in Go
	s := removeStyleScriptBlocks(html)
	s = rexBlock.ReplaceAllString(s, "\n")
	s = rexTags.ReplaceAllString(s, "")
	s = strings.NewReplacer(
		"&amp;", "&",
		"&lt;", "<",
		"&gt;", ">",
		"&quot;", `"`,
		"&#39;", "'",
		"&nbsp;", " ",
	).Replace(s)
	s = rexBlankLines.ReplaceAllString(s, "\n\n")
	return strings.TrimSpace(s)
}

// removeStyleScriptBlocks removes <style>...</style> and <script>...</script> content.
func removeStyleScriptBlocks(html string) string {
	tags := []string{"style", "script"}
	s := html
	for _, tag := range tags {
		closeTag := "</" + tag + ">"
		for {
			loc := rexStyleOpen.FindStringIndex(s)
			if loc == nil {
				break
			}
			closeIdx := strings.Index(strings.ToLower(s[loc[1]:]), closeTag)
			if closeIdx < 0 {
				s = s[:loc[0]]
				break
			}
			s = s[:loc[0]] + s[loc[1]+closeIdx+len(closeTag):]
		}
	}
	return s
}

// NormalizeDetail auto-fills derived fields on a MessageDetail after a provider returns it.
func NormalizeDetail(d *MessageDetail) {
	if d == nil {
		return
	}
	hasText := strings.TrimSpace(d.BodyText) != ""
	hasHTML := strings.TrimSpace(d.BodyHTML) != ""

	if hasHTML && !hasText {
		d.BodyText = StripHTML(d.BodyHTML)
		hasText = true
	}

	d.IsHTML = hasHTML

	switch {
	case hasHTML && hasText:
		d.ContentType = "multipart/alternative"
	case hasHTML:
		d.ContentType = "text/html"
	default:
		d.ContentType = "text/plain"
	}

	if d.BodyPreview == "" && hasText {
		runes := []rune(strings.TrimSpace(d.BodyText))
		if len(runes) > 200 {
			runes = runes[:200]
		}
		d.BodyPreview = string(runes)
	}

	d.Message.HasAttachments = len(d.Attachments) > 0

	if d.MessageID != "" {
		if d.Headers == nil {
			d.Headers = make(map[string]string)
		}
		if _, ok := d.Headers["Message-ID"]; !ok {
			d.Headers["Message-ID"] = d.MessageID
		}
	}
}
