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
//! (crate::app::Message::SelectType) не переномеровывается. Тот же закон держит
//! список серий: [`ReleaseView::ordered_episodes`] только переставляет ряды по
//! [`EpisodeOrder`], а [`Message::PlayEpisode`] (crate::app::Message::PlayEpisode)
//! носит индекс вектора `episodes`, а не место на экране.
//!
//! Экран источников построен [`widgets::sticky_body`]: шапка с возвратом к
//! озвучкам, пилюлями видеохостингов и переключателем «Сначала 1-я / Сначала
//! новая» прикреплена к верху зоны, а под ней — тело, которому [`crate::app`]
//! передаёт измеренную ширину окна, поэтому ряды серий идут во всю его длину.
//!
//! Спуск вперёд идёт через кэш: [`ReleaseView::sources_by_type`] хранит источники
//! каждой уже виденной озвучки, [`ReleaseView::episodes_by_source`] — серии каждой
//! уже виденной пары «озвучка + источник». [`crate::app::App::route` наполняет их
//! и опережающими запросами — как только пришла озвучка, у неё спрашиваются
//! источники первого варианта, а как только пришли источники, серии первого из них
//! решаются вперёд. Повторный визит назад не отправляет к серверу ни одного
//! запроса, и список серий лежит на экране к моменту, когда экран успевает
//! отрисоваться.
//!
//! Ответы сервера о смене статуса и избранного отражаются локально
//! ([`ReleaseView::apply_status`] / [`ReleaseView::apply_favorite`]) и проверяются
//! по id релиза, чтобы запоздавший ответ о прошлом релизе не перекрасил пилюли
//! нынешнего.

use anixart_core::{Episode, EpisodeType, Release, Source};
use iced::widget::{button, container, horizontal_space, text, Column, Row};
use iced::{alignment, Element, Length};

use std::collections::HashMap;

use crate::app::Message;
use crate::lists::{ReleaseStatus, VariantFilter, WatchStatus};
use crate::posters::{self, PosterCache};
use crate::theme;
use crate::util;
use crate::widgets;

/// Порядок серий на экране источников.
///
/// Backend отдаёт серии как попало: у одного видеохостинга первой идёт свежая,
/// у другого — наоборот, а `position` иногда нулевой. Экран поэтому не полагается
/// на порядок ответа и сортирует сам, а пилюля в прикреплённой шапке выбирает
/// направление.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum EpisodeOrder {
    /// По возрастанию номера: сначала первая серия.
    #[default]
    First,
    /// По убыванию: свежая выпущенная серия наверху.
    Newest,
}

