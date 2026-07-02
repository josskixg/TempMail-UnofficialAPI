import { Emailfake } from './Emailfake.js';

/**
 * generator.email provider — same gasmurl backend as emailfake.com.
 * Only the base URL and provider name differ; all logic is inherited.
 */
export class GeneratorEmail extends Emailfake {
  /** @param {{ randomUA?: boolean, useCookies?: boolean }|undefined} opts */
  constructor(opts = {}) {
    super(opts, 'generator.email', 'https://generator.email');
  }
}
