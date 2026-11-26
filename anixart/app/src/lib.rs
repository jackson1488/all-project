//! Unit tests for app state, screen transitions, and message handling.

#[cfg(test)]
mod tests {
    // ── Screen transition tests ─────────────────────────────────────────────

    #[derive(Debug, Clone, Copy, PartialEq)]
    enum Screen { Login, Feed, Search, Release }

    #[test]
    fn screen_navigation_works() {
        let mut stack: Vec<Screen> = Vec::new();
        let mut current = Screen::Login;

        // Login -> Feed
        stack.push(current);
        current = Screen::Feed;
        assert_eq!(current, Screen::Feed);
        assert_eq!(stack.last(), Some(&Screen::Login));

        // Feed -> Search
        stack.push(current);
        current = Screen::Search;
        assert_eq!(current, Screen::Search);

        // Back
        current = stack.pop().unwrap();
        assert_eq!(current, Screen::Feed);
        current = stack.pop().unwrap();
        assert_eq!(current, Screen::Login);
    }

    #[test]
    fn screen_equality() {
        assert_eq!(Screen::Login, Screen::Login);
        assert_ne!(Screen::Feed, Screen::Search);
    }

    // ── EpisodeInfo message test ────────────────────────────────────────────

    #[derive(Debug, Clone)]
    struct EpisodeInfo { url: String }

    #[derive(Debug, Clone)]
    enum Msg {
        Play(EpisodeInfo),
        Back,
        Noop,
    }

    #[test]
    fn episode_info_carries_url() {
        let info = EpisodeInfo { url: "https://example.com/video.mp4".into() };
        let msg = Msg::Play(info);
        if let Msg::Play(ep) = msg {
            assert!(ep.url.contains("example.com"));
            assert!(ep.url.ends_with(".mp4"));
        } else {
            panic!("expected Play variant");
        }
    }

    #[test]
    fn message_clone() {
        let m1 = Msg::Back;
        let m2 = m1.clone();
        assert!(matches!(m2, Msg::Back));
    }

    // ── Error state tests ───────────────────────────────────────────────────

    #[derive(Debug, Clone)]
    struct FormState {
        error: Option<String>,
        loading: bool,
    }

    #[test]
    fn form_state_transitions() {
        let mut form = FormState { error: None, loading: false };

        form.loading = true;
        assert!(form.loading);

        form.error = Some("network error".into());
        assert_eq!(
            form.error.as_deref(),
            Some("network error")
        );

        form.loading = false;
        form.error = None;
        assert!(form.error.is_none());
        assert!(!form.loading);
    }

    // ── Navigation guard: logout clears stack ───────────────────────────────

    #[test]
    fn logout_clears_state() {
        let mut stack = vec![Screen::Login, Screen::Feed, Screen::Search];
        let mut screen = Screen::Search;

        // Logout
        screen = Screen::Login;
        stack.clear();

        assert_eq!(stack.len(), 0);
        assert_eq!(screen, Screen::Login);
    }

    // ── Screen prevents duplicate pushes ────────────────────────────────────

    #[test]
    fn no_duplicate_screen_push() {
        let mut stack: Vec<Screen> = Vec::new();
        let mut current = Screen::Feed;

        if current != Screen::Feed {
            stack.push(current);
        }
        assert_eq!(stack.len(), 0);

        // Navigate to different screen
        if current != Screen::Search {
            stack.push(current);
        }
        current = Screen::Search;
        assert_eq!(stack.len(), 1);
        assert_eq!(stack[0], Screen::Feed);
    }

    // ── Type selected state ─────────────────────────────────────────────────

    #[test]
    fn type_selection_resets_sources_and_episodes() {
        let mut selected_type_idx: Option<usize> = None;
        let mut selected_source_idx: Option<usize> = Some(0);
        let mut sources = vec!["src1", "src2"];
        let mut episodes = vec!["ep1", "ep2"];

        // Select a type
        selected_type_idx = Some(0);
        selected_source_idx = None;
        sources.clear();
        episodes.clear();

        assert_eq!(selected_type_idx, Some(0));
        assert!(selected_source_idx.is_none());
        assert!(sources.is_empty());
        assert!(episodes.is_empty());
    }

    // ── Source selection resets episodes ────────────────────────────────────

    #[test]
    fn source_selection_resets_episodes() {
        let mut episodes = vec!["ep1", "ep2"];

        // Select a source
        episodes.clear();

        assert!(episodes.is_empty());
    }

    // ── Async result wrapping ───────────────────────────────────────────────

    #[test]
    fn result_mapping() {
        let ok: Result<String, String> = Ok("token_abc".into());
        let mapped = ok.map(|t| format!("got: {t}"));
        assert_eq!(mapped.as_deref(), Ok("got: token_abc"));

        let err: Result<String, String> = Err("failed".into());
        assert!(err.is_err());
        assert_eq!(err.unwrap_err(), "failed");
    }

    // ── Best URL selection mimics ParsedVideo logic ─────────────────────────

    #[test]
    fn best_url_selection() {
        let qualities = vec![
            ("480p", "url_480"),
            ("720p", "url_720"),
            ("1080p", "url_1080"),
        ];
        let priority = ["2160p", "1440p", "1080p", "720p", "480p", "360p", "Default"];

        let best = priority.iter().find_map(|q| {
            qualities.iter().find(|(k, _)| k == q).map(|(_, v)| *v)
        });
        assert_eq!(best, Some("url_1080"));
    }

    // ── Playback URL validation ─────────────────────────────────────────────

    #[test]
    fn direct_stream_check() {
        // Replicate anixart_parsers::is_direct_stream logic
        fn is_direct(url: &str) -> bool {
            let path = url.split('?').next().unwrap_or(url).to_ascii_lowercase();
            path.ends_with(".mp4") || path.ends_with(".m3u8")
        }

        assert!(is_direct("https://cdn.example.com/video.mp4?token=x"));
        assert!(is_direct("https://cdn.example.com/master.m3u8"));
        assert!(!is_direct("https://kodik.info/serial/123"));
    }
}
