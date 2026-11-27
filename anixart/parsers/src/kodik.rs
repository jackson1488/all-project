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

fn decode_base64(input: &str) -> Result<String, ParserError> {
    const TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut lut = [255u8; 256];
    for (i, &b) in TABLE.iter().enumerate() {
        lut[b as usize] = i as u8;
    }

    let mut out = Vec::with_capacity(input.len() * 3 / 4);
    let mut buf: u32 = 0;
    let mut bits: u8 = 0;
    for b in input.bytes() {
        if b == b'=' || b.is_ascii_whitespace() {
            continue;
        }
        let val = lut[b as usize];
        if val == 255 {
            return Err(ParserError::NoQualitiesFound(
                "Kodik: invalid base64 character in stream link".into(),
            ));
        }
        buf = (buf << 6) | u32::from(val);
        bits += 6;
        if bits >= 8 {
            bits -= 8;
            out.push(((buf >> bits) & 0xFF) as u8);
        }
    }
    String::from_utf8(out)
        .map_err(|_| ParserError::NoQualitiesFound("Kodik: non-UTF8 decoded stream link".into()))
}

/// Decodes Kodik's ROT-18 + Base64 obfuscated stream URL (passes through plain `//` / `http` URLs).
pub fn decode_kodik_src(raw: &str) -> Result<String, ParserError> {
    if raw.contains("//") {
        return Ok(normalize_stream_url(raw));
    }
    let rotated: String = raw
        .chars()
        .map(|c| {
            if c.is_ascii_uppercase() {
                let code = c as u8 + 18;
                (if code <= b'Z' { code } else { code - 26 }) as char
            } else if c.is_ascii_lowercase() {
                let code = c as u8 + 18;
                (if code <= b'z' { code } else { code - 26 }) as char
            } else {
                c
            }
        })
        .collect();
    let decoded = decode_base64(&rotated)?;
    Ok(normalize_stream_url(&decoded))
}

fn extract_ftor_form(html: &str) -> Option<Vec<(String, String)>> {
    let url_params_re = Regex::new(r#"urlParams\s*=\s*'([^']+)'"#).ok()?;
    let url_params_raw = url_params_re.captures(html)?.get(1)?.as_str();
    let url_params: serde_json::Value = serde_json::from_str(url_params_raw).ok()?;

    let vtype_re = Regex::new(r#"vInfo\.type\s*=\s*'([^']+)'"#).ok()?;
    let vhash_re = Regex::new(r#"vInfo\.hash\s*=\s*'([^']+)'"#).ok()?;
    let vid_re = Regex::new(r#"vInfo\.id\s*=\s*'([^']+)'"#).ok()?;

    let vtype = vtype_re.captures(html)?.get(1)?.as_str().to_owned();
    let vhash = vhash_re.captures(html)?.get(1)?.as_str().to_owned();
    let vid = vid_re.captures(html)?.get(1)?.as_str().to_owned();

    let str_field = |k: &str| {
        url_params
            .get(k)
            .and_then(|v| v.as_str())
            .unwrap_or_default()
            .to_owned()
    };

    Some(vec![
        ("d".to_owned(), str_field("d")),
        ("d_sign".to_owned(), str_field("d_sign")),
        ("pd".to_owned(), str_field("pd")),
        ("pd_sign".to_owned(), str_field("pd_sign")),
        ("ref".to_owned(), str_field("ref")),
        ("ref_sign".to_owned(), str_field("ref_sign")),
        ("type".to_owned(), vtype),
        ("hash".to_owned(), vhash),
        ("id".to_owned(), vid),
    ])
}

async fn resolve_via_ftor(
    client: &reqwest::Client,
    embed_url: &str,
) -> Result<ParsedVideo, ParserError> {
    const UA: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    let normalized_embed = if embed_url.starts_with("//") {
        format!("https:{embed_url}")
    } else {
        embed_url.to_owned()
    };

    let html = client
        .get(&normalized_embed)
        .header("User-Agent", UA)
        .header("Referer", &normalized_embed)
        .send()
        .await?
        .text()
        .await?;

    let form = extract_ftor_form(&html).ok_or_else(|| {
        ParserError::NoQualitiesFound("Kodik: urlParams/vInfo not found in embed page".into())
    })?;

    let host = extract_host(&normalized_embed).unwrap_or_else(|| "kodikplayer.com".to_owned());
    let ftor_url = format!("https://{host}/ftor");

    let body = client
        .post(&ftor_url)
        .header("User-Agent", UA)
        .header("Referer", &normalized_embed)
        .header("X-Requested-With", "XMLHttpRequest")
        .form(&form)
        .send()
        .await?
        .text()
        .await?;

    let raw_qualities = parse_video_links_json(&body)?;
    let mut qualities = HashMap::new();
    for (k, v) in raw_qualities {
        if let Ok(decoded) = decode_kodik_src(&v) {
            qualities.insert(k, decoded);
        }
    }
    if qualities.is_empty() {
        return Err(ParserError::NoQualitiesFound(
            "Kodik: /ftor returned no decodable links".into(),
        ));
    }

    let mut parsed = ParsedVideo::new(VideoSource::Kodik);
    parsed.qualities = qualities;
    parsed.headers.insert("Referer".into(), normalized_embed);
    Ok(parsed)
}

/// Resolves a Kodik embed URL into playable direct stream URLs.
pub async fn resolve(client: &reqwest::Client, embed_url: &str) -> Result<ParsedVideo, ParserError> {
    if let Ok(parsed) = resolve_via_ftor(client, embed_url).await {
        return Ok(parsed);
    }

    let api_url = build_api_url(embed_url);
    let resp = client
        .get(&api_url)
        .header("Referer", embed_url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        )
        .send()
        .await?
        .text()
        .await?;

    let raw_qualities = parse_video_links_json(&resp)?;
    let mut qualities = HashMap::new();
    for (k, v) in raw_qualities {
        if let Ok(decoded) = decode_kodik_src(&v) {
            qualities.insert(k, decoded);
        }
    }
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

    #[test]
    fn test_decode_kodik_src_rot18_and_plain() {
        assert_eq!(
            decode_kodik_src("//cloud.kodik.info/video/720.m3u8").unwrap(),
            "https://cloud.kodik.info/video/720.m3u8"
        );

        let plain = "//cloud.kodik.info/1080.mp4:hls:hls.m3u8";
        let b64 = "Ly9jbG91ZC5rb2Rpay5pbmZvLzEwODAubXA0OmhsczpobHMubTN1OA==";
        let rot18: String = b64
            .chars()
            .map(|c| {
                if c.is_ascii_uppercase() {
                    let code = c as u8 + 8;
                    (if code <= b'Z' { code } else { code - 26 }) as char
                } else if c.is_ascii_lowercase() {
                    let code = c as u8 + 8;
                    (if code <= b'z' { code } else { code - 26 }) as char
                } else {
                    c
                }
            })
            .collect();
        assert_eq!(
            decode_kodik_src(&rot18).unwrap(),
            plain.replace("//", "https://").replace(":hls:hls.m3u8", ":hls:manifest.m3u8")
        );
    }
}
