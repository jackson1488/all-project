use crate::error::ParserError;
use crate::models::{ParsedVideo, VideoSource};
use regex::Regex;
use std::collections::HashMap;

const KODIK_API_KEY: &str = "56a768d08f43091901c44b54fe970049";

/// Constructs the Kodik video-links API URL from an embed URL.
pub fn build_api_url(embed_url: &str) -> String {
    if let Some((link_part, domain_part)) = embed_url.split_once("d=") {
        let mut link = link_part;
        if link.ends_with('?') || link.ends_with('&') {
            link = &link[..link.len() - 1];
        }
        let link_cleaned = link.replace("https://", "//").replace("http://", "//");
        let domain = domain_part.split('&').next().unwrap_or(domain_part);
        format!(
            "https://kodikres.com/api/video-links?p={KODIK_API_KEY}&link={link_cleaned}&d={domain}"
        )
    } else {
        let link_cleaned = embed_url.replace("https://", "//").replace("http://", "//");
        let domain = extract_host(embed_url).unwrap_or_else(|| "kodik.info".to_string());
        format!(
            "https://kodikres.com/api/video-links?p={KODIK_API_KEY}&link={link_cleaned}&d={domain}"
        )
    }
}

fn extract_host(url: &str) -> Option<String> {
    let after_scheme = if let Some(idx) = url.find("://") {
        &url[idx + 3..]
    } else {
        url
    };
    let host = after_scheme
        .split('/')
        .next()?
        .split('?')
        .next()?
        .split(':')
        .next()?;
    if host.is_empty() {
        None
    } else {
        Some(host.to_ascii_lowercase())
    }
}

fn normalize_stream_url(url: &str) -> String {
    let with_scheme = if url.starts_with("//") {
        format!("https:{url}")
    } else {
        url.to_string()
    };
    with_scheme.replace(":hls:hls.m3u8", ":hls:manifest.m3u8")
}

/// Parses the JSON response from kodikres.com/api/video-links into a quality -> URL map.
pub fn parse_video_links_json(body: &str) -> Result<HashMap<String, String>, ParserError> {
    let mut qualities = HashMap::new();

    // 1. Try structured JSON parsing if it matches the links schema
    if let Ok(val) = serde_json::from_str::<serde_json::Value>(body) {
        let links_obj = val.get("links").and_then(|l| l.as_object()).or_else(|| val.as_object());
        if let Some(map) = links_obj {
            for (res_key, link_val) in map {
                let src_opt = match link_val {
                    serde_json::Value::Object(o) => o
                        .get("src")
                        .or_else(|| o.get("Src"))
                        .and_then(|s| s.as_str()),
                    serde_json::Value::Array(arr) => arr.first().and_then(|item| {
                        item.get("src")
                            .or_else(|| item.get("Src"))
                            .and_then(|s| s.as_str())
                    }),
                    serde_json::Value::String(s) => Some(s.as_str()),
                    _ => None,
                };
                if let Some(src) = src_opt {
                    let key = if res_key.ends_with('p') {
                        res_key.clone()
                    } else {
                        format!("{res_key}p")
                    };
                    qualities.insert(key, normalize_stream_url(src));
                }
            }
        }
    }

    // 2. If qualities is empty, fall back to regex matching per decompiled KodikParser
    if qualities.is_empty() {
        let resolutions = ["360", "480", "720", "1080"];
        for res in resolutions {
            let pattern = format!(r#""{res}":\s*\{{\s*"[sS]rc":\s*"([^"]+)""#);
            if let Ok(re) = Regex::new(&pattern) {
                if let Some(caps) = re.captures(body) {
                    if let Some(matched) = caps.get(1) {
                        qualities.insert(format!("{res}p"), normalize_stream_url(matched.as_str()));
                    }
                }
            }
        }
    }

    if qualities.is_empty() {
        Err(ParserError::NoQualitiesFound(
            "Kodik: no stream links found in response".into(),
        ))
    } else {
        Ok(qualities)
    }
}

/// Resolves a Kodik embed URL into playable direct stream URLs.
pub async fn resolve(client: &reqwest::Client, embed_url: &str) -> Result<ParsedVideo, ParserError> {
    let api_url = build_api_url(embed_url);
    let resp = client
        .get(&api_url)
        .header("Referer", embed_url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36",
        )
        .send()
        .await?
        .text()
        .await?;

    let qualities = parse_video_links_json(&resp)?;
    let mut parsed = ParsedVideo::new(VideoSource::Kodik);
    parsed.qualities = qualities;
    parsed.headers.insert("Referer".into(), embed_url.into());
    Ok(parsed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_build_api_url_with_d() {
        let embed = "https://kodik.info/serial/123/456?d=example.com";
        let api = build_api_url(embed);
        assert_eq!(
            api,
            "https://kodikres.com/api/video-links?p=56a768d08f43091901c44b54fe970049&link=//kodik.info/serial/123/456&d=example.com"
        );
    }

    #[test]
    fn test_build_api_url_without_d() {
        let embed = "https://kodik.cc/video/12345/720p";
        let api = build_api_url(embed);
        assert_eq!(
            api,
            "https://kodikres.com/api/video-links?p=56a768d08f43091901c44b54fe970049&link=//kodik.cc/video/12345/720p&d=kodik.cc"
        );
    }

    #[test]
    fn test_parse_video_links_json_regex() {
        let fixture = r#"{
            "360": {"src": "//cloud.kodik.info/video/360/index.m3u8:hls:hls.m3u8"},
            "720": {"src": "//cloud.kodik.info/video/720/index.m3u8"}
        }"#;
        let res = parse_video_links_json(fixture).unwrap();
        assert_eq!(
            res.get("360p").unwrap(),
            "https://cloud.kodik.info/video/360/index.m3u8:hls:manifest.m3u8"
        );
        assert_eq!(
            res.get("720p").unwrap(),
            "https://cloud.kodik.info/video/720/index.m3u8"
        );
    }

    #[test]
    fn test_parse_video_links_json_structured() {
        let fixture = r#"{
            "links": {
                "480": [{"src": "https://cloud.kodik.info/video/480.m3u8"}],
                "1080": [{"src": "//cloud.kodik.info/video/1080.m3u8"}]
            }
        }"#;
        let res = parse_video_links_json(fixture).unwrap();
        assert_eq!(
            res.get("480p").unwrap(),
            "https://cloud.kodik.info/video/480.m3u8"
        );
        assert_eq!(
            res.get("1080p").unwrap(),
            "https://cloud.kodik.info/video/1080.m3u8"
        );
    }
}
