//! Async glue between the iced runtime and `anixart-core` / `anixart-parsers`.
//!
//! Every helper builds its client *inside* the spawned future: iced is powered by
//! the `tokio` executor, so all reqwest traffic then runs on a real tokio runtime.

use std::time::Duration;

use anixart_core::{
    AnixartClient, Episode, EpisodeType, FeedItem, Pageable, Release, ReleaseSearchResponse,
    Source,
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
