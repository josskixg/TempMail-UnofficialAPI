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
            _ => throw new ArgumentException($"Unknown provider: {name}. Supported: mailtm, guerrillamail, yopmail, dropmail, 1secemail, ncaori", nameof(name))
        };
    }
}
