package providers

// GeneratorEmail is the generator.email provider (same gasmurl backend as emailfake).
type GeneratorEmail struct {
	*gasmurlBackend
}

// NewGeneratorEmail creates a new GeneratorEmail provider.
func NewGeneratorEmail(config map[string]string) *GeneratorEmail {
	return &GeneratorEmail{newGasmurlBackend(config, "generator.email", "https://generator.email", ".generator.email")}
}
