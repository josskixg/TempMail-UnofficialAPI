import { TempMailProvider } from '../base.js';
import { Message, MessageDetail } from '../models.js';
import { TempMailError } from '../errors.js';

const API = 'https://zoromail.com/public_api.php/v1';
const CHARS = 'abcdefghijklmnopqrstuvwxyz0123456789';

/**
 * zoromail.com provider — clean REST API, no auth required.
 *
 * Every response is wrapped as `{ success, data, error }`; this helper unwraps
 * the `data` payload and raises on a falsy `success`.
 */
export class Zoromail extends TempMailProvider {
  /** @param {{ randomUA?: boolean, useCookies?: boolean }|undefined} config */
  constructor(config = {}) {
    super('zoromail', { randomUA: config.randomUA, useCookies: false });
    /** @type {string|null} */
    this._email = null;
  }

  /**
   * Unwrap a zoromail API response: requires `success`, returns `data`.
   * @param {string} path
   * @param {RequestInit} [options]
   * @returns {Promise<any>}
   */
  async _api(path, options = {}) {
    const res = await this._fetch(`${API}${path}`, options);
    const payload = await res.json();
    if (!payload || payload.success !== true) {
      throw new TempMailError(
        `API returned error: ${payload?.error || 'unknown error'}`,
        this.name,
      );
    }
    return payload.data;
  }

  /** @returns {Promise<string>} */
  async generateEmail() {
    const domains = await this._api('/domains');
    if (!Array.isArray(domains) || !domains.length) {
      throw new TempMailError('No domains available', this.name);
    }
    const domain = domains[Math.floor(Math.random() * domains.length)];
    const username = Array.from({ length: 10 }, () =>
      CHARS[Math.floor(Math.random() * CHARS.length)],
    ).join('');
    const data = await this._api('/emails', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, domain }),
    });
    this._email = data.email;
    if (!this._email) throw new TempMailError('No email returned', this.name);
    return this._email;
  }

  /**
   * @param {string} email
   * @returns {Promise<Message[]>}
   */
  async getInbox(email) {
    const data = await this._api(`/emails/${email}/messages`);
    if (!Array.isArray(data)) return [];
    return data.map(item => new Message({
      id: String(item.id),
      sender: item.from || '',
      subject: item.subject || '',
      date: item.date,
    }));
  }

  /**
   * @param {string} messageId
   * @returns {Promise<MessageDetail>}
   */
  async readMessage(messageId) {
    const data = await this._api(`/messages/${messageId}`);
    return new MessageDetail({
      id: String(data?.id ?? messageId),
      sender: data?.from || '',
      subject: data?.subject || '',
      date: data?.date,
      bodyText: data?.text || data?.body_text || '',
      bodyHtml: data?.html || data?.body_html || '',
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
