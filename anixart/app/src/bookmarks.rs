//! «Закладки» — восемь разделов библиотеки вошедшего профиля, накопленных
//! прокруткой.
//!
//! Один экран, восемь табаков, три вида ответа: пять списков просмотра и избранное
//! приходят страницами [`anixart_core::Release`], история — тоже страницей
//! релизов, и только «Коллекции» несут [`anixart_core::Collection`].
//! [`BookmarkPage`] — это сумма, позволяющая одному
//! [`Message::BookmarkLoaded`](crate::app::Message::BookmarkLoaded) держать все
//! восемь разделов, так что таб, к которому принадлежит ответ, никогда не
//! угадывается постфактум.
//!
//! Кнопок перелистывания здесь нет: каждый таб — бесконечная полка, которая
//! дописывается новым ответом ([`Bookmarks::apply`], [`crate::lists::append_unique`])
//! и заканчивается на странице, не принёсшей ничего нового. Профильные
//! `profile/list/*`, `favorite/all` и `history/*` тоже отдают
//! `total_page_count = 0`, поэтому никакой «Стр. 3 из 12» тут быть не может.
//!
//! Все разделы — `require_token()` вызовы upstream, поэтому у экрана есть гостевое
//! состояние: без сессии он показывает подсказку, а приложение не отправляет
//! запрос, который сервер заведомо отклонит ([`Bookmarks::fetch_allowed`]).

use anixart_core::{Collection, Pageable, Release};
use iced::widget::{Column, Row};
use iced::Element;

use crate::app::Message;
use crate::lists::{append_unique, BookmarkTab, Paging};
use crate::posters::{self, PosterCache};
use crate::widgets;

/// Одна fetched страница любого таба.
#[derive(Debug, Clone)]
pub enum BookmarkPage {
    /// Релизы: избранное, история и пять списков просмотра.
    Releases(Pageable<Release>),
    /// Коллекции — единственный таб, в котором они живут.
    Collections(Pageable<Collection>),
}

impl BookmarkPage {
    /// Сколько объектов принёс ответ.
    pub fn len(&self) -> usize {
        match self {
            Self::Releases(page) => page.content.len(),
            Self::Collections(page) => page.content.len(),
        }
    }

    /// `len()` той половины, которую ответ действительно несёт.
    pub fn is_empty(&self) -> bool {
        match self {
            Self::Releases(page) => page.content.is_empty(),
            Self::Collections(page) => page.content.is_empty(),
        }
    }

    /// Пары `(ключ кэша, URL)` всего, что на странице.
    pub fn artwork(&self) -> Vec<(String, Option<String>)> {
        match self {
            Self::Releases(page) => page
                .content
                .iter()
                .map(|release| {
                    (
                        posters::release_key(release.id),
                        posters::cover_of(&release.image),
                    )
                })
                .collect(),
            Self::Collections(page) => page
                .content
                .iter()
                .map(|collection| {
                    (
                        posters::collection_key(collection.id),
                        posters::cover_url(&collection.image),
                    )
                })
                .collect(),
        }
    }

    /// Принадлежит ли ответ тому табу, который его просил: ответ, пришедший после
    /// смены таба, должен быть отброшен, а не показан под чужой подписью.
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

/// Состояние «Закладок»: две накопленные полки и геометрия прокрутки.
#[derive(Debug, Default)]
pub struct Bookmarks {
    pub tab: BookmarkTab,
    pub releases: Vec<Release>,
    pub collections: Vec<Collection>,
    pub paging: Paging,
    /// `false`, пока сеанс гостевой: ничего нельзя ни просить, ни показывать.
    pub signed_in: bool,
}

impl Bookmarks {
    /// Таб, который выбрал пользователь: раздел меняется, полка обнуляется,
    /// потому что чужая стена постеров под новой подписью читается как неверные
    /// данные.
    pub fn select(&mut self, tab: BookmarkTab) {
        if self.tab == tab {
            return;
        }
        self.tab = tab;
        self.releases.clear();
        self.collections.clear();
        self.paging.reset();
    }

    /// `false`, когда табу нужен сеанс, которого у этого приложения нет.
    pub fn fetch_allowed(&self) -> bool {
        self.signed_in || !self.tab.needs_token()
    }

    /// Номер страницы, которую надо просить следующей.
    pub fn next_page(&self) -> u32 {
        self.paging.next()
    }

