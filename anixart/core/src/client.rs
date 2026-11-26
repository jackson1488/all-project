use std::time::Duration;

use reqwest::header::{HeaderMap, HeaderValue, USER_AGENT};
use reqwest::Client;
use serde::de::DeserializeOwned;

use crate::error::{AnixartError, Result};
use crate::models::{
    CodeResponse, CollectionResponse, EpisodeResponse, FeedItem, Pageable, ProfileInfoResponse,
    ProfileResponse, Release, ReleaseResponse, ReleaseSearchResponse, SearchRequest, SignInResponse,
    SocialSignInResponse, SourcesResponse, TypesResponse,
};
use crate::oauth::{self, SocialProvider, TelegramPkce};
use crate::API_BASE_URL;

const DEFAULT_UA: &str =
    "AnixartApp/10.5-26101718 (Android 14; SDK 34; arm64-v8a; Google Pixel 7; ru)";

/// Async Anixart REST client (reqwest + rustls).
#[derive(Debug, Clone)]
pub struct AnixartClient {
    http: Client,
    base_url: String,
    token: Option<String>,
}

impl AnixartClient {
    /// Builds a client pointed at the production API base URL.
    pub fn new() -> Result<Self> {
        Self::with_base_url(API_BASE_URL)
    }

    /// Builds a client with a custom base URL (trailing slash optional).
    pub fn with_base_url(base_url: impl Into<String>) -> Result<Self> {
        let mut headers = HeaderMap::new();
        headers.insert(USER_AGENT, HeaderValue::from_static(DEFAULT_UA));

        let http = Client::builder()
            .default_headers(headers)
            .timeout(Duration::from_secs(20))
            .connect_timeout(Duration::from_secs(10))
            .build()?;

        let mut base_url = base_url.into();
        if !base_url.ends_with('/') {
            base_url.push('/');
        }

        Ok(Self {
            http,
            base_url,
            token: None,
        })
    }

    /// Stores the session token appended as `?token=` on subsequent calls.
    pub fn set_token(&mut self, token: impl Into<String>) {
        self.token = Some(token.into());
    }

    /// Clears the stored session token.
    pub fn clear_token(&mut self) {
        self.token = None;
    }

    /// Returns the currently stored token, if any.
    pub fn token(&self) -> Option<&str> {
        self.token.as_deref()
    }

    /// The underlying HTTP client (connection pool, default headers, timeouts).
    ///
    /// Exposed so the free-function OAuth helpers in [`crate::oauth`] reuse it
    /// instead of building a fresh reqwest client per call.
    pub fn http(&self) -> &Client {
        &self.http
    }

    fn url(&self, path: &str) -> String {
        let path = path.trim_start_matches('/');
        format!("{}{path}", self.base_url)
    }

    fn require_token(&self) -> Result<&str> {
        self.token
            .as_deref()
            .ok_or(AnixartError::MissingToken)
    }

    fn build_url(&self, path: &str, extra: &[(&str, String)]) -> Result<reqwest::Url> {
        let mut url = reqwest::Url::parse(&self.url(path))
            .map_err(|e| AnixartError::Unexpected(e.to_string()))?;
        {
            let mut qp = url.query_pairs_mut();
            if let Some(token) = &self.token {
                qp.append_pair("token", token);
            }
            for (k, v) in extra {
                qp.append_pair(k, v);
            }
        }
        Ok(url)
    }

    async fn get_json<T: DeserializeOwned>(
        &self,
        path: &str,
        extra: &[(&str, String)],
    ) -> Result<T> {
        let url = self.build_url(path, extra)?;
        let resp = self.http.get(url).send().await?.error_for_status()?;
        let body = resp.text().await?;
        Ok(serde_json::from_str(&body)?)
    }

    async fn post_json<T: DeserializeOwned, B: serde::Serialize>(
        &self,
        path: &str,
        body: &B,
        extra_headers: &[(&str, &str)],
    ) -> Result<T> {
        let url = self.build_url(path, &[])?;
        let mut req = self.http.post(url).json(body);
        for (k, v) in extra_headers {
            req = req.header(*k, *v);
        }
        let resp = req.send().await?.error_for_status()?;
        let text = resp.text().await?;
        Ok(serde_json::from_str(&text)?)
    }

    async fn post_form<T: DeserializeOwned>(&self, path: &str, form: &[(&str, &str)]) -> Result<T> {
        self.post_form_with_headers(path, form, &[]).await
    }

