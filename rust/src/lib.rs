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
use providers::zoromail::Zoromail;
use providers::tempmail_lol::TempmailLol;
use providers::tempmailc::Tempmailc;
use providers::temp_mail_io::TempMailIo;
use providers::tempmail_plus::TempmailPlus;
use providers::emailfake::Emailfake;
use providers::generator_email::GeneratorEmail;
use providers::mailnesia::Mailnesia;
use providers::ten_minute_mail::TenMinuteMail;
use providers::email_temp::EmailTemp;

/// Available temporary email providers.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Provider {
    MailTm,
    GuerrillaMail,
    Yopmail,
    Dropmail,
    OneSecEmail,
    NcaoriMail,
    Zoromail,
    TempmailLol,
    Tempmailc,
    TempMailIo,
    TempmailPlus,
    Emailfake,
    GeneratorEmail,
    Mailnesia,
    TenMinuteMail,
    EmailTemp,
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
            Provider::Zoromail => Ok(Box::new(Zoromail::new(client))),
            Provider::TempmailLol => Ok(Box::new(TempmailLol::new(client))),
            Provider::Tempmailc => Ok(Box::new(Tempmailc::new(client))),
            Provider::TempMailIo => Ok(Box::new(TempMailIo::new(client))),
            Provider::TempmailPlus => Ok(Box::new(TempmailPlus::new(client))),
            Provider::Emailfake => Ok(Box::new(Emailfake::new(client))),
            Provider::GeneratorEmail => Ok(Box::new(GeneratorEmail::new(client))),
            Provider::Mailnesia => Ok(Box::new(Mailnesia::new(client))),
            Provider::EmailTemp => Ok(Box::new(EmailTemp::new(client))),
            Provider::TenMinuteMail => {
                // ponytail: 10minutemail needs a cookie_store client; build_default's shared
                // `client` is cookie-less, so build a fresh one here. Use build_ten_minute_mail()
                // to supply a custom client.
                let c = HttpClient::with_config(Vec::new(), false, true)?;
                Ok(Box::new(TenMinuteMail::new(c)))
            }
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

    /// Build Zoromail.
    pub fn build_zoromail(self) -> Result<Zoromail, TempMailError> {
        let client = self.http_client.unwrap_or(HttpClient::default_client()?);
        Ok(Zoromail::new(client))
    }

    /// Build tempmail.lol.
    pub fn build_tempmail_lol(self) -> Result<TempmailLol, TempMailError> {
        let client = self.http_client.unwrap_or(HttpClient::default_client()?);
        Ok(TempmailLol::new(client))
    }

    /// Build tempmailc.
    pub fn build_tempmailc(self) -> Result<Tempmailc, TempMailError> {
        let client = self.http_client.unwrap_or(HttpClient::default_client()?);
        Ok(Tempmailc::new(client))
    }

    /// Build temp-mail.io.
    pub fn build_temp_mail_io(self) -> Result<TempMailIo, TempMailError> {
        let client = self.http_client.unwrap_or(HttpClient::default_client()?);
        Ok(TempMailIo::new(client))
    }

    /// Build tempmail.plus.
    pub fn build_tempmail_plus(self) -> Result<TempmailPlus, TempMailError> {
        let client = self.http_client.unwrap_or(HttpClient::default_client()?);
        Ok(TempmailPlus::new(client))
    }

    /// Build emailfake.
    pub fn build_emailfake(self) -> Result<Emailfake, TempMailError> {
        let client = self.http_client.unwrap_or(HttpClient::default_client()?);
        Ok(Emailfake::new(client))
    }

    /// Build generator.email.
    pub fn build_generator_email(self) -> Result<GeneratorEmail, TempMailError> {
        let client = self.http_client.unwrap_or(HttpClient::default_client()?);
        Ok(GeneratorEmail::new(client))
    }

    /// Build mailnesia.
    pub fn build_mailnesia(self) -> Result<Mailnesia, TempMailError> {
        let client = self.http_client.unwrap_or(HttpClient::default_client()?);
        Ok(Mailnesia::new(client))
    }

    /// Build email-temp.
    pub fn build_email_temp(self) -> Result<EmailTemp, TempMailError> {
        let client = self.http_client.unwrap_or(HttpClient::default_client()?);
        Ok(EmailTemp::new(client))
    }

    /// Build 10minutemail (cookie-based session).
    pub fn build_ten_minute_mail(self) -> Result<TenMinuteMail, TempMailError> {
        // ponytail: 10minutemail tracks session via cookies set by /session/address
        let client = match self.http_client {
            Some(c) => c,
            None => HttpClient::with_config(Vec::new(), false, true)?,
        };
        Ok(TenMinuteMail::new(client))
    }
}

impl Default for TempMailBuilder {
    fn default() -> Self {
        Self::new()
    }
}
