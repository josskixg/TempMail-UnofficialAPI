import { TempMailProvider } from '../base.js';
import { Message, MessageDetail } from '../models.js';
import { TempMailError, NotFoundError } from '../errors.js';

const API = 'https://api.mail.tm';

/**
 * mail.tm provider — bearer token auth.
 */
export class MailTm extends TempMailProvider {
  /** @param {{ password?: string, randomUA?: boolean, useCookies?: boolean }|undefined} config */
  constructor(config = {}) {
    super('mail.tm', { randomUA: config.randomUA, useCookies: config.useCookies });
    this.password = config.password || 'P@ssw0rd!Temp';
    /** @type {string|null} */
    this._token = null;
    /** @type {string|null} */
    this._accountId = null;
  }

  /** @returns {Promise<string>} */
  async generateEmail() {
    return await this._generateEmailCore();
  }

  /** @returns {Promise<string>} */
  async _generateEmailCore() {
    const domainsRes = await this._fetch(`${API}/domains`);
    const domains = await domainsRes.json();
    const domain = domains['hydra:member']?.[0]?.domain || domains.member?.[0]?.domain;
    if (!domain) throw new TempMailError('No domain available', this.name);

    const address = `tmp${Date.now().toString(36)}@${domain}`;
    const res = await this._fetch(`${API}/accounts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ address, password: this.password }),
    });
    const account = await res.json();
    this._accountId = account.id;

    // Get token
    const tokenRes = await this._fetch(`${API}/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ address, password: this.password }),
    });
    const tokenData = await tokenRes.json();
    this._token = tokenData.token;

    return address;
  }

  /** @returns {Promise<Record<string, string>>} */
  _headers() {
    const h = { 'Content-Type': 'application/json' };
    if (this._token) h['Authorization'] = `Bearer ${this._token}`;
    return h;
  }

  /**
   * @param {string} _email
   * @returns {Promise<Message[]>}
   */
  async getInbox(_email) {
    const res = await this._fetch(`${API}/messages`, { headers: this._headers() });
    const data = await res.json();
    const items = data['hydra:member'] || data.member || [];
    return items.map(m => new Message({
      id: m.id, // raw ID — MailTm uses token auth, no email context needed
      sender: m.from?.address || m.from?.name || 'unknown',
      subject: m.subject || '',
      date: m.createdAt,
    }));
  }

  /**
   * @param {string} messageId
   * @returns {Promise<MessageDetail>}
   */
  async readMessage(messageId) {
    const res = await this._fetch(`${API}/messages/${messageId}`, { headers: this._headers() });
    const m = await res.json();
    if (!m.id) throw new NotFoundError(`Message ${messageId} not found`, this.name);
    return new MessageDetail({
      id: m.id,
      sender: m.from?.address || m.from?.name || 'unknown',
      subject: m.subject || '',
      date: m.createdAt,
      bodyText: m.text || '',
      bodyHtml: m.html?.[0] || '',
      attachments: (m.attachments || []).map(a => ({
        filename: a.filename,
        contentType: a.contentType,
        url: `${API}/sources/${m.id}/attachments/${a.id}`,
      })),
    });
  }

  /**
   * @param {string} _email
   * @returns {Promise<boolean>}
   */
  async deleteEmail(_email) {
    if (!this._accountId) return false;
    await this._fetch(`${API}/accounts/${this._accountId}`, {
      method: 'DELETE',
      headers: this._headers(),
    });
    this._token = null;
    this._accountId = null;
    return true;
  }
}
