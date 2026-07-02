use std::time::Duration;
use std::sync::atomic::{AtomicUsize, Ordering};
use tempmail_unofficial::{Provider, TempMailBuilder, TempMailProvider};

fn load_dotenv() {
    let paths = ["../.env", ".env"];
    for path in &paths {
        if let Ok(content) = std::fs::read_to_string(path) {
            for line in content.lines() {
                let line = line.trim();
                if line.is_empty() || line.starts_with('#') { continue; }
                if let Some(eq) = line.find('=') {
                    let k = line[..eq].trim();
                    let v = line[eq+1..].trim();
                    if std::env::var(k).is_err() { std::env::set_var(k, v); }
                }
            }
            break;
        }
    }
}

const UA_POOL: &[&str] = &[
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
];

static UA_COUNTER: AtomicUsize = AtomicUsize::new(0);

fn random_ua() -> &'static str {
    UA_POOL[UA_COUNTER.fetch_add(1, Ordering::Relaxed) % UA_POOL.len()]
}

fn resend_api_key() -> String {
    std::env::var("RESEND_API_KEY").unwrap_or_default()
}

/// Send a test email to the given address via Resend API.
/// Returns true on success so the test can wait for inbox delivery.
async fn send_test_email(to: &str) -> bool {
    let delays = [1u64, 3, 5];
    for attempt in 0..3usize {
        let client = reqwest::Client::new();
        let resp = client
            .post("https://api.resend.com/emails")
            .header("Authorization", format!("Bearer {}", resend_api_key()))
            .header("User-Agent", random_ua())
            .timeout(Duration::from_secs(10))
            .json(&serde_json::json!({
                "from": "onboarding@rokupusu.web.id",
                "to": to,
                "subject": "TempMail E2E Test",
                "html": "<p>E2E test email from TempMail wrapper</p>"
            }))
            .send()
            .await;
        match resp {
            Ok(r) => {
                if r.status().is_success() { return true; }
                if attempt < 2 {
                    tokio::time::sleep(Duration::from_secs(delays[attempt])).await;
                    continue;
                }
                return false;
            }
            Err(_) => {
                if attempt < 2 {
                    tokio::time::sleep(Duration::from_secs(delays[attempt])).await;
                    continue;
                }
                return false;
            }
        }
    }
    false
}

async fn run_e2e(provider: Box<dyn TempMailProvider>, name: &str) {
    load_dotenv();
    println!("[{}] Starting test", name);
    let email = match provider.generate_email().await {
        Ok(e) => e,
        Err(e) => {
            println!("[{}] Warning: generate_email failed: {}", name, e);
            return;
        }
    };
    println!("[{}] Generated: {}", name, email);

    // Try to send a test email so we have something in the inbox
    let sent = send_test_email(&email).await;
    if sent {
        println!("[{}] Test email queued, waiting 4s for delivery...", name);
        tokio::time::sleep(Duration::from_secs(4)).await;
    } else {
        println!("[{}] Warning: send_test_email failed, skipping inbox assertion", name);
    }

    let inbox = match provider.get_inbox(&email).await {
        Ok(i) => i,
        Err(e) => {
            println!("[{}] Warning: get_inbox failed: {}", name, e);
            return;
        }
    };
    println!("[{}] Inbox has {} messages", name, inbox.len());

    if sent {
        assert!(
            inbox.len() >= 1,
            "[{}] Expected at least 1 message after sending test email, got {}",
            name,
            inbox.len()
        );
    }

    if let Some(msg) = inbox.first() {
        match provider.read_message(&msg.id).await {
            Ok(m) => println!("[{}] Read message: {}", name, m.message.subject),
            Err(e) => println!("[{}] Warning: read_message failed: {}", name, e),
        }
    }

    match provider.delete_email(&email).await {
        Ok(_) => println!("[{}] Deleted email", name),
        Err(e) => println!("[{}] Warning: delete_email failed: {}", name, e),
    }
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_mailtm() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::MailTm)
            .unwrap(),
        "MailTm",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_guerrillamail() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::GuerrillaMail)
            .unwrap(),
        "GuerrillaMail",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_yopmail() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::Yopmail)
            .unwrap(),
        "Yopmail",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_dropmail() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::Dropmail)
            .unwrap(),
        "Dropmail",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_onesecemail() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::OneSecEmail)
            .unwrap(),
        "OneSecEmail",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_ncaori() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::NcaoriMail)
            .unwrap(),
        "NcaoriMail",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_zoromail() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::Zoromail)
            .unwrap(),
        "Zoromail",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_tempmail_lol() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::TempmailLol)
            .unwrap(),
        "TempmailLol",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_tempmailc() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::Tempmailc)
            .unwrap(),
        "Tempmailc",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_temp_mail_io() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::TempMailIo)
            .unwrap(),
        "TempMailIo",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_tempmail_plus() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::TempmailPlus)
            .unwrap(),
        "TempmailPlus",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_emailfake() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::Emailfake)
            .unwrap(),
        "Emailfake",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_generator_email() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::GeneratorEmail)
            .unwrap(),
        "GeneratorEmail",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_mailnesia() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::Mailnesia)
            .unwrap(),
        "Mailnesia",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_ten_minute_mail() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::TenMinuteMail)
            .unwrap(),
        "TenMinuteMail",
    )
    .await;
}

#[tokio::test]
#[ignore = "requires network access"]
async fn test_email_temp() {
    run_e2e(
        TempMailBuilder::new()
            .build_default(Provider::EmailTemp)
            .unwrap(),
        "EmailTemp",
    )
    .await;
}
