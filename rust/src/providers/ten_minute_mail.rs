use async_trait::async_trait;
use regex::Regex;
use std::sync::Arc;
use tokio::sync::Mutex;

use crate::error::TempMailError;
use crate::httpclient::HttpClient;
use crate::models::{Message, MessageDetail};
use crate::providers::TempMailProvider;

const BASE_URL: &str = "https://10minutemail.net";

/// Provider for 10minutemail.net — HTML scraping public mailbox.
#[derive(Debug)]
pub struct TenMinuteMail {
    client: HttpClient,
    email: Arc<Mutex<Option<String>>>,
}

impl TenMinuteMail {
    pub fn new(client: HttpClient) -> Self {
        Self {
            client,
            email: Arc::new(Mutex::new(None)),
        }
    }
}

// Decode Cloudflare obfuscated email address
fn decode_cf_email(hex: &str) -> String {
    if hex.len() < 4 {
        return String::new();
    }
    let k = match u8::from_str_radix(&hex[0..2], 16) {
        Ok(val) => val,
        Err(_) => return String::new(),
    };
    let mut decoded = String::new();
    for i in (2..hex.len()).step_by(2) {
        if i + 2 <= hex.len() {
            if let Ok(val) = u8::from_str_radix(&hex[i..i+2], 16) {
                decoded.push((val ^ k) as char);
            }
        }
    }
    decoded
}

fn strip_html(html: &str) -> String {
    let re = Regex::new(r"<[^>]+>").unwrap();
    re.replace_all(html, "").trim().to_string()
}

