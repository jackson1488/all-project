//! The «Профиль» screen's data: the session's own account, reduced to what the
//! UI has to show.
//!
//! `GET /profile/info` answers with almost nothing — a numeric `channel_id`, the
//! privilege level and the rating — while login, avatar and the list counters live
//! on `GET /profile/{id}`. [`ProfileSnapshot`] is those two responses joined, which
//! is also what lets the screen render *something* when the profile document has
//! not been created yet (a freshly-linked social account): the info half still
//! carries the rating and the ban flags.

use anixart_core::{Profile, ProfileInfoResponse};
use iced::widget::{Column, Row};
use iced::{alignment, Element, Length};

use crate::app::Message;
use crate::lists::WatchStatus;
use crate::posters::{self, PosterCache};
use crate::widgets;

/// [`ProfileInfoResponse`] plus the [`Profile`] document it points at.
#[derive(Debug, Clone, PartialEq, Default)]
pub struct ProfileSnapshot {
    /// `GET /profile/info` — always present for a signed-in session.
    pub info: ProfileInfoResponse,
    /// `GET /profile/{channel_id}` — `None` until it is fetched, or when the
    /// session has no channel id to fetch it with.
    pub profile: Option<Profile>,
}

impl ProfileSnapshot {
    /// Joins the two halves.
    pub fn new(info: ProfileInfoResponse, profile: Option<Profile>) -> Self {
        Self { info, profile }
    }

    /// The profile id the info response points at, `None` when the backend has
    /// not assigned one (id `0` is its "not set" marker).
    pub fn channel_id(&self) -> Option<i64> {
        self.info.channel_id.filter(|id| *id > 0)
    }

    /// Nickname to put in the title row: the profile login, or a fallback that
    /// says the account has none rather than an empty string.
    pub fn display_name(&self) -> &str {
        self.profile
            .as_ref()
            .map(|profile| profile.login.as_str())
            .filter(|login| !login.is_empty())
            .unwrap_or("Без имени")
    }

    /// Status line the owner wrote under the nickname.
    pub fn bio(&self) -> Option<&str> {
        self.profile
            .as_ref()
            .and_then(|profile| profile.status.as_deref())
            .map(str::trim)
            .filter(|status| !status.is_empty())
    }

    /// Absolute avatar URL, `None` while the profile carries none — the widget
    /// then draws the deterministic placeholder instead of fetching.
    pub fn avatar_url(&self) -> Option<&str> {
        self.profile
            .as_ref()
            .map(|profile| profile.avatar.as_str())
            .filter(|avatar| avatar.starts_with("http"))
    }

    /// Rating score, preferring the profile document (the info response is the
    /// stale half of the pair once both exist).
    pub fn rating(&self) -> i32 {
        match self.profile.as_ref() {
            Some(profile) => profile.rating_score,
            None => self.info.rating_score,
        }
    }

    /// Privilege level of the account.
    pub fn privilege(&self) -> i64 {
        match self.profile.as_ref() {
            Some(profile) => profile.privilege_level,
            None => self.info.privilege_level,
        }
    }

    /// `true` while the account can still be used — the permanent ban from the
    /// info response wins over everything else, because that one is not mirrored
    /// onto the profile document.
    pub fn is_blocked(&self) -> bool {
        self.info.is_perm_banned
            || self
                .profile
                .as_ref()
                .is_some_and(|profile| profile.is_perm_banned || profile.is_banned)
    }

    /// The five list counters, in [`WatchStatus`] order and skipping the
    /// «Не смотрю» slot, which is not a list.
    pub fn lists(&self) -> Vec<(WatchStatus, i32)> {
        let Some(profile) = self.profile.as_ref() else {
            return Vec::new();
        };
        [
            (WatchStatus::Watching, profile.watching_count),
            (WatchStatus::Plan, profile.plan_count),
            (WatchStatus::Completed, profile.completed_count),
            (WatchStatus::HoldOn, profile.hold_on_count),
            (WatchStatus::Dropped, profile.dropped_count),
        ]
        .into_iter()
        .filter(|(status, _)| !status.is_unlisted())
        .collect()
    }

