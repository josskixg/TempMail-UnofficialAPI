/**
 * Base error for all temp mail operations.
 */
export class TempMailError extends Error {
  /**
   * @param {string} message
   * @param {string} [provider]
   */
  constructor(message, provider) {
    super(message);
    this.name = 'TempMailError';
    this.provider = provider;
  }
}

/**
 * Thrown when a provider rate-limits the request.
 */
export class RateLimitError extends TempMailError {
  /**
   * @param {string} message
   * @param {string} [provider]
   * @param {number} [retryAfterMs]
   */
  constructor(message, provider, retryAfterMs) {
    super(message, provider);
    this.name = 'RateLimitError';
    this.retryAfterMs = retryAfterMs;
  }
}

/**
 * Thrown when a requested resource is not found.
 */
export class NotFoundError extends TempMailError {
  /**
   * @param {string} message
   * @param {string} [provider]
   */
  constructor(message, provider) {
    super(message, provider);
    this.name = 'NotFoundError';
  }
}
