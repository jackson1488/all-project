//! «Закладки» — the eight sections of the signed-in profile's library.
//!
//! One screen, eight tabs, three kinds of answer: the five watch lists and the
//! favourites come back as [`anixart_core::Release`] pages, the history is also a
//! release page, and the collections tab is the only one carrying
//! [`anixart_core::Collection`] values. [`BookmarkPage`] is the sum that lets a
//! single [`Message::BookmarkLoaded`](crate::app::Message::BookmarkLoaded) hold
//! all eight, so the tab a reply belongs to is never guessed after the fact.
//!
//! Every section is a `require_token()` endpoint upstream, which is why the screen
//! has a guest state: without a session it renders the sign-in prompt and the app
//! never issues the request ([`Bookmarks::fetch_allowed`]).

use anixart_core::{Collection, Pageable, Release};
use iced::widget::{Column, Row};
use iced::Element;

use crate::app::Message;
use crate::lists::BookmarkTab;
use crate::posters::{self, PosterCache};
use crate::widgets;

/// One fetched page of any tab.
#[derive(Debug, Clone)]
pub enum BookmarkPage {
    /// Releases: избранное, история and the five watch lists.
    Releases(Pageable<Release>),
    /// Collections, for the one tab that holds them.
    Collections(Pageable<Collection>),
}

impl BookmarkPage {
    /// The page number the backend echoed back.
    pub fn page(&self) -> usize {
        let current = match self {
            Self::Releases(page) => page.current_page,
            Self::Collections(page) => page.current_page,
        };
        current.max(0) as usize
    }

    /// Total pages the backend reported, `0` when it did not count them.
    pub fn total_pages(&self) -> usize {
        let total = match self {
            Self::Releases(page) => page.total_page_count,
            Self::Collections(page) => page.total_page_count,
        };
        total.max(0) as usize
    }

    /// How many objects the page carries.
    pub fn len(&self) -> usize {
        match self {
            Self::Releases(page) => page.content.len(),
            Self::Collections(page) => page.content.len(),
        }
    }

    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }

    /// `(cache key, image URL)` pairs of everything on the page.
    pub fn artwork(&self) -> Vec<(String, Option<String>)> {
        match self {
            Self::Releases(page) => page
                .content
                .iter()
                .map(|release| {
                    (
                        posters::release_key(release.id),
                        release.image.clone().filter(|url| !url.is_empty()),
                    )
                })
                .collect(),
            Self::Collections(page) => page
                .content
                .iter()
                .map(|collection| {
                    (
                        posters::collection_key(collection.id),
                        collection
                            .image
                            .clone()
                            .filter(|url| !url.is_empty() && url.starts_with("http")),
                    )
                })
                .collect(),
        }
    }

    /// Whether this answer belongs to the tab that asked for it — a reply that
    /// lands after the user switched tabs has to be dropped rather than shown
    /// under the wrong caption.
    pub fn fits(&self, tab: BookmarkTab) -> bool {
        match self {
            Self::Releases(_) => !tab.is_collections(),
            Self::Collections(_) => tab.is_collections(),
        }
    }
}

impl From<Pageable<Release>> for BookmarkPage {
    fn from(page: Pageable<Release>) -> Self {
        Self::Releases(page)
    }
}

impl From<Pageable<Collection>> for BookmarkPage {
    fn from(page: Pageable<Collection>) -> Self {
        Self::Collections(page)
    }
}

/// State of the «Закладки» screen.
#[derive(Debug, Default)]
pub struct Bookmarks {
    pub tab: BookmarkTab,
    pub releases: Vec<Release>,
    pub collections: Vec<Collection>,
    pub page: usize,
    pub total_pages: usize,
    pub loading: bool,
    /// `false` while the session is a guest one: nothing may be fetched or shown.
    pub signed_in: bool,
}

