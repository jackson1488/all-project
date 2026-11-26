//! Async glue between the iced runtime and `anixart-core` / `anixart-parsers`.
//!
//! Every helper builds its client *inside* the spawned future: iced is powered by
//! the `tokio` executor, so all reqwest traffic then runs on a real tokio runtime.

use std::time::Duration;

use anixart_core::{
    AnixartClient, Episode, EpisodeType, FeedItem, Pageable, Release, ReleaseSearchResponse,
    SocialSignInResponse, Source,
};
use anixart_parsers::ParsedVideo;

/// Builds an API client, attaching the session token when available.
fn api_client(token: Option<String>) -> Result<AnixartClient, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    if let Some(token) = token {
        client.set_token(token);
    }
    Ok(client)
}

/// `POST /auth/signin` — resolves to the session token.
pub async fn login(username: String, password: String) -> Result<String, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    client
        .login(&username, &password)
        .await
        .map_err(|e| e.to_string())?;

    client
        .token()
        .map(str::to_owned)
        .ok_or_else(|| "No token in sign-in response".to_owned())
}

/// Turns a social `auth/*` answer into either the session token or a message
/// that explains what the caller has to do next (see `docs/oauth.md` §1).
fn token_of(response: &SocialSignInResponse) -> Result<String, String> {
    if let Some(token) = response.token().filter(|t| !t.is_empty()) {
        return Ok(token.to_owned());
    }
    Err(describe_code(response.code))
}

/// Human-readable form of the `auth/{provider}` response codes.
fn describe_code(code: i32) -> String {
    match code {
        0 => "No token in sign-in response".to_owned(),
        1 => "Anixart rejected the credential (code 1)".to_owned(),
        2 => "Invalid OAuth request (code 2)".to_owned(),
        3 => "This social account is not linked to an Anixart profile yet — \
              sign up with login + e-mail in the official client, then sign in \
              here (code 3)"
            .to_owned(),
        4 => "Invalid login (code 4)".to_owned(),
        5 => "Invalid e-mail (code 5)".to_owned(),
        6 => "Login already taken (code 6)".to_owned(),
        7 => "E-mail already taken (code 7)".to_owned(),
        402 => "This account is banned (code 402)".to_owned(),
        403 => "This account is permanently banned (code 403)".to_owned(),
        other => format!("Anixart returned code {other}"),
    }
}

/// `POST /auth/telegram` with a ready-made Login-Widget `id_token`.
pub async fn sign_in_telegram_id_token(id_token: String) -> Result<String, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    let response = client
        .sign_in_telegram(&id_token)
        .await
        .map_err(|e| e.to_string())?;
    token_of(&response)
}

/// Telegram redirect → `code` → `POST https://oauth.telegram.org/token` →
/// `POST /auth/telegram`. `code_verifier` must belong to the challenge that was
/// sent to the browser.
pub async fn sign_in_telegram_redirect(
    redirect_url: String,
    code_verifier: String,
) -> Result<String, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    let response = client
        .sign_in_telegram_redirect(&redirect_url, &code_verifier)
        .await
        .map_err(|e| e.to_string())?;
    token_of(&response)
}

/// `POST /auth/vk` with a VK ID access token (scope `email`).
pub async fn sign_in_vk(access_token: String) -> Result<String, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    let response = client
        .sign_in_vk(&access_token)
        .await
        .map_err(|e| e.to_string())?;
    token_of(&response)
}

/// `POST /auth/google` with a **Firebase** ID token.
pub async fn sign_in_google(id_token: String) -> Result<String, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    let response = client
        .sign_in_google(&id_token)
        .await
        .map_err(|e| e.to_string())?;
    token_of(&response)
}

/// `GET /article/all/{page}` — feed page.
pub async fn feed(token: Option<String>, page: u32) -> Result<Pageable<FeedItem>, String> {
    let client = api_client(token)?;
    client.feed(page).await.map_err(|e| e.to_string())
}

/// `POST /search/releases/{page}` — release search.
pub async fn search(
    token: Option<String>,
    query: String,
    page: u32,
) -> Result<ReleaseSearchResponse, String> {
    let client = api_client(token)?;
    client.search(&query, page).await.map_err(|e| e.to_string())
}

/// `GET /release/{id}` — extended release page.
///
/// The release body is several hundred bytes wide, so it travels behind a box to
/// keep every [`Message`](crate::app::Message) variant the same size.
pub async fn release(token: Option<String>, id: i64) -> Result<Box<Release>, String> {
    let client = api_client(token)?;
    let response = client.release(id, true).await.map_err(|e| e.to_string())?;
    response
        .release
        .map(Box::new)
        .ok_or_else(|| format!("Release {id} not found"))
}

/// `GET /episode/{id}` — voiceovers / translations.
pub async fn types(token: Option<String>, release_id: i64) -> Result<Vec<EpisodeType>, String> {
    let client = api_client(token)?;
    client.types(release_id).await.map(|r| r.types).map_err(|e| e.to_string())
}

/// `GET /episode/{releaseId}/{typeId}` — video sources of a voiceover.
pub async fn sources(
    token: Option<String>,
    release_id: i64,
    type_id: i64,
) -> Result<Vec<Source>, String> {
    let client = api_client(token)?;
    client
        .sources(release_id, type_id)
        .await
        .map(|r| r.sources)
        .map_err(|e| e.to_string())
}

/// `GET /episode/{releaseId}/{typeId}/{sourceId}` — episodes of a source.
pub async fn episodes(
    token: Option<String>,
    release_id: i64,
    type_id: i64,
    source_id: i64,
) -> Result<Vec<Episode>, String> {
    let client = api_client(token)?;
    client
        .episodes(release_id, type_id, source_id, 0)
        .await
        .map(|r| r.episodes)
        .map_err(|e| e.to_string())
}

/// Resolves an embed / host URL (Kodik, Sibnet, VK, RuTube, Anilibria, direct)
/// into playable stream variants via `anixart-parsers`.
pub async fn resolve(url: String) -> Result<ParsedVideo, String> {
    let http = reqwest::Client::builder()
        .timeout(Duration::from_secs(20))
        .build()
        .map_err(|e| e.to_string())?;

    anixart_parsers::resolve_with_client(&http, &url)
        .await
        .map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use anixart_core::ProfileToken;

    fn response(code: i32, token: &str) -> SocialSignInResponse {
        SocialSignInResponse {
            code,
            profile_token: (!token.is_empty()).then(|| ProfileToken {
                id: 1,
                token: token.to_owned(),
            }),
            ..SocialSignInResponse::default()
        }
    }

    #[test]
    fn a_token_in_the_response_becomes_the_session_token() {
        assert_eq!(token_of(&response(0, "tok-1")).as_deref(), Ok("tok-1"));
    }

    #[test]
    fn an_empty_token_is_not_a_success() {
        assert_eq!(token_of(&response(0, "")).as_deref(), Err("No token in sign-in response"));
    }

    #[test]
    fn every_documented_code_gets_a_message() {
        for code in [1, 2, 3, 4, 5, 6, 7, 8, 402, 403, 999] {
            let message = describe_code(code);
            assert!(message.contains(&code.to_string()), "{code}: {message}");
        }
        assert!(describe_code(3).contains("not linked to an Anixart profile"));
        assert!(describe_code(402).contains("banned"));
    }

    #[test]
    fn not_registered_explains_the_next_step() {
        let error = token_of(&response(3, "")).expect_err("code 3");
        assert!(error.starts_with("This social account is not linked"));
    }
}
