//! Login screen: password, social OAuth through the system browser, and a
//! direct Anixart session token.
//!
//! Every method ends in the same place — an Anixart `profileToken.token` — and
//! the difference is only how the credential is obtained:
//!
//! * **Password** — `POST /auth/signIn` (`anixart-core`).
//! * **Telegram** — OAuth 2.0 + PKCE Login Widget. The app generates the PKCE
//!   pair, opens `https://oauth.telegram.org/auth` with `xdg-open`, and the user
//!   pastes back either the final `https://app…-login.tg.dev/tglogin?code=…`
//!   URL (the code is then exchanged for an `id_token`) or an `id_token`
//!   obtained elsewhere.
//! * **VK** — VK ID. The browser is opened on the VK ID authorize URL; the user
//!   pastes the redirect URL carrying `access_token`, or the token itself.
//! * **Google** — the backend expects a **Firebase** ID token (see
//!   `anixart_core::oauth::google_id_token_hint`), so the browser button only
//!   helps when a Google OAuth client id is supplied; pasting a token is the
//!   universal path.
//! * **Token** — an already-issued Anixart session token is accepted instantly,
//!   without any request.
//!
//! A local HTTP callback listener is deliberately not used: the registered
//! Telegram / VK redirect hosts (`app1593901251-login.tg.dev`,
//! `vk54701760://vk.ru/blank.html`) are bound to the Android package, so a
//! `http://localhost` redirect would be rejected by the identity providers.
//! Copying the URL the browser lands on is the equivalent, provider-approved
//! fallback.

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

/// State of the login screen.
#[derive(Debug, Default)]
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

    // Google
    pub google_client_id: String,
    pub google_redirect: String,
    pub google_paste: String,

    // Direct token
    pub token: String,
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

    /// Google implicit-flow authorize URL; `None` without a client id.
    pub fn prepare_google(&mut self) -> Option<String> {
        let client_id = self.google_client_id.trim();
        if client_id.is_empty() {
            return None;
        }
        let redirect = if self.google_redirect.trim().is_empty() {
            "https://localhost".to_owned()
        } else {
            self.google_redirect.trim().to_owned()
        };
        Some(format!(
            "https://accounts.google.com/o/oauth2/v2/auth?client_id={}&redirect_uri={}\
             &response_type=id_token&scope=openid%20email&nonce={}",
            urlencoding::encode(client_id),
            urlencoding::encode(&redirect),
            urlencoding::encode(&random_verifier()),
        ))
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
                 2. Copy the final URL from the address bar (it starts with \
                 https://app1593901251-login.tg.dev/tglogin?code=…). \
                 3. Paste it below — the code is exchanged for an id_token with the \
                 PKCE verifier this window generated. You may also paste an id_token \
                 directly.",
            ))
            .push(button("Open Telegram login in browser")
                .width(Length::Fill)
                .padding(10)
                .on_press(Message::LoginTelegramOpen))
            .push(text_input("Redirect URL or Telegram id_token", &self.telegram_paste)
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
        Column::new()
            .spacing(10)
            .push(hint(
                "The Anixart backend expects a Firebase ID token, not the raw Google \
                 one. Paste that token below, or supply a Google OAuth client id to \
                 open the browser sign-in first.",
            ))
            .push(text_input("Google OAuth client id (optional)", &self.google_client_id)
                .on_input(Message::LoginGoogleClientId)
                .padding(10))
            .push(text_input("Redirect URI (optional, default https://localhost)", &self.google_redirect)
                .on_input(Message::LoginGoogleRedirect)
                .padding(10))
            .push(button("Open Google login in browser")
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

    fn submit(&self, label: &str, message: Message) -> Element<'_, Message> {
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

/// Pulls a Google / Firebase ID token out of a pasted redirect URL or the token.
pub fn google_id_token(input: &str) -> Option<String> {
    let input = input.trim();
    if input.is_empty() {
        return None;
    }
    if input.contains("://") {
        if let Some(token) = param_in_url(input, "id_token") {
            return Some(token);
        }
        return param_in_url(input, "access_token");
    }
    Some(input.to_owned())
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

/// Fallback PKCE material when the OS RNG wrapper is unavailable.
///
/// An atomic counter is mixed into the clock so two calls in the same
/// nanosecond still differ.
fn random_verifier() -> String {
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
    fn google_authorize_url_needs_a_client_id() {
        let mut login = Login::default();
        assert_eq!(login.prepare_google(), None);
        login.google_client_id = "123.apps.googleusercontent.com".into();
        let url = login.prepare_google().expect("url");
        assert!(url.starts_with("https://accounts.google.com/o/oauth2/v2/auth?client_id=123"));
        assert!(url.contains("redirect_uri=https%3A%2F%2Flocalhost"));
        assert!(url.contains("response_type=id_token"));
    }

    #[test]
    fn fallback_verifier_is_base64url_shaped() {
        let a = random_verifier();
        assert_eq!(a.len(), 43);
        assert!(a.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_'));
        assert_ne!(a, random_verifier());
    }
}