    /// Можно ли догружать.
    pub fn can_load_more(&self) -> bool {
        self.paging.can_load_more()
    }

    /// Стартует первый запрос таба.
    pub fn begin(&mut self) {
        self.paging.begin();
    }

    /// Стартует догрузка. `false` — просить нечего.
    pub fn begin_more(&mut self) -> bool {
        self.paging.begin_more()
    }

    /// Кладёт ответ в полку текущего таба.
    ///
    /// Другая полка при этом не стирается: ответ принадлежит тому табу, который
    /// его запросил, а [`BookmarkPage::fits`] уже отбраковал чужие. Повторы
    /// (релиз мог лежать в двух запрошенных страницах) выбрасываются.
    pub fn apply(&mut self, page: BookmarkPage) {
        let added = match page {
            BookmarkPage::Releases(page) => {
                append_unique(&mut self.releases, page.content, |release| release.id)
            }
            BookmarkPage::Collections(page) => append_unique(
                &mut self.collections,
                page.content,
                |collection| collection.id,
            ),
        };
        self.paging.finish(added);
    }

    /// Отказ сервера: запрос снят, страница не засчитана.
    pub fn fail(&mut self) {
        self.paging.fail();
    }

    /// Обложки видимого раздела.
    pub fn artwork(&self) -> Vec<(String, Option<String>)> {
        if self.tab.is_collections() {
            self.collections
                .iter()
                .map(|collection| {
                    (
                        posters::collection_key(collection.id),
                        posters::cover_url(&collection.image),
                    )
                })
                .collect()
        } else {
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
    }

    /// Сколько объектов в текущем разделе.
    pub fn len(&self) -> usize {
        if self.tab.is_collections() {
            self.collections.len()
        } else {
            self.releases.len()
        }
    }

    /// Пуста ли полка, которая сейчас на экране.
    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }

