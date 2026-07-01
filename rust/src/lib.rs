pub mod error;
pub mod httpclient;
pub mod models;
pub mod providers;

pub use error::TempMailError;
pub use httpclient::HttpClient;
pub use models::{Message, MessageDetail};
pub use providers::TempMailProvider;

use providers::dropmail::{Dropmail, DropmailConfig};
use providers::guerrilla_mail::{GuerrillaMail, GuerrillaMailConfig};
use providers::mail_tm::{MailTm, MailTmConfig};
use providers::yopmail::{Yopmail, YopmailConfig};
use providers::one_sec_email::OneSecEmail;
use providers::ncaori_mail::NcaoriMail;

/// Available temporary email providers.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Provider {
    MailTm,
    GuerrillaMail,
    Yopmail,
    Dropmail,
    OneSecEmail,
    NcaoriMail,
}

/// Builder for constructing a configured provider instance.
pub struct TempMailBuilder {
    http_client: Option<HttpClient>,
}

impl TempMailBuilder {
    pub fn new() -> Self {
        Self { http_client: None }
    }

    /// Use a custom HttpClient.
    pub fn http_client(mut self, client: HttpClient) -> Self {
        self.http_client = Some(client);
        self
    }

    /// Build a provider with default config using HttpClient defaults.
    pub fn build_default(self, provider: Provider) -> Result<Box<dyn TempMailProvider>, TempMailError> {
        let client = self.http_client.unwrap_or(HttpClient::default_client()?);
        match provider {
            Provider::MailTm => Ok(Box::new(MailTm::new(client, None))),
            Provider::GuerrillaMail => Ok(Box::new(GuerrillaMail::new(client, None))),
            Provider::Yopmail => Ok(Box::new(Yopmail::new(client, None))),
            Provider::Dropmail => Ok(Box::new(Dropmail::new(client, None))),
            Provider::OneSecEmail => Ok(Box::new(OneSecEmail::new(client))),
            Provider::NcaoriMail => Ok(Box::new(NcaoriMail::new(client))),
        }
    }

    /// Build mail.tm with custom config.
    pub fn build_mail_tm(self, config: MailTmConfig) -> Result<MailTm, TempMailError> {
        let client = self.http_client.unwrap_or(HttpClient::default_client()?);
        Ok(MailTm::new(client, Some(config)))
    }

    /// Build guerrillamail with custom config.
    pub fn build_guerrilla_mail(self, config: GuerrillaMailConfig) -> Result<GuerrillaMail, TempMailError> {
        // ponytail: guerrillamail needs cookies for session tracking
        let client = match self.http_client {
            Some(c) => c,
            None => HttpClient::with_config(Vec::new(), false, true)?,
        };
        Ok(GuerrillaMail::new(client, Some(config)))
    }

    /// Build YOPmail with custom config.
    pub fn build_yopmail(self, config: YopmailConfig) -> Result<Yopmail, TempMailError> {
        // ponytail: yopmail needs cookies for session tracking
        let client = match self.http_client {
            Some(c) => c,
            None => HttpClient::with_config(Vec::new(), false, true)?,
        };
        Ok(Yopmail::new(client, Some(config)))
    }

    /// Build Dropmail with custom config.
    pub fn build_dropmail(self, config: DropmailConfig) -> Result<Dropmail, TempMailError> {
        let client = self.http_client.unwrap_or(HttpClient::default_client()?);
        Ok(Dropmail::new(client, Some(config)))
    }

    /// Build OneSecEmail.
    pub fn build_one_sec_email(self) -> Result<OneSecEmail, TempMailError> {
        let client = self.http_client.unwrap_or(HttpClient::default_client()?);
        Ok(OneSecEmail::new(client))
    }
}

impl Default for TempMailBuilder {
    fn default() -> Self {
        Self::new()
    }
}
