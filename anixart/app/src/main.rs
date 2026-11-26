//! Anixart Linux client: iced UI with login, feed, search,
//! release page, episode/voiceover/source list, and mpv playback.

use iced::{button, executor, Application, Button, Column, Command, Element, Row, Settings, Text};
use std::process::Command as ProcessCommand;

mod feed;
mod login;
mod release;
mod search;

// ── Screen ──────────────────────────────────────────────────────────────────

#[derive(Debug, Clone, Copy, PartialEq)]
enum Screen {
    Login,
    Feed,
    Search,
    Release,
}

// ── Messages ────────────────────────────────────────────────────────────────

#[derive(Debug, Clone)]
pub enum Message {
    LoginUsername(String),
    LoginPassword(String),
    LoginGo,

    Nav(Screen),
    Logout,

    FeedPage(usize),

    SearchQuery(String),
    SearchGo,

    ReleaseSelected(i64),
    TypeSelected(usize),
    SourceSelected(usize),
    EpisodeSelected(usize),
    Play(EpisodeInfo),
    BackToRelease,
    BackToTypes,

    LoginOk(String),
    LoginFail(String),
    FeedOk(Vec<FeedItem>),
    FeedFail(String),
    SearchOk(ReleaseSearchResponse),
    SearchFail(String),
    ReleaseOk(Release),
    ReleaseFail(String),
    TypesOk(Vec<EpisodeType>),
    TypesFail(String),
    SourcesOk(Vec<Source>),
    SourcesFail(String),
    EpisodesOk(Vec<Episode>),
    EpisodesFail(String),
    PlaybackOk(ParsedVideo),
    PlaybackFail(String),

    Noop,
}

use anixart_core::{
    AnixartClient, Episode, EpisodeType, FeedItem, Release, ReleaseSearchResponse, Source,
};
use anixart_parsers::ParsedVideo;

#[derive(Debug, Clone)]
pub struct EpisodeInfo {
    pub url: String,
}

// ── Async helpers ───────────────────────────────────────────────────────────

async fn login_go(username: String, password: String) -> Result<String, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    client.login(&username, &password).await.map_err(|e| e.to_string())?;
    client.token().map(|t| t.to_string()).ok_or_else(|| "No token".to_string())
}

async fn feed_page(client: AnixartClient, page: usize) -> Result<Vec<FeedItem>, String> {
    client.feed(page as u32).await.map(|r| r.content).map_err(|e| e.to_string())
}

async fn search_for(client: AnixartClient, query: String, page: usize) -> Result<ReleaseSearchResponse, String> {
    client.search(&query, page as u32).await.map_err(|e| e.to_string())
}

async fn release_detail(client: AnixartClient, id: i64) -> Result<Release, String> {
    client.release(id, true).await
        .and_then(|r| r.release.ok_or_else(|| "Not found".to_string()))
        .map_err(|e| e.to_string())
}

async fn fetch_types(client: AnixartClient, id: i64) -> Result<Vec<EpisodeType>, String> {
    client.types(id).await.map(|r| r.types).map_err(|e| e.to_string())
}

async fn fetch_sources(client: AnixartClient, rid: i64, tid: i64) -> Result<Vec<Source>, String> {
    client.sources(rid, tid).await.map(|r| r.sources).map_err(|e| e.to_string())
}

async fn fetch_episodes(client: AnixartClient, rid: i64, tid: i64, sid: i64) -> Result<Vec<Episode>, String> {
    client.episodes(rid, tid, sid, 0).await.map(|r| r.episodes).map_err(|e| e.to_string())
}

async fn resolve_video_url(http: reqwest::Client, url: String) -> Result<ParsedVideo, String> {
    anixart_parsers::resolve_with_client(&http, &url).await.map_err(|e| e.to_string())
}

// ── App state ───────────────────────────────────────────────────────────────

struct AnixartApp {
    client: AnixartClient,
    http: reqwest::Client,
    screen: Screen,
    screen_stack: Vec<Screen>,