    /// Что говорит пустое состояние этого таба, сформулированное как действие,
    /// которое его наполняет.
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
                tabs.wrap(),
            ));

        if !self.signed_in {
            body = body.push(widgets::empty_state(
                "Нужен вход в профиль".to_owned(),
                "Все разделы «Закладок» принадлежат профилю, поэтому гостю показывать \
                 нечего. Войдите или зарегистрируйтесь — список загрузится сам."
                    .to_owned(),
            ));
            return widgets::scroll_body(body);
        }

        if self.paging.loading {
            body = body.push(widgets::busy(format!("Загрузка: «{}»", self.tab)));
        }

        if self.is_empty() && !self.paging.loading {
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

        let footer = (!self.is_empty()).then(|| {
            widgets::shelf_tail(
                self.paging.loading_more,
                self.paging.exhausted,
                self.len(),
            )
        });

        widgets::scroll_infinite(body, footer, self.can_load_more() && !self.is_empty())
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
            current_page: 0,
            total_count: ids.len() as i64,
            // живой профиль не считает страниц: признак конца — пустой ответ
            total_page_count: 0,
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
                    image: format!("https://cdn/anixart/c{id}.jpg"),
                    creator: Some(Profile {
                        login: "neon".to_owned(),
                        ..Default::default()
                    }),
                    ..Default::default()
                })
                .collect(),
            current_page: 0,
            total_count: ids.len() as i64,
            total_page_count: 0,
        }
    }

    #[test]
    fn a_page_reports_its_own_geometry() {
        let page = BookmarkPage::Releases(releases(&[1, 2, 3]));
        assert_eq!(page.len(), 3);
        assert!(!page.is_empty());
        assert!(page.fits(BookmarkTab::Favorites));
        assert!(!page.fits(BookmarkTab::Collections));

        let page = BookmarkPage::Collections(collections(&[4, 5]));
        assert_eq!(page.len(), 2);
        assert!(page.fits(BookmarkTab::Collections));
        assert!(!page.fits(BookmarkTab::History));

        let empty = BookmarkPage::Releases(releases(&[]));
        assert!(empty.is_empty());
        assert_eq!(empty.len(), 0);

        // `From` не путает два вида ответа
        let page: BookmarkPage = releases(&[9]).into();
        assert!(matches!(page, BookmarkPage::Releases(_)));
        let page: BookmarkPage = collections(&[9]).into();
        assert!(matches!(page, BookmarkPage::Collections(_)));
    }

    #[test]
    fn artwork_is_namespaced_per_kind_of_object() {
        let releases = BookmarkPage::Releases(releases(&[12, 13]));
        let artwork = releases.artwork();
        assert_eq!(artwork.len(), 2);
        assert_eq!(artwork[0].0, posters::release_key(12));
        assert_eq!(artwork[0].1.as_deref(), Some("https://cdn/anixart/12.jpg"));

        let collections = BookmarkPage::Collections(collections(&[12]));
        let artwork = collections.artwork();
        assert_eq!(artwork.len(), 1);
        assert_eq!(artwork[0].0, posters::collection_key(12));
        assert_ne!(
            artwork[0].0,
            posters::release_key(12),
            "коллекция 12 и релиз 12 — разные объекты"
        );
    }

    #[test]
    fn a_collection_without_an_absolute_url_gets_no_fetch() {
        let mut collection = Collection {
            id: 3,
            title: "Коллекция".to_owned(),
            image: "/relative/only.jpg".to_owned(),
            ..Default::default()
        };
        let page = BookmarkPage::Collections(Pageable {
            content: vec![collection.clone()],
            ..Default::default()
        });
        assert_eq!(page.artwork()[0].1, None);

        collection.image = String::new();
        let page = BookmarkPage::Collections(Pageable {
            content: vec![collection],
            ..Default::default()
        });
        assert_eq!(page.artwork()[0].1, None);
    }

    #[test]
    fn switching_the_tab_resets_the_page_and_the_contents() {
        let mut bookmarks = Bookmarks {
            signed_in: true,
            ..Default::default()
        };
        bookmarks.begin();
        bookmarks.apply(BookmarkPage::Releases(releases(&[1, 2])));
        bookmarks.begin_more();
        bookmarks.apply(BookmarkPage::Releases(releases(&[3])));
        assert_eq!(bookmarks.releases.len(), 3);
        assert_eq!(bookmarks.next_page(), 2);

        bookmarks.select(BookmarkTab::Collections);
        assert!(bookmarks.releases.is_empty());
        assert!(bookmarks.collections.is_empty());
        assert_eq!(bookmarks.next_page(), 0);
        assert!(!bookmarks.paging.exhausted);
        assert!(!bookmarks.paging.loading);
        assert!(!bookmarks.can_load_more(), "первый запрос ещё не был");

        // та же пилюля дважды — не сброс
        bookmarks.begin();
        bookmarks.apply(BookmarkPage::Collections(collections(&[4])));
        bookmarks.select(BookmarkTab::Collections);
        assert_eq!(bookmarks.collections.len(), 1);
    }

    #[test]
    fn pages_append_and_a_repeat_of_the_last_one_ends_the_tab() {
        let mut bookmarks = Bookmarks {
            tab: BookmarkTab::History,
            signed_in: true,
            ..Default::default()
        };
        bookmarks.begin();
        bookmarks.apply(BookmarkPage::Releases(releases(&[1, 2])));
        assert!(bookmarks.begin_more());
        bookmarks.apply(BookmarkPage::Releases(releases(&[2, 3])));
        assert_eq!(
            bookmarks.releases.iter().map(|r| r.id).collect::<Vec<i64>>(),
            vec![1, 2, 3]
        );

        bookmarks.begin_more();
        bookmarks.apply(BookmarkPage::Releases(releases(&[])));
        assert!(bookmarks.paging.exhausted);
        assert!(!bookmarks.can_load_more());
        assert!(!bookmarks.begin_more());
        assert_eq!(bookmarks.releases.len(), 3, "пустой ответ ничего не стирает");
    }

    #[test]
    fn applying_a_page_touches_only_the_kind_it_carries() {
        let mut bookmarks = Bookmarks {
            tab: BookmarkTab::Collections,
            signed_in: true,
            ..Default::default()
        };
        // коллекции не затирают релизы, и наоборот: полки различаются по табу,
        // а `fits` уже отбраковывает чужие ответы
        bookmarks.begin();
        bookmarks.apply(BookmarkPage::Collections(collections(&[4, 5])));
        assert_eq!(bookmarks.collections.len(), 2);
        assert!(bookmarks.releases.is_empty());
        assert_eq!(bookmarks.len(), 2);
        assert!(!bookmarks.is_empty());

        bookmarks.begin_more();
        bookmarks.apply(BookmarkPage::Collections(collections(&[5, 6])));
        assert_eq!(
            bookmarks
                .collections
                .iter()
                .map(|c| c.id)
                .collect::<Vec<i64>>(),
            vec![4, 5, 6]
        );
    }

    #[test]
    fn a_failed_page_keeps_the_tab_and_its_accumulated_items() {
        let mut bookmarks = Bookmarks {
            signed_in: true,
            ..Default::default()
        };
        bookmarks.begin();
        bookmarks.apply(BookmarkPage::Releases(releases(&[1, 2])));
        assert!(bookmarks.begin_more());
        bookmarks.fail();

        assert_eq!(bookmarks.releases.len(), 2);
        assert_eq!(bookmarks.next_page(), 1);
        assert!(!bookmarks.paging.loading_more);
        assert!(bookmarks.can_load_more());
    }

    #[test]
    fn a_guest_is_never_allowed_to_fetch_a_section() {
        let bookmarks = Bookmarks::default();
        assert!(!bookmarks.signed_in);
        for tab in BookmarkTab::ALL {
            let tab_bookmarks = Bookmarks {
                tab,
                ..Default::default()
            };
            assert!(
                !tab_bookmarks.fetch_allowed(),
                "{tab} требует токен: гость не отправляет заведомый отказ"
            );
        }

        for tab in BookmarkTab::ALL {
            let signed = Bookmarks {
                tab,
                signed_in: true,
                ..Default::default()
            };
            assert!(signed.fetch_allowed(), "{tab}");
        }
    }

    #[test]
    fn every_tab_has_an_empty_state_that_is_not_the_other_tabs_message() {
        let mut bookmarks = Bookmarks {
            signed_in: true,
            ..Default::default()
        };
        for tab in BookmarkTab::ALL {
            bookmarks.tab = tab;
            let hint = bookmarks.empty_hint();
            assert!(!hint.is_empty(), "{tab}");
            match tab {
                BookmarkTab::Collections => assert!(hint.contains("коллекц"), "{tab}: {hint}"),
                BookmarkTab::History => assert!(hint.contains("историю"), "{tab}: {hint}"),
                BookmarkTab::Favorites => assert!(hint.contains("избранное"), "{tab}: {hint}"),
                other => assert!(
                    hint.contains("странице релиза"),
                    "{other}: {hint}"
                ),
            }
        }
        assert!(Bookmarks::default().is_empty());
    }

    #[test]
    fn the_history_and_favourite_tabs_keep_their_own_kind_of_answer() {
        for tab in [BookmarkTab::History, BookmarkTab::Favorites, BookmarkTab::Plan] {
            let mut bookmarks = Bookmarks {
                tab,
                signed_in: true,
                ..Default::default()
            };
            bookmarks.begin();
            bookmarks.apply(BookmarkPage::Releases(releases(&[1, 2])));
            assert_eq!(bookmarks.releases.len(), 2, "{tab}");
            assert!(bookmarks.collections.is_empty(), "{tab}");
            assert_eq!(bookmarks.len(), 2);
            assert_eq!(bookmarks.artwork()[0].0, posters::release_key(1));
        }
    }

    #[test]
    fn screens_build_headlessly_for_every_tab() {
        let cache = PosterCache::new();

        // гость: подсказка вместо данных, и никаких ног полки
        let guest = Bookmarks::default();
        let _ = guest.view(&cache);

        for tab in BookmarkTab::ALL {
            let mut bookmarks = Bookmarks {
                tab,
                signed_in: true,
                ..Default::default()
            };
            let _empty = bookmarks.view(&cache);

            bookmarks.paging.loading = true;
            let _busy = bookmarks.view(&cache);

            bookmarks.paging.loading = false;
            bookmarks.begin();
            bookmarks.apply(match tab {
                BookmarkTab::Collections => BookmarkPage::Collections(collections(&[1, 2])),
                _ => BookmarkPage::Releases(releases(&[1, 2, 3, 4, 5])),
            });
            let _first = bookmarks.view(&cache);

            bookmarks.begin_more();
            let _loading_more = bookmarks.view(&cache);

            bookmarks.apply(match tab {
                BookmarkTab::Collections => BookmarkPage::Collections(collections(&[])),
                _ => BookmarkPage::Releases(releases(&[])),
            });
            let _exhausted = bookmarks.view(&cache);
        }
    }
}
