namespace TempMail.Exceptions;

public class TempMailException(string message, Exception? innerException = null)
    : Exception(message, innerException);
