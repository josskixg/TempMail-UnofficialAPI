use async_trait::async_trait;
use serde::Deserialize;

use crate::error::TempMailError;
use crate::httpclient::HttpClient;
use crate::models::{Message, MessageDetail};
use crate::providers::TempMailProvider;

const BASE_URL: &str = "https://api.tempmail.lol/v2";

/// Provider for tempmail.lol — REST API, token-based, no auth header.
#[derive(Debug)]
pub struct TempmailLol {
    client: HttpClient,
    /// (email, token) cached after generation.
    state: std::sync::Arc<tokio::sync::Mutex<Option<(String, String)>>>,
}

#[derive(Deserialize)]
struct CreateResp {
    address: Option<String>,
    token: Option<String>,
}

#[derive(Deserialize)]
struct InboxResp {
    #[serde(default)]
    expired: bool,
    #[serde(default)]
    emails: Vec<serde_json::Value>,
}

impl TempmailLol {
    pub fn new(client: HttpClient) -> Self {
        Self {
            client,
            state: std::sync::Arc::new(tokio::sync::Mutex::new(None)),
        }
    }

    async fn token(&self) -> Result<String, TempMailError> {
        self.state
            .lock()
            .await
            .clone()
            .map(|(_, t)| t)
            .ok_or_else(|| TempMailError::Api("Must call generate_email first".into()))
    }

    async fn fetch_inbox(&self) -> Result<Vec<serde_json::Value>, TempMailError> {
        let token = self.token().await?;
        let resp: InboxResp = self
            .client
            .get_json(&format!("{}/inbox?token={}", BASE_URL, token))
            .await?;
        if resp.expired {
            return Err(TempMailError::Api("Token expired".into()));
        }
        Ok(resp.emails)
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

fn email_to_message(m: &serde_json::Value) -> Message {
    Message {
        id: opt_vid(m.get("_id").or_else(|| m.get("id")).or_else(|| m.get("uid"))),
        sender: opt_str(m.get("from").or_else(|| m.get("sender"))),
        subject: opt_str(m.get("subject")),
        date: parse_iso(&opt_str(m.get("date").or_else(|| m.get("createdAt")))), preview: String::new(), has_attachments: false }
}

#[async_trait]
impl TempMailProvider for TempmailLol {
    async fn generate_email(&self) -> Result<String, TempMailError> {
        let body = serde_json::json!({});
        let resp: CreateResp = self
            .client
            .post_json(&format!("{}/inbox/create", BASE_URL), &body)
            .await?;
        let email = resp
            .address
            .ok_or_else(|| TempMailError::Api("Missing address in response".into()))?;
        let token = resp
            .token
            .ok_or_else(|| TempMailError::Api("Missing token in response".into()))?;
        let mut state = self.state.lock().await;
        *state = Some((email.clone(), token));
        Ok(email)
    }

    async fn get_inbox(&self, _email: &str) -> Result<Vec<Message>, TempMailError> {
        let emails = self.fetch_inbox().await?;
        Ok(emails.iter().map(email_to_message).collect())
    }

    async fn read_message(&self, message_id: &str) -> Result<MessageDetail, TempMailError> {
        // ponytail: tempmail.lol has no single-message endpoint; re-fetch inbox and find by id
        let emails = self.fetch_inbox().await?;
        for m in &emails {
            if opt_vid(m.get("_id").or_else(|| m.get("id")).or_else(|| m.get("uid"))) == message_id {
                return Ok(MessageDetail {
                    message: email_to_message(m),
                    body_text: {
                        let b = opt_str(m.get("body").or_else(|| m.get("text")));
                        if b.is_empty() { None } else { Some(b) }
                    },
                    body_html: {
                        let h = opt_str(m.get("html"));
                        if h.is_empty() { None } else { Some(h) }
                    },
                    attachments: vec![], ..Default::default()                 });
            }
        }
        Err(TempMailError::NotFound)
    }

    async fn delete_email(&self, _email: &str) -> Result<bool, TempMailError> {
        let mut state = self.state.lock().await;
        *state = None;
        Ok(true)
    }
}
