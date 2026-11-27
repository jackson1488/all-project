use serde::{Deserialize, Serialize};

use super::common::de_null_default;
use super::profile::Profile;
use super::release::Release;

/// User / editorial collection of releases.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct Collection {
    #[serde(default, deserialize_with = "de_null_default")]
    pub id: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub title: String,
    #[serde(default, deserialize_with = "de_null_default")]
    pub description: String,
    #[serde(default, deserialize_with = "de_null_default")]
    pub image: String,
    #[serde(default, deserialize_with = "de_null_default")]
    pub comment_count: i64,
    #[serde(default, alias = "favoriteCount", deserialize_with = "de_null_default")]
    pub favorites_count: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub creation_date: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub last_update_date: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_favorite: bool,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_private: bool,
    #[serde(default)]
    pub creator: Option<Profile>,
    #[serde(default, deserialize_with = "de_null_default")]
    pub releases: Vec<Release>,
}

/// `GET /collection/{id}` envelope.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct CollectionResponse {
    #[serde(default, deserialize_with = "de_null_default")]
    pub code: i32,
    #[serde(default)]
    pub collection: Option<Collection>,
    #[serde(default, deserialize_with = "de_null_default")]
    pub watching_count: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub plan_count: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub completed_count: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub hold_on_count: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub dropped_count: i64,
}

impl CollectionResponse {
    pub fn is_success(&self) -> bool {
        self.code == 0
    }
}
