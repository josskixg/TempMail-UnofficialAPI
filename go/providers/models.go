package providers

import "time"

// Message is a summary of an email in the inbox.
type Message struct {
	ID              string    `json:"id"`
	Sender          string    `json:"sender"`
	Subject         string    `json:"subject"`
	Date            time.Time `json:"date"`
	Preview         string    `json:"preview,omitempty"`
	HasAttachments  bool      `json:"has_attachments,omitempty"`
}

// MessageDetail contains the full email body, headers, and metadata.
// Fields are auto-normalized by NormalizeDetail() after a provider returns.
type MessageDetail struct {
	Message
	BodyText    string            `json:"body_text"`
	BodyHTML    string            `json:"body_html"`
	BodyPreview string            `json:"body_preview,omitempty"`
	ContentType string            `json:"content_type,omitempty"`
	Raw         string            `json:"raw,omitempty"`
	Headers     map[string]string `json:"headers,omitempty"`
	CC          []string          `json:"cc,omitempty"`
	ReplyTo     string            `json:"reply_to,omitempty"`
	MessageID   string            `json:"message_id,omitempty"`
	Size        int               `json:"size,omitempty"`
	IsHTML      bool              `json:"is_html"`
	Attachments []map[string]any  `json:"attachments"`
}
