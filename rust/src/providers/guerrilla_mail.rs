use async_trait::async_trait;
use serde::Deserialize;

use crate::error::TempMailError;
use crate::httpclient::HttpClient;
use crate::models::{Message, MessageDetail};
use crate::providers::TempMailProvider;

const BASE_URL: &str = "https://api.guerrillamail.com/ajax.php";

/// Configuration for guerrillamail.com provider.
#[derive(Debug, Clone, Default)]
pub struct GuerrillaMailConfig {
    /// Preferred language (default: "en").
    pub lang: String,
}

#[derive(Debug, Clone)]
pub struct GuerrillaMail {
    client: HttpClient,
    config: GuerrillaMailConfig,
    session_id: std::sync::Arc<tokio::sync::Mutex<Option<String>>>,
}

impl GuerrillaMail {
    pub fn new(client: HttpClient, config: Option<GuerrillaMailConfig>) -> Self {
        Self {
            client,
            config: config.unwrap_or(GuerrillaMailConfig {
                lang: "en".to_string(),
            }),
            session_id: std::sync::Arc::new(tokio::sync::Mutex::new(None)),
        }
    }
}

#[derive(Deserialize)]
struct EmailAddrResponse {
    email_addr: String,
    sid_token: String,
}

#[derive(Deserialize)]
struct InboxResponse {
    list: Option<Vec<RawGuerrillaMessage>>,
}

#[derive(Deserialize)]
struct RawGuerrillaMessage {
    mail_id: String,
    mail_from: String,
    mail_subject: String,
    mail_date: String,
    mail_excerpt: Option<String>,
    mail_body: Option<String>,
    content_type: Option<String>,
}

#[derive(Deserialize)]
struct EmailBodyResponse {
    mail_id: String,
    mail_from: String,
    mail_subject: String,
    mail_date: String,
    mail_body: Option<String>,
    content_type: Option<String>,
}

fn parse_date(s: &str) -> chrono::DateTime<chrono::Utc> {
    chrono::NaiveDateTime::parse_from_str(s, "%Y-%m-%d %H:%M:%S")
        .map(|dt| dt.and_utc())
        .unwrap_or_else(|_| chrono::Utc::now())
}

#[async_trait]
impl TempMailProvider for GuerrillaMail {
    async fn generate_email(&self) -> Result<String, TempMailError> {
        let resp: EmailAddrResponse = self
            .client
            .get_json(&format!(
                "{}?f=get_email_address&lang={}",
                BASE_URL, self.config.lang
            ))
            .await?;

        let mut sid = self.session_id.lock().await;
        *sid = Some(resp.sid_token);

        Ok(resp.email_addr)
    }

    async fn get_inbox(&self, _email: &str) -> Result<Vec<Message>, TempMailError> {
        let sid = self.session_id.lock().await;
        let sid = sid
            .as_ref()
            .ok_or_else(|| TempMailError::Api("Must call generate_email first".into()))?
            .clone();

        let inbox: InboxResponse = self
            .client
            .get_json(&format!(
                "{}?f=get_inbox&lang={}&sid_token={}",
                BASE_URL, self.config.lang, sid
            ))
            .await?;

        let list = inbox.list.unwrap_or_default();
        Ok(list
            .into_iter()
            .map(|m| Message {
                id: m.mail_id,
                sender: m.mail_from,
                subject: m.mail_subject,
                date: parse_date(&m.mail_date), preview: String::new(), has_attachments: false })
            .collect())
    }

    async fn read_message(&self, message_id: &str) -> Result<MessageDetail, TempMailError> {
        let sid = self.session_id.lock().await;
        let sid = sid
            .as_ref()
            .ok_or_else(|| TempMailError::Api("Must call generate_email first".into()))?
            .clone();

        let resp: EmailBodyResponse = self
            .client
            .get_json(&format!(
                "{}?f=fetch_email&lang={}&sid_token={}&email_id={}",
                BASE_URL, self.config.lang, sid, message_id
            ))
            .await?;

        let (body_text, body_html) = match resp.content_type.as_deref() {
            Some("html") => (None, resp.mail_body),
            _ => (resp.mail_body, None),
        };

        Ok(MessageDetail {
            message: Message {
                id: resp.mail_id,
                sender: resp.mail_from,
                subject: resp.mail_subject,
                date: parse_date(&resp.mail_date), preview: String::new(), has_attachments: false },
            body_text,
            body_html,
            attachments: vec![], ..Default::default()         })
    }

    async fn delete_email(&self, email: &str) -> Result<bool, TempMailError> {
        let sid = self.session_id.lock().await;
        let sid = sid
            .as_ref()
            .ok_or_else(|| TempMailError::Api("Must call generate_email first".into()))?;

        #[derive(Deserialize)]
        struct DelResponse {
            is_disabled: Option<bool>,
        }

        let resp: DelResponse = self
            .client
            .get_json(&format!(
                "{}?f=del_email&email_ids[]={}&sid_token={}",
                BASE_URL, email, sid
            ))
            .await
            .unwrap_or(DelResponse {
                is_disabled: Some(false),
            });

        Ok(resp.is_disabled.unwrap_or(false))
    }
}
