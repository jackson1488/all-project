use crate::error::ParserError;
use crate::models::{ParsedVideo, VideoSource};
use regex::Regex;
use serde::Deserialize;
use std::collections::HashMap;

#[derive(Debug, Deserialize)]
struct Detail {
    #[serde(rename = "type")]
    detail_type: Option<String>,
}

#[derive(Debug, Deserialize)]
struct LiveStreams {
    hls: Option<String>,
}

#[derive(Debug, Deserialize)]
struct VideoBalancer {
    m3u8: Option<String>,
}

#[derive(Debug, Deserialize)]
struct PlayOptionsResponse {
    detail: Option<Detail>,
    live_streams: Option<LiveStreams>,
    video_balancer: Option<VideoBalancer>,
}

/// Extracts RuTube video ID and optional private token `p` from a URL.
pub fn extract_video_id(url: &str) -> Option<(String, Option<String>)> {
    // 1. Private video: rutube.ru/video/private/<id>/?p=<p>
    let re_private = Regex::new(r#"rutube\.ru/video/private/([a-f0-9]{32})(?:\?p=([^&]+))?"#).ok()?;
    if let Some(caps) = re_private.captures(url) {
        let id = caps.get(1)?.as_str().to_string();
        let p = caps.get(2).map(|m| m.as_str().to_string());
        return Some((id, p));
    }

    // 2. Public / embed / shorts formats
    let patterns = [
        r#"rutube\.ru/video/([a-f0-9]{32})"#,
        r#"rutube\.ru/play/embed/([a-f0-9]{32})"#,
        r#"rutube\.ru/shorts/([a-f0-9]{32})"#,
    ];
    for pat in patterns {
        if let Ok(re) = Regex::new(pat) {
            if let Some(caps) = re.captures(url) {
                if let Some(id) = caps.get(1) {
                    return Some((id.as_str().to_string(), None));
                }
            }
        }
    }
    None
}

/// Validates play options JSON and returns the master M3U8 URL.
pub fn parse_play_options(json_str: &str) -> Result<String, ParserError> {
    let play_options: PlayOptionsResponse = serde_json::from_str(json_str)?;

    if let Some(detail) = &play_options.detail {
        if detail.detail_type.as_deref() == Some("blocking_rule") {
            return Err(ParserError::Unavailable(
                "RuTube: video is region-locked".into(),
            ));
        }
    }

    if let Some(live) = &play_options.live_streams {
        if let Some(hls) = &live.hls {
            if !hls.is_empty() {
                return Err(ParserError::Unavailable(
                    "RuTube: live streams are not supported".into(),
                ));
            }
        }
    }

    if let Some(balancer) = play_options.video_balancer {
        if let Some(m3u8) = balancer.m3u8 {
            if !m3u8.is_empty() {
                return Ok(m3u8);
            }
        }
    }

    Err(ParserError::ExtractFailed(
        "RuTube: M3U8 balancer URL not found".into(),
    ))
}

#[derive(Debug, Clone)]
struct StreamVariant {
    width: u32,
    height: u32,
    bandwidth: u64,
    url: String,
}

/// Parses master M3U8 content and maps stream variants to quality labels.
pub fn parse_m3u8_variants(
    base_m3u8_url: &str,
    m3u8_body: &str,
) -> Result<HashMap<String, String>, ParserError> {
    let re_res = Regex::new(r#"RESOLUTION=(\d+)x(\d+)"#)?;
    let re_bw = Regex::new(r#"BANDWIDTH=(\d+)"#)?;

    let lines: Vec<&str> = m3u8_body.lines().map(|l| l.trim()).collect();
    let mut variants = Vec::new();

    for (i, &line) in lines.iter().enumerate() {
        if line.starts_with("#EXT-X-STREAM-INF:") && i + 1 < lines.len() {
            let next_line = lines[i + 1];
            if !next_line.is_empty() && !next_line.starts_with('#') {
                let (width, height) = if let Some(caps) = re_res.captures(line) {
                    let w: u32 = caps.get(1).and_then(|m| m.as_str().parse().ok()).unwrap_or(0);
                    let h: u32 = caps.get(2).and_then(|m| m.as_str().parse().ok()).unwrap_or(0);
                    (w, h)
                } else {
                    (0, 0)
                };

                let bandwidth: u64 = if let Some(caps) = re_bw.captures(line) {
                    caps.get(1).and_then(|m| m.as_str().parse().ok()).unwrap_or(0)
                } else {
                    0
                };

                let full_url = if next_line.starts_with("http://") || next_line.starts_with("https://") {
                    next_line.to_string()
                } else if next_line.starts_with('/') {
                    let host_prefix = base_m3u8_url
                        .split('/')
                        .take(3)
                        .collect::<Vec<_>>()
                        .join("/");
                    format!("{host_prefix}{next_line}")
                } else {
                    let last_slash = base_m3u8_url.rfind('/').unwrap_or(0);
                    format!("{}/{}", &base_m3u8_url[..last_slash], next_line)
                };

                variants.push(StreamVariant {
                    width,
                    height,
                    bandwidth,
                    url: full_url,
                });
            }
        }
    }

    // Sort by bandwidth descending
    variants.sort_by(|a, b| b.bandwidth.cmp(&a.bandwidth));

    let mut qualities = HashMap::new();
    for v in variants {
        let max_dim = std::cmp::max(v.width, v.height);
        let label = if max_dim >= 3840 {
            "2160p"
        } else if max_dim >= 2560 {
            "1440p"
        } else if max_dim >= 1920 {
            "1080p"
        } else if max_dim >= 1280 {
            "720p"
        } else if max_dim >= 854 {
            "480p"
        } else if max_dim >= 640 {
            "360p"
        } else {
            "Default"
        };
        qualities.entry(label.to_string()).or_insert(v.url);
    }

    if qualities.is_empty() {
        Err(ParserError::NoQualitiesFound(
            "RuTube: no playlist variants found".into(),
        ))
    } else {
        Ok(qualities)
    }
}

/// Resolves a RuTube video URL into playable stream variants.
pub async fn resolve(client: &reqwest::Client, url: &str) -> Result<ParsedVideo, ParserError> {
    let (video_id, private_token) = extract_video_id(url).ok_or_else(|| {
        ParserError::ExtractFailed(format!("RuTube: could not extract video ID from {url}"))
    })?;

    let mut options_url = format!("https://rutube.ru/api/play/options/{video_id}/?no_404=true&referer&pver=v2");
    if let Some(p) = private_token {
        options_url.push_str(&format!("&p={p}"));
    }

    let resp_text = client
        .get(&options_url)
        .header("Referer", "https://rutube.ru/")
        .send()
        .await?
        .text()
        .await?;

    let m3u8_url = parse_play_options(&resp_text)?;

    let m3u8_content = client
        .get(&m3u8_url)
        .header("Referer", "https://rutube.ru/")
        .send()
        .await?
        .text()
        .await?;

    let qualities = parse_m3u8_variants(&m3u8_url, &m3u8_content)?;

    let mut parsed = ParsedVideo::new(VideoSource::Rutube);
    parsed.qualities = qualities;
    parsed.headers.insert("Referer".into(), "https://rutube.ru/".into());
    Ok(parsed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_video_id() {
        let u1 = "https://rutube.ru/video/1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d/";
        assert_eq!(
            extract_video_id(u1),
            Some(("1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d".into(), None))
        );

        let u2 = "https://rutube.ru/video/private/1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d/?p=privatetoken123";
        assert_eq!(
            extract_video_id(u2),
            Some((
                "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d".into(),
                Some("privatetoken123".into())
            ))
        );

        let u3 = "https://rutube.ru/play/embed/1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d";
        assert_eq!(
            extract_video_id(u3),
            Some(("1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d".into(), None))
        );
    }

    #[test]
    fn test_parse_play_options_ok() {
        let fixture = r#"{
            "video_balancer": {
                "m3u8": "https://rutube.ru/video_balancer/123/master.m3u8"
            }
        }"#;
        let url = parse_play_options(fixture).unwrap();
        assert_eq!(url, "https://rutube.ru/video_balancer/123/master.m3u8");
    }

    #[test]
    fn test_parse_play_options_region_locked() {
        let fixture = r#"{
            "detail": {
                "type": "blocking_rule"
            }
        }"#;
        assert!(matches!(
            parse_play_options(fixture),
            Err(ParserError::Unavailable(_))
        ));
    }

    #[test]
    fn test_parse_m3u8_variants() {
        let m3u8 = "#EXTM3U\n\
            #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720\n\
            720p.m3u8\n\
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080\n\
            1080p.m3u8\n";
        let base = "https://rutube.ru/balancer/index.m3u8";
        let res = parse_m3u8_variants(base, m3u8).unwrap();
        assert_eq!(res.get("720p").unwrap(), "https://rutube.ru/balancer/720p.m3u8");
        assert_eq!(res.get("1080p").unwrap(), "https://rutube.ru/balancer/1080p.m3u8");
    }
}
