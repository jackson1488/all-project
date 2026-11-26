//! Social OAuth request building for Anixart: Telegram, VK ID, Google, Yandex ID.
//!
//! Constants and URL shapes are reverse-engineered from the decompiled Android
//! client (`network/api/AuthApi.java`, `utils/network/TelegramAuthHelper*.java`,
//! `utils/network/VkAuthHelper.java`, `ui/activity/AuthActivity.java`,
//! `AndroidManifest.xml`) and documented in `anixart/docs/oauth.md`.
//!
//! Only the *Telegram* provider can be driven end-to-end from this crate
//! (PKCE + authorize URL + code → `id_token` exchange). VK ID and Yandex ID
//! tokens are produced by their vendor SDKs, and the Google credential is a
//! Firebase ID token, so those three helpers only build requests / forward
//! credentials.

use sha2::{Digest, Sha256};

use crate::error::{AnixartError, Result};

/// Response `code` values of `auth/{telegram,vk,google,yandex}`, `auth/verify`
/// and `auth/resend` (see `network/response/auth/*Response.java`).
pub mod codes {
    pub const SUCCESS: i32 = 0;
    pub const FAILED: i32 = 1;
    pub const INVALID_REQUEST: i32 = 2;
    /// Social account is known to the IdP but has no Anixart profile yet:
    /// retry with the sign-up variant (`login` + `email`).
    pub const NOT_REGISTERED: i32 = 3;
    pub const INVALID_LOGIN: i32 = 4;
    pub const INVALID_EMAIL: i32 = 5;
    pub const LOGIN_ALREADY_TAKEN: i32 = 6;
    pub const EMAIL_ALREADY_TAKEN: i32 = 7;
    /// Google reuses 8..11 differently — see [`codes::google`].
    pub const CODE_ALREADY_SEND: i32 = 8;
    pub const EMAIL_SERVICE_DISALLOWED: i32 = 9;
    pub const TOO_MANY_REGISTRATIONS: i32 = 10;

    /// `GoogleResponse` constants from 8 upwards.
    pub mod google {
        pub const EMAIL_CHANGED: i32 = 8;
        pub const EMAIL_CHANGED_AND_CODE_ALREADY_SEND: i32 = 9;
        pub const EMAIL_SERVICE_DISALLOWED: i32 = 10;
        pub const TOO_MANY_REGISTRATIONS: i32 = 11;
    }
}

// ---------------------------------------------------------------------------
// Telegram (Login Widget, OAuth 2.0 + PKCE)
// ---------------------------------------------------------------------------

/// Telegram bot / Login-Widget `client_id` injected by `AuthActivity.onCreate`.
pub const TELEGRAM_CLIENT_ID: &str = "8789559054";
/// Registered redirect URI (`https://app1593901251-login.tg.dev/tglogin`).
pub const TELEGRAM_REDIRECT_URI: &str = "https://app1593901251-login.tg.dev/tglogin";
/// Scope string sent to Telegram (`"openid"` is always prepended).
pub const TELEGRAM_SCOPE: &str = "openid profile";
pub const TELEGRAM_AUTHORIZE_URL: &str = "https://oauth.telegram.org/auth";
pub const TELEGRAM_CROSSAPP_URL: &str = "https://oauth.telegram.org/crossapp";
pub const TELEGRAM_TOKEN_URL: &str = "https://oauth.telegram.org/token";
pub const TELEGRAM_CODE_CHALLENGE_METHOD: &str = "S256";

/// PKCE material for the Telegram code flow.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TelegramPkce {
    /// Sent verbatim in the token exchange body.
    pub verifier: String,
    /// `BASE64URL(SHA256(verifier))`, sent in the authorize request.
    pub challenge: String,
}

/// Base64url (RFC 4648 §5) **without** padding, matching Android's
/// `Base64.NO_WRAP | URL_SAFE | NO_PADDING` used by `TelegramAuthHelper`.
fn base64url(input: &[u8]) -> String {
    const ALPHABET: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    // base64 grows by 4/3; round up generously so the allocation never resizes.
    let mut out = String::with_capacity(input.len() * 2);
    for chunk in input.chunks(3) {
        let mut buf = [0u8; 3];
        buf[..chunk.len()].copy_from_slice(chunk);
        let n = (u32::from(buf[0]) << 16) | (u32::from(buf[1]) << 8) | u32::from(buf[2]);
        out.push(ALPHABET[((n >> 18) & 0x3F) as usize] as char);
        out.push(ALPHABET[((n >> 12) & 0x3F) as usize] as char);
        if chunk.len() > 1 {
            out.push(ALPHABET[((n >> 6) & 0x3F) as usize] as char);
        }
        if chunk.len() > 2 {
            out.push(ALPHABET[(n & 0x3F) as usize] as char);
        }
    }
    out
}

