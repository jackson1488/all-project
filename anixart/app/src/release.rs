//! Страница релиза: обложка, метаданные, списки профиля и переход
//! «озвучка → источник → серия → mpv».
//!
//! Экран начинается карточкой с крупным постером слева и всем, что backend знает о
//! релизе, справа: статус, год, страна, студия, жанры, рейтинг, счётчики и описание.
//! Под шапкой — кнопка «В избранное» и ряд пилюль списков профиля ([`WatchStatus`]).
//! Обе группы работают только у вошедшего пользователя: `favorite/*` и
//! `profile/list/*` — вызовы `require_token()`, поэтому гостю вместо них показана
//! подсказка, а не запрос, который сервер заведомо отклонит.
//!
//! Ниже идёт привычный спуск: озвучки → источники → серии. Выбор каждой ступени
//! сбрасывает нижние, а «Назад» сворачивает их по одной, так что состояние экрана
//! описывается тремя индексами, а не вложенным деревом ([`ReleaseView::collapse`]).
//!
//! Ответы сервера о смене статуса и избранного отражаются локально
//! ([`ReleaseView::apply_status`] / [`ReleaseView::apply_favorite`]) и проверяются
//! по id релиза, чтобы запоздавший ответ о прошлом релизе не перекрасил пилюли
//! нынешнего.

use anixart_core::{Episode, EpisodeType, Release, Source};
use iced::widget::{button, horizontal_space, text, Column, Row};
use iced::{alignment, Element, Length};

use crate::app::Message;
use crate::lists::{ReleaseStatus, WatchStatus};
use crate::posters::{self, PosterCache};
use crate::util;
use crate::widgets;

/// Состояние страницы релиза и текущего спуска по озвучкам.
#[derive(Debug, Default)]
pub struct ReleaseView {
    pub release: Option<Release>,
    pub loading: bool,
    pub types: Vec<EpisodeType>,
    pub loading_types: bool,
    pub selected_type: Option<usize>,
    pub sources: Vec<Source>,
    pub loading_sources: bool,
    pub selected_source: Option<usize>,
    pub episodes: Vec<Episode>,
    pub loading_episodes: bool,
    pub pending: Option<usize>,
    pub playing: Option<usize>,
    /// `false` у гостя: кнопки списков становятся неактивными, а не ошибкой.
    pub signed_in: bool,
    /// Изменение избранного или статуса в полёте — повторные нажатия игнорируются.
    pub saving: bool,
}

impl ReleaseView {
    /// Обложка открытого релиза — единственное изображение, которое стоит тянуть.
    pub fn artwork(&self) -> Vec<(String, Option<String>)> {
        match self.release.as_ref() {
            Some(release) => vec![(
                posters::release_key(release.id),
                posters::cover_of(&release.image),
            )],
            None => Vec::new(),
        }
    }

    /// Статус списка профиля, который сейчас показан на пилюлях.
    pub fn status(&self) -> WatchStatus {
        WatchStatus::of_release(self.release.as_ref().map_or(0, |release| release.profile_list_status))
    }

    /// id открытого релиза, когда он уже пришёл.
    pub fn release_id(&self) -> Option<i64> {
        self.release.as_ref().map(|release| release.id)
    }

    /// id выбранной озвучки.
    pub fn type_id(&self) -> Option<i64> {
        self.selected_type
            .and_then(|index| self.types.get(index))
            .map(|kind| kind.id)
    }

    /// id выбранного источника.
    pub fn source_id(&self) -> Option<i64> {
        self.selected_source
            .and_then(|index| self.sources.get(index))
            .map(|source| source.id)
    }

    /// Серия, выбранная для воспроизведения.
    pub fn episode(&self, index: usize) -> Option<&Episode> {
        self.episodes.get(index)
    }

    /// Отражает ответ `profile/list/*` в локальной копии релиза.
    pub fn apply_status(&mut self, status: WatchStatus) {
        if let Some(release) = self.release.as_mut() {
            release.profile_list_status = status.code();
        }
        self.saving = false;
    }

    /// Отражает ответ `favorite/*` в локальной копии релиза.
    pub fn apply_favorite(&mut self, favorite: bool) {
        if let Some(release) = self.release.as_mut() {
            release.is_favorite = favorite;
        }
        self.saving = false;
    }

    /// Сбрасывает спуск при открытии другого релиза.
    pub fn reset(&mut self) {
        self.types.clear();
        self.selected_type = None;
        self.sources.clear();
        self.selected_source = None;
        self.episodes.clear();
        self.playing = None;
        self.pending = None;
        self.saving = false;
        self.loading_types = false;
        self.loading_sources = false;
        self.loading_episodes = false;
    }

