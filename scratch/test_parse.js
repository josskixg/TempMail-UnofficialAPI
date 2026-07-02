import fs from 'fs';

const html = fs.readFileSync('10min_mail.html', 'utf8');

// Decode Cloudflare email obfuscation
function decodeCfEmail(hex) {
  let str = '';
  const k = parseInt(hex.substr(0, 2), 16);
  for (let i = 2; i < hex.length; i += 2) {
    str += String.fromCharCode(parseInt(hex.substr(i, 2), 16) ^ k);
  }
  return str;
}

// Strip HTML tags helper
function stripHtml(html) {
  return html.replace(/<[^>]+>/g, '').trim();
}

// Extract body
const bodyMatch = html.match(/class="mailinhtml"[^>]*>([\s\S]*?)<div[^>]*style="clear:both;"/i);
const bodyHtml = bodyMatch ? bodyMatch[1].trim() : '';

// Replace any cloudflare email links inside bodyHtml
let cleanBodyHtml = bodyHtml.replace(/<(a|span)[^>]*class="__cf_email__"[^>]*data-cfemail="([^"]+)"[^>]*>([\s\S]*?)<\/\1>/gi, (m, tag, hex) => {
  return decodeCfEmail(hex);
});

// Also replace href="/cdn-cgi/l/email-protection#..." with decoded email
cleanBodyHtml = cleanBodyHtml.replace(/href="\/cdn-cgi\/l\/email-protection#([^"]+)"/g, (m, hex) => {
  return `href="mailto:${decodeCfEmail(hex)}"`;
});

const bodyText = stripHtml(cleanBodyHtml);

console.log("BODY HTML EXCERPT:");
console.log(cleanBodyHtml.substring(0, 500));
console.log("\nBODY TEXT EXCERPT:");
console.log(bodyText.substring(0, 500));
