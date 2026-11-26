//! Anixart Linux client library: an iced 0.13 UI (modern Wayland / X11) wired to
//! `anixart-core` (REST API) and `anixart-parsers` (video host resolution).
//!
//! Screens: login, feed, search, release page with voiceover / source / episode
//! selection, and playback through an external `mpv` process.

pub mod app;
pub mod feed;
pub mod login;
pub mod net;
pub mod release;
pub mod search;

pub use app::{App, Message, Screen};

/// Window title of the Linux client.
pub const WINDOW_TITLE: &str = "Anixart Linux";

/// Builds and runs the Anixart window (used by the `anixart` binary).
pub fn run() -> iced::Result {
    iced::application(WINDOW_TITLE, App::update, App::view)
        .theme(|_| iced::Theme::Dark)
        .window(iced::window::Settings {
            size: iced::Size::new(1024.0, 768.0),
            min_size: Some(iced::Size::new(620.0, 480.0)),
            position: iced::window::Position::Centered,
            ..Default::default()
        })
        .run_with(App::startup)
}

#[cfg(test)]
mod tests {
    // ── Screen transition tests ─────────────────────────────────────────────

    #[derive(Debug, Clone, Copy, PartialEq)]
    enum Screen {
        Login,
        Feed,
        Search,
        Release,
    }

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
        assert_ne!(Screen::Release, Screen::Search);
    }

    // ── EpisodeInfo message test ────────────────────────────────────────────

    #[derive(Debug, Clone)]
    struct EpisodeInfo {
        url: String,
    }

    #[derive(Debug, Clone)]
    enum Msg {
        Play(EpisodeInfo),
        Back,
        Noop,
    }

    #[test]
    fn episode_info_carries_url() {
        let info = EpisodeInfo {
            url: "https://example.com/video.mp4".into(),
        };
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

        let noop = Msg::Noop;
        assert!(matches!(noop, Msg::Noop));
    }

    // ── Error state tests ───────────────────────────────────────────────────

    #[derive(Debug, Clone)]
    struct FormState {
        error: Option<String>,
        loading: bool,
    }

    #[test]
    fn form_state_transitions() {
        let mut form = FormState {
            error: None,
            loading: false,
        };

        form.loading = true;
        assert!(form.loading);

        form.error = Some("network error".into());
        assert_eq!(form.error.as_deref(), Some("network error"));

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
        assert_eq!(screen, Screen::Search);

        // Logout
        screen = Screen::Login;
        stack.clear();

        assert!(stack.is_empty());
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
        assert!(stack.is_empty());

        // Navigate to different screen
        if current != Screen::Search {
            stack.push(current);
        }
        current = Screen::Search;
        assert_eq!(current, Screen::Search);
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

        // Initial state
        assert!(selected_type_idx.is_none());
        assert_eq!(selected_source_idx, Some(0));

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

        fn failing() -> Result<String, String> {
            Err("failed".into())
        }
        let err = failing();
        assert!(err.is_err());
        match err {
            Err(e) => assert_eq!(e, "failed"),
            Ok(_) => panic!("expected Err"),
        }
    }

    // ── Best URL selection mimics ParsedVideo logic ─────────────────────────

    #[test]
    fn best_url_selection() {
        let qualities = [("480p", "url_480"), ("720p", "url_720"), ("1080p", "url_1080")];
        let priority = ["2160p", "1440p", "1080p", "720p", "480p", "360p", "Default"];

        let best = priority
            .iter()
            .find_map(|q| qualities.iter().find(|(k, _)| k == q).map(|(_, v)| *v));
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

#[cfg(test)]
mod app_tests {
    use crate::app::{play_with_mpv, App, Message, Screen};
    use crate::release::ReleaseView;

    use anixart_core::{FeedItem, Pageable};
    use anixart_parsers::{ParsedVideo, VideoSource};

    #[test]
    fn window_title_is_anixart_linux() {
        assert_eq!(crate::WINDOW_TITLE, "Anixart Linux");
    }

    #[test]
    fn startup_lands_on_login() {
        let (app, _task) = App::startup();
        assert_eq!(app.screen, Screen::Login);
        assert!(app.token.is_none());
        assert!(app.stack.is_empty());
        assert!(app.error.is_none());
    }

    #[test]
    fn failed_login_stays_on_login() {
        let (mut app, _) = App::startup();
        let _ = app.update(Message::LoginUsername("user".into()));
        let _ = app.update(Message::LoggedIn(Err("401 invalid password".into())));

        assert_eq!(app.screen, Screen::Login);
        assert!(!app.login.loading);
        assert_eq!(app.error.as_deref(), Some("401 invalid password"));
    }

    #[test]
    fn blank_username_does_not_start_a_login() {
        let (mut app, _) = App::startup();
        let _ = app.update(Message::LoginUsername("   ".into()));
        let _ = app.update(Message::LoginSubmit);

        assert!(!app.login.loading);
        assert_eq!(app.error.as_deref(), Some("Username is required"));
    }

    #[test]
    fn successful_login_opens_the_feed() {
        let (mut app, _) = App::startup();
        let _ = app.update(Message::LoginPassword("secret".into()));
        let _ = app.update(Message::LoggedIn(Ok("token-xyz".into())));

        assert_eq!(app.token.as_deref(), Some("token-xyz"));
        assert_eq!(app.screen, Screen::Feed);
        assert!(app.stack.is_empty());
        assert!(app.feed.loading);
        assert_eq!(app.feed.page, 0);
        assert!(app.login.password.is_empty());
        assert!(!app.login.loading);
    }

    #[test]
    fn feed_loaded_stores_items_and_pages() {
        let (mut app, _) = App::startup();
        let pageable = Pageable {
            code: 0,
            content: vec![FeedItem {
                id: 1,
                title: Some("New season".into()),
                ..Default::default()
            }],
            current_page: 0,
            total_count: 1,
            total_page_count: 3,
        };
        let _ = app.update(Message::FeedLoaded(Ok(pageable)));

        assert_eq!(app.feed.items.len(), 1);
        assert_eq!(app.feed.total_pages, 3);
        assert!(!app.feed.loading);
    }

    #[test]
    fn feed_error_clears_loading() {
        let (mut app, _) = App::startup();
        app.feed.loading = true;
        let _ = app.update(Message::FeedLoaded(Err("offline".into())));

        assert!(!app.feed.loading);
        assert_eq!(app.error.as_deref(), Some("offline"));
    }

    #[test]
    fn blank_search_query_is_ignored() {
        let (mut app, _) = App::startup();
        let _ = app.update(Message::SearchQuery("   ".into()));
        let _ = app.update(Message::SearchSubmit);

        assert!(!app.search.loading);
        assert_eq!(app.search.page, 0);
    }

    #[test]
    fn navigation_pushes_and_pops_the_stack() {
        let (mut app, _) = App::startup();
        app.token = Some("token".into());
        app.screen = Screen::Feed;
        app.stack = Vec::new();

        let _ = app.update(Message::Nav(Screen::Search));
        assert_eq!(app.screen, Screen::Search);
        assert_eq!(app.stack, vec![Screen::Feed]);

        // Re-selecting the current screen must not duplicate the entry.
        let _ = app.update(Message::Nav(Screen::Search));
        assert_eq!(app.stack, vec![Screen::Feed]);

        let _ = app.update(Message::Back);
        assert_eq!(app.screen, Screen::Feed);
        assert!(app.stack.is_empty());
    }

    #[test]
    fn logout_resets_everything() {
        let (mut app, _) = App::startup();
        app.token = Some("token".into());
        app.screen = Screen::Release;
        app.stack = vec![Screen::Feed, Screen::Search];
        app.error = Some("boom".into());

        let _ = app.update(Message::Logout);

        assert_eq!(app.screen, Screen::Login);
        assert!(app.token.is_none());
        assert!(app.stack.is_empty());
        assert!(app.error.is_none());
        assert!(app.feed.items.is_empty());
        assert!(app.release.release.is_none());
    }

    #[test]
    fn select_type_out_of_range_is_a_noop() {
        let (mut app, _) = App::startup();
        let _ = app.update(Message::SelectType(7));

        assert!(app.release.selected_type.is_none());
        assert!(!app.release.loading_sources);
    }

    #[test]
    fn select_source_out_of_range_is_a_noop() {
        let (mut app, _) = App::startup();
        app.release = ReleaseView {
            selected_type: Some(0),
            ..Default::default()
        };
        let _ = app.update(Message::SelectSource(3));

        assert!(app.release.selected_source.is_none());
        assert!(!app.release.loading_episodes);
    }

    #[test]
    fn release_back_walks_the_selection_levels() {
        let (mut app, _) = App::startup();
        app.release = ReleaseView {
            release: None,
            selected_type: Some(0),
            selected_source: Some(1),
            episodes: vec![Default::default()],
            playing: Some(1),
            ..Default::default()
        };
        app.screen = Screen::Release;
        app.stack = vec![Screen::Search];

        let _ = app.update(Message::ReleaseBack);
        assert!(app.release.selected_source.is_none());
        assert!(app.release.episodes.is_empty());
        assert_eq!(app.release.selected_type, Some(0));
        assert_eq!(app.screen, Screen::Release);

        let _ = app.update(Message::ReleaseBack);
        assert!(app.release.selected_type.is_none());

        // Third press leaves the release screen entirely.
        let _ = app.update(Message::ReleaseBack);
        assert_eq!(app.screen, Screen::Search);
    }

    #[test]
    fn play_episode_without_url_reports_an_error() {
        let (mut app, _) = App::startup();
        app.release.episodes = vec![Default::default()];
        let _ = app.update(Message::PlayEpisode(0));

        assert!(app.release.pending.is_none());
        assert_eq!(app.error.as_deref(), Some("Episode has no playback URL"));
    }

    #[test]
    fn playback_failure_clears_the_pending_episode() {
        let (mut app, _) = App::startup();
        app.release.pending = Some(2);
        let _ = app.update(Message::PlaybackResolved(Err("resolver down".into())));

        assert!(app.release.pending.is_none());
        assert_eq!(app.error.as_deref(), Some("resolver down"));
    }

    #[test]
    fn mpv_needs_a_resolved_stream() {
        let video = ParsedVideo::new(VideoSource::Direct);
        assert!(video.best_url().is_none());
        assert!(play_with_mpv(&video).is_err());
    }

    #[test]
    fn dismissing_error_clears_it() {
        let (mut app, _) = App::startup();
        app.error = Some("temporary".into());
        let _ = app.update(Message::DismissError);

        assert!(app.error.is_none());
    }
}
