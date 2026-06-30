use std::time::Duration;

use tempmail_unofficial::{Provider, TempMailBuilder, TempMailProvider};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Example 1: Using Guerrilla Mail (no API key needed)
    println!("=== Guerrilla Mail ===");
    let guerrilla = TempMailBuilder::new().build_default(Provider::GuerrillaMail)?;

    let g_email = guerrilla.generate_email().await?;
    println!("Generated email: {}", g_email);

    let g_inbox = guerrilla.get_inbox(&g_email).await?;
    println!("Inbox has {} messages", g_inbox.len());

    // Example 2: Using Mail.tm (no API key needed)
    println!("\n=== Mail.tm ===");
    let mailtm = TempMailBuilder::new().build_default(Provider::MailTm)?;

    let tm_email = mailtm.generate_email().await?;
    println!("Generated email: {}", tm_email);

    // Example 3: YOPmail (HTML scraping, no API key)
    println!("\n=== YOPmail ===");
    let yopmail = TempMailBuilder::new().build_default(Provider::Yopmail)?;

    let yop_email = yopmail.generate_email().await?;
    println!("Generated email: {}", yop_email);

    // Example 4: Dropmail (GraphQL, no API key)
    println!("\n=== Dropmail ===");
    use tempmail_unofficial::providers::dropmail::DropmailConfig;
        let dropmail = TempMailBuilder::new().build_dropmail(DropmailConfig::default())?;

    let drop_email = dropmail.generate_email().await?;
    println!("Generated email: {}", drop_email);

    Ok(())
}
