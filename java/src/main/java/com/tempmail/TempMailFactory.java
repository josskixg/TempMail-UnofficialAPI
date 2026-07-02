package com.tempmail;

import com.tempmail.providers.DropmailProvider;
import com.tempmail.providers.GuerrillaMailProvider;
import com.tempmail.providers.MailTmProvider;
import com.tempmail.providers.YOPmailProvider;
import com.tempmail.providers.OneSecEmailProvider;
import com.tempmail.providers.NcaoriMailProvider;
import com.tempmail.providers.ZoromailProvider;
import com.tempmail.providers.TempmailLolProvider;
import com.tempmail.providers.TempmailcProvider;
import com.tempmail.providers.TempMailIoProvider;
import com.tempmail.providers.TempmailPlusProvider;
import com.tempmail.providers.EmailfakeProvider;
import com.tempmail.providers.GeneratorEmailProvider;
import com.tempmail.providers.MailnesiaProvider;
import com.tempmail.providers.TenMinuteMailProvider;
import com.tempmail.providers.EmailTempProvider;

public final class TempMailFactory {

    public enum Service {
        MAILTM,
        GUERRILLAMAIL,
        YOPMAIL,
        DROPMAIL,
        ONESECMAIL,
        NCAORIMAIL,
        ZOROMAIL,
        TEMPMAIL_LOL,
        TEMPMAILC,
        TEMPMAIL_IO,
        TEMPMAIL_PLUS,
        EMAILFAKE,
        GENERATOR_EMAIL,
        MAILNESIA,
        TENMINUTEMAIL,
        EMAIL_TEMP
    }

    private TempMailFactory() {
    }

    public static TempMailProvider create(Service service) {
        switch (service) {
            case MAILTM:
                return new MailTmProvider();
            case GUERRILLAMAIL:
                return new GuerrillaMailProvider();
            case YOPMAIL:
                return new YOPmailProvider();
            case DROPMAIL:
                return new DropmailProvider();
            case ONESECMAIL:
                return new OneSecEmailProvider();
            case NCAORIMAIL:
                return new NcaoriMailProvider();
            case ZOROMAIL:
                return new ZoromailProvider();
            case TEMPMAIL_LOL:
                return new TempmailLolProvider();
            case TEMPMAILC:
                return new TempmailcProvider();
            case TEMPMAIL_IO:
                return new TempMailIoProvider();
            case TEMPMAIL_PLUS:
                return new TempmailPlusProvider();
            case EMAILFAKE:
                return new EmailfakeProvider();
            case GENERATOR_EMAIL:
                return new GeneratorEmailProvider();
            case MAILNESIA:
                return new MailnesiaProvider();
            case TENMINUTEMAIL:
                return new TenMinuteMailProvider();
            case EMAIL_TEMP:
                return new EmailTempProvider();
            default:
                throw new IllegalArgumentException("Unknown service: " + service);
        }
    }

    /**
     * Create a provider by name.
     *
     * @param provider One of: "mail_tm", "guerrilla_mail", "yopmail", "dropmail",
     *                 "1secemail", "zoromail", "tempmail.lol", "tempmailc",
     *                 "temp-mail.io", "tempmail.plus", "emailfake", "generator.email",
     *                 "mailnesia", "10minutemail", "email-temp"
     * @return TempMailProvider instance
     */
    public static TempMailProvider create(String provider) {
        switch (provider.toLowerCase()) {
            case "mail_tm":
            case "mailtm":
                return new MailTmProvider();
            case "guerrilla_mail":
            case "guerrilla":
                return new GuerrillaMailProvider();
            case "yopmail":
            case "yop":
                return new YOPmailProvider();
            case "dropmail":
            case "dropmail.me":
                return new DropmailProvider();
            case "1secemail":
            case "onesecmail":
                return new OneSecEmailProvider();
            case "ncaori":
            case "ncaorimail":
                return new NcaoriMailProvider();
            case "zoromail":
                return new ZoromailProvider();
            case "tempmail.lol":
            case "tempmail_lol":
                return new TempmailLolProvider();
            case "tempmailc":
                return new TempmailcProvider();
            case "temp-mail.io":
            case "tempmail.io":
            case "temp_mail_io":
                return new TempMailIoProvider();
            case "tempmail.plus":
            case "tempmail_plus":
                return new TempmailPlusProvider();
            case "emailfake":
                return new EmailfakeProvider();
            case "generator.email":
            case "generator_email":
                return new GeneratorEmailProvider();
            case "mailnesia":
                return new MailnesiaProvider();
            case "10minutemail":
            case "tenminutemail":
                return new TenMinuteMailProvider();
            case "email-temp":
            case "email_temp":
                return new EmailTempProvider();
            default:
                throw new IllegalArgumentException("Unknown provider: " + provider
                        + ". Supported: mail_tm, guerrilla_mail, yopmail, dropmail, 1secemail, ncaori, "
                        + "zoromail, tempmail.lol, tempmailc, temp-mail.io, tempmail.plus, "
                        + "emailfake, generator.email, mailnesia, 10minutemail, email-temp");
        }
    }

    public static TempMailProvider createMailTm() {
        return new MailTmProvider();
    }

    public static TempMailProvider createGuerrillaMail() {
        return new GuerrillaMailProvider();
    }

    public static TempMailProvider createYOPmail() {
        return new YOPmailProvider();
    }

    public static TempMailProvider createDropmail() {
        return new DropmailProvider();
    }
}