impl EpisodeOrder {
    /// Русская подпись пилюли-переключателя — она же состояние порядка в заголовке
    /// списка серий.
    pub fn label(self) -> &'static str {
        match self {
            Self::First => "Сначала 1-я",
            Self::Newest => "Сначала новая",
        }
    }

    /// Противоположный порядок: ровно то, что делает нажатие на пилюлю.
    pub fn toggled(self) -> Self {
        match self {
            Self::First => Self::Newest,
            Self::Newest => Self::First,
        }
    }
}

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
    /// Источники каждой уже виденной озвучки, ключ — её `id`.
    ///
    /// Спуск «озвучка → источник → серия» проходится по несколько раз: пользователь
    /// сравнивает варианты и возвращается назад. Второй визит на ту же озвучку
    /// обязан открыться мгновенно, без единого запроса, поэтому ответ хранится всё
    /// время, пока открыт релиз ([`ReleaseView::reset`] стирает кэш вместе со спуском).
    pub sources_by_type: HashMap<i64, Vec<Source>>,
    /// Серии каждой уже виденной пары «озвучка + источник», ключ — их id.
    pub episodes_by_source: HashMap<(i64, i64), Vec<Episode>>,
    /// Направление сортировки списка серий, выбранное пилюлей в шапке источников.
    pub episode_order: EpisodeOrder,
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
        self.release.as_ref().map(widgets::release_title)
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
            .map(widgets::voiceover_title)
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

    /// Кладёт источники озвучки в кэш и делает их текущим списком.
    pub fn remember_sources(&mut self, type_id: i64, sources: Vec<Source>) {
        self.sources_by_type.insert(type_id, sources.clone());
        self.sources = sources;
        self.loading_sources = false;
    }

    /// Текущие источники — из кэша, если эта озвучка уже открывалась.
    ///
    /// `true` значит «список готов, просить сервер не нужно» — именно так экран
    /// источников открывается со второго визита на ту же озвучку.
    pub fn cached_sources(&mut self, type_id: i64) -> bool {
        let Some(sources) = self.sources_by_type.get(&type_id).cloned() else {
            return false;
        };
        self.sources = sources;
        self.loading_sources = false;
        true
    }

    /// Кладёт серии пары «озвучка + источник» в кэш и делает их текущим списком.
    pub fn remember_episodes(&mut self, key: (i64, i64), episodes: Vec<Episode>) {
        self.episodes_by_source.insert(key, episodes.clone());
        self.episodes = episodes;
        self.loading_episodes = false;
    }

    /// Текущие серии — из кэша, если этот источник уже открывался.
    pub fn cached_episodes(&mut self, key: (i64, i64)) -> bool {
        let Some(episodes) = self.episodes_by_source.get(&key).cloned() else {
            return false;
        };
        self.episodes = episodes;
        self.loading_episodes = false;
        true
    }

    /// id первого источника выбранной озвучки — его серии догружаются заранее.
    pub fn first_source_id(&self) -> Option<i64> {
        self.sources.first().map(|source| source.id)
    }

    /// Уже ли лежат в кэше серии пары «озвучка + источник».
    pub fn has_cached_episodes(&self, key: (i64, i64)) -> bool {
        self.episodes_by_source.contains_key(&key)
    }

    /// Уже ли лежат в кэше источники озвучки.
    pub fn has_cached_sources(&self, type_id: i64) -> bool {
        self.sources_by_type.contains_key(&type_id)
    }

    /// Номер серии, по которому строится сортировка: настоящее `position` ответа
    /// сервера, а если его не пришло — место в исходном списке. Тот же закон, что
    /// у подписи ряда.
    fn episode_sort_key(&self, index: usize) -> i32 {
        match self.episodes.get(index) {
            Some(episode) if episode.position > 0 => episode.position,
            _ => index as i32 + 1,
        }
    }

    /// Индексы серий в выбранном порядке.
    ///
    /// Список только переставляется: `self.episodes` не меняется, поэтому
    /// [`Message::PlayEpisode`] носит настоящий индекс вектора, а не место ряда на
    /// экране, и `pending` / `playing` переживают переключение порядка. Равные
    /// номера остаются в порядке ответа сервера — сортировка устойчива.
    pub fn ordered_episodes(&self) -> Vec<usize> {
        let mut indices: Vec<usize> = (0..self.episodes.len()).collect();
        indices.sort_by(|left, right| {
            let (first, second) = (
                self.episode_sort_key(*left),
                self.episode_sort_key(*right),
            );
            let by_number = match self.episode_order {
                EpisodeOrder::First => first.cmp(&second),
                EpisodeOrder::Newest => second.cmp(&first),
            };
            by_number.then(left.cmp(right))
        });
        indices
    }

    /// Меняет направление списка серий.
    pub fn toggle_episode_order(&mut self) {
        self.episode_order = self.episode_order.toggled();
    }

    /// Подпись серии — такая же, как на её ряду, и ровно та, что попадает
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
    ///
    /// Порядок серий (`episode_order`) намеренно переживает сброс: это привычка
    /// просмотра, а не часть спуска по озвучке — её пилюля стоит в шапке источника
    /// и должна оставаться выбранной, пока пользователь листает релизы. Кэши
    /// источников и серий стираются: их id принадлежат прошлому релизу.
    pub fn reset(&mut self) {
        self.types.clear();
        self.selected_type = None;
        self.sources.clear();
        self.selected_source = None;
        self.episodes.clear();
        self.sources_by_type.clear();
        self.episodes_by_source.clear();
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
    /// в шапке стоит «← Назад к озвучкам». Сама шапка — возврат, ряд пилюль
    /// источников и пилюля-переключатель порядка серий — прикреплена к верху зоны
    /// и в прокрутку не уходит: серий у длинного тайтла бывают сотни, и ни возврат,
    /// ни выбор видеохостинга, ни «Сначала 1-я / Сначала новая» не должны уезжать
    /// вместе с ними. Ширина тела приходит извне ([`crate::app::App::body_width`]),
    /// поэтому ряды серий идут во всю ширину окна, а прокрутка строится один раз и
    /// не сбрасывает смещение при каждом перерисовывании.
    pub fn sources_view<'a>(&'a self, zone_width: f32) -> Element<'a, Message> {
        widgets::sticky_body(
            self.sources_header(),
            zone_width,
            false,
            move |_| self.sources_body(),
        )
    }

    /// Прикреплённая шапка экрана источников.
    fn sources_header<'a>(&'a self) -> Element<'a, Message> {
        let heading = match (self.title(), self.type_name()) {
            (Some(title), Some(kind)) => format!("{title} · {kind}"),
            (Some(title), None) => title,
            _ => "Источники и серии".to_owned(),
        };

        let top = Row::new()
            .spacing(10)
            .align_y(alignment::Vertical::Center)
            .push(
                button(widgets::button_label("← Назад к озвучкам", 14))
                    .padding([8, 16])
                    .style(crate::theme::styles::ghost)
                    .on_press(Message::ReleaseBack),
            )
            .push(widgets::headline_row(heading))
            .push(widgets::toggle_pill(
                self.episode_order.label().to_owned(),
                self.episode_order == EpisodeOrder::Newest,
                Message::ToggleEpisodeOrder,
            ));

        let mut header = Column::new().spacing(10).push(top);

        if !self.sources.is_empty() {
            let pills = self
                .sources
                .iter()
                .enumerate()
                .fold(Row::new().spacing(8), |row, (index, source)| {
                    row.push(widgets::pill(
                        source.name.trim().to_owned(),
                        self.selected_source == Some(index),
                        Message::SelectSource(index),
                    ))
                });
            header = header.push(widgets::section(
                format!(
                    "Источники · {}",
                    util::count_label(
                        self.sources.len() as i64,
                        "видеохостинг",
                        "видеохостинга",
                        "видеохостингов"
                    )
                ),
                pills.wrap(),
            ));
        }

        header.into()
    }

    /// Тело экрана источников: пока источник не выбран — подробный список с
    /// числами серий, выбранный — серии во всю ширину зоны.
    fn sources_body<'a>(&'a self) -> Column<'a, Message> {
        let body = Column::new().spacing(12);

        if self.types.is_empty() && !self.loading_types {
            return body.push(widgets::empty_state(
                "Озвучек у релиза нет".to_owned(),
                "Вернитесь к озвучкам и выберите другой релиз: без вариантов озвучки \
                 источникам и сериям взяться негде."
                    .to_owned(),
            ));
        }

        if self.selected_source.is_none() {
            return body.push(sources(self));
        }

        body.push(episodes(self))
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
        push_parts(&mut chips, country, "", PART_CHIPS);
    }
    if let Some(studio) = release
        .studio
        .as_deref()
        .map(str::trim)
        .filter(|text| !text.is_empty())
    {
        push_parts(&mut chips, studio, "Студия: ", PART_CHIPS);
    }
    if let Some(source) = release
        .source
        .as_deref()
        .map(str::trim)
        .filter(|text| !text.is_empty())
    {
        push_parts(&mut chips, source, "Источник: ", PART_CHIPS);
    }
    // Жанры приходят одной строкой через запятую — «драма, приключения, сэйнэн».
    // Один чип на такую строку и есть тот «высокий чип»: значения режутся на
    // отдельные, короткие, а излишек сворачивается в «ещё N».
    if let Some(genres) = release
        .genres
        .as_deref()
        .map(str::trim)
        .filter(|text| !text.is_empty())
    {
        push_parts(&mut chips, genres, "", GENRE_CHIPS);
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
        push_parts(&mut chips, translators, "Перевод: ", PART_CHIPS);
    }
    chips
}

