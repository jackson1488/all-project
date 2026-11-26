//! Состояние приложения, маршрутизация сообщений и сборка экранов.
//!
//! Приложение — один объект [`App`] с перечнем экранов ([`Screen`]) и стеком
//! возврата, а вся работа идёт через [`Message`]: каждое нажатие превращается в
//! сообщение, сообщение возвращает [`iced::Task`], а ответ сети приходит тем же
//! сообщением. Такой порядок даёт три свойства, за которыми здесь следят:
//!
//! * **Гость не стреляет в закрытые двери.** `favorites`, `watch_list`, `history`,
//!   `collectionFavorite` и `profile/*` — вызовы `require_token()`, поэтому каждый
//!   fetch сверяет токен до того, как построить запрос
//!   ([`App::fetch_bookmarks`], [`App::fetch_profile`], [`App::signed_in`] на
//!   странице релиза). Гость видит «Обзор» и «Главную» — единственные полки,
//!   которые читаются без входа.
//! * **Постеры тянутся один раз.** [`App::refresh_posters`] после каждого сообщения
//!   проходит видимые объекты через [`PosterCache::claim`], а `claim` отвечает
//!   [`PosterEffect::None`] для уже готового, летящего и заведомо битого — так что
//!   повторных запросов нет, а битая обложка не порождает ретрай-шторм.
//! * **Запоздавший ответ не портит картинку.** Ответы `profile/list/*` и
//!   `favorite/*` несут id релиза, и [`App::route`] применяет их только к тому же
//!   самому релизу, который сейчас открыт.
//!
//! Экраны владеют своей формой данных ([`Feed`], [`Browse`], [`Bookmarks`],
//! [`ProfileView`], [`ReleaseView`], [`Login`]), а здесь лежат только запросы,
//! навигация, баннеры и постер-веер.

use std::process::{Command, Stdio};

use anixart_core::{
    Collection, Episode, EpisodeType, FeedItem, Pageable, Release, ReleaseSearchResponse, Source,
};
use anixart_parsers::ParsedVideo;
use iced::widget::{button, container, horizontal_space, vertical_space, Column, Row};
use iced::{alignment, Element, Length, Task, Theme};

use crate::bookmarks::{BookmarkPage, Bookmarks};
use crate::feed::Feed;
use crate::lists::{BookmarkTab, BrowseTab, FeedCategory, WatchStatus};
use crate::login::{Login, LoginMethod, PasteTarget, TelegramPaste};
use crate::net;
use crate::posters::{self, PosterCache, PosterEffect};
use crate::profile::{ProfileSnapshot, ProfileView};
use crate::release::ReleaseView;
use crate::search::{Browse, BrowsePage};
use crate::session::SessionStore;
use crate::theme;
use crate::util;
use crate::widgets;

/// Ширина навигационной панели — четырёх разделов, которые всегда под рукой.
const RAIL_WIDTH: f32 = 210.0;

/// Экраны клиента.
///
/// Первые четыре — корневые, они же пилюли навигационной панели; остальные два
/// открываются поверх них и возвращаются стеком.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum Screen {
    #[default]
    Login,
    Feed,
    Browse,
    Bookmarks,
    Profile,
    Release,
    Collection,
}

impl Screen {
    /// Корневые разделы в порядке пилюлей панели.
    pub const NAV: [Screen; 4] = [
        Screen::Feed,
        Screen::Browse,
        Screen::Bookmarks,
        Screen::Profile,
    ];

    /// Подпись пилюли.
    pub fn label(self) -> &'static str {
        match self {
            Screen::Login => "Вход",
            Screen::Feed => "Главная",
            Screen::Browse => "Обзор",
            Screen::Bookmarks => "Закладки",
            Screen::Profile => "Профиль",
            Screen::Release => "Релиз",
            Screen::Collection => "Коллекция",
        }
    }

    /// Какая пилюля горит, когда открыт этот экран; `None` для входа и вложенных
    /// экранов, у которых нет своего места в панели.
    pub fn root(self) -> Option<Screen> {
        Screen::NAV.contains(&self).then_some(self)
    }
}

/// Все сообщения приложения.
#[derive(Debug, Clone)]
pub enum Message {
    // ── Вход: общий ────────────────────────────────────────────────────────
    LoginTab(LoginMethod),
    /// Скрыть информационную строку.
    DismissStatus,
    /// Скрыть ошибку.
    DismissError,

    // ── Вход: пароль ───────────────────────────────────────────────────────
    LoginUsername(String),
    LoginPassword(String),
    LoginSubmit,

    // ── Вход: Telegram (OAuth 2.0 + PKCE Login Widget) ─────────────────────
    LoginTelegramOpen,
    LoginTelegramPaste(String),
    LoginTelegramSubmit,

    // ── Вход: VK ID ────────────────────────────────────────────────────────
    LoginVkOpen,
    LoginVkPaste(String),
    LoginVkSubmit,

    // ── Вход: Yandex ID (иммитуый поток, токен из адресной строки) ─────────
    LoginYandexOpen,
    LoginYandexPaste(String),
    LoginYandexSubmit,

    // ── Вход: Google / Firebase ────────────────────────────────────────────
    /// Вход одним кликом: loopback-слушатель → браузер → Firebase
    /// `accounts:signInWithIdp` → `POST /auth/google`. Ни id, ни токена, ни вставки.
    LoginGoogleBrowser,
    LoginGoogleClientId(String),
    LoginFirebaseKey(String),
    LoginGoogleRedirect(String),
    /// Ручной вариант со вставкой: браузер без локального слушателя.
    LoginGoogleOpen,
    LoginGooglePaste(String),
    LoginGoogleSubmit,

    // ── Вход: прямой токен сессии ──────────────────────────────────────────
    LoginTokenPaste(String),
    LoginTokenSubmit,

    // ── Вход: буфер обмена («Вставить и войти») ────────────────────────────
    /// Кнопка нажата. Ctrl+V в iced 0.13 до `text_input` под Wayland не доходит,
    /// поэтому выделение вытягивается здесь.
    LoginClipboard(PasteTarget),
    /// Что ответил `iced::clipboard::read()` — пустой ответ передаёт дело
    /// внешним читалкам `wl-paste` / `xclip` / `xsel`.
    ClipboardText(PasteTarget, Option<String>),
    /// Что ответили те читалки.
    ClipboardFallback(PasteTarget, Result<String, String>),

    /// Все способы входа сходятся сюда токеном сессии.
    LoggedIn(Result<String, String>),
    /// Гостевой пропуск: «Обзор» и «Главная» читаются без токена.
    GuestSkip,

    // ── Навигация ──────────────────────────────────────────────────────────
    Nav(Screen),
    Back,
    Logout,

    // ── «Главная» ──────────────────────────────────────────────────────────
    FeedCategory(FeedCategory),
    FeedPage(usize),
    FeedLoaded(Result<Pageable<FeedItem>, String>),

    // ── «Обзор» ────────────────────────────────────────────────────────────
    BrowseTab(BrowseTab),
    BrowseSort(i32),
    BrowsePage(usize),
    SearchQuery(String),
    SearchSubmit,
    SearchLoaded(Result<BrowsePage, String>),

    // ── «Закладки» ─────────────────────────────────────────────────────────
    BookmarkTab(BookmarkTab),
    BookmarkPage(usize),
    BookmarkLoaded(Result<BookmarkPage, String>),

    // ── «Профиль» ──────────────────────────────────────────────────────────
    ProfileLoaded(Result<ProfileSnapshot, String>),

    // ── Релиз ──────────────────────────────────────────────────────────────
    OpenRelease(i64),
    ReleaseLoaded(Result<Box<Release>, String>),
    ReleaseBack,
    TypesLoaded(Result<Vec<EpisodeType>, String>),
    SelectType(usize),
    SourcesLoaded(Result<Vec<Source>, String>),
    SelectSource(usize),
    EpisodesLoaded(Result<Vec<Episode>, String>),
    PlayEpisode(usize),
    PlaybackResolved(Result<ParsedVideo, String>),
    /// Запись просмотренной серии в историю — фоновая, её отказ не мешает смотреть.
    HistoryRecorded(Result<(), String>),
    ToggleFavorite(bool),
    FavoriteApplied(Result<(i64, bool), String>),
    SetWatchStatus(WatchStatus),
    WatchStatusApplied(Result<(i64, WatchStatus), String>),

