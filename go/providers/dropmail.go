package providers

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

const dropmailPaddleOCRURL = "https://mamamacjdjj-padle-predict.hf.space/predict"

// PaddleOCRSolver solves a captcha image via PaddleOCR HuggingFace space (3 retries).
func PaddleOCRSolver(imgBytes []byte) string {
	for attempt := 0; attempt < 3; attempt++ {
		var buf bytes.Buffer
		mw := multipart.NewWriter(&buf)
		part, _ := mw.CreateFormFile("file", "cap.png")
		part.Write(imgBytes)
		mw.Close()

		ocrResp, err := http.Post(dropmailPaddleOCRURL, mw.FormDataContentType(), &buf) //nolint:noctx
		if err != nil {
			continue
		}
		var ocrData struct {
			Results []struct {
				Text       string  `json:"text"`
				Confidence float64 `json:"confidence"`
			} `json:"results"`
		}
		json.NewDecoder(ocrResp.Body).Decode(&ocrData)
		ocrResp.Body.Close()

		if len(ocrData.Results) > 0 && ocrData.Results[0].Confidence >= 0.7 {
			if text := strings.TrimSpace(ocrData.Results[0].Text); text != "" {
				return text
			}
		}
	}
	return ""
}

type Dropmail struct {
	client         *HttpClient
	sessions       map[string]*dropmailSession
	mu             sync.Mutex
	CaptchaSolvers []func(imgBytes []byte) string
}

type dropmailSession struct {
	Token     string
	SessionID string
	AddressID string
}

func NewDropmail(config map[string]string) *Dropmail {
	proxies := ParseProxies(config["proxies"])
	randomUA := config["randomUa"] == "true"

	return &Dropmail{
		client:   NewHttpClient(proxies, randomUA, false),
		sessions: make(map[string]*dropmailSession),
	}
}

// dropmailRawPost performs a POST using the shared HttpClient cookie jar,
// returning the raw http.Response (caller must close Body).
func (d *Dropmail) dropmailRawPost(url string, contentType string, body []byte) (*http.Response, error) {
	req, _ := http.NewRequest(http.MethodPost, url, bytes.NewReader(body))
	req.Header.Set("Content-Type", contentType)
	// Use GetClient so cookies are shared with the jar.
	client := d.client.GetClient()
	return client.Do(req)
}

// dropmailRawGet performs a GET using the shared HttpClient cookie jar.
func (d *Dropmail) dropmailRawGet(rawURL string) (*http.Response, error) {
	req, _ := http.NewRequest(http.MethodGet, rawURL, nil)
	client := d.client.GetClient()
	return client.Do(req)
}

// solveCaptchaAndGetToken runs the 5-step 90d captcha flow.
// Returns the token on success, "" on failure (caller falls back to 1d).
func (d *Dropmail) solveCaptchaAndGetToken(captcha map[string]interface{}) string {
	v, _ := captcha["v"].(string)
	nonce, _ := captcha["nonce"].(string)
	key, _ := captcha["key"].(string)
	sig, _ := captcha["_sig"].(string)

	// Step 2: download captcha image — same cookie jar
	imgURL := fmt.Sprintf(
		"https://dropmail.me/captcha/image.png?_r=0&v=%s&nonce=%s&key=%s&_sig=%s",
		url.QueryEscape(v), url.QueryEscape(nonce), url.QueryEscape(key), url.QueryEscape(sig),
	)
	imgResp, err := d.dropmailRawGet(imgURL)
	if err != nil || imgResp.StatusCode != 200 {
		if imgResp != nil {
			imgResp.Body.Close()
		}
		return ""
	}
	imgBytes, err := io.ReadAll(imgResp.Body)
	imgResp.Body.Close()
	if err != nil {
		return ""
	}

	// Step 3: solve via solver chain
	var ocrText string
	solvers := d.CaptchaSolvers
	if len(solvers) == 0 {
		solvers = []func([]byte) string{PaddleOCRSolver}
	}
	for _, solver := range solvers {
		result := strings.TrimSpace(solver(imgBytes))
		if result != "" {
			ocrText = result
			break
		}
	}
	if ocrText == "" {
		return ""
	}

	// Step 4: submit solution — same cookie jar, form-encoded
	formVals := url.Values{
		"response": {ocrText},
		"v":        {v},
		"nonce":    {nonce},
		"key":      {key},
		"_sig":     {sig},
	}
	solBody := []byte(formVals.Encode())
	solResp, err := d.dropmailRawPost(
		"https://dropmail.me/captcha/solution",
		"application/x-www-form-urlencoded",
		solBody,
	)
	if err != nil {
		return ""
	}
	var solData struct {
		Result string `json:"result"`
	}
	json.NewDecoder(solResp.Body).Decode(&solData)
	solResp.Body.Close()
	if solData.Result != "correct" {
		return ""
	}

	// Step 5: retry token generation with 90d — same cookie jar
	tokenBody, _ := json.Marshal(map[string]string{"type": "af", "lifetime": "90d"})
	tokenResp, err := d.dropmailRawPost(
		"https://dropmail.me/api/token/generate",
		"application/json",
		tokenBody,
	)
	if err != nil || tokenResp.StatusCode != 200 {
		if tokenResp != nil {
			tokenResp.Body.Close()
		}
		return ""
	}
	var tokenData struct {
		Token string `json:"token"`
	}
	json.NewDecoder(tokenResp.Body).Decode(&tokenData)
	tokenResp.Body.Close()
	return tokenData.Token
}

