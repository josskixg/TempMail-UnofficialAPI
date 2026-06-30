package main

import (
	"fmt"
	"log"
	"time"

	tempmail "github.com/josskixg/TempMail-UnofficialAPI/go"
)

func main() {
	// --- Mail.tm (no auth needed for account creation) ---
	fmt.Println("=== Mail.tm ===")
	p1, err := tempmail.NewProvider("mail.tm", nil)
	if err != nil {
		log.Fatal(err)
	}
	demo(p1)

	// --- Guerrilla Mail (no auth) ---
	fmt.Println("\n=== Guerrilla Mail ===")
	p2, err := tempmail.NewProvider("guerrillamail", nil)
	if err != nil {
		log.Fatal(err)
	}
	demo(p2)

	// --- YOPmail (HTML scraping, no auth) ---
	fmt.Println("\n=== YOPmail ===")
	p3, err := tempmail.NewProvider("yopmail", nil)
	if err != nil {
		log.Fatal(err)
	}
	demo(p3)

	// --- Dropmail (GraphQL, no auth) ---
	fmt.Println("\n=== Dropmail ===")
	p4, err := tempmail.NewProvider("dropmail", nil)
	if err != nil {
		log.Fatal(err)
	}
	demo(p4)
}

func demo(p tempmail.TempMailProvider) {
	email, err := p.GenerateEmail()
	if err != nil {
		fmt.Printf("GenerateEmail error: %v\n", err)
		return
	}
	fmt.Printf("Generated: %s\n", email)

	msgs, err := p.GetInbox(email)
	if err != nil {
		fmt.Printf("GetInbox error: %v\n", err)
		return
	}
	fmt.Printf("Inbox: %d message(s)\n", len(msgs))

	for _, msg := range msgs {
		fmt.Printf("  - [%s] %s: %s\n", msg.ID, msg.Sender, msg.Subject)

		detail, err := p.ReadMessage(msg.ID)
		if err != nil {
			fmt.Printf("    ReadMessage error: %v\n", err)
			continue
		}
		fmt.Printf("    Body: %d chars text, %d chars html, %d attachment(s)\n",
			len(detail.BodyText), len(detail.BodyHTML), len(detail.Attachments))
	}

	// Example: wait for email with timeout (commented out to keep example fast)
	// msg, err := p.WaitForEmail(email, 30*time.Second, 5*time.Second)
	// if err != nil {
	//     fmt.Printf("WaitForEmail: %v\n", err)
	// } else {
	//     fmt.Printf("Received: %s - %s\n", msg.Sender, msg.Subject)
	// }
	_ = time.Second

	del, err := p.DeleteEmail(email)
	if err != nil {
		fmt.Printf("DeleteEmail: %v\n", err)
	} else {
		fmt.Printf("Deleted: %v\n", del)
	}
}
