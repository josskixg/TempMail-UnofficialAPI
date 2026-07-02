const CODE_SNIPPETS = {
  go: {
    title: 'main.go',
    code: `package main

import (
    "fmt"
    "log"
    "time"
    tempmail "github.com/josskixg/TempMail-UnofficialAPI/go"
)

func main() {
    // 1. Initialize provider
    provider, _ := tempmail.NewProvider("mail.tm", nil)

    // 2. Generate disposable email
    email, _ := provider.GenerateEmail()
    fmt.Println("Created mailbox:", email)

    // 3. WaitForEmail (blocking poll)
    fmt.Println("Waiting for verification email...")
    msg, _ := provider.WaitForEmail(email, 60*time.Second, 5*time.Second)
    fmt.Printf("Received! Subject: %s\\n", msg.Subject)

    // 4. Fetch details
    detail, _ := provider.ReadMessage(msg.ID)
    fmt.Println("Body:", detail.BodyText)
}`
  },
  python: {
    title: 'app.py',
    code: `import time
from tempmail_wrapper import create_provider

# 1. Initialize provider — choose any of 16 providers:
#    mail.tm | guerrillamail | yopmail | dropmail | 1secemail
#    ncaori  | zoromail | tempmail.lol | tempmailc | temp-mail.io
#    tempmail.plus | emailfake | generator.email | email-temp
#    mailnesia | 10minutemail
provider = create_provider("mail.tm")

# 2. Generate email
email = provider.generate_email()
print(f"Generated email: {email}")

# 3. Poll for messages with wait_for_email helper
print("Waiting for inbox delivery...")
message = provider.wait_for_email(email, timeout=60, interval=5)

if message:
    print(f"New mail: {message.subject} from {message.sender}")
    # 4. Fetch full details — body_text/body_html auto-normalized
    detail = provider.read_message(message.id)
    print(f"Content-Type: {detail.content_type}")
    print(f"Is HTML: {detail.is_html}")
    print(f"Plain text:\\n{detail.body_text}")
    print(f"Preview: {detail.body_preview}")
    if detail.body_html:
        print(f"HTML body available ({len(detail.body_html)} chars)")

# 5. Clean up
provider.delete_email(email)`
  },
  javascript: {
    title: 'index.js',
    code: `import { createProvider } from 'tempmail-unofficial-api';

// 1. Initialize provider
const provider = createProvider('mail.tm');

// 2. Generate email
const email = await provider.generateEmail();
console.log(\`Generated: \${email}\`);

// 3. Wait for email
console.log('Polling for new emails...');
const msg = await provider.waitForEmail(email, 60000, 5000);

if (msg) {
  console.log(\`Received: \${msg.subject}\`);
  // 4. Read message details
  const detail = await provider.readMessage(msg.id);
  console.log(\`Plain text body: \${detail.body_text}\`);
}

// 5. Clean up
await provider.deleteEmail(email);`
  },
  java: {
    title: 'Main.java',
    code: `import com.tempmail.TempMailBuilder;
import com.tempmail.TempMailProvider;
import com.tempmail.Message;
import com.tempmail.MessageDetail;

public class Main {
    public static void main(String[] args) throws Exception {
        // 1. Create client
        TempMailProvider provider = new TempMailBuilder()
                .withProvider("mail.tm")
                .build();

        // 2. Generate address
        String email = provider.generateEmail();
        System.out.println("Email generated: " + email);

        // 3. WaitForEmail (Blocking poll helper)
        System.out.println("Waiting for emails...");
        Message msg = provider.waitForEmail(email, 60, 5);

        if (msg != null) {
            // 4. Fetch content details
            MessageDetail detail = provider.readMessage(msg.getId());
            System.out.println("Subject: " + detail.getSubject());
            System.out.println("Body: " + detail.getBodyText());
        }
    }
}`
  },
  php: {
    title: 'index.php',
    code: `<?php
require 'vendor/autoload.php';

use TempMail\\TempMailBuilder;

// 1. Instantiate provider
$provider = (new TempMailBuilder())
    ->withProvider('mail.tm')
    ->build();

// 2. Generate mailbox
$email = $provider->generateEmail();
echo "Generated address: {$email}\\n";

// 3. Block and poll for incoming email
echo "Waiting for messages...\\n";
$msg = $provider->waitForEmail($email, 60, 5);

if ($msg) {
    echo "Found email: {$msg->subject}\\n";
    // 4. Fetch content details
    $detail = $provider->readMessage($msg->id);
    echo "Body: {$detail->body_text}\\n";
}`
  },
  rust: {
    title: 'main.rs',
    code: `use std::time::Duration;
use tempmail_unofficial::{TempMailBuilder, TempMailProvider};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // 1. Instantiate client
    let provider = TempMailBuilder::new()
        .provider("mail.tm")
        .build()?;

    // 2. Generate email
    let email = provider.generate_email().await?;
    println!("Email address: {}", email);

    // 3. Block and wait for delivery
    println!("Waiting for messages...");
    if let Some(msg) = provider.wait_for_email(&email, Duration::from_secs(60), Duration::from_secs(5)).await? {
        println!("Received: {}", msg.subject);
        // 4. Read body content
        let detail = provider.read_message(&msg.id).await?;
        println!("Body:\\n{}", detail.body_text);
    }
    
    Ok(())
}`
  },
  csharp: {
    title: 'Program.cs',
    code: `using System;
using System.Threading.Tasks;
using TempMail;

class Program
{
    static async Task Main(string[] args)
    {
        // 1. Create wrapper provider client
        var provider = new TempMailBuilder()
            .WithProvider("mail.tm")
            .Build();

        // 2. Generate
        string email = await provider.GenerateEmailAsync();
        Console.WriteLine($"Mailbox Address: {email}");

        // 3. Poll
        Console.WriteLine("Polling inbox...");
        var msg = await provider.WaitForEmailAsync(email, timeoutSec: 60, intervalSec: 5);

        if (msg != null)
        {
            // 4. Read Detail
            var detail = await provider.ReadMessageAsync(msg.Id);
            Console.WriteLine($"Subject: {detail.Subject}");
            Console.WriteLine($"Content: {detail.BodyText}");
        }
    }
}`
  }
};

