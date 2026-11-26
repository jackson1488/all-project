//! Login screen: password, social OAuth through the system browser, and a
//! direct Anixart session token.
//!
//! Every method ends in the same place — an Anixart `profileToken.token` — and
//! the difference is only how the credential is obtained:
//!
//! * **Password** — `POST /auth/signIn` (`anixart-core`).
//! * **Telegram** — OAuth 2.0 + PKCE Login Widget. The app generates the PKCE
//!   pair, opens `https://oauth.telegram.org/auth` with `xdg-open`, and the user
//!   pastes back what the browser lands on. Any of these forms works: the full
//!   `https://app…-login.tg.dev/tglogin?code=…` URL, just the `?code=…` /
//!   `code=…` query part copied out of the address bar (the code is then
//!   exchanged for an `id_token` with the PKCE verifier this window generated),
//!   or an `id_token` obtained elsewhere.
//! * **VK** — VK ID. The browser is opened on the VK ID authorize URL; the user
//!   pastes the redirect URL carrying `access_token`, or the token itself.
//! * **Google** — one click, no token hunting. The Google OAuth web client id
//!   and the Firebase API key the Anixart APK ships are pre-filled
//!   (`oauth::GOOGLE_WEB_CLIENT_ID`, `oauth::FIREBASE_API_KEY`), and **“Sign in
//!   with Google (Browser)”** binds a loopback listener on `127.0.0.1:0`, opens
//!   the implicit-flow authorize URL against it, catches the redirect the browser
//!   lands on (a relay page reads the URL fragment and POSTs it back), exchanges
//!   the Google credential for a **Firebase** ID token through
//!   `identitytoolkit.googleapis.com` `accounts:signInWithIdp`, and posts that to
//!   `auth/google`. The paste box stays as the universal fallback and also
//!   exchanges a raw Google JWT / `ya29…` access token through Firebase when
//!   Anixart rejects it as a Firebase token.
//! * **Token** — an already-issued Anixart session token is accepted instantly,
//!   without any request.
//!
//! A loopback callback listener is used for Google only: a Google web client id
//! accepts `http://127.0.0.1:<any port>`. The Telegram / VK redirect hosts
//! (`app1593901251-login.tg.dev`, `vk54701760://vk.ru/blank.html`) are bound to
//! the Android package, so a `http://localhost` redirect would be rejected by
//! those identity providers — copying the URL the browser lands on is the
//! equivalent, provider-approved fallback there.

use anixart_core::oauth;
use iced::widget::{
    button, container, horizontal_space, scrollable, text, text_input, Column, Row,
};
use iced::{Element, Length, Theme};

use crate::app::Message;

/// Login methods accepted by the Anixart backend (`AuthApi.java`).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum LoginMethod {
    #[default]
    Password,
    Telegram,
    Vk,
    Google,
    /// Direct session token — instant login, no network call.
    Token,
}

impl LoginMethod {
    /// Every method, in tab order.
    pub const ALL: [LoginMethod; 5] = [
        LoginMethod::Password,
        LoginMethod::Telegram,
        LoginMethod::Vk,
        LoginMethod::Google,
        LoginMethod::Token,
    ];

    /// Tab caption.
    pub fn label(self) -> &'static str {
        match self {
            LoginMethod::Password => "Password",
            LoginMethod::Telegram => "Telegram",
            LoginMethod::Vk => "VK",
            LoginMethod::Google => "Google",
            LoginMethod::Token => "Token",
        }
    }

    /// Stable identifier persisted in `session.json`.
    pub fn as_str(self) -> &'static str {
        match self {
            LoginMethod::Password => "password",
            LoginMethod::Telegram => "telegram",
            LoginMethod::Vk => "vk",
            LoginMethod::Google => "google",
            LoginMethod::Token => "token",
        }
    }
}

/// What the user pasted on the Telegram tab.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TelegramPaste {
    /// A redirect URI: it must be turned into an `id_token` with the PKCE
    /// verifier the app generated when it opened the browser.
    Redirect(String),
    /// A ready-made `id_token` — posted straight to `auth/telegram`.
    IdToken(String),
}

/// Default redirect URI of the manual (paste-back) Google flow. Google web client
/// ids accept the loopback origin, and `http://localhost` is the URI
/// `google-services.json` registers for `default_web_client_id`.
pub const GOOGLE_MANUAL_REDIRECT: &str = "http://localhost";

