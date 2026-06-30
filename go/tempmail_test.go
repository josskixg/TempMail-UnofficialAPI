package tempmail_test

import (
	tempmail "github.com/josskixg/TempMail-UnofficialAPI/go"
	"github.com/josskixg/TempMail-UnofficialAPI/go/providers"
)

// Compile-time interface compliance checks.
var _ tempmail.TempMailProvider = (*providers.MailTM)(nil)
var _ tempmail.TempMailProvider = (*providers.GuerrillaMail)(nil)
var _ tempmail.TempMailProvider = (*providers.YOPmail)(nil)
var _ tempmail.TempMailProvider = (*providers.Dropmail)(nil)
