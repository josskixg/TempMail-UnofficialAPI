import { MailTm } from '../src/index.js';

const client = new MailTm();

const email = await client.generateEmail();
console.log('Email:', email);

const msg = await client.waitForEmail(email);
if (msg) {
  console.log('From:', msg.sender);
  console.log('Subject:', msg.subject);
  const detail = await client.readMessage(msg.id);
  console.log('Body:', detail.bodyText.slice(0, 200));

  // Cleanup
  await client.deleteEmail(email);
  console.log('Account deleted.');
} else {
  console.log('No email received.');
}
