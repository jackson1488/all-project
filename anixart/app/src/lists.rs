//! The vocabulary of the user's release lists and of the shelves that show them.
//!
//! Four things live here: [`WatchStatus`], the six positions a release can hold in
//! a profile list (`Release.profile_list_status` — the same numbering the official
//! client uses), [`BookmarkTab`], the eight sections the «Закладки» screen is
//! built from, and [`FeedCategory`], the seven home shelves, which are nothing but
//! a [`FilterRequest`] each. All of them are plain data: the endpoints they map
//! onto are chosen in [`crate::net`], the rows that render them in
//! [`crate::bookmarks`] / [`crate::feed`].
//!
//! The status codes come from `ProfileListApi` in the shipping client:
//! `profile/list/all/{status}/{page}` and `profile/list/add/{status}/{r_id}` take
//! the integer of [`WatchStatus::code`], and status 0 is the absence of a list
//! entry, which is why [`WatchStatus::None`] deletes rather than adds.
//!
//! [`Paging`] and [`append_unique`] are the infinite-scroll bookkeeping the three
//! shelved screens share: `POST /filter/{page}` always echoes `total_page_count =
//! 0`, so the only honest end of a shelf is the page that brought nothing new.

use std::collections::HashSet;
use std::fmt;

use anixart_core::{EpisodeType, FilterRequest, WatchStatus as CoreStatus};

/// Where a release sits in the signed-in profile's lists.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum WatchStatus {
    /// Not in any list — `profile/list/delete/{previous}/{r_id}` clears the entry.
    #[default]
    None,
    /// 1 — «Смотрю».
    Watching,
    /// 2 — «В планах».
    Plan,
    /// 3 — «Просмотрено».
    Completed,
    /// 4 — «Отложено».
    HoldOn,
    /// 5 — «Брошено».
    Dropped,
}

impl WatchStatus {
    /// Every status, in the order the official client lists them.
    pub const ALL: [WatchStatus; 6] = [
        WatchStatus::None,
        WatchStatus::Watching,
        WatchStatus::Plan,
        WatchStatus::Completed,
        WatchStatus::HoldOn,
        WatchStatus::Dropped,
    ];

    /// The integer the API speaks.
    pub fn code(self) -> i32 {
        match self {
            WatchStatus::None => 0,
            WatchStatus::Watching => 1,
            WatchStatus::Plan => 2,
            WatchStatus::Completed => 3,
            WatchStatus::HoldOn => 4,
            WatchStatus::Dropped => 5,
        }
    }

    /// Russian caption of the status, as the app shows it on a pill.
    pub fn label(self) -> &'static str {
        match self {
            WatchStatus::None => "Не смотрю",
            WatchStatus::Watching => "Смотрю",
            WatchStatus::Plan => "В планах",
            WatchStatus::Completed => "Просмотрено",
            WatchStatus::HoldOn => "Отложено",
            WatchStatus::Dropped => "Брошено",
        }
    }

    /// Reads the status back out of `Release::profile_list_status`. Anything the
    /// backend adds later degrades to [`WatchStatus::None`] instead of panicking.
    pub fn from_code(code: i32) -> Self {
        match code {
            1 => WatchStatus::Watching,
            2 => WatchStatus::Plan,
            3 => WatchStatus::Completed,
            4 => WatchStatus::HoldOn,
            5 => WatchStatus::Dropped,
            _ => WatchStatus::None,
        }
    }

    /// `true` for [`WatchStatus::None`], the only status that is not a list entry.
    pub fn is_unlisted(self) -> bool {
        self == WatchStatus::None
    }

    /// The `anixart-core` counterpart, `None` for the unlisted status — that is
    /// the status the `/profile/list/*` endpoints are not called with.
    pub fn to_core(self) -> Option<CoreStatus> {
        match self {
            WatchStatus::None => None,
            WatchStatus::Watching => Some(CoreStatus::Watching),
            WatchStatus::Plan => Some(CoreStatus::Planned),
            WatchStatus::Completed => Some(CoreStatus::Watched),
            WatchStatus::HoldOn => Some(CoreStatus::Delayed),
            WatchStatus::Dropped => Some(CoreStatus::Dropped),
        }
    }

    /// App-side status of a `anixart-core` one; the two enums carry the same five
    /// list entries under different names.
    pub fn from_core(status: CoreStatus) -> Self {
        match status {
            CoreStatus::Watching => WatchStatus::Watching,
            CoreStatus::Planned => WatchStatus::Plan,
            CoreStatus::Watched => WatchStatus::Completed,
            CoreStatus::Delayed => WatchStatus::HoldOn,
            CoreStatus::Dropped => WatchStatus::Dropped,
        }
    }

    /// Status of a release as the catalogue reports it in
    /// `Release::profile_list_status`.
    pub fn of_release(code: i32) -> Self {
        Self::from_code(code)
    }

    /// The tab that shows this list, if the «Закладки» screen has one (all of them
    /// do, which keeps [`BookmarkTab::of_status`] total).
    pub fn tab(self) -> BookmarkTab {
        BookmarkTab::of_status(self)
    }
}

impl fmt::Display for WatchStatus {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.label())
    }
}

