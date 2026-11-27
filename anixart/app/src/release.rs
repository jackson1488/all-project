//! Страница релиза: обложка, метаданные, списки профиля и пошаговый спуск
//! «озвучка → источник → серия → mpv».
//!
//! Экран начинается карточкой с крупным постером слева и всем, что backend знает о
//! релизе, справа: статус, год, страна, студия, жанры, рейтинг, счётчики и описание.
//! Под шапкой — кнопка «В избранное» и ряд пилюль списков профиля ([`WatchStatus`]).
//! Обе группы работают только у вошедшего пользователя: `favorite/*` и
//! `profile/list/*` — вызовы `require_token()`, поэтому гостю вместо них показана
//! подсказка, а не запрос, который сервер заведомо отклонит.
//!
//! Досмотр начинается кнопкой «Воспроизвести», которая раскрывает ряд
//! «Выберите вариант» ([`VariantFilter`]: Все / Озвучки / Субтитры) — ровно как в
//! официальном клиенте. Выбор варианта только фильтрует список озвучек, а выбор
//! озвучки уводит на **отдельный экран источников и серий** ([`Screen::Sources`]
//! в [`crate::app`], [`ReleaseView::sources_view`]): там список видеохостингов
//! выбранной озвучки и, под ним, серии выбранного источника. Обратный путь —
//! «← Назад к озвучкам», и только потом страница релиза закрывается
//! ([`ReleaseView::back`]).
//!
//! Индексы в [`ReleaseView::types`] всегда настоящие позиции ответа сервера:
//! фильтр вариантов только скрывает ряды, поэтому [`Message::SelectType`]
//! (crate::app::Message::SelectType) не переномеровывается.
//!
//! Ответы сервера о смене статуса и избранного отражаются локально
//! ([`ReleaseView::apply_status`] / [`ReleaseView::apply_favorite`]) и проверяются
//! по id релиза, чтобы запоздавший ответ о прошлом релизе не перекрасил пилюли
//! нынешнего.

use anixart_core::{Episode, EpisodeType, Release, Source};
use iced::widget::{button, horizontal_space, text, Column, Row};
use iced::{alignment, Element, Length};

use crate::app::Message;
use crate::lists::{ReleaseStatus, VariantFilter, WatchStatus};
use crate::posters::{self, PosterCache};
use crate::util;
use crate::widgets;

/// Куда отступает «Назад» на текущей ступени спуска.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Back {
    /// Серии свернуты: на экране остались источники выбранной озвучки.
    Sources,
    /// Экран источников закрыт: возвращаемся к списку озвучек.
    Voiceovers,
    /// Сворачивать нечего — страница релиза закрывается.
    Close,
}

/// Состояние страницы релиза и текущего спуска по озвучкам.
#[derive(Debug, Default)]
pub struct ReleaseView {
    pub release: Option<Release>,
    pub loading: bool,
    pub types: Vec<EpisodeType>,
    pub loading_types: bool,
    /// Раскрыт ли ряд «Выберите вариант».
    pub choosing: bool,
    /// Активный фильтр вариантов.
    pub variant: VariantFilter,
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