    /// Same as [`Self::post_form`] but with extra headers (e.g. `X-Long-Code: 1`,
    /// which turns a social `auth/*` call into a registration server-side).
    async fn post_form_with_headers<T: DeserializeOwned>(
        &self,
        path: &str,
        form: &[(&str, &str)],
        headers: &[(&str, &str)],
    ) -> Result<T> {
        let url = self.url(path);
        let encoded: String = form
            .iter()
            .map(|(k, v)| format!("{k}={}", urlencoding::encode(v)))
            .collect::<Vec<_>>()
            .join("&");
        let mut req = self
            .http
            .post(url)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(encoded);
        for (k, v) in headers {
            req = req.header(*k, *v);
        }
        let resp = req.send().await?.error_for_status()?;
        let text = resp.text().await?;
        Ok(serde_json::from_str(&text)?)
    }

    /// `POST /auth/signIn` — stores the token on success.
    pub async fn login(&mut self, login: &str, password: &str) -> Result<SignInResponse> {
        let resp: SignInResponse = self
            .post_form("auth/signIn", &[("login", login), ("password", password)])
            .await?;
        if let Some(token) = resp.token() {
            self.set_token(token.to_string());
        }
        Ok(resp)
    }

    /// Shared implementation of `POST /auth/{telegram,vk,google,yandex}`.
    ///
    /// The session token is stored automatically when the backend answers
    /// `code == 0`; any other code (`3 = NOT_REGISTERED`, `6 = LOGIN_ALREADY_TAKEN`, …)
    /// is surfaced through the response so the caller can continue with
    /// [`Self::sign_up_telegram`] or the e-mail code flow.
    async fn social_sign_in(
        &mut self,
        provider: SocialProvider,
        credential: &str,
    ) -> Result<SocialSignInResponse> {
        let form = oauth::sign_in_form(provider, credential);
        let refs: Vec<(&str, &str)> = form
            .iter()
            .map(|(k, v)| (k.as_str(), v.as_str()))
            .collect();
        let resp: SocialSignInResponse = self.post_form(provider.endpoint(), &refs).await?;
        if let Some(token) = resp.token() {
            self.set_token(token.to_string());
        }
        Ok(resp)
    }

    /// Shared implementation of the registration variants, which differ only by
    /// the `X-Long-Code: 1` header and the extra `login` / `email` fields.
    async fn social_sign_up(
        &mut self,
        provider: SocialProvider,
        login: &str,
        email: &str,
        credential: &str,
    ) -> Result<SocialSignInResponse> {
        let form = oauth::sign_up_form(provider, login, email, credential);
        let refs: Vec<(&str, &str)> = form
            .iter()
            .map(|(k, v)| (k.as_str(), v.as_str()))
            .collect();
        let resp: SocialSignInResponse = self
            .post_form_with_headers(provider.endpoint(), &refs, &[oauth::LONG_CODE_HEADER])
            .await?;
        if let Some(token) = resp.token() {
            self.set_token(token.to_string());
        }
        Ok(resp)
    }

    /// `POST /auth/telegram` with the `id_token` produced by the Telegram
    /// Login-Widget PKCE flow (see [`Self::exchange_telegram_code`]).
    pub async fn sign_in_telegram(
        &mut self,
        telegram_id_token: &str,
    ) -> Result<SocialSignInResponse> {
        self.social_sign_in(SocialProvider::Telegram, telegram_id_token)
            .await
    }

    /// `POST /auth/vk` with a VK ID access token (scope `email`).
    pub async fn sign_in_vk(&mut self, vk_access_token: &str) -> Result<SocialSignInResponse> {
        self.social_sign_in(SocialProvider::Vk, vk_access_token).await
    }

    /// `POST /auth/google` with a **Firebase** ID token (the Android client
    /// exchanges the Google `idToken` in Firebase first — see `oauth::google_id_token_hint`).
    ///
    /// A raw Google credential (`id_token` JWT or `ya29.…` access token) is
    /// exchanged through [`Self::sign_in_google_via_firebase`] instead; the
    /// implementation lives beside the OAuth request builders in [`crate::oauth`].
    pub async fn sign_in_google(
        &mut self,
        google_id_token: &str,
    ) -> Result<SocialSignInResponse> {
        self.social_sign_in(SocialProvider::Google, google_id_token)
            .await
    }

    /// `POST /auth/yandex` with a Yandex ID OAuth access token (implicit flow).
    pub async fn sign_in_yandex(
        &mut self,
        yandex_access_token: &str,
    ) -> Result<SocialSignInResponse> {
        self.social_sign_in(SocialProvider::Yandex, yandex_access_token)
            .await
    }

    /// `POST /auth/telegram` + `X-Long-Code: 1` (register a new profile).
    pub async fn sign_up_telegram(
        &mut self,
        login: &str,
        email: &str,
        telegram_id_token: &str,
    ) -> Result<SocialSignInResponse> {
        self.social_sign_up(SocialProvider::Telegram, login, email, telegram_id_token)
            .await
    }