/// The eight sections of the «Закладки» screen, in tab order.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum BookmarkTab {
    /// Collections the profile has favourited (`collectionFavorite/all/{page}`).
    Collections,
    /// Recently opened episodes (`history/{page}`).
    History,
    /// Favourited releases (`favorite/all/{page}`).
    #[default]
    Favorites,
    /// The five profile lists.
    Watching,
    Plan,
    Completed,
    HoldOn,
    Dropped,
}

impl BookmarkTab {
    /// Every section, in the order the pill row shows them.
    pub const ALL: [BookmarkTab; 8] = [
        BookmarkTab::Collections,
        BookmarkTab::History,
        BookmarkTab::Favorites,
        BookmarkTab::Watching,
        BookmarkTab::Plan,
        BookmarkTab::Completed,
        BookmarkTab::HoldOn,
        BookmarkTab::Dropped,
    ];

    /// Russian caption of the tab.
    pub fn label(self) -> &'static str {
        match self {
            BookmarkTab::Collections => "Коллекции",
            BookmarkTab::History => "История",
            BookmarkTab::Favorites => "Избранное",
            BookmarkTab::Watching => "Смотрю",
            BookmarkTab::Plan => "В планах",
            BookmarkTab::Completed => "Просмотрено",
            BookmarkTab::HoldOn => "Отложено",
            BookmarkTab::Dropped => "Брошено",
        }
    }

    /// The watch status behind a list tab, `None` for the three that are not
    /// lists (collections, history, favourites).
    pub fn status(self) -> Option<WatchStatus> {
        match self {
            BookmarkTab::Collections | BookmarkTab::History | BookmarkTab::Favorites => None,
            BookmarkTab::Watching => Some(WatchStatus::Watching),
            BookmarkTab::Plan => Some(WatchStatus::Plan),
            BookmarkTab::Completed => Some(WatchStatus::Completed),
            BookmarkTab::HoldOn => Some(WatchStatus::HoldOn),
            BookmarkTab::Dropped => Some(WatchStatus::Dropped),
        }
    }

    /// The tab of a status — total, because status 0 has no list of its own and
    /// is shown by the favourites tab.
    pub fn of_status(status: WatchStatus) -> BookmarkTab {
        match status {
            WatchStatus::None => BookmarkTab::Favorites,
            WatchStatus::Watching => BookmarkTab::Watching,
            WatchStatus::Plan => BookmarkTab::Plan,
            WatchStatus::Completed => BookmarkTab::Completed,
            WatchStatus::HoldOn => BookmarkTab::HoldOn,
            WatchStatus::Dropped => BookmarkTab::Dropped,
        }
    }

    /// `true` for the tab whose rows are collections rather than releases.
    pub fn is_collections(self) -> bool {
        self == BookmarkTab::Collections
    }

    /// Every tab needs the session token: the three list-of-others endpoints
    /// (`favorite/all`, `history/{page}`, `collectionFavorite/all`) are
    /// `require_token()` calls in `anixart-core`, so a guest never opens this
    /// screen's data path.
    pub fn needs_token(self) -> bool {
        true
    }

    pub fn index(self) -> usize {
        Self::ALL.iter().position(|tab| *tab == self).unwrap_or(0)
    }

    pub fn from_index(index: usize) -> Self {
        Self::ALL.get(index).copied().unwrap_or_default()
    }
}

impl fmt::Display for BookmarkTab {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.label())
    }
}

/// Catalogue status of a release, numbered the way the live Anixart API numbers it.
///
/// A catalogue card carries the status twice: a bare `status_id`, which the filter
/// endpoint leaves at `0`, and a `status: { id, name }` object whose `id` is the
/// value below. [`crate::release`] therefore prefers the name the backend sent and
/// falls back to this table. Unknown values render nothing at all instead of a
/// wrong word, so a status this client does not know never lies to the user.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ReleaseStatus {
    /// 1 — «Вышел»: airing finished.
    Released,
    /// 2 — «Выходит»: онгоинг.
    Ongoing,
    /// 3 — «Анонс»: announced, nothing streamed yet.
    Announced,
}

impl ReleaseStatus {
    /// Every status the API has, in id order.
    pub const ALL: [ReleaseStatus; 3] = [
        ReleaseStatus::Released,
        ReleaseStatus::Ongoing,
        ReleaseStatus::Announced,
    ];

    /// Russian caption shown on the release card.
    pub fn label(self) -> &'static str {
        match self {
            ReleaseStatus::Released => "Вышел",
            ReleaseStatus::Ongoing => "Выходит",
            ReleaseStatus::Announced => "Анонс",
        }
    }

    /// The `status_id` the filter endpoint is asked with.
    pub fn id(self) -> i64 {
        match self {
            ReleaseStatus::Released => 1,
            ReleaseStatus::Ongoing => 2,
            ReleaseStatus::Announced => 3,
        }
    }

    /// Reads it out of a numeric status; `None` for a value this client does
    /// not know.
    pub fn from_id(id: i64) -> Option<Self> {
        match id {
            1 => Some(ReleaseStatus::Released),
            2 => Some(ReleaseStatus::Ongoing),
            3 => Some(ReleaseStatus::Announced),
            _ => None,
        }
    }

    /// Caption of a raw `status_id`, or `None` when there is nothing to say.
    pub fn label_of_id(id: i64) -> Option<&'static str> {
        Self::from_id(id).map(Self::label)
    }
}

