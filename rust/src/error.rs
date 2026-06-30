use thiserror::Error;

#[derive(Debug, Error)]
pub enum TempMailError {
    #[error("HTTP error: {0}")]
    Http(#[from] reqwest::Error),

    #[error("API error: {0}")]
    Api(String),

    #[error("Rate limit exceeded")]
    RateLimit,

    #[error("Not found")]
    NotFound,

    #[error("Invalid configuration: {0}")]
    InvalidConfig(String),
}
