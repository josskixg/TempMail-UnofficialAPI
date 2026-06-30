namespace TempMail.Exceptions;

public class RateLimitException(string message, Exception? innerException = null)
    : TempMailException(message, innerException);