impl fmt::Display for ReleaseStatus {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.label())
    }
}

/// The seven shelves of the «Главная» screen, in pill order — the same tabs the
/// official client puts on its home.
///
/// Every shelf is one `POST /filter/{page}` request, so a category is nothing but
/// the [`FilterRequest`] it stands for: a country for «Аниме» and «Дунхуа», a
/// catalogue category for «Фильмы», a release status for «Онгоинги», «Анонсы» and
/// «Завершённые», and a bare sort for «Последнее». The ids are the ones the live
/// API answers with — `category_id` 2 is «Фильм», `status_id` 1 «Вышел», 2
/// «Выходит», 3 «Анонс» — and the body goes out in the snake_case those names
/// have there.
///
/// None of that needs a session token, so all seven shelves open for a guest.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum FeedCategory {
    /// Свежие обновления каталога — `sort = 0` без прочих ограничений.
    #[default]
    Actual,
    /// Онгоинги — `status_id = 2`.
    Ongoing,
    /// Анонсы — `status_id = 3`.
    Announced,
    /// Завершённые — `status_id = 1`.
    Finished,
    /// Фильмы — `category_id = 2`.
    Movies,
    /// Аниме — `country = "Япония"`.
    Anime,
    /// Дунхуа — `country = "Китай"`.
    Donghua,
}

impl FeedCategory {
    /// Every pill, in the order the home screen shows them.
    pub const ALL: [FeedCategory; 7] = [
        FeedCategory::Actual,
        FeedCategory::Ongoing,
        FeedCategory::Announced,
        FeedCategory::Finished,
        FeedCategory::Movies,
        FeedCategory::Anime,
        FeedCategory::Donghua,
    ];

    /// Russian caption of the pill.
    pub fn label(self) -> &'static str {
        match self {
            FeedCategory::Actual => "Последнее",
            FeedCategory::Ongoing => "Онгоинги",
            FeedCategory::Announced => "Анонсы",
            FeedCategory::Finished => "Завершённые",
            FeedCategory::Movies => "Фильмы",
            FeedCategory::Anime => "Аниме",
            FeedCategory::Donghua => "Дунхуа",
        }
    }

    /// Каталог-ограничение (`category.id`), `None` для полок, которые его не сужают.
    pub fn category_id(self) -> Option<i64> {
        match self {
            FeedCategory::Movies => Some(2),
            _ => None,
        }
    }

    /// Статус релиза (`status.id`), `None` для полок, которые не смотрят на статус.
    pub fn status_id(self) -> Option<i64> {
        match self {
            FeedCategory::Finished => Some(ReleaseStatus::Released.id()),
            FeedCategory::Ongoing => Some(ReleaseStatus::Ongoing.id()),
            FeedCategory::Announced => Some(ReleaseStatus::Announced.id()),
            _ => None,
        }
    }

    /// Страна производства, `None` для полок без странного фильтра.
    pub fn country(self) -> Option<&'static str> {
        match self {
            FeedCategory::Anime => Some("Япония"),
            FeedCategory::Donghua => Some("Китай"),
            _ => None,
        }
    }

    /// Тело запроса `POST /filter/{page}`: полка — это именно оно, ничего больше
    /// на «Главной» не выбирается.
    pub fn filter(self) -> FilterRequest {
        FilterRequest {
            sort: 0,
            category_id: self.category_id(),
            status_id: self.status_id(),
            country: self.country().map(str::to_owned),
            ..Default::default()
        }
    }

    pub fn index(self) -> usize {
        Self::ALL.iter().position(|category| *category == self).unwrap_or(0)
    }

    pub fn from_index(index: usize) -> Self {
        Self::ALL.get(index).copied().unwrap_or_default()
    }
}

impl fmt::Display for FeedCategory {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.label())
    }
}

/// Ряд «Выберите вариант» на странице релиза: какие из озвучек и переводов показать.
///
/// Официальный клиент делит список вариантов именно так — «Все», только закадровая
/// озвучка, только субтитры, — и деление это берётся из `EpisodeType::is_sub`,
/// который backend присылает вместе с каждым вариантом.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum VariantFilter {
    /// Все варианты подряд.
    #[default]
    All,
    /// Только озвучки (`is_sub == false`).
    Voiceover,
    /// Только субтитры (`is_sub == true`).
    Subtitle,
}

impl VariantFilter {
    /// Every option, in the order the row shows them.
    pub const ALL: [VariantFilter; 3] = [
        VariantFilter::All,
        VariantFilter::Voiceover,
        VariantFilter::Subtitle,
    ];