func (d *Dropmail) GenerateEmail() (string, error) {
	// Step 1: try 1d token (fast, no captcha)
	reqBody, _ := json.Marshal(map[string]string{"type": "af", "lifetime": "1d"})
	req, _ := http.NewRequest(http.MethodPost, "https://dropmail.me/api/token/generate", bytes.NewReader(reqBody))
	req.Header.Set("Content-Type", "application/json")

	// Use GetClient so the response is visible and cookies are stored.
	rawClient := d.client.GetClient()
	resp, err := rawClient.Do(req)
	if err != nil {
		return "", err
	}
	respBody, _ := io.ReadAll(resp.Body)
	resp.Body.Close()

	var token string

	if resp.StatusCode == 200 {
		var tokenResp struct {
			Token string `json:"token"`
		}
		if err := json.Unmarshal(respBody, &tokenResp); err != nil || tokenResp.Token == "" {
			return "", &TempMailError{Provider: "dropmail", Message: "failed to get token"}
		}
		token = tokenResp.Token
	} else if resp.StatusCode == 402 {
		// Captcha required — attempt 90d flow
		var capResp struct {
			Captcha map[string]interface{} `json:"captcha"`
		}
		json.Unmarshal(respBody, &capResp)
		token = d.solveCaptchaAndGetToken(capResp.Captcha)

		if token == "" {
			// Fallback: retry 1d
			req2, _ := http.NewRequest(http.MethodPost, "https://dropmail.me/api/token/generate", bytes.NewReader(reqBody))
			req2.Header.Set("Content-Type", "application/json")
			resp2, err := d.client.RawDo(req2, reqBody)
			if err != nil {
				return "", err
			}
			var tokenResp2 struct {
				Token string `json:"token"`
			}
			if err := json.Unmarshal(resp2, &tokenResp2); err != nil || tokenResp2.Token == "" {
				return "", &TempMailError{Provider: "dropmail", Message: "failed to get token (1d fallback)"}
			}
			token = tokenResp2.Token
		}
	} else {
		return "", &TempMailError{Provider: "dropmail", Message: fmt.Sprintf("token generation failed: HTTP %d", resp.StatusCode)}
	}

	graphql := `{"query": "mutation { introduceSession { id addresses { id address restoreKey } } }"}`
	req2, _ := http.NewRequest(http.MethodPost, fmt.Sprintf("https://dropmail.me/api/graphql/%s", token), bytes.NewBufferString(graphql))
	req2.Header.Set("Content-Type", "application/json")
	respBody2, err := d.client.RawDo(req2, []byte(graphql))
	if err != nil {
		return "", err
	}

	var sessionResp struct {
		Data struct {
			IntroduceSession struct {
				ID        string `json:"id"`
				Addresses []struct {
					ID      string `json:"id"`
					Address string `json:"address"`
				} `json:"addresses"`
			} `json:"introduceSession"`
		} `json:"data"`
	}
	if err := json.Unmarshal(respBody2, &sessionResp); err != nil {
		return "", &TempMailError{Provider: "dropmail", Message: "failed to parse session"}
	}

	addrs := sessionResp.Data.IntroduceSession.Addresses
	if len(addrs) == 0 {
		return "", &TempMailError{Provider: "dropmail", Message: "no address created"}
	}

	email := addrs[0].Address
	sess := &dropmailSession{
		Token:     token,
		SessionID: sessionResp.Data.IntroduceSession.ID,
		AddressID: addrs[0].ID,
	}

	d.mu.Lock()
	d.sessions[email] = sess
	d.mu.Unlock()

	return email, nil
}

