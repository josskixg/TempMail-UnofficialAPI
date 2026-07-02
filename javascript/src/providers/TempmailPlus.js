import { TempMailProvider } from '../base.js';
import { Message, MessageDetail } from '../models.js';
import { TempMailError } from '../errors.js';

const BASE = 'https://tempmail.plus';
const DOMAIN = 'mailto.plus';
const CHARS = 'abcdefghijklmnopqrstuvwxyz0123456789';

/**
 * tempmail.plus provider — REST API, no auth, email passed as query param.
 */
export class TempmailPlus extends TempMailProvider {
  /** @param {{ randomUA?: boolean, useCookies?: boolean }|undefined} config */
  constructor(config = {}) {
    super('tempmail.plus', { randomUA: config.randomUA, useCookies: true });
    /** @type {string|null} */
    this._email = null;
  }

  /** @returns {Promise<string>} */
  async generateEmail() {
    const username = Array.from({ length: 10 }, () =>
      CHARS[Math.floor(Math.random() * CHARS.length)],
    ).join('');
    this._email = `${username}@${DOMAIN}`;
    return this._email;
  }

  /**
   * @param {string} email
   * @returns {Promise<Message[]>}
   */
  async getInbox(email) {
    const res = await this._fetch(`${BASE}/api/mails?email=${encodeURIComponent(email)}`);
    const data = await res.json();
    if (data.result === false) throw new TempMailError('API returned error', this.name);
    return (data.mail_list || []).map(item => new Message({
      id: String(item.mail_id ?? ''),
      sender: item.from_mail || '',
      subject: item.subject || '',
      date: item.time,
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
    const url = `${BASE}/api/mails/${encodeURIComponent(messageId)}?email=${encodeURIComponent(this._email)}`;
    const res = await this._fetch(url);
    const data = await res.json();
    return new MessageDetail({
      id: String(data.mail_id ?? messageId),
      sender: data.from_mail || data.from || '',
      subject: data.subject || '',
      date: data.date,
      bodyText: data.text || '',
      bodyHtml: data.html || '',
      attachments: (data.attachments || []).map(a => ({
        filename: a.filename || '',
        contentType: a.content_type || '',
        url: '',
      })),
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
