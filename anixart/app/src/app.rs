//! Application state, message routing, and screen composition.

use std::process::Command;

use anixart_core::{
    Episode, EpisodeType, FeedItem, Pageable, Release, ReleaseSearchResponse, Source,
};
use anixart_parsers::ParsedVideo;
use iced::widget::{button, horizontal_space, text, Column, Row};
use iced::{Element, Task, Theme};

use crate::feed::Feed;
use crate::login::Login;
use crate::net;
use crate::release::ReleaseView;
use crate::search::Search;

/// Screens of the client.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum Screen {
    #[default]
    Login,
    Feed,
    Search,
    Release,
}

/// Messages handled by [`App::update`].
#[derive(Debug, Clone)]
pub enum Message {
    LoginUsername(String),
    LoginPassword(String),
    LoginSubmit,
    LoggedIn(Result<String, String>),

    Nav(Screen),
    Back,
    Logout,
    DismissError,

    FeedPage(usize),
    FeedLoaded(Result<Pageable<FeedItem>, String>),

    SearchQuery(String),
    SearchSubmit,
    SearchPage(usize),
    SearchLoaded(Result<ReleaseSearchResponse, String>),

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
}

/// Whole application state.
#[derive(Debug, Default)]
pub struct App {
    pub token: Option<String>,
    pub screen: Screen,
    pub stack: Vec<Screen>,
    pub error: Option<String>,
    pub login: Login,
    pub feed: Feed,
    pub search: Search,
    pub release: ReleaseView,
}

/// Hands the best available stream to an external `mpv` process, forwarding the
/// headers the resolver collected (Referer / User-Agent and friends).
pub fn play_with_mpv(video: &ParsedVideo) -> Result<(), String> {
    let url = video
        .best_url()
        .ok_or_else(|| "No playable stream found".to_owned())?;

    let mut command = Command::new("mpv");
    command.arg(url);
    for (name, value) in &video.headers {
        command.arg(format!("--http-header-fields={name}: {value}"));
    }

    command
        .spawn()
        .map_err(|e| format!("mpv failed to start: {e}"))?;

    Ok(())
}

impl App {
    /// Initial state of the application.
    pub fn startup() -> (Self, Task<Message>) {
        (Self::default(), Task::none())
    }

    /// Routes a [`Message`], optionally returning an async task to run.
    pub fn update(&mut self, message: Message) -> Task<Message> {
        match message {
            // ── Login ──────────────────────────────────────────────────────
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
                    self.error = Some("Username is required".to_owned());
                    return Task::none();
                }

                self.login.loading = true;
                self.error = None;
                Task::perform(
                    net::login(self.login.username.clone(), self.login.password.clone()),
                    Message::LoggedIn,
                )
            }
            Message::LoggedIn(Ok(token)) => {
                self.login.loading = false;
                self.login.password.clear();
                self.error = None;
                self.token = Some(token);
                self.stack.clear();
                self.screen = Screen::Feed;
                self.feed = Default::default();
                self.fetch_feed(0)
            }
            Message::LoggedIn(Err(error)) => {
                self.login.loading = false;
                self.error = Some(error);
                Task::none()
            }

            // ── Navigation ─────────────────────────────────────────────────
            Message::Nav(screen) => {
                self.push_screen(screen);
                if screen == Screen::Feed && self.feed.items.is_empty() && !self.feed.loading {
                    self.fetch_feed(self.feed.page)
                } else {
                    Task::none()
                }
            }
            Message::Back => {
                self.pop_screen();
                Task::none()
            }
            Message::Logout => {
                *self = Default::default();
                Task::none()
            }
            Message::DismissError => {
                self.error = None;
                Task::none()
            }

            // ── Feed ───────────────────────────────────────────────────────
            Message::FeedPage(page) => self.fetch_feed(page),
            Message::FeedLoaded(Ok(page)) => {
                let total = page.total_page_count.max(0) as usize;
                self.feed.items = page.content;
                self.feed.total_pages = total;
                self.feed.loading = false;
                Task::none()
            }
            Message::FeedLoaded(Err(error)) => {
                self.feed.loading = false;
                self.error = Some(error);
                Task::none()
            }

            // ── Search ─────────────────────────────────────────────────────
            Message::SearchQuery(query) => {
                self.search.query = query;
                Task::none()
            }
            Message::SearchSubmit => self.fetch_search(0),
            Message::SearchPage(page) => self.fetch_search(page),
            Message::SearchLoaded(Ok(response)) => {
                self.search.results = response.releases;
                self.search.loading = false;
                Task::none()
            }
            Message::SearchLoaded(Err(error)) => {
                self.search.loading = false;
                self.error = Some(error);
                Task::none()
            }

