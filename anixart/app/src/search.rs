//! «Обзор» — search, the catalogue filter and the two discovery shelves in one
//! screen with four pills.
//!
//! The four shelves are kept in one state machine rather than four screens,
//! because they all answer with releases and all lead to the same release page:
//! switching a pill changes only the request, never the layout. The search and
//! catalogue shelves are paginated (`POST /search/releases/{page}`,
//! `POST /filter/{page}`), while the discovery shelves (`POST /discover/*`) take
//! no page argument at all — [`BrowseTab::is_paged`] is what hides their pager.
//!
//! None of the four endpoints is `require_token()` upstream, so this screen is the
//! one a guest can open straight away.

use anixart_core::{Pageable, Release, ReleaseSearchResponse};
use iced::widget::{text_input, Column, Row};
use iced::Element;

use crate::app::Message;
use crate::lists::{catalog_sort_label, BrowseTab, CATALOG_SORTS};
use crate::posters::{self, PosterCache};
use crate::widgets;

/// One fetched shelf of the «Обзор» screen: the search endpoint answers with a
/// bare list plus a related genre, the other three with a page.
#[derive(Debug, Clone)]
pub enum BrowsePage {
    Search(ReleaseSearchResponse),
    Shelf(Pageable<Release>),
}

impl BrowsePage {
    /// Releases the answer carries.
    pub fn releases(&self) -> &[Release] {
        match self {
            Self::Search(response) => &response.releases,
            Self::Shelf(page) => &page.content,
        }
    }

    pub fn len(&self) -> usize {
        self.releases().len()
    }

    /// Пустой ли ответ: та же половина, что считает [`BrowsePage::len`].
    pub fn is_empty(&self) -> bool {
        self.releases().is_empty()
    }

    /// Page number the backend echoed, `None` for the answers that carry none —
    /// the search endpoint and the discovery shelves report no page cursor at all,
    /// so the page the request asked for is the only one that can be shown.
    pub fn page(&self) -> Option<usize> {
        match self {
            Self::Search(_) => None,
            Self::Shelf(page) => Some(page.current_page.max(0) as usize),
        }
    }

    /// Total pages, `0` for search (the endpoint does not count them) and for the
    /// unpaginated discovery shelves.
    pub fn total_pages(&self) -> usize {
        match self {
            Self::Search(_) => 0,
            Self::Shelf(page) => page.total_page_count.max(0) as usize,
        }
    }

    /// Genre the backend suggested next to the query, if it offered one.
    pub fn related(&self) -> Option<&str> {
        match self {
            Self::Search(response) => response
                .related
                .as_ref()
                .and_then(|related| related.name.as_deref())
                .map(str::trim)
                .filter(|name| !name.is_empty()),
            Self::Shelf(_) => None,
        }
    }
}

/// State of the «Обзор» screen.
#[derive(Debug, Default)]
pub struct Browse {
    pub tab: BrowseTab,
    pub query: String,
    pub releases: Vec<Release>,
    pub page: usize,
    pub total_pages: usize,
    pub loading: bool,
    /// Sort id `POST /filter/{page}` was asked with.
    pub sort: i32,
    /// Related genre the last search offered.
    pub related: Option<String>,
}

impl Browse {
    /// The pill row changed: the shelf is emptied, because a wall of posters from
    /// the previous request under a new caption reads as wrong data.
    pub fn select(&mut self, tab: BrowseTab) {
        if self.tab == tab {
            return;
        }
        self.tab = tab;
        self.page = 0;
        self.total_pages = 0;
        self.releases.clear();
        self.related = None;
        self.loading = false;
    }

    /// A new query means a new first page.
    pub fn set_query(&mut self, query: String) {
        self.query = query;
    }

    /// `true` when the current shelf can be fetched at all.
    pub fn fetch_allowed(&self) -> bool {
        !self.tab.needs_query() || !self.query.trim().is_empty()
    }

    /// Stores an answer for the current shelf.
    pub fn apply(&mut self, page: BrowsePage) {
        self.related = page.related().map(str::to_owned);
        self.total_pages = page.total_pages();
        // an answer without a page cursor leaves the requested page in place
        if let Some(current) = page.page() {
            self.page = current;
        }
        self.releases = page.releases().to_vec();
        self.loading = false;
    }

    /// Artwork of the visible shelf.
    pub fn artwork(&self) -> Vec<(String, Option<String>)> {
        self.releases
            .iter()
            .map(|release| {
                (
                    posters::release_key(release.id),
                    posters::cover_of(&release.image),
                )
            })
            .collect()
    }

    /// Caption of the empty state, phrased as the action that fills the shelf.
    pub fn empty_hint(&self) -> String {
        match self.tab {
            BrowseTab::Search if self.query.trim().is_empty() => {
                "Введите название, оригинальное имя или альтернативу — поиск идёт по \
                 трём полям каталога."
            }
            BrowseTab::Search => {
                "По этому запросу ничего не найдено: попробуйте короче или на \
                 оригинальном названии."
            }
            BrowseTab::Catalog => {
                "Каталог с такой сортировкой пуст — смените порядок на другой из ряда \
                 над списком."
            }
            other => {
                if other.discover().is_some() {
                    "Полки «Интересное» и «Обсуждаемое» наполняет сам Anixart: \
                     загляните сюда позже или откройте другую полку."
                } else {
                    "Здесь пока пусто."
                }
            }
        }
        .to_owned()
    }