    login_form: login::LoginForm,
    feed_screen: feed::FeedScreen,
    search_screen: search::SearchScreen,
    release_screen: release::ReleaseScreen,

    // Persistent button states for nav bar
    nav_feed: button::State,
    nav_search: button::State,
    nav_logout: button::State,
}

impl Application for AnixartApp {
    type Executor = executor::Default;
    type Message = Message;
    type Flags = ();

    fn new(_flags: Self::Flags) -> (Self, Command<Self::Message>) {
        (
            Self {
                client: AnixartClient::new().expect("API client"),
                http: reqwest::Client::builder()
                    .timeout(std::time::Duration::from_secs(20))
                    .build()
                    .expect("HTTP client"),
                screen: Screen::Login,
                screen_stack: Vec::new(),
                login_form: login::LoginForm::new(),
                feed_screen: feed::FeedScreen::new(),
                search_screen: search::SearchScreen::new(),
                release_screen: release::ReleaseScreen::new(),
                nav_feed: button::State::new(),
                nav_search: button::State::new(),
                nav_logout: button::State::new(),
            },
            Command::none(),
        )
    }

    fn title(&self) -> String {
        "Anixart — Linux Client".into()
    }

    fn update(&mut self, msg: Self::Message) -> Command<Self::Message> {
        match msg {
            // ── Login ──────────────────────────────────────────────────────
            Message::LoginUsername(v) => {
                self.login_form.set_username(v);
                Command::none()
            }
            Message::LoginPassword(v) => {
                self.login_form.set_password(v);
                Command::none()
            }
            Message::LoginGo => {
                let u = self.login_form.username.clone();
                let p = self.login_form.password.clone();
                self.login_form.loading = true;
                Command::perform(login_go(u, p), |r| match r {
                    Ok(t) => Message::LoginOk(t),
                    Err(e) => Message::LoginFail(e),
                })
            }
            Message::LoginOk(token) => {
                self.client.set_token(token);
                self.login_form.loading = false;
                self.screen = Screen::Feed;
                self.feed_screen.page = 0;
                let c = self.client.clone();
                Command::perform(feed_page(c, 0), |r| match r {
                    Ok(items) => Message::FeedOk(items),
                    Err(e) => Message::FeedFail(e),
                })
            }
            Message::LoginFail(e) => {
                self.login_form.loading = false;
                self.login_form.error = Some(e);
                Command::none()
            }

            // ── Navigation ─────────────────────────────────────────────────
            Message::Nav(s) => {
                if self.screen != s {
                    self.screen_stack.push(self.screen);
                }
                self.screen = s;
                Command::none()
            }
            Message::Logout => {
                self.client.clear_token();
                self.screen = Screen::Login;
                self.screen_stack.clear();
                Command::none()
            }

            // ── Feed ───────────────────────────────────────────────────────
            Message::FeedPage(page) => {
                self.feed_screen.page = page;
                let c = self.client.clone();
                Command::perform(feed_page(c, page), |r| match r {
                    Ok(items) => Message::FeedOk(items),
                    Err(e) => Message::FeedFail(e),
                })
            }
            Message::FeedOk(items) => {
                self.feed_screen.items = items;
                self.feed_screen.loading = false;
                self.feed_screen.error = None;
                Command::none()
            }
            Message::FeedFail(e) => {
                self.feed_screen.error = Some(e);
                self.feed_screen.loading = false;
                Command::none()
            }

            // ── Search ─────────────────────────────────────────────────────
            Message::SearchQuery(v) => {
                self.search_screen.query = v;
                Command::none()
            }
            Message::SearchGo => {
                let q = self.search_screen.query.clone();
                if q.trim().is_empty() {
                    return Command::none();
                }
                self.search_screen.page = 0;
                self.search_screen.loading = true;
                let c = self.client.clone();
                Command::perform(search_for(c, q, 0), |r| match r {
                    Ok(resp) => Message::SearchOk(resp),
                    Err(e) => Message::SearchFail(e),
                })
            }
            Message::SearchOk(resp) => {
                self.search_screen.results = resp.releases;
                self.search_screen.loading = false;
                self.search_screen.error = None;
                Command::none()
            }
            Message::SearchFail(e) => {
                self.search_screen.error = Some(e);
                self.search_screen.loading = false;
                Command::none()
            }

            // ── Release detail ─────────────────────────────────────────────
            Message::ReleaseSelected(id) => {
                self.screen = Screen::Release;
                self.release_screen = release::ReleaseScreen::new();
                let c = self.client.clone();
                Command::perform(release_detail(c, id), move |r| match r {
                    Ok(rel) => Message::ReleaseOk(rel),
                    Err(e) => Message::ReleaseFail(e),
                })
            }
            Message::ReleaseOk(rel) => {
                let id = rel.id;
                self.release_screen.release = Some(rel);
                let c = self.client.clone();
                Command::perform(fetch_types(c, id), |r| match r {
                    Ok(t) => Message::TypesOk(t),
                    Err(e) => Message::TypesFail(e),
                })
            }
            Message::ReleaseFail(e) => {
                self.release_screen.error = Some(e);
                Command::none()
            }
            Message::TypesOk(types) => {
                self.release_screen.types = types;
                self.release_screen.loading_types = false;
                Command::none()
            }
            Message::TypesFail(e) => {
                self.release_screen.error = Some(e);
                self.release_screen.loading_types = false;
                Command::none()
            }
            Message::TypeSelected(idx) => {
                if let Some(typ) = self.release_screen.types.get(idx) {
                    self.release_screen.selected_type_idx = Some(idx);
                    self.release_screen.selected_source_idx = None;
                    self.release_screen.sources.clear();
                    self.release_screen.episodes.clear();
                    if let Some(rid) = self.release_screen.release.as_ref().map(|r| r.id) {
                        let c = self.client.clone();
                        let tid = typ.id;
                        Command::perform(fetch_sources(c, rid, tid), move |r| match r {
                            Ok(s) => Message::SourcesOk(s),
                            Err(e) => Message::SourcesFail(e),
                        })
                    } else {
                        Command::none()
                    }
                } else {
                    Command::none()
                }
            }
            Message::SourcesOk(s) => {
                self.release_screen.sources = s;
                self.release_screen.loading_sources = false;
                Command::none()
            }
            Message::SourcesFail(e) => {
                self.release_screen.error = Some(e);
                self.release_screen.loading_sources = false;
                Command::none()
            }
            Message::SourceSelected(idx) => {
                if let Some(src) = self.release_screen.sources.get(idx) {
                    self.release_screen.selected_source_idx = Some(idx);
                    self.release_screen.episodes.clear();
                    if let (Some(rid), Some(tid)) = (
                        self.release_screen.release.as_ref().map(|r| r.id),
                        self.release_screen.types.get(self.release_screen.selected_type_idx.unwrap_or(0)).map(|t| t.id),
                    ) {
                        let c = self.client.clone();
                        let sid = src.id;
                        Command::perform(fetch_episodes(c, rid, tid, sid), move |r| match r {
                            Ok(eps) => Message::EpisodesOk(eps),
                            Err(e) => Message::EpisodesFail(e),
                        })
                    } else {
                        Command::none()
                    }
                } else {
                    Command::none()
                }
            }
            Message::EpisodesOk(eps) => {
                self.release_screen.episodes = eps;
                self.release_screen.loading_episodes = false;
                Command::none()
            }
            Message::EpisodesFail(e) => {
                self.release_screen.error = Some(e);
                self.release_screen.loading_episodes = false;
                Command::none()
            }
            Message::EpisodeSelected(idx) => {
                if let Some(ep) = self.release_screen.episodes.get(idx) {
                    let http = self.http.clone();
                    let url = ep.url.clone();
                    Command::perform(resolve_video_url(http, url), |r| match r {
                        Ok(pv) => Message::PlaybackOk(pv),
                        Err(e) => Message::PlaybackFail(e),
                    })
                } else {
                    Command::none()
                }
            }

            // ── Playback ───────────────────────────────────────────────────
            Message::Play(ep_info) => {
                let http = self.http.clone();
                Command::perform(resolve_video_url(http, ep_info.url), |r| match r {
                    Ok(pv) => Message::PlaybackOk(pv),
                    Err(e) => Message::PlaybackFail(e),
                })
            }
            Message::PlaybackOk(pv) => {
                if let Some(url) = pv.best_url() {
                    let mut cmd = ProcessCommand::new("mpv");
                    cmd.arg(url);
                    for (k, v) in &pv.headers {
                        cmd.arg(format!("--http-header-fields={}: {}", k, v));
                    }
                    match cmd.spawn() {
                        Ok(_) => println!("mpv spawned"),
                        Err(e) => self.release_screen.error = Some(format!("mpv: {e}")),
                    }
                }
                Command::none()
            }
            Message::PlaybackFail(e) => {
                self.release_screen.error = Some(e);
                Command::none()
            }

            // ── Back navigation ────────────────────────────────────────────
            Message::BackToRelease => {
                self.release_screen.selected_type_idx = None;
                self.release_screen.selected_source_idx = None;
                self.release_screen.sources.clear();
                self.release_screen.episodes.clear();
                Command::none()
            }
            Message::BackToTypes => {
                self.release_screen.selected_source_idx = None;
                self.release_screen.episodes.clear();
                Command::none()
            }

            Message::Noop => Command::none(),
        }
    }

