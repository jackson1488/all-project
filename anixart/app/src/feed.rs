//! «Главная» — семь полок каталога Anixart, накопленных прокруткой.
//!
//! Экран повторяет домашние вкладки официального клиента: «Последнее», «Онгоинги»,
//! «Анонсы», «Завершённые», «Фильмы», «Аниме», «Дунхуа». Каждая полка — это один
//! [`FilterRequest`], который строит [`FeedCategory::filter`], и один эндпоинт
//! `POST /filter/{page}`, так что переключение пилюли меняет только тело запроса,
//! никогда — раскладку. Релизы, а не статьи: постер, название, год, оценка, число
//! серий, статус и синопсис — всё, чем карточка отвечает на «что это такое», не
//! открывая её.
//!
//! Страниц здесь нет. Смещение прокрутки приходит как
//! [`Message::Scrolled`](crate::app::Message::Scrolled), [`widgets::reached_end`]
//! решает, доехали ли мы до низа, а новая страница *дописывается* к накопленному
//! списку ([`Feed::apply`], [`crate::lists::append_unique`]). Признак конца
//! выборки — страница, которая не принесла ни одного нового релиза:
//! `POST /filter/{page}` всегда отдаёт `total_page_count = 0`, поэтому
//! «Стр. 3 из 12» здесь было бы ложью.
//!
//! `/filter` не требует токена, так что все семь полок открыты и гостю: вход нужен
//! только «Закладкам» и «Профилю».

use anixart_core::{Pageable, Release};
use iced::widget::{Column, Row};
use iced::Element;

use crate::app::Message;
use crate::lists::{append_unique, FeedCategory, Paging};
use crate::posters::{self, PosterCache};
use crate::util;
use crate::widgets;

/// Состояние «Главной»: накопленный список релизов плюс геометрия прокрутки.
#[derive(Debug, Default)]
pub struct Feed {
    pub releases: Vec<Release>,
    pub category: FeedCategory,
    pub paging: Paging,
}

impl Feed {
    /// Полка меняется: список обнуляется, потому что стена постеров от чужого
    /// фильтра под новой подписью читается как неверные данные.
    pub fn select(&mut self, category: FeedCategory) {
        if self.category == category {
            return;
        }
        self.category = category;
        self.releases.clear();
        self.paging.reset();
    }

    /// Номер страницы, которую надо просить следующей.
    pub fn next_page(&self) -> u32 {
        self.paging.next()
    }

    /// Можно ли догружать: не в полёте, не конец, и первый ответ уже был.
    pub fn can_load_more(&self) -> bool {
        self.paging.can_load_more()
    }

    /// Стартует первый запрос полки.
    pub fn begin(&mut self) {
        self.paging.begin();
    }

    /// Стартует догрузка. `false` — просить нечего, флаг не поднимается.
    pub fn begin_more(&mut self) -> bool {
        self.paging.begin_more()
    }

    /// Кладёт ответ в полку. `page` — та самая страница, которую просили:
    /// содержимое дописывается, повторы (релиз мог вернуться и на следующей
    /// странице, если каталог обновился между запросами) выбрасываются.
    pub fn apply(&mut self, page: Pageable<Release>) {
        let added = append_unique(&mut self.releases, page.content, |release| release.id);
        self.paging.finish(added);
    }

    /// Отказ сервера: запрос снят, страница не засчитана.
    pub fn fail(&mut self) {
        self.paging.fail();
    }

    /// Полка ещё ни разу не ответила — стоит ли её вообще загружать.
    pub fn is_empty(&self) -> bool {
        self.releases.is_empty()
    }

    /// Обложки, которые нужны видимым релизам: `(ключ кэша, URL)` пары.
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

    /// Подпись пустой полки — действие, которое её наполняет.
    pub fn empty_hint(&self) -> String {
        format!(
            "В полке «{}» пока нечего показать: каталог обновляется сам, а \
             сортировка по обновлению догрузит релизы прокруткой вниз.",
            self.category
        )
    }

