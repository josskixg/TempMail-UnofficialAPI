use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Message {
    pub id: String,
    pub sender: String,
    pub subject: String,
    pub date: DateTime<Utc>,
    #[serde(default)]
    pub preview: String,
    #[serde(default)]
    pub has_attachments: bool,
}

impl Default for Message {
    fn default() -> Self {
        Self {
            id: String::new(),
            sender: String::new(),
            subject: String::new(),
            date: DateTime::from_timestamp(0, 0).unwrap_or_default(),
            preview: String::new(),
            has_attachments: false,
        }
    }
}


#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MessageDetail {
    #[serde(flatten)]
    pub message: Message,
    /// Plain text body. Auto-filled from body_html if empty after normalize().
    #[serde(default)]
    pub body_text: Option<String>,
    /// Raw HTML body from provider.
    #[serde(default)]
    pub body_html: Option<String>,
    /// Short plain-text preview (max 200 chars), auto-filled by normalize().
    #[serde(default)]
    pub body_preview: String,
    /// MIME content-type, auto-inferred by normalize().
    #[serde(default)]
    pub content_type: String,
    /// Raw MIME string if provider supplies it.
    #[serde(default)]
    pub raw: String,
    /// Email headers (From, To, Message-ID, etc.).
    #[serde(default)]
    pub headers: HashMap<String, String>,
    /// CC recipients.
    #[serde(default)]
    pub cc: Vec<String>,
    /// Reply-To address.
    #[serde(default)]
    pub reply_to: String,
    /// Message-ID header value.
    #[serde(default)]
    pub message_id: String,
    /// Email size in bytes.
    #[serde(default)]
    pub size: usize,
    /// True if body_html is non-empty. Auto-computed by normalize().
    #[serde(default)]
    pub is_html: bool,
    #[serde(default)]
    pub attachments: Vec<serde_json::Value>,
}

impl Default for MessageDetail {
    fn default() -> Self {
        Self {
            message: Message::default(),
            body_text: None,
            body_html: None,
            body_preview: String::new(),
            content_type: "text/plain".to_string(),
            raw: String::new(),
            headers: std::collections::HashMap::new(),
            cc: Vec::new(),
            reply_to: String::new(),
            message_id: String::new(),
            size: 0,
            is_html: false,
            attachments: Vec::new(),
        }
    }
}

impl MessageDetail {
    /// Strip HTML tags to plain text (lightweight, no external deps).
    pub fn strip_html(html: &str) -> String {
        let mut result = String::with_capacity(html.len());
        let mut in_tag = false;
        for ch in html.chars() {
            match ch {
                '<' => in_tag = true,
                '>' => in_tag = false,
                _ if !in_tag => result.push(ch),
                _ => {}
            }
        }
        let result = result
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ");
        result.split_whitespace().collect::<Vec<_>>().join(" ")
    }

    /// Auto-normalize derived fields after provider populates the struct.
    pub fn normalize(&mut self) {
        let has_html = self.body_html.as_deref().map_or(false, |s| !s.trim().is_empty());
        let has_text_raw = self.body_text.as_deref().map_or(false, |s| !s.trim().is_empty());

        // Auto-strip HTML → text if only HTML provided
        if has_html && !has_text_raw {
            let stripped = Self::strip_html(self.body_html.as_deref().unwrap_or(""));
            self.body_text = Some(stripped);
        }

        let has_text = self.body_text.as_deref().map_or(false, |s| !s.trim().is_empty());

        self.is_html = has_html;
        self.message.has_attachments = !self.attachments.is_empty();

        self.content_type = if has_html && has_text {
            "multipart/alternative".to_string()
        } else if has_html {
            "text/html".to_string()
        } else {
            "text/plain".to_string()
        };

        if self.body_preview.is_empty() && has_text {
            let text = self.body_text.as_deref().unwrap_or("");
            let preview: String = text.chars().take(200).collect();
            self.body_preview = preview.trim().to_string();
        }

        if !self.message_id.is_empty() && !self.headers.contains_key("Message-ID") {
            self.headers.insert("Message-ID".to_string(), self.message_id.clone());
        }
    }
}
