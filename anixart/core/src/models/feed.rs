use serde::{Deserialize, Serialize};

use super::common::de_null_default;

/// Lightweight channel reference embedded in feed articles.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct FeedChannel {
    #[serde(default, deserialize_with = "de_null_default")]
    pub id: i64,
    #[serde(default)]
    pub title: Option<String>,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub avatar: Option<String>,
}

/// Feed / article entry (`Article` in the Android client).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct FeedItem {
    #[serde(default, deserialize_with = "de_null_default")]
    pub id: i64,
    #[serde(default)]
    pub title: Option<String>,
    #[serde(default)]
    pub image: Option<String>,
    #[serde(default, deserialize_with = "de_null_default")]
    pub creation_date: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub last_update_date: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub comment_count: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub vote_count: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub vote: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub repost_count: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_pinned: bool,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_deleted: bool,
    #[serde(default)]
    pub channel: Option<FeedChannel>,
}
