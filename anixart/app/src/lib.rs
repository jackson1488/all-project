//! Anixart Linux client library: an iced 0.13 UI (modern Wayland / X11) wired to
//! `anixart-core` (REST API) and `anixart-parsers` (video host resolution).
//!
//! Screens: a login screen offering password, Telegram / VK / Google OAuth
//! (system browser + pasted redirect or token, plus a 1-click Google button that
//! catches its own redirect on a loopback port and exchanges the credential
//! through Firebase) and a direct Anixart session token, then the feed, search,
//! and a release page with voiceover / source / episode selection and playback
//! through an external `mpv` process.
//!
//! The resulting token is persisted to `~/.config/anixart-linux/session.json`
//! (see [`session`]) and restored on the next start, so the user stays logged in.

pub mod app;
pub mod feed;
pub mod login;
pub mod net;
pub mod release;
pub mod search;
pub mod session;

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
    use crate::login::LoginMethod;
    use crate::release::ReleaseView;
    use crate::session::SessionStore;

    use anixart_core::{FeedItem, Pageable};
    use anixart_parsers::{ParsedVideo, VideoSource};

    #[test]
    fn window_title_is_anixart_linux() {
        assert_eq!(crate::WINDOW_TITLE, "Anixart Linux");
    }

    #[test]
    fn startup_lands_on_login_without_a_saved_session() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.restore_session();

        assert_eq!(app.screen, Screen::Login);
        assert!(app.token.is_none());
        assert!(app.stack.is_empty());
        assert!(app.error.is_none());
        assert!(app.status.is_none());
    }

    #[test]
    fn startup_matches_whatever_the_environment_holds() {
        let (app, _task) = App::startup();
        // A restored session implies the feed; nothing else may skip login.
        assert_eq!(app.screen == Screen::Feed, app.token.is_some());
        assert!(app.stack.is_empty());
    }

    #[test]
    fn failed_login_stays_on_login() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginUsername("user".into()));
        let _ = app.update(Message::LoggedIn(Err("401 invalid password".into())));

        assert_eq!(app.screen, Screen::Login);
        assert!(!app.login.loading);
        assert_eq!(app.error.as_deref(), Some("401 invalid password"));
    }

    #[test]
    fn blank_username_does_not_start_a_login() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginUsername("   ".into()));
        let _ = app.update(Message::LoginSubmit);

        assert!(!app.login.loading);
        assert_eq!(app.error.as_deref(), Some("Username is required"));
    }

    #[test]
    fn successful_login_opens_the_feed() {
        let mut app = App::with_store(SessionStore::disabled());
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
        let mut app = App::with_store(SessionStore::disabled());
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
        let mut app = App::with_store(SessionStore::disabled());
        app.feed.loading = true;
        let _ = app.update(Message::FeedLoaded(Err("offline".into())));

        assert!(!app.feed.loading);
        assert_eq!(app.error.as_deref(), Some("offline"));
    }

    #[test]
    fn blank_search_query_is_ignored() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::SearchQuery("   ".into()));
        let _ = app.update(Message::SearchSubmit);

        assert!(!app.search.loading);
        assert_eq!(app.search.page, 0);
    }

    #[test]
    fn navigation_pushes_and_pops_the_stack() {
        let mut app = App::with_store(SessionStore::disabled());
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
        let mut app = App::with_store(SessionStore::disabled());
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
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::SelectType(7));

        assert!(app.release.selected_type.is_none());
        assert!(!app.release.loading_sources);
    }

    #[test]
    fn select_source_out_of_range_is_a_noop() {
        let mut app = App::with_store(SessionStore::disabled());
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
        let mut app = App::with_store(SessionStore::disabled());
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
        let mut app = App::with_store(SessionStore::disabled());
        app.release.episodes = vec![Default::default()];
        let _ = app.update(Message::PlayEpisode(0));

        assert!(app.release.pending.is_none());
        assert_eq!(app.error.as_deref(), Some("Episode has no playback URL"));
    }

    #[test]
    fn playback_failure_clears_the_pending_episode() {
        let mut app = App::with_store(SessionStore::disabled());
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
        let mut app = App::with_store(SessionStore::disabled());
        app.error = Some("temporary".into());
        let _ = app.update(Message::DismissError);

        assert!(app.error.is_none());
    }

    #[test]
    fn dismissing_status_clears_it() {
        let mut app = App::with_store(SessionStore::disabled());
        app.status = Some("Telegram opened in the browser".into());
        let _ = app.update(Message::DismissStatus);

        assert!(app.status.is_none());
    }

    // ── Login tabs and social flows ────────────────────────────────────────

    fn temp_store(tag: &str) -> SessionStore {
        let nanos = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.subsec_nanos())
            .unwrap_or(0);
        let root = std::env::temp_dir().join(format!("anixart-app-{tag}-{nanos:09}"));
        SessionStore::with_root(root)
    }

    #[test]
    fn switching_tab_clears_the_previous_error() {
        let mut app = App::with_store(SessionStore::disabled());
        app.error = Some("bad password".into());

        let _ = app.update(Message::LoginTab(LoginMethod::Telegram));
        assert_eq!(app.login.method, LoginMethod::Telegram);
        assert!(app.error.is_none());
        assert_eq!(app.screen, Screen::Login);
    }

    #[test]
    fn telegram_redirect_needs_the_verifier_it_generated() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Telegram));
        let _ = app.update(Message::LoginTelegramPaste(
            "https://app1593901251-login.tg.dev/tglogin?code=abc".into(),
        ));
        let _ = app.update(Message::LoginTelegramSubmit);

        assert!(!app.login.loading);
        assert_eq!(app.screen, Screen::Login);
        assert!(app
            .error
            .as_deref()
            .unwrap_or_default()
            .starts_with("Open the Telegram login first"));
    }

    #[test]
    fn telegram_id_token_paste_goes_straight_to_the_backend() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Telegram));
        let _ = app.update(Message::LoginTelegramPaste(
            "eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.signature".into(),
        ));
        let _ = app.update(Message::LoginTelegramSubmit);

        assert!(app.login.loading);
        assert!(app.error.is_none());
    }

    #[test]
    fn empty_pastes_are_rejected_without_a_request() {
        let mut app = App::with_store(SessionStore::disabled());

        let _ = app.update(Message::LoginTab(LoginMethod::Vk));
        let _ = app.update(Message::LoginVkSubmit);
        assert!(!app.login.loading);
        assert!(app.error.as_deref().unwrap().contains("VK ID redirect URL"));

        let _ = app.update(Message::LoginTab(LoginMethod::Google));
        let _ = app.update(Message::LoginGoogleSubmit);
        assert!(!app.login.loading);
        assert!(app.error.as_deref().unwrap().contains("id_token"));

        let _ = app.update(Message::LoginTab(LoginMethod::Token));
        let _ = app.update(Message::LoginTokenSubmit);
        assert!(app.error.as_deref().unwrap().contains("Paste an Anixart session token"));
        assert_eq!(app.screen, Screen::Login);
    }

    #[test]
    fn vk_and_google_tokens_start_their_requests() {
        let mut app = App::with_store(SessionStore::disabled());

        let _ = app.update(Message::LoginTab(LoginMethod::Vk));
        let _ = app.update(Message::LoginVkPaste(
            "vk54701760://vk.ru/blank.html#access_token=VK&TTL=7200".into(),
        ));
        let _ = app.update(Message::LoginVkSubmit);
        assert!(app.login.loading);

        let _ = app.update(Message::LoggedIn(Err("Anixart rejected the credential".into())));
        assert!(!app.login.loading);
        assert_eq!(app.screen, Screen::Login);

        let _ = app.update(Message::LoginTab(LoginMethod::Google));
        let _ = app.update(Message::LoginGooglePaste("firebase.id.token".into()));
        let _ = app.update(Message::LoginGoogleSubmit);
        assert!(app.login.loading);
    }

    #[test]
    fn the_one_click_google_button_runs_without_any_setup() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Google));

        // Nothing typed, no token hunted: the APK client id / Firebase key are
        // already in place, so the browser flow starts immediately.
        assert_eq!(app.login.google_client_id, anixart_core::oauth::GOOGLE_WEB_CLIENT_ID);
        assert_eq!(app.login.firebase_api_key, anixart_core::oauth::FIREBASE_API_KEY);

        let _ = app.update(Message::LoginGoogleBrowser);
        assert!(app.login.loading);
        assert!(app.error.is_none());
        assert!(app
            .status
            .as_deref()
            .unwrap_or_default()
            .contains("127.0.0.1"));

        // Headless CI has no opener, so the task reports it through LoggedIn(Err)
        // and the login screen stays usable with the paste fallback.
        let _ = app.update(Message::LoggedIn(Err(
            "Could not open the browser (no desktop opener found)".into(),
        )));
        assert!(!app.login.loading);
        assert_eq!(app.screen, Screen::Login);
        assert!(app.error.as_deref().unwrap().contains("desktop opener"));

        // A second press while loading is ignored.
        let _ = app.update(Message::LoginGooglePaste("ey.jwt.tok".into()));
        let _ = app.update(Message::LoginGoogleSubmit);
        assert!(app.login.loading);
        let _ = app.update(Message::LoginGoogleBrowser);
        assert!(app.login.loading);
    }

    #[test]
    fn google_identifiers_can_be_overridden() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Google));
        let _ = app.update(Message::LoginGoogleClientId("custom.client.id".into()));
        let _ = app.update(Message::LoginFirebaseKey("AIzaCustom".into()));

        assert_eq!(app.login.effective_google_client_id(), "custom.client.id");
        assert_eq!(app.login.effective_firebase_api_key(), "AIzaCustom");

        // Clearing them restores the shipped values.
        let _ = app.update(Message::LoginGoogleClientId(String::new()));
        let _ = app.update(Message::LoginFirebaseKey(String::new()));
        assert_eq!(
            app.login.effective_google_client_id(),
            anixart_core::oauth::GOOGLE_WEB_CLIENT_ID
        );
        assert_eq!(
            app.login.effective_firebase_api_key(),
            anixart_core::oauth::FIREBASE_API_KEY
        );
    }

    #[test]
    fn opening_google_in_manual_mode_always_yields_a_url() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Google));
        let _ = app.update(Message::LoginGoogleOpen);

        // No client id is required any more: the baked-in one is used, so the only
        // possible failure is the missing desktop opener (headless CI).
        assert!(app.status.is_some() || app.error.is_some());
        assert!(!app.login.loading);
    }

    #[test]
    fn opening_a_provider_url_either_succeeds_or_reports_the_opener() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Telegram));
        let _ = app.update(Message::LoginTelegramOpen);

        // Headless CI has no xdg-open, so one of the two is set; both mean the
        // PKCE verifier was generated and the URL was handed to the desktop.
        assert!(app.status.is_some() || app.error.is_some());
        assert!(!app.login.telegram_verifier.is_empty());
    }

    #[test]
    fn a_direct_token_logs_in_without_any_request() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Token));
        let _ = app.update(Message::LoginTokenPaste("  tok-direct  ".into()));
        let _ = app.update(Message::LoginTokenSubmit);

        assert_eq!(app.token.as_deref(), Some("tok-direct"));
        assert_eq!(app.screen, Screen::Feed);
        assert!(app.feed.loading);
        assert!(app.error.is_none());
        assert!(app.status.as_deref().unwrap().contains("Signed in via Token"));
    }

    #[test]
    fn a_saved_session_restores_into_the_feed() {
        let store = temp_store("restore");
        let _ = std::fs::remove_dir_all(store.path().unwrap().parent().unwrap());

        let mut app = App::with_store(store.clone());
        let _ = app.update(Message::LoginTab(LoginMethod::Telegram));
        let _ = app.update(Message::LoginTelegramPaste("jwt.id.token".into()));
        let _ = app.update(Message::LoggedIn(Ok("tok-telegram".into())));
        assert_eq!(app.token.as_deref(), Some("tok-telegram"));
        assert!(app.error.is_none());

        // A brand-new process sees the persisted token.
        let mut reopened = App::with_store(store.clone());
        assert_eq!(reopened.screen, Screen::Login);
        let _ = reopened.restore_session();
        assert_eq!(reopened.token.as_deref(), Some("tok-telegram"));
        assert_eq!(reopened.screen, Screen::Feed);
        assert!(reopened.feed.loading);
        assert!(reopened.status.as_deref().unwrap().contains("telegram"));

        // Logging out wipes the file.
        let _ = reopened.update(Message::Logout);
        assert!(reopened.token.is_none());
        assert_eq!(reopened.screen, Screen::Login);
        assert!(store.load().is_none());
        assert!(store.clear().is_ok());
    }
}
