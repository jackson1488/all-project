use serde::{Deserialize, Serialize};

use super::common::de_null_default;

/// User profile (compact enough for auth / profile screens).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct Profile {
    #[serde(default, deserialize_with = "de_null_default")]
    pub id: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub login: String,
    #[serde(default, deserialize_with = "de_null_default")]
    pub avatar: String,
    #[serde(default)]
    pub status: Option<String>,
    #[serde(default, deserialize_with = "de_null_default")]
    pub privilege_level: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub rating_score: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub watching_count: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub plan_count: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub completed_count: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub hold_on_count: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub dropped_count: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub favorite_count: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub comment_count: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub collection_count: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub friend_count: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub last_activity_time: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub register_date: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_online: bool,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_verified: bool,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_sponsor: bool,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_banned: bool,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_perm_banned: bool,
}

/// `GET /profile/{id}` envelope.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct ProfileResponse {
    #[serde(default, deserialize_with = "de_null_default")]
    pub code: i32,
    #[serde(default)]
    pub profile: Option<Profile>,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_my_profile: bool,
}

impl ProfileResponse {
    pub fn is_success(&self) -> bool {
        self.code == 0
    }
}

/// `GET /profile/info` session status.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct ProfileInfoResponse {
    #[serde(default, deserialize_with = "de_null_default")]
    pub code: i32,
    #[serde(default)]
    pub channel_id: Option<i64>,
    #[serde(default, deserialize_with = "de_null_default")]
    pub privilege_level: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub rating_score: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub sponsorship_expires: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_sponsor: bool,
    #[serde(default, deserialize_with = "de_null_default")]
    pub is_perm_banned: bool,
}

impl ProfileInfoResponse {
    pub fn is_success(&self) -> bool {
        self.code == 0
    }
}
