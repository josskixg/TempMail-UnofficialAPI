namespace TempMail.Providers;

// ponytail: thin subclass — same backend family as emailfake.com, different domain list and URL
public sealed class EmailTempProvider(HttpClient httpClient) : EmailfakeProvider(httpClient)
{
    protected override string BaseUrl => "https://email-temp.com";
    protected override string SiteName => "email-temp";
}