impl Bookmarks {
    /// The tab the user picked: the section changes, the page goes back to the
    /// first one and the previous contents are dropped, because a stale wall of
    /// posters under a new caption reads as wrong data.
    pub fn select(&mut self, tab: BookmarkTab) {
        if self.tab == tab {
            return;
        }
        self.tab = tab;
        self.page = 0;
        self.total_pages = 0;
        self.releases.clear();
        self.collections.clear();
        self.loading = false;
    }

    /// `false` when the tab needs a session this app does not have.
    pub fn fetch_allowed(&self) -> bool {
        self.signed_in || !self.tab.needs_token()
    }

    /// Stores a page that belongs to the current tab.
    pub fn apply(&mut self, page: BookmarkPage) {
        self.total_pages = page.total_pages();
        self.page = page.page();
        self.loading = false;
        match page {
            BookmarkPage::Releases(page) => {
                self.collections.clear();
                self.releases = page.content;
            }
            BookmarkPage::Collections(page) => {
                self.releases.clear();
                self.collections = page.content;
            }
        }
    }

    /// Artwork of the visible section.
    pub fn artwork(&self) -> Vec<(String, Option<String>)> {
        if self.tab.is_collections() {
            self.collections
                .iter()
                .map(|collection| {
                    (
                        posters::collection_key(collection.id),
                        collection
                            .image
                            .clone()
                            .filter(|url| !url.is_empty() && url.starts_with("http")),
                    )
                })
                .collect()
        } else {
            self.releases
                .iter()
                .map(|release| {
                    (
                        posters::release_key(release.id),
                        release.image.clone().filter(|url| !url.is_empty()),
                    )
                })
                .collect()
        }
    }

