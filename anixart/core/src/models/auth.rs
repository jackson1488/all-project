use serde::{Deserialize, Serialize};

use super::common::de_null_default;
use super::profile::Profile;

/// Token object returned by sign-in.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct ProfileToken {
    #[serde(default, deserialize_with = "de_null_default")]
    pub id: i64,
    #[serde(default, deserialize_with = "de_null_default")]
    pub token: String,
}

/// `POST /auth/signIn` response.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct SignInResponse {
    #[serde(default, deserialize_with = "de_null_default")]
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
    #[serde(default, deserialize_with = "de_null_default")]
    pub code: i32,
    #[serde(default, deserialize_with = "de_null_default")]
    pub available: bool,
    #[serde(default, alias = "suggestedLogins", deserialize_with = "de_null_default")]
    pub suggested_logins: Vec<String>,
}

/// Shared response of `auth/telegram`, `auth/vk`, `auth/google`, `auth/yandex`
/// (and of `auth/verify`). Mirrors `network/response/auth/*Response.java`,
/// which all extend `Response` and implement `CodeInputResponse`.
///
/// Wire keys arrive both as camelCase getters (`profileToken`) and as explicit
/// `@JsonProperty` snake_case names (`code_length`, `code_time_left`); both
/// spellings are accepted.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct SocialSignInResponse {
    #[serde(default, deserialize_with = "de_null_default")]
    pub code: i32,
    #[serde(default)]
    pub profile: Option<Profile>,
    #[serde(default, alias = "profileToken")]
    pub profile_token: Option<ProfileToken>,
    /// Non-empty when the server expects an e-mail confirmation code.
    #[serde(default, deserialize_with = "de_null_default")]
    pub hash: String,
    #[serde(default, alias = "codeTimestampExpires", deserialize_with = "de_null_default")]
    pub code_timestamp_expires: i64,
    #[serde(default, alias = "codeLength")]
    pub code_length: Option<i32>,
    #[serde(default, alias = "codeTimeLeft")]
    pub code_time_left: Option<i64>,
    #[serde(default, alias = "suggestedLogins", deserialize_with = "de_null_default")]
    pub suggested_logins: Vec<String>,
    /// Only the Yandex variant carries a verified e-mail.
    #[serde(default)]
    pub email: Option<String>,
}

impl SocialSignInResponse {
    pub fn is_success(&self) -> bool {
        self.code == crate::oauth::codes::SUCCESS
    }

    pub fn token(&self) -> Option<&str> {
        self.profile_token.as_ref().map(|t| t.token.as_str())
    }

    /// `code == 3`: the social identity is unknown to Anixart, so the caller has
    /// to register with `login` + `email` (`X-Long-Code: 1`).
    pub fn is_not_registered(&self) -> bool {
        self.code == crate::oauth::codes::NOT_REGISTERED
    }

    /// An e-mail code has to be entered (`auth/verify`) and possibly resent
    /// (`auth/resend`).
    pub fn needs_email_code(&self) -> bool {
        !self.hash.is_empty() && self.code_length.is_some()
    }
}
