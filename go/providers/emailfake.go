package providers

// Emailfake is the emailfake.com provider (gasmurl scraping backend).
type Emailfake struct {
	*gasmurlBackend
}

// NewEmailfake creates a new Emailfake provider.
func NewEmailfake(config map[string]string) *Emailfake {
	return &Emailfake{newGasmurlBackend(config, "emailfake", "https://emailfake.com", ".emailfake.com")}
}