    // ── Коллекция ──────────────────────────────────────────────────────────
    OpenCollection(i64),
    CollectionLoaded(Result<Box<Collection>, String>),

    // ── Постеры ────────────────────────────────────────────────────────────
    /// Байты обложки для ключа кэша; `Err` — что сломалось в загрузке.
    PosterLoaded(String, Result<Vec<u8>, String>),
}

/// Состояние всего приложения.
#[derive(Debug, Default)]
pub struct App {
    pub token: Option<String>,
    pub screen: Screen,
    pub stack: Vec<Screen>,
    pub error: Option<String>,
    /// Информационная строка (восстановленная сессия, передача браузеру, …).
    pub status: Option<String>,
    pub login: Login,
    pub feed: Feed,
    pub browse: Browse,
    pub bookmarks: Bookmarks,
    pub profile: ProfileView,
    pub release: ReleaseView,
    /// Открытая коллекция: `/collection/{id}` приходит целиком со своими релизами.
    pub collection: Option<Box<Collection>>,
    pub collection_loading: bool,
    /// Декодированные обложки всех видимых экранов.
    pub posters: PosterCache,
    /// Где токен сессии переживает перезапуск.
    pub store: SessionStore,
}

/// `POST /search/releases/{page}` → сообщение «Обзора».
fn search_loaded(result: Result<ReleaseSearchResponse, String>) -> Message {
    Message::SearchLoaded(result.map(BrowsePage::Search))
}

/// Полка релизов (`/filter`, `/discover`, списки профиля) → сообщение «Обзора».
fn shelf_loaded(result: Result<Pageable<Release>, String>) -> Message {
    Message::SearchLoaded(result.map(BrowsePage::Shelf))
}

/// Таб «Закладок» с релизами → своё сообщение.
fn releases_loaded(result: Result<Pageable<Release>, String>) -> Message {
    Message::BookmarkLoaded(result.map(BookmarkPage::Releases))
}

/// Таб «Закладок» с коллекциями → своё сообщение.
fn collections_loaded(result: Result<Pageable<Collection>, String>) -> Message {
    Message::BookmarkLoaded(result.map(BookmarkPage::Collections))
}

/// Передача лучшей найденной ссылки внешнему процессу `mpv` с заголовками, которые
/// собрал решатель (Referer / User-Agent и прочее).
pub fn play_with_mpv(video: &ParsedVideo) -> Result<(), String> {
    let url = video
        .best_url()
        .ok_or_else(|| "Ни одной играбельной ссылки не найдено".to_owned())?;

    let mut command = Command::new("mpv");
    command.arg(url);
    for (name, value) in &video.headers {
        command.arg(format!("--http-header-fields={name}: {value}"));
    }

    command
        .spawn()
        .map_err(|e| format!("mpv не запустился: {e}"))?;

    Ok(())
}

/// Открытие URL в браузере пользователя — `xdg-open` (затем `gio` /
/// `sensible-browser`) на Linux, `open` на macOS, `cmd /C start` на Windows.
///
/// Если открывщика нет, ошибка содержит сам URL: OAuth-потокам нужен только
/// *итоговый* редирект, возвращённый в это окно, живой колбэк не требуется.
pub fn open_url(url: &str) -> Result<(), String> {
    if url.trim().is_empty() {
        return Err("Нечего открывать".to_owned());
    }

    let mut last_error = "не нашлось программы для открытия браузера".to_owned();
    for mut candidate in browser_commands(url) {
        candidate
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null());
        match candidate.spawn() {
            Ok(_) => return Ok(()),
            Err(error) => last_error = error.to_string(),
        }
    }
    Err(format!(
        "Не удалось открыть браузер ({last_error}).\nОткройте эту ссылку вручную: {url}"
    ))
}

/// Кандидаты открывщика браузера для текущей платформы, в порядке предпочтения.
fn browser_commands(url: &str) -> Vec<Command> {
    let mut candidates: Vec<Command> = Vec::new();

    #[cfg(target_os = "windows")]
    {
        let mut command = Command::new("cmd");
        command.args(["/C", "start", "", url]);
        candidates.push(command);
    }

    #[cfg(target_os = "macos")]
    {
        let mut command = Command::new("open");
        command.arg(url);
        candidates.push(command);
    }

    #[cfg(not(any(target_os = "windows", target_os = "macos")))]
    {
        let mut xdg = Command::new("xdg-open");
        xdg.arg(url);
        candidates.push(xdg);

        let mut gio = Command::new("gio");
        gio.args(["open", url]);
        candidates.push(gio);

        let mut sensible = Command::new("sensible-browser");
        sensible.arg(url);
        candidates.push(sensible);
    }

    candidates
}

impl App {
    /// Приложение с явным хранилищем сессии (тесты, переносные установки).
    pub fn with_store(store: SessionStore) -> Self {
        Self {
            store,
            ..Default::default()
        }
    }

    /// Начальное состояние: восстановленная сессия, если файл конфигурации её
    /// хранит, — и сразу «Главная».
    pub fn startup() -> (Self, Task<Message>) {
        let mut app = Self::with_store(SessionStore::from_env());
        let task = app.restore_session();
        (app, task)
    }

    /// Принимает сессию с диска, когда она там есть.
    pub fn restore_session(&mut self) -> Task<Message> {
        match self.store.load() {
            Some(session) => {
                self.status = Some(format!(
                    "Сессия {} восстановлена с диска — вход повторять не нужно",
                    session.method
                ));
                self.token = Some(session.token);
                self.mark_signed_in();
                self.screen = Screen::Feed;
                self.fetch_feed(0)
            }
            None => Task::none(),
        }
    }

    /// Тема окна: Material 3 Dark из [`crate::theme`].
    pub fn theme(&self) -> Theme {
        theme::theme()
    }

    /// Маршрут сообщения и, возможно, задача на его выполнение.
    pub fn update(&mut self, message: Message) -> Task<Message> {
        let route = self.route(message);
        // Обложки видимых экранов дозаявляются после каждого шага: данные могли
        // прийти, экран мог смениться, а `claim` сам решает, что тянуть не нужно.
        let artwork = self.refresh_posters();
        Task::batch([route, artwork])
    }

    /// Есть ли токен сессии — то есть доступны ли разделы профиля.
    pub fn signed_in(&self) -> bool {
        self.token.is_some()
    }

    /// Разметка «вошёл» для всех экранов, у которых есть гостевое состояние.
    fn mark_signed_in(&mut self) {
        self.bookmarks.signed_in = true;
        self.profile.signed_in = true;
        self.release.signed_in = true;
    }

