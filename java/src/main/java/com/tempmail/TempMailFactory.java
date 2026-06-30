package com.tempmail;

import com.tempmail.providers.DropmailProvider;
import com.tempmail.providers.GuerrillaMailProvider;
import com.tempmail.providers.MailTmProvider;
import com.tempmail.providers.YOPmailProvider;
import com.tempmail.providers.OneSecEmailProvider;

public final class TempMailFactory {

    public enum Service {
        MAILTM,
        GUERRILLAMAIL,
        YOPMAIL,
        DROPMAIL,
        ONESECMAIL
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
            default:
                throw new IllegalArgumentException("Unknown service: " + service);
        }
    }

    /**
     * Create a provider by name.
     *
     * @param provider One of: "mail_tm", "guerrilla_mail", "yopmail", "dropmail"
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
            default:
                throw new IllegalArgumentException("Unknown provider: " + provider
                        + ". Supported: mail_tm, guerrilla_mail, yopmail, dropmail");
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
