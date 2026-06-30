use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::Mutex;

use crate::error::TempMailError;
use crate::httpclient::HttpClient;
use crate::models::{Message, MessageDetail};
use crate::providers::TempMailProvider;

const PADDLE_OCR_URL: &str = "https://mamamacjdjj-padle-predict.hf.space/predict";

/// PaddleOCR solver — 3 retries via HuggingFace space.
pub async fn paddle_ocr_solver(img_bytes: &[u8]) -> Option<String> {
    let ocr_client = reqwest::Client::new();
    let boundary = format!("DropmailOCR{}", chrono::Utc::now().timestamp_millis());
    for _ in 0..3 {
        let body = build_multipart(&boundary, img_bytes);
        let content_type = format!("multipart/form-data; boundary={}", boundary);
        let ocr_resp = match ocr_client
            .post(PADDLE_OCR_URL)
            .header("Content-Type", content_type.clone())
            .body(body)
            .send()
            .await
        {
            Ok(r) => r,
            Err(_) => continue,
        };
        let ocr_data: serde_json::Value = match ocr_resp.json().await {
            Ok(v) => v,
            Err(_) => continue,
        };
        if let Some(results) = ocr_data["results"].as_array() {
            if let Some(best) = results.first() {
                let confidence = best["confidence"].as_f64().unwrap_or(0.0);
                if confidence >= 0.7 {
                    if let Some(t) = best["text"].as_str() {
                        let trimmed = t.trim().to_string();
                        if !trimmed.is_empty() {
                            return Some(trimmed);
                        }
                    }
                }
            }
        }
    }
    None
}

/// Configuration for Dropmail provider.
#[derive(Debug, Clone, Default)]
pub struct DropmailConfig {}

#[derive(Debug)]
struct DropmailState {
    token: String,
    session_id: String,
    address_id: String,
    address: String,
}

pub struct Dropmail {
    client: HttpClient,
    state: Arc<Mutex<Option<DropmailState>>>,
    captcha_solvers: Vec<Arc<dyn Fn(&[u8]) -> Option<String> + Send + Sync>>,
}

impl std::fmt::Debug for Dropmail {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Dropmail")
            .field("state", &self.state)
            .field("captcha_solvers", &self.captcha_solvers.len())
            .finish()
    }
}

impl Dropmail {
    pub fn new(client: HttpClient, _config: Option<DropmailConfig>) -> Self {
        Self {
            client,
            state: Arc::new(Mutex::new(None)),
            captcha_solvers: Vec::new(),
        }
    }

    pub fn with_captcha_solvers(
        mut self,
        solvers: Vec<Arc<dyn Fn(&[u8]) -> Option<String> + Send + Sync>>,
    ) -> Self {
        self.captcha_solvers = solvers;
        self
    }
}

#[derive(Serialize)]
struct GenerateTokenReq<'a> {
    r#type: &'a str,
    lifetime: &'a str,
}

#[derive(Deserialize)]
struct GenerateTokenRes {
    token: Option<String>,
}

#[derive(Deserialize)]
struct CaptchaField {
    v: Option<String>,
    nonce: Option<String>,
    key: Option<String>,
    #[serde(rename = "_sig")]
    sig: Option<String>,
}

#[derive(Deserialize)]
struct TokenOrCaptchaRes {
    token: Option<String>,
    captcha: Option<CaptchaField>,
}

#[derive(Serialize)]
struct GraphqlReq<'a> {
    query: &'a str,
}

#[derive(Deserialize)]
struct GraphqlRes<T> {
    data: Option<T>,
    errors: Option<Vec<GraphqlError>>,
}

#[derive(Deserialize)]
struct GraphqlError {
    message: String,
}

#[derive(Deserialize)]
struct IntroduceSessionData {
    #[serde(rename = "introduceSession")]
    introduce_session: IntroduceSession,
}

#[derive(Deserialize)]
struct IntroduceSession {
    id: String,
    addresses: Vec<DropmailAddress>,
}

#[derive(Deserialize)]
struct DropmailAddress {
    id: String,
    address: String,
}

#[derive(Deserialize)]
struct SessionData {
    session: Option<Session>,
}

#[derive(Deserialize)]
struct Session {
    mails: Option<Vec<DropmailMail>>,
}

#[derive(Deserialize, Clone)]
struct DropmailMail {
    id: String,
    #[serde(rename = "fromAddr")]
    from_addr: Option<String>,
    #[serde(rename = "headerSubject")]
    header_subject: Option<String>,
    #[serde(rename = "receivedAt")]
    received_at: Option<String>,
    text: Option<String>,
    html: Option<String>,
    attachments: Option<Vec<serde_json::Value>>,
}

fn parse_date(s: &str) -> chrono::DateTime<chrono::Utc> {
    chrono::DateTime::parse_from_rfc3339(s)
        .map(|dt| dt.with_timezone(&chrono::Utc))
        .unwrap_or_else(|_| chrono::Utc::now())
}

