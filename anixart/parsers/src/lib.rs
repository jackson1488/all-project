//! Video source parsers (direct mp4/m3u8 links and embed hosts).

/// Returns `true` when `url` points straight at a supported video stream.
pub fn is_direct_stream(url: &str) -> bool {
    let path = url
        .split('?')
        .next()
        .unwrap_or(url)
        .to_ascii_lowercase();

    path.ends_with(".mp4") || path.ends_with(".m3u8")
}

#[cfg(test)]
mod tests {
    use super::is_direct_stream;

    #[test]
    fn recognises_direct_streams() {
        assert!(is_direct_stream("https://example.com/video.mp4?token=1"));
        assert!(is_direct_stream("https://example.com/master.m3u8"));
        assert!(!is_direct_stream("https://kodik.cc/serial/123/720p"));
    }
}
