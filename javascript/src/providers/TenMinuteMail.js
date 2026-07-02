import { TempMailProvider } from '../base.js';
import { Message, MessageDetail } from '../models.js';
import { TempMailError, NotFoundError } from '../errors.js';

const BASE = 'https://10minutemail.net';

// Helper to decode Cloudflare obfuscated email
function decodeCfEmail(hex) {
  if (!hex) return '';
  let str = '';
  const k = parseInt(hex.substr(0, 2), 16);
  for (let i = 2; i < hex.length; i += 2) {
    str += String.fromCharCode(parseInt(hex.substr(i, 2), 16) ^ k);
  }
  return str;
}

// Strip HTML tags helper
function stripHtml(html) {
  if (!html) return '';
  return html.replace(/<[^>]+>/g, '').trim();
}

/**
 * 10minutemail.net provider — HTML scraping public mailbox.
 */
export class TenMinuteMail extends TempMailProvider {
  /** @param {{ randomUA?: boolean, useCookies?: boolean }|undefined} config */
  constructor(config = {}) {
    super('10minutemail', { randomUA: config.randomUA, useCookies: true });
    /** @type {string|null} */
    this._email = null;
  }

  /** @returns {Promise<string>} */
  async generateEmail() {
    const res = await this._fetch(`${BASE}/`);
    const html = await res.text();
    const match = html.match(/id="fe_text"[^>]*value="([^"]+)"/i);
    this._email = match ? match[1].trim() : null;
    if (!this._email) {
      throw new TempMailError('No address in response', this.name);
    }
    return this._email;
  }

  /**
   * @param {string} _email
   * @returns {Promise<Message[]>}
   */
  async getInbox(_email) {
    const res = await this._fetch(`${BASE}/mailbox.ajax.php`);
    const html = await res.text();
    const messages = [];
    const rowRe = /<tr[^>]*>([\s\S]*?)<\/tr>/gi;
    
    // Skip header row
    let isHeader = true;
    let rm;
    while ((rm = rowRe.exec(html))) {
      if (isHeader) {
        isHeader = false;
        continue;
      }
      const row = rm[1];
      const cells = [];
      const cellRe = /<td[^>]*>([\s\S]*?)<\/td>/gi;
      let cm;
      while ((cm = cellRe.exec(row))) cells.push(cm[1]);
      if (cells.length < 3) continue;

      // Extract sender (handling Cloudflare obfuscation)
      let sender = '';
      const cfMatch = cells[0].match(/data-cfemail="([^"]+)"/i);
      if (cfMatch) {
        sender = decodeCfEmail(cfMatch[1]);
      } else {
        sender = stripHtml(cells[0]);
      }

      const subject = stripHtml(cells[1]);

      // Extract Date (from title if exists)
      let dateStr = '';
      const dateMatch = cells[2].match(/title="([^"]+)"/i);
      if (dateMatch) {
        dateStr = dateMatch[1];
      } else {
        dateStr = stripHtml(cells[2]);
      }
      const date = new Date(dateStr + (dateStr.toLowerCase().includes('utc') ? '' : ' UTC'));

      // Extract mid (message ID)
      const midMatch = row.match(/mid=([^'&"\s>]+)/i);
      const id = midMatch ? midMatch[1] : '';

      if (id) {
        messages.push(new Message({ id, sender, subject, date }));
      }
    }
    return messages;
  }

  /**
   * @param {string} messageId
   * @returns {Promise<MessageDetail>}
   */
  async readMessage(messageId) {
    const mid = messageId.includes(':') ? messageId.split(':')[0] : messageId;
    const res = await this._fetch(`${BASE}/readmail.html?mid=${mid}`);
    const html = await res.text();

    // Check if mail exists (if we get the page)
    const bodyMatch = html.match(/class="mailinhtml"[^>]*>([\s\S]*?)<div[^>]*style="clear:both;"/i);
    if (!bodyMatch) {
      throw new NotFoundError(`Message ${messageId} not found`, this.name);
    }

    const bodyHtmlRaw = bodyMatch[1].trim();

    // Decode Cloudflare email obfuscation in body
    let bodyHtml = bodyHtmlRaw.replace(/<(a|span)[^>]*class="__cf_email__"[^>]*data-cfemail="([^"]+)"[^>]*>([\s\S]*?)<\/\1>/gi, (m, tag, hex) => {
      return decodeCfEmail(hex);
    });
    bodyHtml = bodyHtml.replace(/href="\/cdn-cgi\/l\/email-protection#([^"]+)"/g, (m, hex) => {
      return `href="mailto:${decodeCfEmail(hex)}"`;
    });

    const bodyText = stripHtml(bodyHtml);

    const subjectMatch = html.match(/<div class="mail_header">[\s\S]*?<h2[^>]*>([\s\S]*?)<\/h2>/i);
    const subject = subjectMatch ? stripHtml(subjectMatch[1]) : '';

    const fromMatch = html.match(/<span class="mail_from">([\s\S]*?)<\/span>/i);
    let sender = '';
    if (fromMatch) {
      const cfFrom = fromMatch[1].match(/data-cfemail="([^"]+)"/i);
      sender = cfFrom ? decodeCfEmail(cfFrom[1]) : stripHtml(fromMatch[1]);
    }

    return new MessageDetail({
      id: messageId,
      sender,
      subject,
      date: new Date(), // fallback
      bodyText,
      bodyHtml,
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
