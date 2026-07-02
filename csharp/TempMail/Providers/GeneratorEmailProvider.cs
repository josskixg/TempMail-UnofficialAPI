namespace TempMail.Providers;

// ponytail: thin subclass — same backend as emailfake.com, different domain list and URL
public sealed class GeneratorEmailProvider(HttpClient httpClient) : EmailfakeProvider(httpClient)
{
    protected override string BaseUrl => "https://generator.email";
    protected override string SiteName => "generator";
}