/// Сколько отдельных чипов делает одно поле, в котором backend перечислил несколько
/// значений через запятую.
pub const PART_CHIPS: usize = 4;

/// Жанров показывается не больше этих шести: остальное — одним чипом «ещё N».
pub const GENRE_CHIPS: usize = 6;

/// Разбивает перечисление, которое backend прислал одной строкой, на отдельные
/// значения: «драма, приключения, сэйнэн» → три коротких чипа вместо одного
/// длинного. Разделители — запятая и точка с запятой; пустые куски отбрасываются,
/// повторы схлопываются, порядок остаётся серверным.
pub fn split_parts(raw: &str) -> Vec<String> {
    let mut parts: Vec<String> = Vec::new();
    for piece in raw.split([',', ';']) {
        let piece = piece.trim();
        if piece.is_empty() {
            continue;
        }
        if !parts.iter().any(|kept| kept == piece) {
            parts.push(piece.to_owned());
        }
    }
    parts
}

/// Добавляет чипы одного перечисления: первые `limit` значений с префиксом и, если
/// осталось, чип «ещё N» — так карточка не расползается на двадцать жанров.
fn push_parts(chips: &mut Vec<String>, raw: &str, prefix: &str, limit: usize) {
    let parts = split_parts(raw);
    for part in parts.iter().take(limit) {
        chips.push(if prefix.is_empty() {
            part.clone()
        } else {
            format!("{prefix}{part}")
        });
    }
    let spare = parts.len().saturating_sub(limit);
    if spare > 0 {
        chips.push(format!("ещё {spare}"));
    }
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

/// Серии выбранного источника — список во всю ширину зоны, отсортированный по
/// номеру серии.
///
/// Ряды строятся по индексам [`ReleaseView::ordered_episodes`], поэтому пилюля
/// «Смотреть» несёт настоящий индекс `self.episodes`, а не место ряда на экране:
/// переключение порядка не роняет ни `pending`, ни `playing`, ни заголовок окна
/// `mpv`. Пока решатель в полёте, весь список не нажимается — второе нажатие
/// подняло бы второй процесс поверх первого.
fn episodes(view: &ReleaseView) -> Element<'_, Message> {
    let caption = format!(
        "Серии · {} · {}",
        util::count_label(view.episodes.len() as i64, "эпизод", "эпизода", "эпизодов"),
        view.episode_order.label()
    );

    if view.episodes.is_empty() {
        let note = if view.loading_episodes {
            "Загружаю серии…"
        } else {
            "В этом источнике серий нет."
        };
        return widgets::section(caption, widgets::busy(note.to_owned()));
    }

    let waiting = view.pending.is_some();
    let mut rows = Column::new().spacing(6);
    for index in view.ordered_episodes() {
        let action = if view.pending == Some(index) {
            "Решаю ссылку…"
        } else if view.playing == Some(index) {
            "Играет"
        } else {
            "Смотреть"
        };

        rows = rows.push(episode_row(
            &view.episodes[index],
            index,
            view.playing == Some(index),
            action,
            (!waiting).then_some(Message::PlayEpisode(index)),
        ));
    }

    widgets::section(caption, rows)
}

