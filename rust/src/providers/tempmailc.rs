use async_trait::async_trait;
use serde::Deserialize;

use crate::error::TempMailError;
use crate::httpclient::HttpClient;
use crate::models::{Message, MessageDetail};
use crate::providers::TempMailProvider;

const BASE_URL: &str = "https://tempmailc.com/api/v1";

/// Provider for tempmailc.com — REST API, no auth, no cookies.
#[derive(Debug)]
pub struct Tempmailc {
    client: HttpClient,
    email: std::sync::Arc<tokio::sync::Mutex<Option<String>>>,
}

#[derive(Deserialize)]
struct NewResp {
    ok: bool,
    email: Option<String>,
}

impl Tempmailc {
    pub fn new(client: HttpClient) -> Self {
        Self {
            client,
            email: std::sync::Arc::new(tokio::sync::Mutex::new(None)),
        }
    }
}

fn parse_iso(s: &str) -> chrono::DateTime<chrono::Utc> {
    chrono::DateTime::parse_from_rfc3339(s)
        .map(|dt| dt.with_timezone(&chrono::Utc))
        .unwrap_or_else(|_| chrono::Utc::now())
}

fn opt_str(v: Option<&serde_json::Value>) -> String {
    v.and_then(|x| x.as_str()).unwrap_or("").to_string()
}

fn opt_vid(v: Option<&serde_json::Value>) -> String {
    match v {
        Some(serde_json::Value::String(s)) => s.clone(),
        Some(serde_json::Value::Number(n)) => n.to_string(),
        Some(other) => other.to_string(),
        None => String::new(),
    }
}

#[async_trait]
impl TempMailProvider for Tempmailc {
    async fn generate_email(&self) -> Result<String, TempMailError> {
        let resp: NewResp = self
            .client
            .get_json(&format!("{}/new", BASE_URL))
            .await?;
        if !resp.ok {
            return Err(TempMailError::Api("tempmailc API returned not ok".into()));
        }
        let email = resp
            .email
            .ok_or_else(|| TempMailError::Api("Missing email in response".into()))?;
        let mut e = self.email.lock().await;
        *e = Some(email.clone());
        Ok(email)
    }

    async fn get_inbox(&self, email: &str) -> Result<Vec<Message>, TempMailError> {
        let v: serde_json::Value = self
            .client
            .get_json(&format!("{}/inbox?email={}", BASE_URL, email))
            .await?;
        let arr = v.get("messages").and_then(|m| m.as_array());
        Ok(arr
            .map(|items| {
                items
                    .iter()
                    .map(|m| Message {
                        id: opt_vid(m.get("id").or_else(|| m.get("msg_id"))),
                        sender: opt_str(m.get("from").or_else(|| m.get("from_mail"))),
                        subject: opt_str(m.get("subject")),
                        date: parse_iso(&opt_str(m.get("date").or_else(|| m.get("time")))), preview: String::new(), has_attachments: false })
                    .collect()
            })
            .unwrap_or_default())
    }

    async fn read_message(&self, message_id: &str) -> Result<MessageDetail, TempMailError> {
        let email = self
            .email
            .lock()
            .await
            .clone()
            .ok_or_else(|| TempMailError::Api("Must call generate_email first".into()))?;
        let m: serde_json::Value = self
            .client
            .get_json(&format!(
                "{}/message?msg_id={}&email={}",
                BASE_URL, message_id, email
            ))
            .await?;
        Ok(MessageDetail {
            message: Message {
                id: opt_vid(m.get("id").or_else(|| m.get("msg_id"))),
                sender: opt_str(m.get("from").or_else(|| m.get("from_mail"))),
                subject: opt_str(m.get("subject")),
                date: parse_iso(&opt_str(m.get("date").or_else(|| m.get("time")))), preview: String::new(), has_attachments: false },
            body_text: {
                let b = opt_str(m.get("text").or_else(|| m.get("body_text")));
                if b.is_empty() { None } else { Some(b) }
            },
            body_html: {
                let h = opt_str(m.get("html").or_else(|| m.get("body_html")));
                if h.is_empty() { None } else { Some(h) }
            },
            attachments: vec![], ..Default::default()         })
    }

    async fn delete_email(&self, _email: &str) -> Result<bool, TempMailError> {
        let mut e = self.email.lock().await;
        *e = None;
        Ok(true)
    }
}