    /// Маршрутизация одного сообщения без постер-веера.
    fn route(&mut self, message: Message) -> Task<Message> {
        match message {
            // ── Вход ───────────────────────────────────────────────────────
            Message::LoginTab(method) => {
                self.login.method = method;
                self.login.loading = false;
                self.error = None;
                Task::none()
            }
            Message::DismissStatus => {
                self.status = None;
                Task::none()
            }
            Message::DismissError => {
                self.error = None;
                Task::none()
            }
            Message::LoginUsername(value) => {
                self.login.username = value;
                Task::none()
            }
            Message::LoginPassword(value) => {
                self.login.password = value;
                Task::none()
            }
            Message::LoginSubmit => {
                if self.login.loading {
                    return Task::none();
                }
                if self.login.username.trim().is_empty() {
                    self.error = Some("Укажите логин или почту профиля".to_owned());
                    return Task::none();
                }

                self.login.loading = true;
                self.error = None;
                Task::perform(
                    net::login(self.login.username.clone(), self.login.password.clone()),
                    Message::LoggedIn,
                )
            }
            Message::LoginTelegramOpen => {
                let url = self.login.prepare_telegram();
                self.error = None;
                match open_url(&url) {
                    Ok(()) => self.status = Some(
                        "Telegram открыт в браузере: подтвердите вход и вставьте URL, \
                         на который браузер приземлится."
                            .to_owned(),
                    ),
                    Err(error) => self.error = Some(error),
                }
                Task::none()
            }
            Message::LoginTelegramPaste(value) => {
                self.login.telegram_paste = value;
                Task::none()
            }
            Message::LoginTelegramSubmit => self.submit_telegram(),
            Message::LoginVkOpen => {
                let url = self.login.prepare_vk();
                self.error = None;
                match open_url(&url) {
                    Ok(()) => self.status = Some(
                        "VK ID открыт в браузере: вставьте URL редиректа, на который \
                         он перенаправит после подтверждения."
                            .to_owned(),
                    ),
                    Err(error) => self.error = Some(error),
                }
                Task::none()
            }
            Message::LoginVkPaste(value) => {
                self.login.vk_paste = value;
                Task::none()
            }
            Message::LoginVkSubmit => self.submit_vk(),
            Message::LoginYandexOpen => {
                let url = self.login.prepare_yandex();
                self.error = None;
                match open_url(&url) {
                    Ok(()) => self.status = Some(
                        "Yandex ID открыт в браузере: редирект уходит на страницу \
                         приложения, поэтому скопируйте адресную строку после \
                         подтверждения — в ней будет access_token."
                            .to_owned(),
                    ),
                    Err(error) => self.error = Some(error),
                }
                Task::none()
            }
            Message::LoginYandexPaste(value) => {
                self.login.yandex_paste = value;
                Task::none()
            }
            Message::LoginYandexSubmit => self.submit_yandex(),
            Message::LoginGoogleBrowser => {
                if self.login.loading {
                    return Task::none();
                }
                self.error = None;
                self.login.loading = true;
                self.status = Some(
                    "Жду браузер: завершите вход Google, редирект поймается на \
                     127.0.0.1, и вход выполнится сам."
                        .to_owned(),
                );
                Task::perform(
                    net::sign_in_google_browser(
                        self.login.effective_google_client_id(),
                        self.login.effective_firebase_api_key(),
                    ),
                    Message::LoggedIn,
                )
            }
            Message::LoginGoogleClientId(value) => {
                self.login.google_client_id = value;
                Task::none()
            }
            Message::LoginFirebaseKey(value) => {
                self.login.firebase_api_key = value;
                Task::none()
            }
            Message::LoginGoogleRedirect(value) => {
                self.login.google_redirect = value;
                Task::none()
            }
            Message::LoginGoogleOpen => {
                self.error = None;
                let url = self.login.prepare_google();
                match open_url(&url) {
                    Ok(()) => self.status = Some(
                        "Google открыт в браузере (ручной режим): вставьте URL, на \
                         который он приземлится; голый токен Google меняется через \
                         Firebase сам."
                            .to_owned(),
                    ),
                    Err(error) => self.error = Some(error),
                }
                Task::none()
            }
            Message::LoginGooglePaste(value) => {
                self.login.google_paste = value;
                Task::none()
            }
            Message::LoginGoogleSubmit => self.submit_google(),
            Message::LoginTokenPaste(value) => {
                self.login.token = value;
                Task::none()
            }
            Message::LoginTokenSubmit => self.submit_token(),

            // ── Вход: буфер обмена («Вставить и войти») ────────────────────
            Message::LoginClipboard(target) => {
                if self.login.loading {
                    return Task::none();
                }
                self.error = None;
                // `iced::clipboard::read()` стартует только когда Task возвращён
                // рантайму, поэтому пустой случай решается следующим сообщением.
                iced::clipboard::read()
                    .map(move |contents| Message::ClipboardText(target, contents))
            }
            Message::ClipboardText(target, contents) => {
                match contents.filter(|text| !text.trim().is_empty()) {
                    Some(text) => self.paste_and_sign_in(target, text),
                    None => Task::perform(net::read_clipboard(), move |result| {
                        Message::ClipboardFallback(target, result)
                    }),
                }
            }
            Message::ClipboardFallback(target, result) => match result {
                Ok(text) => self.paste_and_sign_in(target, text),
                Err(error) => {
                    self.login.loading = false;
                    let label = target.method().label();
                    self.error = Some(format!("{label}: {error}"));
                    Task::none()
                }
            },
            Message::LoggedIn(Ok(token)) => self.accept_token(token),
            Message::LoggedIn(Err(error)) => {
                self.login.loading = false;
                self.error = Some(error);
                Task::none()
            }
            Message::GuestSkip => {
                // Гостевой сеанс: токена нет, поэтому библиотечные полки остаются
                // с гостевой подсказкой, а «Обзор» открывается каталогом — полкой,
                // которой не нужен ни токен, ни запрос.
                self.token = None;
                self.stack.clear();
                self.error = None;
                self.login.loading = false;
                self.bookmarks = Bookmarks::default();
                self.profile = ProfileView::default();
                self.release = ReleaseView::default();
                self.collection = None;
                self.collection_loading = false;
                self.posters.clear();
                self.screen = Screen::Browse;
                self.browse = Browse::default();
                self.browse.select(BrowseTab::Catalog);
                self.status = Some(
                    "Гостевой сеанс: «Главная» и «Обзор» читаются без входа, \
                     «Закладки» и «Профиль» откроются после него."
                        .to_owned(),
                );
                self.fetch_browse(0)
            }

            // ── Навигация ──────────────────────────────────────────────────
            Message::Nav(screen) => {
                self.push_screen(screen);
                self.error = None;
                match screen {
                    Screen::Feed
                        if self.feed.items.is_empty() && !self.feed.loading =>
                    {
                        let page = self.feed.page_for(self.feed.category);
                        self.fetch_feed(page)
                    }
                    Screen::Browse
                        if self.browse.releases.is_empty() && !self.browse.loading =>
                    {
                        self.fetch_browse(0)
                    }
                    Screen::Bookmarks
                        if self.bookmarks.is_empty() && !self.bookmarks.loading =>
                    {
                        self.fetch_bookmarks(0)
                    }
                    Screen::Profile
                        if self.profile.snapshot.is_none() && !self.profile.loading =>
                    {
                        self.fetch_profile()
                    }
                    _ => Task::none(),
                }
            }
            Message::Back => {
                self.pop_screen();
                Task::none()
            }
            Message::Logout => {
                let _ = self.store.clear();
                let store = self.store.clone();
                *self = Self::with_store(store);
                Task::none()
            }

            // ── «Главная» ──────────────────────────────────────────────────
            Message::FeedCategory(category) => {
                let page = self.feed.page_for(category);
                self.feed.category = category;
                self.error = None;
                self.fetch_feed(page)
            }
            Message::FeedPage(page) => self.fetch_feed(page),
            Message::FeedLoaded(Ok(page)) => {
                self.feed.apply(page);
                Task::none()
            }
            Message::FeedLoaded(Err(error)) => {
                self.feed.loading = false;
                self.error = Some(error);
                Task::none()
            }

            // ── «Обзор» ────────────────────────────────────────────────────
            Message::BrowseTab(tab) => {
                self.browse.select(tab);
                self.error = None;
                self.fetch_browse(0)
            }
            Message::BrowseSort(sort) => {
                self.browse.sort = sort;
                self.error = None;
                self.fetch_browse(0)
            }
            Message::BrowsePage(page) => self.fetch_browse(page),
            Message::SearchQuery(query) => {
                self.browse.set_query(query);
                Task::none()
            }
            Message::SearchSubmit => self.fetch_browse(0),
            Message::SearchLoaded(Ok(page)) => {
                self.browse.apply(page);
                Task::none()
            }
            Message::SearchLoaded(Err(error)) => {
                self.browse.loading = false;
                self.error = Some(error);
                Task::none()
            }

            // ── «Закладки» ─────────────────────────────────────────────────
            Message::BookmarkTab(tab) => {
                self.bookmarks.select(tab);
                self.error = None;
                self.fetch_bookmarks(0)
            }
            Message::BookmarkPage(page) => self.fetch_bookmarks(page),
            Message::BookmarkLoaded(Ok(page)) => {
                // Ответ, пришедший после смены таба, не должен лечь под чужой
                // заголовок — он просто снимает флаг загрузки.
                if page.fits(self.bookmarks.tab) {
                    self.bookmarks.apply(page);
                } else {
                    self.bookmarks.loading = false;
                }
                Task::none()
            }
            Message::BookmarkLoaded(Err(error)) => {
                self.bookmarks.loading = false;
                self.error = Some(error);
                Task::none()
            }

            // ── «Профиль» ──────────────────────────────────────────────────
            Message::ProfileLoaded(Ok(snapshot)) => {
                self.profile.snapshot = Some(snapshot);
                self.profile.loading = false;
                Task::none()
            }
            Message::ProfileLoaded(Err(error)) => {
                self.profile.loading = false;
                self.error = Some(error);
                Task::none()
            }

            // ── Релиз ──────────────────────────────────────────────────────
            Message::OpenRelease(id) => {
                self.push_screen(Screen::Release);
                self.error = None;
                self.release = ReleaseView {
                    loading: true,
                    signed_in: self.signed_in(),
                    ..Default::default()
                };
                Task::perform(net::release(self.token.clone(), id), Message::ReleaseLoaded)
            }
            Message::ReleaseLoaded(Ok(release)) => {
                let id = release.id;
                self.release.release = Some(*release);
                self.release.loading = false;
                self.release.signed_in = self.signed_in();
                self.release.loading_types = true;
                Task::perform(net::types(self.token.clone(), id), Message::TypesLoaded)
            }
            Message::ReleaseLoaded(Err(error)) => {
                self.release.loading = false;
                self.error = Some(error);
                Task::none()
            }
            Message::TypesLoaded(Ok(types)) => {
                self.release.types = types;
                self.release.loading_types = false;
                Task::none()
            }
            Message::TypesLoaded(Err(error)) => {
                self.release.loading_types = false;
                self.error = Some(error);
                Task::none()
            }
            Message::SelectType(index) => {
                let ids = (
                    self.release.release_id(),
                    self.release.types.get(index).map(|kind| kind.id),
                );
                let (Some(release_id), Some(type_id)) = ids else {
                    return Task::none();
                };

                self.release.selected_type = Some(index);
                self.release.selected_source = None;
                self.release.sources.clear();
                self.release.episodes.clear();
                self.release.playing = None;
                self.release.pending = None;
                self.release.loading_sources = true;

                Task::perform(
                    net::sources(self.token.clone(), release_id, type_id),
                    Message::SourcesLoaded,
                )
            }
            Message::SourcesLoaded(Ok(sources)) => {
                self.release.sources = sources;
                self.release.loading_sources = false;
                Task::none()
            }
            Message::SourcesLoaded(Err(error)) => {
                self.release.loading_sources = false;
                self.error = Some(error);
                Task::none()
            }
            Message::SelectSource(index) => {
                let type_id = self
                    .release
                    .selected_type
                    .and_then(|step| self.release.types.get(step))
                    .map(|kind| kind.id);
                let ids = (
                    self.release.release_id(),
                    type_id,
                    self.release.sources.get(index).map(|source| source.id),
                );
                let (Some(release_id), Some(type_id), Some(source_id)) = ids else {
                    return Task::none();
                };

                self.release.selected_source = Some(index);
                self.release.episodes.clear();
                self.release.playing = None;
                self.release.pending = None;
                self.release.loading_episodes = true;

                Task::perform(
                    net::episodes(self.token.clone(), release_id, type_id, source_id),
                    Message::EpisodesLoaded,
                )
            }
            Message::EpisodesLoaded(Ok(episodes)) => {
                self.release.episodes = episodes;
                self.release.loading_episodes = false;
                Task::none()
            }
            Message::EpisodesLoaded(Err(error)) => {
                self.release.loading_episodes = false;
                self.error = Some(error);
                Task::none()
            }
            Message::PlayEpisode(index) => {
                let url = self
                    .release
                    .episode(index)
                    .map(|episode| episode.url.trim().to_owned());
                let Some(url) = url.filter(|url| !url.is_empty()) else {
                    self.error = Some(
                        "У этой серии нет ссылки для воспроизведения — выберите другую \
                         озвучку или источник"
                            .to_owned(),
                    );
                    return Task::none();
                };

                self.release.pending = Some(index);
                self.release.playing = None;
                self.error = None;
                Task::perform(net::resolve(url), Message::PlaybackResolved)
            }
            Message::PlaybackResolved(Ok(video)) => {
                let pending = self.release.pending.take();
                match play_with_mpv(&video) {
                    Ok(()) => {
                        self.release.playing = pending;
                        self.error = None;
                    }
                    Err(error) => {
                        self.error = Some(error);
                        return Task::none();
                    }
                }

                // Серия сыграла — профиль запоминает её. Без токена записи нет, и
                // запрос не строится.
                let (Some(release_id), Some(source_id)) =
                    (self.release.release_id(), self.release.source_id())
                else {
                    return Task::none();
                };
                if !self.signed_in() {
                    return Task::none();
                }
                let position = pending
                    .and_then(|index| self.release.episode(index))
                    .map_or(0, |episode| episode.position.max(0));

                Task::perform(
                    net::history_add(self.token.clone(), release_id, source_id, position),
                    Message::HistoryRecorded,
                )
            }
            Message::PlaybackResolved(Err(error)) => {
                self.release.pending = None;
                self.error = Some(error);
                Task::none()
            }
            Message::HistoryRecorded(Ok(())) => Task::none(),
            Message::HistoryRecorded(Err(error)) => {
                // История — фоновая: просмотр не сломался, поэтому это подсказка,
                // а не ошибка.
                self.status = Some(format!("Серия не записана в историю: {error}"));
                Task::none()
            }
            Message::ToggleFavorite(favorite) => {
                let Some(release_id) = self.release.release_id() else {
                    return Task::none();
                };
                if !self.signed_in() || self.release.saving {
                    return Task::none();
                }

                self.release.saving = true;
                self.error = None;
                Task::perform(
                    net::set_favorite(self.token.clone(), release_id, favorite),
                    move |result| Message::FavoriteApplied(result.map(|_| (release_id, favorite))),
                )
            }
            Message::FavoriteApplied(Ok((release_id, favorite))) => {
                if self.release.release_id() == Some(release_id) {
                    self.release.apply_favorite(favorite);
                }
                Task::none()
            }
            Message::FavoriteApplied(Err(error)) => {
                self.release.saving = false;
                self.error = Some(error);
                Task::none()
            }
            Message::SetWatchStatus(status) => {
                let Some(release_id) = self.release.release_id() else {
                    return Task::none();
                };
                if !self.signed_in() || self.release.saving {
                    return Task::none();
                }

                self.release.saving = true;
                self.error = None;
                let token = self.token.clone();

                if status.is_unlisted() {
                    // У «Не смотрю» своего эндпоинта нет: релиз просто убирается из
                    // того списка, в котором он лежит сейчас.
                    let from = self.release.status();
                    Task::perform(
                        net::drop_watch_status(token, from, release_id),
                        move |result| {
                            Message::WatchStatusApplied(result.map(|_| (release_id, status)))
                        },
                    )
                } else {
                    Task::perform(
                        net::apply_watch_status(token, status, release_id),
                        move |result| {
                            Message::WatchStatusApplied(result.map(|_| (release_id, status)))
                        },
                    )
                }
            }
            Message::WatchStatusApplied(Ok((release_id, status))) => {
                if self.release.release_id() == Some(release_id) {
                    self.release.apply_status(status);
                }
                Task::none()
            }
            Message::WatchStatusApplied(Err(error)) => {
                self.release.saving = false;
                self.error = Some(error);
                Task::none()
            }
            Message::ReleaseBack => {
                // Спуск сворачивается по одной ступени, и только потом закрывается
                // сама страница.
                if !self.release.collapse() {
                    self.pop_screen();
                }
                Task::none()
            }

            // ── Коллекция ──────────────────────────────────────────────────
            Message::OpenCollection(id) => {
                self.push_screen(Screen::Collection);
                self.error = None;
                self.collection = None;
                self.collection_loading = true;
                Task::perform(net::collection(self.token.clone(), id), Message::CollectionLoaded)
            }
            Message::CollectionLoaded(Ok(collection)) => {
                self.collection = Some(collection);
                self.collection_loading = false;
                Task::none()
            }
            Message::CollectionLoaded(Err(error)) => {
                self.collection_loading = false;
                self.error = Some(error);
                Task::none()
            }

            // ── Постеры ────────────────────────────────────────────────────
            Message::PosterLoaded(key, bytes) => {
                // `record` сам решает, что делать: ответ о уже ушедшем объекте
                // игнорируется, битые байты садятся на заглушку навсегда.
                self.posters.record(&key, bytes.as_deref().ok());
                Task::none()
            }
        }
    }