document.addEventListener('DOMContentLoaded', () => {
  // Tabs switcher logic
  const tabs = document.querySelectorAll('.lang-tab');
  const codePre = document.getElementById('code-display');
  const codeTitle = document.getElementById('code-filename');
  const copyBtn = document.getElementById('btn-copy-code');
  
  function setLanguage(lang) {
    tabs.forEach(t => t.classList.toggle('active', t.dataset.lang === lang));
    if (CODE_SNIPPETS[lang]) {
      codePre.textContent = CODE_SNIPPETS[lang].code;
      codeTitle.textContent = CODE_SNIPPETS[lang].title;
    }
  }
  
  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      setLanguage(tab.dataset.lang);
    });
  });
  
  // Copy to clipboard logic
  copyBtn.addEventListener('click', () => {
    const textToCopy = codePre.textContent;
    navigator.clipboard.writeText(textToCopy).then(() => {
      const copyText = copyBtn.querySelector('.copy-text');
      const originalText = copyText.textContent;
      copyText.textContent = 'Copied!';
      copyBtn.style.borderColor = 'var(--accent)';
      copyBtn.style.color = 'var(--accent)';
      
      setTimeout(() => {
        copyText.textContent = originalText;
        copyBtn.style.borderColor = '';
        copyBtn.style.color = '';
      }, 2000);
    });
  });
  
  // Initialize Go as default snippet
  setLanguage('go');
  
  // Simulator Logic
  const simSteps = document.querySelectorAll('.sim-step');
  const consoleBody = document.getElementById('sim-console-text');
  
  const SIM_ACTIONS = {
    1: async () => {
      writeConsole('$ tempmail init --provider=mail.tm', 'cmd');
      await sleep(600);
      writeConsole('Initializing connection to mail.tm endpoints...', 'info');
      await sleep(800);
      writeConsole('Success! Client provider built successfully.', 'success');
    },
    2: async () => {
      writeConsole('$ tempmail generate', 'cmd');
      await sleep(600);
      writeConsole('Connecting to API provider secure generator...', 'info');
      await sleep(1000);
      const generatedMail = `test_${Math.floor(Math.random() * 89999 + 10000)}@mail.tm`;
      writeConsole(`Mailbox created: ${generatedMail}`, 'success');
      return generatedMail;
    },
    3: async () => {
      writeConsole('$ tempmail wait-for-email --timeout=30', 'cmd');
      await sleep(600);
      writeConsole('Listening to real-time SMTP inbox stream...', 'info');
      await sleep(800);
      writeConsole('Polling... (no new messages)', 'info');
      await sleep(1000);
      writeConsole('Incoming mail detected! [ID: 9a0f44b2]', 'success');
      await sleep(400);
      writeConsole('  From: newsletter@github.com', 'info');
      writeConsole('  Subject: Welcome to GitHub Universe 2026!', 'info');
    },
    4: async () => {
      writeConsole('$ tempmail read-message --id=9a0f44b2', 'cmd');
      await sleep(600);
      writeConsole('Fetching decryted plain-text & HTML payload...', 'info');
      await sleep(1000);
      writeConsole('--- BODY ---', 'success');
      writeConsole('Hey developer! Welcome to GitHub Universe. Your confirmation code is: 549320. Enjoy building!', 'info');
      writeConsole('------------', 'success');
    }
  };
  
  let currentStep = 1;
  let running = false;
  
  function writeConsole(text, type = '') {
    const line = document.createElement('div');
    line.className = `sim-console-line ${type}`;
    if (type === 'cmd') {
      line.textContent = text;
    } else if (type === 'success') {
      line.textContent = `[✔] ${text}`;
    } else if (type === 'info') {
      line.textContent = `[ℹ] ${text}`;
    } else {
      line.textContent = text;
    }
    consoleBody.appendChild(line);
    consoleBody.scrollTop = consoleBody.scrollHeight;
  }
  
  async function runStep(stepNum) {
    if (running) return;
    running = true;
    
    // UI active classes
    simSteps.forEach(s => s.classList.toggle('active', parseInt(s.dataset.step) === stepNum));
    
    // Clear screen if starting first step
    if (stepNum === 1) {
      consoleBody.innerHTML = '';
    }
    
    if (SIM_ACTIONS[stepNum]) {
      await SIM_ACTIONS[stepNum]();
    }
    
    currentStep = stepNum + 1;
    if (currentStep > 4) {
      currentStep = 1;
    }
    
    running = false;
  }
  
  simSteps.forEach(step => {
    step.addEventListener('click', () => {
      const stepNum = parseInt(step.dataset.step);
      runStep(stepNum);
    });
  });
  
  function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
  
  // Auto-run first console line
  writeConsole('Ready. Select Step 1 on the left to initiate the demo wrapper.', 'info');
});