    /// `POST /auth/vk` + `X-Long-Code: 1`.
    pub async fn sign_up_vk(
        &mut self,
        login: &str,
        email: &str,
        vk_access_token: &str,
    ) -> Result<SocialSignInResponse> {
        self.social_sign_up(SocialProvider::Vk, login, email, vk_access_token)
            .await
    }

    /// `POST /auth/google` + `X-Long-Code: 1`.
    pub async fn sign_up_google(
        &mut self,
        login: &str,
        email: &str,
        google_id_token: &str,
    ) -> Result<SocialSignInResponse> {
        self.social_sign_up(SocialProvider::Google, login, email, google_id_token)
            .await
    }

    /// `POST /auth/yandex` + `X-Long-Code: 1`.
    pub async fn sign_up_yandex(
        &mut self,
        login: &str,
        email: &str,
        yandex_access_token: &str,
    ) -> Result<SocialSignInResponse> {
        self.social_sign_up(SocialProvider::Yandex, login, email, yandex_access_token)
            .await
    }

    /// Exchanges a Telegram OAuth `code` for an `id_token`
    /// (`POST https://oauth.telegram.org/token`).
    ///
    /// `code_verifier` must be the one whose SHA-256 was sent as
    /// `code_challenge` (see [`oauth::generate_pkce`]).
    pub async fn exchange_telegram_code(
        &self,
        code: &str,
        code_verifier: &str,
    ) -> Result<String> {
        self.exchange_telegram_code_at(oauth::TELEGRAM_TOKEN_URL, code, code_verifier)
            .await
    }

    /// [`Self::exchange_telegram_code`] against an explicit endpoint
    /// (unit tests, or a self-hosted Telegram OAuth proxy).
    pub async fn exchange_telegram_code_at(
        &self,
        url: &str,
        code: &str,
        code_verifier: &str,
    ) -> Result<String> {
        let body = oauth::telegram_token_body(
            oauth::TELEGRAM_CLIENT_ID,
            code,
            oauth::TELEGRAM_REDIRECT_URI,
            code_verifier,
        );
        let resp = self
            .http
            .post(url)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .body(body)
            .send()
            .await?;
        let status = resp.status();
        let text = resp.text().await?;
        if !status.is_success() {
            return Err(AnixartError::OAuth(format!(
                "HTTP {}: {text}",
                status.as_u16()
            )));
        }
        oauth::extract_telegram_id_token(&text)
            .ok_or_else(|| AnixartError::OAuth("No id_token in response".into()))
    }

    /// Asks Telegram for a URL that continues the login inside the native app
    /// (`GET https://oauth.telegram.org/crossapp?...`). `None` means the caller
    /// must fall back to the WebView authorize URL.
    pub async fn fetch_telegram_inapp_url(&self, pkce: &TelegramPkce) -> Result<Option<String>> {
        self.fetch_telegram_inapp_url_at(&oauth::telegram_crossapp_url_with(pkce))
            .await
    }

    /// [`Self::fetch_telegram_inapp_url`] against an explicit endpoint.
    pub async fn fetch_telegram_inapp_url_at(&self, url: &str) -> Result<Option<String>> {
        let resp = self
            .http
            .get(url)
            .header("Accept", "application/json")
            .send()
            .await?;
        if !resp.status().is_success() {
            return Ok(None);
        }
        let text = resp.text().await?;
        Ok(oauth::extract_telegram_inapp_url(&text))
    }

    /// Full Telegram sign-in from a redirect URI: parse `code` / `error`,
    /// exchange the code for an `id_token`, then `POST /auth/telegram`.
    pub async fn sign_in_telegram_redirect(
        &mut self,
        redirect_url: &str,
        code_verifier: &str,
    ) -> Result<SocialSignInResponse> {
        self.sign_in_telegram_redirect_at(oauth::TELEGRAM_TOKEN_URL, redirect_url, code_verifier)
            .await
    }

    /// [`Self::sign_in_telegram_redirect`] against an explicit token endpoint.
    pub async fn sign_in_telegram_redirect_at(
        &mut self,
        token_url: &str,
        redirect_url: &str,
        code_verifier: &str,
    ) -> Result<SocialSignInResponse> {
        match oauth::parse_telegram_redirect(redirect_url) {
            oauth::TelegramRedirect::Error(message) => Err(AnixartError::OAuth(message)),
            oauth::TelegramRedirect::Code(code) => {
                let id_token = self
                    .exchange_telegram_code_at(token_url, &code, code_verifier)
                    .await?;
                self.sign_in_telegram(&id_token).await
            }
        }
    }