/// `code_verifier` from raw random bytes (the client uses 32 bytes).
pub fn code_verifier_from_bytes(bytes: &[u8]) -> String {
    base64url(bytes)
}

/// `code_challenge` = base64url(`SHA-256(verifier)`), method `S256`.
pub fn code_challenge(verifier: &str) -> String {
    let digest = Sha256::digest(verifier.as_bytes());
    base64url(&digest)
}

/// Generates 32 OS-random bytes and derives the PKCE pair.
pub fn generate_pkce() -> Result<TelegramPkce> {
    let mut bytes = [0u8; 32];
    getrandom::getrandom(&mut bytes)
        .map_err(|e| AnixartError::OAuth(format!("OS RNG unavailable: {e}")))?;
    Ok(pkce_from_bytes(&bytes))
}

/// PKCE pair from already-generated random bytes.
pub fn pkce_from_bytes(bytes: &[u8]) -> TelegramPkce {
    let verifier = code_verifier_from_bytes(bytes);
    let challenge = code_challenge(&verifier);
    TelegramPkce {
        verifier,
        challenge,
    }
}

/// Percent-encodes a value for `application/x-www-form-urlencoded`
/// (space becomes `+`, as Java's `URLEncoder.encode` did in the client).
fn form_encode(value: &str) -> String {
    urlencoding::encode(value).replace("%20", "+")
}

/// `GET https://oauth.telegram.org/crossapp?...` — asks Telegram for a URL that
/// hands the flow over to the installed Telegram app (`tg://` deep link).
///
/// Parameter order matches `TelegramAuthHelper$fetchInAppUrl$2`.
pub fn telegram_crossapp_url(
    client_id: &str,
    redirect_uri: &str,
    scope: &str,
    code_challenge: &str,
) -> String {
    format!(
        "{TELEGRAM_CROSSAPP_URL}?client_id={}&response_type=code&scope={}&redirect_uri={}&android_sdk=1&code_challenge={}&code_challenge_method={TELEGRAM_CODE_CHALLENGE_METHOD}",
        urlencoding::encode(client_id),
        urlencoding::encode(scope),
        urlencoding::encode(redirect_uri),
        urlencoding::encode(code_challenge),
    )
}

/// Same as [`telegram_crossapp_url`] with the app's baked-in constants.
pub fn telegram_crossapp_url_with(pkce: &TelegramPkce) -> String {
    telegram_crossapp_url(
        TELEGRAM_CLIENT_ID,
        TELEGRAM_REDIRECT_URI,
        TELEGRAM_SCOPE,
        &pkce.challenge,
    )
}

/// `GET https://oauth.telegram.org/auth?...` — WebView fallback.
///
/// Parameter order matches `TelegramAuthHelper$startLogin$1`.
pub fn telegram_authorize_url(
    client_id: &str,
    redirect_uri: &str,
    scope: &str,
    code_challenge: &str,
) -> String {
    format!(
        "{TELEGRAM_AUTHORIZE_URL}?client_id={}&response_type=code&scope={}&redirect_uri={}&code_challenge={}&code_challenge_method={TELEGRAM_CODE_CHALLENGE_METHOD}",
        urlencoding::encode(client_id),
        urlencoding::encode(scope),
        urlencoding::encode(redirect_uri),
        urlencoding::encode(code_challenge),
    )
}

/// Same as [`telegram_authorize_url`] with the app's baked-in constants.
pub fn telegram_authorize_url_with(pkce: &TelegramPkce) -> String {
    telegram_authorize_url(
        TELEGRAM_CLIENT_ID,
        TELEGRAM_REDIRECT_URI,
        TELEGRAM_SCOPE,
        &pkce.challenge,
    )
}

