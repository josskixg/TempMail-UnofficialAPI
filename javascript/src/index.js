export { TempMailProvider } from './base.js';
export { Message, MessageDetail } from './models.js';
export { TempMailError, RateLimitError, NotFoundError } from './errors.js';
export { HttpClient } from './httpclient.js';
export { MailTm } from './providers/MailTm.js';
export { GuerrillaMail } from './providers/GuerrillaMail.js';
export { YOPmail } from './providers/YOPmail.js';
export { Dropmail } from './providers/Dropmail.js';
export { OneSecEmail } from './providers/OneSecEmail.js';
export { NcaoriMail } from './providers/NcaoriMail.js';
export { Zoromail } from './providers/Zoromail.js';
export { TempmailLol } from './providers/TempmailLol.js';
export { Tempmailc } from './providers/Tempmailc.js';
export { TempMailIo } from './providers/TempMailIo.js';
export { TempmailPlus } from './providers/TempmailPlus.js';
export { Emailfake } from './providers/Emailfake.js';
export { GeneratorEmail } from './providers/GeneratorEmail.js';
export { Mailnesia } from './providers/Mailnesia.js';
export { TenMinuteMail } from './providers/TenMinuteMail.js';
export { EmailTemp } from './providers/EmailTemp.js';

import { MailTm } from './providers/MailTm.js';
import { GuerrillaMail } from './providers/GuerrillaMail.js';
import { YOPmail } from './providers/YOPmail.js';
import { Dropmail } from './providers/Dropmail.js';
import { OneSecEmail } from './providers/OneSecEmail.js';
import { NcaoriMail } from './providers/NcaoriMail.js';
import { Zoromail } from './providers/Zoromail.js';
import { TempmailLol } from './providers/TempmailLol.js';
import { Tempmailc } from './providers/Tempmailc.js';
import { TempMailIo } from './providers/TempMailIo.js';
import { TempmailPlus } from './providers/TempmailPlus.js';
import { Emailfake } from './providers/Emailfake.js';
import { GeneratorEmail } from './providers/GeneratorEmail.js';
import { Mailnesia } from './providers/Mailnesia.js';
import { TenMinuteMail } from './providers/TenMinuteMail.js';
import { EmailTemp } from './providers/EmailTemp.js';
import { TempMailError } from './errors.js';

/**
 * Factory: create a provider by name.
 *
 * @param {string} name - provider key (case-insensitive)
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
    case 'ncaori':
    case 'ncaorimail':
    case 'nca.my.id':
      return new NcaoriMail(config);
    case 'zoromail':
      return new Zoromail(config);
    case 'tempmail.lol':
      return new TempmailLol(config);
    case 'tempmailc':
      return new Tempmailc(config);
    case 'temp-mail.io':
      return new TempMailIo(config);
    case 'tempmail.plus':
      return new TempmailPlus(config);
    case 'emailfake':
      return new Emailfake(config);
    case 'generator.email':
      return new GeneratorEmail(config);
    case 'mailnesia':
      return new Mailnesia(config);
    case '10minutemail':
      return new TenMinuteMail(config);
    case 'email-temp':
      return new EmailTemp(config);
    default:
      throw new TempMailError(`Unknown provider: ${name}`);
  }
}
