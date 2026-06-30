package providers

import (
	"bytes"
	"crypto/tls"
	"io"
	"math/rand"
	"net/http"
	"net/http/cookiejar"
	"net/url"
	"strings"
	"sync"
	"time"
)

// 53 user-agent strings to rotate through.
var uaPool = []string{
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
	"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0",
	"Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:130.0) Gecko/20100101 Firefox/130.0",
	"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0",
	"Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
	"Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
	"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0",
	"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Windows NT 10.0; ARM64; rv:131.0) Gecko/20100101 Firefox/131.0",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
	"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0",
	"Mozilla/5.0 (Android 14; Mobile; rv:131.0) Gecko/131.0 Firefox/131.0",
	"Mozilla/5.0 (iPad; CPU OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1",
	"Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
	"Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
	"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:131.0) Gecko/20100101 Firefox/131.0",
	"Mozilla/5.0 (Windows NT 11.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 13_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
	"Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Linux; Android 13; SM-A546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
	"Mozilla/5.0 (Linux; Android 14; moto g73 5G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36",
	"Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 OPR/116.0.0.0",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 OPR/116.0.0.0",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 OPR/117.0.0.0",
	"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
}

var defaultBackoff = []time.Duration{1 * time.Second, 3 * time.Second, 5 * time.Second}

// HttpClient wraps http.Client with anti-429 features:
// UA rotation, proxy rotation, and automatic retry with backoff.
type HttpClient struct {
	transport *http.Transport
	jar       http.CookieJar
	timeout   time.Duration
	proxies   []string
	randomUA  bool
	mu        sync.Mutex
	rng       *rand.Rand
}

// NewHttpClient creates an HTTP client with optional proxy rotation, random UA, and cookie support.
func NewHttpClient(proxies []string, randomUA bool, useCookies bool) *HttpClient {
	var jar http.CookieJar
	if useCookies {
		jar, _ = cookiejar.New(nil)
	}

	return &HttpClient{
		transport: &http.Transport{
			TLSClientConfig: &tls.Config{InsecureSkipVerify: false},
		},
		jar:      jar,
		timeout:  15 * time.Second,
		proxies:  proxies,
		randomUA: randomUA,
		rng:      rand.New(rand.NewSource(time.Now().UnixNano())),
	}
}

// ParseProxies parses a comma-separated string of proxy URLs.
func ParseProxies(s string) []string {
	if s == "" {
		return nil
	}
	parts := strings.Split(s, ",")
	result := make([]string, 0, len(parts))
	for _, p := range parts {
		trimmed := strings.TrimSpace(p)
		if trimmed != "" {
			result = append(result, trimmed)
		}
	}
	return result
}

// Get performs a GET request with retry and proxy rotation.
func (h *HttpClient) Get(url string) ([]byte, error) {
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, &TempMailError{Provider: "httpclient", Message: err.Error()}
	}
	return h.do(req, nil)
}

// GetWithHeaders performs a GET request with custom headers.
func (h *HttpClient) GetWithHeaders(url string, headers map[string]string) ([]byte, error) {
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, &TempMailError{Provider: "httpclient", Message: err.Error()}
	}
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	return h.do(req, nil)
}

// Post performs a POST request with retry and proxy rotation.
// Reads body into memory for safe retry.
func (h *HttpClient) Post(url string, contentType string, body io.Reader) ([]byte, error) {
	var bodyBytes []byte
	if body != nil {
		var err error
		bodyBytes, err = io.ReadAll(body)
		if err != nil {
			return nil, &TempMailError{Provider: "httpclient", Message: err.Error()}
		}
	}

	req, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, &TempMailError{Provider: "httpclient", Message: err.Error()}
	}
	req.Header.Set("Content-Type", contentType)
	return h.do(req, bodyBytes)
}

