use serde::{Deserialize, Serialize};

use super::profile::Profile;

/// Token object returned by sign-in.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct ProfileToken {
    #[serde(default)]
    pub id: i64,
    #[serde(default)]
    pub token: String,
}

/// `POST /auth/signIn` response.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct SignInResponse {
    #[serde(default)]
    pub code: i32,
    #[serde(default)]
    pub profile: Option<Profile>,
    #[serde(default, alias = "profileToken")]
    pub profile_token: Option<ProfileToken>,
}

impl SignInResponse {
    pub fn is_success(&self) -> bool {
        self.code == 0
    }

    pub fn token(&self) -> Option<&str> {
        self.profile_token.as_ref().map(|t| t.token.as_str())
    }
}

/// `POST /auth/checkLogin` response.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct CheckLoginResponse {
    #[serde(default)]
    pub code: i32,
    #[serde(default)]
    pub available: bool,
    #[serde(default, alias = "suggestedLogins")]
    pub suggested_logins: Vec<String>,
}
