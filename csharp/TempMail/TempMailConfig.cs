namespace TempMail;

public sealed class TempMailConfig
{
    public string? RapidApiKey { get; init; }
    public string? MailtrapApiToken { get; init; }
    public string? MailtrapAccountId { get; init; }
    public string? GuerrillaMailToken { get; init; }
    public TimeSpan DefaultTimeout { get; init; } = TimeSpan.FromMinutes(2);
    public TimeSpan DefaultPollInterval { get; init; } = TimeSpan.FromSeconds(5);
}
