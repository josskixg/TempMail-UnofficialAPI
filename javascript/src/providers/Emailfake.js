import { TempMailProvider } from '../base.js';
import { Message, MessageDetail } from '../models.js';
import { TempMailError, NotFoundError } from '../errors.js';

const CHARS = 'abcdefghijklmnopqrstuvwxyz0123456789';

/**
 * emailfake.com provider — surl cookie + channel URL, HTML scraping.
 *
 * This is the base for the emailfake / generator.email / email-temp family:
 * they share the same backend (gasmurl), the same `surl` cookie, the same
 * `email-table` inbox markup and `from_div_*`/`subj_div_*`/`time_div_*` fields.
 * Subclasses only override the base URL and provider name.
 *
 * HTML is parsed with regex — no DOMParser in Node, no deps added.
 */
export class Emailfake extends TempMailProvider {
  /**
   * @param {{ randomUA?: boolean, useCookies?: boolean }|undefined} opts
   * @param {string} [name]
   * @param {string} [base]
   */
  constructor(opts = {}, name = 'emailfake', base = 'https://emailfake.com') {
    super(name, { randomUA: opts.randomUA, useCookies: true });
    this._base = base;
    /** @type {string|null} */
    this._email = null;
    /** @type {string|null} */
    this._domain = null;
    /** @type {string|null} */
    this._username = null;
  }

  /**
   * Fetch a /channel{1-9}/ page and scrape domain options from it.
   * @returns {Promise<string[]>}
   */
  async _getDomains() {
    const ch = 1 + Math.floor(Math.random() * 9);
    const res = await this._fetch(`${this._base}/channel${ch}/`);
    const html = await res.text();
    const domains = new Set();
    const re = /<option[^>]*value="([^"]+)"/gi;
    let m;
    while ((m = re.exec(html))) {
      const v = m[1].trim();
      if (v.includes('.') && !v.includes(' ') && !v.includes('@')) domains.add(v);
    }
    if (!domains.size) {
      // Fallback: look for domain-like strings in text content
      const textRe = />([a-z0-9-]+\.[a-z.]{2,})</gi;
      let m2;
      while ((m2 = textRe.exec(html))) {
        const v = m2[1].trim();
        if (!v.includes('emailfake') && !v.includes('generator') && !v.includes('email-temp')) {
          domains.add(v);
        }
      }
    }
    if (!domains.size) throw new TempMailError('No domains found on page', this.name);
    return [...domains];
  }

  /** @returns {Promise<string>} */
  async generateEmail() {
    const domains = await this._getDomains();
    this._domain = domains[Math.floor(Math.random() * domains.length)];
    this._username = Array.from({ length: 10 }, () =>
      CHARS[Math.floor(Math.random() * CHARS.length)],
    ).join('');
    this._email = `${this._username}@${this._domain}`;
    // surl cookie routes the channel page to this mailbox: {domain}/{username}
    this.http.cookieJar['surl'] = `${this._domain}/${this._username}`;
    return this._email;
  }

  /**
   * @param {string} _email — unused; the surl cookie carries the mailbox
   * @returns {Promise<Message[]>}
   */
  async getInbox(_email) {
    if (!this._domain || !this._username) {
      throw new TempMailError('No email generated — call generateEmail() first', this.name);
    }
    const ch = 1 + Math.floor(Math.random() * 9);
    const res = await this._fetch(`${this._base}/channel${ch}/`);
    const html = await res.text();
    const table = this._extractBlockById(html, 'email-table');
    if (!table) return [];
    const messages = [];
    const linkRe = /<a[^>]*class="[^"]*list-group-item[^"]*"[^>]*>([\s\S]*?)<\/a>/gi;
    let lm;
    while ((lm = linkRe.exec(table))) {
      const block = lm[0];
      const href = (block.match(/href="([^"]+)"/) || [])[1] || '';
      const msgId = href ? href.replace(/\/+$/, '').split('/').pop() : '';
      if (!msgId || msgId.length < 10) continue;
      const sender = this._divText(block, 'from');
      const subject = this._divText(block, 'subj');
      const time = this._divText(block, 'time');
      messages.push(new Message({ id: msgId, sender, subject, date: time }));
    }
    return messages;
  }

  /**
   * @param {string} messageId
   * @returns {Promise<MessageDetail>}
   */
  async readMessage(messageId) {
    if (!this._domain || !this._username) {
      throw new TempMailError('No email generated — call generateEmail() first', this.name);
    }
    const url = `${this._base}/${this._domain}/${this._username}/${messageId}`;
    const res = await this._fetch(url);
    const html = await res.text();
    const body = this._extractBlockById(html, 'message');
    if (!body) throw new NotFoundError(`Message ${messageId} body not found`, this.name);
    return new MessageDetail({
      id: messageId,
      sender: this._divText(html, 'from_div'),
      subject: this._divText(html, 'subj_div'),
      date: this._divText(html, 'time_div'),
      bodyText: this._stripHtml(body),
      bodyHtml: body,
      attachments: [],
    });
  }

  /**
   * @param {string} _email
   * @returns {Promise<boolean>}
   */
  async deleteEmail(_email) {
    this._email = null;
    this._domain = null;
    this._username = null;
    return true;
  }

  // --- regex HTML helpers (no DOMParser in Node, no deps) ---

  /**
   * Extract the inner HTML of `<div id="{id}">` by counting balanced div tags.
   * Returns null if the opening tag is absent; falls back to the rest of the
   * document if no matching close is found.
   * @param {string} html
   * @param {string} id
   * @returns {string|null}
   */
  _extractBlockById(html, id) {
    const open = html.match(new RegExp(`<div[^>]*id="${id}"[^>]*>`, 'i'));
    if (!open) return null;
    const start = open.index + open[0].length;
    let depth = 1;
    const tagRe = /<\/?div\b[^>]*>/gi;
    tagRe.lastIndex = start;
    let t;
    while ((t = tagRe.exec(html))) {
      depth += t[0].startsWith('</') ? -1 : 1;
      if (depth === 0) return html.slice(start, t.index);
    }
    return html.slice(start);
  }

  /**
   * Text content of the first `<div class="…{classFrag}…">…</div>` (non-greedy).
   * @param {string} html
   * @param {string} classFrag
   * @returns {string}
   */
  _divText(html, classFrag) {
    const re = new RegExp(
      `<div[^>]*class="[^"]*${classFrag}[^"]*"[^>]*>([\\s\\S]*?)<\\/div>`,
      'i',
    );
    const m = html.match(re);
    return m ? this._stripHtml(m[1]) : '';
  }

  /**
   * Strip HTML tags and decode common entities.
   * @param {string} html
   * @returns {string}
   */
  _stripHtml(html) {
    if (!html) return '';
    return html
      .replace(/<[^>]*>/g, '')
      .replace(/&nbsp;/g, ' ')
      .replace(/&amp;/g, '&')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&quot;/g, '"')
      .trim();
  }
}
