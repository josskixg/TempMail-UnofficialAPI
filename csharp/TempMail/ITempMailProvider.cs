using TempMail.Models;

namespace TempMail;

public interface ITempMailProvider
{
    Task<string> GenerateEmailAsync(CancellationToken ct = default);
    Task<IReadOnlyList<Message>> GetInboxAsync(string email, CancellationToken ct = default);
    Task<MessageDetail> ReadMessageAsync(string messageId, CancellationToken ct = default);
    Task<bool> DeleteEmailAsync(string email, CancellationToken ct = default);
    Task<Message?> WaitForEmailAsync(string email, TimeSpan timeout, TimeSpan interval, CancellationToken ct = default);
}