    /// Сворачивает одну ступень спуска. `false` — сворачивать нечего, страницу
    /// можно закрывать.
    pub fn collapse(&mut self) -> bool {
        if self.selected_source.is_some() {
            self.selected_source = None;
            self.episodes.clear();
            self.loading_episodes = false;
            self.playing = None;
            self.pending = None;
            true
        } else if self.selected_type.is_some() {
            self.selected_type = None;
            self.sources.clear();
            self.loading_sources = false;
            self.episodes.clear();
            self.playing = None;
            self.pending = None;
            true
        } else {
            false
        }
    }

    /// Шапка: постер, названия, чипы метаданных, описание и кнопки списков.
    fn header<'a>(&'a self, release: &'a Release, cache: &'a PosterCache) -> Element<'a, Message> {
        let title = widgets::release_title(release);
        let poster = widgets::artwork(
            cache,
            &posters::release_key(release.id),
            &title,
            widgets::HERO_POSTER_WIDTH,
            widgets::HERO_POSTER_HEIGHT,
        );

        let mut facts = Column::new().spacing(8).width(Length::Fill);
        facts = facts.push(widgets::headline_row(title.clone()));
        if let Some(original) = release
            .title_original
            .as_deref()
            .map(str::trim)
            .filter(|text| !text.is_empty() && *text != title)
        {
            facts = facts.push(widgets::muted(original.to_owned()));
        }
        facts = facts.push(widgets::chip_row(meta_chips(release)));

        if let Some(description) = release
            .description
            .as_deref()
            .map(str::trim)
            .filter(|text| !text.is_empty())
        {
            facts = facts
                .push(widgets::divider())
                .push(widgets::paragraph(description.to_owned()));
        }

        facts = facts
            .push(widgets::divider())
            .push(list_actions(release, self.signed_in, self.saving));

        widgets::card(
            Row::new()
                .spacing(16)
                .align_y(alignment::Vertical::Top)
                .push(poster)
                .push(facts),
        )
    }

    pub fn view<'a>(&'a self, cache: &'a PosterCache) -> Element<'a, Message> {
        let Some(release) = self.release.as_ref() else {
            let caption = if self.loading {
                "Загрузка релиза…"
            } else {
                "Релиз не выбран — откройте любой плакат в «Обзоре» или в «Закладках»."
            };
            return widgets::scroll_body(
                Column::new().spacing(12).push(widgets::busy(caption.to_owned())),
            );
        };

        let mut body = Column::new()
            .spacing(12)
            .push(self.header(release, cache))
            .push(voiceovers(self));

        if self.selected_type.is_some() {
            body = body.push(sources(self));
        }
        if self.selected_source.is_some() {
            body = body.push(episodes(self));
        }

        body = body.push(
            Row::new()
                .spacing(10)
                .align_y(alignment::Vertical::Center)
                .push(widgets::action(
                    "Назад к полке",
                    Some(Message::ReleaseBack),
                ))
                .push(horizontal_space()),
        );

        widgets::scroll_body(body)
    }
}

/// Чипы метаданных релиза — только те поля, которые backend действительно прислал.
fn meta_chips(release: &Release) -> Vec<String> {
    let mut chips: Vec<String> = Vec::new();

    if let Some(label) = ReleaseStatus::label_of_id(release.status_id) {
        chips.push(label.to_owned());
    }
    if let Some(year) = release.year.as_deref().map(str::trim).filter(|text| !text.is_empty()) {
        chips.push(year.to_owned());
    }
    if release.season > 0 {
        chips.push(format!("Сезон {}", release.season));
    }
    if let Some(country) = release
        .country
        .as_deref()
        .map(str::trim)
        .filter(|text| !text.is_empty())
    {
        chips.push(country.to_owned());
    }
    if let Some(studio) = release
        .studio
        .as_deref()
        .map(str::trim)
        .filter(|text| !text.is_empty())
    {
        chips.push(format!("Студия: {studio}"));
    }
    if let Some(source) = release
        .source
        .as_deref()
        .map(str::trim)
        .filter(|text| !text.is_empty())
    {
        chips.push(format!("Источник: {source}"));
    }
    if let Some(genres) = release
        .genres
        .as_deref()
        .map(str::trim)
        .filter(|text| !text.is_empty())
    {
        chips.push(genres.to_owned());
    }
    if release.age_rating > 0 {
        chips.push(format!("{}+", release.age_rating));
    }
    if release.duration > 0 {
        chips.push(format!("{} мин", release.duration));
    }
    if release.episodes_released.is_some() || release.episodes_total.is_some() {
        chips.push(format!(
            "Серии: {}",
            util::episodes_label(release.episodes_released, release.episodes_total)
        ));
    }
    if let Some(grade) = widgets::grade_label(release.grade) {
        chips.push(format!("Оценка {grade}"));
    }
    if release.vote_count != 0 {
        chips.push(widgets::votes_label(release.vote_count));
    }
    if release.favorites_count != 0 {
        chips.push(format!(
            "{} в избранном",
            util::compact_count(i64::from(release.favorites_count))
        ));
    }
    chips.push(widgets::comments_label(release.comment_count));
    if release.is_adult {
        chips.push("18+ контент".to_owned());
    }
    if let Some(date) = release
        .release_date
        .as_deref()
        .map(str::trim)
        .filter(|text| !text.is_empty())
    {
        chips.push(format!("Выход: {date}"));
    }
    if let Some(translators) = release
        .translators
        .as_deref()
        .map(str::trim)
        .filter(|text| !text.is_empty())
    {
        chips.push(format!("Перевод: {translators}"));
    }
    chips
}

