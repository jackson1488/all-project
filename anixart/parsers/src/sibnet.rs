use crate::error::ParserError;
use crate::models::{ParsedVideo, VideoSource};
use regex::Regex;

/// Extracts the video player script source from Sibnet embed HTML.
pub fn extract_player_src(html: &str) -> Option<String> {
    let re = Regex::new(r#"src:\s*["']([^"']+)["']"#).ok()?;
    let caps = re.captures(html)?;
    let raw = caps.get(1)?.as_str();
    if raw.starts_with('/') {
        Some(format!("https://video.sibnet.ru{raw}"))
    } else {
        Some(raw.to_string())
    }
}

/// Strips standard HTTPS port `:443` from the final streaming URL per Sibnet parser rules.
pub fn clean_stream_url(url: &str) -> String {
    url.replace(":443", "")
}

/// Resolves a Sibnet video URL into a playable direct stream.
pub async fn resolve(client: &reqwest::Client, embed_url: &str) -> Result<ParsedVideo, ParserError> {
    let html = client
        .get(embed_url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36",
        )
        .send()
        .await?
        .text()
        .await?;

    let player_src = extract_player_src(&html).ok_or_else(|| {
        ParserError::ExtractFailed("Sibnet: unable to find player src in embed page".into())
    })?;

    // Follow redirect to obtain direct CDN stream
    let resp = client
        .get(&player_src)
        .header("Referer", embed_url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36",
        )
        .send()
        .await?;

    let final_url = clean_stream_url(resp.url().as_str());

    let mut parsed = ParsedVideo::new(VideoSource::Sibnet);
    parsed.qualities.insert("Default".into(), final_url);
    parsed.headers.insert("Referer".into(), embed_url.into());
    Ok(parsed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_player_src() {
        let html = r#"
            <!DOCTYPE html>
            <html>
            <head><script>
                var player = new Player({
                    src: "/shell.php?videoid=1234567"
                });
            </script></head>
            </html>
        "#;
        let src = extract_player_src(html).unwrap();
        assert_eq!(src, "https://video.sibnet.ru/shell.php?videoid=1234567");
    }

    #[test]
    fn test_clean_stream_url() {
        let raw = "https://dv.sibnet.ru:443/v/12345.mp4";
        assert_eq!(clean_stream_url(raw), "https://dv.sibnet.ru/v/12345.mp4");
    }
}