    /// Releases across the five watch lists — the number the «Закладки» header
    /// quotes next to the tab count.
    pub fn list_total(&self) -> i32 {
        self.lists().iter().map(|(_, count)| count).sum()
    }

    /// Extra counters that are not watch lists: избранное, коллекции, комментарии.
    pub fn extras(&self) -> Vec<(&'static str, i32)> {
        let Some(profile) = self.profile.as_ref() else {
            return Vec::new();
        };
        vec![
            ("Избранное", profile.favorite_count),
            ("Коллекции", profile.collection_count),
            ("Комментарии", profile.comment_count),
            ("Друзья", profile.friend_count),
        ]
    }
}

/// Russian name of a privilege level.
///
/// The backend only sends a number; the four names below are this client's
/// presentation of Anixart's role ladder, and anything outside it keeps its
/// numeric value so a future role is not silently labelled «Пользователь».
pub fn privilege_label(level: i64) -> String {
    match level {
        0 => "Пользователь".to_owned(),
        1 => "Куратор".to_owned(),
        2 => "Модератор".to_owned(),
        3 => "Администратор".to_owned(),
        other => format!("Роль {other}"),
    }
}

/// State of the «Профиль» screen.
#[derive(Debug, Default)]
pub struct ProfileView {
    pub snapshot: Option<ProfileSnapshot>,
    pub loading: bool,
    /// `false` for a guest: the screen asks to sign in instead of fetching.
    pub signed_in: bool,
}

impl ProfileView {
    /// Artwork of the avatar, once the profile document is in hand.
    pub fn artwork(&self) -> Vec<(String, Option<String>)> {
        let Some(snapshot) = self.snapshot.as_ref() else {
            return Vec::new();
        };
        let Some(id) = snapshot.channel_id() else {
            return Vec::new();
        };
        vec![(
            posters::profile_key(id),
            snapshot.avatar_url().map(str::to_owned),
        )]
    }

