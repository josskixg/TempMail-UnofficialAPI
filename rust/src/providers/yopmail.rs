use async_trait::async_trait;
use regex::Regex;
use serde::Deserialize;
use std::sync::Arc;
use tokio::sync::Mutex;

use crate::error::TempMailError;
use crate::httpclient::HttpClient;
use crate::models::{Message, MessageDetail};
use crate::providers::TempMailProvider;

/// Configuration for YOPmail provider.
#[derive(Debug, Clone, Default)]
pub struct YopmailConfig {}

#[derive(Debug)]
struct YopmailState {
    username: String,
    yp: String,
    yj: String,
    v: String,
}

#[derive(Debug)]
pub struct Yopmail {
    client: HttpClient,
    state: Arc<Mutex<Option<YopmailState>>>,
}

impl Yopmail {
    pub fn new(client: HttpClient, _config: Option<YopmailConfig>) -> Self {
        Self {
            client,
            state: Arc::new(Mutex::new(None)),
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

#[derive(Deserialize)]
struct YopmailRawMessage {
    id: String,
    from: String,
    subject: String,
    date: String,
}

#[async_trait]
impl TempMailProvider for Yopmail {
    async fn generate_email(&self) -> Result<String, TempMailError> {
        let username = random_alphanumeric(10);

        let html1 = self.client.get("https://yopmail.com/en/").await?;

        let re_yp = Regex::new(r#"name="yp" id="yp" value="([^"]+)""#).unwrap();
        let yp1 = re_yp
            .captures(&html1)
            .and_then(|c| c.get(1))
            .map(|m| m.as_str().to_string())
            .unwrap_or_default();

        let re_v = Regex::new(r#"/ver/([0-9.]+)/webmail\.js"#).unwrap();
        let v = re_v
            .captures(&html1)
            .and_then(|c| c.get(1))
            .map(|m| m.as_str().to_string())
            .unwrap_or_else(|| "5.8".to_string());

        let html2 = self
            .client
            .get(&format!("https://yopmail.com/en/?login={}", username))
            .await?;

        let yp2 = re_yp
            .captures(&html2)
            .and_then(|c| c.get(1))
            .map(|m| m.as_str().to_string())
            .unwrap_or(yp1);

        let form = format!("login={}&yp={}&v={}&ctrl=&id=&btn=Vérifier", username, yp2, v);
        let html3 = self.client.post("https://yopmail.com/en/", &form).await?;

        let re_yj = Regex::new(r#"yj\s*=\s*"([^"]+)""#).unwrap();
        let yj = re_yj
            .captures(&html3)
            .and_then(|c| c.get(1))
            .map(|m| m.as_str().to_string())
            .unwrap_or_default();

        let mut state = self.state.lock().await;
        *state = Some(YopmailState {
            username: username.clone(),
            yp: yp2,
            yj,
            v,
        });

        Ok(format!("{}@yopmail.com", username))
    }

    async fn get_inbox(&self, _email: &str) -> Result<Vec<Message>, TempMailError> {
        let state = self.state.lock().await;
        let state = state.as_ref().ok_or_else(|| {
            TempMailError::Api("Must call generate_email first".into())
        })?;

        let url = format!(
            "https://yopmail.com/en/ver/{}?id={}&yp={}&v={}&d=&p=&w=&ad=&r=&c=&m=&sf=&st=&s=",
            state.yj, state.username, state.yp, state.v
        );
        let html = self.client.get(&url).await?;

        let re_mail = Regex::new(
            r#"<div\s+id="m"[^>]*>\s*<span[^>]*>.*?</span>\s*<span[^>]*>\s*<a[^>]*bm[^>]*>(.*?)</a>.*?<span[^>]*>(.*?)</span>\s*<span[^>]*>(.*?)</span>"#,
        )
        .unwrap();

        let messages: Vec<Message> = re_mail
            .captures_iter(&html)
            .map(|cap| Message {
                id: cap.get(2).map(|m| m.as_str().to_string()).unwrap_or_default(),
                sender: cap.get(1).map(|m| m.as_str().to_string()).unwrap_or_default(),
                subject: cap.get(3).map(|m| m.as_str().to_string()).unwrap_or_default(),
                date: chrono::Utc::now(),
            })
            .collect();

        Ok(messages)
    }

    async fn read_message(&self, message_id: &str) -> Result<MessageDetail, TempMailError> {
        let state = self.state.lock().await;
        let state = state.as_ref().ok_or_else(|| {
            TempMailError::Api("Must call generate_email first".into())
        })?;

        let url = format!(
            "https://yopmail.com/en/ver/{}?id={}&m={}&yp={}&v={}",
            state.yj, state.username, message_id, state.yp, state.v
        );
        let html = self.client.get(&url).await?;

        let re_mail = Regex::new(r#"<div[^>]+id="mail"[^>]*>([\s\S]*?)</div>"#).unwrap();
        let body_html = re_mail
            .captures(&html)
            .and_then(|c| c.get(1))
            .map(|m| m.as_str().to_string())
            .or(Some(html));

        Ok(MessageDetail {
            message: Message {
                id: message_id.to_string(),
                sender: String::new(),
                subject: String::new(),
                date: chrono::Utc::now(),
            },
            body_text: None,
            body_html,
            attachments: vec![],
        })
    }

    async fn delete_email(&self, _email: &str) -> Result<bool, TempMailError> {
        // No delete API for YOPmail
        Ok(true)
    }
}

fn parse_date(s: &str) -> chrono::DateTime<chrono::Utc> {
    chrono::NaiveTime::parse_from_str(s, "%H:%M")
        .ok()
        .and_then(|t| {
            let now = chrono::Utc::now();
            Some(now.date_naive().and_time(t).and_utc())
        })
        .unwrap_or_else(chrono::Utc::now)
}
