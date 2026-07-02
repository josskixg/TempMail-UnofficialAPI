use async_trait::async_trait;
use serde::{de::DeserializeOwned, Deserialize, Serialize};

use crate::error::TempMailError;
use crate::httpclient::HttpClient;
use crate::models::{Message, MessageDetail};
use crate::providers::TempMailProvider;

const BASE_URL: &str = "https://zoromail.com/public_api.php/v1";

/// Provider for zoromail.com — clean REST API, no auth required.
#[derive(Debug)]
pub struct Zoromail {
    client: HttpClient,
    email: std::sync::Arc<tokio::sync::Mutex<Option<String>>>,
}

/// Wrapper for the `{success, data, error}` envelope zoromail returns on every call.
#[derive(Deserialize)]
struct ApiResp<T> {
    success: bool,
    data: T,
    #[serde(default)]
    error: Option<String>,
}

#[derive(Deserialize)]
struct EmailCreated {
    email: String,
}

impl Zoromail {
    pub fn new(client: HttpClient) -> Self {
        Self {
            client,
            email: std::sync::Arc::new(tokio::sync::Mutex::new(None)),
        }
    }

    async fn api_get<T: DeserializeOwned>(&self, url: &str) -> Result<T, TempMailError> {
        let resp: ApiResp<T> = self.client.get_json(url).await?;
        if !resp.success {
            return Err(TempMailError::Api(
                resp.error.unwrap_or_else(|| "zoromail API error".into()),
            ));
        }
        Ok(resp.data)
    }

    async fn api_post<T: DeserializeOwned, S: Serialize + ?Sized>(
        &self,
        url: &str,
        body: &S,
    ) -> Result<T, TempMailError> {
        let resp: ApiResp<T> = self.client.post_json(url, body).await?;
        if !resp.success {
            return Err(TempMailError::Api(
                resp.error.unwrap_or_else(|| "zoromail API error".into()),
            ));
        }
        Ok(resp.data)
    }
}

fn random_alphanumeric(len: usize) -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    const CHARSET: &[u8] = b"abcdefghijklmnopqrstuvwxyz0123456789";
    let mut s = String::with_capacity(len);
    let mut seed = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .subsec_nanos() as usize;
    for _ in 0..len {
        seed = (seed.wrapping_mul(1103515245).wrapping_add(12345)) % (1 << 31);
        s.push(CHARSET[seed % CHARSET.len()] as char);
    }
    s
}

fn parse_iso(s: &str) -> chrono::DateTime<chrono::Utc> {
    chrono::DateTime::parse_from_rfc3339(s)
        .map(|dt| dt.with_timezone(&chrono::Utc))
        .unwrap_or_else(|_| chrono::Utc::now())
}

fn opt_str(v: Option<&serde_json::Value>) -> String {
    v.and_then(|x| x.as_str()).unwrap_or("").to_string()
}

/// Coerce a JSON id (string or number) into a plain String.
fn opt_vid(v: Option<&serde_json::Value>) -> String {
    match v {
        Some(serde_json::Value::String(s)) => s.clone(),
        Some(serde_json::Value::Number(n)) => n.to_string(),
        Some(other) => other.to_string(),
        None => String::new(),
    }
}

#[async_trait]
impl TempMailProvider for Zoromail {
    async fn generate_email(&self) -> Result<String, TempMailError> {
        let domains: Vec<String> = self.api_get(&format!("{}/domains", BASE_URL)).await?;
        if domains.is_empty() {
            return Err(TempMailError::Api("No domains available".into()));
        }
        // ponytail: throwaway address — first domain is fine, no need for RNG pick
        let domain = domains[0].clone();
        let username = random_alphanumeric(10);
        let body = serde_json::json!({ "username": username, "domain": domain });
        let created: EmailCreated = self
            .api_post(&format!("{}/emails", BASE_URL), &body)
            .await?;
        let mut email = self.email.lock().await;
        *email = Some(created.email.clone());
        Ok(created.email)
    }

    async fn get_inbox(&self, _email: &str) -> Result<Vec<Message>, TempMailError> {
        let email = self
            .email
            .lock()
            .await
            .clone()
            .ok_or_else(|| TempMailError::Api("Must call generate_email first".into()))?;
        let raw: Vec<serde_json::Value> = self
            .api_get(&format!("{}/emails/{}/messages", BASE_URL, email))
            .await?;
        Ok(raw
            .into_iter()
            .map(|m| Message {
                id: opt_vid(m.get("id")),
                sender: opt_str(m.get("from")),
                subject: opt_str(m.get("subject")),
                date: parse_iso(&opt_str(m.get("date"))), preview: String::new(), has_attachments: false })
            .collect())
    }

    async fn read_message(&self, message_id: &str) -> Result<MessageDetail, TempMailError> {
        let m: serde_json::Value = self
            .api_get(&format!("{}/messages/{}", BASE_URL, message_id))
            .await?;
        Ok(MessageDetail {
            message: Message {
                id: opt_vid(m.get("id")),
                sender: opt_str(m.get("from")),
                subject: opt_str(m.get("subject")),
                date: parse_iso(&opt_str(m.get("date"))), preview: String::new(), has_attachments: false },
            body_text: Some(opt_str(m.get("text"))).filter(|s| !s.is_empty()),
            body_html: Some(opt_str(m.get("html"))).filter(|s| !s.is_empty()),
            attachments: vec![], ..Default::default()         })
    }

    async fn delete_email(&self, _email: &str) -> Result<bool, TempMailError> {
        let mut email = self.email.lock().await;
        *email = None;
        Ok(true)
    }
}
