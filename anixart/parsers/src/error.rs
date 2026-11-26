use thiserror::Error;

#[derive(Debug, Error)]
pub enum ParserError {
    #[error("network request error: {0}")]
    Network(#[from] reqwest::Error),

    #[error("json parsing error: {0}")]
    Json(#[from] serde_json::Error),

    #[error("regex compilation error: {0}")]
    Regex(#[from] regex::Error),

    #[error("unsupported video URL or host: {0}")]
    UnsupportedUrl(String),

    #[error("failed to extract video ID or parameters: {0}")]
    ExtractFailed(String),

    #[error("video is unavailable or region-locked: {0}")]
    Unavailable(String),

    #[error("no stream qualities found: {0}")]
    NoQualitiesFound(String),

    #[error("provider error: {0}")]
    Provider(String),
}
