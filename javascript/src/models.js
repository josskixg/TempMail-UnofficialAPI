/**
 * Represents a message in an inbox.
 */
export class Message {
  /**
   * @param {object} data
   * @param {string} data.id
   * @param {string} data.sender
   * @param {string} data.subject
   * @param {Date} data.date
   */
  constructor({ id, sender, subject, date }) {
    this.id = id;
    this.sender = sender;
    this.subject = subject;
    this.date = date instanceof Date ? date : new Date(date);
  }
}

/**
 * Represents a full message with body and attachments.
 * @extends Message
 */
export class MessageDetail extends Message {
  /**
   * @param {object} data
   * @param {string} data.id
   * @param {string} data.sender
   * @param {string} data.subject
   * @param {Date} data.date
   * @param {string} [data.bodyText]
   * @param {string} [data.bodyHtml]
   * @param {Array<{filename: string, contentType: string, url: string}>} [data.attachments]
   */
  constructor({ id, sender, subject, date, bodyText, bodyHtml, attachments }) {
    super({ id, sender, subject, date });
    this.bodyText = bodyText || '';
    this.bodyHtml = bodyHtml || '';
    this.attachments = attachments || [];
  }
}