/// Body of `POST https://oauth.telegram.org/token`
/// (`Content-Type: application/x-www-form-urlencoded`, `Accept: application/json`).
///
/// Field order matches `TelegramAuthHelper$exchangeCode$2`:
/// `grant_type`, `client_id`, `code`, `redirect_uri`, `code_verifier`.
pub fn telegram_token_body(
    client_id: &str,
    code: &str,
    redirect_uri: &str,
    code_verifier: &str,
) -> String {
    format!(
        "grant_type=authorization_code&client_id={}&code={}&redirect_uri={}&code_verifier={}",
        form_encode(client_id),
        form_encode(code),
        form_encode(redirect_uri),
        form_encode(code_verifier),
    )
}

/// Outcome of the Telegram redirect, mirroring `handleLoginResponse`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TelegramRedirect {
    /// Successful authorization: exchange this code for an `id_token`.
    Code(String),
    /// User denied, session expired, or the redirect carried no code.
    Error(String),
}

/// Extracts `code` / `error` (+ `error_description`) from a redirect URI.
pub fn parse_telegram_redirect(redirect_url: &str) -> TelegramRedirect {
    match reqwest::Url::parse(redirect_url) {
        Ok(url) => redirect_from_url(&url),
        Err(e) => TelegramRedirect::Error(format!("Malformed redirect URI: {e}")),
    }
}

fn redirect_from_url(url: &reqwest::Url) -> TelegramRedirect {
    let mut code: Option<String> = None;
    let mut error: Option<String> = None;
    let mut error_description: Option<String> = None;
    for (key, value) in url.query_pairs() {
        match key.as_ref() {
            "code" if code.is_none() => code = Some(value.into_owned()),
            "error" if error.is_none() => error = Some(value.into_owned()),
            "error_description" if error_description.is_none() => {
                error_description = Some(value.into_owned());
            }
            _ => {}
        }
    }
    if let Some(error) = error {
        return TelegramRedirect::Error(error_description.unwrap_or(error));
    }
    match code {
        Some(code) if !code.is_empty() => TelegramRedirect::Code(code),
        _ => TelegramRedirect::Error("No authorization code in response URI".to_string()),
    }
}

/// Pulls the Anixart `telegramIdToken` credential out of the Telegram token
/// response: `id_token`, falling back to the string field `result`
/// (`TelegramAuthHelper.extractIdToken`).
pub fn extract_telegram_id_token(body: &str) -> Option<String> {
    let value: serde_json::Value = serde_json::from_str(body).ok()?;
    let token = value
        .get("id_token")
        .and_then(serde_json::Value::as_str)
        .or_else(|| value.get("result").and_then(serde_json::Value::as_str))?;
    if token.is_empty() {
        None
    } else {
        Some(token.to_string())
    }
}

/// Pulls the hand-off URL out of the `/crossapp` response: `url`, falling back
/// to `result.url` (`TelegramAuthHelper.extractInAppUrl`).
pub fn extract_telegram_inapp_url(body: &str) -> Option<String> {
    let value: serde_json::Value = serde_json::from_str(body).ok()?;
    let direct = value.get("url").and_then(serde_json::Value::as_str);
    if let Some(url) = direct.filter(|u| !u.is_empty()) {
        return Some(url.to_string());
    }
    let nested = value
        .get("result")?
        .get("url")
        .and_then(serde_json::Value::as_str)?;
    if nested.is_empty() {
        None
    } else {
        Some(nested.to_string())
    }
}

// ---------------------------------------------------------------------------
// VK ID
// ---------------------------------------------------------------------------

/// `VKIDClientID` manifest meta-data (VK ID app id).
pub const VK_CLIENT_ID: &str = "54701760";
/// `VKIDRedirectScheme://VKIDRedirectHost/blank.html`.
pub const VK_REDIRECT_URI: &str = "vk54701760://vk.ru/blank.html";
/// The only scope the app requests (`Collections.singleton("email")`).
pub const VK_SCOPE: &str = "email";
pub const VK_AUTHORIZE_URL: &str = "https://id.vk.ru/authorize";
/// VK ID Mobile SDK version shipped in the APK (`AuthOptionsCreator`).
pub const VK_SDK_VERSION: &str = "2.7.2";

