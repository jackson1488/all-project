use crate::error::ParserError;
use crate::models::{ParsedVideo, VideoSource};
use regex::Regex;
use serde::Deserialize;
use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::SystemTime;

const VK_CLIENT_ID: &str = "51552953";
const VK_CLIENT_SECRET: &str = "qgr0yWwXCrsxA1jnRtRX";
const VK_USER_AGENT: &str =
    "com.vk.vkvideo.prod/822 (iPhone, iOS 16.7.7, iPhone10,4, Scale/2.0) SAK/1.119";

static DEVICE_COUNTER: AtomicU64 = AtomicU64::new(1);

/// Generates a valid UUID v4 string for VK anonymous session registration.
pub fn generate_device_id() -> String {
    let nanos = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0x123456789ABCDEF0);
    let count = DEVICE_COUNTER.fetch_add(1, Ordering::Relaxed);

    let p1 = (nanos as u32) ^ 0x5A5A5A5A;
    let p2 = ((nanos >> 32) as u16) ^ (count as u16);
    let p3 = 0x4000 | (((nanos >> 48) as u16) & 0x0FFF);
    let p4 = 0x8000 | (((count >> 16) as u16) & 0x3FFF);
    let p5 = (nanos.wrapping_mul(6364136223846793005).wrapping_add(count)) & 0x0000FFFFFFFFFFFF;

    format!("{p1:08X}-{p2:04X}-{p3:04X}-{p4:04X}-{p5:012X}")
}