            // ── Release page ───────────────────────────────────────────────
            Message::OpenRelease(id) => {
                self.push_screen(Screen::Release);
                self.error = None;
                self.release = ReleaseView {
                    loading: true,
                    ..Default::default()
                };
                Task::perform(net::release(self.token.clone(), id), Message::ReleaseLoaded)
            }
            Message::ReleaseLoaded(Ok(release)) => {
                let id = release.id;
                self.release.release = Some(*release);
                self.release.loading = false;
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
                let release_id = self.release.release.as_ref().map(|r| r.id);
                let type_id = self.release.types.get(index).map(|t| t.id);
                let (Some(release_id), Some(type_id)) = (release_id, type_id) else {
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
                let selected_type = self.release.selected_type;
                let release_id = self.release.release.as_ref().map(|r| r.id);
                let type_id = selected_type.and_then(|i| self.release.types.get(i)).map(|t| t.id);
                let source_id = self.release.sources.get(index).map(|s| s.id);
                let (Some(release_id), Some(type_id), Some(source_id)) =
                    (release_id, type_id, source_id)
                else {
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
                let url = self.release.episodes.get(index).map(|e| e.url.trim().to_owned());
                let Some(url) = url.filter(|u| !u.is_empty()) else {
                    self.error = Some("Episode has no playback URL".to_owned());
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
                    Err(error) => self.error = Some(error),
                }
                Task::none()
            }
            Message::PlaybackResolved(Err(error)) => {
                self.release.pending = None;
                self.error = Some(error);
                Task::none()
            }
            Message::ReleaseBack => {
                if self.release.selected_source.is_some() {
                    self.release.selected_source = None;
                    self.release.episodes.clear();
                    self.release.playing = None;
                    self.release.pending = None;
                } else if self.release.selected_type.is_some() {
                    self.release.selected_type = None;
                    self.release.sources.clear();
                    self.release.selected_source = None;
                    self.release.episodes.clear();
                } else {
                    self.pop_screen();
                }
                Task::none()
            }
        }
    }

    /// Root view: navigation bar, error banner, and the active screen.
    pub fn view(&self) -> Element<'_, Message> {
        let mut root = Column::new().spacing(12).padding(12).push(self.navbar());

        if let Some(error) = &self.error {
            let message = error.clone();
            root = root.push(
                Row::new()
                    .spacing(10)
                    .push(text(message).size(14).color([1.0, 0.45, 0.45]))
                    .push(horizontal_space())
                    .push(button("Dismiss").on_press(Message::DismissError)),
            );
        }

        root.push(match self.screen {
            Screen::Login => self.login.view(),
            Screen::Feed => self.feed.view(),
            Screen::Search => self.search.view(),
            Screen::Release => self.release.view(),
        })
        .into()
    }

    fn fetch_feed(&mut self, page: usize) -> Task<Message> {
        self.feed.page = page;
        self.feed.loading = true;
        Task::perform(net::feed(self.token.clone(), page as u32), Message::FeedLoaded)
    }

    fn fetch_search(&mut self, page: usize) -> Task<Message> {
        let query = self.search.query.trim().to_owned();
        if query.is_empty() {
            self.search.loading = false;
            return Task::none();
        }

        self.search.page = page;
        self.search.loading = true;
        Task::perform(
            net::search(self.token.clone(), query, page as u32),
            Message::SearchLoaded,
        )
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
    }

    fn navbar(&self) -> Element<'_, Message> {
        let current = self.screen;
        let brand = text("Anixart").size(22).color([0.45, 0.85, 1.0]);

        if current == Screen::Login {
            return Row::new().spacing(10).push(brand).into();
        }

        let primary: fn(&Theme, button::Status) -> button::Style = button::primary;
        let secondary: fn(&Theme, button::Status) -> button::Style = button::secondary;

        let feed_active = current == Screen::Feed;
        let search_active = current == Screen::Search;
        let release_active = current == Screen::Release;

        Row::new()
            .spacing(10)
            .push(brand)
            .push(horizontal_space())
            .push(
                button("Feed")
                    .style(if feed_active { primary } else { secondary })
                    .on_press_maybe((!feed_active).then_some(Message::Nav(Screen::Feed))),
            )
            .push(
                button("Search")
                    .style(if search_active { primary } else { secondary })
                    .on_press_maybe((!search_active).then_some(Message::Nav(Screen::Search))),
            )
            .push(
                button("Release")
                    .style(if release_active { primary } else { secondary })
                    .on_press_maybe((!release_active).then_some(Message::Nav(Screen::Release))),
            )
            .push(
                button("← Back").on_press_maybe((!self.stack.is_empty()).then_some(Message::Back)),
            )
            .push(button("Logout").style(button::danger).on_press(Message::Logout))
            .into()
    }
}
