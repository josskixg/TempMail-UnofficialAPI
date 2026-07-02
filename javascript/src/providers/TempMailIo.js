import { TempMailProvider } from '../base.js';
import { Message, MessageDetail } from '../models.js';
import { TempMailError, NotFoundError } from '../errors.js';

const API = 'https://api.internal.temp-mail.io/api/v3';

/**
 * temp-mail.io provider — free internal REST API, Bearer token.
 *
 * There is no single-message endpoint; readMessage re-fetches the message list
 * for the address and locates the matching id.
 */
export class TempMailIo extends TempMailProvider {
  /** @param {{ randomUA?: boolean, useCookies?: boolean }|undefined} config */
  constructor(config = {}) {
    super('temp-mail.io', { randomUA: config.randomUA, useCookies: false });
    /** @type {string|null} */
    this._token = null;
    /** @type {string|null} */
    this._email = null;
  }

  /** @returns {Record<string, string>} */
  _headers() {
    const h = { 'Content-Type': 'application/json' };
    if (this._token) h['Authorization'] = `Bearer ${this._token}`;
    return h;
  }

  /** @returns {Promise<string>} */
  async generateEmail() {
    const res = await this._fetch(`${API}/email/new`, {
      method: 'POST',
      headers: this._headers(),
      body: JSON.stringify({ min_name_length: 6, max_name_length: 12 }),
    });
    const data = await res.json();
    this._email = data.email;
    this._token = data.token;
    if (!this._email) throw new TempMailError('Missing email in response', this.name);
    return this._email;
  }

  /**
   * @param {string} email
   * @returns {Promise<Message[]>}
   */
  async getInbox(email) {
    const res = await this._fetch(`${API}/email/${email}/messages`, { headers: this._headers() });
    let data = await res.json();
    if (!Array.isArray(data)) data = data.messages || data.mails || [];
    return data.map(item => new Message({
      id: String(item.id ?? item.uid ?? ''),
      sender: this._senderOf(item.from),
      subject: item.subject || '',
      date: item.created_at || item.date,
    }));
  }

  /**
   * @param {string} messageId
   * @returns {Promise<MessageDetail>}
   */
  async readMessage(messageId) {
    if (!this._email) {
      throw new TempMailError('No email — call generateEmail() first', this.name);
    }
    const res = await this._fetch(`${API}/email/${this._email}/messages`, { headers: this._headers() });
    let data = await res.json();
    if (!Array.isArray(data)) data = data.messages || data.mails || [];
    for (const item of data) {
      if (String(item.id ?? item.uid ?? '') === String(messageId)) {
        return new MessageDetail({
          id: String(messageId),
          sender: this._senderOf(item.from),
          subject: item.subject || '',
          date: item.created_at || item.date,
          bodyText: item.body_text || item.text || '',
          bodyHtml: item.body_html || item.html || '',
          attachments: (item.attachments || []).map(a => ({
            filename: a.filename || '',
            contentType: a.content_type || '',
            url: a.url || '',
          })),
        });
      }
    }
    throw new NotFoundError(`Message ${messageId} not found`, this.name);
  }

  /**
   * @param {string} _email
   * @returns {Promise<boolean>}
   */
  async deleteEmail(_email) {
    this._token = null;
    this._email = null;
    return true;
  }

  /**
   * Normalize a `from` field that may be a string or `{ address, name }`.
   * @param {string|object|undefined} from
   * @returns {string}
   */
  _senderOf(from) {
    if (!from) return '';
    if (typeof from === 'object') return from.address || from.name || '';
    return from;
  }
}
