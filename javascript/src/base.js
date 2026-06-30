import { TempMailError } from './errors.js';
import { HttpClient } from './httpclient.js';

/**
 * Abstract base class for temporary email providers.
 */
export class TempMailProvider {
  /** @param {string} name */
  constructor(name, httpOpts = {}) {
    if (new.target === TempMailProvider) {
      throw new Error('TempMailProvider is abstract');
    }
    this.name = name;
    /** @type {HttpClient} */
    this.http = httpOpts instanceof HttpClient ? httpOpts : new HttpClient(httpOpts);
  }

  /**
   * Generate a new temporary email address.
   * @returns {Promise<string>}
   */
  async generateEmail() {
    throw new TempMailError('Not implemented', this.name);
  }

  /**
   * Get messages in the inbox.
   * @param {string} email
   * @returns {Promise<import('./models.js').Message[]>}
   */
  async getInbox(email) {
    throw new TempMailError('Not implemented', this.name);
  }

  /**
   * Read a full message by ID.
   * @param {string} messageId
   * @returns {Promise<import('./models.js').MessageDetail>}
   */
  async readMessage(messageId) {
    throw new TempMailError('Not implemented', this.name);
  }

  /**
   * Delete a temporary email address.
   * @param {string} email
   * @returns {Promise<boolean>}
   */
  async deleteEmail(email) {
    throw new TempMailError('Not implemented', this.name);
  }

  /**
   * Wait for the first email to arrive.
   * @param {string} email
   * @param {number} [timeoutMs=60000]
   * @param {number} [intervalMs=5000]
   * @returns {Promise<import('./models.js').Message|null>}
   */
  async waitForEmail(email, timeoutMs = 60000, intervalMs = 5000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      const inbox = await this.getInbox(email);
      if (inbox.length > 0) return inbox[0];
      await new Promise(r => setTimeout(r, intervalMs));
    }
    return null;
  }

  /**
   * Helper: fetch with error handling via HttpClient.
   * @param {string} url
   * @param {RequestInit} [options]
   * @returns {Promise<Response>}
   */
  async _fetch(url, options = {}) {
    const res = await this.http.request(url, options);
    if (res.status === 429) {
      const { RateLimitError } = await import('./errors.js');
      const retryAfter = res.headers.get('retry-after');
      throw new RateLimitError(
        'Rate limited',
        this.name,
        retryAfter ? Number(retryAfter) * 1000 : undefined
      );
    }
    if (res.status === 404) {
      const { NotFoundError } = await import('./errors.js');
      throw new NotFoundError(`Not found: ${url}`, this.name);
    }
    if (!res.ok) {
      throw new TempMailError(`HTTP ${res.status}: ${res.statusText}`, this.name);
    }
    return res;
  }
}
