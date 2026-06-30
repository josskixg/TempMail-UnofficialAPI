"""Example: Using mail.tm provider."""

from tempmail_wrapper import MailTmProvider


def main() -> None:
    provider = MailTmProvider()

    # Generate email (auto-registers account and gets Bearer token)
    email = provider.generate_email()
    print(f"Generated email: {email}")

    # Check inbox
    inbox = provider.get_inbox(email)
    print(f"Inbox has {len(inbox)} messages")

    # Wait for an email
    print("Waiting for email... (send one now!)")
    msg = provider.wait_for_email(email, timeout=120, interval=10)

    if msg:
        print(f"\nReceived from: {msg.sender}")
        print(f"Subject: {msg.subject}")

        # Read full message
        detail = provider.read_message(msg.id)
        print(f"\nBody:\n{detail.body_text}")

        if detail.attachments:
            print(f"\nAttachments: {len(detail.attachments)}")
    else:
        print("Timeout — no email received.")

    # Cleanup
    provider.delete_email(email)
    print("Session closed.")


if __name__ == "__main__":
    main()
