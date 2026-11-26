//! Anixart API core client (shared by Linux and, later, Windows clients).

pub mod client;
pub mod error;
pub mod models;
pub mod oauth;

pub use client::AnixartClient;
pub use error::{AnixartError, Result};
pub use models::*;
pub use oauth::SocialProvider;

/// Reverse-engineered Anixart API base URL.
pub const API_BASE_URL: &str = "https://api-s.anixsekai.com/";

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn api_base_url_is_https() {
        assert!(API_BASE_URL.starts_with("https://"));
    }

    #[test]
    fn client_stores_token() {
        let mut client = AnixartClient::with_base_url("https://example.test/").unwrap();
        assert!(client.token().is_none());
        client.set_token("abc");
        assert_eq!(client.token(), Some("abc"));
        client.clear_token();
        assert!(client.token().is_none());
    }

    #[tokio::test]
    async fn favorites_and_history_require_token() {
        let client = AnixartClient::with_base_url("https://example.test/").unwrap();
        assert!(matches!(
            client.favorites(0, None, None).await,
            Err(AnixartError::MissingToken)
        ));
        assert!(matches!(
            client.history(0).await,
            Err(AnixartError::MissingToken)
        ));
    }
}
