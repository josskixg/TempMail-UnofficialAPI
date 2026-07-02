import { Emailfake } from './Emailfake.js';

/**
 * email-temp.com provider — same gasmurl backend as emailfake.com.
 * Only the base URL and provider name differ; all logic is inherited.
 */
export class EmailTemp extends Emailfake {
  /** @param {{ randomUA?: boolean, useCookies?: boolean }|undefined} opts */
  constructor(opts = {}) {
    super(opts, 'email-temp', 'https://email-temp.com');
  }
}