// PostWithHeaders performs a POST request with custom headers.
func (h *HttpClient) PostWithHeaders(url string, contentType string, body io.Reader, headers map[string]string) ([]byte, error) {
	var bodyBytes []byte
	if body != nil {
		var err error
		bodyBytes, err = io.ReadAll(body)
		if err != nil {
			return nil, &TempMailError{Provider: "httpclient", Message: err.Error()}
		}
	}

	req, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, &TempMailError{Provider: "httpclient", Message: err.Error()}
	}
	req.Header.Set("Content-Type", contentType)
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	return h.do(req, bodyBytes)
}

// SetCookie adds a cookie to the jar for the given URL.
func (h *HttpClient) SetCookie(rawURL string, cookie *http.Cookie) {
	u, err := url.Parse(rawURL)
	if err != nil || h.jar == nil {
		return
	}
	cookies := h.jar.Cookies(u)
	for i, c := range cookies {
		if c.Name == cookie.Name {
			cookies[i] = cookie
			h.jar.SetCookies(u, cookies)
			return
		}
	}
	h.jar.SetCookies(u, append(cookies, cookie))
}

// GetClient returns a raw *http.Client that shares the same transport and cookie jar.
// Use with RawDo for providers that need custom request construction.
func (h *HttpClient) GetClient() *http.Client {
	return &http.Client{
		Transport: h.transport,
		Jar:       h.jar,
		Timeout:   h.timeout,
	}
}

// RawDo executes a caller-built request through HttpClient's UA/proxy/retry machinery.
// Used by providers that need custom request construction (e.g. yopmail scraping flow).
// bodyBytes should be provided for POST requests so retry can rewind the body.
func (h *HttpClient) RawDo(req *http.Request, bodyBytes []byte) ([]byte, error) {
	return h.do(req, bodyBytes)
}

// do executes req with UA rotation, proxy rotation, and retry on 429/network errors.
func (h *HttpClient) do(req *http.Request, bodyBytes []byte) ([]byte, error) {
	maxAttempts := len(defaultBackoff) + 1

	for attempt := 0; attempt < maxAttempts; attempt++ {
		if err := h.prepareRequest(req, bodyBytes); err != nil {
			return nil, err
		}

		client := h.getClient()
		resp, err := client.Do(req)
		if err != nil {
			if attempt < maxAttempts-1 {
				time.Sleep(defaultBackoff[attempt])
			}
			continue
		}

		body, readErr := io.ReadAll(resp.Body)
		resp.Body.Close()

		if resp.StatusCode == http.StatusTooManyRequests && attempt < maxAttempts-1 {
			time.Sleep(defaultBackoff[attempt])
			continue
		}

		if readErr != nil {
			return nil, &TempMailError{Provider: "httpclient", Message: readErr.Error()}
		}

		return body, nil
	}

	return nil, &TempMailError{Provider: "httpclient", Message: "max retries exceeded"}
}

// prepareRequest sets UA and proxy for this attempt.
func (h *HttpClient) prepareRequest(req *http.Request, bodyBytes []byte) error {
	if h.randomUA {
		h.mu.Lock()
		ua := uaPool[h.rng.Intn(len(uaPool))]
		h.mu.Unlock()
		req.Header.Set("User-Agent", ua)
	}

	proxyCount := len(h.proxies)
	if proxyCount > 0 {
		h.mu.Lock()
		proxyURL := h.proxies[h.rng.Intn(proxyCount)]
		h.mu.Unlock()

		proxy, err := url.Parse(proxyURL)
		if err != nil {
			return &TempMailError{Provider: "httpclient", Message: "invalid proxy: " + proxyURL}
		}
		h.transport.Proxy = http.ProxyURL(proxy)
	} else {
		h.transport.Proxy = nil
	}

	// Reset body for retries.
	if bodyBytes != nil {
		req.Body = io.NopCloser(bytes.NewReader(bodyBytes))
		req.ContentLength = int64(len(bodyBytes))
	}

	return nil
}

// getClient returns an http.Client using the shared transport and jar.
func (h *HttpClient) getClient() *http.Client {
	return &http.Client{
		Transport: h.transport,
		Jar:       h.jar,
		Timeout:   h.timeout,
	}
}
