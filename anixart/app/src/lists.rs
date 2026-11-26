//! The vocabulary of the user's release lists.
//!
//! Two things live here: [`WatchStatus`], the six positions a release can hold in
//! a profile list (`Release.profile_list_status` — the same numbering the official
//! client uses), and [`BookmarkTab`], the eight sections the «Закладки» screen is
//! built from. Both are plain data: the endpoints they map onto are chosen in
//! [`crate::net`], the rows that render them in [`crate::bookmarks`].
//!
//! The status codes come from `ProfileListApi` in the shipping client:
//! `profile/list/all/{status}/{page}` and `profile/list/add/{status}/{r_id}` take
//! the integer of [`WatchStatus::code`], and status 0 is the absence of a list
//! entry, which is why [`WatchStatus::None`] deletes rather than adds.

use std::fmt;

use anixart_core::WatchStatus as CoreStatus;

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

/// Catalogue status of a release (`Release::status_id`), the way the Anixart API
/// numbers it. Unknown values render nothing at all instead of a wrong word, so a
/// future status never lies to the user.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ReleaseStatus {
    /// 1 — announced, not streaming yet.
    Announced,
    /// 2 — on hiatus before it started.
    Stopped,
    /// 3 — finished.
    Finished,
    /// 4 — currently airing.
    Ongoing,
    /// 5 — paused.
    Paused,
    /// 6 — released (a film or OVA that came out).
    Released,
}

impl ReleaseStatus {
    /// Russian caption shown on the release card.
    pub fn label(self) -> &'static str {
        match self {
            ReleaseStatus::Announced => "Анонсируется",
            ReleaseStatus::Stopped => "Не выходит",
            ReleaseStatus::Finished => "Завершён",
            ReleaseStatus::Ongoing => "В процессе",
            ReleaseStatus::Paused => "Приостановлен",
            ReleaseStatus::Released => "Вышел",
        }
    }

    /// Reads it out of `Release::status_id`; `None` for a value this client does
    /// not know.
    pub fn from_id(id: i64) -> Option<Self> {
        match id {
            1 => Some(ReleaseStatus::Announced),
            2 => Some(ReleaseStatus::Stopped),
            3 => Some(ReleaseStatus::Finished),
            4 => Some(ReleaseStatus::Ongoing),
            5 => Some(ReleaseStatus::Paused),
            6 => Some(ReleaseStatus::Released),
            _ => None,
        }
    }

    /// Caption of a raw `status_id`, or `None` when there is nothing to say.
    pub fn label_of_id(id: i64) -> Option<&'static str> {
        Self::from_id(id).map(Self::label)
    }
}

/// The category pills of the «Главная» screen.
///
/// The Anixart feed has two endpoints: `feed/latest/all/{page}` (what the app
/// shows by default) and `feed/all/{page}?date=N`, where `N` is the window the
/// official client numbers as `date_day` … `date_whole_time`. [`FeedCategory::Latest`]
/// therefore carries no `date` at all — it selects the other endpoint.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum FeedCategory {
    /// `GET /feed/latest/all/{page}` — the newest articles of every channel.
    #[default]
    Latest,
    /// `date = 1` — за сутки.
    Day,
    /// `date = 2` — за неделю.
    Week,
    /// `date = 3` — за месяц.
    Month,
    /// `date = 4` — за год.
    Year,
    /// `date = 5` — за всё время.
    WholeTime,
}

impl FeedCategory {
    /// Every pill, in the order the home screen shows them.
    pub const ALL: [FeedCategory; 6] = [
        FeedCategory::Latest,
        FeedCategory::Day,
        FeedCategory::Week,
        FeedCategory::Month,
        FeedCategory::Year,
        FeedCategory::WholeTime,
    ];

    /// Russian caption of the pill.
    pub fn label(self) -> &'static str {
        match self {
            FeedCategory::Latest => "Последние",
            FeedCategory::Day => "За сутки",
            FeedCategory::Week => "За неделю",
            FeedCategory::Month => "За месяц",
            FeedCategory::Year => "За год",
            FeedCategory::WholeTime => "За всё время",
        }
    }

    /// The `date` query parameter, `None` for the latest-articles endpoint, which
    /// takes no filter at all.
    pub fn date(self) -> Option<i32> {
        match self {
            FeedCategory::Latest => None,
            FeedCategory::Day => Some(1),
            FeedCategory::Week => Some(2),
            FeedCategory::Month => Some(3),
            FeedCategory::Year => Some(4),
            FeedCategory::WholeTime => Some(5),
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
    /// with one fixed list, so their pager would only ever show «Стр. 1 из 1».
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

#[cfg(test)]
mod tests {
    use super::*;

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
        for id in 1..=6 {
            let label = ReleaseStatus::label_of_id(id).expect("a known status");
            assert!(!label.is_empty());
        }
        assert_eq!(ReleaseStatus::label_of_id(0), None);
        assert_eq!(ReleaseStatus::label_of_id(7), None);
        assert_eq!(ReleaseStatus::label_of_id(-1), None);
        assert_eq!(ReleaseStatus::from_id(4), Some(ReleaseStatus::Ongoing));
        assert_eq!(ReleaseStatus::Ongoing.label(), "В процессе");
        assert_eq!(ReleaseStatus::Finished.label(), "Завершён");
    }

    #[test]
    fn feed_pills_number_the_date_window_like_the_official_client() {
        // «Последние» is the other endpoint, so it carries no date at all
        assert_eq!(FeedCategory::Latest.date(), None);
        assert_eq!(FeedCategory::Day.date(), Some(1));
        assert_eq!(FeedCategory::Week.date(), Some(2));
        assert_eq!(FeedCategory::Month.date(), Some(3));
        assert_eq!(FeedCategory::Year.date(), Some(4));
        assert_eq!(FeedCategory::WholeTime.date(), Some(5));
        assert_eq!(FeedCategory::default(), FeedCategory::Latest);

        let labels: Vec<&str> = FeedCategory::ALL.iter().map(|c| c.label()).collect();
        assert_eq!(
            labels,
            ["Последние", "За сутки", "За неделю", "За месяц", "За год", "За всё время"]
        );
        assert_eq!(FeedCategory::ALL.len(), 6);
        for (index, category) in FeedCategory::ALL.iter().enumerate() {
            assert_eq!(category.index(), index);
            assert_eq!(FeedCategory::from_index(index), *category);
        }
        assert_eq!(FeedCategory::from_index(99), FeedCategory::Latest);
        assert_eq!(FeedCategory::Month.to_string(), "За месяц");
        // only the latest-articles endpoint is unfiltered
        assert_eq!(
            FeedCategory::ALL.iter().filter(|c| c.date().is_none()).count(),
            1
        );
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
