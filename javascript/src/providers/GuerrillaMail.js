import { TempMailProvider } from '../base.js';
import { Message, MessageDetail } from '../models.js';
import { TempMailError, NotFoundError } from '../errors.js';

const API = 'https://api.guerrillamail.com/ajax.php';

/**
 * GuerrillaMail provider — cookie-based session.
 */
export class GuerrillaMail extends TempMailProvider {
  /** @param {{ randomUA?: boolean, useCookies?: boolean }} [opts] */
  constructor(opts = {}) {
    super('guerrillamail', { randomUA: opts.randomUA, useCookies: opts.useCookies });
  }

  /** @returns {Promise<string>} */
  async generateEmail() {
    const res = await this._fetch(`${API}?f=get_email_address&lang=en`);
    const data = await res.json();
    return data.email_addr;
  }

  /**
   * @param {string} _email
   * @returns {Promise<Message[]>}
   */
  async getInbox(_email) {
    const res = await this._fetch(`${API}?f=get_email_list&offset=0&lang=en`);
    const data = await res.json();
    return (data.list || []).map(m => new Message({
      id: String(m.mail_id),
      sender: m.mail_from,
      subject: m.mail_subject,
      date: m.mail_timestamp ? new Date(m.mail_timestamp * 1000) : new Date(),
    }));
  }

  /**
   * @param {string} messageId
   * @returns {Promise<MessageDetail>}
   */
  async readMessage(messageId) {
    const res = await this._fetch(`${API}?f=fetch_email&email_id=${messageId}&lang=en`);
    const m = await res.json();
    if (!m.mail_id) throw new NotFoundError(`Message ${messageId} not found`, this.name);
    return new MessageDetail({
      id: String(m.mail_id),
      sender: m.mail_from,
      subject: m.mail_subject,
      date: m.mail_timestamp ? new Date(m.mail_timestamp * 1000) : new Date(),
      bodyText: m.mail_body || '',
      bodyHtml: m.mail_body_html || '',
      attachments: [], // GuerrillaMail API doesn't expose attachments directly
    });
  }

  /**
   * @param {string} email
   * @returns {Promise<boolean>}
   */
  async deleteEmail(email) {
    return true;
  }
}
