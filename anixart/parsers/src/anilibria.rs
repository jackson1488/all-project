use crate::error::ParserError;
use crate::models::{ParsedVideo, VideoSource};
use regex::Regex;
use serde::Deserialize;
use std::collections::HashMap;

/// Extracts AniLibria release ID and episode ordinal from an embed URL.
pub fn extract_release_and_ep(url: &str) -> Option<(String, i32)> {
    let re = Regex::new(r#"id=(\d+)&ep=(\d+)"#).ok()?;
    let caps = re.captures(url)?;
    let release_id = caps.get(1)?.as_str().to_string();
    let ep: i32 = caps.get(2)?.as_str().parse().ok()?;
    Some((release_id, ep))
}

#[derive(Debug, Deserialize)]
struct AniLibriaEpisode {
    ordinal: Option<i32>,
    hls1080: Option<String>,
    hls720: Option<String>,
    hls480: Option<String>,
}

#[derive(Debug, Deserialize)]
struct AniLibriaReleaseResponse {
    episodes: Option<Vec<AniLibriaEpisode>>,
}

/// Parses AniLibria release API response and extracts HLS variants for the requested episode.
pub fn parse_release_episodes(json_str: &str, ep_ordinal: i32) -> HashMap<String, String> {
    let mut qualities = HashMap::new();
    if let Ok(resp) = serde_json::from_str::<AniLibriaReleaseResponse>(json_str) {
        if let Some(episodes) = resp.episodes {
            if let Some(ep) = episodes.into_iter().find(|e| e.ordinal == Some(ep_ordinal)) {
                if let Some(hls1080) = ep.hls1080 {
                    let trimmed = hls1080.trim();
                    if !trimmed.is_empty() {
                        qualities.insert("1080p".into(), trimmed.to_string());
                    }
                }
                if let Some(hls720) = ep.hls720 {
                    let trimmed = hls720.trim();
                    if !trimmed.is_empty() {
                        qualities.insert("720p".into(), trimmed.to_string());
                    }
                }
                if let Some(hls480) = ep.hls480 {
                    let trimmed = hls480.trim();
                    if !trimmed.is_empty() {
                        qualities.insert("480p".into(), trimmed.to_string());
                    }
                }
            }
        }
    }
    qualities
}

/// Scrapes embed HTML as a fallback when the AniLibria releases API is unavailable or missing qualities.
pub fn parse_embed_html(html: &str, ep_ordinal: i32) -> HashMap<String, String> {
    let mut qualities = HashMap::new();
    let re_file_pat = format!("\"s{ep_ordinal}\".*?\"file\":\"([^\"]+)\"");
    if let Ok(re_file) = Regex::new(&re_file_pat) {
        if let Some(caps) = re_file.captures(html) {
            if let Some(file_matched) = caps.get(1) {
                let file_str = file_matched.as_str();
                if let Ok(re_quality) = Regex::new(r#"\[([0-9]*p)]([^,\]]+)"#) {
                    let mut found_any = false;
                    for q_caps in re_quality.captures_iter(file_str) {
                        if let (Some(q_label), Some(q_url)) = (q_caps.get(1), q_caps.get(2)) {
                            let label = q_label.as_str();
                            let clean_url = q_url.as_str().replace(r#"\/"#, "/");
                            qualities.insert(label.to_string(), clean_url);
                            found_any = true;
                        }
                    }
                    if !found_any && !file_str.is_empty() {
                        let clean_url = file_str.replace(r#"\/"#, "/");
                        qualities.insert("Default".into(), clean_url);
                    }
                }
            }
        }
    }
    qualities
}

/// Resolves an AniLibria embed URL into direct playable streams.
pub async fn resolve(client: &reqwest::Client, url: &str) -> Result<ParsedVideo, ParserError> {
    let (release_id, ep) = extract_release_and_ep(url).ok_or_else(|| {
        ParserError::ExtractFailed(format!(
            "AniLibria: could not extract release ID and episode from {url}"
        ))
    })?;

    // 1. Try public releases API
    let api_url = format!("https://anilibria.top/api/v1/anime/releases/{release_id}");
    let api_result = client.get(&api_url).send().await;

    let mut qualities = HashMap::new();
    if let Ok(resp) = api_result {
        if let Ok(body) = resp.text().await {
            qualities = parse_release_episodes(&body, ep);
        }
    }

    // 2. Fall back to scraping the embed HTML
    if qualities.is_empty() {
        let embed_resp = client
            .get(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36",
            )
            .send()
            .await?
            .text()
            .await?;

        qualities = parse_embed_html(&embed_resp, ep);
    }

    if qualities.is_empty() {
        return Err(ParserError::NoQualitiesFound(
            "AniLibria: no playable streams found".into(),
        ));
    }

    let mut parsed = ParsedVideo::new(VideoSource::Anilibria);
    parsed.qualities = qualities;
    parsed.headers.insert("Referer".into(), url.into());
    Ok(parsed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_release_and_ep() {
        let u = "https://anilibria.top/player/?id=9876&ep=3";
        let res = extract_release_and_ep(u).unwrap();
        assert_eq!(res.0, "9876");
        assert_eq!(res.1, 3);
    }

    #[test]
    fn test_parse_release_episodes() {
        let fixture = r#"{
            "episodes": [
                {
                    "ordinal": 1,
                    "hls480": "https://cache.libria.fun/ep1/480.m3u8",
                    "hls720": "https://cache.libria.fun/ep1/720.m3u8",
                    "hls1080": "https://cache.libria.fun/ep1/1080.m3u8"
                },
                {
                    "ordinal": 2,
                    "hls720": "https://cache.libria.fun/ep2/720.m3u8"
                }
            ]
        }"#;
        let q = parse_release_episodes(fixture, 1);
        assert_eq!(q.get("1080p").unwrap(), "https://cache.libria.fun/ep1/1080.m3u8");
        assert_eq!(q.get("720p").unwrap(), "https://cache.libria.fun/ep1/720.m3u8");
        assert_eq!(q.get("480p").unwrap(), "https://cache.libria.fun/ep1/480.m3u8");

        let q2 = parse_release_episodes(fixture, 2);
        assert_eq!(q2.get("720p").unwrap(), "https://cache.libria.fun/ep2/720.m3u8");
        assert!(!q2.contains_key("1080p"));
    }

    #[test]
    fn test_parse_embed_html() {
        let fixture = r#"
            var player = {"s1":{"file":"[480p]https:\/\/cdn.anilib.one\/480.mp4,[720p]https:\/\/cdn.anilib.one\/720.mp4"}};
        "#;
        let q = parse_embed_html(fixture, 1);
        assert_eq!(q.get("480p").unwrap(), "https://cdn.anilib.one/480.mp4");
        assert_eq!(q.get("720p").unwrap(), "https://cdn.anilib.one/720.mp4");
    }
}
