use serde::{Deserialize, Deserializer, Serialize};

/// Deserializes `null` as `T::default()`, complementing `#[serde(default)]` which only handles missing keys.
pub fn de_null_default<'de, D, T>(deserializer: D) -> std::result::Result<T, D::Error>
where
    D: Deserializer<'de>,
    T: Default + Deserialize<'de>,
{
    Ok(Option::<T>::deserialize(deserializer)?.unwrap_or_default())
}

/// Envelope `code` values from the Anixart API.
pub mod codes {
    pub const SUCCESS: i32 = 0;
    pub const FAILED: i32 = 1;
    pub const BANNED: i32 = 402;
    pub const PERM_BANNED: i32 = 403;
    pub const INVALID_LOGIN: i32 = 2;
    pub const INVALID_PASSWORD: i32 = 3;
}

/// Generic paginated response used by feed / favorites / history.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct Pageable<T> {
    #[serde(default, deserialize_with = "de_null_default")]
    pub code: i32,
    #[serde(
        default,
        bound(deserialize = "T: Deserialize<'de>"),
        deserialize_with = "de_null_default"
    )]
    pub content: Vec<T>,
    #[serde(default, deserialize_with = "de_null_default")]
    pub current_page: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub total_count: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub total_page_count: i32,
}

impl<T> Pageable<T> {
    pub fn is_success(&self) -> bool {
        self.code == codes::SUCCESS
    }
}

/// Bare `{ "code": n }` response (favorites add/delete, history mutate, …).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct CodeResponse {
    #[serde(default, deserialize_with = "de_null_default")]
    pub code: i32,
}

impl CodeResponse {
    pub fn is_success(&self) -> bool {
        self.code == codes::SUCCESS
    }
}
