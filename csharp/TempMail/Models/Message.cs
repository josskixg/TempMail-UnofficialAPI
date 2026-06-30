using System.Text.Json.Serialization;

namespace TempMail.Models;

public record Message(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("sender")] string Sender,
    [property: JsonPropertyName("subject")] string Subject,
    [property: JsonPropertyName("date")] DateTimeOffset Date
);