    /// Текст буфера пишется в поле вкладки и тем же шагом отправляется на вход —
    /// в этом и смысл кнопки «Вставить и войти», заменяющей Ctrl+V, который
    /// iced 0.13 под Wayland не доставляет.
    fn paste_and_sign_in(&mut self, target: PasteTarget, text: String) -> Task<Message> {
        let text = text.trim().to_owned();
        *target.field(&mut self.login) = text;
        match target {
            PasteTarget::Telegram => self.submit_telegram(),
            PasteTarget::Vk => self.submit_vk(),
            PasteTarget::Yandex => self.submit_yandex(),
            PasteTarget::Google => self.submit_google(),
            PasteTarget::Token => self.submit_token(),
        }
    }

    /// Вход через Telegram: код редиректа меняется на `id_token` по PKCE-verifier'у
    /// этого окна, либо принимается готовый `id_token`.
    fn submit_telegram(&mut self) -> Task<Message> {
        if self.login.loading {
            return Task::none();
        }
        let credential = match self.login.telegram_credential() {
            Some(credential) => credential,
            None => {
                self.error = Some(
                    "Вставьте URL редиректа Telegram, его часть ?code=… или готовый \
                     id_token"
                        .to_owned(),
                );
                return Task::none();
            }
        };

        self.error = None;
        self.login.loading = true;
        match credential {
            TelegramPaste::Redirect(_) if self.login.telegram_verifier.is_empty() => {
                self.login.loading = false;
                self.error = Some(
                    "Сначала откройте вход Telegram: код обменивается по PKCE-verifier'у, \
                     который генерирует именно это действие"
                        .to_owned(),
                );
                Task::none()
            }
            TelegramPaste::Redirect(url) => {
                let verifier = self.login.telegram_verifier.clone();
                Task::perform(net::sign_in_telegram_redirect(url, verifier), Message::LoggedIn)
            }
            TelegramPaste::IdToken(token) => {
                Task::perform(net::sign_in_telegram_id_token(token), Message::LoggedIn)
            }
        }
    }

