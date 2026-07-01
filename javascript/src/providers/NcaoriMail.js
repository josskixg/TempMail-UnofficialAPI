import { TempMailProvider } from '../base.js';
import { Message, MessageDetail } from '../models.js';
import { TempMailError } from '../errors.js';

const BASE = 'https://www.nca.my.id';
const DOMAINS = ['ncaori.my.id', 'nca.my.id'];

/**
 * Ncaori Mail+ provider — internal REST API on same origin.
 * Inbox response already contains full message body.
 * No CSRF or auth needed. Email generated locally.
 */
export class NcaoriMail extends TempMailProvider {
  constructor(opts = {}) {
    super('ncaori', { randomUA: opts.randomUA, useCookies: opts.useCookies });
  }

  _randomName() {
    const words = ['swift', 'crystal', 'storm', 'frost', 'shadow', 'ember', 'azure',
      'phantom', 'silver', 'iron', 'crimson', 'golden', 'neo', 'cosmic', 'lunar',
      'solar', 'dark', 'light', 'void', 'flux'];
    const words2 = ['core', 'leaf', 'forge', 'wave', 'peak', 'gate', 'pulse',
      'blade', 'shard', 'drift', 'hive', 'node', 'edge', 'beacon', 'nova',
      'storm', 'cloud', 'moon', 'star', 'wind'];
    return `${words[Math.floor(Math.random() * words.length)]}_${words2[Math.floor(Math.random() * words2.length)]}`;
  }

  async generateEmail() {
    const name = this._randomName();
    const domain = DOMAINS[Math.floor(Math.random() * DOMAINS.length)];
    this._email = `${name}@${domain}`;
    return this._email;
  }

  async getInbox(email) {
    const res = await this._fetch(`${BASE}/api/emails?recipient=${encodeURIComponent(email)}`);
    if (!res.ok) throw new TempMailError(`Inbox fetch failed: HTTP ${res.status}`, this.name);
    const data = await res.json();
    if (!data || !Array.isArray(data.emails)) return [];
    return data.emails.map(m => {
      const date = m.created_at ? new Date(m.created_at) : new Date();
      const msg = new Message({
        id: m.id || '',
        sender: m.sender || 'unknown',
        subject: m.subject || '(no subject)',
        date,
      });
      // Inbox response already contains full body — return as detail
      if (m.body_text || m.body_html) {
        return new MessageDetail({
          ...msg,
          bodyText: m.body_text || '',
          bodyHtml: m.body_html || '',
        });
      }
      return msg;
    });
  }

  /** @inheritdoc */
  async readMessage(messageId) {
    // Internal API returns full message in inbox. No separate read endpoint found.
    throw new TempMailError(
      'Ncaori Mail+ returns full message content in getInbox(). Use getInbox() then filter by id.',
      this.name,
    );
  }

  async deleteEmail(email) {
    // Emails auto-expire after 48h. No delete endpoint.
    return true;
  }
}
