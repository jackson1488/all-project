//! Библиотека клиента Anixart Linux: интерфейс на iced 0.13 (современные Wayland и
//! X11), обвязанный вокруг `anixart-core` (REST API) и `anixart-parsers` (решатели
//! видеохостингов).
//!
//! Экраны: вход — пароль, Telegram, VK ID, Yandex ID, Google / Firebase и прямой
//! токен сессии, плюс гостевой пропуск; затем четыре корневых раздела — «Главная»,
//! «Обзор», «Закладки», «Профиль» — и страница релиза со спуском
//! озвучка → источник → серия, воспроизведение через внешний процесс `mpv`.
//!
//! Всё, что видит пользователь, написано по-русски и одето в Material 3 Dark
//! ([`theme`]); обложки тянутся асинхронно и кэшируются ([`posters`]).
//!
//! Токен сессии переживает перезапуск в `~/.config/anixart-linux/session.json`
//! (см. [`session`]) и восстанавливается при следующем старте, так что вход
//! приходится повторять только после выхода.

pub mod app;
pub mod bookmarks;
pub mod feed;
pub mod lists;
pub mod login;
pub mod net;
pub mod posters;
pub mod profile;
pub mod release;
pub mod search;
pub mod session;
pub mod theme;
pub mod util;
pub mod widgets;

pub use app::{App, Message, Screen};

/// Заголовок окна клиента.
pub const WINDOW_TITLE: &str = "Anixart Linux";

/// Стартовая ширина окна: из неё [`App::body_width`] считает зону тела до первого
/// события [`Message::WindowResized`](crate::app::Message::WindowResized).
pub use app::DEFAULT_WINDOW_WIDTH;

/// Сборка и запуск окна Anixart (используется бинарником `anixart`).
///
/// Подписка здесь не деталь: именно она приносит приложению размер окна, поэтому
/// тела экранов строятся для известной ширины и их [`scrollable`](iced::widget::scrollable)
/// не пересоздается на каждый кадр — лечение мерцания, описанное в
/// [`crate::widgets::sized_body`]. Та же подписка опрашивает часы встроенного плеера.
pub fn run() -> iced::Result {
    iced::application(WINDOW_TITLE, App::update, App::view)
        .theme(|app| app.theme())
        .subscription(App::subscription)
        .window(iced::window::Settings {
            size: iced::Size::new(DEFAULT_WINDOW_WIDTH as f64, 860.0),
            min_size: Some(iced::Size::new(880.0, 560.0)),
            position: iced::window::Position::Centered,
            ..Default::default()
        })
        .run_with(App::startup)
}

#[cfg(test)]
mod app_tests {
    use crate::app::{mpv_arguments, play_with_mpv, App, Message, Screen};
    use crate::lists::{BrowseTab, FeedCategory};
    use crate::login::{LoginMethod, PasteTarget};
    use crate::release::ReleaseView;
    use crate::session::SessionStore;

    use anixart_core::{Pageable, Release};
    use anixart_parsers::{ParsedVideo, VideoSource};

