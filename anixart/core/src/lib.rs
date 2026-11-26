//! Anixart API core (shared by Linux and, later, Windows clients).

/// Reverse-engineered Anixart API base URL.
pub const API_BASE_URL: &str = "https://api-s.anixsekai.com/";

#[cfg(test)]
mod tests {
    use super::API_BASE_URL;

    #[test]
    fn api_base_url_is_https() {
        assert!(API_BASE_URL.starts_with("https://"));
    }
}