/// Browser / Custom Tab authorize URL of the VK ID code flow (PKCE, `sha256`).
///
/// The vendor SDK adds `prompt`, `stats_info`, `lang_id`, `scheme` and an
/// `oauth2_params` payload on the redirect URI, then exchanges the code for the
/// access token against `https://api.vk.ru` itself.
pub fn vk_authorize_url(
    client_id: &str,
    redirect_uri: &str,
    code_challenge: &str,
    state: &str,
    scope: &str,
) -> String {
    let mut url = format!(
        "{VK_AUTHORIZE_URL}?client_id={}&response_type=code&redirect_uri={}&code_challenge_method=sha256&code_challenge={}&state={}&sdk_type=vkid&v={VK_SDK_VERSION}",
        urlencoding::encode(client_id),
        urlencoding::encode(redirect_uri),
        urlencoding::encode(code_challenge),
        urlencoding::encode(state),
    );
    if !scope.is_empty() {
        url.push_str("&scope=");
        url.push_str(&urlencoding::encode(scope));
    }
    url
}

/// [`vk_authorize_url`] with the app's baked-in client id, redirect URI and scope.
pub fn vk_authorize_url_with(code_challenge: &str, state: &str) -> String {
    vk_authorize_url(
        VK_CLIENT_ID,
        VK_REDIRECT_URI,
        code_challenge,
        state,
        VK_SCOPE,
    )
}

// ---------------------------------------------------------------------------
// Yandex ID
// ---------------------------------------------------------------------------

/// `com.yandex.auth.CLIENT_ID` manifest meta-data.
pub const YANDEX_CLIENT_ID: &str = "a980f00536554180b4826f7daa76f060";
/// `com.yandex.auth.OAUTH_HOST` manifest meta-data.
pub const YANDEX_OAUTH_HOST: &str = "oauth.yandex.ru";
/// `origin` query value used by the Android SDK.
pub const YANDEX_ORIGIN: &str = "android";

/// `https://yx<client_id>.<host>/auth/finish?platform=android` — the
/// autoVerified redirect the SDK is registered for.
pub fn yandex_redirect_uri(client_id: &str, host: &str) -> String {
    format!("https://yx{client_id}.{host}/auth/finish?platform=android")
}

/// Implicit-flow authorize URL (`response_type=token`): the access token arrives
/// in the redirect **fragment**, so no code exchange happens.
///
/// Mirrors `ExternalLoginHandler`: `force_confirm=true`, `origin=android`,
/// redirect URI percent-encoded.
pub fn yandex_authorize_url(client_id: &str, host: &str, state: &str) -> String {
    let redirect = yandex_redirect_uri(client_id, host);
    format!(
        "https://{host}/authorize?response_type=token&client_id={}&redirect_uri={}&state={}&force_confirm=true&origin={YANDEX_ORIGIN}",
        urlencoding::encode(client_id),
        urlencoding::encode(&redirect),
        urlencoding::encode(state),
    )
}

/// Regional OAuth host: the SDK replaces the trailing `ru` of
/// `oauth.yandex.ru` with a suffix picked from the device language.
pub fn yandex_host_for_language(language: &str) -> String {
    let suffix = match language {
        "ru" => "ru",
        "kk" => "kz",
        "be" => "by",
        "tr" => "com.tr",
        // English and every other language the SDK does not special-case.
        _ => "com",
    };
    format!("oauth.yandex.{suffix}")
}

// ---------------------------------------------------------------------------
// Google
// ---------------------------------------------------------------------------

/// The Google OAuth web client id lives in the Android string resource
/// `default_web_client_id` (generated from `google-services.json`), so it is not
/// part of the decompiled sources and must be supplied by the integrator.
pub const GOOGLE_CLIENT_ID_RESOURCE: &str = "default_web_client_id";
/// Endpoint the app uses to revoke a Google credential.
pub const GOOGLE_REVOKE_URL: &str = "https://accounts.google.com/o/oauth2/revoke?token=";

/// What `googleIdToken` really holds: a **Firebase** ID token.
pub fn google_id_token_hint() -> &'static str {
    "GoogleSignIn (requestIdToken = <default_web_client_id>) -> \
     FirebaseAuth.signInWithCredential(GoogleAuthProvider credential) -> \
     FirebaseUser.getToken(true).token; that Firebase ID token is posted as \
     googleIdToken to POST /auth/google."
}

// ---------------------------------------------------------------------------
// Anixart endpoint mapping (AuthApi.java)
// ---------------------------------------------------------------------------

/// A social provider accepted by the Anixart backend.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum SocialProvider {
    Telegram,
    Vk,
    Google,
    Yandex,
}

impl SocialProvider {
    /// Every provider in `AuthApi` declaration order.
    pub const ALL: [SocialProvider; 4] = [
        SocialProvider::Telegram,
        SocialProvider::Vk,
        SocialProvider::Google,
        SocialProvider::Yandex,
    ];

