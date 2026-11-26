use anixart_core::models::{
    CheckLoginResponse, CodeResponse, CollectionResponse, EpisodeResponse, FeedItem, Pageable,
    ProfileInfoResponse, ProfileResponse, Release, ReleaseResponse, ReleaseSearchResponse,
    SearchRequest, SignInResponse, SourcesResponse, TypesResponse,
};

fn parse<T: serde::de::DeserializeOwned>(raw: &str) -> T {
    serde_json::from_str(raw).unwrap_or_else(|e| panic!("fixture parse failed: {e}\n{raw}"))
}

#[test]
fn sign_in_fixture() {
    let resp: SignInResponse = parse(include_str!("../fixtures/sign_in.json"));
    assert!(resp.is_success());
    assert_eq!(resp.token(), Some("tok_abc123"));
    let profile = resp.profile.expect("profile");
    assert_eq!(profile.id, 42);
    assert_eq!(profile.login, "neko");
}

#[test]
fn sign_in_accepts_camel_case_token_key() {
    let raw = r#"{"code":0,"profileToken":{"id":1,"token":"x"}}"#;
    let resp: SignInResponse = parse(raw);
    assert_eq!(resp.token(), Some("x"));
}

#[test]
fn release_fixture() {
    let resp: ReleaseResponse = parse(include_str!("../fixtures/release.json"));
    assert!(resp.is_success());
    let release = resp.release.expect("release");
    assert_eq!(release.id, 1001);
    assert_eq!(release.title_original.as_deref(), Some("Kimetsu no Yaiba"));
    assert!(release.is_favorite);
    assert_eq!(release.episodes_total, Some(26));
}

#[test]
fn search_fixture() {
    let resp: ReleaseSearchResponse = parse(include_str!("../fixtures/search.json"));
    assert!(resp.is_success());
    assert_eq!(resp.releases.len(), 2);
    assert_eq!(resp.related.as_ref().map(|r| r.id), Some(55));
}

#[test]
fn search_request_serializes_search_by_camel_case() {
    let body = SearchRequest::new("naruto");
    let json = serde_json::to_value(&body).unwrap();
    assert_eq!(json["query"], "naruto");
    assert_eq!(json["searchBy"], 0);
    assert!(json.get("search_by").is_none());
}

#[test]
fn types_sources_episodes_fixtures() {
    let types: TypesResponse = parse(include_str!("../fixtures/types.json"));
    assert!(types.is_success());
    assert_eq!(types.types[0].name, "AniLibria");
    assert!(types.types[0].pinned);
    assert!(types.types[1].is_sub);

    let sources: SourcesResponse = parse(include_str!("../fixtures/sources.json"));
    assert_eq!(sources.sources.len(), 2);
    assert_eq!(sources.sources[0].name, "Kodik");

    let episodes: EpisodeResponse = parse(include_str!("../fixtures/episodes.json"));
    assert_eq!(episodes.episodes.len(), 2);
    assert_eq!(episodes.episodes[0].release_id, 1001);
    assert_eq!(episodes.episodes[0].source_id, 201);
    assert!(episodes.episodes[0].is_watched);
    // second item uses camelCase releaseId/sourceId aliases
    assert_eq!(episodes.episodes[1].release_id, 1001);
    assert_eq!(episodes.episodes[1].source_id, 201);
}

#[test]
fn feed_pageable_fixture() {
    let page: Pageable<FeedItem> = parse(include_str!("../fixtures/feed.json"));
    assert!(page.is_success());
    assert_eq!(page.current_page, 0);
    assert_eq!(page.total_count, 1);
    assert_eq!(page.content[0].id, 501);
    assert_eq!(
        page.content[0].channel.as_ref().and_then(|c| c.title.clone()),
        Some("Anixart News".into())
    );
}

#[test]
fn favorites_history_pageable_fixture() {
    let page: Pageable<Release> = parse(include_str!("../fixtures/pageable_releases.json"));
    assert!(page.is_success());
    assert_eq!(page.content.len(), 2);
    assert!(page.content.iter().all(|r| r.is_favorite));
}

#[test]
fn profile_fixtures() {
    let profile: ProfileResponse = parse(include_str!("../fixtures/profile.json"));
    assert!(profile.is_success());
    assert!(profile.is_my_profile);
    assert_eq!(profile.profile.as_ref().map(|p| p.login.as_str()), Some("neko"));

    let info: ProfileInfoResponse = parse(include_str!("../fixtures/profile_info.json"));
    assert!(info.is_success());
    assert_eq!(info.channel_id, Some(9));
    assert!(info.is_sponsor);
    assert!(!info.is_perm_banned);
}

#[test]
fn collection_fixture() {
    let resp: CollectionResponse = parse(include_str!("../fixtures/collection.json"));
    assert!(resp.is_success());
    let col = resp.collection.expect("collection");
    assert_eq!(col.id, 77);
    assert_eq!(col.favorites_count, 88);
    assert_eq!(col.releases.len(), 1);
    assert_eq!(col.creator.as_ref().map(|c| c.id), Some(42));
}

#[test]
fn code_response_and_unknown_fields_ignored() {
    let ok: CodeResponse = parse(include_str!("../fixtures/code_ok.json"));
    assert!(ok.is_success());

    let raw = r#"{"code":1,"mystery":true,"extra":[1,2,3]}"#;
    let fail: CodeResponse = parse(raw);
    assert!(!fail.is_success());
    assert_eq!(fail.code, 1);
}

#[test]
fn check_login_fixture_shape() {
    let raw = r#"{"code":0,"available":true,"suggested_logins":["neko1","neko2"]}"#;
    let resp: CheckLoginResponse = parse(raw);
    assert!(resp.available);
    assert_eq!(resp.suggested_logins.len(), 2);
}

#[test]
fn missing_optional_fields_default() {
    let release: Release = parse(r#"{"id":5}"#);
    assert_eq!(release.id, 5);
    assert!(release.title_ru.is_none());
    assert!(!release.is_favorite);
    assert_eq!(release.grade, 0.0);
}