/// Ширина заголовка ряда в символах: длинное имя серии обрезается многоточием,
/// и ряд не вырастает во вторую строку.
pub const EPISODE_TITLE_CHARS: usize = 48;

/// Компактный ряд серии — **одной строкой**: номер с именем слева, отметки чипом и
/// пилюля действия справа.
///
/// Строится здесь один раз, потому что список серий одинаков на экране источников и
/// во встроенном плеере ([`crate::player`]). Прежде ряд собирался из [`entry`] —
/// заголовок и мета-строка друг под другом, две строки высоты на серию, поэтому
/// сотня серий листалась экранами вместо одного списка.
///
/// `current` подсвечивает играемую серию: ряд получает выбранную карточку, а
/// пилюля — состояние «Играет». `action` — `&'static str`, потому что все три
/// подписи («Смотреть», «Играет», «Решаю ссылку…») известны из кода.
pub fn episode_row<'a>(
    episode: &Episode,
    index: usize,
    current: bool,
    action: &'static str,
    on_press: Option<Message>,
) -> Element<'a, Message> {
    let style: fn(&iced::Theme, button::Status) -> button::Style = if current {
        theme::styles::pill_active
    } else {
        theme::styles::pill
    };

    let marks = episode_marks(episode);
    let mut row = Row::new()
        .spacing(12)
        .align_y(alignment::Vertical::Center)
        .push(widgets::one_line(
            ellipsize(&episode_title(episode, index), EPISODE_TITLE_CHARS),
            14,
            theme::TEXT,
        ));

    if !marks.is_empty() {
        row = row.push(widgets::chip(marks));
    }

    container(row.push(
        button(widgets::button_label(action, 14).wrapping(text::Wrapping::None))
            .padding([8, 16])
            .style(style)
            .on_press_maybe(on_press),
    ))
    .padding([8, 14])
    .width(Length::Fill)
    .style(if current {
        theme::styles::card_selected
    } else {
        theme::styles::card
    })
    .into()
}

