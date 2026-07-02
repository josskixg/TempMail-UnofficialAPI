/**
 * Strips HTML tags and normalizes whitespace to plain text.
 * @param {string} html
 * @returns {string}
 */
function stripHtml(html) {
  if (!html) return '';
  // Remove style/script blocks entirely
  let text = html.replace(/<(style|script)[^>]*>[\s\S]*?<\/\1>/gi, '');
  // Replace block-level elements with newlines
  text = text.replace(/<(br\s*\/?|\/p|\/div|\/tr|\/li|\/h\d)>/gi, '\n');
  // Strip remaining tags
  text = text.replace(/<[^>]+>/g, '');
  // Decode common entities
  text = text
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&nbsp;/g, ' ');
  // Collapse excess blank lines
  text = text.replace(/\n{3,}/g, '\n\n');
  return text.trim();
}

/**
 * Represents a message summary in the inbox.
 */
export class Message {
  /**
   * @param {object} data
   * @param {string} data.id
   * @param {string} data.sender
   * @param {string} data.subject
   * @param {Date} data.date
   * @param {string} [data.preview]
   * @param {boolean} [data.hasAttachments]
   */
  constructor({ id, sender, subject, date, preview = '', hasAttachments = false }) {
    this.id = id;
    this.sender = sender;
    this.subject = subject;
    this.date = date instanceof Date ? date : new Date(date);
    this.preview = preview;
    this.hasAttachments = hasAttachments;
  }
}

/**
 * Represents a full message with body, headers, and metadata.
 * Auto-normalizes: strips HTML → bodyText, computes isHtml, bodyPreview, contentType.
 * @extends Message
 */
export class MessageDetail extends Message {
  /**
   * @param {object} data
   * @param {string} data.id
   * @param {string} data.sender
   * @param {string} data.subject
   * @param {Date} data.date
   * @param {string} [data.preview]
   * @param {boolean} [data.hasAttachments]
   * @param {string} [data.bodyText]
   * @param {string} [data.bodyHtml]
   * @param {string} [data.bodyPreview]
   * @param {string} [data.contentType]
   * @param {string} [data.raw]
   * @param {Object.<string,string>} [data.headers]
   * @param {string[]} [data.cc]
   * @param {string} [data.replyTo]
   * @param {string} [data.messageId]
   * @param {number} [data.size]
   * @param {Array<{filename: string, contentType: string, url: string}>} [data.attachments]
   */
  constructor({
    id, sender, subject, date, preview = '', hasAttachments = false,
    bodyText = '', bodyHtml = '',
    bodyPreview = '', contentType = '',
    raw = '', headers = {}, cc = [],
    replyTo = '', messageId = '', size = 0,
    attachments = [],
  }) {
    super({ id, sender, subject, date, preview, hasAttachments: hasAttachments || attachments.length > 0 });

    this.bodyText = bodyText;
    this.bodyHtml = bodyHtml;
    this.raw = raw;
    this.headers = headers;
    this.cc = cc;
    this.replyTo = replyTo;
    this.messageId = messageId;
    this.size = size;
    this.attachments = attachments;

    this._normalize(bodyPreview, contentType);
  }

  _normalize(bodyPreview, contentType) {
    const hasHtml = this.bodyHtml.trim().length > 0;
    const hasText = this.bodyText.trim().length > 0;

    if (hasHtml && !hasText) {
      this.bodyText = stripHtml(this.bodyHtml);
    }

    this.isHtml = hasHtml;
    this.hasAttachments = this.attachments.length > 0;

    if (contentType) {
      this.contentType = contentType;
    } else if (hasHtml && this.bodyText) {
      this.contentType = 'multipart/alternative';
    } else if (hasHtml) {
      this.contentType = 'text/html';
    } else {
      this.contentType = 'text/plain';
    }

    this.bodyPreview = bodyPreview || this.bodyText.slice(0, 200).trim();

    if (this.messageId && !this.headers['Message-ID']) {
      this.headers['Message-ID'] = this.messageId;
    }
  }
}