/// State of the login screen.
#[derive(Debug)]
pub struct Login {
    pub method: LoginMethod,
    pub loading: bool,

    // Password
    pub username: String,
    pub password: String,

    // Telegram
    pub telegram_verifier: String,
    pub telegram_paste: String,

    // VK
    pub vk_verifier: String,
    pub vk_paste: String,

    // Google — the client id and the Firebase API key arrive pre-filled with the
    // values the Anixart APK ships, so the browser sign-in needs no setup.
    pub google_client_id: String,
    pub firebase_api_key: String,
    pub google_redirect: String,
    pub google_paste: String,

    // Direct token
    pub token: String,
}

impl Default for Login {
    /// Everything empty except the two Google / Firebase identifiers, which are
    /// the ones the APK ships (`docs/oauth.md` §4). Clearing a field falls back
    /// to the baked-in constant, so the 1-click button never runs dry.
    fn default() -> Self {
        Self {
            method: LoginMethod::default(),
            loading: false,
            username: String::new(),
            password: String::new(),
            telegram_verifier: String::new(),
            telegram_paste: String::new(),
            vk_verifier: String::new(),
            vk_paste: String::new(),
            google_client_id: oauth::GOOGLE_WEB_CLIENT_ID.to_owned(),
            firebase_api_key: oauth::FIREBASE_API_KEY.to_owned(),
            google_redirect: String::new(),
            google_paste: String::new(),
            token: String::new(),
        }
    }
}

impl Login {
    /// Telegram authorize URL for the current PKCE material, generating and
    /// remembering a fresh verifier when none is present.
    pub fn prepare_telegram(&mut self) -> String {
        if self.telegram_verifier.is_empty() {
            self.telegram_verifier = match oauth::generate_pkce() {
                Ok(pkce) => pkce.verifier,
                Err(_) => random_verifier(),
            };
        }
        let challenge = oauth::code_challenge(&self.telegram_verifier);
        oauth::telegram_authorize_url(
            oauth::TELEGRAM_CLIENT_ID,
            oauth::TELEGRAM_REDIRECT_URI,
            oauth::TELEGRAM_SCOPE,
            &challenge,
        )
    }

    /// VK ID authorize URL (PKCE), generating the verifier when needed.
    ///
    /// The vendor SDK adds several extra parameters and performs the code
    /// exchange itself, so this URL is a convenience: the credential the
    /// backend wants is the resulting `access_token`.
    pub fn prepare_vk(&mut self) -> String {
        if self.vk_verifier.is_empty() {
            self.vk_verifier = match oauth::generate_pkce() {
                Ok(pkce) => pkce.verifier,
                Err(_) => random_verifier(),
            };
        }
        let challenge = oauth::code_challenge(&self.vk_verifier);
        let state: String = self.vk_verifier.chars().take(8).collect();
        oauth::vk_authorize_url(
            oauth::VK_CLIENT_ID,
            oauth::VK_REDIRECT_URI,
            &challenge,
            &state,
            oauth::VK_SCOPE,
        )
    }

    /// Google implicit-flow authorize URL for the **manual, paste-back** flow
    /// (the 1-click button uses the loopback listener instead of this one).
    ///
    /// `response_type=token id_token` asks Google for both credentials, so the
    /// URL the user pastes back carries an `id_token` — or an `access_token` when
    /// the client id is not enabled for the ID token. Either is exchanged through
    /// Firebase before it reaches `auth/google`.
    pub fn prepare_google(&self) -> String {
        format!(
            "{}?client_id={}&redirect_uri={}&response_type=token%20id_token&scope={}&nonce={}",
            oauth::GOOGLE_OAUTH_AUTH_URL,
            urlencoding::encode(&self.effective_google_client_id()),
            urlencoding::encode(&pick(&self.google_redirect, GOOGLE_MANUAL_REDIRECT)),
            urlencoding::encode(oauth::GOOGLE_SCOPE),
            urlencoding::encode(&random_verifier()),
        )
    }

    /// The Google OAuth client id the browser flows use: the edited one when it
    /// is non-empty, otherwise the id the APK ships.
    pub fn effective_google_client_id(&self) -> String {
        pick(&self.google_client_id, oauth::GOOGLE_WEB_CLIENT_ID)
    }

