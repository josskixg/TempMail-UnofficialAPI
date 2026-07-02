use async_trait::async_trait;
use serde::de::DeserializeOwned;
use serde::Deserialize;

use crate::error::TempMailError;
use crate::httpclient::HttpClient;
use crate::models::{Message, MessageDetail};
use crate::providers::TempMailProvider;

const BASE_URL: &str = "https://api.internal.temp-mail.io/api/v3";

/// Provider for temp-mail.io — free internal REST API, Bearer token.
#[derive(Debug)]
pub struct TempMailIo {
    client: HttpClient,
    /// (email, token) cached after generation.
    state: std::sync::Arc<tokio::sync::Mutex<Option<(String, String)>>>,
}

#[derive(Deserialize)]
struct NewResp {
    email: Option<String>,
    token: Option<String>,
}

impl TempMailIo {
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

    /// GET with Authorization: Bearer <token>. HttpClient.get_json can't carry headers,
    /// so drop to the inner reqwest client for this authenticated call.
    async fn get_auth_json<T: DeserializeOwned>(&self, url: &str) -> Result<T, TempMailError> {
        let token = self.token().await?;
        let resp = self
            .client
            .inner()
            .get(url)
            .header("Authorization", format!("Bearer {}", token))
            .header("Accept", "application/json")
            .send()
            .await
            .map_err(TempMailError::Http)?;
        let status = resp.status();
        if !status.is_success() {
            return Err(TempMailError::Api(format!(
                "HTTP {}: {}",
                status.as_u16(),
                status.canonical_reason().unwrap_or("")
            )));
        }
        resp.json::<T>().await.map_err(TempMailError::Http)
    }

    async fn fetch_messages(&self, email: &str) -> Result<Vec<serde_json::Value>, TempMailError> {
        let v: serde_json::Value = self
            .get_auth_json(&format!("{}/email/{}/messages", BASE_URL, email))
            .await?;
        Ok(match v {
            serde_json::Value::Array(arr) => arr,
            ref other => other
                .get("messages")
                .and_then(|x| x.as_array())
                .or_else(|| other.get("mails").and_then(|x| x.as_array()))
                .cloned()
                .unwrap_or_default(),
        })
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

/// `from` may be a plain string or an object `{address, name}`.
fn opt_sender(v: Option<&serde_json::Value>) -> String {
    match v {
        Some(serde_json::Value::String(s)) => s.clone(),
        Some(serde_json::Value::Object(o)) => o
            .get("address")
            .and_then(|x| x.as_str())
            .or_else(|| o.get("name").and_then(|x| x.as_str()))
            .unwrap_or("")
            .to_string(),
        _ => String::new(),
    }
}

#[async_trait]
impl TempMailProvider for TempMailIo {
    async fn generate_email(&self) -> Result<String, TempMailError> {
        let body = serde_json::json!({ "min_name_length": 6, "max_name_length": 12 });
        let resp: NewResp = self
            .client
            .post_json(&format!("{}/email/new", BASE_URL), &body)
            .await?;
        let email = resp
            .email
            .ok_or_else(|| TempMailError::Api("Missing email in response".into()))?;
        let token = resp.token.unwrap_or_default();
        let mut state = self.state.lock().await;
        *state = Some((email.clone(), token));
        Ok(email)
    }

    async fn get_inbox(&self, email: &str) -> Result<Vec<Message>, TempMailError> {
        let msgs = self.fetch_messages(email).await?;
        Ok(msgs
            .iter()
            .map(|m| Message {
                id: opt_vid(m.get("id")),
                sender: opt_sender(m.get("from").or_else(|| m.get("sender"))),
                subject: opt_str(m.get("subject")),
                date: parse_iso(&opt_str(m.get("created_at").or_else(|| m.get("date")))), preview: String::new(), has_attachments: false })
            .collect())
    }

    async fn read_message(&self, message_id: &str) -> Result<MessageDetail, TempMailError> {
        // ponytail: temp-mail.io has no single-message endpoint; re-fetch inbox and find by id
        let email = self
            .state
            .lock()
            .await
            .clone()
            .map(|(e, _)| e)
            .ok_or_else(|| TempMailError::Api("Must call generate_email first".into()))?;
        let msgs = self.fetch_messages(&email).await?;
        for m in &msgs {
            if opt_vid(m.get("id")) == message_id {
                return Ok(MessageDetail {
                    message: Message {
                        id: opt_vid(m.get("id")),
                        sender: opt_sender(m.get("from").or_else(|| m.get("sender"))),
                        subject: opt_str(m.get("subject")),
                        date: parse_iso(&opt_str(
                            m.get("created_at").or_else(|| m.get("date")),
                        )),
                        ..Default::default()
                    },
                    body_text: {
                        let b = opt_str(m.get("body_text").or_else(|| m.get("text")));
                        if b.is_empty() { None } else { Some(b) }
                    },
                    body_html: {
                        let h = opt_str(m.get("body_html").or_else(|| m.get("html")));
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