    /// Russian caption of the pill.
    pub fn label(self) -> &'static str {
        match self {
            VariantFilter::All => "Все",
            VariantFilter::Voiceover => "Озвучки",
            VariantFilter::Subtitle => "Субтитры",
        }
    }

    /// Does this variant belong to the shelf? The index a [`Message::SelectType`]
    /// carries is always the position in the *unfiltered* list, so filtering never
    /// renumbers the rows ([`crate::release`]).
    pub fn accepts(self, kind: &EpisodeType) -> bool {
        match self {
            VariantFilter::All => true,
            VariantFilter::Voiceover => !kind.is_sub,
            VariantFilter::Subtitle => kind.is_sub,
        }
    }

    pub fn index(self) -> usize {
        Self::ALL.iter().position(|variant| *variant == self).unwrap_or(0)
    }

    pub fn from_index(index: usize) -> Self {
        Self::ALL.get(index).copied().unwrap_or_default()
    }
}

impl fmt::Display for VariantFilter {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.label())
    }
}

/// The two algorithmic shelves of the «Обзор» screen, both `POST` with an empty
/// body and both answered with a page of releases.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DiscoverKind {
    /// `POST /discover/interesting`.
    Interesting,
    /// `POST /discover/comments`.
    Comments,
}

impl DiscoverKind {
    pub const ALL: [DiscoverKind; 2] = [DiscoverKind::Interesting, DiscoverKind::Comments];

    /// Russian caption of the tab.
    pub fn label(self) -> &'static str {
        match self {
            DiscoverKind::Interesting => "Интересное",
            DiscoverKind::Comments => "Обсуждаемое",
        }
    }
}

impl fmt::Display for DiscoverKind {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.label())
    }
}

/// The four shelves of the «Обзор» screen, in tab order.
///
/// The first two are the user's own requests — a search box and the catalogue
/// filter — and the last two are the backend's algorithmic shelves, which is why
/// only [`BrowseTab::Search`] needs a query and only the first two are paginated
/// (`discover/*` takes no page argument).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum BrowseTab {
    /// `POST /search/releases/{page}`.
    #[default]
    Search,
    /// `POST /filter/{page}` with a [`crate::net`] sort id.
    Catalog,
    /// `POST /discover/interesting`.
    Interesting,
    /// `POST /discover/comments`.
    Comments,
}

impl BrowseTab {
    /// Every shelf, in the order the pill row shows them.
    pub const ALL: [BrowseTab; 4] = [
        BrowseTab::Search,
        BrowseTab::Catalog,
        BrowseTab::Interesting,
        BrowseTab::Comments,
    ];

    /// Russian caption of the shelf.
    pub fn label(self) -> &'static str {
        match self {
            BrowseTab::Search => "Поиск",
            BrowseTab::Catalog => "Каталог",
            BrowseTab::Interesting => "Интересное",
            BrowseTab::Comments => "Обсуждаемое",
        }
    }

    /// The discovery shelf this tab names, `None` for the two user-driven ones.
    pub fn discover(self) -> Option<DiscoverKind> {
        match self {
            BrowseTab::Interesting => Some(DiscoverKind::Interesting),
            BrowseTab::Comments => Some(DiscoverKind::Comments),
            _ => None,
        }
    }

    /// `true` for the tab whose request is meaningless without a query.
    pub fn needs_query(self) -> bool {
        self == BrowseTab::Search
    }

    /// `true` for the tabs the backend pages through; the discovery shelves answer
    /// with one fixed list, so their infinite scroll stops after the first page.
    pub fn is_paged(self) -> bool {
        matches!(self, BrowseTab::Search | BrowseTab::Catalog)
    }

    /// `true` for the tab that carries the sort pills.
    pub fn has_sort(self) -> bool {
        self == BrowseTab::Catalog
    }

    /// None of the four shelves is `require_token()` upstream, so a guest may open
    /// all of them.
    pub fn needs_token(self) -> bool {
        false
    }

    pub fn index(self) -> usize {
        Self::ALL.iter().position(|tab| *tab == self).unwrap_or(0)
    }

    pub fn from_index(index: usize) -> Self {
        Self::ALL.get(index).copied().unwrap_or_default()
    }
}

impl fmt::Display for BrowseTab {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.label())
    }
}

/// The sort ids `POST /filter/{page}` accepts, in the order the catalogue pills
/// show them: the four descending keys first, then their ascending counterparts.
pub const CATALOG_SORTS: [(i32, &str); 8] = [
    (0, "По обновлению"),
    (1, "По оценке"),
    (2, "По году"),
    (3, "По популярности"),
    (4, "Сначала давние"),
    (5, "Сначала низкие оценки"),
    (6, "Сначала ранние годы"),
    (7, "Сначала непопулярные"),
];

/// Russian caption of a catalogue sort id; an id the backend adds later keeps its
/// number rather than lying with a wrong word.
pub fn catalog_sort_label(sort: i32) -> &'static str {
    CATALOG_SORTS
        .iter()
        .find(|(id, _)| *id == sort)
        .map(|(_, label)| *label)
        .unwrap_or("Сортировка")
}

/// Сколько ответов уже легло в полку и куда ещё есть идти.
///
/// Полка, накопленная скроллом, отличается от полки, перелистываемой кнопками,
/// двумя вещами: список не заменяется, а дописывается, и конец выборки никто не
/// сообщает. `POST /filter/{page}` отвечает `total_page_count = 0` и
/// `current_page = 0` всегда, поэтому единственный честный признак конца —
/// страница, которая не принесла ни одного нового элемента.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct Paging {
    /// Сколько ответов легло в полку: ноль до первого, страница `pages` — следующая.
    pub pages: usize,
    /// Первый ответ полки в полёте.
    pub loading: bool,
    /// Догрузка следующей страницы в полёте.
    pub loading_more: bool,
    /// Дальше идти некуда: ответ не принёс ничего нового.
    pub exhausted: bool,
}