/// Кнопка избранного и пилюли списков профиля, либо подсказка для гостя.
fn list_actions(release: &Release, signed_in: bool, saving: bool) -> Element<'_, Message> {
    if !signed_in {
        return widgets::muted(
            "Избранное и списки доступны после входа: эти разделы принадлежат профилю \
             и требуют токен сессии."
                .to_owned(),
        );
    }

    let favorite = if saving {
        "Сохраняю…"
    } else if release.is_favorite {
        "Убрать из избранного"
    } else {
        "В избранное"
    };

    let current = WatchStatus::of_release(release.profile_list_status);
    let pills = WatchStatus::ALL.iter().fold(Row::new().spacing(8), |row, status| {
        row.push(widgets::pill(
            status.label().to_owned(),
            *status == current,
            Message::SetWatchStatus(*status),
        ))
    });

    Column::new()
        .spacing(10)
        .push(
            Row::new()
                .spacing(10)
                .align_y(alignment::Vertical::Center)
                .push(widgets::action(
                    favorite,
                    (!saving).then_some(Message::ToggleFavorite(!release.is_favorite)),
                ))
                .push(widgets::muted(format!("Список: {}", current.label())))
                .push(horizontal_space()),
        )
        .push(pills.wrap())
        .into()
}

/// Ряд озвучек и переводов открытого релиза.
fn voiceovers(view: &ReleaseView) -> Element<'_, Message> {
    let caption = format!(
        "Озвучки и субтитры · {}",
        util::count_label(view.types.len() as i64, "вариант", "варианта", "вариантов")
    );

    if view.types.is_empty() {
        let note = if view.loading_types {
            "Загружаю озвучки…"
        } else {
            "У этого релиза озвучек нет — выберите другой или загляните позже."
        };
        return widgets::section(caption, widgets::busy(note.to_owned()));
    }

    let mut rows = Column::new().spacing(10);
    for (index, kind) in view.types.iter().enumerate() {
        let active = view.selected_type == Some(index);
        let meta = widgets::join_meta(vec![
            util::count_label(kind.episodes_count, "серия", "серии", "серий"),
            util::count_label(kind.view_count, "просмотр", "просмотра", "просмотров"),
            widgets::quality_label(kind.quality).to_owned(),
            if kind.is_sub {
                "Субтитры".to_owned()
            } else {
                String::new()
            },
            if kind.pinned {
                "Приоритетная".to_owned()
            } else {
                String::new()
            },
        ]);

        rows = rows.push(entry(
            widgets::voiceover_title(kind),
            meta,
            if active { "Выбрано" } else { "Выбрать" },
            active,
            (!active).then_some(Message::SelectType(index)),
        ));
    }

    widgets::section(caption, rows)
}

