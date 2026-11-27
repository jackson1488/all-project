//! «Обзор» — поиск, фильтр каталога и две алгоритмические полки в одном экране
//! с четырьмя пилюлями.
//!
//! Четыре полки держатся в одной машине состояний, а не в четырёх экранах: все
//! четыре отвечают релизами и все ведут на одну и ту же страницу релиза, поэтому
//! переключение пилюли меняет только запрос, никогда — раскладку. Поиск и каталог
//! paginated (`POST /search/releases/{page}`, `POST /filter/{page}`), а
//! `/discover/*` номера страницы не принимает вовсе — [`BrowseTab::is_paged`]
//! говорит, есть ли у полки куда догружать.
//!
//! Кнопок «Предыдущая» / «Следующая» здесь нет: смещение прокрутки приходит как
//! [`Message::Scrolled`](crate::app::Message::Scrolled), [`widgets::reached_end`]
//! решает, доехали ли до низа, и следующая страница дописывается к стене постеров
//! ([`Browse::apply`]). Полка, у которой нет второй страницы, заканчивается на
//! первой и больше не просит ничего.
//!
//! Ни один из четырёх эндпоинтов не `require_token()`, так что этот экран гость
//! может открыть сразу.

use anixart_core::{Pageable, Release, ReleaseSearchResponse};
use iced::widget::{text_input, Column, Row};
use iced::Element;

use crate::app::Message;
use crate::lists::{append_unique, catalog_sort_label, BrowseTab, CATALOG_SORTS, Paging};
use crate::posters::{self, PosterCache};
use crate::util;
use crate::widgets;

/// Одна fetched полка «Обзора»: поиск отвечает голым списком плюс похожая
/// категория, три остальные — страницей релизов.
#[derive(Debug, Clone)]
pub enum BrowsePage {
    Search(ReleaseSearchResponse),
    Shelf(Pageable<Release>),
}

impl BrowsePage {
    /// Релизы, которые ответ принёс.
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

    /// Похожая категория, которую backend предложил рядом с запросом.
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

/// Состояние «Обзора».
#[derive(Debug, Default)]
pub struct Browse {
    pub tab: BrowseTab,
    pub query: String,
    pub releases: Vec<Release>,
    pub paging: Paging,
    /// Sort id, с которым `POST /filter/{page}` спрашивается сейчас.
    pub sort: i32,
    /// Похожая категория из последнего ответа поиска.
    pub related: Option<String>,
}

impl Browse {
    /// Пилюля сменилась: полка обнуляется, потому что стена постеров от чужого
    /// запроса под новой подписью читается как неверные данные.
    pub fn select(&mut self, tab: BrowseTab) {
        if self.tab == tab {
            return;
        }
        self.tab = tab;
        self.releases.clear();
        self.paging.reset();
        self.related = None;
    }

    /// Новый запрос — новая первая страница.
    pub fn set_query(&mut self, query: String) {
        self.query = query;
    }

    /// `true`, когда текущую полку вообще можно просить.
    pub fn fetch_allowed(&self) -> bool {
        !self.tab.needs_query() || !self.query.trim().is_empty()
    }

    /// Номер страницы, которую надо просить следующей.
    pub fn next_page(&self) -> u32 {
        self.paging.next()
    }

    /// Можно ли догружать.
    pub fn can_load_more(&self) -> bool {
        self.paging.can_load_more()
    }

    /// Стартует первый запрос полки.
    pub fn begin(&mut self) {
        self.paging.begin();
    }

    /// Стартует догрузка. `false` — просить нечего.
    pub fn begin_more(&mut self) -> bool {
        self.paging.begin_more()
    }

    /// Кладёт ответ в полку: содержимое дописывается, повторы выбрасываются.
    ///
    /// Полки без номера страницы (`/discover/*`) заканчиваются на первом же
    /// ответе — второй запрос вернул бы тот же список, так что честнее не
    /// отправлять его вовсе.
    pub fn apply(&mut self, page: BrowsePage) {
        self.related = page.related().map(str::to_owned);
        let added = append_unique(&mut self.releases, page.releases().to_vec(), |release| {
            release.id
        });
        self.paging.finish(added);
        if !self.tab.is_paged() {
            self.paging.exhausted = true;
        }
    }

