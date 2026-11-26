use serde::{Deserialize, Serialize};

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
    #[serde(default)]
    pub code: i32,
    #[serde(default)]
    pub content: Vec<T>,
    #[serde(default)]
    pub current_page: i32,
    #[serde(default)]
    pub total_count: i64,
    #[serde(default)]
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
    #[serde(default)]
    pub code: i32,
}

impl CodeResponse {
    pub fn is_success(&self) -> bool {
        self.code == codes::SUCCESS
    }
}