    /// Lower-case provider slug used in paths (`auth/telegram`).
    pub fn as_str(self) -> &'static str {
        match self {
            SocialProvider::Telegram => "telegram",
            SocialProvider::Vk => "vk",
            SocialProvider::Google => "google",
            SocialProvider::Yandex => "yandex",
        }
    }

    /// `POST` path shared by sign-in and sign-up (relative to the API base URL).
    pub fn endpoint(self) -> &'static str {
        match self {
            SocialProvider::Telegram => "auth/telegram",
            SocialProvider::Vk => "auth/vk",
            SocialProvider::Google => "auth/google",
            SocialProvider::Yandex => "auth/yandex",
        }
    }

    /// Form field carrying the credential (`@Field` name in `AuthApi`).
    pub fn credential_field(self) -> &'static str {
        match self {
            SocialProvider::Telegram => "telegramIdToken",
            SocialProvider::Vk => "vkAccessToken",
            SocialProvider::Google => "googleIdToken",
            SocialProvider::Yandex => "yandexAccessToken",
        }
    }

    /// Form field used by `profile/preference/<provider>/bind` (note: it differs
    /// from the sign-in field for VK and Yandex).
    pub fn bind_field(self) -> &'static str {
        match self {
            SocialProvider::Telegram | SocialProvider::Google => "idToken",
            SocialProvider::Vk | SocialProvider::Yandex => "accessToken",
        }
    }
}

/// Extra header that makes the backend treat the call as a registration
/// (`@Headers({"X-Long-Code: 1"})`).
pub const LONG_CODE_HEADER: (&str, &str) = ("X-Long-Code", "1");

/// Form fields of `signInWith{Provider}` — exactly one pair.
pub fn sign_in_form(provider: SocialProvider, credential: &str) -> Vec<(String, String)> {
    vec![(
        provider.credential_field().to_string(),
        credential.to_string(),
    )]
}

