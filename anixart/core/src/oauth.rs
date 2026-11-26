//! Social OAuth request building for Anixart: Telegram, VK ID, Google, Yandex ID.
//!
//! Constants and URL shapes are reverse-engineered from the decompiled Android
//! client (`network/api/AuthApi.java`, `utils/network/TelegramAuthHelper*.java`,
//! `utils/network/VkAuthHelper.java`, `ui/activity/AuthActivity.java`,
//! `AndroidManifest.xml`) and documented in `anixart/docs/oauth.md`.
//!
//! Only the *Telegram* and *Google* providers can be driven end-to-end from
//! this crate: Telegram through PKCE (authorize URL + code → `id_token`
//! exchange), Google through the Firebase `accounts:signInWithIdp` exchange
//! ([`exchange_google_to_firebase_id_token`] and
//! [`AnixartClient::sign_in_google_via_firebase`]) — the desktop stand-in for
//! the Android client's `FirebaseAuth.signInWithCredential`. VK ID and Yandex ID
//! tokens are produced by their vendor SDKs, so those two helpers only build
//! requests.

use sha2::{Digest, Sha256};

use crate::client::AnixartClient;
use crate::error::{AnixartError, Result};
use crate::models::SocialSignInResponse;

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
///
/// Besides the absolute redirect URL the browser lands on
/// (`https://app1593901251-login.tg.dev/tglogin?code=…`), the paste forms a user
/// can produce by hand are accepted: `?code=…`, `code=…` and `…&code=…`. Only a
/// bare `id_token` stays out of this function — anything carrying a `code=` or
/// `error=` pair is treated as a redirect.
pub fn parse_telegram_redirect(redirect_url: &str) -> TelegramRedirect {
    match reqwest::Url::parse(redirect_url) {
        Ok(url) => redirect_from_url(&url),
        Err(e) => match redirect_from_query_fragment(redirect_url) {
            Some(parsed) => parsed,
            None => TelegramRedirect::Error(format!("Malformed redirect URI: {e}")),
        },
    }
}

/// Parses a redirect that was pasted without its `scheme://host` prefix.
fn redirect_from_query_fragment(input: &str) -> Option<TelegramRedirect> {
    let input = input.trim();
    if !input.contains("code=") && !input.contains("error=") {
        return None;
    }
    let query = input.trim_start_matches('?');
    let url = reqwest::Url::parse(&format!("https://anixart.invalid/?{query}")).ok()?;
    Some(redirect_from_url(&url))
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
// Google (browser sign-in + Firebase exchange)
// ---------------------------------------------------------------------------

/// Google OAuth **web** client id of the Anixart Firebase project.
///
/// The Android client reads it from the `default_web_client_id` string resource
/// that `google-services.json` generates. Both it and [`FIREBASE_API_KEY`] are
/// client-side identifiers that ship inside the APK (they authenticate the
/// *user*, not the caller, so they are not secrets), which lets a desktop client
/// reuse them instead of asking the user to hunt for an OAuth client.
pub const GOOGLE_WEB_CLIENT_ID: &str =
    "983926366374-c3jvolf9t8e151mbgriuqi7uqnbt4c4p.apps.googleusercontent.com";
/// Firebase **web** API key of the same `google-services.json`
/// (`api_key[0].current_key`); authorises the Identity Toolkit calls below.
pub const FIREBASE_API_KEY: &str = "AIzaSyBFPckWOsp0MEqb_1gwszvM1ILdUixM-uw";
/// Name of the Android string resource the client id is generated into — kept
/// for the decompiled-source cross-reference.
pub const GOOGLE_CLIENT_ID_RESOURCE: &str = "default_web_client_id";
/// Endpoint the app uses to revoke a Google credential.
pub const GOOGLE_REVOKE_URL: &str = "https://accounts.google.com/o/oauth2/revoke?token=";
/// Google OAuth authorize endpoint (implicit flow of the browser sign-in).
pub const GOOGLE_OAUTH_AUTH_URL: &str = "https://accounts.google.com/o/oauth2/v2/auth";
/// Scope the desktop client asks Google for.
pub const GOOGLE_SCOPE: &str = "openid email profile";
/// Firebase Authentication (Identity Toolkit) endpoint base.
pub const FIREBASE_IDENTITY_TOOLKIT_BASE: &str = "https://identitytoolkit.googleapis.com/";
/// The `signInWithIdp` method path, relative to [`FIREBASE_IDENTITY_TOOLKIT_BASE`].
pub const FIREBASE_SIGN_IN_WITH_IDP_PATH: &str = "v1/accounts:signInWithIdp";
/// `requestUri` Firebase expects from a non-Android client.
pub const FIREBASE_REQUEST_URI: &str = "http://localhost";

/// What `googleIdToken` really holds: a **Firebase** ID token.
pub fn google_id_token_hint() -> &'static str {
    "GoogleSignIn (requestIdToken = <default_web_client_id>) -> \
     FirebaseAuth.signInWithCredential(GoogleAuthProvider credential) -> \
     FirebaseUser.getToken(true).token; that Firebase ID token is posted as \
     googleIdToken to POST /auth/google. On the desktop client \
     exchange_google_to_firebase_id_token performs the middle step through \
     identitytoolkit.googleapis.com accounts:signInWithIdp."
}

