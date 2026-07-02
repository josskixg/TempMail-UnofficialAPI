import { TempMailProvider } from '../base.js';
import { Message, MessageDetail } from '../models.js';
import { TempMailError } from '../errors.js';

const BASE = 'https://mailnesia.com';
const CHARS = 'abcdefghijklmnopqrstuvwxyz0123456789';

/**
 * mailnesia.com provider — public mailbox, no auth, HTML scraping.
 *
 * Inbox is a `<table>`; each row is sender / subject / time with a link whose
 * trailing path segment is the message id. Read view exposes the body inside
 * `<div id="message">`.
 */
export class Mailnesia extends TempMailProvider {
  /** @param {{ randomUA?: boolean, useCookies?: boolean }|undefined} opts */
  constructor(opts = {}) {
    super('mailnesia', { randomUA: opts.randomUA, useCookies: true });
    /** @type {string|null} */
    this._email = null;
    /** @type {string|null} */
    this._username = null;
  }

  /** @returns {Promise<string>} */
  async generateEmail() {
    this._username = Array.from({ length: 10 }, () =>
      CHARS[Math.floor(Math.random() * CHARS.length)],
    ).join('');
    this._email = `${this._username}@mailnesia.com`;
    return this._email;
  }

  /**
   * Generate random IP for header rotation
   * @returns {string}
   */
  _generateRandomIP() {
    return `${Math.floor(Math.random() * 254) + 1}.${Math.floor(Math.random() * 256)}.${Math.floor(Math.random() * 256)}.${Math.floor(Math.random() * 254) + 1}`;
  }

  /**
   * Get headers with rotated IP to bypass 403
   * @returns {Object}
   */
  _getHeadersWithIPRotation() {
    const ip = this._generateRandomIP();
    return {
      'X-Forwarded-For': ip,
      'X-Real-IP': ip,
      'CF-Connecting-IP': ip,
      'True-Client-IP': ip,
    };
  }

  /**
   * @param {string} email
   * @returns {Promise<Message[]>}
   */
  async getInbox(email) {
    const username = email.includes('@') ? email.split('@')[0] : email;
    const headers = this._getHeadersWithIPRotation();
    const res = await this._fetch(`${BASE}/mailbox/${username}`, { headers });
    const html = await res.text();
    const messages = [];
    const rowRe = /<tr[^>]*>([\s\S]*?)<\/tr>/gi;
    let rm;
    while ((rm = rowRe.exec(html))) {
      const row = rm[1];
      const cells = [];
      const cellRe = /<td[^>]*>([\s\S]*?)<\/td>/gi;
      let cm;
      while ((cm = cellRe.exec(row))) cells.push(cm[1]);
      if (cells.length < 3) continue;
      const sender = this._stripHtml(cells[0]);
      const subject = this._stripHtml(cells[1]);
      const time = this._stripHtml(cells[2]);
      const href = (row.match(/<a[^>]*href="([^"]+)"/) || [])[1] || '';
      const msgId = href ? href.replace(/\/+$/, '').split('/').pop() : '';
      if (sender || subject) {
        messages.push(new Message({ id: msgId, sender, subject, date: time }));
      }
    }
    return messages;
  }

  /**
   * @param {string} messageId
   * @returns {Promise<MessageDetail>}
   */
  async readMessage(messageId) {
    if (!this._username) {
      throw new TempMailError('No email — call generateEmail() first', this.name);
    }
    const headers = this._getHeadersWithIPRotation();
    const res = await this._fetch(`${BASE}/mailbox/${this._username}/${messageId}`, { headers });
    const html = await res.text();
    const body = this._extractBlockById(html, 'message');
    const bodyHtml = body || html;
    const bodyText = body ? this._stripHtml(body) : this._stripHtml(html);
    return new MessageDetail({
      id: messageId,
      sender: '',
      subject: '',
      date: new Date(),
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
    this._username = null;
    return true;
  }

  // --- regex HTML helpers (no DOMParser in Node, no deps) ---

  /**
   * Extract the inner HTML of `<div id="{id}">` by counting balanced div tags.
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
