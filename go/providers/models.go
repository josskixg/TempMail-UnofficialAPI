package providers

import "time"

// Message is a summary of an email in the inbox.
type Message struct {
	ID      string    `json:"id"`
	Sender  string    `json:"sender"`
	Subject string    `json:"subject"`
	Date    time.Time `json:"date"`
}

// MessageDetail contains the full email body and attachments.
type MessageDetail struct {
	Message
	BodyText    string           `json:"body_text"`
	BodyHTML    string           `json:"body_html"`
	Attachments []map[string]any `json:"attachments"`
}