/// Form fields of `signUpWith{Provider}` — `login`, `email`, credential.
///
/// The Android client lower-cases the e-mail before sending it.
pub fn sign_up_form(
    provider: SocialProvider,
    login: &str,
    email: &str,
    credential: &str,
) -> Vec<(String, String)> {
    vec![
        ("login".to_string(), login.to_string()),
        ("email".to_string(), email.to_lowercase()),
        (
            provider.credential_field().to_string(),
            credential.to_string(),
        ),
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    const VERIFIER: &str = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    const CHALLENGE: &str = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    #[test]
    fn base64url_matches_rfc7636_vector() {
        // RFC 7636 appendix B: SHA-256("dBjftJeZ...jXk") -> E9Melhoa...-cM
        assert_eq!(code_challenge(VERIFIER), CHALLENGE);
    }

    #[test]
    fn base64url_encoding_is_unpadded() {
        assert_eq!(base64url(b""), "");
        assert_eq!(base64url(b"f"), "Zg");
        assert_eq!(base64url(b"fo"), "Zm8");
        assert_eq!(base64url(b"foo"), "Zm9v");
        assert_eq!(base64url(b"foob"), "Zm9vYg");
        // 0xFB 0xFF 0xBF exercises the two extra alphabet symbols ('-' and '_')
        let encoded = base64url(&[0xFB, 0xFF, 0xBF]);
        assert_eq!(encoded, "-_-_");
        assert!(!encoded.contains('='));
        assert!(!encoded.contains('+'));
        assert!(!encoded.contains('/'));
    }

    #[test]
    fn code_verifier_from_32_bytes_has_telegram_shape() {
        let bytes = [7u8; 32];
        let verifier = code_verifier_from_bytes(&bytes);
        assert_eq!(verifier.len(), 43); // ceil(32 * 4 / 3)
        assert!(verifier.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_'));
    }

    #[test]
    fn pkce_from_bytes_derives_challenge() {
        let pkce = pkce_from_bytes(b"0123456789abcdef");
        assert_eq!(pkce.verifier, base64url(b"0123456789abcdef"));
        assert_eq!(pkce.challenge, code_challenge(&pkce.verifier));
    }

    #[test]
    fn generate_pkce_is_random_and_valid() {
        let a = generate_pkce().expect("rng");
        let b = generate_pkce().expect("rng");
        assert_ne!(a.verifier, b.verifier);
        assert_eq!(a.challenge, code_challenge(&a.verifier));
        assert!(a.verifier.len() >= 43 && a.verifier.len() <= 128);
    }

    #[test]
    fn telegram_authorize_url_exact() {
        let url = telegram_authorize_url(
            "8789559054",
            "https://app1593901251-login.tg.dev/tglogin",
            "openid profile",
            "chal leng",
        );
        assert_eq!(
            url,
            "https://oauth.telegram.org/auth?client_id=8789559054&response_type=code\
             &scope=openid%20profile&redirect_uri=https%3A%2F%2Fapp1593901251-login.tg.dev%2Ftglogin\
             &code_challenge=chal%20leng&code_challenge_method=S256"
        );
    }

    #[test]
    fn telegram_crossapp_url_carries_android_sdk() {
        let url = telegram_crossapp_url("id", "https://host/rp", "openid profile", "cc");
        assert!(url.starts_with("https://oauth.telegram.org/crossapp?client_id=id"));
        assert!(url.contains("&android_sdk=1&code_challenge=cc&code_challenge_method=S256"));
        assert!(url.contains("&response_type=code&scope=openid%20profile&redirect_uri="));
    }

    #[test]
    fn telegram_helpers_use_baked_in_constants() {
        let pkce = TelegramPkce {
            verifier: "v".into(),
            challenge: "c".into(),
        };
        assert!(telegram_authorize_url_with(&pkce).contains("client_id=8789559054"));
        assert!(telegram_authorize_url_with(&pkce).contains("scope=openid%20profile"));
        assert!(telegram_crossapp_url_with(&pkce).contains("code_challenge=c"));
    }

    #[test]
    fn telegram_token_body_field_order() {
        let body = telegram_token_body("8789559054", "co de", "https://h/rp", "veri=fier");
        assert_eq!(
            body,
            "grant_type=authorization_code&client_id=8789559054&code=co+de\
             &redirect_uri=https%3A%2F%2Fh%2Frp&code_verifier=veri%3Dfier"
        );
    }

    #[test]
    fn parse_telegram_redirect_returns_code() {
        let url = "https://app1593901251-login.tg.dev/tglogin?code=abc123&state=xyz";
        assert_eq!(parse_telegram_redirect(url), TelegramRedirect::Code("abc123".into()));
    }

    #[test]
    fn parse_telegram_redirect_prefers_error_description() {
        let url = "https://host/tglogin?error=access_denied&error_description=User%20cancelled";
        assert_eq!(
            parse_telegram_redirect(url),
            TelegramRedirect::Error("User cancelled".into())
        );
    }

    #[test]
    fn parse_telegram_redirect_empty_code_is_error() {
        assert_eq!(
            parse_telegram_redirect("https://host/tglogin?code="),
            TelegramRedirect::Error("No authorization code in response URI".into())
        );
        assert!(matches!(
            parse_telegram_redirect("not a url"),
            TelegramRedirect::Error(msg) if msg.starts_with("Malformed redirect URI:")
        ));
    }

    #[test]
    fn extract_id_token_variants() {
        assert_eq!(
            extract_telegram_id_token(r#"{"id_token":"JWT","access_token":"at"}"#).as_deref(),
            Some("JWT")
        );
        assert_eq!(
            extract_telegram_id_token(r#"{"result":"JWT2"}"#).as_deref(),
            Some("JWT2")
        );
        assert!(extract_telegram_id_token(r#"{"id_token":""}"#).is_none());
        assert!(extract_telegram_id_token(r#"{"result":42}"#).is_none());
        assert!(extract_telegram_id_token("not json").is_none());
    }

    #[test]
    fn extract_inapp_url_variants() {
        assert_eq!(
            extract_telegram_inapp_url(r#"{"url":"tg://login?code=x"}"#).as_deref(),
            Some("tg://login?code=x")
        );
        assert_eq!(
            extract_telegram_inapp_url(r#"{"result":{"url":"https://tg.dev/x"}}"#).as_deref(),
            Some("https://tg.dev/x")
        );
        assert!(extract_telegram_inapp_url(r#"{"url":""}"#).is_none());
        assert!(extract_telegram_inapp_url("{}").is_none());
    }

    #[test]
    fn vk_authorize_url_exact() {
        let url = vk_authorize_url("54701760", "vk54701760://vk.ru/blank.html", "cc", "st", "email");
        assert_eq!(
            url,
            "https://id.vk.ru/authorize?client_id=54701760&response_type=code\
             &redirect_uri=vk54701760%3A%2F%2Fvk.ru%2Fblank.html&code_challenge_method=sha256\
             &code_challenge=cc&state=st&sdk_type=vkid&v=2.7.2&scope=email"
        );
        assert!(vk_authorize_url("1", "r", "c", "s", "").ends_with("v=2.7.2"));
    }

    #[test]
    fn yandex_authorize_url_uses_implicit_flow() {
        let url = yandex_authorize_url(YANDEX_CLIENT_ID, YANDEX_OAUTH_HOST, "state123");
        assert!(url.starts_with("https://oauth.yandex.ru/authorize?response_type=token"));
        assert!(url.contains(&format!("client_id={YANDEX_CLIENT_ID}")));
        assert!(url.contains(
            "redirect_uri=https%3A%2F%2Fyxa980f00536554180b4826f7daa76f060.oauth.yandex.ru%2Fauth%2Ffinish%3Fplatform%3Dandroid"
        ));
        assert!(url.contains("&state=state123&force_confirm=true&origin=android"));
        assert_eq!(
            yandex_redirect_uri("abc", "oauth.yandex.kz"),
            "https://yxabc.oauth.yandex.kz/auth/finish?platform=android"
        );
    }

    #[test]
    fn yandex_regional_hosts_follow_device_language() {
        assert_eq!(yandex_host_for_language("ru"), "oauth.yandex.ru");
        assert_eq!(yandex_host_for_language("en"), "oauth.yandex.com");
        assert_eq!(yandex_host_for_language("kk"), "oauth.yandex.kz");
        assert_eq!(yandex_host_for_language("be"), "oauth.yandex.by");
        assert_eq!(yandex_host_for_language("tr"), "oauth.yandex.com.tr");
        assert_eq!(yandex_host_for_language("de"), "oauth.yandex.com");
    }

    #[test]
    fn provider_endpoints_and_fields_match_authapi() {
        let expected = [
            (
                SocialProvider::Telegram,
                "auth/telegram",
                "telegramIdToken",
                "idToken",
            ),
            (SocialProvider::Vk, "auth/vk", "vkAccessToken", "accessToken"),
            (SocialProvider::Google, "auth/google", "googleIdToken", "idToken"),
            (
                SocialProvider::Yandex,
                "auth/yandex",
                "yandexAccessToken",
                "accessToken",
            ),
        ];
        assert_eq!(SocialProvider::ALL.len(), expected.len());
        for (provider, path, field, bind) in expected {
            assert_eq!(provider.endpoint(), path);
            assert_eq!(provider.credential_field(), field);
            assert_eq!(provider.bind_field(), bind);
            assert_eq!(provider.as_str(), path.trim_start_matches("auth/"));
        }
    }

    #[test]
    fn sign_in_form_has_exactly_one_credential_field() {
        for provider in SocialProvider::ALL {
            let form = sign_in_form(provider, "cred");
            assert_eq!(form, vec![(provider.credential_field().to_string(), "cred".to_string())]);
        }
    }

    #[test]
    fn sign_up_form_order_and_email_lowercasing() {
        let form = sign_up_form(SocialProvider::Google, "neko", "Neko@Mail.RU", "tok");
        assert_eq!(
            form,
            vec![
                ("login".to_string(), "neko".to_string()),
                ("email".to_string(), "neko@mail.ru".to_string()),
                ("googleIdToken".to_string(), "tok".to_string()),
            ]
        );
    }

    #[test]
    fn long_code_header_shape() {
        assert_eq!(LONG_CODE_HEADER, ("X-Long-Code", "1"));
    }

    #[test]
    fn response_codes_match_the_decompiled_constants() {
        assert_eq!(codes::NOT_REGISTERED, 3);
        assert_eq!(codes::CODE_ALREADY_SEND, 8);
        assert_eq!(codes::google::EMAIL_CHANGED, 8);
        assert_eq!(codes::google::TOO_MANY_REGISTRATIONS, 11);
    }

    #[test]
    fn google_hint_mentions_firebase_id_token() {
        assert!(google_id_token_hint().contains("Firebase"));
        assert_eq!(GOOGLE_CLIENT_ID_RESOURCE, "default_web_client_id");
        assert!(GOOGLE_REVOKE_URL.contains("accounts.google.com"));
    }
}
