import { TempMailProvider } from '../base.js';
import { Message, MessageDetail } from '../models.js';
import { TempMailError, NotFoundError } from '../errors.js';

const BASE = 'https://www.1secemail.com';
const DOMAINS = ['qzueos.com', 'gaziw.com', 'emailgenerator.xyz'];

/**
 * 1secemail.com provider — CSRF-protected web scraping.
 */
export class OneSecEmail extends TempMailProvider {
  constructor(opts = {}) {
    super('1secemail', { randomUA: opts.randomUA, useCookies: opts.useCookies });
    this._csrf = null;
    this._email = null;
  }

  async _fetchCSRF() {
    const res = await this._fetch(`${BASE}/`);
    if (!res.ok) throw new TempMailError(`Failed to load page: HTTP ${res.status}`, this.name);
    const html = await res.text();
    const csrf = html.match(/<meta name="csrf-token" content="([^"]+)">/)?.[1];
    if (!csrf) throw new TempMailError('CSRF token not found', this.name);
    return csrf;
  }

  async _ensureCSRF() {
    if (this._csrf) return;
    this._csrf = await this._fetchCSRF();
  }

  async _post(urlPath, data = {}) {
    await this._ensureCSRF();
    const res = await this._fetch(urlPath, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': this._csrf,
        'x-xsrf-token': this._csrf,
        Referer: `${BASE}/`,
      },
      body: JSON.stringify({ _token: this._csrf, ...data }),
    });
    if (!res.ok) throw new TempMailError(`POST failed: HTTP ${res.status}`, this.name);
    return await res.json();
  }

  async generateEmail() {
    await this._ensureCSRF();
    const name = Math.random().toString(36).substring(2, 12);
    const domain = DOMAINS[Math.floor(Math.random() * DOMAINS.length)];
    await this._post(`${BASE}/change`, { name, domain });
    this._email = `${name}@${domain}`;
    return this._email;
  }

  async getInbox(email) {
    const data = await this._post(`${BASE}/get_messages`, {});
    if (!Array.isArray(data)) return [];
    return data.map(m => {
      const date = m.receivedAt ? new Date(m.receivedAt.replace(' ', 'T')) : new Date();
      return new Message(
        m.id || '',
        m.from_email || m.from || 'unknown',
        m.subject || '(no subject)',
        date,
      );
    });
  }

  async readMessage(messageId) {
    await this._ensureCSRF();
    const res = await this._fetch(`${BASE}/view/${messageId}`, {
      headers: {
        'X-CSRF-TOKEN': this._csrf,
        Referer: `${BASE}/`,
      },
    });
    if (!res.ok) throw new NotFoundError(`Message ${messageId} not found`, this.name);
    const html = await res.text();
    const text = html.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
    const sender = html.match(/From:\s*([^<\n]+)/)?.[1]?.trim() || 'unknown';
    const subject = html.match(/Subject:\s*([^<\n]+)/)?.[1]?.trim() || '(no subject)';
    const dateMatch = html.match(/(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})/);
    const date = dateMatch ? new Date(dateMatch[1].replace(' ', 'T')) : new Date();
    return new MessageDetail(
      new Message(messageId, sender, subject, date),
      text,
      html,
    );
  }

  async deleteEmail(email) {
    return true;
  }
}