    /// Название открытого релиза — для заголовка окна `mpv`.
    pub fn title(&self) -> Option<String> {
        self.release.as_ref().map(|release| widgets::release_title(release))
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

    /// Название выбранной озвучки — заголовок экрана источников.
    pub fn type_name(&self) -> Option<String> {
        self.selected_type
            .and_then(|index| self.types.get(index))
            .map(|kind| widgets::voiceover_title(kind))
    }

    /// id выбранного источника.
    pub fn source_id(&self) -> Option<i64> {
        self.selected_source
            .and_then(|index| self.sources.get(index))
            .map(|source| source.id)
    }

    /// Название выбранного источника.
    pub fn source_name(&self) -> Option<String> {
        self.selected_source
            .and_then(|index| self.sources.get(index))
            .map(|source| source.name.trim().to_owned())
    }

    /// Серия, выбранная для воспроизведения.
    pub fn episode(&self, index: usize) -> Option<&Episode> {
        self.episodes.get(index)
    }

    /// Подпись серии — такая же, как на её пилюле, и ровно та, что попадает
    /// в заголовок окна `mpv`.
    pub fn episode_label(&self, index: usize) -> Option<String> {
        self.episodes
            .get(index)
            .map(|episode| episode_caption(episode, index))
    }

    /// Озвучки и переводы, которые пропускает текущий фильтр вариантов, вместе с
    /// их настоящими позициями в ответе сервера.
    pub fn visible_types(&self) -> Vec<(usize, &EpisodeType)> {
        self.types
            .iter()
            .enumerate()
            .filter(|(_, kind)| self.variant.accepts(kind))
            .collect()
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

    /// Ряд «Выберите вариант» раскрыт, и какой фильтр в нём сейчас активен.
    pub fn choose_variant(&mut self, variant: VariantFilter) {
        self.choosing = true;
        self.variant = variant;
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
        self.choosing = false;
        self.variant = VariantFilter::default();
        self.loading_types = false;
        self.loading_sources = false;
        self.loading_episodes = false;
    }

    /// Отступает спуск на одну ступень. `at_sources` — на экране ли сейчас
    /// источники и серии (в [`crate::app`] это `Screen::Sources`).
    ///
    /// Порядок такой же, как в официальном клиенте: серии → источники → озвучки →
    /// закрытие страницы. Сворачиваясь к озвучкам, выбор озвучки тоже снимается,
    /// иначе её пилюля осталась бы неактивной и вернуться в источники было бы
    /// нечем.
    pub fn back(&mut self, at_sources: bool) -> Back {
        if !at_sources {
            return Back::Close;
        }

        if self.selected_source.is_some() {
            self.selected_source = None;
            self.episodes.clear();
            self.loading_episodes = false;
            self.playing = None;
            self.pending = None;
            return Back::Sources;
        }

        self.selected_type = None;
        self.sources.clear();
        self.loading_sources = false;
        self.episodes.clear();
        self.loading_episodes = false;
        self.playing = None;
        self.pending = None;
        Back::Voiceovers
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
            .push(playback_prompt(self))
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

    /// «Главная» страница релиза: шапка, ряд вариантов (когда раскрыт) и озвучки.
    pub fn view<'a>(&'a self, cache: &'a PosterCache) -> Element<'a, Message> {
        let Some(release) = self.release.as_ref() else {
            let caption = if self.loading {
                "Загрузка релиза…"
            } else {
                "Релиз не выбран — откройте любой плакат в «Обзоре», в «Закладках» \
                 или на «Главной»."
            };
            return widgets::scroll_body(
                Column::new().spacing(12).push(widgets::busy(caption.to_owned())),
            );
        };

        let mut body = Column::new()
            .spacing(12)
            .push(self.header(release, cache))
            .push(variants(self))
            .push(voiceovers(self));

        body = body.push(
            Row::new()
                .spacing(10)
                .align_y(alignment::Vertical::Center)
                .push(widgets::action("Назад к полке", Some(Message::ReleaseBack)))
                .push(horizontal_space()),
        );

        widgets::scroll_body(body)
    }

    /// Отдельный экран «Источники и серии» выбранной озвучки.
    ///
    /// Он открывается из списка озвучек и закрывается назад к нему же, поэтому
    /// верхней пилюлей стоит «← Назад к озвучкам», а не «Назад к полке».
    pub fn sources_view<'a>(&'a self, _cache: &'a PosterCache) -> Element<'a, Message> {
        let heading = match (self.title(), self.type_name()) {
            (Some(title), Some(kind)) => format!("{title} · {kind}"),
            (Some(title), None) => title,
            _ => "Источники и серии".to_owned(),
        };

        let mut body = Column::new()
            .spacing(12)
            .push(widgets::section(
                heading,
                widgets::action("← Назад к озвучкам", Some(Message::ReleaseBack)),
            ));

        if self.types.is_empty() && !self.loading_types {
            body = body.push(widgets::empty_state(
                "Озвучек у релиза нет".to_owned(),
                "Вернитесь к озвучкам и выберите другой релиз: без вариантов озвучки \
                 источникам и сериям взяться негде.".to_owned(),
            ));
            return widgets::scroll_body(body);
        }

        body = body.push(sources(self));

        if self.selected_source.is_some() {
            body = body.push(episodes(self));
        }

        widgets::scroll_body(body)
    }
}

/// Кнопка «Воспроизвести» и подсказка о том, на какой ступени спуска мы сейчас.
///
/// Порядок тот же, что у официального клиента: одно действие раскрывает выбор
/// варианта, три пилюли (Все / Озвучки / Субтитры) лишь фильтруют список
/// озвучек, а источники и серии уводят на отдельный экран.
fn playback_prompt<'a>(view: &ReleaseView) -> Element<'a, Message> {
    let caption = if view.choosing {
        "Менять вариант"
    } else {
        "Воспроизвести"
    };

    let hint = if view.loading_types {
        "Загружаю озвучки и субтитры релиза…".to_owned()
    } else if view.types.is_empty() {
        "У этого релиза пока нет ни озвучек, ни субтитров: как только они появятся, \
         «Воспроизвести» предложит выбрать вариант."
            .to_owned()
    } else if view.choosing {
        format!(
            "Показано {} из {} — фильтр скрывает ряды, но не переписывает их номера.",
            view.visible_types().len(),
            view.types.len()
        )
    } else {
        "Нажмите «Воспроизвести», чтобы выбрать озвучку или субтитры: источники и \
         серии откроются следующим экраном."
            .to_owned()
    };

    Column::new()
        .spacing(10)
        .push(
            Row::new()
                .spacing(10)
                .align_y(alignment::Vertical::Center)
                .push(widgets::action(caption, Some(Message::ReleaseChoose)))
                .push(widgets::muted(format!(
                    "Вариантов озвучки: {}",
                    util::count_label(view.types.len() as i64, "запись", "записи", "записей")
                )))
                .push(horizontal_space()),
        )
        .push(widgets::muted(hint))
        .into()
}

/// Ряд «Выберите вариант»: Все / Озвучки / Субтитры. Пустая колонка, пока ряд не
/// раскрыт кнопкой «Воспроизвести».
fn variants<'a>(view: &ReleaseView) -> Element<'a, Message> {
    if !view.choosing {
        return Column::new().into();
    }

    let pills = VariantFilter::ALL.iter().fold(Row::new().spacing(8), |row, variant| {
        row.push(widgets::pill(
            variant.label().to_owned(),
            *variant == view.variant,
            Message::SelectVariant(*variant),
        ))
    });

    widgets::section("Выберите вариант".to_owned(), pills.wrap())
}

/// Чипы метаданных релиза — только те поля, которые backend действительно прислал.
fn meta_chips(release: &Release) -> Vec<String> {
    let mut chips: Vec<String> = Vec::new();

    // Каталог отдаёт статус дважды: объектом `{id, name}` и голым `status_id`,
    // который в карточке фильтра остаётся нулём. Слово клиента надёжнее числа,
    // а [`ReleaseStatus`] подхватывает те id, где имени не пришло.
    let status = release
        .status
        .as_deref()
        .map(str::trim)
        .filter(|name| !name.is_empty())
        .map(str::to_owned)
        .or_else(|| ReleaseStatus::label_of_id(release.status_id).map(str::to_owned));
    if let Some(status) = status {
        chips.push(status);
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

/// Ряд озвучек и переводов открытого релиза, отфильтрованный активным вариантом.
///
/// `index` — позиция в *неотфильтрованном* ответе сервера, поэтому
/// [`Message::SelectType`] получает тот же id, что получил бы без фильтра, а
/// смена варианта не переписывает ни одну пилюлю.
fn voiceovers(view: &ReleaseView) -> Element<'_, Message> {
    let visible = view.visible_types();
    let caption = if view.choosing && visible.len() != view.types.len() {
        format!(
            "Озвучки и субтитры · {} из {} · фильтр «{}»",
            visible.len(),
            view.types.len(),
            view.variant
        )
    } else {
        format!(
            "Озвучки и субтитры · {}",
            util::count_label(view.types.len() as i64, "вариант", "варианта", "вариантов")
        )
    };

    if view.types.is_empty() {
        let note = if view.loading_types {
            "Загружаю озвучки…"
        } else {
            "У этого релиза озвучек нет — выберите другой или загляните позже."
        };
        return widgets::section(caption, widgets::busy(note.to_owned()));
    }

    if visible.is_empty() {
        return widgets::section(
            caption,
            widgets::muted(format!(
                "Под фильтр «{}» не попала ни одна запись — смените вариант в ряду выше.",
                view.variant
            )),
        );
    }

    let mut rows = Column::new().spacing(10);
    for (index, kind) in visible {
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
    fn back_winds_the_sources_screen_down_before_closing_the_page() {
        let mut view = ReleaseView {
            selected_type: Some(0),
            selected_source: Some(1),
            sources: vec![Source { id: 77, ..Default::default() }],
            episodes: vec![Episode { id: 9, ..Default::default() }],
            playing: Some(0),
            pending: Some(0),
            loading_episodes: true,
            ..Default::default()
        };

        // на странице релиза «Назад» закрывает её: спуск здесь не разворачивается
        assert_eq!(view.back(false), Back::Close);
        assert_eq!(view.selected_source, Some(1), "экран источников не тронут");

        assert_eq!(view.back(true), Back::Sources, "серии сворачиваются первыми");
        assert_eq!(view.selected_source, None);
        assert!(view.episodes.is_empty());
        assert!(!view.loading_episodes);
        assert_eq!(view.playing, None);
        assert_eq!(view.pending, None);
        assert_eq!(view.selected_type, Some(0), "озвучка остаётся выбранной");
        assert_eq!(view.sources.len(), 1, "источники остаются на экране");

        assert_eq!(view.back(true), Back::Voiceovers, "затем экран уступает озвучкам");
        assert_eq!(view.selected_type, None);
        assert!(view.sources.is_empty());
        assert!(!view.loading_sources);

        assert_eq!(view.back(true), Back::Voiceovers, "повторное нажатие ничего не ломает");
        assert_eq!(view.back(false), Back::Close);
    }

    #[test]
    fn the_variant_row_filters_the_list_without_renumbering_the_voiceovers() {
        let dub = EpisodeType {
            id: 5,
            name: "AniLibria".to_owned(),
            is_sub: false,
            ..Default::default()
        };
        let sub = EpisodeType {
            id: 6,
            name: "Субтитры | AniLibria".to_owned(),
            is_sub: true,
            ..Default::default()
        };

        let mut view = ReleaseView {
            types: vec![dub.clone(), sub.clone(), dub.clone()],
            ..Default::default()
        };
        view.choose_variant(VariantFilter::All);
        assert!(view.choosing);
        assert_eq!(
            view.visible_types().iter().map(|(i, _)| *i).collect::<Vec<usize>>(),
            vec![0, 1, 2],
            "«Все» показывает всё подряд настоящими индексами"
        );

        view.choose_variant(VariantFilter::Voiceover);
        assert_eq!(
            view.visible_types().iter().map(|(i, _)| *i).collect::<Vec<usize>>(),
            vec![0, 2],
            "субтитры скрыты, индексы не сдвинулись"
        );
        assert_eq!(view.visible_types()[1].1.id, 5);

        view.choose_variant(VariantFilter::Subtitle);
        assert_eq!(
            view.visible_types().iter().map(|(i, _)| *i).collect::<Vec<usize>>(),
            vec![1]
        );
        assert_eq!(view.visible_types()[0].1.id, 6);

        // ни одна запись не подошла: список пуст, но серверные индексы целы
        view.types = vec![sub.clone()];
        view.choose_variant(VariantFilter::Voiceover);
        assert!(view.visible_types().is_empty());
        assert_eq!(view.types.len(), 1);

        // выбор озвучки читается по индексу в неотфильтрованном ответе
        view.types = vec![dub, sub];
        view.selected_type = Some(1);
        assert_eq!(view.type_id(), Some(6));
        assert_eq!(view.type_name().as_deref(), Some("Субтитры | AniLibria"));
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
            loading_sources: true,
            loading_episodes: true,
            saving: true,
            pending: Some(0),
            choosing: true,
            variant: VariantFilter::Subtitle,
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
        assert!(!view.loading_sources);
        assert!(!view.loading_episodes);
        // ряд «Выберите вариант» свёрнут и сброшен на «Все» вместе с остальным спуском
        assert!(!view.choosing);
        assert_eq!(view.variant, VariantFilter::All);
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
        assert!(chips.contains(&"Анонс".to_owned()), "{chips:?}");
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
    fn the_status_word_of_the_backend_wins_over_the_client_table() {
        // карточка `POST /filter/*`: `status_id` остаётся нулём, а объект `status`
        // пришёл — слово сервера надёжнее таблицы клиента, которая по нулю молчит
        let card = Release {
            id: 1,
            status_id: 0,
            status: Some("Выходит".to_owned()),
            ..Default::default()
        };
        assert!(meta_chips(&card).contains(&"Выходит".to_owned()));

        // имя из одних пробелов — не имя: откат на справочник клиента по `status_id`
        let blank = Release {
            status_id: ReleaseStatus::Released.id(),
            status: Some("   ".to_owned()),
            ..Default::default()
        };
        assert!(meta_chips(&blank).contains(&"Вышел".to_owned()));

        // незнакомого числа нет: пустой чип не появляется
        let unknown = Release {
            status_id: 99,
            ..Default::default()
        };
        assert!(meta_chips(&unknown).iter().all(|chip| !chip.is_empty()));
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
            let _sources = stage.sources_view(&cache);
        }

        {
            let loading = ReleaseView {
                loading: true,
                loading_types: true,
                ..Default::default()
            };
            let _busy = loading.view(&cache);
            let _busy_sources = loading.sources_view(&cache);
        }

        let mut view = ReleaseView {
            release: Some(release(WatchStatus::Plan.code(), true)),
            signed_in: true,
            types: vec![
                EpisodeType {
                    id: 5,
                    name: "AniLibria".to_owned(),
                    episodes_count: 24,
                    view_count: 1200,
                    quality: 4,
                    pinned: true,
                    ..Default::default()
                },
                EpisodeType {
                    id: 6,
                    name: "Субтитры | AniLibria".to_owned(),
                    episodes_count: 24,
                    is_sub: true,
                    ..Default::default()
                },
            ],
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
            let _sources = view.sources_view(&cache);
        }

        // ряд «Выберите вариант» раскрыт и сужает список до субтитров
        view.choose_variant(VariantFilter::Subtitle);
        view.selected_type = Some(1);
        {
            let _choosing = view.view(&cache);
            let _subtitles = view.sources_view(&cache);
        }

        // под фильтр не попала ни одна запись
        view.variant = VariantFilter::Voiceover;
        view.types.retain(|kind| kind.is_sub);
        {
            let _nothing_visible = view.view(&cache);
        }

        view.pending = Some(0);
        view.loading_sources = true;
        view.loading_episodes = true;
        {
            let _resolving = view.view(&cache);
        }

        // озвучек у релиза нет вовсе: экран источников говорит об этом сам
        view.types.clear();
        view.selected_type = None;
        view.sources.clear();
        {
            let _no_voiceovers = view.sources_view(&cache);
        }

        // гость видит подсказку вместо кнопок списков
        view.signed_in = false;
        {
            let _guest = view.view(&cache);
        }
    }

    #[test]
    fn the_mpv_caption_is_taken_from_the_open_release_and_the_chosen_series() {
        let mut view = ReleaseView {
            release: Some(Release {
                id: 42,
                title_ru: None,
                title_original: Some("Bleach".to_owned()),
                ..Default::default()
            }),
            episodes: vec![
                Episode {
                    id: 1,
                    position: 12,
                    name: Some("Битва за Коноху".to_owned()),
                    ..Default::default()
                },
                Episode { id: 2, ..Default::default() },
            ],
            ..Default::default()
        };

        assert_eq!(view.title().as_deref(), Some("Bleach"));
        assert_eq!(
            view.episode_label(0).as_deref(),
            Some("№12 · Битва за Коноху")
        );
        assert_eq!(view.episode_label(1).as_deref(), Some("№2"));
        assert_eq!(view.episode_label(9), None, "за концом списка — ничего");

        view.release = None;
        assert_eq!(view.title(), None);
    }
}
