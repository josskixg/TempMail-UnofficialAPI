use async_trait::async_trait;
use regex::Regex;
use std::sync::Arc;
use tokio::sync::Mutex;

use crate::error::TempMailError;
use crate::httpclient::HttpClient;
use crate::models::{Message, MessageDetail};
use crate::providers::TempMailProvider;

const BASE_URL: &str = "https://mailnesia.com";

/// Provider for mailnesia.com — public mailbox, no auth, HTML table scraping.
#[derive(Debug)]
pub struct Mailnesia {
    client: HttpClient,
    username: Arc<Mutex<Option<String>>>,
}

impl Mailnesia {
    pub fn new(client: HttpClient) -> Self {
        Self {
            client,
            username: Arc::new(Mutex::new(None)),
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

fn strip_html(html: &str) -> String {
    let re = Regex::new(r"<[^>]+>").unwrap();
    let s = re.replace_all(html, "").replace('\n', " ");
    let ws = Regex::new(r"\s+").unwrap();
    ws.replace_all(&s, " ").trim().to_string()
}

fn parse_date(s: &str) -> chrono::DateTime<chrono::Utc> {
    chrono::NaiveDateTime::parse_from_str(s, "%Y-%m-%d %H:%M:%S")
        .map(|dt| dt.and_utc())
        .unwrap_or_else(|_| chrono::Utc::now())
}

#[async_trait]
impl TempMailProvider for Mailnesia {
    async fn generate_email(&self) -> Result<String, TempMailError> {
        let username = random_alphanumeric(10);
        let email = format!("{}@mailnesia.com", username);
        let mut u = self.username.lock().await;
        *u = Some(username);
        Ok(email)
    }

    async fn get_inbox(&self, email: &str) -> Result<Vec<Message>, TempMailError> {
        let username = if let Some(idx) = email.find('@') {
            &email[..idx]
        } else {
            email
        };
        let headers = self.get_headers_with_ip_rotation();
        let url = format!("{}/mailbox/{}", BASE_URL, username);
        let mut builder = self.client.inner().get(&url);
        for (k, v) in headers {
            builder = builder.header(k, v);
        }
        let resp = builder.send().await.map_err(TempMailError::Http)?;
        if !resp.status().is_success() {
            return Err(TempMailError::Api(format!("HTTP {}", resp.status().as_u16())));
        }
        let html = resp.text().await.map_err(TempMailError::Http)?;
        let re_row = Regex::new(r"(?s)<tr[^>]*>(.*?)</tr>").unwrap();
        let re_cell = Regex::new(r"(?s)<td[^>]*>(.*?)</td>").unwrap();
        let re_link = Regex::new(r#"<a[^>]*href="([^"]*)""#).unwrap();

        let mut messages = Vec::new();
        for row in re_row.captures_iter(&html) {
            let row_html = row.get(1).map(|m| m.as_str()).unwrap_or("");
            let cells: Vec<String> = re_cell
                .captures_iter(row_html)
                .map(|c| strip_html(c.get(1).map(|m| m.as_str()).unwrap_or("")))
                .collect();
            if cells.len() < 3 {
                continue;
            }
            let msg_id = re_link
                .captures(row_html)
                .and_then(|c| c.get(1))
                .map(|m| m.as_str().rsplit('/').next().unwrap_or("").to_string())
                .unwrap_or_default();
            let sender = cells[0].clone();
            let subject = cells[1].clone();
            let time_str = cells[2].clone();
            if sender.is_empty() && subject.is_empty() {
                continue;
            }
            messages.push(Message {
                id: msg_id,
                sender,
                subject,
                date: parse_date(&time_str), preview: String::new(), has_attachments: false });
        }
        Ok(messages)
    }

    async fn read_message(&self, message_id: &str) -> Result<MessageDetail, TempMailError> {
        let username = self
            .username
            .lock()
            .await
            .clone()
            .ok_or_else(|| TempMailError::Api("Must call generate_email first".into()))?;
        let html = self
            .client
            .get(&format!("{}/mailbox/{}/{}", BASE_URL, username, message_id))
            .await?;
        let re_msg = Regex::new(r#"(?s)<div[^>]*id="message"[^>]*>(.*?)</div>"#).unwrap();
        let body_html = re_msg
            .captures(&html)
            .and_then(|c| c.get(1))
            .map(|m| m.as_str().to_string())
            .unwrap_or_else(|| html.clone());
        let body_text = strip_html(&body_html);
        Ok(MessageDetail {
            message: Message {
                id: message_id.to_string(),
                sender: String::new(),
                subject: String::new(),
                date: chrono::Utc::now(), preview: String::new(), has_attachments: false },
            body_text: Some(body_text).filter(|s| !s.is_empty()),
            body_html: Some(body_html).filter(|s| !s.is_empty()),
            attachments: vec![], ..Default::default()         })
    }

    async fn delete_email(&self, _email: &str) -> Result<bool, TempMailError> {
        let mut u = self.username.lock().await;
        *u = None;
        Ok(true)
    }
}

impl Mailnesia {
    fn generate_random_ip() -> String {
        use std::time::{SystemTime, UNIX_EPOCH};
        let mut seed = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .subsec_nanos() as u32;
        let mut parts = Vec::with_capacity(4);
        for _ in 0..4 {
            seed = seed.wrapping_mul(1103515245).wrapping_add(12345);
            let val = if parts.is_empty() || parts.len() == 3 {
                (seed % 254) + 1
            } else {
                seed % 256
            };
            parts.push(val.to_string());
        }
        parts.join(".")
    }

    fn get_headers_with_ip_rotation(&self) -> std::collections::HashMap<String, String> {
        let ip = Self::generate_random_ip();
        let mut headers = std::collections::HashMap::new();
        headers.insert("X-Forwarded-For".to_string(), ip.clone());
        headers.insert("X-Real-IP".to_string(), ip.clone());
        headers.insert("CF-Connecting-IP".to_string(), ip.clone());
        headers.insert("True-Client-IP".to_string(), ip);
        headers
    }
}
