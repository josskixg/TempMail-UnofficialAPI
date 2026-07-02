use async_trait::async_trait;
use serde::Deserialize;

use crate::error::TempMailError;
use crate::httpclient::HttpClient;
use crate::models::{Message, MessageDetail};
use crate::providers::TempMailProvider;

const BASE_URL: &str = "https://tempmail.plus";
const DOMAIN: &str = "mailto.plus";

/// Provider for tempmail.plus — REST API, no auth, email passed as query param.
#[derive(Debug)]
pub struct TempmailPlus {
    client: HttpClient,
    email: std::sync::Arc<tokio::sync::Mutex<Option<String>>>,
}

#[derive(Deserialize)]
struct InboxResp {
    #[serde(default)]
    result: Option<bool>,
    #[serde(default)]
    mail_list: Vec<serde_json::Value>,
}

impl TempmailPlus {
    pub fn new(client: HttpClient) -> Self {
        Self {
            client,
            email: std::sync::Arc::new(tokio::sync::Mutex::new(None)),
        }
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

fn parse_date(s: &str) -> chrono::DateTime<chrono::Utc> {
    chrono::NaiveDateTime::parse_from_str(s, "%Y-%m-%d %H:%M:%S")
        .map(|dt| dt.and_utc())
        .unwrap_or_else(|_| chrono::Utc::now())
}

fn parse_rfc2822(s: &str) -> chrono::DateTime<chrono::Utc> {
    chrono::DateTime::parse_from_rfc2822(s)
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
impl TempMailProvider for TempmailPlus {
    async fn generate_email(&self) -> Result<String, TempMailError> {
        let username = random_alphanumeric(10);
        let email = format!("{}@{}", username, DOMAIN);
        let mut e = self.email.lock().await;
        *e = Some(email.clone());
        Ok(email)
    }

    async fn get_inbox(&self, email: &str) -> Result<Vec<Message>, TempMailError> {
        let resp: InboxResp = self
            .client
            .get_json(&format!("{}/api/mails?email={}", BASE_URL, email))
            .await?;
        if let Some(false) = resp.result {
            return Err(TempMailError::Api("tempmail.plus API returned error".into()));
        }
        Ok(resp
            .mail_list
            .into_iter()
            .map(|m| Message {
                id: opt_vid(m.get("mail_id")),
                sender: opt_str(m.get("from_mail")),
                subject: opt_str(m.get("subject")),
                date: parse_date(&opt_str(m.get("time"))), preview: String::new(), has_attachments: false })
            .collect())
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
                "{}/api/mails/{}?email={}",
                BASE_URL, message_id, email
            ))
            .await?;
        Ok(MessageDetail {
            message: Message {
                id: opt_vid(m.get("mail_id")),
                sender: opt_str(m.get("from_mail").or_else(|| m.get("from"))),
                subject: opt_str(m.get("subject")),
                date: parse_rfc2822(&opt_str(m.get("date"))), preview: String::new(), has_attachments: false },
            body_text: {
                let b = opt_str(m.get("text"));
                if b.is_empty() { None } else { Some(b) }
            },
            body_html: {
                let h = opt_str(m.get("html"));
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
