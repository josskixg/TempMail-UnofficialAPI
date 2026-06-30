use reqwest::Client;
use serde::de::DeserializeOwned;
use serde::Serialize;
use std::sync::{Arc, Mutex};

use crate::TempMailError;

// ponytail: 55 entries covering Chrome/Firefox/Edge/Safari/mobile across major versions.
const UA_POOL: &[&str] = &[
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:130.0) Gecko/20100101 Firefox/130.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:131.0) Gecko/20100101 Firefox/131.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:130.0) Gecko/20100101 Firefox/130.0",
    "Mozilla/5.0 (X11; Linux i686; rv:131.0) Gecko/20100101 Firefox/131.0",
    "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0",
    "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
    "Mozilla/5.0 (Linux; Android 14; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
    "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1",
    "Mozilla/5.0 (iPad; CPU OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1",
    "Mozilla/5.0 (Android 14; Mobile; rv:131.0) Gecko/131.0 Firefox/131.0",
    "Mozilla/5.0 (Android 13; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) FxiOS/131.0 Mobile/15E148 Safari/605.1.15",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 Edg/125.0.0.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 Edg/123.0.0.0",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1",
    "Mozilla/5.0 (iPad; CPU OS 16_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 15_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.6 Mobile/15E148 Safari/604.1",
    "Mozilla/5.0 (Linux; Android 12; SM-A525F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    "Mozilla/5.0 (Linux; Android 10; SM-G960F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36",
];

const RETRY_DELAYS: &[u64] = &[1, 3, 5];

/// Builder for creating an HttpClient with optional proxy rotation, cookie store, and random UA.
pub struct HttpClientBuilder {
    proxies: Vec<String>,
    random_ua: bool,
    cookie_store: bool,
}

impl HttpClientBuilder {
    pub fn new() -> Self {
        Self {
            proxies: Vec::new(),
            random_ua: false,
            cookie_store: false,
        }
    }

    pub fn proxies(mut self, proxies: Vec<String>) -> Self {
        self.proxies = proxies;
        self
    }

    pub fn random_ua(mut self, enabled: bool) -> Self {
        self.random_ua = enabled;
        self
    }

    pub fn cookie_store(mut self, enabled: bool) -> Self {
        self.cookie_store = enabled;
        self
    }

    pub fn build(self) -> Result<HttpClient, TempMailError> {
        let inner = if self.cookie_store {
            reqwest::Client::builder()
                .cookie_store(true)
                .build()
                .map_err(TempMailError::Http)?
        } else {
            reqwest::Client::builder()
                .build()
                .map_err(TempMailError::Http)?
        };

        Ok(HttpClient {
            inner,
            proxies: self.proxies,
            random_ua: self.random_ua,
            proxy_idx: Arc::new(Mutex::new(0)),
        })
    }
}

impl Default for HttpClientBuilder {
    fn default() -> Self {
        Self::new()
    }
}

/// HTTP client with anti-429 retry (3 attempts: 1s/3s/5s), proxy rotation, and UA randomization.
#[derive(Debug, Clone)]
pub struct HttpClient {
    inner: Client,
    proxies: Vec<String>,
    random_ua: bool,
    proxy_idx: Arc<Mutex<usize>>,
}

impl HttpClient {
    pub fn default_client() -> Result<Self, TempMailError> {
        HttpClientBuilder::new().build()
    }

    pub fn with_config(
        proxies: Vec<String>,
        random_ua: bool,
        cookie_store: bool,
    ) -> Result<Self, TempMailError> {
        HttpClientBuilder::new()
            .proxies(proxies)
            .random_ua(random_ua)
            .cookie_store(cookie_store)
            .build()
    }