    /// The Firebase API key the `accounts:signInWithIdp` exchange is authorised
    /// with, defaulting to the one the APK ships.
    pub fn effective_firebase_api_key(&self) -> String {
        pick(&self.firebase_api_key, oauth::FIREBASE_API_KEY)
    }

    /// The credential to send for the paste-based flows, if any.
    pub fn telegram_credential(&self) -> Option<TelegramPaste> {
        classify_telegram_paste(&self.telegram_paste)
    }

    pub fn vk_credential(&self) -> Option<String> {
        vk_access_token(&self.vk_paste)
    }

    pub fn google_credential(&self) -> Option<String> {
        google_id_token(&self.google_paste)
    }

    pub fn view(&self) -> Element<'_, Message> {
        let theme: fn(&Theme, button::Status) -> button::Style = button::primary;
        let ghost: fn(&Theme, button::Status) -> button::Style = button::secondary;

        let mut tabs = Row::new().spacing(8);
        let current = self.method;
        for method in LoginMethod::ALL {
            let active = method == current;
            tabs = tabs.push(
                button(text(method.label()).size(15))
                    .style(if active { theme } else { ghost })
                    .padding(10)
                    .on_press_maybe((!active && !self.loading).then_some(Message::LoginTab(method))),
            );
        }

        let body = match current {
            LoginMethod::Password => self.password_form(),
            LoginMethod::Telegram => self.telegram_form(),
            LoginMethod::Vk => self.vk_form(),
            LoginMethod::Google => self.google_form(),
            LoginMethod::Token => self.token_form(),
        };