    /// Хранилище в временном каталоге: своя папка на каждый тест.
    fn temp_store(tag: &str) -> SessionStore {
        let nanos = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.subsec_nanos())
            .unwrap_or(0);
        let root = std::env::temp_dir().join(format!("anixart-app-{tag}-{nanos:09}"));
        SessionStore::with_root(root)
    }

    #[test]
    fn window_title_is_anixart_linux() {
        assert_eq!(crate::WINDOW_TITLE, "Anixart Linux");
    }

    #[test]
    fn every_screen_module_is_declared() {
        // Порядок объявления важен только для читаемости, а вот полнота — нет:
        // пропущенный модуль ломает сборку, поэтому здесь проверяется, что
        // русские экраны и их инфраструктура действительно экспортированы.
        assert_eq!(Screen::Feed.label(), "Главная");
        assert_eq!(Screen::Browse.label(), "Обзор");
        assert_eq!(Screen::Bookmarks.label(), "Закладки");
        assert_eq!(Screen::Profile.label(), "Профиль");

        // Тема окна — свой Material 3 Dark, а не встроенный `Theme::Dark`.
        let theme = crate::theme::theme();
        assert_eq!(format!("{theme}"), "Anixart Dark");
        assert_eq!(theme.palette(), crate::theme::palette());
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
        // Восстановленная сессия открывает «Главную»; больше ничего не минует вход.
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
        assert_eq!(app.error.as_deref(), Some("Укажите логин или почту профиля"));
    }

    #[test]
    fn successful_login_opens_the_feed() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginPassword("secret".into()));
        let _ = app.update(Message::LoggedIn(Ok("token-xyz".into())));

        assert_eq!(app.token.as_deref(), Some("token-xyz"));
        assert_eq!(app.screen, Screen::Feed);
        assert!(app.stack.is_empty());
        assert!(app.feed.paging.loading);
        assert_eq!(app.feed.paging.pages, 0);
        assert!(app.login.password.is_empty());
        assert!(!app.login.loading);
    }

    #[test]
    fn feed_loaded_stores_releases_and_the_page_that_brought_them() {
        let mut app = App::with_store(SessionStore::disabled());
        let pageable = Pageable {
            code: 0,
            content: vec![Release {
                id: 1,
                title_ru: Some("Новый сезон".into()),
                ..Default::default()
            }],
            current_page: 0,
            total_count: 0,
            // живой `POST /filter` всегда отвечает нулём: полка верит не ему
            total_page_count: 0,
        };
        let _ = app.update(Message::FeedLoaded(FeedCategory::Actual, Ok(pageable)));

        assert_eq!(app.feed.releases.len(), 1);
        assert_eq!(app.feed.paging.pages, 1);
        assert!(!app.feed.paging.loading);
        assert!(!app.feed.paging.exhausted, "полная страница — не конец");
    }

    #[test]
    fn feed_error_clears_loading() {
        let mut app = App::with_store(SessionStore::disabled());
        app.feed.begin();
        let _ = app.update(Message::FeedLoaded(
            FeedCategory::Actual,
            Err("офлайн".into()),
        ));

        assert!(!app.feed.paging.loading);
        assert_eq!(app.error.as_deref(), Some("офлайн"));
    }

    #[test]
    fn blank_search_query_is_ignored() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::Nav(Screen::Browse));
        assert_eq!(app.browse.tab, BrowseTab::Search);

        let _ = app.update(Message::SearchQuery("   ".into()));
        let _ = app.update(Message::SearchSubmit);

        assert!(!app.browse.paging.loading);
        assert_eq!(app.browse.paging.pages, 0);
    }

    #[test]
    fn navigation_pushes_and_pops_the_stack() {
        let mut app = App::with_store(SessionStore::disabled());
        app.token = Some("token".into());
        app.screen = Screen::Feed;
        app.stack = Vec::new();

        let _ = app.update(Message::Nav(Screen::Browse));
        assert_eq!(app.screen, Screen::Browse);
        assert_eq!(app.stack, vec![Screen::Feed]);

        // Повторный выбор текущего раздела не дублирует запись стека.
        let _ = app.update(Message::Nav(Screen::Browse));
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
        app.stack = vec![Screen::Feed, Screen::Browse];
        app.error = Some("boom".into());

        let _ = app.update(Message::Logout);

        assert_eq!(app.screen, Screen::Login);
        assert!(app.token.is_none());
        assert!(app.stack.is_empty());
        assert!(app.error.is_none());
        assert!(app.feed.releases.is_empty());
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
        app.screen = Screen::Sources;
        app.stack = vec![Screen::Browse, Screen::Release];

        // на экране источников первой сворачивается серия
        let _ = app.update(Message::ReleaseBack);
        assert!(app.release.selected_source.is_none());
        assert!(app.release.episodes.is_empty());
        assert_eq!(app.release.selected_type, Some(0));
        assert_eq!(app.screen, Screen::Sources);

        // вторая ступень — назад к озвучкам, то есть на страницу релиза
        let _ = app.update(Message::ReleaseBack);
        assert!(app.release.selected_type.is_none());
        assert_eq!(app.screen, Screen::Release);

        // со страницы релиза отступать некуда: третья нажатие закрывает её
        let _ = app.update(Message::ReleaseBack);
        assert_eq!(app.screen, Screen::Browse);
    }

    #[test]
    fn the_variant_choice_stays_on_the_release_page_until_a_voiceover_is_picked() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::Nav(Screen::Browse));
        let _ = app.update(Message::OpenRelease(3));
        let _ = app.update(Message::ReleaseLoaded(Ok(Box::new(Release {
            id: 3,
            title_ru: Some("Наруто".into()),
            ..Default::default()
        }))));
        assert!(app.release.loading_types);

        let _ = app.update(Message::TypesLoaded(Ok(vec![
            anixart_core::EpisodeType {
                id: 5,
                name: "AniDUB".into(),
                is_sub: false,
                ..Default::default()
            },
            anixart_core::EpisodeType {
                id: 6,
                name: "Субтитры | AniDUB".into(),
                is_sub: true,
                ..Default::default()
            },
        ])));

        let _ = app.update(Message::ReleaseChoose);
        assert!(app.release.choosing);
        assert_eq!(app.screen, Screen::Release, "пока выбран только способ просмотра");

        let _ = app.update(Message::SelectVariant(crate::lists::VariantFilter::Subtitle));
        assert_eq!(app.release.visible_types().len(), 1);
        assert_eq!(app.screen, Screen::Release);

        let _ = app.update(Message::SelectType(1));
        assert_eq!(app.screen, Screen::Sources, "озвучка выбрана — экран свой");
        assert!(app.release.loading_sources);
    }

    #[test]
    fn play_episode_without_url_reports_an_error() {
        let mut app = App::with_store(SessionStore::disabled());
        app.release.episodes = vec![Default::default()];
        let _ = app.update(Message::PlayEpisode(0));

        assert!(app.release.pending.is_none());
        let error = app.error.expect("подсказка о пустой ссылке");
        assert!(error.contains("нет ссылки"), "{error}");
    }

    #[test]
    fn playback_failure_clears_the_pending_episode() {
        let mut app = App::with_store(SessionStore::disabled());
        app.release.pending = Some(2);
        let _ = app.update(Message::PlaybackResolved(Err("решатель недоступен".into())));

        assert!(app.release.pending.is_none());
        assert_eq!(app.error.as_deref(), Some("решатель недоступен"));
    }

    #[test]
    fn mpv_needs_a_resolved_stream() {
        let video = ParsedVideo::new(VideoSource::Direct);
        assert!(video.best_url().is_none());
        assert!(play_with_mpv(&video, "Наруто — №1").is_err());
    }

    #[test]
    fn mpv_is_started_with_a_window_and_a_readable_title() {
        let video = ParsedVideo::new(VideoSource::Kodik)
            .with_quality("Default", "https://kodik.example/1.mp4")
            .with_header("Referer", "https://kodik.info/");

        let arguments = mpv_arguments(&video, "Наруто — №12 · Битва за Коноху").unwrap();
        assert_eq!(
            arguments,
            vec![
                "--force-window=immediate".to_owned(),
                "--no-terminal".to_owned(),
                "--title=Наруто — №12 · Битва за Коноху".to_owned(),
                "https://kodik.example/1.mp4".to_owned(),
                "--http-header-fields=Referer: https://kodik.info/".to_owned(),
            ],
            "окно форсируется, терминал молчит, заголовки хоста при делеге"
        );

        // пустая подпись не рождает голого тире в заголовке окна
        let arguments = mpv_arguments(&video, "   ").unwrap();
        assert_eq!(arguments[2], "https://kodik.example/1.mp4");
        assert!(arguments.iter().all(|argument| !argument.contains("--title=")));
    }

    #[test]
    fn dismissing_error_clears_it() {
        let mut app = App::with_store(SessionStore::disabled());
        app.error = Some("временная".into());
        let _ = app.update(Message::DismissError);

        assert!(app.error.is_none());
    }

    #[test]
    fn dismissing_status_clears_it() {
        let mut app = App::with_store(SessionStore::disabled());
        app.status = Some("Telegram открыт в браузере".into());
        let _ = app.update(Message::DismissStatus);

        assert!(app.status.is_none());
    }

    // ── Вход: способы и социальные потоки ────────────────────────────────────

    #[test]
    fn switching_tab_clears_the_previous_error() {
        let mut app = App::with_store(SessionStore::disabled());
        app.error = Some("неверный пароль".into());

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
        let error = app.error.as_deref().unwrap_or_default();
        assert!(error.starts_with("Сначала откройте вход Telegram"), "{error}");
        assert!(error.contains("PKCE"), "{error}");
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
        assert!(app.error.as_deref().unwrap().contains("VK ID"));

        let _ = app.update(Message::LoginTab(LoginMethod::Yandex));
        let _ = app.update(Message::LoginYandexSubmit);
        assert!(!app.login.loading);
        assert!(app.error.as_deref().unwrap().contains("Yandex"));

        let _ = app.update(Message::LoginTab(LoginMethod::Google));
        let _ = app.update(Message::LoginGoogleSubmit);
        assert!(!app.login.loading);
        assert!(app.error.as_deref().unwrap().contains("id_token"));

        let _ = app.update(Message::LoginTab(LoginMethod::Token));
        let _ = app.update(Message::LoginTokenSubmit);
        assert!(app.error.as_deref().unwrap().contains("токен сессии"));
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

        let _ = app.update(Message::LoggedIn(Err("Anixart отверг креденциал".into())));
        assert!(!app.login.loading);
        assert_eq!(app.screen, Screen::Login);

        let _ = app.update(Message::LoginTab(LoginMethod::Google));
        let _ = app.update(Message::LoginGooglePaste("firebase.id.token".into()));
        let _ = app.update(Message::LoginGoogleSubmit);
        assert!(app.login.loading);
    }

    #[test]
    fn yandex_opens_the_implicit_flow_and_takes_its_token_from_the_address_bar() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Yandex));
        let _ = app.update(Message::LoginYandexOpen);

        // Headless CI не имеет открывщика, поэтому либо подсказка, либо ошибка;
        // URL в любом случае собран и передан-desktop.
        assert!(app.status.is_some() || app.error.is_some());
        assert!(!app.login.loading);

        let _ = app.update(Message::LoginYandexPaste(
            "https://yxa980f005.oauth.yandex.ru/auth/finish?platform=android&access_token=YAN.123"
                .into(),
        ));
        let _ = app.update(Message::LoginYandexSubmit);
        assert!(app.login.loading);
        assert!(app.error.is_none());

        let _ = app.update(Message::LoggedIn(Ok("tok-yandex".into())));
        assert_eq!(app.token.as_deref(), Some("tok-yandex"));
        assert_eq!(app.screen, Screen::Feed);
    }

    #[test]
    fn the_one_click_google_button_runs_without_any_setup() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Google));

        // Ничего не вписано и не искалось: client id APK и ключ Firebase уже на
        // месте, поэтому браузерный поток стразу начинается.
        assert_eq!(
            app.login.google_client_id,
            anixart_core::oauth::GOOGLE_WEB_CLIENT_ID
        );
        assert_eq!(
            app.login.firebase_api_key,
            anixart_core::oauth::FIREBASE_API_KEY
        );

        let _ = app.update(Message::LoginGoogleBrowser);
        assert!(app.login.loading);
        assert!(app.error.is_none());
        assert!(app
            .status
            .as_deref()
            .unwrap_or_default()
            .contains("127.0.0.1"));

        // В headless CI открывщика нет, поэтому поток сообщает об этом через
        // LoggedIn(Err), а экран входа остаётся рабочим через ручную вставку.
        let _ = app.update(Message::LoggedIn(Err(
            "Не удалось открыть браузер (desktop opener not found)".into(),
        )));
        assert!(!app.login.loading);
        assert_eq!(app.screen, Screen::Login);
        assert!(app.error.as_deref().unwrap().contains("desktop opener"));

        // Второе нажатие во время полёта игнорируется.
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

        // Очищенные поля падают обратно на вшитые значения.
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

        // Client id больше не требуется: используется вшитый, поэтому единственная
        // возможная неудача — отсутствие открывщика (headless CI).
        assert!(app.status.is_some() || app.error.is_some());
        assert!(!app.login.loading);
    }

    #[test]
    fn opening_a_provider_url_either_succeeds_or_reports_the_opener() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Telegram));
        let _ = app.update(Message::LoginTelegramOpen);

        // В headless CI нет xdg-open, поэтому заполнено одно из двух; и то и другое
        // означает, что PKCE-verifier сгенерирован и URL передан системе.
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
        assert!(app.feed.paging.loading);
        assert!(app.error.is_none());
        assert!(app.status.as_deref().unwrap().contains("Токен"));
    }

    // ── Буфер обмена: «Вставить и войти» ─────────────────────────────────────

    #[test]
    fn clipboard_text_signs_the_token_tab_in() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Token));
        // Читалки печатают перевод строки в конце, а iced отдаёт то, что нашёл.
        let _ = app.update(Message::ClipboardText(
            PasteTarget::Token,
            Some("  tok-clipboard \n".into()),
        ));

        assert_eq!(app.login.token, "tok-clipboard");
        assert_eq!(app.token.as_deref(), Some("tok-clipboard"));
        assert_eq!(app.screen, Screen::Feed);
        assert!(app.feed.paging.loading);
        assert!(app.error.is_none());
    }

    #[test]
    fn an_empty_iced_clipboard_is_handed_to_the_external_readers() {
        let mut app = App::with_store(SessionStore::disabled());

        // `None`, пустая строка и один пробел значат одно: winit выделения не нашёл,
        // поэтому должны ответить `wl-paste` / `xclip` / `xsel`.
        for contents in [None, Some(String::new()), Some("   \n".into())] {
            let _ = app.update(Message::ClipboardText(PasteTarget::Vk, contents));
            assert!(app.login.vk_paste.is_empty());
            assert!(!app.login.loading);
            assert!(app.error.is_none(), "запасной путь ещё в полёте");
        }
    }

    #[test]
    fn fallback_output_is_pasted_and_submitted() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Vk));
        let _ = app.update(Message::ClipboardFallback(
            PasteTarget::Vk,
            Ok("vk.ru/blank.html#access_token=CLIP&state=s".into()),
        ));

        assert_eq!(
            app.login.vk_paste,
            "vk.ru/blank.html#access_token=CLIP&state=s"
        );
        // Адресная строка потеряла схему, поэтому токен читается из восстановленного
        // URL, а запрос уходит на вкладке VK.
        assert!(app.login.loading);
        assert!(app.error.is_none());
    }

    #[test]
    fn a_yandex_clipboard_paste_lands_on_the_yandex_tab() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Yandex));
        let _ = app.update(Message::ClipboardText(
            PasteTarget::Yandex,
            Some("https://yxa980f005.oauth.yandex.ru/auth/finish?access_token=YA\n".into()),
        ));

        assert_eq!(
            app.login.yandex_paste,
            "https://yxa980f005.oauth.yandex.ru/auth/finish?access_token=YA"
        );
        assert!(app.login.loading);
        assert!(app.error.is_none());
    }

    #[test]
    fn a_telegram_paste_still_needs_the_verifier_the_open_generated() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Telegram));
        let _ = app.update(Message::ClipboardFallback(
            PasteTarget::Telegram,
            Ok("app1593901251-login.tg.dev/tglogin?code=abc123".into()),
        ));

        assert_eq!(
            app.login.telegram_paste,
            "app1593901251-login.tg.dev/tglogin?code=abc123"
        );
        assert!(!app.login.loading);
        assert!(app.error.as_deref().unwrap().contains("PKCE"));
        assert_eq!(app.screen, Screen::Login);
    }

    #[test]
    fn a_failed_clipboard_read_names_the_tab_it_was_meant_for() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoginTab(LoginMethod::Google));
        let _ = app.update(Message::ClipboardFallback(
            PasteTarget::Google,
            Err("ни одна программа для чтения буфера не запустилась (wl-paste: No such file or directory)".into()),
        ));

        let error = app.error.expect("ошибка");
        assert!(error.starts_with("Google:"), "{error}");
        assert!(error.contains("wl-paste"), "{error}");
        assert!(app.login.google_paste.is_empty());
        assert!(!app.login.loading);
        assert_eq!(app.screen, Screen::Login);
    }

    #[test]
    fn pressing_paste_clears_the_previous_error_and_ignores_a_second_press() {
        let mut app = App::with_store(SessionStore::disabled());
        app.error = Some("устарело".into());
        let _ = app.update(Message::LoginClipboard(PasteTarget::Telegram));
        assert!(app.error.is_none(), "чтение буфера началось");

        // Пока вход в полёте, кнопка бездейственна — как и её сообщение.
        app.login.loading = true;
        let _ = app.update(Message::LoginClipboard(PasteTarget::Telegram));
        assert!(app.error.is_none());
        assert!(app.login.loading);
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

        // Новый процесс видит сохранённый токен.
        let mut reopened = App::with_store(store.clone());
        assert_eq!(reopened.screen, Screen::Login);
        let _ = reopened.restore_session();
        assert_eq!(reopened.token.as_deref(), Some("tok-telegram"));
        assert_eq!(reopened.screen, Screen::Feed);
        assert!(reopened.feed.paging.loading);
        assert!(reopened.status.as_deref().unwrap().contains("telegram"));

        // Выход стирает файл.
        let _ = reopened.update(Message::Logout);
        assert!(reopened.token.is_none());
        assert_eq!(reopened.screen, Screen::Login);
        assert!(store.load().is_none());
        assert!(store.clear().is_ok());
    }
}
