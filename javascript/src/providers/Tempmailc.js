import { TempMailProvider } from '../base.js';
import { Message, MessageDetail } from '../models.js';
import { TempMailError } from '../errors.js';

const API = 'https://tempmailc.com/api/v1';

/**
 * tempmailc.com provider — REST API, no auth, no cookies.
 */
export class Tempmailc extends TempMailProvider {
  /** @param {{ randomUA?: boolean, useCookies?: boolean }|undefined} config */
  constructor(config = {}) {
    super('tempmailc', { randomUA: config.randomUA, useCookies: false });
    /** @type {string|null} */
    this._email = null;
  }

  /** @returns {Promise<string>} */
  async generateEmail() {
    const res = await this._fetch(`${API}/new`);
    const data = await res.json();
    if (!data.ok) throw new TempMailError('API returned not ok', this.name);
    this._email = data.email;
    if (!this._email) throw new TempMailError('No email returned', this.name);
    return this._email;
  }

  /**
   * @param {string} email
   * @returns {Promise<Message[]>}
   */
  async getInbox(email) {
    const res = await this._fetch(`${API}/inbox?email=${encodeURIComponent(email)}`);
    const data = await res.json();
    return (data.messages || []).map(item => new Message({
      id: String(item.id ?? item.msg_id ?? ''),
      sender: item.from || item.from_mail || '',
      subject: item.subject || '',
      date: item.date || item.time,
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
    const url = `${API}/message?msg_id=${encodeURIComponent(messageId)}&email=${encodeURIComponent(this._email)}`;
    const res = await this._fetch(url);
    const data = await res.json();
    return new MessageDetail({
      id: String(data.id ?? messageId),
      sender: data.from || data.from_mail || '',
      subject: data.subject || '',
      date: data.date || data.time,
      bodyText: data.text || data.body_text || '',
      bodyHtml: data.html || data.body_html || '',
      attachments: [],
    });
  }

  /**
   * @param {string} _email
   * @returns {Promise<boolean>}
   */
  async deleteEmail(_email) {
    this._email = null;
    return true;
  }
}