        container(
            scrollable(
                Column::new()
                    .spacing(16)
                    .max_width(560)
                    .push(text("Anixart").size(30))
                    .push(
                        text("Sign in to reach your feed and collections")
                            .size(14)
                            .color([0.6, 0.6, 0.7]),
                    )
                    .push(tabs)
                    .push(body),
            )
            .height(Length::Fill),
        )
        .center_x(Length::Fill)
        .padding(12)
        .into()
    }

    fn password_form(&self) -> Element<'_, Message> {
        let username = text_input("Username or e-mail", &self.username)
            .on_input(Message::LoginUsername)
            .on_submit(Message::LoginSubmit)
            .padding(10);
        let password = text_input("Password", &self.password)
            .secure(true)
            .on_input(Message::LoginPassword)
            .on_submit(Message::LoginSubmit)
            .padding(10);

        Column::new()
            .spacing(10)
            .push(username)
            .push(password)
            .push(self.submit("Login", Message::LoginSubmit))
            .into()
    }

    fn telegram_form(&self) -> Element<'_, Message> {
        Column::new()
            .spacing(10)
            .push(hint(
                "1. Open the Telegram login in your browser and allow the app. \
                 2. After confirming, the browser lands on \
                 https://app1593901251-login.tg.dev/tglogin?code=… — even when that \
                 page looks blank, fails to load or immediately redirects, the address \
                 bar holds what is needed. \
                 3. Paste it below: the whole URL, or only the ?code=… / code=… part, \
                 both work — the code is exchanged for an id_token with the PKCE \
                 verifier this window generated. A raw Telegram id_token obtained \
                 elsewhere is accepted as well.",
            ))
            .push(button("Open Telegram login in browser")
                .width(Length::Fill)
                .padding(10)
                .on_press(Message::LoginTelegramOpen))
            .push(text_input("Redirect URL, ?code=… or Telegram id_token", &self.telegram_paste)
                .on_input(Message::LoginTelegramPaste)
                .on_submit(Message::LoginTelegramSubmit)
                .padding(10))
            .push(self.submit("Continue with Telegram", Message::LoginTelegramSubmit))
            .into()
    }

    fn vk_form(&self) -> Element<'_, Message> {
        Column::new()
            .spacing(10)
            .push(hint(
                "Open VK ID in the browser and paste the URL it redirects to \
                 (it carries access_token=…), or paste a VK ID access token with \
                 scope email obtained from any VK ID client.",
            ))
            .push(button("Open VK ID login in browser")
                .width(Length::Fill)
                .padding(10)
                .on_press(Message::LoginVkOpen))
            .push(text_input("Redirect URL or VK access token", &self.vk_paste)
                .on_input(Message::LoginVkPaste)
                .on_submit(Message::LoginVkSubmit)
                .padding(10))
            .push(self.submit("Continue with VK", Message::LoginVkSubmit))
            .into()
    }

    fn google_form(&self) -> Element<'_, Message> {
        let primary: fn(&Theme, button::Status) -> button::Style = button::primary;

        Column::new()
            .spacing(10)
            .push(hint(
                "One click is enough — nothing to look up. The Google client id and the \
                 Firebase API key below are the ones the Anixart APK ships. \
                 “Sign in with Google (Browser)” listens on a local loopback port, opens \
                 Google in the browser, picks the credential out of the redirect it lands \
                 on, exchanges it for a Firebase ID token (identitytoolkit \
                 accounts:signInWithIdp) and signs in through auth/google automatically.",
            ))
            .push(
                button(text("Sign in with Google (Browser)").size(15))
                    .width(Length::Fill)
                    .padding(12)
                    .style(primary)
                    .on_press_maybe((!self.loading).then_some(Message::LoginGoogleBrowser)),
            )
            .push(hint(
                "Fallback: paste the redirect URL the browser lands on (it carries \
                 id_token=… or access_token=…) or any Google / Firebase token. A raw \
                 Google JWT or ya29… access token is exchanged through Firebase on its \
                 own if Anixart rejects it. Only edit the two identifiers below if the \
                 shipped ones ever stop working.",
            ))
            .push(text_input("Google OAuth client id (pre-filled from the APK)", &self.google_client_id)
                .on_input(Message::LoginGoogleClientId)
                .padding(10))
            .push(text_input("Firebase API key (pre-filled from the APK)", &self.firebase_api_key)
                .on_input(Message::LoginFirebaseKey)
                .padding(10))
            .push(text_input("Redirect URI for the manual flow (optional, default http://localhost)", &self.google_redirect)
                .on_input(Message::LoginGoogleRedirect)
                .padding(10))
            .push(button("Open Google login in browser (manual, paste-back)")
                .width(Length::Fill)
                .padding(10)
                .on_press(Message::LoginGoogleOpen))
            .push(text_input("Redirect URL or (Firebase) id_token", &self.google_paste)
                .on_input(Message::LoginGooglePaste)
                .on_submit(Message::LoginGoogleSubmit)
                .padding(10))
            .push(self.submit("Continue with Google", Message::LoginGoogleSubmit))
            .into()
    }

    fn token_form(&self) -> Element<'_, Message> {
        Column::new()
            .spacing(10)
            .push(hint(
                "Paste an existing Anixart session token (profileToken.token) to log in \
                 instantly. It is saved to ~/.config/anixart-linux/session.json and reused \
                 on the next start.",
            ))
            .push(text_input("Anixart session token", &self.token)
                .on_input(Message::LoginTokenPaste)
                .on_submit(Message::LoginTokenSubmit)
                .padding(10))
            .push(self.submit("Use this token", Message::LoginTokenSubmit))
            .into()
    }

    fn submit(&self, label: &'static str, message: Message) -> Element<'_, Message> {
        if self.loading {
            return button(text("Working…").size(14))
                .width(Length::Fill)
                .padding(10)
                .into();
        }
        button(text(label).size(14))
            .width(Length::Fill)
            .padding(10)
            .on_press(message)
            .into()
    }
}

fn hint(text_: &str) -> Element<'_, Message> {
    Row::new()
        .spacing(10)
        .push(text(text_).size(13).color([0.62, 0.66, 0.78]))
        .push(horizontal_space())
        .into()
}

/// Distinguishes a pasted redirect URI from a bare Telegram `id_token`.
pub fn classify_telegram_paste(input: &str) -> Option<TelegramPaste> {
    let input = input.trim();
    if input.is_empty() {
        return None;
    }
    // `tg://login?code=…` and the https redirect both count as a redirect URI.
    if input.contains("://") || input.contains("code=") {
        return Some(TelegramPaste::Redirect(input.to_owned()));
    }
    Some(TelegramPaste::IdToken(input.to_owned()))
}

/// Pulls a VK ID access token out of a pasted redirect URL or the token itself.
pub fn vk_access_token(input: &str) -> Option<String> {
    let input = input.trim();
    if input.is_empty() {
        return None;
    }
    if input.contains("://") {
        return param_in_url(input, "access_token");
    }
    Some(input.to_owned())
}

