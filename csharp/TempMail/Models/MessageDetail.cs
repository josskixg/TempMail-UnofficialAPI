using System.Text.Json;

namespace TempMail.Models;

public record MessageDetail(
    string Id,
    string Sender,
    string Subject,
    DateTimeOffset Date,
    string BodyText,
    string BodyHtml,
    List<JsonElement> Attachments
) : Message(Id, Sender, Subject, Date);