    /// Отказ сервера: запрос снят, страница не засчитана.
    pub fn fail(&mut self) {
        self.paging.fail();
    }

    /// Обложки видимой полки.
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

    /// Подпись пустой полки, сформулированная как действие, которое её наполняет.
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

    /// Заголовок полки: и в шапке экрана, и в строке загрузки.
    pub fn shelf_title(&self) -> String {
        let base = match self.tab {
            BrowseTab::Search if !self.query.trim().is_empty() => {
                format!("Поиск: «{}»", self.query.trim())
            }
            BrowseTab::Catalog => format!("Каталог · {}", catalog_sort_label(self.sort)),
            other => other.label().to_owned(),
        };
        format!(
            "{base} · {}",
            util::count_label(self.releases.len() as i64, "релиз", "релиза", "релизов")
        )
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
                if self.paging.loading { "Ищу…" } else { "Искать" },
                (!self.paging.loading).then_some(Message::SearchSubmit),
            );
            body = body.push(widgets::section(
                "Запрос".to_owned(),
                Row::new().spacing(10).push(input).push(action),
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
            body = body.push(widgets::section("Сортировка".to_owned(), sorts.wrap()));
        }

        if let Some(related) = &self.related {
            body = body.push(widgets::muted(format!(
                "Похожая категория: {related} — она открывается тем же запросом."
            )));
        }

        if self.paging.loading {
            body = body.push(widgets::busy(format!("Загрузка: «{}»", self.shelf_title())));
        }

        if self.releases.is_empty() && !self.paging.loading {
            body = body.push(widgets::empty_state(
                self.shelf_title(),
                self.empty_hint(),
            ));
        }

        if !self.releases.is_empty() {
            body = body.push(widgets::poster_grid(cache, &self.releases, widgets::GRID_COLUMNS));
        }

        let footer = (!self.releases.is_empty()).then(|| {
            widgets::shelf_tail(
                self.paging.loading_more,
                self.paging.exhausted,
                self.releases.len(),
            )
        });

        widgets::scroll_infinite(
            body,
            footer,
            self.can_load_more() && !self.releases.is_empty(),
        )
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

    fn shelf(ids: &[i64]) -> Pageable<Release> {
        Pageable {
            code: 1,
            content: ids.iter().copied().map(release).collect(),
            current_page: 0,
            total_count: ids.len() as i64,
            total_page_count: 0,
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
        assert_eq!(page.related(), Some("Сёнен"));
        assert!(!page.is_empty());

        // безымянная похожая категория — не подпись
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

        let empty = BrowsePage::Shelf(shelf(&[]));
        assert!(empty.is_empty());
        assert_eq!(empty.len(), 0);
    }

    #[test]
    fn switching_the_shelf_empties_the_previous_one() {
        let mut browse = Browse {
            tab: BrowseTab::Search,
            query: "наруто".to_owned(),
            ..Default::default()
        };
        browse.begin();
        browse.apply(BrowsePage::Search(search(&[1, 2])));
        assert_eq!(browse.releases.len(), 2);
        assert_eq!(browse.related.as_deref(), Some("Сёнен"));

        browse.select(BrowseTab::Catalog);
        assert!(browse.releases.is_empty());
        assert_eq!(browse.related, None);
        assert_eq!(browse.next_page(), 0);
        assert!(!browse.paging.loading);
        assert!(!browse.paging.exhausted);

        // та же пилюля дважды — не сброс
        browse.begin();
        browse.apply(BrowsePage::Shelf(shelf(&[7])));
        browse.select(BrowseTab::Catalog);
        assert_eq!(browse.releases.len(), 1);
    }

    #[test]
    fn pages_append_and_a_repeat_of_the_last_one_ends_the_shelf() {
        let mut browse = Browse {
            tab: BrowseTab::Catalog,
            ..Default::default()
        };
        browse.begin();
        browse.apply(BrowsePage::Shelf(shelf(&[1, 2])));
        assert!(browse.begin_more());
        browse.apply(BrowsePage::Shelf(shelf(&[2, 3])));
        assert_eq!(
            browse.releases.iter().map(|r| r.id).collect::<Vec<i64>>(),
            vec![1, 2, 3]
        );
        assert_eq!(browse.next_page(), 2);

        browse.begin_more();
        browse.apply(BrowsePage::Shelf(shelf(&[3])));
        assert!(browse.paging.exhausted);
        assert!(!browse.can_load_more());
    }

    #[test]
    fn an_unpaged_discovery_shelf_ends_after_its_single_answer() {
        for tab in [BrowseTab::Interesting, BrowseTab::Comments] {
            let mut browse = Browse { tab, ..Default::default() };
            browse.begin();
            browse.apply(BrowsePage::Shelf(shelf(&[1, 2, 3])));
            assert_eq!(browse.releases.len(), 3, "{tab}");
            assert!(
                browse.paging.exhausted,
                "{tab} не принимает номер страницы — догружать некуда"
            );
            assert!(!browse.can_load_more());
        }

        // каталог и поиск, наоборот, продолжают просить
        let mut catalog = Browse {
            tab: BrowseTab::Catalog,
            ..Default::default()
        };
        catalog.begin();
        catalog.apply(BrowsePage::Shelf(shelf(&[1, 2, 3])));
        assert!(!catalog.paging.exhausted);
        assert!(catalog.can_load_more());
    }

    #[test]
    fn only_the_search_shelf_needs_a_query() {
        let mut browse = Browse::default();
        assert_eq!(browse.tab, BrowseTab::Search);
        assert!(
            !browse.fetch_allowed(),
            "пустой запрос не должен уходить в /search/releases"
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
            assert!(browse.fetch_allowed(), "{tab} запроса не требует");
        }
    }

    #[test]
    fn a_failed_page_keeps_what_was_already_gathered() {
        let mut browse = Browse {
            tab: BrowseTab::Catalog,
            ..Default::default()
        };
        browse.begin();
        browse.apply(BrowsePage::Shelf(shelf(&[1, 2])));
        assert!(browse.begin_more());
        browse.fail();

        assert_eq!(browse.releases.len(), 2);
        assert_eq!(browse.next_page(), 1);
        assert!(!browse.paging.loading_more);
        assert!(browse.can_load_more());
    }

    #[test]
    fn artwork_is_one_entry_per_visible_release_with_a_release_key() {
        let mut browse = Browse {
            tab: BrowseTab::Catalog,
            ..Default::default()
        };
        browse.begin();
        browse.apply(BrowsePage::Shelf(shelf(&[1, 2, 3])));
        let artwork = browse.artwork();
        assert_eq!(artwork.len(), 3);
        assert_eq!(artwork[0].0, posters::release_key(1));
        assert_eq!(artwork[2].1.as_deref(), Some("https://cdn/anixart/3.jpg"));

        // пустой URL — не URL
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
        assert_eq!(browse.shelf_title(), "Поиск: «БЛЕАЧ» · 0 релизов");

        browse.query.clear();
        assert_eq!(browse.shelf_title(), "Поиск · 0 релизов");

        browse.tab = BrowseTab::Catalog;
        browse.sort = 1;
        assert_eq!(browse.shelf_title(), "Каталог · По оценке · 0 релизов");
        browse.sort = 77;
        assert_eq!(browse.shelf_title(), "Каталог · Сортировка · 0 релизов");

        browse.tab = BrowseTab::Comments;
        assert_eq!(browse.shelf_title(), "Обсуждаемое · 0 релизов");

        browse.begin();
        browse.apply(BrowsePage::Shelf(shelf(&[1, 2])));
        assert_eq!(browse.shelf_title(), "Обсуждаемое · 2 релиза");
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
                ..Default::default()
            };
            browse.paging.loading = true;
            let _ = browse.view(&cache);

            browse.paging.loading = false;
            let _ = browse.view(&cache);

            browse.begin();
            browse.apply(match tab {
                BrowseTab::Search => BrowsePage::Search(search(&[1, 2, 3])),
                _ => BrowsePage::Shelf(shelf(&[1, 2, 3, 4, 5, 6])),
            });
            let _first = browse.view(&cache);

            if browse.can_load_more() {
                browse.begin_more();
                let _more = browse.view(&cache);
                browse.apply(BrowsePage::Shelf(shelf(&[])));
            } else {
                browse.apply(BrowsePage::Shelf(shelf(&[])));
            }
            let _tail = browse.view(&cache);
        }
    }
}
