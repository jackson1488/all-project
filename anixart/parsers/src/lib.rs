//! Video source parsers for Anixart (direct mp4/m3u8 links and embed hosts).

pub mod anilibria;
pub mod error;
pub mod kodik;
pub mod models;
pub mod rutube;
pub mod sibnet;
pub mod vk;

pub use error::ParserError;
pub use models::{ParsedVideo, VideoSource};

use std::time::Duration;

/// Returns `true` when `url` points straight at a supported video stream.
pub fn is_direct_stream(url: &str) -> bool {
    let path = url
        .split('?')
        .next()
        .unwrap_or(url)
        .to_ascii_lowercase();

    path.ends_with(".mp4") || path.ends_with(".m3u8")
}

/// Identifies the video source/provider corresponding to the given URL.
pub fn detect_source(url: &str) -> VideoSource {
    if is_direct_stream(url) {
        return VideoSource::Direct;
    }

    let url_lower = url.to_ascii_lowercase();

    if let Some(host) = extract_host(&url_lower) {
        // Kodik hosts
        if host == "kodik.cc"
            || host == "kodik.info"
            || host == "aniqit.com"
            || host == "kodikplayer.com"
            || host == "kodik.biz"
            || host == "kodikres.com"
            || host.ends_with(".kodik.biz")
            || host.ends_with(".kodik.info")
            || host.ends_with(".kodik.cc")
        {
            return VideoSource::Kodik;
        }

        // Sibnet
        if host == "video.sibnet.ru" {
            return VideoSource::Sibnet;
        }

        // RuTube
        if host == "rutube.ru" || host.ends_with(".rutube.ru") {
            return VideoSource::Rutube;
        }

        // VK Video
        if host == "vkvideo.ru"
            || host == "vk.com"
            || host == "m.vk.com"
            || host.ends_with(".vkvideo.ru")
            || host.ends_with(".vk.com")
            || url_lower.contains("video_ext.php")
        {
            return VideoSource::Vk;
        }

        // AniLibria
        if host == "anilibria.tv"
            || host == "new.anilib.one"
            || host == "anixart.libria.fun"
            || host == "anilibria.top"
            || host.ends_with(".anilibria.tv")
            || host.ends_with(".anilibria.top")
        {
            return VideoSource::Anilibria;
        }
    }

    // Additional checks for URLs without standard scheme
    if url_lower.contains("video_ext.php") {
        return VideoSource::Vk;
    }

    VideoSource::Unknown
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
        Some(host.to_string())
    }
}

/// Resolves an embed or stream URL using an explicit HTTP client.
pub async fn resolve_with_client(
    client: &reqwest::Client,
    url: &str,
) -> Result<ParsedVideo, ParserError> {
    match detect_source(url) {
        VideoSource::Direct => {
            let mut parsed = ParsedVideo::new(VideoSource::Direct);
            parsed.qualities.insert("Default".into(), url.to_string());
            Ok(parsed)
        }
        VideoSource::Kodik => kodik::resolve(client, url).await,
        VideoSource::Sibnet => sibnet::resolve(client, url).await,
        VideoSource::Vk => vk::resolve(client, url).await,
        VideoSource::Rutube => rutube::resolve(client, url).await,
        VideoSource::Anilibria => anilibria::resolve(client, url).await,
        VideoSource::Unknown => Err(ParserError::UnsupportedUrl(format!(
            "No parser available for URL: {url}"
        ))),
    }
}

/// Resolves an embed or stream URL into playable stream variants using a default client.
pub async fn resolve(url: &str) -> Result<ParsedVideo, ParserError> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(20))
        .build()?;
    resolve_with_client(&client, url).await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_recognises_direct_streams() {
        assert!(is_direct_stream("https://example.com/video.mp4?token=1"));
        assert!(is_direct_stream("https://example.com/master.m3u8"));
        assert!(!is_direct_stream("https://kodik.cc/serial/123/720p"));
    }

    #[test]
    fn test_detect_source() {
        assert_eq!(
            detect_source("https://example.com/video.mp4"),
            VideoSource::Direct
        );
        assert_eq!(
            detect_source("https://kodik.info/serial/123/456?d=example.com"),
            VideoSource::Kodik
        );
        assert_eq!(
            detect_source("https://video.sibnet.ru/shell.php?videoid=123"),
            VideoSource::Sibnet
        );
        assert_eq!(
            detect_source("https://rutube.ru/video/1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d/"),
            VideoSource::Rutube
        );
        assert_eq!(
            detect_source("https://vk.com/video_ext.php?oid=1&id=2"),
            VideoSource::Vk
        );
        assert_eq!(
            detect_source("https://anilibria.top/player/?id=1&ep=1"),
            VideoSource::Anilibria
        );
        assert_eq!(
            detect_source("https://unknown-host.com/player"),
            VideoSource::Unknown
        );
    }

    #[tokio::test]
    async fn test_resolve_direct_stream() {
        let client = reqwest::Client::new();
        let direct_url = "https://cdn.example.com/stream.m3u8";
        let res = resolve_with_client(&client, direct_url).await.unwrap();
        assert_eq!(res.source, VideoSource::Direct);
        assert_eq!(res.qualities.get("Default").unwrap(), direct_url);
        assert_eq!(res.best_url().unwrap(), direct_url);
    }

    #[test]
    fn test_parsed_video_best_url_priority() {
        let mut video = ParsedVideo::new(VideoSource::Rutube);
        video.qualities.insert("480p".into(), "url_480".into());
        video.qualities.insert("1080p".into(), "url_1080".into());
        video.qualities.insert("720p".into(), "url_720".into());
        assert_eq!(video.best_url(), Some("url_1080"));
    }
}
