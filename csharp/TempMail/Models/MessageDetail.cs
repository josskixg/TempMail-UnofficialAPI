using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace TempMail.Models;

public record MessageDetail : Message
{
    public string BodyText    { get; init; }
    public string BodyHtml    { get; init; }
    public string BodyPreview { get; init; }
    public string ContentType { get; init; }
    public string Raw         { get; init; }
    public Dictionary<string, string> Headers { get; init; }
    public List<string> Cc    { get; init; }
    public string ReplyTo     { get; init; }
    public string MessageIdHeader { get; init; }
    public int Size           { get; init; }
    public bool IsHtml        { get; init; }
    public List<JsonElement> Attachments { get; init; }

    public MessageDetail(
        string Id, string Sender, string Subject, DateTimeOffset Date,
        string bodyText = "", string bodyHtml = "",
        List<JsonElement>? attachments = null,
        string bodyPreview = "", string contentType = "",
        string raw = "", Dictionary<string, string>? headers = null,
        List<string>? cc = null, string replyTo = "",
        string messageIdHeader = "", int size = 0)
        : base(Id, Sender, Subject, Date, Preview: "", HasAttachments: (attachments?.Count ?? 0) > 0)
    {
        BodyHtml    = bodyHtml ?? "";
        Attachments = attachments ?? new List<JsonElement>();
        Raw         = raw ?? "";
        Headers     = headers != null ? new Dictionary<string, string>(headers) : new Dictionary<string, string>();
        Cc          = cc ?? new List<string>();
        ReplyTo     = replyTo ?? "";
        MessageIdHeader = messageIdHeader ?? "";
        Size        = size;

        // Normalize
        bool hasHtml = !string.IsNullOrWhiteSpace(bodyHtml);
        bool hasText = !string.IsNullOrWhiteSpace(bodyText);

        BodyText = hasText ? bodyText! : (hasHtml ? StripHtml(bodyHtml!) : "");
        IsHtml   = hasHtml;

        ContentType = hasHtml && BodyText.Length > 0 ? "multipart/alternative"
                    : hasHtml                        ? "text/html"
                    : !string.IsNullOrEmpty(contentType) ? contentType
                    : "text/plain";

        var preview = !string.IsNullOrEmpty(bodyPreview) ? bodyPreview
                    : BodyText.Length > 200 ? BodyText[..200].Trim()
                    : BodyText.Trim();
        BodyPreview = preview;

        if (!string.IsNullOrEmpty(MessageIdHeader) && !Headers.ContainsKey("Message-ID"))
            Headers["Message-ID"] = MessageIdHeader;
    }

    /// <summary>Strip HTML tags and decode entities to plain text.</summary>
    public static string StripHtml(string html)
    {
        if (string.IsNullOrEmpty(html)) return "";
        var s = Regex.Replace(html, @"<(style|script)[^>]*>[\s\S]*?</\1>", "", RegexOptions.IgnoreCase);
        s = Regex.Replace(s, @"<(br\s*/?|/p|/div|/tr|/li|/h\d)>", "\n", RegexOptions.IgnoreCase);
        s = Regex.Replace(s, "<[^>]+>", "");
        s = s.Replace("&amp;", "&").Replace("&lt;", "<").Replace("&gt;", ">")
             .Replace("&quot;", "\"").Replace("&#39;", "'").Replace("&nbsp;", " ");
        s = Regex.Replace(s, @"\n{3,}", "\n\n");
        return s.Trim();
    }
}