/// `POST …/v1/accounts:signInWithIdp?key=…`
///
/// `endpoint_base` is whatever the request is aimed at (the production Identity
/// Toolkit, an offline test listener, a self-hosted proxy); a trailing slash is
/// optional.
pub fn firebase_sign_in_with_idp_url_at(endpoint_base: &str, api_key: &str) -> String {
    let base = if endpoint_base.ends_with('/') {
        endpoint_base.to_owned()
    } else {
        format!("{endpoint_base}/")
    };
    format!(
        "{base}{FIREBASE_SIGN_IN_WITH_IDP_PATH}?key={}",
        urlencoding::encode(api_key)
    )
}

/// [`firebase_sign_in_with_idp_url_at`] against the production Identity Toolkit
/// endpoint.
pub fn firebase_sign_in_with_idp_url(api_key: &str) -> String {
    firebase_sign_in_with_idp_url_at(FIREBASE_IDENTITY_TOOLKIT_BASE, api_key)
}

/// JSON body of `accounts:signInWithIdp`.
///
/// `postBody` carries the IdP credential in `application/x-www-form-urlencoded`
/// form — Firebase splits it on `&` / `=`, so an OAuth **access** token goes in
/// as `access_token=…` and a signed **id_token** as `id_token=…`, always
/// together with `providerId=google.com`.
pub fn firebase_sign_in_with_idp_body(google_token: &str, is_access_token: bool) -> serde_json::Value {
    let key = if is_access_token {
        "access_token"
    } else {
        "id_token"
    };
    serde_json::json!({
        "postBody": format!("{key}={}&providerId=google.com", urlencoding::encode(google_token)),
        "requestUri": FIREBASE_REQUEST_URI,
        "returnIdpCredential": true,
        "returnSecureToken": true,
    })
}

/// Pulls the Firebase ID token (`idToken`) out of a `signInWithIdp` response.
pub fn extract_firebase_id_token(body: &str) -> Option<String> {
    let value: serde_json::Value = serde_json::from_str(body).ok()?;
    let token = value.get("idToken").and_then(serde_json::Value::as_str)?;
    if token.is_empty() {
        None
    } else {
        Some(token.to_string())
    }
}

/// `true` when a Google credential is an opaque OAuth **access** token
/// (`ya29.…`) rather than a signed `id_token` JWT (`header.payload.signature`).
///
/// Firebase accepts both, but under different `postBody` keys, so the browser
/// flow has to tell them apart.
pub fn google_token_is_access_token(token: &str) -> bool {
    !is_google_id_token(token)
}

/// `true` for a three-segment base64url JWT (`eyJ…`.`…`.`…`).
pub fn is_google_id_token(token: &str) -> bool {
    let token = token.trim();
    token.starts_with("ey") && token.matches('.').count() == 2
}

/// Implicit-flow authorize URL of the 1-click browser sign-in.
///
/// The redirect URI is the desktop client's own loopback listener
/// (`http://127.0.0.1:<port>`, which Google accepts for web clients at any
/// port), and `response_type=token id_token` asks for both credentials so the
/// relay page can hand over an `id_token` — or an `access_token` when the client
/// id is not enabled for the ID token.
pub fn google_browser_authorize_url(client_id: &str, port: u16, nonce: &str) -> String {
    format!(
        "{GOOGLE_OAUTH_AUTH_URL}?client_id={}&redirect_uri={}&response_type=token%20id_token&scope={}&nonce={}",
        urlencoding::encode(client_id),
        urlencoding::encode(&format!("http://127.0.0.1:{port}")),
        urlencoding::encode(GOOGLE_SCOPE),
        urlencoding::encode(nonce),
    )
}

