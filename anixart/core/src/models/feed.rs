use serde::{Deserialize, Serialize};

/// Lightweight channel reference embedded in feed articles.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct FeedChannel {
    #[serde(default)]
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
    #[serde(default)]
    pub id: i64,
    #[serde(default)]
    pub title: Option<String>,
    #[serde(default)]
    pub image: Option<String>,
    #[serde(default)]
    pub creation_date: i64,
    #[serde(default)]
    pub last_update_date: i64,
    #[serde(default)]
    pub comment_count: i64,
    #[serde(default)]
    pub vote_count: i32,
    #[serde(default)]
    pub vote: i32,
    #[serde(default)]
    pub repost_count: i64,
    #[serde(default)]
    pub is_pinned: bool,
    #[serde(default)]
    pub is_deleted: bool,
    #[serde(default)]
    pub channel: Option<FeedChannel>,
}
