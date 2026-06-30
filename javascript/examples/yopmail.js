import { YOPmail } from '../src/index.js';

const client = new YOPmail();

const email = await client.generateEmail();
console.log('Email:', email);

// Wait for first message (timeout 60s)
const msg = await client.waitForEmail(email);
if (msg) {
  console.log('From:', msg.sender);
  console.log('Subject:', msg.subject);
  const detail = await client.readMessage(`${msg.id}:${email}`);
  console.log('Body:', detail.bodyText.slice(0, 200));
} else {
  console.log('No email received.');
}
