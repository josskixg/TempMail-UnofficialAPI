use async_trait::async_trait;
use regex::Regex;
use serde::Deserialize;
use std::sync::Arc;
use tokio::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::error::TempMailError;
use crate::httpclient::HttpClient;
use crate::models::{Message, MessageDetail};
use crate::providers::TempMailProvider;

const BASE: &str = "https://www.1secemail.com";
const DOMAINS: &[&str] = &[
    "qzueos.com", "gaziw.com", "emailgenerator.xyz",
];

#[derive(Debug)]
struct OneSecState {
    csrf: String,
    email: String,
}

#[derive(Debug)]
pub struct OneSecEmail {
    client: HttpClient,
    state: Arc<Mutex<Option<OneSecState>>>,
}

impl OneSecEmail {
    pub fn new(client: HttpClient) -> Self {
        Self {
            client,
            state: Arc::new(Mutex::new(None)),
        }
    }

    async fn ensure_csrf(&self) -> Result<(), TempMailError> {
        {
            if self.state.lock().await.is_some() {
                return Ok(());
            }
        }
        let html = self.client.get("https://www.1secemail.com/").await?;
        let csrf_re = Regex::new(r#"<meta name="csrf-token" content="([^"]+)">"#).unwrap();
        let csrf = csrf_re.captures(&html).and_then(|c| c.get(1).map(|m| m.as_str().to_string()))
            .ok_or_else(|| TempMailError::Api("CSRF token not found".into()))?;
        let mut state = self.state.lock().await;
        *state = Some(OneSecState { csrf, email: String::new() });
        Ok(())
    }

    async fn csrf_token(&self) -> String {
        self.state.lock().await.as_ref().unwrap().csrf.clone()
    }

    async fn set_email(&self, name: &str, domain: &str) -> Result<(), TempMailError> {
        self.ensure_csrf().await?;
        let csrf = self.csrf_token().await;
        let body = serde_json::json!({"_token": csrf, "name": name, "domain": domain}).to_string();

        for i in 0..3 {
            let resp = self.client.inner().post(&format!("{}/change", BASE))
                .header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", &csrf)
                .header("x-xsrf-token", &csrf)
                .header("Referer", &format!("{}/", BASE))
                .header("User-Agent", "Mozilla/5.0")
                .body(body.clone())
                .send().await.map_err(TempMailError::Http)?;
            let status = resp.status();
            if status.as_u16() == 429 {
                tokio::time::sleep(std::time::Duration::from_secs([1, 3, 5][i])).await;
                continue;
            }
            if !status.is_success() {
                return Err(TempMailError::Api(format!("HTTP {}", status.as_u16())));
            }
            return Ok(());
        }
        Err(TempMailError::RateLimit)
    }

    async fn post_json_with_csrf<T: for<'de> Deserialize<'de>>(&self, path: &str) -> Result<T, TempMailError> {
        self.ensure_csrf().await?;
        let csrf = self.csrf_token().await;
        let body = serde_json::json!({"_token": csrf}).to_string();

        for i in 0..3 {
            let resp = self.client.inner().post(&format!("{}{}", BASE, path))
                .header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", &csrf)
                .header("x-xsrf-token", &csrf)
                .header("Referer", &format!("{}/", BASE))
                .header("User-Agent", "Mozilla/5.0")
                .body(body.clone())
                .send().await.map_err(TempMailError::Http)?;
            let status = resp.status();
            if status.as_u16() == 429 {
                tokio::time::sleep(std::time::Duration::from_secs([1, 3, 5][i])).await;
                continue;
            }
            if !status.is_success() {
                return Err(TempMailError::Api(format!("HTTP {}", status.as_u16())));
            }
            return resp.json::<T>().await.map_err(TempMailError::Http);
        }
        Err(TempMailError::RateLimit)
    }
}

fn random_name() -> String {
    const CHARS: &[u8] = b"abcdefghijklmnopqrstuvwxyz0123456789";
    let seed = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos() as u64;
    let mut val = seed;
    (0..10).map(|_| {
        val = val.wrapping_mul(6364136223846793005).wrapping_add(1);
        CHARS[(val >> 33) as usize % CHARS.len()] as char
    }).collect()
}

#[async_trait]
impl TempMailProvider for OneSecEmail {
    async fn generate_email(&self) -> Result<String, TempMailError> {
        let name = random_name();
        let domain = DOMAINS[SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos() as usize % DOMAINS.len()];
        self.set_email(&name, domain).await?;
        let email = format!("{}@{}", name, domain);
        let mut state = self.state.lock().await;
        if let Some(s) = state.as_mut() {
            s.email = email.clone();
        }
        Ok(email)
    }

