import { createProvider } from '../src/index.js';

const client = createProvider('ncaori');

const email = await client.generateEmail();
console.log('Email:', email);

// Ncaori returns full message body in getInbox — no separate readMessage needed.
const msg = await client.waitForEmail(email);
if (msg) {
  console.log('From:', msg.sender);
  console.log('Subject:', msg.subject);
  // MessageDetail includes bodyText/bodyHtml from inbox
  if (msg.bodyText) {
    console.log('Body:', msg.bodyText.slice(0, 300));
  }
} else {
  console.log('No email received.');
}