impl Paging {
    /// Номер страницы, который надо просить следующим.
    pub fn next(self) -> u32 {
        self.pages as u32
    }

    /// Можно ли догружать: не в полёте, не конец, и хотя бы один ответ уже был.
    pub fn can_load_more(self) -> bool {
        !self.loading && !self.loading_more && !self.exhausted && self.pages > 0
    }

    /// Полка начинается заново: ни страниц, ни признака конца.
    pub fn reset(&mut self) {
        *self = Self::default();
    }

    /// Ушёл первый запрос полки.
    pub fn begin(&mut self) {
        self.reset();
        self.loading = true;
    }

    /// Ушёл запрос следующей страницы.
    pub fn begin_more(&mut self) -> bool {
        if !self.can_load_more() {
            return false;
        }
        self.loading_more = true;
        true
    }

    /// Ответ принят: `added` — сколько элементов реально добавилось. Ноль значит,
    /// что полка дошла до конца выборки.
    pub fn finish(&mut self, added: usize) {
        self.loading = false;
        self.loading_more = false;
        self.pages += 1;
        if added == 0 {
            self.exhausted = true;
        }
    }

    /// Отказ сервера: запрос снят, страница не засчитана, конец не объявлен —
    /// догрузку можно повторить.
    pub fn fail(&mut self) {
        self.loading = false;
        self.loading_more = false;
    }
}

/// Прибавляет к накопленному списку только те элементы, которых в нём ещё нет.
///
/// Возвращает число реально добавленных — по нему [`Paging::finish`] понимает, что
/// страницы кончились. Порядок сохраняем: приходившие раньше элементы остаются
/// на своих местах, потому что полка «Последнее» уже отсортирована сервером.
pub fn append_unique<T, F>(held: &mut Vec<T>, incoming: Vec<T>, id: F) -> usize
where
    F: Fn(&T) -> i64,
{
    let mut seen: HashSet<i64> = held.iter().map(&id).collect();
    let mut added = 0;
    for item in incoming {
        if seen.insert(id(&item)) {
            held.push(item);
            added += 1;
        }
    }
    added
}

#[cfg(test)]
mod tests {
    use super::*;
    use anixart_core::Release;

    #[test]
    fn watch_statuses_round_trip_through_their_api_codes() {
        for status in WatchStatus::ALL {
            assert_eq!(WatchStatus::from_code(status.code()), status);
        }
        assert_eq!(WatchStatus::None.code(), 0);
        assert_eq!(WatchStatus::Dropped.code(), 5);
        // an unknown code is not an error, it simply means "not in a list"
        for code in [-1, 6, 7, 42, i32::MAX] {
            assert_eq!(WatchStatus::from_code(code), WatchStatus::None);
        }
        assert_eq!(WatchStatus::default(), WatchStatus::None);
    }

    #[test]
    fn every_status_has_a_distinct_russian_caption() {
        let labels: Vec<&str> = WatchStatus::ALL.iter().map(|s| s.label()).collect();
        assert_eq!(
            labels,
            [
                "Не смотрю",
                "Смотрю",
                "В планах",
                "Просмотрено",
                "Отложено",
                "Брошено"
            ]
        );
        let mut sorted = labels.clone();
        sorted.sort_unstable();
        sorted.dedup();
        assert_eq!(sorted.len(), labels.len(), "no two statuses share a caption");
        assert_eq!(WatchStatus::Plan.to_string(), "В планах");
    }

    #[test]
    fn only_the_unlisted_status_reports_itself_as_absent() {
        assert!(WatchStatus::None.is_unlisted());
        for status in WatchStatus::ALL.iter().skip(1) {
            assert!(!status.is_unlisted(), "{status}");
        }
    }

    #[test]
    fn the_five_list_statuses_convert_to_core_and_back() {
        // status 0 is not a list, so it has no `anixart_core` counterpart
        assert_eq!(WatchStatus::None.to_core(), None);
        for status in WatchStatus::ALL.iter().skip(1) {
            let core = status.to_core().expect("{status}: no core twin");
            assert_eq!(core.as_i32(), status.code(), "{status} ↔ {core:?}");
            assert_eq!(WatchStatus::from_core(core), *status);
        }
        // every core variant is reachable from this side
        for core in CoreStatus::ALL {
            assert!(WatchStatus::from_core(core).to_core().is_some());
        }
        assert_eq!(WatchStatus::of_release(3), WatchStatus::Completed);
        assert_eq!(WatchStatus::of_release(0), WatchStatus::None);
    }