    /// How many objects the current section holds.
    pub fn len(&self) -> usize {
        if self.tab.is_collections() {
            self.collections.len()
        } else {
            self.releases.len()
        }
    }

    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }

    /// What the empty state says for this tab, phrased as the action that fills it.
    pub fn empty_hint(&self) -> String {
        match self.tab {
            BookmarkTab::Collections => {
                "Создавайте коллекции в официальном клиенте — здесь они появятся в этом \
                 списке и откроются релизами по клику."
            }
            BookmarkTab::History => {
                "Здесь появятся серии, которые вы включали. Откройте любую озвучку на \
                 странице релиза — эпизод запишется в историю."
            }
            BookmarkTab::Favorites => {
                "Нажмите «В избранное» на странице релиза, и он окажется в этом списке."
            }
            other => {
                if other.status() == Some(crate::lists::WatchStatus::Watching) {
                    "Статус «Смотрю» ставится на странице релиза — список пополнится здесь."
                } else {
                    "Релизы этого статуса показываются на странице релиза в ряду статусов."
                }
            }
        }
        .to_owned()
    }

    pub fn view<'a>(&'a self, cache: &'a PosterCache) -> Element<'a, Message> {
        let tabs = BookmarkTab::ALL.iter().fold(Row::new().spacing(8), |row, tab| {
            row.push(widgets::pill(
                tab.label().to_owned(),
                *tab == self.tab,
                Message::BookmarkTab(*tab),
            ))
        });

        let mut body = Column::new()
            .spacing(12)
            .push(widgets::section(
                "Разделы библиотеки".to_owned(),
                tabs.wrap().into(),
            ));

        if !self.signed_in {
            body = body.push(widgets::empty_state(
                "Нужен вход в профиль".to_owned(),
                "Все разделы «Закладок» принадлежат профилю, поэтому гостю показывать \
                 нечего. Войдите или зарегистрируйтесь — список загрузится сам.".to_owned(),
            ));
            return widgets::scroll_body(body);
        }

        if self.loading {
            body = body.push(widgets::busy(format!("Загрузка: «{}»", self.tab)));
        }

        if self.is_empty() && !self.loading {
            body = body.push(widgets::empty_state(
                format!("«{}» пуста", self.tab),
                self.empty_hint(),
            ));
        }

        if self.tab.is_collections() {
            for collection in &self.collections {
                body = body.push(widgets::collection_card(cache, collection));
            }
        } else if !self.releases.is_empty() {
            body = body.push(widgets::poster_grid(
                cache,
                &self.releases,
                widgets::GRID_COLUMNS,
            ));
        }

        if !self.is_empty() {
            body = body.push(widgets::pager(
                self.page,
                self.total_pages,
                Message::BookmarkPage(self.page.saturating_sub(1)),
                Message::BookmarkPage(self.page + 1),
            ));
        }

        widgets::scroll_body(body)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::lists::WatchStatus;
    use anixart_core::Profile;

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
            current_page: 2,
            total_count: ids.len() as i64,
            total_page_count: 7,
        }
    }

    fn collections(ids: &[i64]) -> Pageable<Collection> {
        Pageable {
            code: 1,
            content: ids
                .iter()
                .map(|id| Collection {
                    id: *id,
                    title: format!("Коллекция {id}"),
                    image: format!("https://cdn/anixart/c{int}.jpg", int = id),
                    creator: Some(Profile {
                        login: "neon".to_owned(),
                        ..Default::default()
                    }),
                    ..Default::default()
                })
                .collect(),
            current_page: 1,
            total_count: ids.len() as i64,
            total_page_count: 4,
        }
    }

    #[test]
    fn a_page_reports_its_own_geometry() {
        let page = BookmarkPage::from(releases(&[1, 2, 3]));
        assert_eq!(page.page(), 2);
        assert_eq!(page.total_pages(), 7);
        assert_eq!(page.len(), 3);
        assert!(!page.is_empty());
        assert!(page.fits(BookmarkTab::Favorites));
        assert!(!page.fits(BookmarkTab::Collections));

        let page = BookmarkPage::from(collections(&[4, 5]));
        assert_eq!(page.page(), 1);
        assert_eq!(page.total_pages(), 4);
        assert_eq!(page.len(), 2);
        assert!(page.fits(BookmarkTab::Collections));
        assert!(!page.fits(BookmarkTab::History));

        // a negative count from the API is never a slice length
        let empty = BookmarkPage::Releases(Pageable {
            code: 1,
            content: vec![],
            current_page: -3,
            total_count: 0,
            total_page_count: -9,
        });
        assert_eq!((empty.page(), empty.total_pages(), empty.len()), (0, 0, 0));
        assert!(empty.is_empty());
    }

    #[test]
    fn artwork_is_namespaced_per_kind_of_object() {
        let releases = BookmarkPage::from(releases(&[10, 11]));
        let keys: Vec<String> = releases
            .artwork()
            .into_iter()
            .map(|(key, _)| key)
            .collect();
        assert_eq!(keys, vec![posters::release_key(10), posters::release_key(11)]);

        let collections = BookmarkPage::from(collections(&[10, 11]));
        let keys: Vec<String> = collections
            .artwork()
            .into_iter()
            .map(|(key, _)| key)
            .collect();
        assert_eq!(
            keys,
            vec![
                posters::collection_key(10),
                posters::collection_key(11)
            ]
        );
        // the same numeric id stays two different posters
        assert_ne!(
            posters::release_key(10),
            posters::collection_key(10),
            "a release and a collection must not share a cache slot"
        );
    }

    #[test]
    fn a_collection_without_an_absolute_url_gets_no_fetch() {
        let mut page = collections(&[1]);
        page.content[0].image = "/relative/1.jpg".to_owned();
        assert_eq!(BookmarkPage::from(page).artwork()[0].1, None);

        let mut page = collections(&[1]);
        page.content[0].image = String::new();
        assert_eq!(BookmarkPage::from(page).artwork()[0].1, None);
    }

    #[test]
    fn switching_the_tab_resets_the_page_and_the_contents() {
        let mut bookmarks = Bookmarks {
            signed_in: true,
            ..Default::default()
        };
        bookmarks.apply(BookmarkPage::from(releases(&[1, 2])));
        assert_eq!(bookmarks.releases.len(), 2);
        assert_eq!(bookmarks.page, 2);

        bookmarks.select(BookmarkTab::Collections);
        assert_eq!(bookmarks.tab, BookmarkTab::Collections);
        assert!(bookmarks.releases.is_empty(), "the old list is gone");
        assert_eq!(bookmarks.page, 0);
        assert_eq!(bookmarks.total_pages, 0);
        assert!(!bookmarks.loading);

        // re-selecting the same tab is not a reset
        bookmarks.apply(BookmarkPage::from(collections(&[7])));
        bookmarks.select(BookmarkTab::Collections);
        assert_eq!(bookmarks.collections.len(), 1);
    }

    #[test]
    fn applying_a_collections_page_clears_the_release_list_and_vice_versa() {
        let mut bookmarks = Bookmarks {
            tab: BookmarkTab::Collections,
            signed_in: true,
            ..Default::default()
        };
        bookmarks.releases = releases(&[1, 2, 3]).content;
        bookmarks.apply(BookmarkPage::from(collections(&[9])));
        assert!(bookmarks.releases.is_empty());
        assert_eq!(bookmarks.collections.len(), 1);
        assert_eq!(bookmarks.total_pages, 4);
        assert!(!bookmarks.loading);
        assert_eq!(bookmarks.artwork().len(), 1);
        assert_eq!(bookmarks.len(), 1);

        bookmarks.tab = BookmarkTab::Watching;
        bookmarks.apply(BookmarkPage::from(releases(&[4, 5])));
        assert!(bookmarks.collections.is_empty());
        assert_eq!(bookmarks.artwork().len(), 2);
    }

    #[test]
    fn a_guest_is_never_allowed_to_fetch_a_section() {
        let guest = Bookmarks::default();
        assert!(!guest.signed_in);
        assert!(
            !guest.fetch_allowed(),
            "every tab is a require_token() endpoint upstream"
        );
        assert!(BookmarkTab::ALL.iter().all(|tab| tab.needs_token()));

        let member = Bookmarks {
            signed_in: true,
            ..Default::default()
        };
        assert!(member.fetch_allowed());
    }

    #[test]
    fn every_tab_has_an_empty_state_that_is_not_the_other_tabs_message() {
        let mut hints = Vec::new();
        for tab in BookmarkTab::ALL {
            let bookmarks = Bookmarks {
                tab,
                signed_in: true,
                ..Default::default()
            };
            let hint = bookmarks.empty_hint();
            assert!(!hint.is_empty(), "{tab}");
            assert!(hint.chars().count() > 30, "{tab}: {hint}");
            hints.push(hint);
        }
        let mut unique = hints.clone();
        unique.sort();
        unique.dedup();
        // collections / история / избранное each say something of their own
        assert!(unique.len() >= 3, "the hints are all the same sentence");
    }

    #[test]
    fn the_history_and_favourite_tabs_keep_their_own_kind_of_answer() {
        for tab in [
            BookmarkTab::History,
            BookmarkTab::Favorites,
            BookmarkTab::Watching,
            BookmarkTab::Dropped,
        ] {
            assert!(BookmarkPage::from(releases(&[1])).fits(tab), "{tab}");
            assert!(!BookmarkPage::from(collections(&[1])).fits(tab), "{tab}");
        }
        // and the status tabs really do carry a status
        assert_eq!(BookmarkTab::Watching.status(), Some(WatchStatus::Watching));
        assert_eq!(BookmarkTab::Collections.status(), None);
    }

    #[test]
    fn screens_build_headlessly_for_every_tab() {
        let cache = PosterCache::new();
        for tab in BookmarkTab::ALL {
            let mut bookmarks = Bookmarks {
                tab,
                signed_in: true,
                loading: true,
                ..Default::default()
            };
            let _ = bookmarks.view(&cache);

            if tab.is_collections() {
                bookmarks.apply(BookmarkPage::from(collections(&[1, 2])));
            } else {
                bookmarks.apply(BookmarkPage::from(releases(&[1, 2, 3, 4, 5])));
            }
            let _ = bookmarks.view(&cache);
        }
        // the guest view is the prompt, and it builds without touching the lists
        let _ = Bookmarks::default().view(&cache);
    }
}