func (d *Dropmail) GetInbox(email string) ([]Message, error) {
	d.mu.Lock()
	sess := d.sessions[email]
	d.mu.Unlock()
	if sess == nil {
		return nil, &TempMailError{Provider: "dropmail", Message: "unknown email"}
	}

	query := fmt.Sprintf(`{"query": "query { session(id: \"%s\") { mails { id fromAddr headerSubject receivedAt text html attachments { id name mime rawSize } } } }"}`, sess.SessionID)
	req, _ := http.NewRequest(http.MethodPost, fmt.Sprintf("https://dropmail.me/api/graphql/%s", sess.Token), bytes.NewBufferString(query))
	req.Header.Set("Content-Type", "application/json")
	body, err := d.client.RawDo(req, []byte(query))
	if err != nil {
		return nil, err
	}

	var inboxResp struct {
		Data struct {
			Session struct {
				Mails []struct {
					ID            string `json:"id"`
					FromAddr      string `json:"fromAddr"`
					HeaderSubject string `json:"headerSubject"`
					ReceivedAt    string `json:"receivedAt"`
				} `json:"mails"`
			} `json:"session"`
		} `json:"data"`
	}
	if err := json.Unmarshal(body, &inboxResp); err != nil {
		return nil, &TempMailError{Provider: "dropmail", Message: "parse error"}
	}

	var msgs []Message
	for _, m := range inboxResp.Data.Session.Mails {
		date, _ := time.Parse(time.RFC3339, m.ReceivedAt)
		msgs = append(msgs, Message{
			ID:      fmt.Sprintf("%s|%s", email, m.ID),
			Sender:  m.FromAddr,
			Subject: m.HeaderSubject,
			Date:    date,
		})
	}
	return msgs, nil
}

func (d *Dropmail) ReadMessage(messageID string) (*MessageDetail, error) {
	parts := strings.SplitN(messageID, "|", 2)
	if len(parts) != 2 {
		return nil, &TempMailError{Provider: "dropmail", Message: "invalid message ID"}
	}
	email, realID := parts[0], parts[1]

	d.mu.Lock()
	sess := d.sessions[email]
	d.mu.Unlock()
	if sess == nil {
		return nil, &TempMailError{Provider: "dropmail", Message: "unknown email"}
	}

	query := fmt.Sprintf(`{"query": "query { session(id: \"%s\") { mails { id fromAddr headerSubject receivedAt text html attachments { id name mime rawSize } } } }"}`, sess.SessionID)
	req, _ := http.NewRequest(http.MethodPost, fmt.Sprintf("https://dropmail.me/api/graphql/%s", sess.Token), bytes.NewBufferString(query))
	req.Header.Set("Content-Type", "application/json")
	body, err := d.client.RawDo(req, []byte(query))
	if err != nil {
		return nil, err
	}

	var inboxResp struct {
		Data struct {
			Session struct {
				Mails []struct {
					ID            string           `json:"id"`
					FromAddr      string           `json:"fromAddr"`
					HeaderSubject string           `json:"headerSubject"`
					ReceivedAt    string           `json:"receivedAt"`
					Text          string           `json:"text"`
					HTML          string           `json:"html"`
					Attachments   []map[string]any `json:"attachments"`
				} `json:"mails"`
			} `json:"session"`
		} `json:"data"`
	}
	if err := json.Unmarshal(body, &inboxResp); err != nil {
		return nil, &TempMailError{Provider: "dropmail", Message: "parse error"}
	}

	for _, m := range inboxResp.Data.Session.Mails {
		if m.ID == realID {
			date, _ := time.Parse(time.RFC3339, m.ReceivedAt)
			return &MessageDetail{
				Message: Message{
					ID:      messageID,
					Sender:  m.FromAddr,
					Subject: m.HeaderSubject,
					Date:    date,
				},
				BodyText:    m.Text,
				BodyHTML:    m.HTML,
				Attachments: m.Attachments,
			}, nil
		}
	}
	return nil, &NotFoundError{Provider: "dropmail", Resource: "message"}
}

func (d *Dropmail) DeleteEmail(email string) (bool, error) {
	d.mu.Lock()
	sess := d.sessions[email]
	if sess != nil {
		delete(d.sessions, email)
	}
	d.mu.Unlock()

	if sess == nil {
		return true, nil
	}

	query := fmt.Sprintf(`{"query": "mutation { deleteAddress(input: { addressId: \"%s\" }) { id } }"}`, sess.AddressID)
	req, _ := http.NewRequest(http.MethodPost, fmt.Sprintf("https://dropmail.me/api/graphql/%s", sess.Token), bytes.NewBufferString(query))
	req.Header.Set("Content-Type", "application/json")
	_, err := d.client.RawDo(req, []byte(query))
	if err != nil {
		return false, err
	}
	return true, nil
}

func (d *Dropmail) WaitForEmail(email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	return waitForEmail(d, email, timeout, interval)
}
