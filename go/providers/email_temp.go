package providers

// EmailTemp is the email-temp.com provider (same gasmurl backend as emailfake).
type EmailTemp struct {
	*gasmurlBackend
}

// NewEmailTemp creates a new EmailTemp provider.
func NewEmailTemp(config map[string]string) *EmailTemp {
	return &EmailTemp{newGasmurlBackend(config, "email-temp", "https://email-temp.com", ".email-temp.com")}
}
