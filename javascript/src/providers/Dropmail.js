import { TempMailProvider } from '../base.js';
import { Message, MessageDetail } from '../models.js';
import { TempMailError, NotFoundError } from '../errors.js';

const TOKEN_URL = 'https://dropmail.me/api/token/generate';
const PADDLE_OCR_URL = 'https://mamamacjdjj-padle-predict.hf.space/predict';

/**
 * Built-in PaddleOCR solver via HuggingFace space.
 * Tries up to 3 times, returns trimmed text on success or null.
 * @param {ArrayBuffer} imgBytes - captcha image bytes
 * @returns {Promise<string|null>}
 */
export async function paddleOcrSolver(imgBytes) {
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const form = new FormData();
      form.append('file', new Blob([imgBytes], { type: 'image/png' }), 'cap.png');
      const ocrRes = await fetch(PADDLE_OCR_URL, { method: 'POST', body: form });
      const ocrData = await ocrRes.json();
      const results = ocrData.results || [];
      if (results.length > 0 && results[0].confidence >= 0.7) {
        return results[0].text.trim();
      }
    } catch { /* ignore */ }
  }
  return null;
}

/**
 * Dropmail.me provider — GraphQL API.
 */
export class Dropmail extends TempMailProvider {
  /** @param {{ randomUA?: boolean, useCookies?: boolean, solvers?: Array<function(ArrayBuffer): Promise<string|null>> }} [opts] */
  constructor(opts = {}) {
    super('dropmail', { randomUA: opts.randomUA, useCookies: opts.useCookies });
    /** @type {Array<function(ArrayBuffer): Promise<string|null>>|null} */
    this._solvers = opts.solvers || null;
    /** @type {string|null} */
    this._token = null;
    /** @type {string|null} */
    this._endpoint = null;
    /** @type {string|null} */
    this._sessionId = null;
    /** @type {string|null} */
    this._addressId = null;
    /** @type {string|null} */
    this._email = null;
  }