    fn view(&mut self) -> Element<'_, Self::Message> {
        let nav_bar: Element<'_, Message> = if self.screen != Screen::Login {
            Row::new()
                .spacing(8)
                .push(
                    Button::new(&mut self.nav_feed, Text::new("Feed"))
                        .on_press(Message::Nav(Screen::Feed)),
                )
                .push(
                    Button::new(&mut self.nav_search, Text::new("Search"))
                        .on_press(Message::Nav(Screen::Search)),
                )
                .push(
                    Button::new(&mut self.nav_logout, Text::new("Logout"))
                        .on_press(Message::Logout),
                )
                .into()
        } else {
            Column::new().into()
        };

        let content: Element<'_, Message> = match self.screen {
            Screen::Login => self.login_form.view().map(|m| match m {
                login::Message::UsernameChanged(v) => Message::LoginUsername(v),
                login::Message::PasswordChanged(v) => Message::LoginPassword(v),
                login::Message::Login => Message::LoginGo,
            }),
            Screen::Feed => self.feed_screen.view().map(|m| match m {
                feed::Message::FeedPage(p) => Message::FeedPage(p),
                feed::Message::FeedItemSelected(_) => Message::Noop,
            }),
            Screen::Search => self.search_screen.view().map(|m| match m {
                search::Message::QueryChanged(v) => Message::SearchQuery(v),
                search::Message::Search => Message::SearchGo,
                search::Message::SetPage(_) => Message::Noop,
                search::Message::SelectResult(rel) => Message::ReleaseSelected(rel.id),
            }),
            Screen::Release => self.release_screen.view().map(|m| match m {
                release::Message::SelectType(i) => Message::TypeSelected(i),
                release::Message::SelectSource(i) => Message::SourceSelected(i),
                release::Message::SelectEpisode(i) => Message::EpisodeSelected(i),
                release::Message::PlayEpisode(ep) => {
                    Message::Play(EpisodeInfo { url: ep.url })
                }
                release::Message::BackToRelease => Message::BackToRelease,
                release::Message::BackToTypes => Message::BackToTypes,
                release::Message::BackToSources => Message::BackToTypes,
            }),
        };

        Column::new().push(nav_bar).push(content).into()
    }
}

// ── Main ────────────────────────────────────────────────────────────────────

fn main() -> iced::Result {
    AnixartApp::run(Settings {
        window: iced::window::Settings {
            size: (1024, 768),
            ..iced::window::Settings::default()
        },
        ..Settings::default()
    })
}
