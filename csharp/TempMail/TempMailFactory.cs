using TempMail.Providers;

namespace TempMail;

public static class TempMailFactory
{
    public static ITempMailProvider Create(string name, TempMailConfig? config = null, HttpClient? httpClient = null)
    {
        config ??= new TempMailConfig();
        httpClient ??= new HttpClient(useCookies: false);

        return name.ToLowerInvariant() switch
        {
            "mailtm" or "mail.tm" => new MailTmProvider(httpClient),
            "guerrillamail" or "guerrilla" => new GuerrillaMailProvider(httpClient ?? new HttpClient(useCookies: true)),
            "yopmail" => new YOPmailProvider(httpClient ?? new HttpClient(useCookies: true)),
            "dropmail" or "dropmail.me" => new DropmailProvider(httpClient),
            "1secemail" => new OneSecEmailProvider(httpClient ?? new HttpClient(useCookies: true)),
            "ncaori" or "ncaorimail" or "nca.my.id" => new NcaoriMailProvider(httpClient ?? new HttpClient(useCookies: false)),
            "zoromail" => new ZoromailProvider(httpClient),
            "tempmail.lol" => new TempmailLolProvider(httpClient),
            "tempmailc" => new TempmailcProvider(httpClient),
            "temp-mail.io" => new TempMailIoProvider(httpClient),
            "tempmail.plus" => new TempmailPlusProvider(httpClient),
            "emailfake" => new EmailfakeProvider(httpClient),
            "generator.email" => new GeneratorEmailProvider(httpClient),
            "mailnesia" => new MailnesiaProvider(httpClient),
            "10minutemail" => new TenMinuteMailProvider(httpClient),
            "email-temp" => new EmailTempProvider(httpClient),
            _ => throw new ArgumentException($"Unknown provider: {name}. Supported: mailtm, guerrillamail, yopmail, dropmail, 1secemail, ncaori, zoromail, tempmail.lol, tempmailc, temp-mail.io, tempmail.plus, emailfake, generator.email, mailnesia, 10minutemail, email-temp", nameof(name))
        };
    }
}
