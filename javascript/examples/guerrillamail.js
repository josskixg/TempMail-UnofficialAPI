import { GuerrillaMail } from '../src/index.js';

const client = new GuerrillaMail();

const email = await client.generateEmail();
console.log('Email:', email);

const inbox = await client.getInbox(email);
console.log(`Inbox has ${inbox.length} messages.`);

if (inbox.length > 0) {
  const detail = await client.readMessage(inbox[0].id);
  console.log('Latest subject:', detail.subject);
}