/// Exchanges a Google credential for a **Firebase** ID token:
/// `POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=…`.
///
/// This is the desktop stand-in for the Android client's
/// `FirebaseAuth.signInWithCredential(GoogleAuthProvider …)`; the token it
/// returns is what `POST /auth/google` expects as `googleIdToken`.
///
/// `is_access_token` selects the `postBody` key — see
/// [`google_token_is_access_token`] for deriving it from the credential shape.
pub async fn exchange_google_to_firebase_id_token(
    http: &reqwest::Client,
    firebase_api_key: &str,
    google_id_or_access_token: &str,
    is_access_token: bool,
) -> Result<String> {
    let url = firebase_sign_in_with_idp_url(firebase_api_key);
    exchange_google_to_firebase_id_token_at(http, &url, google_id_or_access_token, is_access_token).await
}

/// [`exchange_google_to_firebase_id_token`] against an explicit endpoint URL
/// (offline tests, or a self-hosted Identity Toolkit proxy).
pub async fn exchange_google_to_firebase_id_token_at(
    http: &reqwest::Client,
    endpoint_url: &str,
    google_id_or_access_token: &str,
    is_access_token: bool,
) -> Result<String> {
    let body = firebase_sign_in_with_idp_body(google_id_or_access_token, is_access_token);
    let resp = http.post(endpoint_url).json(&body).send().await?;
    let status = resp.status();
    let text = resp.text().await?;
    if !status.is_success() {
        return Err(AnixartError::OAuth(format!(
            "Firebase signInWithIdp HTTP {}: {text}",
            status.as_u16()
        )));
    }
    extract_firebase_id_token(&text)
        .ok_or_else(|| AnixartError::OAuth("Firebase signInWithIdp returned no idToken".into()))
}

impl AnixartClient {
    /// Google credential → Firebase ID token → `POST /auth/google`.
    ///
    /// `is_access_token` marks the input as a `ya29.…` access token instead of a
    /// Google `id_token` JWT (see [`google_token_is_access_token`]). The Firebase
    /// API key the APK ships is used; another key or endpoint goes through
    /// [`Self::sign_in_google_via_firebase_at`].
    pub async fn sign_in_google_via_firebase(
        &mut self,
        google_token: &str,
        is_access_token: bool,
    ) -> Result<SocialSignInResponse> {
        let url = firebase_sign_in_with_idp_url(FIREBASE_API_KEY);
        self.sign_in_google_via_firebase_at(&url, google_token, is_access_token)
            .await
    }

    /// [`Self::sign_in_google_via_firebase`] against an explicit Identity
    /// Toolkit endpoint, which is also how a different API key is supplied.
    pub async fn sign_in_google_via_firebase_at(
        &mut self,
        identity_toolkit_url: &str,
        google_token: &str,
        is_access_token: bool,
    ) -> Result<SocialSignInResponse> {
        let firebase_id_token = exchange_google_to_firebase_id_token_at(
            self.http(),
            identity_toolkit_url,
            google_token,
            is_access_token,
        )
        .await?;
        self.sign_in_google(&firebase_id_token).await
    }
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

    #[test]
    fn apk_google_and_firebase_constants_are_the_shipped_ones() {
        assert_eq!(
            GOOGLE_WEB_CLIENT_ID,
            "983926366374-c3jvolf9t8e151mbgriuqi7uqnbt4c4p.apps.googleusercontent.com"
        );
        assert!(GOOGLE_WEB_CLIENT_ID.ends_with(".apps.googleusercontent.com"));
        assert_eq!(FIREBASE_API_KEY, "AIzaSyBFPckWOsp0MEqb_1gwszvM1ILdUixM-uw");
        assert!(FIREBASE_API_KEY.starts_with("AIza"));
        assert!(google_id_token_hint().contains("signInWithIdp"));
    }

    #[test]
    fn firebase_sign_in_with_idp_url_shape() {
        assert_eq!(
            firebase_sign_in_with_idp_url("KEY"),
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=KEY"
        );
        // the production URL is the same builder over the baked-in base
        assert_eq!(
            firebase_sign_in_with_idp_url(FIREBASE_API_KEY),
            firebase_sign_in_with_idp_url_at(FIREBASE_IDENTITY_TOOLKIT_BASE, FIREBASE_API_KEY)
        );
        // a trailing slash on the endpoint base is optional (offline test servers)
        assert_eq!(
            firebase_sign_in_with_idp_url_at("http://127.0.0.1:9", "K"),
            firebase_sign_in_with_idp_url_at("http://127.0.0.1:9/", "K")
        );
        assert!(firebase_sign_in_with_idp_url_at("http://127.0.0.1:9/", "K")
            .ends_with("v1/accounts:signInWithIdp?key=K"));
        // reserved characters in a key are percent-encoded
        assert!(firebase_sign_in_with_idp_url("a/b c").ends_with("?key=a%2Fb%20c"));
    }