/// Extracts (oid, id, optional hash) from VK embed or page URLs.
pub fn extract_video_params(url: &str) -> Option<(String, String, Option<String>)> {
    if url.contains("video_ext.php") {
        let re_oid = Regex::new(r#"[?&]oid=(-?\d+)"#).ok()?;
        let re_id = Regex::new(r#"[?&]id=(\d+)"#).ok()?;
        let re_hash = Regex::new(r#"[?&]hash=([a-f0-9]+)"#).ok()?;

        let oid = re_oid.captures(url)?.get(1)?.as_str().to_string();
        let id = re_id.captures(url)?.get(1)?.as_str().to_string();
        let hash = re_hash
            .captures(url)
            .and_then(|c| c.get(1).map(|m| m.as_str().to_string()));

        return Some((oid, id, hash));
    }

    let re_direct = Regex::new(r#"(?:video|clip)(-?\d+)_(\d+)(?:_([a-f0-9]+))?"#).ok()?;
    if let Some(caps) = re_direct.captures(url) {
        let oid = caps.get(1)?.as_str().to_string();
        let id = caps.get(2)?.as_str().to_string();
        let hash = caps.get(3).map(|m| m.as_str().to_string());
        return Some((oid, id, hash));
    }

    if let Ok(re_z) = Regex::new(r#"[?&]z=([^&]+)"#) {
        if let Some(caps) = re_z.captures(url) {
            if let Some(matched_z) = caps.get(1) {
                let decoded = urlencoding_decode(matched_z.as_str());
                if let Some(inner_caps) = re_direct.captures(&decoded) {
                    let oid = inner_caps.get(1)?.as_str().to_string();
                    let id = inner_caps.get(2)?.as_str().to_string();
                    let hash = inner_caps.get(3).map(|m| m.as_str().to_string());
                    return Some((oid, id, hash));
                }
            }
        }
    }

    None
}

fn urlencoding_encode(s: &str) -> String {
    let mut encoded = String::new();
    for b in s.bytes() {
        match b {
            b'a'..=b'z' | b'A'..=b'Z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                encoded.push(b as char);
            }
            _ => {
                encoded.push_str(&format!("%{b:02X}"));
            }
        }
    }
    encoded
}

fn urlencoding_decode(s: &str) -> String {
    let mut result = String::with_capacity(s.len());
    let mut chars = s.chars();
    while let Some(ch) = chars.next() {
        if ch == '%' {
            let h1 = chars.next().unwrap_or('0');
            let h2 = chars.next().unwrap_or('0');
            let hex_str = format!("{h1}{h2}");
            if let Ok(byte) = u8::from_str_radix(&hex_str, 16) {
                result.push(byte as char);
            } else {
                result.push('%');
                result.push(h1);
                result.push(h2);
            }
        } else if ch == '+' {
            result.push(' ');
        } else {
            result.push(ch);
        }
    }
    result
}

#[derive(Debug, Deserialize)]
struct AnonymTokenResponse {
    token: Option<String>,
    error: Option<String>,
}

/// Parses the anonymous token JSON response.
pub fn parse_anonym_token(json_str: &str) -> Result<String, ParserError> {
    let parsed: AnonymTokenResponse = serde_json::from_str(json_str)?;
    if let Some(token) = parsed.token {
        if !token.is_empty() {
            return Ok(token);
        }
    }
    let err_msg = parsed
        .error
        .unwrap_or_else(|| "missing token field in response".into());
    Err(ParserError::Provider(format!(
        "VK anonymous token failed: {err_msg}"
    )))
}

#[derive(Debug, Deserialize)]
struct VkApiError {
    error_code: Option<i64>,
    error_msg: Option<String>,
}

#[derive(Debug, Deserialize)]
struct VkRestriction {
    title: Option<String>,
}

#[derive(Debug, Deserialize)]
struct VkVideoItem {
    restriction: Option<VkRestriction>,
    files: Option<HashMap<String, String>>,
}

#[derive(Debug, Deserialize)]
struct VkVideoResponse {
    items: Option<Vec<VkVideoItem>>,
}

#[derive(Debug, Deserialize)]
struct VkVideoGetResponse {
    response: Option<VkVideoResponse>,
    error: Option<VkApiError>,
}

/// Parses VK video.get response and maps file qualities.
pub fn parse_video_get_response(json_str: &str) -> Result<HashMap<String, String>, ParserError> {
    let get_resp: VkVideoGetResponse = serde_json::from_str(json_str)?;

    if let Some(err) = get_resp.error {
        let code = err.error_code.unwrap_or(0);
        let msg = err.error_msg.unwrap_or_else(|| "unknown error".into());
        return Err(ParserError::Provider(format!(
            "VK API error {code}: {msg}"
        )));
    }

    let items = get_resp
        .response
        .and_then(|r| r.items)
        .ok_or_else(|| ParserError::ExtractFailed("VK: missing items in response".into()))?;

    let first_item = items.into_iter().next().ok_or_else(|| {
        ParserError::Unavailable("VK: video response items list is empty".into())
    })?;

    if let Some(restr) = first_item.restriction {
        let title = restr.title.unwrap_or_else(|| "Restricted content".into());
        return Err(ParserError::Unavailable(format!("VK: {title}")));
    }

    let files = first_item
        .files
        .ok_or_else(|| ParserError::NoQualitiesFound("VK: no files map in video item".into()))?;

    let mut qualities = HashMap::new();
    let resolutions = [
        ("mp4_2160", "2160p"),
        ("mp4_1440", "1440p"),
        ("mp4_1080", "1080p"),
        ("mp4_720", "720p"),
        ("mp4_480", "480p"),
        ("mp4_360", "360p"),
        ("mp4_240", "240p"),
        ("mp4_144", "144p"),
    ];

    for (file_key, label) in resolutions {
        if let Some(url) = files.get(file_key) {
            if !url.is_empty() {
                qualities.insert(label.to_string(), url.clone());
            }
        }
    }

    if let Some(hls_url) = files.get("hls") {
        if !hls_url.is_empty() {
            qualities.entry("Default".to_string()).or_insert_with(|| hls_url.clone());
        }
    }

    if qualities.is_empty() {
        Err(ParserError::NoQualitiesFound(
            "VK: no supported stream qualities found in files".into(),
        ))
    } else {
        Ok(qualities)
    }
}

/// Resolves a VK video URL into playable streams.
pub async fn resolve(client: &reqwest::Client, url: &str) -> Result<ParsedVideo, ParserError> {
    let (oid, id, hash) = extract_video_params(url).ok_or_else(|| {
        ParserError::ExtractFailed(format!("VK: could not extract video parameters from {url}"))
    })?;

    let device_id = generate_device_id();

    // 1. Obtain anonymous token
    let token_url = format!(
        "https://oauth.vk.com/oauth/get_anonym_token?client_id={VK_CLIENT_ID}&client_secret={VK_CLIENT_SECRET}&device_id={device_id}"
    );

    let token_resp = client
        .get(&token_url)
        .header("User-Agent", VK_USER_AGENT)
        .send()
        .await?
        .text()
        .await?;

    let anonym_token = parse_anonym_token(&token_resp)?;

    // 2. Fetch video details via API
    let video_target = match hash {
        Some(h) => format!("{oid}_{id}_{h}"),
        None => format!("{oid}_{id}"),
    };

    let form_body = format!(
        "anonymous_token={}&device_id={}&lang=en&v=5.244&videos={}",
        urlencoding_encode(anonym_token.as_str()),
        urlencoding_encode(device_id.as_str()),
        urlencoding_encode(video_target.as_str())
    );

    let get_resp = client
        .post("https://api.vk.com/method/video.get")
        .header("User-Agent", VK_USER_AGENT)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .body(form_body)
        .send()
        .await?
        .text()
        .await?;

    let qualities = parse_video_get_response(&get_resp)?;

    let mut parsed = ParsedVideo::new(VideoSource::Vk);
    parsed.qualities = qualities;
    parsed.headers.insert("Referer".into(), "https://vk.com/".into());
    Ok(parsed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_video_params_ext() {
        let u = "https://vk.com/video_ext.php?oid=-123456&id=7891011&hash=abcdef123456";
        let res = extract_video_params(u).unwrap();
        assert_eq!(res.0, "-123456");
        assert_eq!(res.1, "7891011");
        assert_eq!(res.2, Some("abcdef123456".into()));
    }

    #[test]
    fn test_extract_video_params_direct() {
        let u = "https://vkvideo.ru/video-98765_43210_deadbeef";
        let res = extract_video_params(u).unwrap();
        assert_eq!(res.0, "-98765");
        assert_eq!(res.1, "43210");
        assert_eq!(res.2, Some("deadbeef".into()));
    }

    #[test]
    fn test_parse_anonym_token() {
        let fixture = r#"{
            "token": "anonym_token_xyz_123",
            "expired_at": 1700000000
        }"#;
        let token = parse_anonym_token(fixture).unwrap();
        assert_eq!(token, "anonym_token_xyz_123");
    }

    #[test]
    fn test_parse_video_get_response() {
        let fixture = r#"{
            "response": {
                "count": 1,
                "items": [{
                    "id": 12345,
                    "owner_id": -67890,
                    "files": {
                        "mp4_720": "https://vkvd.net/720.mp4",
                        "mp4_1080": "https://vkvd.net/1080.mp4",
                        "hls": "https://vkvd.net/master.m3u8"
                    }
                }]
            }
        }"#;
        let q = parse_video_get_response(fixture).unwrap();
        assert_eq!(q.get("720p").unwrap(), "https://vkvd.net/720.mp4");
        assert_eq!(q.get("1080p").unwrap(), "https://vkvd.net/1080.mp4");
        assert_eq!(q.get("Default").unwrap(), "https://vkvd.net/master.m3u8");
    }

    #[test]
    fn test_parse_video_get_restricted() {
        let fixture = r#"{
            "response": {
                "count": 1,
                "items": [{
                    "id": 12345,
                    "restriction": {
                        "title": "Video blocked by copyright holder"
                    }
                }]
            }
        }"#;
        assert!(matches!(
            parse_video_get_response(fixture),
            Err(ParserError::Unavailable(_))
        ));
    }
}