    /// Вход через VK ID: access token редиректа или выданный в другом клиенте.
    fn submit_vk(&mut self) -> Task<Message> {
        if self.login.loading {
            return Task::none();
        }
        match self.login.vk_credential() {
            Some(token) => {
                self.error = None;
                self.login.loading = true;
                Task::perform(net::sign_in_vk(token), Message::LoggedIn)
            }
            None => {
                self.error = Some(
                    "Вставьте URL редиректа VK ID (с access_token=…) или сам токен \
                     доступа со скоупом email"
                        .to_owned(),
                );
                Task::none()
            }
        }
    }

    /// Вход через Yandex ID: access token иммитуого редиректа или вставный напрямую.
    fn submit_yandex(&mut self) -> Task<Message> {
        if self.login.loading {
            return Task::none();
        }
        match self.login.yandex_credential() {
            Some(token) => {
                self.error = None;
                self.login.loading = true;
                Task::perform(net::sign_in_yandex(token), Message::LoggedIn)
            }
            None => {
                self.error = Some(
                    "Вставьте адресную строку после подтверждения в Yandex ID \
                     (access_token=…) или сам токен доступа"
                        .to_owned(),
                );
                Task::none()
            }
        }
    }

    /// Вход через Google: любой Google-форматный credencial идёт через автоматический
    /// обмен в Firebase.
    fn submit_google(&mut self) -> Task<Message> {
        if self.login.loading {
            return Task::none();
        }
        let pasted = self.login.google_paste.trim().to_owned();
        if pasted.is_empty() {
            self.error = Some(
                "Нажмите «Войти через Google (браузер)» для входа одним кликом либо \
                 вставьте URL редиректа (id_token=… / access_token=…) или токен"
                    .to_owned(),
            );
            return Task::none();
        }
        self.error = None;
        self.login.loading = true;
        Task::perform(
            net::sign_in_google_auto(pasted, self.login.effective_firebase_api_key()),
            Message::LoggedIn,
        )
    }

    /// Прямой токен сессии: принимается без единого запроса.
    fn submit_token(&mut self) -> Task<Message> {
        let token = self.login.token.trim().to_owned();
        if token.is_empty() {
            self.error = Some("Сначала вставьте токен сессии Anixart".to_owned());
            return Task::none();
        }
        self.error = None;
        self.accept_token(token)
    }

    /// Общий хвост любого входа: принять токен, сохранить его и открыть «Главную».
    fn accept_token(&mut self, token: String) -> Task<Message> {
        let method = self.login.method;
        self.login.loading = false;
        self.login.password.clear();
        self.token = Some(token.clone());
        self.stack.clear();
        self.error = None;
        self.screen = Screen::Feed;
        self.feed = Feed::default();
        self.browse = Browse::default();
        self.bookmarks = Bookmarks::default();
        self.profile = ProfileView::default();
        self.release = ReleaseView::default();
        self.collection = None;
        self.collection_loading = false;
        // Обложки прошлого аккаунта новому не принадлежат: кэш обнуляется целиком.
        self.posters.clear();
        self.mark_signed_in();

        match self.store.save(&token, method.as_str()) {
            Ok(()) => {
                let location = match self.store.path() {
                    Some(path) => path.display().to_string(),
                    None => "файл конфигурации".to_owned(),
                };
                self.status = Some(format!(
                    "Вход через {} выполнен — сессия сохранена в {location}",
                    method.label()
                ));
            }
            Err(error) => {
                self.error = Some(format!("Вход выполнен, но сессия не сохранена: {error}"));
            }
        }

        self.fetch_feed(0)
    }

    /// Одна страница «Главной» в текущем датированном окне.
    fn fetch_feed(&mut self, page: usize) -> Task<Message> {
        self.feed.page = page;
        self.feed.loading = true;
        let category = self.feed.category;
        Task::perform(
            net::feed_window(self.token.clone(), category, page as u32),
            Message::FeedLoaded,
        )
    }

    /// Текущая полка «Обзора». Пустой запрос на поисковой полке — не запрос:
    /// [`Browse::fetch_allowed`] держит `/search/releases` от мусора.
    fn fetch_browse(&mut self, page: usize) -> Task<Message> {
        if !self.browse.fetch_allowed() {
            self.browse.loading = false;
            return Task::none();
        }

        self.browse.page = page;
        self.browse.loading = true;
        let token = self.token.clone();
        let tab = self.browse.tab;

        match tab {
            BrowseTab::Search => {
                let query = self.browse.query.trim().to_owned();
                Task::perform(net::search(token, query, page as u32), search_loaded)
            }
            BrowseTab::Catalog => {
                let sort = self.browse.sort;
                Task::perform(net::filter_releases(token, page as u32, sort), shelf_loaded)
            }
            BrowseTab::Interesting | BrowseTab::Comments => {
                let Some(kind) = tab.discover() else {
                    return Task::none();
                };
                Task::perform(net::discover(token, kind), shelf_loaded)
            }
        }
    }

    /// Текущий таб «Закладок». Все восемь разделов требуют токена, поэтому гость
    /// получает подсказку экрана, а не запрос, который сервер заведомо отклонит.
    fn fetch_bookmarks(&mut self, page: usize) -> Task<Message> {
        self.bookmarks.signed_in = self.signed_in();
        if !self.bookmarks.fetch_allowed() {
            self.bookmarks.loading = false;
            return Task::none();
        }

        self.bookmarks.loading = true;
        let token = self.token.clone();

        if self.bookmarks.tab.is_collections() {
            Task::perform(
                net::bookmark_collections(token, page as u32),
                collections_loaded,
            )
        } else {
            let tab = self.bookmarks.tab;
            Task::perform(net::bookmark_releases(token, tab, page as u32), releases_loaded)
        }
    }