    async fn get_inbox(&self, _email: &str) -> Result<Vec<Message>, TempMailError> {
        #[derive(Deserialize)]
        struct Item {
            id: String,
            #[serde(default)]
            from: Option<String>,
            #[serde(default)]
            from_email: Option<String>,
            #[serde(default)]
            subject: Option<String>,
            #[serde(default, rename = "receivedAt")]
            received_at: Option<String>,
        }
        let items: Vec<Item> = self.post_json_with_csrf("/get_messages").await?;
        Ok(items.into_iter().map(|it| {
            let sender = it.from_email.or(it.from).unwrap_or_else(|| "unknown".into());
            let subject = it.subject.unwrap_or_else(|| "(no subject)".into());
            let date = it.received_at
                .and_then(|s| chrono::NaiveDateTime::parse_from_str(&s, "%Y-%m-%d %H:%M:%S").ok())
                .map(|dt| chrono::DateTime::<chrono::Utc>::from_naive_utc_and_offset(dt, chrono::Utc))
                .unwrap_or_else(chrono::Utc::now);
            Message { id: it.id, sender, subject, date, ..Default::default() }
        }).collect())
    }

    async fn read_message(&self, message_id: &str) -> Result<MessageDetail, TempMailError> {
        self.ensure_csrf().await?;
        let csrf = self.csrf_token().await;
        let url = format!("{}/view/{}", BASE, message_id);

        let html = self.client.inner().get(&url)
            .header("X-CSRF-TOKEN", &csrf)
            .header("Referer", &format!("{}/", BASE))
            .header("User-Agent", "Mozilla/5.0")
            .send().await.map_err(TempMailError::Http)?
            .text().await.map_err(TempMailError::Http)?;

        let text = strip_html(&html);
        let sender = extract_regex(&html, r"From:\s*([^<\n]+)").unwrap_or_else(|| "unknown".into());
        let subject = extract_regex(&html, r"Subject:\s*([^<\n]+)").unwrap_or_else(|| "(no subject)".into());
        let date = extract_regex(&html, r"(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})")
            .and_then(|s| chrono::NaiveDateTime::parse_from_str(&s, "%Y-%m-%d %H:%M:%S").ok())
            .map(|dt| chrono::DateTime::<chrono::Utc>::from_naive_utc_and_offset(dt, chrono::Utc))
            .unwrap_or_else(chrono::Utc::now);

        Ok(MessageDetail {
            message: Message { id: message_id.into(), sender, subject, date, ..Default::default() },
            body_text: Some(text),
            body_html: Some(html),
            attachments: vec![],
            ..Default::default()
        })
    }

    async fn delete_email(&self, _email: &str) -> Result<bool, TempMailError> {
        Ok(true)
    }
}

fn strip_html(html: &str) -> String {
    let re = Regex::new(r"<[^>]+>").unwrap();
    let s = re.replace_all(html, "").to_string().replace('\n', " ");
    let ws = Regex::new(r"\s+").unwrap();
    ws.replace_all(&s, " ").trim().to_string()
}

fn extract_regex(html: &str, pattern: &str) -> Option<String> {
    Regex::new(pattern).ok()
        .and_then(|r| r.captures(html))
        .map(|c| c[1].trim().to_string())
}