    #[test]
    fn bookmark_tabs_cover_the_lists_and_the_library_sections() {
        let labels: Vec<&str> = BookmarkTab::ALL.iter().map(|t| t.label()).collect();
        assert_eq!(
            labels,
            [
                "Коллекции",
                "История",
                "Избранное",
                "Смотрю",
                "В планах",
                "Просмотрено",
                "Отложено",
                "Брошено"
            ]
        );
        assert_eq!(BookmarkTab::ALL.len(), 8);
        assert_eq!(BookmarkTab::default(), BookmarkTab::Favorites);
        assert!(BookmarkTab::Collections.is_collections());
        assert!(!BookmarkTab::Favorites.is_collections());
        // every tab is a token-bound section: a guest never reaches the data path
        assert!(BookmarkTab::ALL.iter().all(|t| t.needs_token()));
    }

    #[test]
    fn list_tabs_expose_their_status_and_the_others_do_not() {
        for tab in [
            BookmarkTab::Collections,
            BookmarkTab::History,
            BookmarkTab::Favorites,
        ] {
            assert_eq!(tab.status(), None, "{tab}");
        }
        for tab in [
            BookmarkTab::Watching,
            BookmarkTab::Plan,
            BookmarkTab::Completed,
            BookmarkTab::HoldOn,
            BookmarkTab::Dropped,
        ] {
            assert!(tab.status().is_some(), "{tab}");
        }
        assert_eq!(BookmarkTab::Plan.status(), Some(WatchStatus::Plan));
        assert_eq!(BookmarkTab::Dropped.status(), Some(WatchStatus::Dropped));
    }

    #[test]
    fn the_status_to_tab_mapping_is_total_and_consistent() {
        for status in WatchStatus::ALL {
            let tab = status.tab();
            assert_eq!(tab.index(), BookmarkTab::ALL.iter().position(|t| *t == tab).unwrap());
            match status {
                // status 0 is not a list, so the favourites tab stands in for it
                WatchStatus::None => assert_eq!(tab, BookmarkTab::Favorites),
                other => assert_eq!(BookmarkTab::of_status(other), tab),
            }
        }
        // the five real lists each get their own tab
        let tabs: Vec<BookmarkTab> = WatchStatus::ALL[1..]
            .iter()
            .map(|status| status.tab())
            .collect();
        let mut distinct = tabs.clone();
        distinct.dedup();
        assert_eq!(distinct.len(), tabs.len());
        assert_eq!(BookmarkTab::Watching.to_string(), "Смотрю");
    }

    #[test]
    fn tab_indices_round_trip_and_clamp() {
        for (index, tab) in BookmarkTab::ALL.iter().enumerate() {
            assert_eq!(tab.index(), index);
            assert_eq!(BookmarkTab::from_index(index), *tab);
        }
        assert_eq!(BookmarkTab::from_index(7), BookmarkTab::Dropped);
        assert_eq!(BookmarkTab::from_index(8), BookmarkTab::Favorites);
        assert_eq!(BookmarkTab::from_index(usize::MAX), BookmarkTab::Favorites);
    }

    #[test]
    fn known_release_statuses_get_a_label_and_unknown_ones_get_nothing() {
        for status in ReleaseStatus::ALL {
            let label = ReleaseStatus::label_of_id(status.id()).expect("a known status");
            assert_eq!(label, status.label());
            assert!(!label.is_empty());
            assert_eq!(ReleaseStatus::from_id(status.id()), Some(status));
        }
        // the numbering the live catalogue sends: 1 вышел, 2 выходит, 3 анонс
        assert_eq!(ReleaseStatus::label_of_id(1), Some("Вышел"));
        assert_eq!(ReleaseStatus::label_of_id(2), Some("Выходит"));
        assert_eq!(ReleaseStatus::label_of_id(3), Some("Анонс"));
        // `status_id = 0` is what the filter endpoint leaves in the card: no word
        assert_eq!(ReleaseStatus::label_of_id(0), None);
        for id in [4, 5, 6, 7, 42, -1, i64::MAX] {
            assert_eq!(ReleaseStatus::label_of_id(id), None, "{id}");
        }
        assert_eq!(ReleaseStatus::Ongoing.to_string(), "Выходит");
    }

    #[test]
    fn feed_pills_are_the_seven_home_shelves_of_the_official_client() {
        let labels: Vec<&str> = FeedCategory::ALL.iter().map(|c| c.label()).collect();
        assert_eq!(
            labels,
            [
                "Последнее",
                "Онгоинги",
                "Анонсы",
                "Завершённые",
                "Фильмы",
                "Аниме",
                "Дунхуа"
            ]
        );
        assert_eq!(FeedCategory::ALL.len(), 7);
        assert_eq!(FeedCategory::default(), FeedCategory::Actual);
        for (index, category) in FeedCategory::ALL.iter().enumerate() {
            assert_eq!(category.index(), index);
            assert_eq!(FeedCategory::from_index(index), *category);
        }
        assert_eq!(FeedCategory::from_index(99), FeedCategory::Actual);
        assert_eq!(FeedCategory::Donghua.to_string(), "Дунхуа");
    }

