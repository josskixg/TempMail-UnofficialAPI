use async_trait::async_trait;
use serde::{Deserialize, Serialize};

use crate::error::TempMailError;
use crate::httpclient::HttpClient;
use crate::models::{Message, MessageDetail};
use crate::providers::TempMailProvider;

const BASE_URL: &str = "https://api.mail.tm";

/// Configuration for mail.tm provider.
#[derive(Debug, Clone, Default)]
pub struct MailTmConfig {
    /// Optional password for the account (auto-generated if None).
    pub password: Option<String>,
}

#[derive(Debug, Clone)]
pub struct MailTm {
    client: HttpClient,
    config: MailTmConfig,
    /// Cached auth token + account id after generation.
    state: std::sync::Arc<tokio::sync::Mutex<Option<MailTmState>>>,
}

#[derive(Debug, Clone)]
struct MailTmState {
    token: String,
    email: String,
}

impl MailTm {
    pub fn new(client: HttpClient, config: Option<MailTmConfig>) -> Self {
        Self {
            client,
            config: config.unwrap_or_default(),
            state: std::sync::Arc::new(tokio::sync::Mutex::new(None)),
        }
    }

    async fn generate_email_core(&self) -> Result<String, TempMailError> {
        #[derive(Deserialize)]
        struct DomainList {
            #[serde(rename = "hydra:member")]
            members: Vec<Domain>,
        }
        #[derive(Deserialize)]
        struct Domain {
            domain: String,
        }

        let domains: DomainList = self
            .client
            .get_json(&format!("{}/domains", BASE_URL))
            .await?;

        let domain = domains
            .members
            .first()
            .map(|d| d.domain.as_str())
            .unwrap_or("mail.tm");

        let random_user: String = (0..10)
            .map(|_| fastrand_alphanumeric())
            .collect();
        let email = format!("{}@{}", random_user, domain);
        let password = self
            .config
            .password
            .clone()
            .unwrap_or_else(|| "TempMail123!".to_string());

        let account: AccountResponse = self
            .client
            .post_json(
                &format!("{}/accounts", BASE_URL),
                &AccountCreate {
                    address: &email,
                    password: &password,
                },
            )
            .await
            .map_err(|e| TempMailError::Api(format!("Account creation failed: {}", e)))?;

        #[derive(Serialize)]
        struct LoginReq<'a> {
            address: &'a str,
            password: &'a str,
        }
        let token_resp: TokenResponse = self
            .client
            .post_json(
                &format!("{}/token", BASE_URL),
                &LoginReq {
                    address: &email,
                    password: &password,
                },
            )
            .await?;

        let mut state = self.state.lock().await;
        *state = Some(MailTmState {
            token: token_resp.token,
            email: account.address.clone(),
        });

        Ok(account.address)
    }
}

#[derive(Serialize)]
struct AccountCreate<'a> {
    address: &'a str,
    password: &'a str,
}

#[derive(Deserialize)]
struct AccountResponse {
    id: String,
    address: String,
}

#[derive(Deserialize)]
struct TokenResponse {
    token: String,
}

#[derive(Deserialize)]
struct MailTmMessageList {
    #[serde(rename = "hydra:member")]
    members: Vec<MailTmRawMessage>,
}

#[derive(Deserialize)]
struct MailTmRawMessage {
    id: String,
    #[serde(rename = "from")]
    from_info: MailTmAddress,
    subject: Option<String>,
    #[serde(rename = "createdAt")]
    created_at: String,
    #[serde(rename = "intro")]
    _intro: Option<String>,
    text: Option<String>,
    html: Option<Vec<String>>,
    #[serde(rename = "hasAttachments")]
    _has_attachments: Option<bool>,
    attachments: Option<Vec<serde_json::Value>>,
}

#[derive(Deserialize)]
struct MailTmAddress {
    address: String,
}

fn parse_date(s: &str) -> chrono::DateTime<chrono::Utc> {
    chrono::DateTime::parse_from_rfc3339(s)
        .map(|dt| dt.with_timezone(&chrono::Utc))
        .unwrap_or_else(|_| chrono::Utc::now())
}

#[async_trait]
impl TempMailProvider for MailTm {
    async fn generate_email(&self) -> Result<String, TempMailError> {
        self.generate_email_core().await
    }

    async fn get_inbox(&self, _email: &str) -> Result<Vec<Message>, TempMailError> {
        let state = self.state.lock().await;
        let state = state.as_ref().ok_or_else(|| {
            TempMailError::Api("Must call generate_email first".into())
        })?;

        // ponytail: HttpClient.get_json doesn't support custom headers;
        // for auth we use inner() directly. Anti-429 is handled by HttpClient.get_json
        // when we route through it. Here we accept the tradeoff for Bearer tokens.
        let list: MailTmMessageList = self
            .client
            .inner()
            .get(format!("{}/messages", BASE_URL))
            .header("Authorization", format!("Bearer {}", state.token))
            .send()
            .await?
            .json()
            .await?;

        Ok(list
            .members
            .into_iter()
            .map(|m| Message {
                id: m.id,
                sender: m.from_info.address,
                subject: m.subject.unwrap_or_default(),
                date: parse_date(&m.created_at), preview: String::new(), has_attachments: false })
            .collect())
    }

    async fn read_message(&self, message_id: &str) -> Result<MessageDetail, TempMailError> {
        let state = self.state.lock().await;
        let state = state.as_ref().ok_or_else(|| {
            TempMailError::Api("Must call generate_email first".into())
        })?;

        let raw: MailTmRawMessage = self
            .client
            .inner()
            .get(format!("{}/messages/{}", BASE_URL, message_id))
            .header("Authorization", format!("Bearer {}", state.token))
            .send()
            .await?
            .json()
            .await?;

        let body_html = raw.html.and_then(|h| h.into_iter().next());

        Ok(MessageDetail {
            message: Message {
                id: raw.id,
                sender: raw.from_info.address,
                subject: raw.subject.unwrap_or_default(),
                date: parse_date(&raw.created_at), preview: String::new(), has_attachments: false },
            body_text: raw.text,
            body_html,
            attachments: raw.attachments.unwrap_or_default(), ..Default::default()         })
    }

    async fn delete_email(&self, _email: &str) -> Result<bool, TempMailError> {
        let mut state = self.state.lock().await;
        if state.is_none() {
            return Err(TempMailError::Api("Must call generate_email first".into()));
        }
        // ponytail: mail.tm has no public account deletion endpoint
        *state = None;
        Ok(true)
    }
}

fn fastrand_alphanumeric() -> char {
    const CHARSET: &[u8] = b"abcdefghijklmnopqrstuvwxyz0123456789";
    CHARSET[std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| (d.subsec_nanos() % CHARSET.len() as u32) as usize)
        .unwrap_or(0)] as char
}
