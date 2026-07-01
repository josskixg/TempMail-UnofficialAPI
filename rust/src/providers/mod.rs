pub mod dropmail;
pub mod guerrilla_mail;
pub mod mail_tm;
pub mod yopmail;
pub mod one_sec_email;
pub mod ncaori_mail;

use std::time::Duration;

use async_trait::async_trait;

use crate::error::TempMailError;
use crate::models::{Message, MessageDetail};

#[async_trait]
pub trait TempMailProvider: Send + Sync {
    /// Generate a new temporary email address.
    async fn generate_email(&self) -> Result<String, TempMailError>;

    /// Get inbox messages for the given email address.
    async fn get_inbox(&self, email: &str) -> Result<Vec<Message>, TempMailError>;

    /// Read a specific message by its ID.
    async fn read_message(&self, message_id: &str) -> Result<MessageDetail, TempMailError>;

    /// Delete a temporary email address. Returns true if deleted.
    async fn delete_email(&self, email: &str) -> Result<bool, TempMailError>;

    /// Poll for the first email arriving within `timeout`, checking every `interval`.
    async fn wait_for_email(
        &self,
        email: &str,
        timeout: Duration,
        interval: Duration,
    ) -> Result<Option<Message>, TempMailError> {
        let start = tokio::time::Instant::now();
        while start.elapsed() < timeout {
            let inbox = self.get_inbox(email).await?;
            if let Some(msg) = inbox.into_iter().next() {
                return Ok(Some(msg));
            }
            tokio::time::sleep(interval).await;
        }
        Ok(None)
    }
}