/// Pulls a Google / Firebase ID token out of a pasted redirect URL, a bare
/// fragment (`#id_token=…`) or the token itself.
pub fn google_id_token(input: &str) -> Option<String> {
    google_credential_from(input).map(|(token, _)| token)
}

/// Splits whatever the Google flow produced into the credential Firebase
/// consumes plus the `postBody` key it belongs to.
///
/// Accepted inputs: the full redirect URL the browser lands on (query *or*
/// fragment), a lone fragment / query string handed over by the loopback relay
/// page, and a bare token — a three-segment JWT is treated as an already-signed
/// ID token, anything else (`ya29.…`) as an OAuth access token.
///
/// `None` when the input carries only an OAuth error or no credential at all.
pub fn google_credential_from(input: &str) -> Option<(String, bool)> {
    let input = input.trim();
    if input.is_empty() {
        return None;
    }
    let lookup = |key: &str| -> Option<String> {
        if input.contains("://") {
            param_in_url(input, key)
        } else {
            param_in_pairs(input, key)
        }
    };
    if let Some(token) = lookup("id_token") {
        return Some((token, false));
    }
    if let Some(token) = lookup("access_token") {
        return Some((token, true));
    }
    // A landing page that only carries `error=…` (or nothing) is not a credential.
    if input.contains("error=") || (input.contains("://") && !input.contains('=')) {
        return None;
    }
    Some((
        input.to_owned(),
        oauth::google_token_is_access_token(input),
    ))
}

/// Reason Google gave for refusing the sign-in, read out of a callback redirect
/// (`#error=access_denied&error_description=…`). `None` on a successful landing.
pub fn google_callback_error(payload: &str) -> Option<String> {
    let payload = payload.trim();
    if payload.is_empty() {
        return None;
    }
    let lookup = |key: &str| -> Option<String> {
        if payload.contains("://") {
            param_in_url(payload, key)
        } else {
            param_in_pairs(payload, key)
        }
    };
    lookup("error_description").or_else(|| lookup("error"))
}

/// `value` trimmed, or `fallback` when it is blank — so a cleared identifier field
/// still produces a working browser flow.
fn pick(value: &str, fallback: &str) -> String {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        fallback.to_owned()
    } else {
        trimmed.to_owned()
    }
}

/// Reads `key` out of a bare query / fragment payload (`#id_token=…&state=…`,
/// `?code=…`, `code=…`) by rebuilding it as a URL.
fn param_in_pairs(pairs: &str, key: &str) -> Option<String> {
    let query = pairs.trim_start_matches(|c| matches!(c, '#' | '?' | '&'));
    let url = reqwest::Url::parse(&format!("https://anixart.invalid/?{query}")).ok()?;
    let (_, value) = url.query_pairs().find(|(name, _)| name == key)?;
    Some(value.into_owned())
}

/// Reads `key` from the query string, falling back to the URL fragment
/// (the OAuth implicit flow carries its parameters there).
fn param_in_url(url: &str, key: &str) -> Option<String> {
    let parsed = reqwest::Url::parse(url).ok()?;
    if let Some((_, value)) = parsed.query_pairs().find(|(name, _)| name == key) {
        return Some(value.into_owned());
    }
    let fragment = parsed.fragment()?.trim_start_matches('?');
    let rebuilt = reqwest::Url::parse(&format!("https://anixart.invalid/?{fragment}")).ok()?;
    let (_, value) = rebuilt.query_pairs().find(|(name, _)| name == key)?;
    Some(value.into_owned())
}