    /// `POST /search/releases/{page}`.
    pub async fn search(&self, query: &str, page: u32) -> Result<ReleaseSearchResponse> {
        let body = SearchRequest::new(query);
        self.post_json(
            &format!("search/releases/{page}"),
            &body,
            &[("API-Version", "v2")],
        )
        .await
    }

    /// `GET /feed/latest/all/{page}`.
    pub async fn feed(&self, page: u32) -> Result<Pageable<FeedItem>> {
        self.get_json(&format!("feed/latest/all/{page}"), &[]).await
    }

    /// `GET /feed/all/{page}` with optional channel filter.
    pub async fn feed_all(
        &self,
        page: u32,
        channel_id: Option<i64>,
        date: i32,
    ) -> Result<Pageable<FeedItem>> {
        let date_s = date.to_string();
        let channel_s = channel_id.map(|id| id.to_string());
        let mut extra: Vec<(&str, String)> = vec![("date", date_s)];
        if let Some(id) = channel_s {
            extra.push(("channel_id", id));
        }
        self.get_json(&format!("feed/all/{page}"), &extra).await
    }

    /// `GET /release/{id}`.
    pub async fn release(&self, id: i64, extended: bool) -> Result<ReleaseResponse> {
        self.get_json(
            &format!("release/{id}"),
            &[("extended_mode", extended.to_string())],
        )
        .await
    }

    /// `GET /episode/{releaseId}` — voiceover / type list.
    pub async fn types(&self, release_id: i64) -> Result<TypesResponse> {
        self.get_json(&format!("episode/{release_id}"), &[]).await
    }

    /// `GET /episode/{releaseId}/{typeId}` — sources for a voiceover.
    pub async fn sources(&self, release_id: i64, type_id: i64) -> Result<SourcesResponse> {
        self.get_json(&format!("episode/{release_id}/{type_id}"), &[])
            .await
    }

    /// `GET /episode/{releaseId}/{typeId}/{sourceId}` — episodes.
    pub async fn episodes(
        &self,
        release_id: i64,
        type_id: i64,
        source_id: i64,
        sort: i32,
    ) -> Result<EpisodeResponse> {
        self.get_json(
            &format!("episode/{release_id}/{type_id}/{source_id}"),
            &[("sort", sort.to_string())],
        )
        .await
    }

    /// `GET /favorite/all/{page}`.
    pub async fn favorites(
        &self,
        page: u32,
        sort: Option<i32>,
        filter_announce: Option<i32>,
    ) -> Result<Pageable<Release>> {
        self.require_token()?;
        let mut extra: Vec<(&str, String)> = Vec::new();
        if let Some(s) = sort {
            extra.push(("sort", s.to_string()));
        }
        if let Some(f) = filter_announce {
            extra.push(("filter_announce", f.to_string()));
        }
        self.get_json(&format!("favorite/all/{page}"), &extra).await
    }

    /// `GET /favorite/add/{id}`.
    pub async fn favorite_add(&self, release_id: i64) -> Result<CodeResponse> {
        self.require_token()?;
        self.get_json(&format!("favorite/add/{release_id}"), &[])
            .await
    }

    /// `GET /favorite/delete/{id}`.
    pub async fn favorite_delete(&self, release_id: i64) -> Result<CodeResponse> {
        self.require_token()?;
        self.get_json(&format!("favorite/delete/{release_id}"), &[])
            .await
    }

    /// `GET /history/{page}`.
    pub async fn history(&self, page: u32) -> Result<Pageable<Release>> {
        self.require_token()?;
        self.get_json(&format!("history/{page}"), &[]).await
    }

    /// `GET /history/add/{r_id}/{s_id}/{position}`.
    pub async fn history_add(
        &self,
        release_id: i64,
        source_id: i64,
        position: i32,
    ) -> Result<CodeResponse> {
        self.require_token()?;
        self.get_json(
            &format!("history/add/{release_id}/{source_id}/{position}"),
            &[],
        )
        .await
    }

    /// `GET /history/delete/{r_id}`.
    pub async fn history_delete(&self, release_id: i64) -> Result<CodeResponse> {
        self.require_token()?;
        self.get_json(&format!("history/delete/{release_id}"), &[])
            .await
    }

    /// `GET /profile/info`.
    pub async fn profile_info(&self) -> Result<ProfileInfoResponse> {
        self.require_token()?;
        self.get_json("profile/info", &[]).await
    }

    /// `GET /profile/{id}`.
    pub async fn profile(&self, id: i64) -> Result<ProfileResponse> {
        self.get_json(&format!("profile/{id}"), &[]).await
    }

    /// `GET /collection/{id}`.
    pub async fn collection(&self, id: i64) -> Result<CollectionResponse> {
        self.get_json(&format!("collection/{id}"), &[]).await
    }
}