    #[test]
    fn every_feed_pill_is_one_filter_request() {
        // «Последнее» ничего не сужает, кроме порядка: свежие обновления сверху
        let actual = FeedCategory::Actual.filter();
        assert_eq!(actual.sort, 0);
        assert_eq!(actual.category_id, None);
        assert_eq!(actual.status_id, None);
        assert_eq!(actual.country, None);

        // статусные полки берут `status_id` из того же справочника, что и чипы
        assert_eq!(
            FeedCategory::Ongoing.filter().status_id,
            Some(ReleaseStatus::Ongoing.id())
        );
        assert_eq!(
            FeedCategory::Announced.filter().status_id,
            Some(ReleaseStatus::Announced.id())
        );
        assert_eq!(
            FeedCategory::Finished.filter().status_id,
            Some(ReleaseStatus::Released.id())
        );
        for category in [
            FeedCategory::Actual,
            FeedCategory::Movies,
            FeedCategory::Anime,
            FeedCategory::Donghua,
        ] {
            assert_eq!(category.filter().status_id, None, "{category}");
        }

        // «Фильмы» — каталожная категория 2, страны у неё нет
        let movies = FeedCategory::Movies.filter();
        assert_eq!(movies.category_id, Some(2));
        assert_eq!(movies.country, None);
        for category in [
            FeedCategory::Actual,
            FeedCategory::Ongoing,
            FeedCategory::Announced,
            FeedCategory::Finished,
        ] {
            assert_eq!(category.filter().category_id, None, "{category}");
        }

        // страновые полки — единственный фильтр, который говорит по-русски
        assert_eq!(FeedCategory::Anime.filter().country.as_deref(), Some("Япония"));
        assert_eq!(FeedCategory::Donghua.filter().country.as_deref(), Some("Китай"));
        for category in [
            FeedCategory::Actual,
            FeedCategory::Ongoing,
            FeedCategory::Announced,
            FeedCategory::Finished,
            FeedCategory::Movies,
        ] {
            assert_eq!(category.filter().country, None, "{category}");
        }

        // ни одна полка не трогает то, чего «Главная» не выбирает
        for category in FeedCategory::ALL {
            let filter = category.filter();
            assert_eq!(filter.genres, Vec::<String>::new());
            assert_eq!(filter.types, Vec::<i64>::new());
            assert_eq!(filter.start_year, None);
            assert_eq!(filter.end_year, None);
            assert_eq!(filter.episodes_from, None);
            assert_eq!(filter.episodes_to, None);
            assert!(!filter.is_genres_exclude_mode_enabled);
        }
    }

    #[test]
    fn the_variant_row_splits_voiceovers_from_subtitles() {
        let dub = EpisodeType {
            name: "AniDUB".to_owned(),
            is_sub: false,
            ..Default::default()
        };
        let sub = EpisodeType {
            name: "Субтитры | Netflix".to_owned(),
            is_sub: true,
            ..Default::default()
        };

        assert!(VariantFilter::All.accepts(&dub));
        assert!(VariantFilter::All.accepts(&sub));
        assert!(VariantFilter::Voiceover.accepts(&dub));
        assert!(!VariantFilter::Voiceover.accepts(&sub));
        assert!(VariantFilter::Subtitle.accepts(&sub));
        assert!(!VariantFilter::Subtitle.accepts(&dub));

        let labels: Vec<&str> = VariantFilter::ALL.iter().map(|v| v.label()).collect();
        assert_eq!(labels, ["Все", "Озвучки", "Субтитры"]);
        assert_eq!(VariantFilter::default(), VariantFilter::All);
        for (index, variant) in VariantFilter::ALL.iter().enumerate() {
            assert_eq!(variant.index(), index);
            assert_eq!(VariantFilter::from_index(index), *variant);
        }
        assert_eq!(VariantFilter::from_index(7), VariantFilter::All);
        assert_eq!(VariantFilter::Subtitle.to_string(), "Субтитры");
    }

    #[test]
    fn a_shelf_only_ends_on_a_page_that_added_nothing_new() {
        let mut paging = Paging::default();
        assert_eq!(paging.next(), 0, "первая страница — нулевая");
        assert!(!paging.can_load_more(), "до первого ответа догружать нечего");

        paging.begin();
        assert!(paging.loading);
        assert!(!paging.can_load_more(), "первый ответ ещё в полёте");

        paging.finish(25);
        assert_eq!((paging.pages, paging.next()), (1, 1));
        assert!(!paging.loading);
        assert!(!paging.exhausted);
        assert!(paging.can_load_more());

        // догрузка: вторая страница, тоже полная
        assert!(paging.begin_more());
        assert!(paging.loading_more);
        assert!(!paging.can_load_more(), "вторая догрузка в полёте не начинается");
        paging.finish(25);
        assert_eq!(paging.next(), 2);

        // страница, которая ничего нового не принесла, и есть конец выборки
        paging.begin_more();
        paging.finish(0);
        assert!(paging.exhausted);
        assert!(!paging.can_load_more());
        assert_eq!(paging.next(), 3, "пройденные страницы не откатываются");

        // отказ сервера страницу не засчитывает: её можно попросить снова
        let mut retry = Paging {
            pages: 2,
            ..Default::default()
        };
        assert!(retry.begin_more());
        retry.fail();
        assert_eq!(retry.next(), 2);
        assert!(!retry.loading_more);
        assert!(retry.can_load_more());
        assert!(!retry.exhausted);

        // смена полки забывает всё: ни страниц, ни конца
        let mut fresh = Paging::default();
        fresh.begin();
        fresh.finish(0);
        assert!(fresh.exhausted);
        fresh.reset();
        assert_eq!(fresh, Paging::default());
        assert!(!fresh.exhausted);

        // `begin` сам сбрасывает прошлый конец: полка начинается заново
        let mut again = Paging {
            pages: 4,
            exhausted: true,
            ..Default::default()
        };
        again.begin();
        assert_eq!(again.pages, 0);
        assert!(!again.exhausted);
        assert!(again.loading);
    }

