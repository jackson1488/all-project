use thiserror::Error;

/// Errors produced by the Anixart API client.
#[derive(Debug, Error)]
pub enum AnixartError {
    #[error("HTTP transport error: {0}")]
    Http(#[from] reqwest::Error),

    #[error("JSON decode error: {0}")]
    Json(#[from] serde_json::Error),

    #[error("API returned failure code {code}")]
    Api { code: i32 },

    #[error("missing auth token")]
    MissingToken,

    #[error("unexpected response: {0}")]
    Unexpected(String),
}

pub type Result<T> = std::result::Result<T, AnixartError>;