/// Fallback PKCE material / OAuth nonce when the OS RNG wrapper is unavailable.
///
/// An atomic counter is mixed into the clock so two calls in the same
/// nanosecond still differ.
pub(crate) fn random_verifier() -> String {
    use std::sync::atomic::{AtomicU64, Ordering};

    const ALPHABET: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    static COUNTER: AtomicU64 = AtomicU64::new(0);

    let seed = match std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH) {
        Ok(delta) => delta.as_secs() ^ (u64::from(delta.subsec_nanos()) << 32),
        Err(_) => 0x2545_F491_4F6C_DD1D,
    };
    let tick = COUNTER.fetch_add(1, Ordering::Relaxed);
    let mut state = (seed ^ tick.wrapping_mul(0x9E37_79B9_7F4A_7C15)) | 1;

    let mut out = String::with_capacity(43);
    for _ in 0..43 {
        state ^= state << 13;
        state ^= state >> 7;
        state ^= state << 17;
        out.push(ALPHABET[(state % 64) as usize] as char);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn methods_cover_the_auth_api() {
        let labels: Vec<&str> = LoginMethod::ALL.iter().map(|m| m.as_str()).collect();
        assert_eq!(labels, ["password", "telegram", "vk", "google", "token"]);
        assert_eq!(LoginMethod::default(), LoginMethod::Password);
    }

    #[test]
    fn telegram_redirect_is_detected() {
        let url = "https://app1593901251-login.tg.dev/tglogin?code=abc&state=1";
        assert_eq!(
            classify_telegram_paste(url),
            Some(TelegramPaste::Redirect(url.to_string()))
        );
        assert_eq!(
            classify_telegram_paste("tg://login?code=abc"),
            Some(TelegramPaste::Redirect("tg://login?code=abc".into()))
        );
    }

    #[test]
    fn bare_jwt_is_an_id_token() {
        let jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.sig";
        assert_eq!(classify_telegram_paste(jwt), Some(TelegramPaste::IdToken(jwt.into())));
        assert_eq!(classify_telegram_paste("   "), None);
    }

    #[test]
    fn vk_token_comes_from_query_or_paste() {
        assert_eq!(
            vk_access_token("https://vk.ru/?foo=1&access_token=VK123&expires=7200").as_deref(),
            Some("VK123")
        );
        assert_eq!(
            vk_access_token("vk54701760://vk.ru/blank.html#access_token=FRA&state=s")
                .as_deref(),
            Some("FRA")
        );
        assert_eq!(vk_access_token("PLAIN").as_deref(), Some("PLAIN"));
        assert_eq!(vk_access_token(""), None);
        assert_eq!(vk_access_token("https://vk.ru/blank.html"), None);
    }

    #[test]
    fn google_prefers_id_token_then_access_token() {
        assert_eq!(
            google_id_token("https://localhost#id_token=GAP.eyJhbGciOiGS.access").as_deref(),
            Some("GAP.eyJhbGciOiGS.access")
        );
        assert_eq!(
            google_id_token("https://localhost/?access_token=FB").as_deref(),
            Some("FB")
        );
        assert_eq!(google_id_token("ey.abc.def").as_deref(), Some("ey.abc.def"));
        assert_eq!(google_id_token(""), None);
        assert_eq!(google_id_token("https://localhost/"), None);
    }

    #[test]
    fn google_credentials_carry_their_postbody_kind() {
        // A signed redirect is an id_token → `postBody: id_token=…`.
        assert_eq!(
            google_credential_from("https://localhost/#id_token=GOOG.JWT.tok&expires_in=3600"),
            Some(("GOOG.JWT.tok".to_owned(), false))
        );
        // Only an access token came back → `postBody: access_token=…`.
        assert_eq!(
            google_credential_from("#access_token=ya29.a0AfB&token_type=Bearer"),
            Some(("ya29.a0AfB".to_owned(), true))
        );
        // The loopback relay page posts the fragment without its leading `#`.
        assert_eq!(
            google_credential_from("id_token=FB.JWT.tok&scope=openid%20email"),
            Some(("FB.JWT.tok".to_owned(), false))
        );
        assert_eq!(
            google_credential_from("?access_token=ya29.abc"),
            Some(("ya29.abc".to_owned(), true))
        );
        // Bare tokens are classified by shape.
        assert_eq!(
            google_credential_from("eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.sig"),
            Some(("eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.sig".to_owned(), false))
        );
        assert_eq!(
            google_credential_from("ya29.a0AfB_byC"),
            Some(("ya29.a0AfB_byC".to_owned(), true))
        );
        // An OAuth error carries no credential.
        assert_eq!(
            google_credential_from("https://localhost/#error=access_denied&error_description=User%20cancelled"),
            None
        );
        assert_eq!(google_credential_from("#error=access_denied"), None);
        assert_eq!(google_credential_from("   "), None);
    }

    #[test]
    fn google_callback_errors_are_read_from_query_or_fragment() {
        assert_eq!(
            google_callback_error(
                "https://localhost/#error=access_denied&error_description=User%20cancelled"
            )
            .as_deref(),
            Some("User cancelled")
        );
        // fragment-only payload, without the error_description detail
        assert_eq!(
            google_callback_error("#error=invalid_request").as_deref(),
            Some("invalid_request")
        );
        assert_eq!(
            google_callback_error("?error=access_denied").as_deref(),
            Some("access_denied")
        );
        // a successful landing has no error
        assert_eq!(google_callback_error("#id_token=FB.JWT.tok"), None);
        assert_eq!(google_callback_error(""), None);
    }

    #[test]
    fn google_identifiers_default_to_the_apk_values() {
        let mut login = Login::default();
        // Pre-filled from `anixart_core::oauth`, so no token hunting is needed.
        assert_eq!(login.google_client_id, oauth::GOOGLE_WEB_CLIENT_ID);
        assert_eq!(login.firebase_api_key, oauth::FIREBASE_API_KEY);

        let url = login.prepare_google();
        assert!(
            url.starts_with("https://accounts.google.com/o/oauth2/v2/auth?client_id=983926366374"),
            "{url}"
        );
        assert!(url.contains("&redirect_uri=http%3A%2F%2Flocalhost&response_type=token%20id_token"));
        assert!(url.contains("&scope=openid%20email%20profile&nonce="));

        // Clearing a field falls back to the baked-in constant.
        login.google_client_id.clear();
        login.firebase_api_key.clear();
        assert_eq!(login.effective_google_client_id(), oauth::GOOGLE_WEB_CLIENT_ID);
        assert_eq!(login.effective_firebase_api_key(), oauth::FIREBASE_API_KEY);
        assert!(login.prepare_google().contains("client_id=983926366374"));

        // An explicit override wins, and so does a custom redirect URI.
        login.google_client_id = "my-own.client.id".into();
        login.firebase_api_key = "AIzaMyOwnKey".into();
        login.google_redirect = "https://example.invalid/cb";
        let url = login.prepare_google();
        assert!(url.contains("client_id=my-own.client.id"));
        assert!(url.contains("redirect_uri=https%3A%2F%2Fexample.invalid%2Fcb"));
        assert_eq!(login.effective_firebase_api_key(), "AIzaMyOwnKey");
    }

    #[test]
    fn telegram_accepts_a_bare_code_paste() {
        // The tg.dev page is often blank / redirects, so only the address bar
        // query is copyable — that has to work as well as the full URL.
        assert_eq!(
            classify_telegram_paste("?code=abc123&state=xyz"),
            Some(TelegramPaste::Redirect("?code=abc123&state=xyz".into()))
        );
        assert_eq!(
            classify_telegram_paste("code=abc123"),
            Some(TelegramPaste::Redirect("code=abc123".into()))
        );
        // A raw id_token still goes straight to auth/telegram.
        let jwt = "eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.signature";
        assert_eq!(classify_telegram_paste(jwt), Some(TelegramPaste::IdToken(jwt.into())));
    }

    #[test]
    fn telegram_authorize_url_carries_generated_pkce() {
        let mut login = Login::default();
        let url = login.prepare_telegram();
        assert!(url.starts_with("https://oauth.telegram.org/auth?client_id=8789559054"));
        assert!(url.contains("&code_challenge_method=S256"));
        assert!(!login.telegram_verifier.is_empty());

        // The verifier is reused, so the same challenge survives a re-open.
        let again = login.prepare_telegram();
        assert_eq!(url, again);

        let challenge = oauth::code_challenge(&login.telegram_verifier);
        assert!(url.contains(&format!("code_challenge={challenge}")));
    }

    #[test]
    fn vk_authorize_url_uses_the_baked_in_client() {
        let mut login = Login::default();
        let url = login.prepare_vk();
        assert!(url.starts_with("https://id.vk.ru/authorize?client_id=54701760"));
        assert!(url.contains("&scope=email"));
        assert!(url.contains("code_challenge="));
    }

    #[test]
    fn fallback_verifier_is_base64url_shaped() {
        let a = random_verifier();
        assert_eq!(a.len(), 43);
        assert!(a.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_'));
        assert_ne!(a, random_verifier());
    }
}