    /// Заголовок полки: название пилюли и сколько релизов уже накоплено.
    pub fn shelf_title(&self) -> String {
        format!(
            "«{}» · {}",
            self.category,
            util::count_label(self.releases.len() as i64, "релиз", "релиза", "релизов")
        )
    }

    /// Шапка полки: заголовок текущей пилюли и ряд самих пилюль.
    ///
    /// Она прикреплена к верху зоны и в прокрутку не уходит — переключиться на
    /// другую полку можно, не долистывая стену постеров обратно.
    fn header<'a>(&'a self) -> Element<'a, Message> {
        let pills = FeedCategory::ALL.iter().fold(Row::new().spacing(8), |row, category| {
            row.push(widgets::pill(
                category.label().to_owned(),
                *category == self.category,
                Message::FeedCategory(*category),
            ))
        });

        Column::new()
            .spacing(10)
            .push(widgets::subhead(self.shelf_title()))
            .push(widgets::section("Полки каталога".to_owned(), pills.wrap()))
            .into()
    }

    /// Тело полки под прикреплённой шапкой: состояние, сетка на `columns` колонок
    /// и нога, которая просит следующую страницу.
    fn shelf<'a>(&'a self, cache: &'a PosterCache, columns: usize) -> Column<'a, Message> {
        let mut body = Column::new().spacing(12);

        if self.paging.loading {
            body = body.push(widgets::busy(format!("Загрузка: «{}»", self.category)));
        }

        if self.releases.is_empty() && !self.paging.loading {
            body = body.push(widgets::empty_state(self.shelf_title(), self.empty_hint()));
        }

        if !self.releases.is_empty() {
            body = body.push(widgets::poster_grid(cache, &self.releases, columns));
            body = body.push(widgets::shelf_tail(
                self.paging.loading_more,
                self.paging.exhausted,
                self.releases.len(),
            ));
        }

        body
    }

    pub fn view<'a>(&'a self, cache: &'a PosterCache) -> Element<'a, Message> {
        let more = self.can_load_more() && !self.releases.is_empty();

        widgets::sticky_body(self.header(), more, move |size| {
            self.shelf(cache, widgets::grid_columns_for(size.width))
        })
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
            description: Some("Ниндзя и его команда".to_owned()),
            ..Default::default()
        }
    }

    fn shelf(ids: &[i64]) -> Pageable<Release> {
        Pageable {
            code: 0,
            content: ids.iter().copied().map(release).collect(),
            // живой каталог всегда отвечает нулями: на них и надо полагаться
            current_page: 0,
            total_count: 0,
            total_page_count: 0,
        }
    }

    #[test]
    fn a_first_page_starts_the_shelf_and_the_next_page_is_asked_for() {
        let mut feed = Feed::default();
        assert_eq!(feed.next_page(), 0);
        assert!(!feed.can_load_more(), "до первого ответа догружать нечего");

        feed.begin();
        assert!(feed.paging.loading);
        assert!(!feed.can_load_more());

        feed.apply(shelf(&[1, 2, 3]));
        assert_eq!(feed.releases.len(), 3);
        assert_eq!(feed.next_page(), 1);
        assert!(!feed.paging.loading);
        assert!(!feed.paging.exhausted);
        assert!(feed.can_load_more());
    }

    #[test]
    fn pages_append_instead_of_replacing_and_repeats_are_dropped() {
        let mut feed = Feed::default();
        feed.begin();
        feed.apply(shelf(&[1, 2]));
        feed.begin_more();
        feed.apply(shelf(&[2, 3, 4]));

        assert_eq!(
            feed.releases.iter().map(|release| release.id).collect::<Vec<i64>>(),
            vec![1, 2, 3, 4],
            "вторая страница дописывается, а не подменяет первую"
        );
        assert_eq!(feed.next_page(), 2);
    }

    #[test]
    fn a_page_that_brought_nothing_new_ends_the_shelf() {
        let mut feed = Feed::default();
        feed.begin();
        feed.apply(shelf(&[1, 2]));
        assert!(feed.begin_more());
        feed.apply(shelf(&[]));

        assert!(feed.paging.exhausted);
        assert!(!feed.can_load_more(), "конца больше не просим");
        assert!(!feed.begin_more());
        assert!(!feed.paging.loading_more);
        assert_eq!(feed.releases.len(), 2, "пустой ответ ничего не стирает");
    }

    #[test]
    fn a_failed_request_keeps_the_shelf_and_the_page_it_was_on() {
        let mut feed = Feed::default();
        feed.begin();
        feed.apply(shelf(&[1, 2, 3]));
        assert!(feed.begin_more());
        feed.fail();

        assert!(!feed.paging.loading_more);
        assert!(!feed.paging.exhausted);
        assert_eq!(feed.next_page(), 1, "страница, которая не пришла, не засчитана");
        assert!(feed.can_load_more(), "догрузку можно повторить");
        assert_eq!(feed.releases.len(), 3);
    }

    #[test]
    fn switching_the_pill_empties_the_shelf_and_forgets_the_end() {
        let mut feed = Feed::default();
        feed.begin();
        feed.apply(shelf(&[1, 2]));
        feed.begin_more();
        feed.apply(shelf(&[]));
        assert!(feed.paging.exhausted);

        feed.select(FeedCategory::Donghua);
        assert!(feed.releases.is_empty());
        assert_eq!(feed.category, FeedCategory::Donghua);
        assert_eq!(feed.next_page(), 0);
        assert!(!feed.paging.exhausted);

        // повторная пилюля — не сброс: накопленное остаётся на экране
        feed.begin();
        feed.apply(shelf(&[7, 8]));
        feed.select(FeedCategory::Donghua);
        assert_eq!(feed.releases.len(), 2);
    }

    #[test]
    fn artwork_is_one_entry_per_visible_release_with_a_release_key() {
        let mut feed = Feed::default();
        feed.begin();
        feed.apply(shelf(&[1, 2, 3]));

        let artwork = feed.artwork();
        assert_eq!(artwork.len(), 3);
        assert_eq!(artwork[0].0, posters::release_key(1));
        assert_eq!(artwork[0].1.as_deref(), Some("https://cdn/anixart/1.jpg"));

        // пустой URL — не URL: обложка сядет на заглушку без запроса
        feed.releases[0].image = Some(String::new());
        feed.releases[1].image = None;
        assert_eq!(feed.artwork()[0].1, None);
        assert_eq!(feed.artwork()[1].1, None);

        let mut keys: Vec<&str> = artwork.iter().map(|(key, _)| key.as_str()).collect();
        keys.sort_unstable();
        keys.dedup();
        assert_eq!(keys.len(), 3, "один релиз никогда не перетирает другой");
    }

    #[test]
    fn shelf_captions_name_the_pill_and_what_has_been_gathered() {
        let mut feed = Feed::default();
        assert_eq!(feed.shelf_title(), "«Последнее» · 0 релизов");
        assert!(feed.empty_hint().contains("Последнее"));

        feed.select(FeedCategory::Movies);
        assert_eq!(feed.shelf_title(), "«Фильмы» · 0 релизов");
        assert!(feed.empty_hint().contains("Фильмы"));

        feed.begin();
        feed.apply(shelf(&[1, 2, 3, 4]));
        assert_eq!(feed.shelf_title(), "«Фильмы» · 4 релиза");
    }

    #[test]
    fn the_screen_builds_headlessly_in_every_state_of_the_shelf() {
        let cache = PosterCache::new();

        let empty = Feed::default();
        let _ = empty.view(&cache);

        let mut loading = Feed::default();
        loading.begin();
        let _ = loading.view(&cache);

        let mut feed = Feed::default();
        feed.begin();
        feed.apply(shelf(&(1..=6).collect::<Vec<i64>>()));
        let _ = feed.view(&cache);
        assert!(feed.can_load_more());

        feed.begin_more();
        let _ = feed.view(&cache);

        feed.apply(shelf(&[]));
        let _ = feed.view(&cache);
    }
}