    /// «Профиль»: `/profile/info` плюс документ профиля по его `channel_id`.
    fn fetch_profile(&mut self) -> Task<Message> {
        self.profile.signed_in = self.signed_in();
        if !self.profile.signed_in {
            self.profile.loading = false;
            return Task::none();
        }

        self.profile.loading = true;
        Task::perform(net::profile_snapshot(self.token.clone()), Message::ProfileLoaded)
    }

    /// Дозаявка обложек видимого экрана.
    fn refresh_posters(&mut self) -> Task<Message> {
        let artwork = match self.screen {
            Screen::Login => Vec::new(),
            Screen::Feed => self.feed.artwork(),
            Screen::Browse => self.browse.artwork(),
            Screen::Bookmarks => self.bookmarks.artwork(),
            Screen::Profile => self.profile.artwork(),
            Screen::Release => self.release.artwork(),
            Screen::Collection => self.collection_artwork(),
        };

        let mut tasks = Vec::new();
        for (key, url) in artwork {
            if let PosterEffect::Fetch { key, url } = self.posters.claim(key, url.as_deref()) {
                tasks.push(Task::perform(net::poster_bytes(url), move |bytes| {
                    Message::PosterLoaded(key.clone(), bytes)
                }));
            }
        }
        Task::batch(tasks)
    }

    /// Обложки открытой коллекции: её собственная и все релизы внутри.
    fn collection_artwork(&self) -> Vec<(String, Option<String>)> {
        let Some(collection) = self.collection.as_deref() else {
            return Vec::new();
        };

        let mut artwork = vec![(
            posters::collection_key(collection.id),
            posters::cover_url(&collection.image),
        )];
        artwork.extend(
            collection
                .releases
                .iter()
                .map(|release| (posters::release_key(release.id), posters::cover_of(&release.image))),
        );
        artwork
    }

    fn push_screen(&mut self, screen: Screen) {
        if self.screen != screen {
            self.stack.push(self.screen);
        }
        self.screen = screen;
    }

    fn pop_screen(&mut self) {
        if let Some(previous) = self.stack.pop() {
            self.screen = previous;
        }
        // Закрытая коллекция больше не видима: её данные держатся только до
        // следующего открытия.
        if self.screen != Screen::Collection {
            self.collection = None;
            self.collection_loading = false;
        }
    }

    /// Баннер статуса и баннер ошибки — над содержимым любого экрана.
    fn alerts(&self) -> Option<Element<'_, Message>> {
        let mut column = Column::new().spacing(8);
        let mut any = false;

        if let Some(status) = &self.status {
            column = column.push(widgets::banner(
                status.clone(),
                widgets::Tone::Info,
                Message::DismissStatus,
            ));
            any = true;
        }
        if let Some(error) = &self.error {
            column = column.push(widgets::banner(
                error.clone(),
                widgets::Tone::Error,
                Message::DismissError,
            ));
            any = true;
        }

