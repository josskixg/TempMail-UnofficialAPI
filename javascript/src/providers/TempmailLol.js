import { TempMailProvider } from '../base.js';
import { Message, MessageDetail } from '../models.js';
import { TempMailError, NotFoundError } from '../errors.js';

const API = 'https://api.tempmail.lol/v2';

/**
 * tempmail.lol provider — REST API, token-based, no auth header.
 *
 * The inbox response already carries full message bodies, so readMessage just
 * re-fetches the inbox and locates the matching id.
 */
export class TempmailLol extends TempMailProvider {
  /** @param {{ randomUA?: boolean, useCookies?: boolean }|undefined} config */
  constructor(config = {}) {
    super('tempmail.lol', { randomUA: config.randomUA, useCookies: false });
    /** @type {string|null} */
    this._token = null;
    /** @type {string|null} */
    this._email = null;
  }

  /** @returns {Promise<string>} */
  async generateEmail() {
    const res = await this._fetch(`${API}/inbox/create`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    });
    const data = await res.json();
    this._email = data.address;
    this._token = data.token;
    if (!this._email || !this._token) {
      throw new TempMailError('Missing address or token in response', this.name);
    }
    return this._email;
  }

  /**
   * @param {string} _email — unused; tempmail.lol is token-based
   * @returns {Promise<Message[]>}
   */
  async getInbox(_email) {
    if (!this._token) {
      throw new TempMailError('No token — call generateEmail() first', this.name);
    }
    const res = await this._fetch(`${API}/inbox?token=${encodeURIComponent(this._token)}`);
    const data = await res.json();
    if (data.expired) throw new TempMailError('Token expired', this.name);
    return (data.emails || []).map(item => new Message({
      id: String(item._id ?? item.id ?? item.uid ?? ''),
      sender: item.from || item.sender || '',
      subject: item.subject || '',
      date: item.date || item.createdAt,
    }));
  }

  /**
   * @param {string} messageId
   * @returns {Promise<MessageDetail>}
   */
  async readMessage(messageId) {
    if (!this._token) {
      throw new TempMailError('No token — call generateEmail() first', this.name);
    }
    const res = await this._fetch(`${API}/inbox?token=${encodeURIComponent(this._token)}`);
    const data = await res.json();
    for (const item of (data.emails || [])) {
      if (String(item._id ?? item.id ?? item.uid ?? '') === String(messageId)) {
        return new MessageDetail({
          id: String(messageId),
          sender: item.from || item.sender || '',
          subject: item.subject || '',
          date: item.date || item.createdAt,
          bodyText: item.body || item.text || '',
          bodyHtml: item.html || '',
          attachments: [],
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
}
