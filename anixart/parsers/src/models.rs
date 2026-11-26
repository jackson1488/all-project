use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Known video provider hosts.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum VideoSource {
    Direct,
    Kodik,
    Sibnet,
    Vk,
    Rutube,
    Anilibria,
    Unknown,
}

/// Resolved video stream variants and playback headers.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ParsedVideo {
    pub source: VideoSource,
    /// Quality label ("Default", "360p", "480p", "720p", "1080p", "1440p", "2160p") -> direct URL.
    pub qualities: HashMap<String, String>,
    /// HTTP headers required for playback (e.g. "Referer", "User-Agent").
    pub headers: HashMap<String, String>,
}

impl ParsedVideo {
    pub fn new(source: VideoSource) -> Self {
        Self {
            source,
            qualities: HashMap::new(),
            headers: HashMap::new(),
        }
    }

    pub fn with_quality(mut self, quality: impl Into<String>, url: impl Into<String>) -> Self {
        self.qualities.insert(quality.into(), url.into());
        self
    }

    pub fn with_header(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.headers.insert(key.into(), value.into());
        self
    }

    /// Returns the best quality URL available, prioritizing highest resolution down to Default.
    pub fn best_url(&self) -> Option<&str> {
        const QUALITY_PRIORITY: &[&str] = &[
            "2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p", "Default",
        ];
        for q in QUALITY_PRIORITY {
            if let Some(url) = self.qualities.get(*q) {
                return Some(url.as_str());
            }
        }
        self.qualities.values().next().map(|s| s.as_str())
    }
}