/// Источники видео выбранной озвучки.
fn sources(view: &ReleaseView) -> Element<'_, Message> {
    let caption = format!(
        "Источники · {}",
        util::count_label(view.sources.len() as i64, "вариант", "варианта", "вариантов")
    );

    if view.sources.is_empty() {
        let note = if view.loading_sources {
            "Загружаю источники…"
        } else {
            "У этой озвучки нет источников видео."
        };
        return widgets::section(caption, widgets::busy(note.to_owned()));
    }

    let mut rows = Column::new().spacing(10);
    for (index, source) in view.sources.iter().enumerate() {
        let active = view.selected_source == Some(index);
        let meta = widgets::join_meta(vec![
            util::count_label(
                source.episodes_count,
                "серия",
                "серии",
                "серий",
            ),
            widgets::quality_label(source.quality).to_owned(),
        ]);

        rows = rows.push(entry(
            source.name.trim().to_owned(),
            meta,
            if active { "Выбрано" } else { "Выбрать" },
            active,
            (!active).then_some(Message::SelectSource(index)),
        ));
    }

    widgets::section(caption, rows)
}

/// Серии выбранного источника — ряд пилюль, потому что их бывает несколько сотен.
fn episodes(view: &ReleaseView) -> Element<'_, Message> {
    let caption = format!(
        "Серии · {}",
        util::count_label(view.episodes.len() as i64, "эпизод", "эпизода", "эпизодов")
    );

    if view.episodes.is_empty() {
        let note = if view.loading_episodes {
            "Загружаю серии…"
        } else {
            "В этом источнике серий нет."
        };
        return widgets::section(caption, widgets::busy(note.to_owned()));
    }

    let mut pills = Row::new().spacing(8);
    for (index, episode) in view.episodes.iter().enumerate() {
        let chosen = view.playing == Some(index) || view.pending == Some(index);
        pills = pills.push(widgets::pill(episode_caption(episode, index), chosen, Message::PlayEpisode(index)));
    }

    let mut body = Column::new().spacing(10).push(pills.wrap());
    if let Some(pending) = view.pending {
        let position = view
            .episodes
            .get(pending)
            .map(|episode| episode.position.max(0))
            .unwrap_or(0);
        body = body.push(widgets::busy(format!("Разбираю ссылку серии №{position}…")));
    }

    widgets::section(caption, body)
}

/// Подпись пилюли серии: номер, имя и всё, что о ней известно.
fn episode_caption(episode: &Episode, index: usize) -> String {
    let position = if episode.position > 0 {
        episode.position
    } else {
        index as i32 + 1
    };
    let name = episode
        .name
        .as_deref()
        .map(str::trim)
        .filter(|text| !text.is_empty())
        .map(|text| format!(" · {text}"))
        .unwrap_or_default();
    let quality = widgets::quality_label(episode.quality);
    let marks = widgets::join_meta(vec![
        if quality == "Default" {
            String::new()
        } else {
            quality.to_owned()
        },
        if episode.is_filler {
            "Филлер".to_owned()
        } else {
            String::new()
        },
        if episode.is_watched {
            "Просмотрено".to_owned()
        } else {
            String::new()
        },
    ]);

    if marks.is_empty() {
        format!("№{position}{name}")
    } else {
        format!("№{position}{name} · {marks}")
    }
}

