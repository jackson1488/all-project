use serde::{Deserialize, Deserializer, Serialize};

fn de_status_name<'de, D>(deserializer: D) -> std::result::Result<Option<String>, D::Error>
where
    D: Deserializer<'de>,
{
    let value: Option<serde_json::Value> = Option::deserialize(deserializer)?;
    Ok(match value {
        Some(serde_json::Value::String(s)) => Some(s),
        Some(serde_json::Value::Object(map)) => map
            .get("name")
            .and_then(|v| v.as_str())
            .map(str::to_owned),
        _ => None,
    })
}

/// Release card / catalogue entry.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct Release {
    #[serde(default)]
    pub id: i64,
    #[serde(default)]
    pub title_ru: Option<String>,
    #[serde(default)]
    pub title_original: Option<String>,
    #[serde(default)]
    pub title_alt: Option<String>,
    #[serde(default)]
    pub description: Option<String>,
    #[serde(default)]
    pub image: Option<String>,
    #[serde(default)]
    pub poster: Option<String>,
    #[serde(default)]
    pub year: Option<String>,
    #[serde(default)]
    pub genres: Option<String>,
    #[serde(default)]
    pub country: Option<String>,
    #[serde(default)]
    pub studio: Option<String>,
    #[serde(default)]
    pub author: Option<String>,
    #[serde(default)]
    pub director: Option<String>,
    #[serde(default)]
    pub translators: Option<String>,
    #[serde(default)]
    pub source: Option<String>,
    #[serde(default)]
    pub grade: f64,
    #[serde(default)]
    pub rating: i32,
    #[serde(default)]
    pub age_rating: i32,
    #[serde(default)]
    pub duration: i32,
    #[serde(default)]
    pub season: i32,
    #[serde(default)]
    pub status_id: i64,
    #[serde(default)]
    pub episodes_released: Option<i32>,
    #[serde(default)]
    pub episodes_total: Option<i32>,
    #[serde(default)]
    pub favorites_count: i32,
    #[serde(default)]
    pub comment_count: i64,
    #[serde(default)]
    pub vote_count: i32,
    #[serde(default)]
    pub is_favorite: bool,
    #[serde(default)]
    pub is_adult: bool,
    #[serde(default)]
    pub is_viewed: bool,
    #[serde(default)]
    pub profile_list_status: i32,
    #[serde(default)]
    pub release_date: Option<String>,
    #[serde(default, deserialize_with = "de_status_name")]
    pub status: Option<String>,
}

impl Release {
    /// Returns the best available poster URL, falling back to `poster` when `image` is absent.
    pub fn poster_url(&self) -> Option<String> {
        if let Some(img) = self.image.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
            return Some(img.to_owned());
        }
        let p = self.poster.as_deref().map(str::trim).filter(|s| !s.is_empty())?;
        if p.starts_with("http://") || p.starts_with("https://") || p.starts_with('/') {
            Some(p.to_owned())
        } else {
            Some(format!("https://s.anixmirai.com/posters/{p}.jpg"))
        }
    }
}

/// `GET /release/{id}` envelope.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct ReleaseResponse {
    #[serde(default)]
    pub code: i32,
    #[serde(default)]
    pub release: Option<Release>,
}

impl ReleaseResponse {
    pub fn is_success(&self) -> bool {
        self.code == 0
    }
}

/// Related franchise block occasionally returned by search.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct Related {
    #[serde(default)]
    pub id: i64,
    #[serde(default)]
    pub name: Option<String>,
}

/// `POST /search/releases/{page}` response.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct ReleaseSearchResponse {
    #[serde(default)]
    pub code: i32,
    #[serde(default, alias = "content")]
    pub releases: Vec<Release>,
    #[serde(default)]
    pub related: Option<Related>,
}

impl ReleaseSearchResponse {
    pub fn is_success(&self) -> bool {
        self.code == 0
    }
}

/// Item returned by `POST /discover/interesting`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct DiscoverInterestingItem {
    #[serde(default)]
    pub id: i64,
    #[serde(default)]
    pub title: Option<String>,
    #[serde(default)]
    pub description: Option<String>,
    #[serde(default)]
    pub image: Option<String>,
    #[serde(default, rename = "type")]
    pub kind: i32,
    #[serde(default)]
    pub action: Option<String>,
}

/// Item returned by `POST /discover/comments`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct DiscoverCommentItem {
    #[serde(default)]
    pub id: i64,
    #[serde(default)]
    pub message: Option<String>,
    #[serde(default)]
    pub release: Option<Release>,
}

/// Body for release / collection / profile search.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SearchRequest {
    pub query: String,
    #[serde(rename = "searchBy")]
    pub search_by: i32,
}

impl SearchRequest {
    pub fn new(query: impl Into<String>) -> Self {
        Self {
            query: query.into(),
            search_by: 0,
        }
    }
}
