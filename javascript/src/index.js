export { TempMailProvider } from './base.js';
export { Message, MessageDetail } from './models.js';
export { TempMailError, RateLimitError, NotFoundError } from './errors.js';
export { HttpClient } from './httpclient.js';
export { MailTm } from './providers/MailTm.js';
export { GuerrillaMail } from './providers/GuerrillaMail.js';
export { YOPmail } from './providers/YOPmail.js';
export { Dropmail } from './providers/Dropmail.js';
export { OneSecEmail } from './providers/OneSecEmail.js';

import { MailTm } from './providers/MailTm.js';
import { GuerrillaMail } from './providers/GuerrillaMail.js';
import { YOPmail } from './providers/YOPmail.js';
import { Dropmail } from './providers/Dropmail.js';
import { OneSecEmail } from './providers/OneSecEmail.js';
import { TempMailError } from './errors.js';

/**
 * Factory: create a provider by name.
 *
 * @param {'mailtm'|'guerrillamail'|'yopmail'|'dropmail'|'1secemail'} name
 * @param {object} [config]
 * @returns {import('./base.js').TempMailProvider}
 */
export function createProvider(name, config = {}) {
  switch (name.toLowerCase()) {
    case 'mailtm':
    case 'mail.tm':
      return new MailTm(config);
    case 'guerrillamail':
    case 'guerrilla':
      return new GuerrillaMail(config);
    case 'yopmail':
    case 'yop':
      return new YOPmail(config);
    case 'dropmail':
    case 'dropmail.me':
      return new Dropmail(config);
    case '1secemail':
      return new OneSecEmail(config);
    default:
      throw new TempMailError(`Unknown provider: ${name}`);
  }
}