/// Одна строка выбора: заголовок и мета-строка слева, пилюля действия справа.
///
/// Пилюля строится здесь, а не через [`widgets::pill`], потому что неактивное
/// состояние должно рождать именно отсутствие нажатия, а не запасное сообщение:
/// «Выбрать» гаснет, пока грузится следующий источник.
fn entry<'a>(
    title: String,
    meta: String,
    action: &str,
    active: bool,
    on_press: Option<Message>,
) -> Element<'a, Message> {
    let style: fn(&iced::Theme, button::Status) -> button::Style = if active {
        crate::theme::styles::pill_active
    } else {
        crate::theme::styles::pill
    };

    widgets::card(
        Row::new()
            .spacing(12)
            .align_y(alignment::Vertical::Center)
            .push(
                Column::new()
                    .spacing(4)
                    .width(Length::Fill)
                    .push(widgets::headline_row(title))
                    .push_maybe(if meta.is_empty() {
                        None
                    } else {
                        Some(widgets::muted(meta))
                    }),
            )
            .push(
                button(text(action.to_owned()).size(14).wrapping(text::Wrapping::Word))
                    .padding([8, 16])
                    .style(style)
                    .on_press_maybe(on_press),
            ),
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    fn release(status: i32, favorite: bool) -> Release {
        Release {
            id: 42,
            title_ru: Some("Наруто".to_owned()),
            title_original: Some("NARUTO".to_owned()),
            description: Some("  Ниндзя и его команда  ".to_owned()),
            image: Some("https://cdn/anixart/42.jpg".to_owned()),
            year: Some("2002".to_owned()),
            country: Some("Япония".to_owned()),
            studio: Some("Pierrot".to_owned()),
            genres: Some("Ниндзя, сёнэн".to_owned()),
            source: Some("Манга".to_owned()),
            translators: Some("AniLibria".to_owned()),
            grade: 8.4,
            age_rating: 12,
            duration: 24,
            season: 3,
            status_id: 3,
            episodes_released: Some(220),
            episodes_total: Some(220),
            favorites_count: 1500,
            comment_count: 12,
            vote_count: 3400,
            is_favorite: favorite,
            profile_list_status: status,
            release_date: Some("2002-10-03".to_owned()),
            ..Default::default()
        }
    }

    #[test]
    fn artwork_names_the_hero_poster_only_when_a_release_is_open() {
        let empty = ReleaseView::default();
        assert!(empty.artwork().is_empty());

        let view = ReleaseView {
            release: Some(release(1, false)),
            ..Default::default()
        };
        let artwork = view.artwork();
        assert_eq!(artwork.len(), 1);
        assert_eq!(artwork[0].0, posters::release_key(42));
        assert_eq!(artwork[0].1.as_deref(), Some("https://cdn/anixart/42.jpg"));
    }

    #[test]
    fn an_empty_poster_url_is_never_fetched() {
        let view = ReleaseView {
            release: Some(Release {
                id: 7,
                image: Some(String::new()),
                ..Default::default()
            }),
            ..Default::default()
        };
        assert_eq!(view.artwork()[0].1, None);
    }

    #[test]
    fn status_reads_the_profile_list_code() {
        let view = ReleaseView {
            release: Some(release(WatchStatus::Plan.code(), false)),
            ..Default::default()
        };
        assert_eq!(view.status(), WatchStatus::Plan);

        let none = ReleaseView::default();
        assert_eq!(none.status(), WatchStatus::None, "no release yet means no list");

        let unknown = ReleaseView {
            release: Some(Release {
                id: 1,
                profile_list_status: 99,
                ..Default::default()
            }),
            ..Default::default()
        };
        assert_eq!(unknown.status(), WatchStatus::None);
    }

    #[test]
    fn applying_a_reply_moves_the_local_copy_without_a_second_request() {
        let mut view = ReleaseView {
            release: Some(release(WatchStatus::Watching.code(), false)),
            saving: true,
            ..Default::default()
        };

        view.apply_status(WatchStatus::Completed);
        assert_eq!(view.status(), WatchStatus::Completed);
        assert_eq!(view.release.as_ref().unwrap().profile_list_status, 3);
        assert!(!view.saving);

        view.saving = true;
        view.apply_favorite(true);
        assert!(view.release.as_ref().unwrap().is_favorite);
        assert!(!view.saving, "the pills become pressable again");
    }

    #[test]
    fn collapse_winds_the_drill_down_back_one_step_at_a_time() {
        let mut view = ReleaseView {
            selected_type: Some(0),
            selected_source: Some(1),
            episodes: vec![Episode { id: 9, ..Default::default() }],
            playing: Some(0),
            pending: Some(0),
            ..Default::default()
        };

        assert!(view.collapse(), "the episodes fold first");
        assert_eq!(view.selected_source, None);
        assert!(view.episodes.is_empty());
        assert_eq!(view.playing, None);
        assert_eq!(view.selected_type, Some(0), "the voiceover stays");

        assert!(view.collapse(), "then the voiceover");
        assert_eq!(view.selected_type, None);
        assert!(view.sources.is_empty());

        assert!(!view.collapse(), "there is nothing left to fold");
    }

    #[test]
    fn reset_drops_every_stage_of_a_previous_release() {
        let mut view = ReleaseView {
            release: Some(release(1, false)),
            types: vec![EpisodeType { id: 5, ..Default::default() }],
            sources: vec![Source { id: 77, ..Default::default() }],
            episodes: vec![Episode { id: 1, ..Default::default() }],
            selected_type: Some(0),
            selected_source: Some(0),
            loading_types: true,
            saving: true,
            pending: Some(0),
            ..Default::default()
        };

        view.reset();
        assert!(view.types.is_empty());
        assert!(view.sources.is_empty());
        assert!(view.episodes.is_empty());
        assert_eq!(view.selected_type, None);
        assert_eq!(view.type_id(), None);
        assert!(!view.saving);
        assert!(!view.loading_types);
    }

    #[test]
    fn ids_of_the_current_selection_are_read_out_of_the_vectors() {
        let mut view = ReleaseView {
            types: vec![EpisodeType { id: 5, ..Default::default() }],
            sources: vec![Source { id: 77, ..Default::default() }],
            selected_type: Some(0),
            selected_source: Some(0),
            release: Some(release(1, false)),
            ..Default::default()
        };
        assert_eq!(view.release_id(), Some(42));
        assert_eq!(view.type_id(), Some(5));
        assert_eq!(view.source_id(), Some(77));
        assert_eq!(view.episode(0).map(|episode| episode.id), None);

        view.episodes.push(Episode { id: 3, ..Default::default() });
        assert_eq!(view.episode(0).map(|episode| episode.id), Some(3));

        // an index past the end is no id at all — a late reply must not panic
        view.selected_type = Some(9);
        assert_eq!(view.type_id(), None);
    }

    #[test]
    fn meta_chips_skip_every_field_the_backend_left_blank() {
        let chips = meta_chips(&Release::default());
        assert_eq!(chips, vec!["0 комментариев".to_owned()]);

        let chips = meta_chips(&release(1, true));
        assert!(chips.contains(&"Завершён".to_owned()), "{chips:?}");
        assert!(chips.contains(&"2002".to_owned()), "{chips:?}");
        assert!(chips.contains(&"Сезон 3".to_owned()));
        assert!(chips.contains(&"Япония".to_owned()));
        assert!(chips.contains(&"Студия: Pierrot".to_owned()));
        assert!(chips.contains(&"Источник: Манга".to_owned()));
        assert!(chips.contains(&"Перевод: AniLibria".to_owned()));
        assert!(chips.contains(&"12+".to_owned()));
        assert!(chips.contains(&"24 мин".to_owned()));
        assert!(chips.contains(&"Серии: 220".to_owned()));
        assert!(chips.contains(&"Оценка 8,4".to_owned()));
        assert!(chips.contains(&"3400 лайков".to_owned()));
        assert!(chips.contains(&"1,5 тыс. в избранном".to_owned()));
        assert!(chips.contains(&"12 комментариев".to_owned()));
        assert!(chips.contains(&"Выход: 2002-10-03".to_owned()));
        assert!(!chips.iter().any(|chip| chip.is_empty()), "{chips:?}");
        assert!(!chips.iter().any(|chip| chip.contains('(')), "{chips:?}");
    }

    #[test]
    fn episode_labels_number_from_the_position_then_the_index() {
        let named = Episode {
            position: 12,
            name: Some("Битва за Коноху".to_owned()),
            quality: 4,
            is_watched: true,
            ..Default::default()
        };
        assert_eq!(
            episode_caption(&named, 0),
            "№12 · Битва за Коноху · 1080p · Просмотрено"
        );

        let anonymous = Episode {
            quality: 0,
            ..Default::default()
        };
        assert_eq!(episode_caption(&anonymous, 2), "№3");

        let filler = Episode {
            position: 4,
            is_filler: true,
            quality: 3,
            ..Default::default()
        };
        assert_eq!(episode_caption(&filler, 0), "№4 · 720p · Филлер");
    }

    #[test]
    fn the_page_builds_headlessly_in_every_stage() {
        let cache = PosterCache::new();

        // `Element` живёт столько же, сколько заимствование экрана, поэтому каждый
        // случай собран в своём блоке: иначе следующая мутация `view` попала бы
        // в середину активного заимствования
        {
            let stage = ReleaseView::default();
            let _empty = stage.view(&cache);
        }

        {
            let loading = ReleaseView {
                loading: true,
                ..Default::default()
            };
            let _busy = loading.view(&cache);
        }

        let mut view = ReleaseView {
            release: Some(release(WatchStatus::Plan.code(), true)),
            signed_in: true,
            types: vec![EpisodeType {
                id: 5,
                name: "AniLibria".to_owned(),
                episodes_count: 24,
                view_count: 1200,
                quality: 4,
                pinned: true,
                ..Default::default()
            }],
            selected_type: Some(0),
            sources: vec![Source {
                id: 77,
                name: " Kodik ".to_owned(),
                episodes_count: 24,
                quality: 4,
            }],
            selected_source: Some(0),
            episodes: vec![Episode {
                id: 1,
                position: 1,
                url: "https://kodik.example/1".to_owned(),
                ..Default::default()
            }],
            ..Default::default()
        };
        {
            let _page = view.view(&cache);
        }

        view.pending = Some(0);
        {
            let _resolving = view.view(&cache);
        }

        // a guest sees the hint instead of pressable list buttons
        view.signed_in = false;
        {
            let _guest = view.view(&cache);
        }
    }
}