        // без сообщений зона статуса не создаётся вовсе
        if any {
            Some(column.padding([12, 20]).width(Length::Fill).into())
        } else {
            None
        }
    }

    /// Активный экран.
    fn screen_body(&self) -> Element<'_, Message> {
        let cache = &self.posters;
        match self.screen {
            Screen::Login => self.login.view(),
            Screen::Feed => self.feed.view(cache),
            Screen::Browse => self.browse.view(cache),
            Screen::Bookmarks => self.bookmarks.view(cache),
            Screen::Profile => self.profile.view(cache),
            Screen::Release => self.release.view(cache),
            Screen::Collection => self.collection_view(cache),
        }
    }

    /// Страница коллекции: карточка с обложкой и сетка её релизов.
    fn collection_view<'a>(&'a self, cache: &'a PosterCache) -> Element<'a, Message> {
        if self.collection_loading {
            return widgets::scroll_body(
                Column::new()
                    .spacing(12)
                    .push(widgets::busy("Загрузка коллекции…".to_owned())),
            );
        }

        let Some(collection) = self.collection.as_deref() else {
            return widgets::scroll_body(Column::new().spacing(12).push(widgets::empty_state(
                "Коллекция не загружена".to_owned(),
                "Откройте её снова в «Закладках»: /collection/{id} читается даже без \
                 входа, так что отказ здесь — обычно срыв одного запроса."
                    .to_owned(),
            )));
        };

        let key = posters::collection_key(collection.id);
        let title = if collection.title.trim().is_empty() {
            "Коллекция без названия".to_owned()
        } else {
            collection.title.trim().to_owned()
        };
        let cover = widgets::artwork(
            cache,
            &key,
            &title,
            widgets::HERO_POSTER_WIDTH,
            widgets::HERO_POSTER_HEIGHT,
        );

        let author = collection
            .creator
            .as_ref()
            .map(|creator| creator.login.trim())
            .filter(|login| !login.is_empty())
            .unwrap_or("Anixart");

        let mut chips = vec![
            author.to_owned(),
            util::count_label(
                collection.releases.len() as i64,
                "релиз",
                "релиза",
                "релизов",
            ),
            util::count_label(
                i64::from(collection.favorites_count),
                "лайк",
                "лайка",
                "лайков",
            ),
            widgets::comments_label(collection.comment_count),
            format!("Создана: {}", util::date_label(collection.creation_date)),
        ];
        if collection.is_private {
            chips.push("Приватная".to_owned());
        }

        let description = collection.description.trim();
        let mut facts = Column::new().spacing(8).width(Length::Fill);
        facts = facts.push(widgets::headline_row(title));
        if !description.is_empty() {
            facts = facts.push(widgets::paragraph(description.to_owned()));
        }
        facts = facts
            .push(widgets::chip_row(chips))
            .push(widgets::divider());

        let shelf = if collection.releases.is_empty() {
            widgets::empty_state(
                "В коллекции нет релизов".to_owned(),
                "Добавляйте релизы из «Обзора» — при следующем открытии список \
                 заполнится сам."
                    .to_owned(),
            )
        } else {
            widgets::poster_grid(cache, &collection.releases, widgets::GRID_COLUMNS)
        };

        let caption = format!(
            "Релизы коллекции · {}",
            util::count_label(
                collection.releases.len() as i64,
                "релиз",
                "релиза",
                "релизов"
            )
        );

        let body = Column::new()
            .spacing(12)
            .push(widgets::card(
                Row::new()
                    .spacing(16)
                    .align_y(alignment::Vertical::Top)
                    .push(cover)
                    .push(facts),
            ))
            .push(widgets::section(caption, shelf))
            .push(
                Row::new()
                    .spacing(10)
                    .align_y(alignment::Vertical::Center)
                    .push(widgets::action("Назад", Some(Message::Back)))
                    .push(horizontal_space()),
            );

        widgets::scroll_body(body)
    }

    /// Навигационная панель: четыре корневых раздела, возврат и выход.
    fn nav_rail(&self) -> Element<'_, Message> {
        let active = self.screen.root();

        let session = if self.signed_in() {
            "Сессия активна — библиотека профиля доступна"
        } else {
            "Гостевой сеанс — закладки и профиль закрыты"
        };

        let mut rail = Column::new()
            .spacing(6)
            .padding([16, 12])
            .width(Length::Fixed(RAIL_WIDTH));
        rail = rail.push(widgets::headline("Anixart".to_owned()));
        rail = rail.push(widgets::muted(session.to_owned()));
        rail = rail.push(widgets::divider());

        for screen in Screen::NAV {
            let selected = active == Some(screen);
            let style: fn(&Theme, button::Status) -> button::Style = if selected {
                theme::styles::rail_active
            } else {
                theme::styles::rail
            };

            rail = rail.push(
                button(widgets::button_label(screen.label(), 15))
                    .width(Length::Fill)
                    .padding([12, 14])
                    .style(style)
                    .on_press_maybe((!selected).then_some(Message::Nav(screen))),
            );
        }

        rail = rail.push(vertical_space());
        rail = rail.push(
            button(widgets::button_label("Назад", 14))
                .width(Length::Fill)
                .padding([10, 14])
                .style(theme::styles::ghost)
                .on_press_maybe((!self.stack.is_empty()).then_some(Message::Back)),
        );
        rail = rail.push(
            button(widgets::button_label("Выйти", 14))
                .width(Length::Fill)
                .padding([10, 14])
                .style(theme::styles::danger)
                .on_press_maybe(self.signed_in().then_some(Message::Logout)),
        );

        container(rail)
            .height(Length::Fill)
            .style(theme::styles::surface)
            .into()
    }

    /// Корень окна: панель слева (кроме входа), баннеры и активный экран справа.
    pub fn view(&self) -> Element<'_, Message> {
        let mut column = Column::new()
            .spacing(0)
            .width(Length::Fill)
            .height(Length::Fill);

        if let Some(alerts) = self.alerts() {
            column = column.push(alerts);
        }
        column = column.push(self.screen_body());

        if self.screen == Screen::Login {
            return column.into();
        }

        Row::new()
            .spacing(0)
            .push(self.nav_rail())
            .push(column)
            .into()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::posters::PosterState;

    fn articles(ids: &[i64]) -> Pageable<FeedItem> {
        Pageable {
            code: 1,
            content: ids
                .iter()
                .map(|id| FeedItem {
                    id: *id,
                    title: Some(format!("Статья {id}")),
                    image: Some(format!("https://cdn/anixart/{id}.jpg")),
                    ..Default::default()
                })
                .collect(),
            current_page: 0,
            total_count: ids.len() as i64,
            total_page_count: 3,
        }
    }

    fn releases(ids: &[i64]) -> Pageable<Release> {
        Pageable {
            code: 1,
            content: ids
                .iter()
                .map(|id| Release {
                    id: *id,
                    title_ru: Some(format!("Релиз {id}")),
                    image: Some(format!("https://cdn/anixart/{id}.jpg")),
                    ..Default::default()
                })
                .collect(),
            current_page: 1,
            total_count: ids.len() as i64,
            total_page_count: 4,
        }
    }

    #[test]
    fn nav_labels_are_russian_and_cover_the_four_sections() {
        let labels: Vec<&str> = Screen::NAV.iter().map(|screen| screen.label()).collect();
        assert_eq!(labels, ["Главная", "Обзор", "Закладки", "Профиль"]);

        // у входа и вложенных экранов своей пилюли нет
        assert_eq!(Screen::Login.root(), None);
        assert_eq!(Screen::Release.root(), None);
        assert_eq!(Screen::Collection.root(), None);
        assert_eq!(Screen::Browse.root(), Some(Screen::Browse));
    }

    #[test]
    fn a_restored_session_marks_the_library_available() {
        let mut app = App::with_store(SessionStore::disabled());
        assert!(!app.signed_in());
        assert_eq!(app.screen, Screen::Login);

        let _ = app.update(Message::LoggedIn(Ok("tok".to_owned())));
        assert!(app.signed_in());
        assert!(app.bookmarks.signed_in);
        assert!(app.profile.signed_in);
        assert!(app.release.signed_in);
        assert_eq!(app.screen, Screen::Feed);
        assert!(app.feed.loading, "«Главная» тянется сразу после входа");
    }

    #[test]
    fn guest_skip_lands_on_the_catalogue_and_fetches_nothing_privileged() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::GuestSkip);

        assert!(app.token.is_none());
        assert_eq!(app.screen, Screen::Browse);
        assert_eq!(app.browse.tab, BrowseTab::Catalog, "полка без запроса");
        assert!(app.browse.loading);
        assert!(!app.bookmarks.signed_in);
        assert!(!app.profile.signed_in);
        assert!(app.status.is_some());

        // Ни один библиотечный запрос не уходит: гость получает подсказку экрана.
        let _ = app.update(Message::Nav(Screen::Bookmarks));
        assert!(!app.bookmarks.loading);
        assert!(!app.bookmarks.signed_in);
        assert!(app.bookmarks.is_empty());

        let _ = app.update(Message::Nav(Screen::Profile));
        assert!(!app.profile.loading);
        assert!(app.profile.snapshot.is_none());

        // История и избранное тоже не трогаются: флаги saving не поднимаются.
        let _ = app.update(Message::ToggleFavorite(true));
        let _ = app.update(Message::SetWatchStatus(WatchStatus::Watching));
        assert!(!app.release.saving);
    }

    #[test]
    fn an_empty_search_query_never_reaches_the_endpoint() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::Nav(Screen::Browse));
        assert_eq!(app.browse.tab, BrowseTab::Search);
        assert!(!app.browse.loading, "пустой запрос не отправляется");

        let _ = app.update(Message::SearchQuery("  ".to_owned()));
        let _ = app.update(Message::SearchSubmit);
        assert!(!app.browse.loading);

        let _ = app.update(Message::SearchQuery("наруто".to_owned()));
        let _ = app.update(Message::SearchSubmit);
        assert!(app.browse.loading);
        assert_eq!(app.browse.page, 0);
    }

    #[test]
    fn a_fetched_page_starts_the_poster_fan_out_exactly_once() {
        let mut app = App::with_store(SessionStore::disabled());
        // Обложки дозаказываются только для видимого экрана, поэтому сначала «Главная».
        let _ = app.update(Message::Nav(Screen::Feed));
        let _ = app.update(Message::FeedLoaded(Ok(articles(&[1, 2]))));

        assert_eq!(app.feed.items.len(), 2);
        assert_eq!(app.posters.count_state(PosterState::Loading), 2);
        assert_eq!(app.posters.state(&posters::article_key(1)), PosterState::Loading);

        // Второй проход того же экрана ничего не дозаказывает.
        let _ = app.update(Message::DismissError);
        assert_eq!(app.posters.count_state(PosterState::Loading), 2);
        assert_eq!(app.posters.len(), 2);
    }

    #[test]
    fn a_broken_poster_settles_on_the_placeholder_without_retrying() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::Nav(Screen::Feed));
        let _ = app.update(Message::FeedLoaded(Ok(articles(&[5]))));

        let key = posters::article_key(5);
        let _ = app.update(Message::PosterLoaded(key.clone(), Err("403 forbidden".to_owned())));
        assert_eq!(app.posters.state(&key), PosterState::Failed);

        let _ = app.update(Message::Nav(Screen::Profile));
        let _ = app.update(Message::Nav(Screen::Feed));
        assert_eq!(
            app.posters.state(&key),
            PosterState::Failed,
            "битая обложка не уходит обратно в загрузку"
        );

        // Ответ о забытом ключе безвреден: кэш не растёт.
        let _ = app.update(Message::PosterLoaded("release:0".to_owned(), Ok(vec![1, 2, 3])));
        assert_eq!(app.posters.len(), 1);
    }

    #[test]
    fn article_without_a_picture_is_never_fetched() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::Nav(Screen::Feed));
        let page = Pageable {
            content: vec![
                FeedItem {
                    id: 1,
                    image: Some(String::new()),
                    ..Default::default()
                },
                FeedItem {
                    id: 2,
                    image: None,
                    ..Default::default()
                },
            ],
            total_page_count: 1,
            ..Default::default()
        };
        let _ = app.update(Message::FeedLoaded(Ok(page)));

        assert_eq!(app.posters.count_state(PosterState::Loading), 0);
        assert_eq!(app.posters.count_state(PosterState::Failed), 2);
    }

    #[test]
    fn watch_list_replies_move_only_the_release_they_came_for() {
        let mut app = App::with_store(SessionStore::disabled());
        app.token = Some("tok".to_owned());
        let _ = app.update(Message::OpenRelease(42));
        assert_eq!(app.screen, Screen::Release);
        assert!(app.release.loading);

        let release = Release {
            id: 42,
            title_ru: Some("Наруто".to_owned()),
            profile_list_status: WatchStatus::Plan.code(),
            ..Default::default()
        };
        let _ = app.update(Message::ReleaseLoaded(Ok(Box::new(release))));
        assert_eq!(app.release.status(), WatchStatus::Plan);

        let _ = app.update(Message::SetWatchStatus(WatchStatus::Completed));
        assert!(app.release.saving);
        let _ = app.update(Message::WatchStatusApplied(Ok((
            42,
            WatchStatus::Completed,
        ))));
        assert_eq!(app.release.status(), WatchStatus::Completed);
        assert!(!app.release.saving);

        // запоздавший ответ о другом релизе не перекрашивает пилюли
        let _ = app.update(Message::SetWatchStatus(WatchStatus::HoldOn));
        let _ = app.update(Message::WatchStatusApplied(Ok((
            7,
            WatchStatus::Dropped,
        ))));
        assert_eq!(
            app.release.status(),
            WatchStatus::Completed,
            "чужой id не применяется: пилюли ждут ответа своего релиза"
        );
        assert!(app.release.saving);

        // отказ сервера возвращает пилюли в рабочее состояние
        let _ = app.update(Message::WatchStatusApplied(Err("Anixart вернул код 1".to_owned())));
        assert!(!app.release.saving);
        assert!(app.error.as_deref().unwrap().contains("код 1"));
    }

    #[test]
    fn favorite_toggle_follows_the_same_rule() {
        let mut app = App::with_store(SessionStore::disabled());
        app.token = Some("tok".to_owned());
        let _ = app.update(Message::OpenRelease(9));
        let _ = app.update(Message::ReleaseLoaded(Ok(Box::new(Release {
            id: 9,
            is_favorite: false,
            ..Default::default()
        }))));

        let _ = app.update(Message::ToggleFavorite(true));
        assert!(app.release.saving);
        let _ = app.update(Message::FavoriteApplied(Err("нет доступа".to_owned())));
        assert!(!app.release.saving);
        assert!(!app.release.release.as_ref().unwrap().is_favorite);

        let _ = app.update(Message::ToggleFavorite(true));
        let _ = app.update(Message::FavoriteApplied(Ok((9, true))));
        assert!(app.release.release.as_ref().unwrap().is_favorite);
        assert!(!app.release.saving);
    }

    #[test]
    fn the_release_back_button_winds_down_before_closing() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::Nav(Screen::Browse));
        let _ = app.update(Message::OpenRelease(3));
        assert_eq!(app.screen, Screen::Release);

        app.release.selected_type = Some(0);
        app.release.selected_source = Some(0);
        app.release.episodes = vec![Episode {
            id: 1,
            ..Default::default()
        }];

        let _ = app.update(Message::ReleaseBack);
        assert_eq!(app.screen, Screen::Release, "сначала серии");
        assert!(app.release.episodes.is_empty());

        let _ = app.update(Message::ReleaseBack);
        assert_eq!(app.screen, Screen::Release, "потом озвучка");
        assert_eq!(app.release.selected_type, None);

        let _ = app.update(Message::ReleaseBack);
        assert_eq!(app.screen, Screen::Browse, "затем страница закрывается");
    }

    #[test]
    fn a_bookmark_page_that_arrived_late_is_dropped_not_applied() {
        let mut app = App::with_store(SessionStore::disabled());
        app.token = Some("tok".to_owned());
        let _ = app.update(Message::Nav(Screen::Bookmarks));
        let _ = app.update(Message::BookmarkTab(BookmarkTab::Favorites));
        assert_eq!(app.bookmarks.tab, BookmarkTab::Favorites);

        let page = BookmarkPage::Collections(Pageable {
            code: 1,
            content: vec![Collection {
                id: 4,
                title: "Коллекция".to_owned(),
                ..Default::default()
            }],
            current_page: 0,
            total_count: 1,
            total_page_count: 1,
        });
        let _ = app.update(Message::BookmarkLoaded(Ok(page)));
        assert!(app.bookmarks.collections.is_empty());
        assert!(!app.bookmarks.loading);

        let _ = app.update(Message::BookmarkLoaded(Ok(BookmarkPage::Releases(
            releases(&[1, 2, 3]),
        ))));
        assert_eq!(app.bookmarks.releases.len(), 3);
        assert_eq!(app.bookmarks.page, 1);
        assert_eq!(app.posters.count_state(PosterState::Loading), 3);
    }

    #[test]
    fn collection_page_builds_and_its_artwork_is_claimed() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::OpenCollection(4));
        assert_eq!(app.screen, Screen::Collection);
        assert!(app.collection_loading);
        let _ = app.view();

        let collection = Collection {
            id: 4,
            title: "  Зимнее  ".to_owned(),
            description: "  Тёплые вещи  ".to_owned(),
            image: "https://cdn/anixart/c4.jpg".to_owned(),
            releases: releases(&[1, 2]).content,
            ..Default::default()
        };
        let _ = app.update(Message::CollectionLoaded(Ok(Box::new(collection))));
        assert!(!app.collection_loading);

        // своя обложка плюс два релиза внутри
        assert_eq!(app.posters.count_state(PosterState::Loading), 3);
        let _ = app.view();

        let _ = app.update(Message::Back);
        assert!(app.collection.is_none(), "закрытая коллекция не держит данные");
    }

    #[test]
    fn failed_playback_leaves_the_episode_unplayed() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::OpenRelease(1));
        let _ = app.update(Message::ReleaseLoaded(Ok(Box::new(Release {
            id: 1,
            ..Default::default()
        }))));
        app.release.episodes = vec![Episode {
            id: 5,
            position: 5,
            url: String::new(),
            ..Default::default()
        }];

        let _ = app.update(Message::PlayEpisode(0));
        assert!(app.error.as_deref().unwrap().contains("нет ссылки"));
        assert_eq!(app.release.pending, None);
        assert_eq!(app.release.playing, None);

        let _ = app.update(Message::PlaybackResolved(Err("хост недоступен".to_owned())));
        assert_eq!(app.release.pending, None);
        assert!(app.error.as_deref().unwrap().contains("хост"));
    }

    #[test]
    fn a_failed_history_write_is_a_hint_and_never_an_error() {
        let mut app = App::with_store(SessionStore::disabled());

        let _ = app.update(Message::HistoryRecorded(Ok(())));
        assert!(app.status.is_none());
        assert!(app.error.is_none());

        // Серия сыграла, а профиль её не запомнил: это подсказка, потому что
        // просмотр не сломался. Гостевой сеанс до этой ветки не доходит вовсе —
        // `PlaybackResolved` строит запрос истории только при токене.
        let _ = app.update(Message::HistoryRecorded(Err("Anixart вернул код 1".to_owned())));
        assert!(app.status.as_deref().unwrap().contains("историю"));
        assert!(app.error.is_none(), "история — не ошибка просмотра");

        let _ = app.update(Message::DismissStatus);
        assert!(app.status.is_none());
    }

    #[test]
    fn alerts_and_the_rail_render_in_every_screen() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.view();

        app.status = Some("Подсказка".to_owned());
        app.error = Some("Отказ".to_owned());
        let _ = app.view();

        let _ = app.update(Message::DismissStatus);
        assert!(app.status.is_none());
        let _ = app.update(Message::DismissError);
        assert!(app.error.is_none());

        let _ = app.update(Message::GuestSkip);
        let _ = app.update(Message::FeedLoaded(Ok(articles(&[1, 2]))));
        let _ = app.update(Message::Nav(Screen::Feed));
        let _ = app.view();

        let _ = app.update(Message::Nav(Screen::Profile));
        let _ = app.update(Message::Nav(Screen::Bookmarks));
        let _ = app.view();
    }

    #[test]
    fn logging_out_wipes_the_session_and_the_posters() {
        let mut app = App::with_store(SessionStore::disabled());
        let _ = app.update(Message::LoggedIn(Ok("tok".to_owned())));
        let _ = app.update(Message::FeedLoaded(Ok(articles(&[1]))));
        assert!(!app.posters.is_empty());

        let _ = app.update(Message::Logout);
        assert!(app.token.is_none());
        assert_eq!(app.screen, Screen::Login);
        assert!(app.posters.is_empty(), "чужие обложки не переходят по аккаунтам");
        assert!(app.feed.items.is_empty());
        assert!(!app.bookmarks.signed_in);
    }
}