  /**
   * Execute a GraphQL query/mutation.
   * @param {string} query
   * @param {object} [variables]
   * @returns {Promise<object>}
   */
  async _graphql(query, variables = {}) {
    if (!this._endpoint) throw new TempMailError('Not initialized. Call generateEmail first.', this.name);

    const res = await this._fetch(this._endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query, variables }),
    });

    if (!res.ok) throw new TempMailError(`HTTP ${res.status}`, this.name);
    const data = await res.json();

    if (data.errors && data.errors.length > 0) {
      throw new TempMailError(data.errors[0].message, this.name);
    }

    return data.data;
  }

  /**
   * Solve captcha and get 90d token.
   * Uses this.http.request() (same cookie jar) for Dropmail steps; plain fetch for PaddleOCR.
   * @param {object} captcha - captcha object from 402 response
   * @returns {Promise<string|null>} token or null on failure
   */
  async _solveCaptchaAndGetToken(captcha) {
    const { v = '3', nonce = '', key = '', _sig: sig = '' } = captcha;

    // Step 2: download captcha image — same cookie jar via this.http.request()
    const imgUrl = `https://dropmail.me/captcha/image.png?_r=0&v=${encodeURIComponent(v)}&nonce=${encodeURIComponent(nonce)}&key=${encodeURIComponent(key)}&_sig=${encodeURIComponent(sig)}`;
    const imgRes = await this.http.request(imgUrl, { method: 'GET' });
    if (!imgRes.ok) return null;
    const imgBytes = await imgRes.arrayBuffer();

    // Step 3: OCR via PaddleOCR — plain fetch, no shared cookies
    let ocrText = null;
    const solvers = this._solvers || [paddleOcrSolver];
    for (const solver of solvers) {
      try {
        const result = await solver(imgBytes);
        if (result && result.trim()) {
          ocrText = result.trim();
          break;
        }
      } catch { /* ignore */ }
    }
    if (!ocrText) return null;

    // Step 4: submit solution — same cookie jar, form-encoded
    const formBody = new URLSearchParams({ response: ocrText, v, nonce, key, _sig: sig });
    const solRes = await this.http.request('https://dropmail.me/captcha/solution', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: formBody.toString(),
    });
    let solData;
    try { solData = await solRes.json(); } catch { return null; }
    if (solData.result !== 'correct') return null;

    // Step 5: retry token generation with 90d — same cookie jar
    const tokenRes = await this.http.request(TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ type: 'af', lifetime: '90d' }),
    });
    if (!tokenRes.ok) return null;
    const tokenData = await tokenRes.json();
    return tokenData.token || null;
  }

  /**
   * Generate token and create email address.
   * @returns {Promise<string>}
   */
  async generateEmail() {
    // Step 1: try 1d token (fast, no captcha)
    const tokenRes = await this.http.request(TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ type: 'af', lifetime: '1d' }),
    });

    if (tokenRes.ok) {
      const tokenData = await tokenRes.json();
      this._token = tokenData.token;
    } else if (tokenRes.status === 402) {
      // Captcha required — attempt 90d flow
      let captchaData = {};
      try {
        const body = await tokenRes.json();
        captchaData = body.captcha || {};
      } catch { /* ignore */ }

      try {
        this._token = await this._solveCaptchaAndGetToken(captchaData);
      } catch {
        this._token = null;
      }

      if (!this._token) {
        // Fallback: retry 1d on possibly-refreshed session
        console.warn('Dropmail: captcha solve failed, retrying with 1d token');
        const fallbackRes = await this._fetch(TOKEN_URL, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ type: 'af', lifetime: '1d' }),
        });
        if (!fallbackRes.ok) throw new TempMailError(`Token generation failed: HTTP ${fallbackRes.status}`, this.name);
        const fallbackData = await fallbackRes.json();
        this._token = fallbackData.token;
      }
    } else {
      throw new TempMailError(`Token generation failed: HTTP ${tokenRes.status}`, this.name);
    }

    if (!this._token) throw new TempMailError('No token in response', this.name);
    this._endpoint = `https://dropmail.me/api/graphql/${this._token}`;

    // Create session and address
    const mutation = `
      mutation {
        introduceSession {
          id
          addresses {
            id
            address
            restoreKey
          }
        }
      }
    `;

    const data = await this._graphql(mutation);
    this._sessionId = data.introduceSession.id;

    const addresses = data.introduceSession.addresses;
    if (!addresses || addresses.length === 0) {
      throw new TempMailError('No address returned', this.name);
    }

    this._addressId = addresses[0].id;
    this._email = addresses[0].address;

    return this._email;
  }

  /**
   * @param {string} _email
   * @returns {Promise<Message[]>}
   */
  async getInbox(_email) {
    const query = `
      query($id: ID!) {
        session(id: $id) {
          mails {
            id
            fromAddr
            headerSubject
            receivedAt
            text
            html
            attachments {
              id
              name
              mime
              rawSize
            }
          }
        }
      }
    `;

    const data = await this._graphql(query, { id: this._sessionId });
    const mails = data.session?.mails || [];

    return mails.map(m => new Message({
      id: m.id,
      sender: m.fromAddr || 'unknown',
      subject: m.headerSubject || '',
      date: m.receivedAt,
    }));
  }

  /**
   * @param {string} messageId
   * @returns {Promise<MessageDetail>}
   */
  async readMessage(messageId) {
    // messageId format: "id:email" or just "id"
    const id = messageId.split(':')[0];

    const query = `
      query($id: ID!) {
        session(id: $id) {
          mails {
            id
            fromAddr
            headerSubject
            receivedAt
            text
            html
            attachments {
              id
              name
              mime
              rawSize
            }
          }
        }
      }
    `;

    const data = await this._graphql(query, { id: this._sessionId });
    const mails = data.session?.mails || [];
    const mail = mails.find(m => m.id === id);

    if (!mail) throw new NotFoundError(`Message ${id} not found`, this.name);

    return new MessageDetail({
      id: messageId,
      sender: mail.fromAddr || 'unknown',
      subject: mail.headerSubject || '',
      date: mail.receivedAt,
      bodyText: mail.text || '',
      bodyHtml: mail.html || '',
      attachments: (mail.attachments || []).map(a => ({
        filename: a.name,
        contentType: a.mime,
        url: '', // Dropmail doesn't provide direct URL, would need download API
      })),
    });
  }

  /**
   * Delete the email address.
   * @param {string} _email
   * @returns {Promise<boolean>}
   */
  async deleteEmail(_email) {
    if (!this._addressId) return false;

    const mutation = `
      mutation($input: DeleteAddressInput!) {
        deleteAddress(input: $input)
      }
    `;

    try {
      await this._graphql(mutation, {
        input: { addressId: this._addressId },
      });
      this._token = null;
      this._endpoint = null;
      this._sessionId = null;
      this._addressId = null;
      this._email = null;
      return true;
    } catch {
      return false;
    }
  }
}