/// Build a minimal multipart/form-data body for a single file field (no reqwest multipart feature needed).
fn build_multipart(boundary: &str, img_bytes: &[u8]) -> Vec<u8> {
    let mut body = Vec::new();
    let header = format!(
        "--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"cap.png\"\r\nContent-Type: image/png\r\n\r\n"
    );
    body.extend_from_slice(header.as_bytes());
    body.extend_from_slice(img_bytes);
    let footer = format!("\r\n--{boundary}--\r\n");
    body.extend_from_slice(footer.as_bytes());
    body
}

/// Percent-encode a string for use as a query parameter value.
fn pct_encode(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for b in s.bytes() {
        if b.is_ascii_alphanumeric() || matches!(b, b'-' | b'.' | b'_' | b'~') {
            out.push(b as char);
        } else {
            out.push_str(&format!("%{:02X}", b));
        }
    }
    out
}

/// Attempt captcha→90d flow. Returns token on success, None on any failure.
/// Uses a cookie-enabled reqwest::Client for Dropmail (same session).
async fn solve_captcha_and_get_token(
    captcha: CaptchaField,
    dropmail_client: &reqwest::Client,
    solvers: &[Arc<dyn Fn(&[u8]) -> Option<String> + Send + Sync>],
) -> Option<String> {
    let v     = captcha.v.unwrap_or_else(|| "3".into());
    let nonce = captcha.nonce.unwrap_or_default();
    let key   = captcha.key.unwrap_or_default();
    let sig   = captcha.sig.unwrap_or_default();

    // Step 2: download captcha image — same cookie session
    let img_url = format!(
        "https://dropmail.me/captcha/image.png?_r=0&v={}&nonce={}&key={}&_sig={}",
        pct_encode(&v), pct_encode(&nonce), pct_encode(&key), pct_encode(&sig),
    );
    let img_resp = dropmail_client.get(&img_url).send().await.ok()?;
    if !img_resp.status().is_success() { return None; }
    let img_bytes = img_resp.bytes().await.ok()?;

    // Step 3: solve via solver chain
    // Try user-provided sync solvers first
    let ocr_text = solvers.iter().find_map(|solver| solver(&img_bytes));
    let ocr_text = match ocr_text {
        Some(t) => t,
        None => {
            // Fall back to built-in async PaddleOCR
            paddle_ocr_solver(&img_bytes).await?
        }
    };

    // Step 4: submit solution — same cookie session, form-encoded
    let mut form_params = HashMap::new();
    form_params.insert("response", ocr_text.as_str());
    form_params.insert("v",        v.as_str());
    form_params.insert("nonce",    nonce.as_str());
    form_params.insert("key",      key.as_str());
    form_params.insert("_sig",     sig.as_str());

    let sol_resp = dropmail_client
        .post("https://dropmail.me/captcha/solution")
        .form(&form_params)
        .send()
        .await
        .ok()?;
    let sol_data: serde_json::Value = sol_resp.json().await.ok()?;
    if sol_data["result"].as_str() != Some("correct") {
        return None;
    }

    // Step 5: retry token generation with 90d — same cookie session
    let token_body = GenerateTokenReq { r#type: "af", lifetime: "90d" };
    let token_resp = dropmail_client
        .post("https://dropmail.me/api/token/generate")
        .json(&token_body)
        .send()
        .await
        .ok()?;
    if !token_resp.status().is_success() { return None; }
    let token_data: GenerateTokenRes = token_resp.json().await.ok()?;
    token_data.token
}

#[async_trait]
impl TempMailProvider for Dropmail {
    async fn generate_email(&self) -> Result<String, TempMailError> {
        // Cookie-enabled client for the entire Dropmail session
        let dropmail_client = reqwest::Client::builder()
            .cookie_store(true)
            .build()
            .map_err(|e| TempMailError::Api(format!("client build failed: {}", e)))?;

        // Step 1: try 1d token
        let resp_1d = dropmail_client
            .post("https://dropmail.me/api/token/generate")
            .json(&GenerateTokenReq { r#type: "af", lifetime: "1d" })
            .send()
            .await
            .map_err(|e| TempMailError::Api(format!("token request failed: {}", e)))?;

        let status = resp_1d.status();
        let token = if status.is_success() {
            let data: GenerateTokenRes = resp_1d
                .json()
                .await
                .map_err(|e| TempMailError::Api(format!("token parse failed: {}", e)))?;
            data.token.ok_or_else(|| TempMailError::Api("no token in response".into()))?
        } else if status.as_u16() == 402 {
            // Captcha required — attempt 90d flow
            let body: TokenOrCaptchaRes = resp_1d.json().await
                .unwrap_or(TokenOrCaptchaRes { token: None, captcha: None });

            let solved = if let Some(captcha) = body.captcha {
                solve_captcha_and_get_token(captcha, &dropmail_client, &self.captcha_solvers).await
            } else {
                None
            };

            if let Some(t) = solved {
                t
            } else {
                // Fallback: get 1d token via the main HttpClient
                eprintln!("Dropmail: captcha solve failed, falling back to 1d token");
                let fallback: GenerateTokenRes = self
                    .client
                    .post_json(
                        "https://dropmail.me/api/token/generate",
                        &GenerateTokenReq { r#type: "af", lifetime: "1d" },
                    )
                    .await
                    .map_err(|e| TempMailError::Api(format!("1d fallback failed: {}", e)))?;
                fallback.token.ok_or_else(|| TempMailError::Api("no token in fallback".into()))?
            }
        } else {
            return Err(TempMailError::Api(format!(
                "token generation failed: HTTP {}",
                status.as_u16()
            )));
        };

        let gql_url = format!("https://dropmail.me/api/graphql/{}", token);
        let mutation = r#"mutation { introduceSession { id addresses { id address } } }"#;
        let gql_req = GraphqlReq { query: mutation };
        let gql_res: GraphqlRes<IntroduceSessionData> = self
            .client
            .post_json(&gql_url, &gql_req)
            .await
            .map_err(|e| TempMailError::Api(format!("GraphQL introduceSession failed: {}", e)))?;

        if let Some(errors) = gql_res.errors {
            if !errors.is_empty() {
                return Err(TempMailError::Api(format!("GraphQL error: {}", errors[0].message)));
            }
        }

        let session_data = gql_res
            .data
            .ok_or_else(|| TempMailError::Api("no data in GraphQL response".into()))?;
        let session = session_data.introduce_session;
        let address = session
            .addresses
            .into_iter()
            .next()
            .ok_or_else(|| TempMailError::Api("no addresses returned".into()))?;

        let mut state = self.state.lock().await;
        *state = Some(DropmailState {
            token,
            session_id: session.id,
            address_id: address.id,
            address: address.address.clone(),
        });

        Ok(address.address)
    }

    async fn get_inbox(&self, _email: &str) -> Result<Vec<Message>, TempMailError> {
        let state = self.state.lock().await;
        let state = state.as_ref().ok_or_else(|| {
            TempMailError::Api("Must call generate_email first".into())
        })?;

        let query = format!(
            r#"query {{ session(id: "{}") {{ mails {{ id fromAddr headerSubject receivedAt }} }} }}"#,
            state.session_id
        );
        let gql_req = GraphqlReq { query: &query };
        let gql_res: GraphqlRes<SessionData> = self
            .client
            .post_json(
                &format!("https://dropmail.me/api/graphql/{}", state.token),
                &gql_req,
            )
            .await
            .map_err(|e| TempMailError::Api(format!("GraphQL getInbox failed: {}", e)))?;

        let mails = gql_res
            .data
            .and_then(|d| d.session)
            .and_then(|s| s.mails)
            .unwrap_or_default();

        Ok(mails
            .into_iter()
            .map(|m| Message {
                id: m.id,
                sender: m.from_addr.unwrap_or_default(),
                subject: m.header_subject.unwrap_or_default(),
                date: m.received_at.as_deref().map(parse_date).unwrap_or_else(chrono::Utc::now),
            })
            .collect())
    }

    async fn read_message(&self, message_id: &str) -> Result<MessageDetail, TempMailError> {
        let state = self.state.lock().await;
        let state = state.as_ref().ok_or_else(|| {
            TempMailError::Api("Must call generate_email first".into())
        })?;

        let query = format!(
            r#"query {{ session(id: "{}") {{ mails {{ id fromAddr headerSubject receivedAt text html attachments }} }} }}"#,
            state.session_id
        );
        let gql_req = GraphqlReq { query: &query };
        let gql_res: GraphqlRes<SessionData> = self
            .client
            .post_json(
                &format!("https://dropmail.me/api/graphql/{}", state.token),
                &gql_req,
            )
            .await
            .map_err(|e| TempMailError::Api(format!("GraphQL readMessage failed: {}", e)))?;

        let mails = gql_res
            .data
            .and_then(|d| d.session)
            .and_then(|s| s.mails)
            .unwrap_or_default();

        mails
            .into_iter()
            .find(|m| m.id == message_id)
            .map(|mail| MessageDetail {
                message: Message {
                    id: mail.id,
                    sender: mail.from_addr.unwrap_or_default(),
                    subject: mail.header_subject.unwrap_or_default(),
                    date: mail.received_at.as_deref().map(parse_date).unwrap_or_else(chrono::Utc::now),
                },
                body_text: mail.text,
                body_html: mail.html,
                attachments: mail.attachments.unwrap_or_default(),
            })
            .ok_or(TempMailError::NotFound)
    }

    async fn delete_email(&self, _email: &str) -> Result<bool, TempMailError> {
        let state = self.state.lock().await;
        let state = state.as_ref().ok_or_else(|| {
            TempMailError::Api("Must call generate_email first".into())
        })?;

        let query = format!(
            r#"mutation {{ deleteAddress(input: {{ addressId: "{}" }}) }}"#,
            state.address_id
        );
        let gql_req = GraphqlReq { query: &query };
        let _gql_res: serde_json::Value = self.client
            .post_json(
                &format!("https://dropmail.me/api/graphql/{}", state.token),
                &gql_req,
            )
            .await
            .map_err(|e| TempMailError::Api(format!("GraphQL deleteAddress failed: {}", e)))?;

        Ok(true)
    }
}
