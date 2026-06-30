namespace TempMail.Exceptions;

public class NotFoundException(string message, Exception? innerException = null)
    : TempMailException(message, innerException);