    /// The header card: avatar, nickname, role, rating and the list counters.
    fn header<'a>(&'a self, snapshot: &'a ProfileSnapshot, cache: &'a PosterCache) -> Element<'a, Message> {
        let id = snapshot.channel_id().unwrap_or(0);
        let key = posters::profile_key(id);
        let avatar = widgets::artwork(cache, &key, snapshot.display_name(), 96, 96);

        let mut lines = Column::new().spacing(6).width(Length::Fill).push(
            widgets::headline_row(snapshot.display_name().to_owned()),
        );
        if let Some(bio) = snapshot.bio() {
            lines = lines.push(widgets::muted(bio.to_owned()));
        }
        let meta = widgets::join_meta(vec![
            privilege_label(snapshot.privilege()),
            format!("Рейтинг {}", snapshot.rating()),
            if snapshot.is_blocked() {
                "Заблокирован".to_owned()
            } else {
                String::new()
            },
        ]);
        lines = lines.push(widgets::muted(meta));

        // Счётчики списков и «не списки» (избранное, коллекции…) печатаются одной
        // строкой пилюль, поэтому обе половины сводятся к `(String, i32)`.
        let counters = snapshot
            .lists()
            .into_iter()
            .map(|(status, count)| (status.label().to_owned(), count))
            .chain(
                snapshot
                    .extras()
                    .into_iter()
                    .map(|(name, count)| (name.to_owned(), count)),
            )
            .fold(Row::new().spacing(8), |row, (caption, count)| {
                row.push(widgets::chip(format!("{caption} · {count}")))
            });

        widgets::card(
            Column::new()
                .spacing(14)
                .push(
                    Row::new()
                        .spacing(14)
                        .align_y(alignment::Vertical::Center)
                        .push(avatar)
                        .push(lines),
                )
                .push(widgets::divider())
                .push(counters.wrap()),
        )
    }

    pub fn view<'a>(&'a self, cache: &'a PosterCache) -> Element<'a, Message> {
        let mut body = Column::new().spacing(12);

        if !self.signed_in {
            body = body.push(widgets::empty_state(
                "Профиль доступен только вошедшим".to_owned(),
                "«Профиль» читает /profile/info и /profile/{id} — оба требуют токен \
                 сессии. Войдите любым способом на экране входа, и здесь появятся \
                 аватар, рейтинг и счётчики списков.".to_owned(),
            ));
            return widgets::scroll_body(body);
        }

        if self.loading {
            body = body.push(widgets::busy("Загрузка профиля…".to_owned()));
        }

        match self.snapshot.as_ref() {
            Some(snapshot) => body = body.push(self.header(snapshot, cache)),
            None if !self.loading => body = body.push(widgets::empty_state(
                "Профиль не загружен".to_owned(),
                "Нажмите «Профиль» ещё раз — запрос ушёл бы сразу, если сессия \
                 действительна.".to_owned(),
            )),
            None => {}
        }

        widgets::scroll_body(body)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn info() -> ProfileInfoResponse {
        ProfileInfoResponse {
            code: 1,
            channel_id: Some(77),
            privilege_level: 2,
            rating_score: 120,
            sponsorship_expires: 0,
            is_sponsor: false,
            is_perm_banned: false,
        }
    }

    fn profile() -> Profile {
        Profile {
            id: 77,
            login: "neon".to_owned(),
            avatar: "https://cdn.anixart.ru/avatars/77.png".to_owned(),
            status: Some("  Смотрю аниме  ".to_owned()),
            privilege_level: 2,
            rating_score: 140,
            watching_count: 3,
            plan_count: 4,
            completed_count: 5,
            hold_on_count: 1,
            dropped_count: 2,
            favorite_count: 9,
            comment_count: 11,
            collection_count: 2,
            friend_count: 30,
            ..Profile::default()
        }
    }

    #[test]
    fn a_snapshot_without_the_profile_document_still_answers() {
        let snapshot = ProfileSnapshot::new(info(), None);
        assert_eq!(snapshot.channel_id(), Some(77));
        assert_eq!(snapshot.display_name(), "Без имени");
        assert_eq!(snapshot.bio(), None);
        assert_eq!(snapshot.avatar_url(), None);
        // the info half is all there is, so its numbers are used
        assert_eq!(snapshot.rating(), 120);
        assert_eq!(snapshot.privilege(), 2);
        assert!(snapshot.lists().is_empty());
        assert_eq!(snapshot.list_total(), 0);
        assert!(snapshot.extras().is_empty());
        assert!(!snapshot.is_blocked());
    }

    #[test]
    fn zero_and_negative_channel_ids_are_not_an_id() {
        for id in [0, -1, i64::MIN] {
            let info = ProfileInfoResponse { channel_id: Some(id), ..info() };
            assert_eq!(ProfileSnapshot::new(info, None).channel_id(), None);
        }
        assert_eq!(ProfileSnapshot::default().channel_id(), None);
    }

    #[test]
    fn the_profile_document_overrides_the_info_half() {
        let snapshot = ProfileSnapshot::new(info(), Some(profile()));
        assert_eq!(snapshot.display_name(), "neon");
        assert_eq!(snapshot.rating(), 140);
        assert_eq!(snapshot.privilege(), 2);
        assert_eq!(snapshot.bio(), Some("Смотрю аниме"));
        assert_eq!(
            snapshot.avatar_url(),
            Some("https://cdn.anixart.ru/avatars/77.png")
        );
    }

    #[test]
    fn empty_and_relative_avatars_and_blank_status_are_nothing_to_show() {
        let mut profile = profile();
        profile.avatar = String::new();
        profile.status = Some("   ".to_owned());
        let snapshot = ProfileSnapshot::new(info(), Some(profile.clone()));
        assert_eq!(snapshot.avatar_url(), None);
        assert_eq!(snapshot.bio(), None);

        // a protocol-relative or relative path is not a URL the CDN call can use
        profile.avatar = "/avatars/77.png".to_owned();
        profile.status = None;
        let snapshot = ProfileSnapshot::new(info(), Some(profile));
        assert_eq!(snapshot.avatar_url(), None);
        assert_eq!(snapshot.bio(), None);
    }

    #[test]
    fn an_account_without_a_login_falls_back_instead_of_showing_nothing() {
        let mut profile = profile();
        profile.login = String::new();
        let snapshot = ProfileSnapshot::new(info(), Some(profile));
        assert_eq!(snapshot.display_name(), "Без имени");
    }

    #[test]
    fn list_counters_skip_the_unlisted_status_and_sum_up() {
        let snapshot = ProfileSnapshot::new(info(), Some(profile()));
        let lists = snapshot.lists();
        assert_eq!(lists.len(), 5);
        assert!(lists.iter().all(|(status, _)| !status.is_unlisted()));
        assert_eq!(lists[0], (WatchStatus::Watching, 3));
        assert_eq!(lists[4], (WatchStatus::Dropped, 2));
        assert_eq!(snapshot.list_total(), 3 + 4 + 5 + 1 + 2);
        // the counters are presented in the same order the tabs use
        let statuses: Vec<WatchStatus> = lists.iter().map(|(status, _)| *status).collect();
        assert_eq!(
            statuses,
            [
                WatchStatus::Watching,
                WatchStatus::Plan,
                WatchStatus::Completed,
                WatchStatus::HoldOn,
                WatchStatus::Dropped
            ]
        );
    }

    #[test]
    fn extras_are_the_four_non_list_counters_with_russian_names() {
        let extras = ProfileSnapshot::new(info(), Some(profile())).extras();
        let names: Vec<&str> = extras.iter().map(|(name, _)| *name).collect();
        assert_eq!(names, ["Избранное", "Коллекции", "Комментарии", "Друзья"]);
        assert_eq!(extras[0].1, 9);
        assert_eq!(extras[3].1, 30);
        for (name, _) in &extras {
            assert!(!name.is_empty());
        }
    }

    #[test]
    fn a_permanent_ban_from_either_response_blocks_the_screen() {
        // локальные привязки называются иначе, чем фабрики `info()` / `profile()`:
        // иначе последующие вызовы упираются в уже не функцию, а значение
        let banned = ProfileInfoResponse {
            is_perm_banned: true,
            ..info()
        };
        assert!(ProfileSnapshot::new(banned.clone(), None).is_blocked());

        let mut doc = profile();
        assert!(!ProfileSnapshot::new(info(), Some(doc.clone())).is_blocked());
        doc.is_banned = true;
        assert!(ProfileSnapshot::new(info(), Some(doc.clone())).is_blocked());
        doc.is_banned = false;
        doc.is_perm_banned = true;
        assert!(ProfileSnapshot::new(info(), Some(doc)).is_blocked());
        // the info half alone is enough, even with a clean profile document
        assert!(ProfileSnapshot::new(banned, Some(profile())).is_blocked());
    }

    #[test]
    fn roles_are_named_up_to_administrator_and_kept_numeric_beyond() {
        assert_eq!(privilege_label(0), "Пользователь");
        assert_eq!(privilege_label(1), "Куратор");
        assert_eq!(privilege_label(2), "Модератор");
        assert_eq!(privilege_label(3), "Администратор");
        assert_eq!(privilege_label(4), "Роль 4");
        assert_eq!(privilege_label(-1), "Роль -1");
        // no two known roles share a name
        let names: Vec<String> = (0..4).map(privilege_label).collect();
        let mut sorted = names.clone();
        sorted.sort();
        sorted.dedup();
        assert_eq!(sorted.len(), names.len());
    }
}