    #[test]
    fn appending_a_page_keeps_the_order_and_drops_the_repeats() {
        let mut held: Vec<Release> = vec![
            Release { id: 1, ..Default::default() },
            Release { id: 2, ..Default::default() },
        ];
        let incoming = vec![
            Release { id: 2, ..Default::default() },
            Release { id: 3, ..Default::default() },
            Release { id: 3, ..Default::default() },
        ];
        let added = append_unique(&mut held, incoming, |release| release.id);
        assert_eq!(added, 1, "повторы не считаются");
        assert_eq!(
            held.iter().map(|release| release.id).collect::<Vec<i64>>(),
            vec![1, 2, 3]
        );

        // пустая страница — ноль добавленных, список не тронут
        let added = append_unique(&mut held, Vec::new(), |release: &Release| release.id);
        assert_eq!(added, 0);
        assert_eq!(held.len(), 3);

        // полка с нуля принимает всё подряд
        let mut empty: Vec<Release> = Vec::new();
        let added = append_unique(
            &mut empty,
            vec![Release { id: 7, ..Default::default() }],
            |release| release.id,
        );
        assert_eq!((added, empty.len()), (1, 1));

        // ключ берётся не только у релизов: коллекции живут в том же скролле
        let mut titles = vec!["a".to_owned()];
        let added = append_unique(&mut titles, vec!["a".to_owned(), "b".to_owned()], |title| {
            title.len() as i64
        });
        assert_eq!(added, 0, "«b» той же длины, что «a»");
    }

    #[test]
    fn browse_tabs_split_the_user_driven_shelves_from_the_algorithmic_ones() {
        let labels: Vec<&str> = BrowseTab::ALL.iter().map(|t| t.label()).collect();
        assert_eq!(labels, ["Поиск", "Каталог", "Интересное", "Обсуждаемое"]);
        assert_eq!(BrowseTab::ALL.len(), 4);
        assert_eq!(BrowseTab::default(), BrowseTab::Search);

        // only the two discovery tabs name a `DiscoverKind`
        assert_eq!(BrowseTab::Search.discover(), None);
        assert_eq!(BrowseTab::Catalog.discover(), None);
        assert_eq!(BrowseTab::Interesting.discover(), Some(DiscoverKind::Interesting));
        assert_eq!(BrowseTab::Comments.discover(), Some(DiscoverKind::Comments));

        // only the search tab is meaningless without a query
        assert!(BrowseTab::Search.needs_query());
        assert!(!BrowseTab::Catalog.needs_query());

        // the discovery shelves take no page argument
        assert!(BrowseTab::Search.is_paged());
        assert!(BrowseTab::Catalog.is_paged());
        assert!(!BrowseTab::Interesting.is_paged());
        assert!(!BrowseTab::Comments.is_paged());

        // only the catalogue has sort pills
        assert!(BrowseTab::Catalog.has_sort());
        assert!(!BrowseTab::Search.has_sort());

        // none of the four is a token-bound endpoint: «Обзор» opens for a guest
        assert!(BrowseTab::ALL.iter().all(|t| !t.needs_token()));

        for (index, tab) in BrowseTab::ALL.iter().enumerate() {
            assert_eq!(tab.index(), index);
            assert_eq!(BrowseTab::from_index(index), *tab);
        }
        assert_eq!(BrowseTab::from_index(9), BrowseTab::Search);
        assert_eq!(BrowseTab::Comments.to_string(), "Обсуждаемое");
    }

    #[test]
    fn the_two_discovery_shelves_have_their_own_captions() {
        assert_eq!(DiscoverKind::ALL, [DiscoverKind::Interesting, DiscoverKind::Comments]);
        assert_eq!(DiscoverKind::Interesting.label(), "Интересное");
        assert_eq!(DiscoverKind::Comments.to_string(), "Обсуждаемое");
        assert_ne!(DiscoverKind::Interesting.label(), DiscoverKind::Comments.label());
    }

    #[test]
    fn catalogue_sorts_are_the_eight_ids_the_filter_endpoint_takes() {
        let ids: Vec<i32> = CATALOG_SORTS.iter().map(|(id, _)| *id).collect();
        assert_eq!(ids, (0..8).collect::<Vec<i32>>());
        assert_eq!(catalog_sort_label(0), "По обновлению");
        assert_eq!(catalog_sort_label(3), "По популярности");
        assert_eq!(catalog_sort_label(7), "Сначала непопулярные");
        // an id from a future backend keeps its number rather than lying
        assert_eq!(catalog_sort_label(42), "Сортировка");
        assert_eq!(catalog_sort_label(-1), "Сортировка");
        for (id, label) in CATALOG_SORTS {
            assert!(!label.is_empty(), "{id}");
            assert_eq!(catalog_sort_label(id), label);
        }
    }
}