    /// Title of the shelf, used both in the app bar and in the busy line.
    pub fn shelf_title(&self) -> String {
        match self.tab {
            BrowseTab::Search if !self.query.trim().is_empty() => {
                format!("Поиск: «{}»", self.query.trim())
            }
            BrowseTab::Catalog => format!("Каталог · {}", catalog_sort_label(self.sort)),
            other => other.label().to_owned(),
        }
    }

    pub fn view<'a>(&'a self, cache: &'a PosterCache) -> Element<'a, Message> {
        let pills = BrowseTab::ALL.iter().fold(Row::new().spacing(8), |row, tab| {
            row.push(widgets::pill(
                tab.label().to_owned(),
                *tab == self.tab,
                Message::BrowseTab(*tab),
            ))
        });

        let mut body = Column::new()
            .spacing(12)
            .push(widgets::section("Полки обзора".to_owned(), pills.wrap()));

        if self.tab.needs_query() {
            let input = text_input("Название, оригинал или альтернатива…", &self.query)
                .on_input(Message::SearchQuery)
                .on_submit(Message::SearchSubmit)
                .padding(10)
                .style(crate::theme::styles::input);
            let action = widgets::action(
                if self.loading {
                    "Ищу…"
                } else {
                    "Искать"
                },
                (!self.loading).then_some(Message::SearchSubmit),
            );
            body = body.push(widgets::section(
                "Запрос".to_owned(),
                Row::new()
                    .spacing(10)
                    .push(input)
                    .push(action),
            ));
        }

        if self.tab.has_sort() {
            let sorts = CATALOG_SORTS.iter().fold(Row::new().spacing(8), |row, (id, label)| {
                row.push(widgets::pill(
                    (*label).to_owned(),
                    *id == self.sort,
                    Message::BrowseSort(*id),
                ))
            });
            body = body.push(widgets::section(
                "Сортировка".to_owned(),
                sorts.wrap(),
            ));
        }

        if let Some(related) = &self.related {
            body = body.push(widgets::muted(format!(
                "Похожая категория: {related} — она открывается тем же запросом."
            )));
        }

        if self.loading {
            body = body.push(widgets::busy(format!(
                "Загрузка: «{}»",
                self.shelf_title()
            )));
        }

        if self.releases.is_empty() && !self.loading {
            body = body.push(widgets::empty_state(
                self.shelf_title(),
                self.empty_hint(),
            ));
        }

        if !self.releases.is_empty() {
            body = body
                .push(widgets::poster_grid(cache, &self.releases, widgets::GRID_COLUMNS));

            if self.tab.is_paged() {
                body = body.push(widgets::pager(
                    self.page,
                    self.total_pages,
                    Message::BrowsePage(self.page.saturating_sub(1)),
                    Message::BrowsePage(self.page + 1),
                ));
            }
        }

        widgets::scroll_body(body)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn release(id: i64) -> Release {
        Release {
            id,
            title_ru: Some(format!("Релиз {id}")),
            image: Some(format!("https://cdn/anixart/{id}.jpg")),
            ..Default::default()
        }
    }

    fn shelf(ids: &[i64], current: i32, total: i32) -> Pageable<Release> {
        Pageable {
            code: 1,
            content: ids.iter().copied().map(release).collect(),
            current_page: current,
            total_count: ids.len() as i64,
            total_page_count: total,
        }
    }

    fn search(ids: &[i64]) -> ReleaseSearchResponse {
        ReleaseSearchResponse {
            code: 1,
            releases: ids.iter().copied().map(release).collect(),
            related: Some(anixart_core::Related {
                id: 9,
                name: Some("Сёнен".to_owned()),
            }),
        }
    }

    #[test]
    fn a_search_answer_carries_its_related_genre_and_no_page_count() {
        let page = BrowsePage::Search(search(&[1, 2]));
        assert_eq!(page.len(), 2);
        assert_eq!(page.page(), None, "the search endpoint echoes no page cursor");
        assert_eq!(page.total_pages(), 0, "the endpoint does not count pages");
        assert_eq!(page.related(), Some("Сёнен"));

        // an unnamed related genre is not a caption
        let mut response = search(&[1]);
        response.related.as_mut().unwrap().name = None;
        assert_eq!(BrowsePage::Search(response).related(), None);

        let mut response = search(&[1]);
        response.related.as_mut().unwrap().name = Some("  ".to_owned());
        assert_eq!(BrowsePage::Search(response).related(), None);

        // `related` отсутствует целиком — ответ без него тоже не даёт подписи
        let mut response = search(&[1]);
        response.related = None;
        assert_eq!(BrowsePage::Search(response).related(), None);
    }

    #[test]
    fn a_shelf_answer_carries_its_geometry() {
        let page = BrowsePage::Shelf(shelf(&[3, 4, 5], 2, 9));
        assert_eq!(page.len(), 3);
        assert_eq!(page.page(), Some(2));
        assert_eq!(page.total_pages(), 9);
        assert_eq!(page.related(), None);
        assert!(!page.is_empty());

        let empty = BrowsePage::Shelf(shelf(&[], 0, -4));
        assert!(empty.is_empty());
        assert_eq!(empty.total_pages(), 0);
    }

    #[test]
    fn switching_the_shelf_empties_the_previous_one() {
        let mut browse = Browse {
            tab: BrowseTab::Search,
            query: "наруто".to_owned(),
            ..Default::default()
        };
        browse.apply(BrowsePage::Search(search(&[1, 2])));
        assert_eq!(browse.releases.len(), 2);
        assert_eq!(browse.related.as_deref(), Some("Сёнен"));

        browse.select(BrowseTab::Catalog);
        assert!(browse.releases.is_empty());
        assert_eq!(browse.related, None);
        assert_eq!((browse.page, browse.total_pages), (0, 0));
        assert!(!browse.loading);

        // the same pill twice is not a reset
        browse.apply(BrowsePage::Shelf(shelf(&[7], 1, 3)));
        browse.select(BrowseTab::Catalog);
        assert_eq!(browse.releases.len(), 1);
    }

    #[test]
    fn only_the_search_shelf_needs_a_query() {
        let mut browse = Browse::default();
        assert_eq!(browse.tab, BrowseTab::Search);
        assert!(
            !browse.fetch_allowed(),
            "an empty query must not go out to /search/releases"
        );
        browse.query = "  ".to_owned();
        assert!(!browse.fetch_allowed());
        browse.query = "один символ".to_owned();
        assert!(browse.fetch_allowed());

        for tab in [
            BrowseTab::Catalog,
            BrowseTab::Interesting,
            BrowseTab::Comments,
        ] {
            browse.tab = tab;
            browse.query.clear();
            assert!(browse.fetch_allowed(), "{tab} needs no query");
        }
    }

    #[test]
    fn artwork_is_one_entry_per_visible_release_with_a_release_key() {
        let mut browse = Browse {
            tab: BrowseTab::Catalog,
            ..Default::default()
        };
        browse.apply(BrowsePage::Shelf(shelf(&[1, 2, 3], 0, 1)));
        let artwork = browse.artwork();
        assert_eq!(artwork.len(), 3);
        assert_eq!(artwork[0].0, posters::release_key(1));
        assert_eq!(artwork[2].1.as_deref(), Some("https://cdn/anixart/3.jpg"));

        // an article and a release with the same numeric id stay apart
        browse.releases[0].image = Some(String::new());
        assert_eq!(browse.artwork()[0].1, None);
    }

    #[test]
    fn shelf_captions_name_the_query_and_the_sort() {
        let mut browse = Browse {
            tab: BrowseTab::Search,
            query: "  БЛЕАЧ  ".to_owned(),
            ..Default::default()
        };
        assert_eq!(browse.shelf_title(), "Поиск: «БЛЕАЧ»");

        browse.query.clear();
        assert_eq!(browse.shelf_title(), "Поиск");

        browse.tab = BrowseTab::Catalog;
        browse.sort = 1;
        assert_eq!(browse.shelf_title(), "Каталог · По оценке");
        browse.sort = 77;
        assert_eq!(browse.shelf_title(), "Каталог · Сортировка");

        browse.tab = BrowseTab::Comments;
        assert_eq!(browse.shelf_title(), "Обсуждаемое");
    }

    #[test]
    fn every_shelf_has_an_empty_state_that_says_what_to_do() {
        let mut browse = Browse {
            tab: BrowseTab::Search,
            ..Default::default()
        };
        assert!(browse.empty_hint().contains("Введите название"));
        browse.query = "нет такого".to_owned();
        assert!(browse.empty_hint().contains("не найдено"));

        browse.tab = BrowseTab::Catalog;
        assert!(browse.empty_hint().contains("сортировкой"));

        for tab in [BrowseTab::Interesting, BrowseTab::Comments] {
            browse.tab = tab;
            let hint = browse.empty_hint();
            assert!(hint.contains("Anixart"), "{tab}: {hint}");
        }
    }

    #[test]
    fn screens_build_headlessly_for_every_shelf_and_state() {
        let cache = PosterCache::new();
        for tab in BrowseTab::ALL {
            let mut browse = Browse {
                tab,
                query: "наруто".to_owned(),
                loading: true,
                ..Default::default()
            };
            let _ = browse.view(&cache);

            browse.loading = false;
            let _ = browse.view(&cache);

            browse.apply(match tab {
                BrowseTab::Search => BrowsePage::Search(search(&[1, 2, 3])),
                _ => BrowsePage::Shelf(shelf(&[1, 2, 3, 4, 5, 6], 0, 4)),
            });
            let _ = browse.view(&cache);
        }
    }
}
