import { TempMailProvider } from '../base.js';
import { Message, MessageDetail } from '../models.js';
import { TempMailError, NotFoundError } from '../errors.js';

const BASE = 'https://yopmail.com';

/**
 * YOPmail provider — HTML scraping, no auth required.
 */
export class YOPmail extends TempMailProvider {
  /** @param {{ randomUA?: boolean, useCookies?: boolean }} [opts] */
  constructor(opts = {}) {
    super('yopmail', { randomUA: opts.randomUA, useCookies: opts.useCookies !== false });
    /** @type {string} */
    this._yp = '';
    /** @type {string} */
    this._yj = '';
    /** @type {string} */
    this._v = '';
  }

  /**
   * Generate a random 10-char alphanumeric string + @yopmail.com.
   * @returns {Promise<string>}
   */
  async generateEmail() {
    const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
    let user = '';
    for (let i = 0; i < 10; i++) {
      user += chars[Math.floor(Math.random() * chars.length)];
    }
    const email = `${user}@yopmail.com`;
    await this._initSession(user);
    return email;
  }

  /**
   * Initialize YOPmail session following the exact flow to avoid 400.
   * @param {string} username
   */
  async _initSession(username) {
    // Step 1: GET https://yopmail.com/en/ -> extract yp and v
    const res1 = await this._fetch(`${BASE}/en/`);
    const html1 = await res1.text();

    const ypMatch1 = html1.match(/name="yp" id="yp" value="([^"]+)"/);
    if (!ypMatch1) throw new TempMailError('Could not extract initial yp token', this.name);

    const vMatch = html1.match(/\/ver\/([0-9.]+)\/webmail\.js/);
    if (!vMatch) throw new TempMailError('Could not extract version', this.name);
    this._v = vMatch[1];

    // Step 2: GET https://yopmail.com/en/?login={username}
    const res2 = await this._fetch(`${BASE}/en/?login=${username}`);
    const html2 = await res2.text();

    const ypMatch2 = html2.match(/name="yp" id="yp" value="([^"]+)"/);
    if (!ypMatch2) throw new TempMailError('Could not extract second yp token', this.name);
    this._yp = ypMatch2[1];

    // Step 3: POST https://yopmail.com/en/ with form body
    const res3 = await this._fetch(`${BASE}/en/`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `login=${username}&id=&yp=${this._yp}`,
    });
    await res3.text();

    // Step 4: GET https://yopmail.com/ver/{v}/webmail.js -> extract yj
    const res4 = await this._fetch(`${BASE}/ver/${this._v}/webmail.js`);
    const jsContent = await res4.text();

    const yjMatch = jsContent.match(/value\+'\&yj\=([0-9a-zA-Z]*)\&v\='/);
    if (!yjMatch) throw new TempMailError('Could not extract yj token', this.name);
    this._yj = yjMatch[1];

    // Step 5: Set cookie ytime = {hour}:{minute}
    const now = new Date();
    const hour = String(now.getHours()).padStart(2, '0');
    const minute = String(now.getMinutes()).padStart(2, '0');
    this.http.cookieJar['ytime'] = `${hour}:${minute}`;
  }

  /**
   * @param {string} email
   * @returns {Promise<Message[]>}
   */
  async getInbox(email) {
    const user = email.split('@')[0];
    const params = new URLSearchParams({
      login: user,
      p: '1',
      d: '',
      ctrl: '',
      yp: this._yp,
      yj: this._yj,
      v: this._v,
      r_c: '',
      id: '',
      ad: '0',
    });

    const res = await this._fetch(`${BASE}/en/inbox?${params}`);

    if (!res.ok) throw new TempMailError(`HTTP ${res.status}`, this.name);
    const html = await res.text();

    // Parse inbox HTML for messages
    const messages = [];
    // Match message rows - looking for mail links with id
    const msgRegex = /id="m([^"]+)"[^>]*>[\s\S]*?<span[^>]*class="lmf"[^>]*>([^<]*)<\/span>[\s\S]*?<span[^>]*class="lms"[^>]*>([^<]*)<\/span>/g;
    let match;

    while ((match = msgRegex.exec(html)) !== null) {
      messages.push(new Message({
        id: match[1],
        sender: this._stripHtml(match[2]),
        subject: this._stripHtml(match[3]),
        date: new Date(),
      }));
    }

    // Fallback: simpler regex if above doesn't match
    if (messages.length === 0) {
      const simpleRegex = /href="\/en\/mail\?[^"]*id=([^"&]+)[^"]*"[^>]*>([\s\S]*?)<\/a>/g;
      while ((match = simpleRegex.exec(html)) !== null) {
        messages.push(new Message({
          id: match[1],
          sender: 'unknown',
          subject: this._stripHtml(match[2]),
          date: new Date(),
        }));
      }
    }

    return messages;
  }

  /**
   * @param {string} messageId
   * @returns {Promise<MessageDetail>}
   */
  async readMessage(messageId) {
    // messageId format: "id:email" or just "id"
    const parts = messageId.split(':');
    const id = parts[0];
    const email = parts[1] || '';
    const user = email ? email.split('@')[0] : '';

    const params = new URLSearchParams({
      b: user,
      id: id,
      yp: this._yp,
      yj: this._yj,
      v: this._v,
    });

    const res = await this._fetch(`${BASE}/en/mail?${params}`);

    if (!res.ok) throw new NotFoundError(`Message ${id} not found`, this.name);
    const html = await res.text();

    // Extract body from div#mail
    const bodyMatch = html.match(/<div[^>]*id="mail"[^>]*>([\s\S]*?)<\/div>/i);
    const bodyHtml = bodyMatch ? bodyMatch[1].trim() : '';
    const bodyText = this._stripHtml(bodyHtml);

    // Extract sender and subject from page if available
    const senderMatch = html.match(/<span[^>]*class="ellipsis"[^>]*>([^<]+)<\/span>/i);
    const subjectMatch = html.match(/<span[^>]*class="ellipsis"[^>]*>([^<]+)<\/span>[\s\S]*?<span[^>]*class="ellipsis"[^>]*>([^<]+)<\/span>/i);

    return new MessageDetail({
      id: messageId,
      sender: senderMatch ? this._stripHtml(senderMatch[1]) : 'unknown',
      subject: subjectMatch ? this._stripHtml(subjectMatch[2]) : '',
      date: new Date(),
      bodyText,
      bodyHtml,
      attachments: [],
    });
  }

  /**
   * YOPmail does not support explicit deletion.
   * @returns {Promise<boolean>}
   */
  async deleteEmail() {
    return true;
  }

  /**
   * Strip HTML tags from string.
   * @param {string} html
   * @returns {string}
   */
  _stripHtml(html) {
    if (!html) return '';
    return html.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').trim();
  }
}
