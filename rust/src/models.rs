use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Message {
    pub id: String,
    pub sender: String,
    pub subject: String,
    pub date: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MessageDetail {
    #[serde(flatten)]
    pub message: Message,
    pub body_text: Option<String>,
    pub body_html: Option<String>,
    #[serde(default)]
    pub attachments: Vec<serde_json::Value>,
}