/// Заголовок, обрезанный многоточием до `limit` символов.
///
/// iced 0.13 не режет текст с «…»: строка либо переносится, либо переполняет
/// соседа. Список серий растёт переносом, поэтому обрезается здесь, по символам,
/// а не по байтам — русские имена серий в многобайтовых буквах.
pub fn ellipsize(caption: &str, limit: usize) -> String {
    let trimmed = caption.trim();
    if trimmed.chars().count() <= limit {
        return trimmed.to_owned();
    }
    let keep = trimmed.chars().take(limit.saturating_sub(1)).collect::<String>();
    format!("{}…", keep.trim_end())
}

/// Заголовок ряда: номер серии и её имя, когда backend потрудился его прислать.
fn episode_title(episode: &Episode, index: usize) -> String {
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

    format!("№{position}{name}")
}

/// Всё остальное, что известно о серии: качество, филлер, просмотрено.
fn episode_marks(episode: &Episode) -> String {
    let quality = widgets::quality_label(episode.quality);

    widgets::join_meta(vec![
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
    ])
}

/// Подпись серии целиком — заголовок ряда плюс её отметки, тем же порядком.
///
/// Публичная, потому что ею живут и заголовок окна `mpv`, и ряд списка серий во
/// встроенном плеере ([`crate::player`]).
pub fn episode_caption(episode: &Episode, index: usize) -> String {
    widgets::join_meta(vec![
        episode_title(episode, index),
        episode_marks(episode),
    ])
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

    /// Ширина зоны тела экрана, с которой в тестах собираются списки: окно 1280 px
    /// минус навигационная панель — ровно то, что даёт [`crate::app::App::body_width`].
    const ZONE_WIDTH: f32 = 1070.0;

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
    fn the_episode_order_sorts_without_renumbering_the_rows() {
        let mut view = ReleaseView {
            episodes: vec![
                Episode {
                    id: 30,
                    position: 3,
                    ..Default::default()
                },
                Episode {
                    id: 10,
                    position: 1,
                    ..Default::default()
                },
                Episode {
                    id: 20,
                    position: 2,
                    ..Default::default()
                },
                // без `position` серия нумеруется местом в ответе сервера — №4
                Episode {
                    id: 40,
                    ..Default::default()
                },
            ],
            ..Default::default()
        };

        assert_eq!(
            view.episode_order,
            EpisodeOrder::First,
            "по умолчанию список начинается с первой серии"
        );
        assert_eq!(view.ordered_episodes(), vec![1, 2, 0, 3]);

        view.toggle_episode_order();
        assert_eq!(view.episode_order, EpisodeOrder::Newest);
        assert_eq!(view.ordered_episodes(), vec![3, 0, 2, 1]);

        // перестановка не трогает сам список: индекс ряда остаётся настоящим
        assert_eq!(
            view
                .episodes
                .iter()
                .map(|episode| episode.id)
                .collect::<Vec<i64>>(),
            vec![30, 10, 20, 40]
        );
        assert_eq!(view.episode_label(1).as_deref(), Some("№1"));

        view.pending = Some(0);
        view.toggle_episode_order();
        assert_eq!(view.episode_order, EpisodeOrder::First);
        assert_eq!(
            view.episode(0).map(|episode| episode.id),
            Some(30),
            "ожидание решателя не съехало вместе с рядом"
        );

        assert_eq!(EpisodeOrder::First.label(), "Сначала 1-я");
        assert_eq!(EpisodeOrder::Newest.label(), "Сначала новая");
        assert_eq!(EpisodeOrder::First.toggled(), EpisodeOrder::Newest);
        assert_eq!(EpisodeOrder::Newest.toggled(), EpisodeOrder::First);
    }

    #[test]
    fn the_sources_screen_builds_headlessly_with_a_pinned_header_and_both_orders() {
        let mut view = ReleaseView {
            release: Some(release(1, false)),
            types: vec![EpisodeType {
                id: 5,
                name: "AniLibria".to_owned(),
                episodes_count: 2,
                ..Default::default()
            }],
            selected_type: Some(0),
            sources: vec![Source {
                id: 77,
                name: " Kodik ".to_owned(),
                episodes_count: 2,
                quality: 4,
            }],
            selected_source: Some(0),
            episodes: vec![
                Episode {
                    id: 2,
                    position: 2,
                    url: "https://kodik.example/2".to_owned(),
                    ..Default::default()
                },
                Episode {
                    id: 1,
                    position: 1,
                    url: "https://kodik.example/1".to_owned(),
                    is_watched: true,
                    ..Default::default()
                },
            ],
            ..Default::default()
        };

        {
            let _first = view.sources_view(ZONE_WIDTH);
        }
        view.toggle_episode_order();
        {
            let _newest = view.sources_view(ZONE_WIDTH);
        }

        // источник снят: пилюли в шапке остаются, тело пересказывает список хостов
        view.selected_source = None;
        {
            let _hosting_only = view.sources_view(ZONE_WIDTH);
        }

        // серия в полёте: ряды собираются и не нажимаются
        view.selected_source = Some(0);
        view.pending = Some(1);
        {
            let _resolving = view.sources_view(ZONE_WIDTH);
        }

        // озвучек вовсе нет: шапка жива, тело — одна подсказка
        view.types.clear();
        view.sources.clear();
        view.selected_type = None;
        {
            let _nothing = view.sources_view(ZONE_WIDTH);
        }
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
            let _sources = stage.sources_view(ZONE_WIDTH);
        }

        {
            let loading = ReleaseView {
                loading: true,
                loading_types: true,
                ..Default::default()
            };
            let _busy = loading.view(&cache);
            let _busy_sources = loading.sources_view(ZONE_WIDTH);
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
            let _sources = view.sources_view(ZONE_WIDTH);
        }

        // ряд «Выберите вариант» раскрыт и сужает список до субтитров
        view.choose_variant(VariantFilter::Subtitle);
        view.selected_type = Some(1);
        {
            let _choosing = view.view(&cache);
            let _subtitles = view.sources_view(ZONE_WIDTH);
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
            let _no_voiceovers = view.sources_view(ZONE_WIDTH);
        }

        // гость видит подсказку вместо кнопок списков
        view.signed_in = false;
        {
            let _guest = view.view(&cache);
        }
    }

    #[test]
    fn genres_arrive_as_one_line_and_leave_as_separate_chips() {
        // Та самая «высокая пилюля»: backend прислал девять жанров одной строкой.
        let view = Release {
            genres: Some(
                "драма, приключения, сэйнэн, сверхъестественное, психология, \
                 триллер, школа, детектив, фантастика"
                    .to_owned(),
            ),
            studio: Some("Pierrot, Studio Deen".to_owned()),
            translators: Some("AniLibria, Anidub".to_owned()),
            ..Default::default()
        };
        let chips = meta_chips(&view);

        assert!(chips.contains(&"драма".to_owned()), "{chips:?}");
        assert!(chips.contains(&"приключения".to_owned()), "{chips:?}");
        assert!(chips.contains(&"сэйнэн".to_owned()), "{chips:?}");
        assert!(
            !chips.iter().any(|chip| chip.contains(',')),
            "ни один чип не носит сразу несколько жанров: {chips:?}"
        );
        assert!(chips.contains(&"ещё 3".to_owned()), "{chips:?}");
        assert_eq!(
            chips.iter().filter(|chip| chip.starts_with("Студия: ")).count(),
            2,
            "перечисление студий тоже режется: {chips:?}"
        );
        assert!(chips.contains(&"Студия: Pierrot".to_owned()));
        assert!(chips.contains(&"Перевод: AniLibria".to_owned()));

        // пустое поле и поле из одних запятых не рождают чип-пустышку
        for blank in [None, Some(String::new()), Some("  , , ".to_owned())] {
            let chips = meta_chips(&Release {
                genres: blank.clone(),
                ..Default::default()
            });
            assert!(!chips.iter().any(|chip| chip.trim().is_empty()), "{chips:?}");
        }

        assert_eq!(split_parts("а, б;; в, а"), vec!["а", "б", "в"]);
        assert!(split_parts("   ").is_empty());
    }

    #[test]
    fn the_cache_answers_the_second_visit_without_a_request() {
        let dub = Source {
            id: 77,
            name: "Kodik".to_owned(),
            ..Default::default()
        };
        let sub = Source {
            id: 78,
            name: "Sibnet".to_owned(),
            ..Default::default()
        };
        let first = Episode {
            id: 1,
            position: 1,
            ..Default::default()
        };

        let mut view = ReleaseView {
            loading_sources: true,
            ..Default::default()
        };
        // первый визит: ответ сервера ложится в кэш и становится текущим списком
        view.remember_sources(5, vec![dub.clone(), sub.clone()]);
        assert_eq!(view.sources.len(), 2);
        assert!(!view.loading_sources);
        assert_eq!(view.first_source_id(), Some(77));
        assert!(view.has_cached_sources(5));
        assert!(!view.has_cached_sources(6));

        view.remember_episodes((5, 77), vec![first.clone()]);
        assert_eq!(view.episodes.len(), 1);
        assert!(!view.loading_episodes);
        assert!(view.has_cached_episodes((5, 77)));
        assert!(!view.has_cached_episodes((5, 78)));

        // уход назад к озвучкам сворачивает список, но не память
        view.selected_type = Some(0);
        view.selected_source = Some(0);
        assert_eq!(view.back(true), Back::Sources);
        assert!(view.episodes.is_empty());
        assert!(view.cached_episodes((5, 77)), "серии берутся из кэша");
        assert_eq!(view.episodes.len(), 1);

        assert_eq!(view.back(true), Back::Voiceovers);
        assert!(view.sources.is_empty());
        assert!(view.cached_sources(5), "источники берутся из кэша");
        assert_eq!(view.sources.len(), 2);

        // чужой кэш не подменяет список: запрос на неизвестную пару честно делается
        assert!(!view.cached_sources(9));
        assert!(!view.cached_episodes((9, 99)));

        // другой релиз — другая память: id источников и серий к нему не относятся
        view.reset();
        assert!(!view.has_cached_sources(5));
        assert!(!view.has_cached_episodes((5, 77)));
        assert!(view.sources.is_empty() && view.episodes.is_empty());
    }

    #[test]
    fn episode_rows_are_one_line_high_and_carry_the_true_index() {
        let named = Episode {
            id: 12,
            position: 12,
            name: Some("Битва за Коноху, которую ждали весь сезон и даже дольше обещанного".to_owned()),
            quality: 4,
            is_watched: true,
            url: "https://kodik.example/12".to_owned(),
            ..Default::default()
        };
        let anonymous = Episode {
            id: 13,
            ..Default::default()
        };

        // заголовок ряда обрезается, а не переносится: высота одна строка
        let title = ellipsize(&episode_title(&named, 0), EPISODE_TITLE_CHARS);
        assert!(title.ends_with('…'), "{title}");
        assert!(
            title.chars().count() <= EPISODE_TITLE_CHARS,
            "обрезанный заголовок не длиннее ряда: {title}"
        );
        assert_eq!(ellipsize("  №1  ", 48), "№1");
        assert_eq!(ellipsize("", 48), "");

        let _plain = episode_row(&named, 0, false, "Смотреть", Some(Message::PlayEpisode(0)));
        let _current = episode_row(&anonymous, 1, true, "Играет", None);
        let _waiting = episode_row(&anonymous, 1, false, "Решаю ссылку…", None);

        // подпись серии для окна `mpv` не обрезается: там хватает места
        assert_eq!(
            episode_caption(&named, 0),
            "№12 · Битва за Коноху, которую ждали весь сезон и даже дольше обещанного · 1080p · Просмотрено"
        );
        assert_eq!(episode_caption(&anonymous, 1), "№2");
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
