use async_trait::async_trait;
use serde::Deserialize;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::error::TempMailError;
use crate::httpclient::HttpClient;
use crate::models::{Message, MessageDetail};
use crate::providers::TempMailProvider;

const BASE: &str = "https://www.nca.my.id";
const DOMAINS: &[&str] = &["ncaori.my.id", "nca.my.id"];

const WORDS: &[&str] = &[
    "swift", "crystal", "storm", "frost", "shadow", "ember", "azure",
    "phantom", "silver", "iron", "crimson", "golden", "neo", "cosmic", "lunar",
    "solar", "dark", "light", "void", "flux",
];

const WORDS2: &[&str] = &[
    "core", "leaf", "forge", "wave", "peak", "gate", "pulse",
    "blade", "shard", "drift", "hive", "node", "edge", "beacon", "nova",
    "storm", "cloud", "moon", "star", "wind",
];

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct NcaoriInboxResponse {
    emails: Vec<NcaoriEmailItem>,
}

#[derive(Debug, Deserialize)]
struct NcaoriEmailItem {
    id: Option<String>,
    sender: Option<String>,
    subject: Option<String>,
    body_text: Option<String>,
    body_html: Option<String>,
    created_at: Option<String>,
}

fn random_name() -> String {
    let seed = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos() as u64;
    let mut val = seed;
    let pick = |arr: &[&str]| {
        val = val.wrapping_mul(6364136223846793005).wrapping_add(1);
        arr[(val >> 33) as usize % arr.len()]
    };
    format!("{}_{}", pick(WORDS), pick(WORDS2))
}

#[derive(Debug)]
pub struct NcaoriMail {
    client: HttpClient,
}

impl NcaoriMail {
    pub fn new(client: HttpClient) -> Self {
        Self { client }
    }
}

#[async_trait]
impl TempMailProvider for NcaoriMail {
    async fn generate_email(&self) -> Result<String, TempMailError> {
        let name = random_name();
        let seed = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos() as u64;
        let domain = DOMAINS[(seed >> 33) as usize % DOMAINS.len()];
        Ok(format!("{}@{}", name, domain))
    }

    async fn get_inbox(&self, email: &str) -> Result<Vec<Message>, TempMailError> {
        let url = format!("{}/api/emails?recipient={}", BASE, urlencoding(&email));
        let body = self.client.get(&url).await?;
        let resp: NcaoriInboxResponse = serde_json::from_str(&body)
            .map_err(|e| TempMailError::Api(format!("parse error: {}", e)))?;

        let msgs: Vec<Message> = resp.emails.into_iter().map(|m| {
            let id = m.id.unwrap_or_default();
            let sender = m.sender.unwrap_or_else(|| "unknown".into());
            let subject = m.subject.unwrap_or_else(|| "(no subject)".into());
            let date = m.created_at
                .and_then(|s| chrono::DateTime::parse_from_rfc3339(&s).ok())
                .map(|d| d.with_timezone(&chrono::Utc))
                .unwrap_or_else(chrono::Utc::now);

            if m.body_text.is_some() || m.body_html.is_some() {
                MessageDetail {
                    message: Message { id, sender, subject, date },
                    body_text: m.body_text,
                    body_html: m.body_html,
                    attachments: Vec::new(),
                }
            } else {
                Message { id, sender, subject, date }
            }
        }).collect();
        Ok(msgs)
    }

    async fn read_message(&self, _message_id: &str) -> Result<MessageDetail, TempMailError> {
        Err(TempMailError::Api(
            "Ncaori Mail+ returns full message in get_inbox(). Use get_inbox() then filter by id.".into(),
        ))
    }

    async fn delete_email(&self, _email: &str) -> Result<bool, TempMailError> {
        Ok(true)
    }
}

fn urlencoding(s: &str) -> String {
    // Simple URL encoding, enough for email addresses
    s.replace('@', "%40")
}