    #[test]
    fn firebase_body_selects_the_postbody_key() {
        let id = firebase_sign_in_with_idp_body("eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.sig", false);
        assert_eq!(
            id["postBody"],
            "id_token=eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.sig&providerId=google.com"
        );
        assert_eq!(id["requestUri"], "http://localhost");
        assert_eq!(id["returnIdpCredential"], true);
        assert_eq!(id["returnSecureToken"], true);

        let access = firebase_sign_in_with_idp_body("ya29.a0ARW5m7XYZ", true);
        assert_eq!(access["postBody"], "access_token=ya29.a0ARW5m7XYZ&providerId=google.com");
        assert_eq!(access["requestUri"], "http://localhost");
    }

    #[test]
    fn firebase_body_percent_encodes_the_credential() {
        // A token with form-significant characters must survive the postBody split.
        let body = firebase_sign_in_with_idp_body("a b&c=d/e", false);
        let post = body["postBody"].as_str().expect("postBody");
        assert!(post.starts_with("id_token=a%20b%26c%3Dd%2Fe&providerId=google.com"), "{post}");
        // exactly one `&` separator: the credential itself contributes none
        assert_eq!(post.matches('&').count(), 1);
    }

    #[test]
    fn extract_firebase_id_token_variants() {
        assert_eq!(
            extract_firebase_id_token(
                r#"{"kind":"identitytoolkit#VerifyAssertionResponse","localId":"1","idToken":"FB-JWT","refreshToken":"r"}"#
            )
            .as_deref(),
            Some("FB-JWT")
        );
        assert!(extract_firebase_id_token(r#"{"idToken":""}"#).is_none());
        assert!(extract_firebase_id_token(r#"{"refreshToken":"r"}"#).is_none());
        assert!(extract_firebase_id_token(r#"{"idToken":42}"#).is_none());
        assert!(extract_firebase_id_token(r#"{"error":{"message":"INVALID_IDP_RESPONSE"}}"#).is_none());
        assert!(extract_firebase_id_token("not json").is_none());
    }

    #[test]
    fn google_credentials_are_classified_by_shape() {
        let jwt = "eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.signature";
        assert!(is_google_id_token(jwt));
        assert!(!google_token_is_access_token(jwt));
        assert!(is_google_id_token("  ey.h.s  "), "surrounding whitespace is trimmed");

        assert!(google_token_is_access_token("ya29.a0AfB_byC7Qm"));
        assert!(!is_google_id_token("ya29.a0AfB_byC7Qm"));
        assert!(google_token_is_access_token("not-a-jwt"), "opaque payloads are access tokens");
        assert!(!is_google_id_token("ey.two_segments"), "two segments are not a JWT");
        assert!(!is_google_id_token(""));
    }

    #[test]
    fn google_browser_url_points_at_the_loopback_listener() {
        let url = google_browser_authorize_url(GOOGLE_WEB_CLIENT_ID, 43211, "nonce value");
        assert!(
            url.starts_with("https://accounts.google.com/o/oauth2/v2/auth?client_id=983926366374"),
            "{url}"
        );
        assert!(url.contains(
            "&redirect_uri=http%3A%2F%2F127.0.0.1%3A43211&response_type=token%20id_token"
        ));
        assert!(url.contains("&scope=openid%20email%20profile&nonce=nonce%20value"));
        assert_eq!(GOOGLE_SCOPE, "openid email profile");
    }

    #[test]
    fn parse_telegram_redirect_accepts_a_bare_code() {
        // What a user copies out of the address bar when the page itself is blank.
        assert_eq!(
            parse_telegram_redirect("?code=abc123&state=xyz"),
            TelegramRedirect::Code("abc123".into())
        );
        assert_eq!(
            parse_telegram_redirect("code=abc123"),
            TelegramRedirect::Code("abc123".into())
        );
        assert_eq!(
            parse_telegram_redirect("  code=abc123&state=1  "),
            TelegramRedirect::Code("abc123".into())
        );
        assert_eq!(
            parse_telegram_redirect("?error=access_denied&error_description=User%20cancelled"),
            TelegramRedirect::Error("User cancelled".into())
        );
        // a bare id_token still is not a redirect: the caller keeps the JWT
        assert!(matches!(
            parse_telegram_redirect("eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.sig"),
            TelegramRedirect::Error(msg) if msg.starts_with("Malformed redirect URI:")
        ));
    }
}
