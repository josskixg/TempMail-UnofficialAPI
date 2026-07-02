use async_trait::async_trait;
use regex::Regex;
use std::sync::Arc;
use tokio::sync::Mutex;

use crate::error::TempMailError;
use crate::httpclient::HttpClient;
use crate::models::{Message, MessageDetail};
use crate::providers::TempMailProvider;

const BASE_URL: &str = "https://generator.email";
const SITE_KEY: &str = "generator";
const UA: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

/// Provider for generator.email — surl cookie + channel URL, HTML scraping.
#[derive(Debug)]
pub struct GeneratorEmail {
    client: HttpClient,
    state: Arc<Mutex<Option<SurlState>>>,
}

#[derive(Debug, Clone)]
struct SurlState {
    #[allow(dead_code)]
    email: String,
    domain: String,
    username: String,
}

impl GeneratorEmail {
    pub fn new(client: HttpClient) -> Self {
        Self {
            client,
            state: Arc::new(Mutex::new(None)),
        }
    }

    async fn get_domains(&self) -> Result<Vec<String>, TempMailError> {
        let ch = 1 + rand_below(9);
        let html = self
            .client
            .get(&format!("{}/channel{}/", BASE_URL, ch))
            .await?;
        let re = Regex::new(r#"<option[^>]+value="([^"]+)""#).unwrap();
        let mut domains: Vec<String> = re
            .captures_iter(&html)
            .map(|c| c[1].trim().to_string())
            .filter(|v| v.contains('.') && !v.contains(' ') && !v.contains('@'))
            .collect();
        if domains.is_empty() {
            let re2 = Regex::new(r"\b([a-z0-9-]+\.[a-z]{2,}(?:\.[a-z]{2,})?)\b").unwrap();
            domains = re2
                .captures_iter(&html)
                .map(|c| c[1].to_string())
                .filter(|d| !d.contains(SITE_KEY))
                .collect();
        }
        domains.sort();
        domains.dedup();
        if domains.is_empty() {
            return Err(TempMailError::Api("No domains found on page".into()));
        }
        Ok(domains)
    }

    async fn fetch_inbox_html(&self) -> Result<String, TempMailError> {
        let st = self
            .state
            .lock()
            .await
            .clone()
            .ok_or_else(|| TempMailError::Api("Must call generate_email first".into()))?;
        let ch = 1 + rand_below(9);
        let url = format!("{}/channel{}/", BASE_URL, ch);
        let resp = self
            .client
            .inner()
            .get(&url)
            .header("Cookie", format!("surl={}/{}", st.domain, st.username))
            .header("User-Agent", UA)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .send()
            .await
            .map_err(TempMailError::Http)?;
        let status = resp.status();
        if !status.is_success() {
            return Err(TempMailError::Api(format!(
                "Failed to load inbox: HTTP {}",
                status.as_u16()
            )));
        }
        resp.text().await.map_err(TempMailError::Http)
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

fn rand_below(n: usize) -> usize {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| (d.subsec_nanos() as usize) % n)
        .unwrap_or(0)
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
impl TempMailProvider for GeneratorEmail {
    async fn generate_email(&self) -> Result<String, TempMailError> {
        let domains = self.get_domains().await?;
        let domain = domains[rand_below(domains.len())].clone();
        let username = random_alphanumeric(10);
        let email = format!("{}@{}", username, domain);
        let mut state = self.state.lock().await;
        *state = Some(SurlState {
            email: email.clone(),
            domain,
            username,
        });
        Ok(email)
    }

    async fn get_inbox(&self, _email: &str) -> Result<Vec<Message>, TempMailError> {
        let html = self.fetch_inbox_html().await?;
        let re_anchor = Regex::new(r#"(?s)<a([^>]*list-group-item[^>]*)>(.*?)</a>"#).unwrap();
        let re_href = Regex::new(r#"href="([^"]*)""#).unwrap();
        let re_from = Regex::new(r#"(?s)<div[^>]*class="[^"]*from[^"]*"[^>]*>(.*?)</div>"#).unwrap();
        let re_subj = Regex::new(r#"(?s)<div[^>]*class="[^"]*subj[^"]*"[^>]*>(.*?)</div>"#).unwrap();
        let re_time = Regex::new(r#"(?s)<div[^>]*class="[^"]*time[^"]*"[^>]*>(.*?)</div>"#).unwrap();

        let mut messages = Vec::new();
        for cap in re_anchor.captures_iter(&html) {
            let attrs = cap.get(1).map(|m| m.as_str()).unwrap_or("");
            let inner = cap.get(2).map(|m| m.as_str()).unwrap_or("");
            let href = re_href
                .captures(attrs)
                .and_then(|c| c.get(1))
                .map(|m| m.as_str())
                .unwrap_or("");
            let msg_id = href.rsplit('/').next().unwrap_or("").to_string();
            if msg_id.is_empty() || msg_id.len() < 10 {
                continue;
            }
            let sender = re_from
                .captures(inner)
                .and_then(|c| c.get(1))
                .map(|m| strip_html(m.as_str()))
                .unwrap_or_default();
            let subject = re_subj
                .captures(inner)
                .and_then(|c| c.get(1))
                .map(|m| strip_html(m.as_str()))
                .unwrap_or_default();
            let time_str = re_time
                .captures(inner)
                .and_then(|c| c.get(1))
                .map(|m| strip_html(m.as_str()))
                .unwrap_or_default();
            messages.push(Message {
                id: msg_id,
                sender,
                subject,
                date: parse_date(&time_str), preview: String::new(), has_attachments: false });
        }
        Ok(messages)
    }

    async fn read_message(&self, message_id: &str) -> Result<MessageDetail, TempMailError> {
        let st = self
            .state
            .lock()
            .await
            .clone()
            .ok_or_else(|| TempMailError::Api("Must call generate_email first".into()))?;
        let url = format!("{}/{}/{}/{}", BASE_URL, st.domain, st.username, message_id);
        let resp = self
            .client
            .inner()
            .get(&url)
            .header("Cookie", format!("surl={}/{}", st.domain, st.username))
            .header("User-Agent", UA)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            )
            .send()
            .await
            .map_err(TempMailError::Http)?;
        if !resp.status().is_success() {
            return Err(TempMailError::NotFound);
        }
        let html = resp.text().await.map_err(TempMailError::Http)?;

        let re_msg = Regex::new(r#"(?s)<div[^>]*id="message"[^>]*>(.*?)</div>"#).unwrap();
        let body_html = re_msg
            .captures(&html)
            .and_then(|c| c.get(1))
            .map(|m| m.as_str().to_string())
            .unwrap_or_else(|| html.clone());
        let body_text = strip_html(&body_html);

        let re_from = Regex::new(r#"(?s)<div[^>]*class="[^"]*from_div[^"]*"[^>]*>(.*?)</div>"#).unwrap();
        let re_subj = Regex::new(r#"(?s)<div[^>]*class="[^"]*subj_div[^"]*"[^>]*>(.*?)</div>"#).unwrap();
        let re_time = Regex::new(r#"(?s)<div[^>]*class="[^"]*time_div[^"]*"[^>]*>(.*?)</div>"#).unwrap();
        let sender = re_from.captures(&html).and_then(|c| c.get(1)).map(|m| strip_html(m.as_str())).unwrap_or_default();
        let subject = re_subj.captures(&html).and_then(|c| c.get(1)).map(|m| strip_html(m.as_str())).unwrap_or_default();
        let time_str = re_time.captures(&html).and_then(|c| c.get(1)).map(|m| strip_html(m.as_str())).unwrap_or_default();

        Ok(MessageDetail {
            message: Message {
                id: message_id.to_string(),
                sender,
                subject,
                date: parse_date(&time_str), preview: String::new(), has_attachments: false },
            body_text: Some(body_text).filter(|s| !s.is_empty()),
            body_html: Some(body_html).filter(|s| !s.is_empty()),
            attachments: vec![], ..Default::default()         })
    }

    async fn delete_email(&self, _email: &str) -> Result<bool, TempMailError> {
        let mut state = self.state.lock().await;
        *state = None;
        Ok(true)
    }
}