    fn ua(&self) -> &'static str {
        if self.random_ua {
            let idx = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| (d.subsec_nanos() as usize) % UA_POOL.len())
                .unwrap_or(0);
            UA_POOL[idx]
        } else {
            UA_POOL[0]
        }
    }

    fn next_proxy(&self) -> Option<String> {
        if self.proxies.is_empty() {
            return None;
        }
        let mut idx = self.proxy_idx.lock().ok()?;
        let proxy = self.proxies[*idx].clone();
        *idx = (*idx + 1) % self.proxies.len();
        Some(proxy)
    }

    // --- raw string methods ---

    async fn request_text(
        &self,
        method: reqwest::Method,
        url: &str,
        body: Option<&str>,
        content_type: Option<&str>,
    ) -> Result<String, TempMailError> {
        for (i, &delay) in RETRY_DELAYS.iter().enumerate() {
            let mut builder = self
                .inner
                .request(method.clone(), url)
                .header("User-Agent", self.ua())
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                )
                .header("Accept-Language", "en-US,en;q=0.9");

            if let Some(proxy_url) = self.next_proxy() {
                let proxy = reqwest::Proxy::all(&proxy_url)?;
                builder = reqwest::Client::builder()
                    .proxy(proxy)
                    .build()
                    .map_err(TempMailError::Http)?
                    .request(method.clone(), url)
                    .header("User-Agent", self.ua())
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    )
                    .header("Accept-Language", "en-US,en;q=0.9");
            }

            if let Some(ct) = content_type {
                builder = builder.header("Content-Type", ct);
            }
            if let Some(b) = body {
                builder = builder.body(b.to_string());
            }

            let resp = builder.send().await?;
            let status = resp.status();

            if status.as_u16() == 429 {
                if i < RETRY_DELAYS.len() - 1 {
                    tokio::time::sleep(std::time::Duration::from_secs(delay)).await;
                    continue;
                }
                return Err(TempMailError::RateLimit);
            }

            if !status.is_success() {
                return Err(TempMailError::Api(format!(
                    "HTTP {}: {}",
                    status.as_u16(),
                    status.canonical_reason().unwrap_or("")
                )));
            }

            return resp.text().await.map_err(TempMailError::Http);
        }

        Err(TempMailError::RateLimit)
    }

    /// GET with retry on 429.
    pub async fn get(&self, url: &str) -> Result<String, TempMailError> {
        self.request_text(reqwest::Method::GET, url, None, None).await
    }

    /// POST with retry on 429.
    pub async fn post(&self, url: &str, body: &str) -> Result<String, TempMailError> {
        self.request_text(
            reqwest::Method::POST,
            url,
            Some(body),
            Some("application/x-www-form-urlencoded"),
        )
        .await
    }

    // --- JSON methods with retry ---

    /// GET JSON with retry on 429.
    pub async fn get_json<T: DeserializeOwned>(&self, url: &str) -> Result<T, TempMailError> {
        for (i, &delay) in RETRY_DELAYS.iter().enumerate() {
            let resp = self
                .inner
                .get(url)
                .header("User-Agent", self.ua())
                .header("Accept", "application/json")
                .send()
                .await?;

            let status = resp.status();
            if status.as_u16() == 429 {
                if i < RETRY_DELAYS.len() - 1 {
                    tokio::time::sleep(std::time::Duration::from_secs(delay)).await;
                    continue;
                }
                return Err(TempMailError::RateLimit);
            }

            if !status.is_success() {
                return Err(TempMailError::Api(format!(
                    "HTTP {}: {}",
                    status.as_u16(),
                    status.canonical_reason().unwrap_or("")
                )));
            }

            return resp.json::<T>().await.map_err(TempMailError::Http);
        }

        Err(TempMailError::RateLimit)
    }

    /// POST JSON with retry on 429.
    pub async fn post_json<T: DeserializeOwned, S: Serialize + ?Sized>(
        &self,
        url: &str,
        body: &S,
    ) -> Result<T, TempMailError> {
        for (i, &delay) in RETRY_DELAYS.iter().enumerate() {
            let resp = self
                .inner
                .post(url)
                .header("User-Agent", self.ua())
                .header("Accept", "application/json")
                .json(body)
                .send()
                .await?;

            let status = resp.status();
            if status.as_u16() == 429 {
                if i < RETRY_DELAYS.len() - 1 {
                    tokio::time::sleep(std::time::Duration::from_secs(delay)).await;
                    continue;
                }
                return Err(TempMailError::RateLimit);
            }

            if !status.is_success() {
                return Err(TempMailError::Api(format!(
                    "HTTP {}: {}",
                    status.as_u16(),
                    status.canonical_reason().unwrap_or("")
                )));
            }

            return resp.json::<T>().await.map_err(TempMailError::Http);
        }

        Err(TempMailError::RateLimit)
    }

    /// Access the inner reqwest::Client for edge cases.
    pub fn inner(&self) -> &Client {
        &self.inner
    }
}
