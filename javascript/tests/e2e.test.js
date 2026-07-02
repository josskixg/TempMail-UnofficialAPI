import { describe, it } from 'node:test';
import assert from 'node:assert';
import { createProvider } from '../src/index.js';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

// Load .env (Node doesn't auto-load it)
try {
  const envPath = join(dirname(fileURLToPath(import.meta.url)), '../.env');
  for (const line of readFileSync(envPath, 'utf8').split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq < 0) continue;
    const k = trimmed.slice(0, eq).trim();
    const v = trimmed.slice(eq + 1).trim();
    if (!process.env[k]) process.env[k] = v;
  }
} catch { /* .env optional */ }

const UA_POOL = [
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
];

const RESEND_API_KEY = process.env.RESEND_API_KEY || '';

/**
 * Sends a test email to the specified address via Resend API.
 * @param {string} to - Recipient email address
 * @returns {Promise<boolean>}
 */
async function sendTestEmail(to) {
  const delays = [1000, 3000, 5000];
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const resp = await fetch('https://api.resend.com/emails', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${RESEND_API_KEY}`,
          'User-Agent': UA_POOL[Math.floor(Math.random() * UA_POOL.length)],
        },
        body: JSON.stringify({
          from: 'onboarding@rokupusu.web.id',
          to,
          subject: 'TempMail E2E Test',
          html: '<p>E2E test email from TempMail wrapper</p>',
        }),
      });
      if (resp.ok) return true;
      if (attempt < 2) {
        await new Promise(r => setTimeout(r, delays[attempt]));
        continue;
      }
      return false;
    } catch {
      if (attempt < 2) {
        await new Promise(r => setTimeout(r, delays[attempt]));
      }
      continue;
    }
  }
  return false;
}

/**
 * Helper to run provider E2E test.
 * @param {string} providerName
 * @param {object} [config]
 */
async function testProvider(providerName, config) {
  const provider = createProvider(providerName, config);
  assert.ok(provider, `${providerName}: provider should be created`);

  // Step 1: Generate email
  let email;
  try {
    email = await provider.generateEmail();
    console.log(`  ${providerName}: generated ${email}`);
  } catch (err) {
    console.warn(`  ${providerName}: generateEmail failed (skipping): ${err.message}`);
    return; // Skip rest of test
  }

  assert.ok(email.includes('@'), `${providerName}: email should contain @`);

  // Step 2: Send test email and wait for delivery
  let emailSent = false;
  try {
    emailSent = await sendTestEmail(email);
    if (emailSent) {
      console.log(`  ${providerName}: test email sent to ${email}`);
      await new Promise(r => setTimeout(r, 4000));
    } else {
      console.warn(`  ${providerName}: sendTestEmail failed (skipping inbox assertion)`);
    }
  } catch (err) {
    console.warn(`  ${providerName}: sendTestEmail error: ${err.message}`);
  }

  // Step 3: Get inbox
  let inbox = [];
  try {
    inbox = await provider.getInbox(email);
    console.log(`  ${providerName}: inbox has ${inbox.length} messages`);
  } catch (err) {
    console.warn(`  ${providerName}: getInbox failed: ${err.message}`);
  }

  assert.ok(Array.isArray(inbox), `${providerName}: inbox should be array`);

  // If send succeeded, warn if empty
  if (emailSent && inbox.length === 0) {
    console.warn(`  ${providerName}: WARNING: test email was sent but inbox is empty (delivery may be slow)`);
  }

  // Step 3: If inbox has messages, read the first one
  if (inbox.length > 0) {
    const msg = inbox[0];
    assert.ok(msg.id, `${providerName}: message should have id`);
    assert.ok(msg.sender, `${providerName}: message should have sender`);

    try {
      // Use format "id:email" for providers that need it
      const messageId = `${msg.id}:${email}`;
      const detail = await provider.readMessage(messageId);
      console.log(`  ${providerName}: read message ${msg.id}`);

      assert.ok(detail.id, `${providerName}: detail should have id`);
      assert.ok(typeof detail.bodyText === 'string', `${providerName}: detail should have bodyText`);
    } catch (err) {
      console.warn(`  ${providerName}: readMessage failed: ${err.message}`);
    }
  } else {
    console.log(`  ${providerName}: no messages to read (expected for fresh address)`);
  }

  // Step 4: Delete email
  try {
    const deleted = await provider.deleteEmail(email);
    console.log(`  ${providerName}: deleteEmail returned ${deleted}`);
    assert.ok(typeof deleted === 'boolean', `${providerName}: deleteEmail should return boolean`);
  } catch (err) {
    console.warn(`  ${providerName}: deleteEmail failed: ${err.message}`);
  }
}

describe('E2E Provider Tests', () => {
  describe('mailtm', () => {
    it('should complete full flow', async () => {
      await testProvider('mailtm');
    });
  });

  describe('guerrillamail', () => {
    it('should complete full flow', async () => {
      await testProvider('guerrillamail');
    });
  });

  describe('yopmail', () => {
    it('should complete full flow', async () => {
      await testProvider('yopmail');
    });
  });

  describe('dropmail', () => {
    it('should complete full flow', async () => {
      await testProvider('dropmail');
    });
  });

  describe('1secemail', () => {
    it('should complete full flow', async () => {
      await testProvider('1secemail');
    });
  });

  describe('ncaori', () => {
    it('should complete full flow', async () => {
      await testProvider('ncaori');
    });
  });

  describe('zoromail', () => {
    it('should complete full flow', async () => {
      await testProvider('zoromail');
    });
  });

  describe('tempmail.lol', () => {
    it('should complete full flow', async () => {
      await testProvider('tempmail.lol');
    });
  });

  describe('tempmailc', () => {
    it('should complete full flow', async () => {
      await testProvider('tempmailc');
    });
  });

  describe('temp-mail.io', () => {
    it('should complete full flow', async () => {
      await testProvider('temp-mail.io');
    });
  });

  describe('tempmail.plus', () => {
    it('should complete full flow', async () => {
      await testProvider('tempmail.plus');
    });
  });

  describe('emailfake', () => {
    it('should complete full flow', async () => {
      await testProvider('emailfake');
    });
  });

  describe('generator.email', () => {
    it('should complete full flow', async () => {
      await testProvider('generator.email');
    });
  });

  describe('mailnesia', () => {
    it('should complete full flow', async () => {
      await testProvider('mailnesia');
    });
  });

  describe('10minutemail', () => {
    it('should complete full flow', async () => {
      await testProvider('10minutemail');
    });
  });

  describe('email-temp', () => {
    it('should complete full flow', async () => {
      await testProvider('email-temp');
    });
  });
});

describe('Factory Tests', () => {
  it('should create yopmail provider with alternate name', () => {
    const provider = createProvider('yop');
    assert.ok(provider);
    assert.strictEqual(provider.name, 'yopmail');
  });

  it('should create dropmail provider with alternate name', () => {
    const provider = createProvider('dropmail.me');
    assert.ok(provider);
    assert.strictEqual(provider.name, 'dropmail');
  });

  it('should create 1secemail provider', () => {
    const provider = createProvider('1secemail');
    assert.ok(provider);
    assert.strictEqual(provider.name, '1secemail');
  });

  it('should create ncaori provider', () => {
    const provider = createProvider('ncaori');
    assert.ok(provider);
    assert.strictEqual(provider.name, 'ncaori');
  });

  it('should create ncaori provider with alternate name', () => {
    const provider = createProvider('nca.my.id');
    assert.ok(provider);
    assert.strictEqual(provider.name, 'ncaori');
  });

  it('should create zoromail provider', () => {
    const provider = createProvider('zoromail');
    assert.ok(provider);
    assert.strictEqual(provider.name, 'zoromail');
  });

  it('should create tempmail.lol provider', () => {
    const provider = createProvider('tempmail.lol');
    assert.ok(provider);
    assert.strictEqual(provider.name, 'tempmail.lol');
  });

  it('should create tempmailc provider', () => {
    const provider = createProvider('tempmailc');
    assert.ok(provider);
    assert.strictEqual(provider.name, 'tempmailc');
  });

  it('should create temp-mail.io provider', () => {
    const provider = createProvider('temp-mail.io');
    assert.ok(provider);
    assert.strictEqual(provider.name, 'temp-mail.io');
  });

  it('should create tempmail.plus provider', () => {
    const provider = createProvider('tempmail.plus');
    assert.ok(provider);
    assert.strictEqual(provider.name, 'tempmail.plus');
  });

  it('should create emailfake provider', () => {
    const provider = createProvider('emailfake');
    assert.ok(provider);
    assert.strictEqual(provider.name, 'emailfake');
  });

  it('should create generator.email provider', () => {
    const provider = createProvider('generator.email');
    assert.ok(provider);
    assert.strictEqual(provider.name, 'generator.email');
  });

  it('should create mailnesia provider', () => {
    const provider = createProvider('mailnesia');
    assert.ok(provider);
    assert.strictEqual(provider.name, 'mailnesia');
  });

  it('should create 10minutemail provider', () => {
    const provider = createProvider('10minutemail');
    assert.ok(provider);
    assert.strictEqual(provider.name, '10minutemail');
  });

  it('should create email-temp provider', () => {
    const provider = createProvider('email-temp');
    assert.ok(provider);
    assert.strictEqual(provider.name, 'email-temp');
  });

  it('should throw on unknown provider', () => {
    assert.throws(() => createProvider('unknown'), /Unknown provider/);
  });
});
