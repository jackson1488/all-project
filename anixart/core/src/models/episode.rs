use serde::{Deserialize, Deserializer, Serialize};

use super::common::de_null_default;

fn de_source_or_id<'de, D>(deserializer: D) -> std::result::Result<Option<Source>, D::Error>
where
    D: Deserializer<'de>,
{
    let value: Option<serde_json::Value> = Option::deserialize(deserializer)?;
    Ok(match value {
        Some(serde_json::Value::Object(map)) => {
            serde_json::from_value(serde_json::Value::Object(map)).ok()
        }
        _ => None,
    })
}

/// Voiceover / translation type (`Type` in the Android client).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct EpisodeType {
    #[serde(default, deserialize_with = "de_null_default")]
    pub id: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub name: String,
    #[serde(default, deserialize_with = "de_null_default")]
    pub icon: String,
    #[serde(default, deserialize_with = "de_null_default")]
    pub workers: String,
    #[serde(default, deserialize_with = "de_null_default")]
    pub episodes_count: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub view_count: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub quality: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub pinned: bool,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_sub: bool,
}

/// Alias used in product language ("voiceover").
pub type Voiceover = EpisodeType;

/// Video source / player host for a type.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct Source {
    #[serde(default, deserialize_with = "de_null_default")]
    pub id: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub name: String,
    #[serde(default, deserialize_with = "de_null_default")]
    pub episodes_count: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub quality: i32,
}

/// Single episode / player entry.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct Episode {
    #[serde(default, deserialize_with = "de_null_default")]
    pub id: i64,
    #[serde(default, alias = "releaseId", deserialize_with = "de_null_default")]
    pub release_id: i64,
    #[serde(default, alias = "sourceId", deserialize_with = "de_null_default")]
    pub source_id: i64,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default, deserialize_with = "de_null_default")]
    pub url: String,
    #[serde(default, deserialize_with = "de_null_default")]
    pub position: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub quality: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub iframe: bool,
    #[serde(default, alias = "addedDate", deserialize_with = "de_null_default")]
    pub added_date: i64,
    #[serde(default, alias = "playbackPosition", deserialize_with = "de_null_default")]
    pub playback_position: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_filler: bool,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_watched: bool,
    #[serde(default, deserialize_with = "de_source_or_id")]
    pub source: Option<Source>,
}

/// `GET /episode/{releaseId}` → types / voiceovers.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct TypesResponse {
    #[serde(default, deserialize_with = "de_null_default")]
    pub code: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub types: Vec<EpisodeType>,
}

impl TypesResponse {
    pub fn is_success(&self) -> bool {
        self.code == 0
    }
}

/// `GET /episode/{releaseId}/{typeId}` → sources.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct SourcesResponse {
    #[serde(default, deserialize_with = "de_null_default")]
    pub code: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub sources: Vec<Source>,
}

impl SourcesResponse {
    pub fn is_success(&self) -> bool {
        self.code == 0
    }
}

/// `GET /episode/{releaseId}/{typeId}/{sourceId}` → episodes.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct EpisodeResponse {
    #[serde(default, deserialize_with = "de_null_default")]
    pub code: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub episodes: Vec<Episode>,
}

impl EpisodeResponse {
    pub fn is_success(&self) -> bool {
        self.code == 0
    }
}