#[async_trait]
impl TempMailProvider for TenMinuteMail {
    async fn generate_email(&self) -> Result<String, TempMailError> {
        let html = self.client.get(&format!("{}/", BASE_URL)).await?;
        let re = Regex::new(r#"(?i)id="fe_text"[^>]*value="([^"]+)""#).unwrap();
        let cap = re.captures(&html)
            .ok_or_else(|| TempMailError::Api("No address in response".into()))?;
        let email = cap.get(1).unwrap().as_str().trim().to_string();
        let mut lock = self.email.lock().await;
        *lock = Some(email.clone());
        Ok(email)
    }

    async fn get_inbox(&self, _email: &str) -> Result<Vec<Message>, TempMailError> {
        let html = self.client.get(&format!("{}/mailbox.ajax.php", BASE_URL)).await?;
        
        let re_row = Regex::new(r"(?is)<tr[^>]*>(.*?)</tr>").unwrap();
        let re_cell = Regex::new(r"(?is)<td[^>]*>(.*?)</td>").unwrap();
        let re_cf = Regex::new(r#"(?i)data-cfemail="([^"]+)""#).unwrap();
        let re_title = Regex::new(r#"(?i)title="([^"]+)""#).unwrap();
        let re_mid = Regex::new(r"(?i)mid=([^'&'\s>]+)").unwrap();

        let rows: Vec<_> = re_row.captures_iter(&html).collect();
        if rows.len() <= 1 {
            return Ok(vec![]);
        }

        let mut messages = vec![];
        // Skip header row
        for cap in rows.iter().skip(1) {
            let row_html = cap.get(1).unwrap().as_str();
            let cells: Vec<_> = re_cell.captures_iter(row_html).collect();
            if cells.len() < 3 {
                continue;
            }

            // Sender
            let cell_0 = cells[0].get(1).unwrap().as_str();
            let sender = if let Some(cf_cap) = re_cf.captures(cell_0) {
                decode_cf_email(cf_cap.get(1).unwrap().as_str())
            } else {
                strip_html(cell_0)
            };

            let subject = strip_html(cells[1].get(1).unwrap().as_str());

            // Date
            let cell_2 = cells[2].get(1).unwrap().as_str();
            let date_str = if let Some(title_cap) = re_title.captures(cell_2) {
                title_cap.get(1).unwrap().as_str().to_string()
            } else {
                strip_html(cell_2)
            };

            let mut date_str_utc = date_str;
            if !date_str_utc.to_lowercase().contains("utc") {
                date_str_utc.push_str(" UTC");
            }
            
            let date = chrono::NaiveDateTime::parse_from_str(&date_str_utc, "%Y-%m-%d %H:%M:%S UTC")
                .map(|ndt| chrono::DateTime::<chrono::Utc>::from_naive_utc_and_offset(ndt, chrono::Utc))
                .unwrap_or_else(|_| chrono::Utc::now());

            // Mid
            let id = if let Some(mid_cap) = re_mid.captures(row_html) {
                mid_cap.get(1).unwrap().as_str().to_string()
            } else {
                continue;
            };

            messages.push(Message {
                id,
                sender,
                subject,
                date,
                ..Default::default()
            });
        }

        Ok(messages)
    }

    async fn read_message(&self, message_id: &str) -> Result<MessageDetail, TempMailError> {
        let mid = if let Some(idx) = message_id.find(':') {
            &message_id[..idx]
        } else {
            message_id
        };

        let html = self.client.get(&format!("{}/readmail.html?mid={}", BASE_URL, mid)).await?;

        let re_body = Regex::new(r#"(?is)class="mailinhtml"[^>]*>(.*?)<div[^>]*style="clear:both;""#).unwrap();
        let cap_body = re_body.captures(&html)
            .ok_or(TempMailError::NotFound)?;
        let mut body_html = cap_body.get(1).unwrap().as_str().trim().to_string();

        // Decode CF email obfuscation in body
        let re_cf_link = Regex::new(r#"(?i)<(?:a|span)[^>]*class="__cf_email__"[^>]*data-cfemail="([^"]+)"[^>]*>.*?</(?:a|span)>"#).unwrap();
        body_html = re_cf_link.replace_all(&body_html, |caps: &regex::Captures| {
            decode_cf_email(caps.get(1).unwrap().as_str())
        }).to_string();

        let re_cf_prot = Regex::new(r#"(?i)href="/cdn-cgi/l/email-protection#([^"]+)""#).unwrap();
        body_html = re_cf_prot.replace_all(&body_html, |caps: &regex::Captures| {
            format!("href=\"mailto:{}\"", decode_cf_email(caps.get(1).unwrap().as_str()))
        }).to_string();

        let body_text = strip_html(&body_html);

        // Subject
        let re_sub = Regex::new(r#"(?is)<div class="mail_header">.*?<h2[^>]*>(.*?)</h2>"#).unwrap();
        let subject = if let Some(sub_cap) = re_sub.captures(&html) {
            strip_html(sub_cap.get(1).unwrap().as_str())
        } else {
            String::new()
        };

        // Sender
        let re_from = Regex::new(r#"(?is)<span class="mail_from">(.*?)</span>"#).unwrap();
        let sender = if let Some(from_cap) = re_from.captures(&html) {
            let from_html = from_cap.get(1).unwrap().as_str();
            let re_cf_from = Regex::new(r#"(?i)data-cfemail="([^"]+)""#).unwrap();
            if let Some(cf_from_cap) = re_cf_from.captures(from_html) {
                decode_cf_email(cf_from_cap.get(1).unwrap().as_str())
            } else {
                strip_html(from_html)
            }
        } else {
            String::new()
        };

        Ok(MessageDetail {
            message: Message {
                id: mid.to_string(),
                sender,
                subject,
                date: chrono::Utc::now(),
                ..Default::default()
            },
            body_text: if body_text.is_empty() { None } else { Some(body_text) },
            body_html: if body_html.is_empty() { None } else { Some(body_html) },
            attachments: vec![],
            ..Default::default()
        })
    }

    async fn delete_email(&self, _email: &str) -> Result<bool, TempMailError> {
        let mut e = self.email.lock().await;
        *e = None;
        Ok(true)
    }
}
