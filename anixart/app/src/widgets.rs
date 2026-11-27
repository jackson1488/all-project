//! Reusable Material 3 pieces the Russian screens compose from.
//!
//! Everything here is a plain function that hands back an
//! [`Element`](iced::Element) of [`Message`](crate::app::Message): the screens stay
//! readable because the padding, corner radius and colour of a card are decided
//! once, in this module, instead of being repeated at every call site.
//!
//! The visual values come from [`crate::theme`]; the small text-shaping helpers
//! ([`grade_label`], [`quality_label`], [`join_meta`], [`release_title`]) are pure
//! and unit-tested, because a Russian interface lives or dies on its plurals and
//! on whether «7,8» is printed with a comma.
//!
//! Three shelves — «Главная», «Обзор», «Закладки» — are endless lists of posters,
//! and none of them has page buttons: [`scroll_infinite`] watches the viewport and
//! fires the load-more message when the user reaches [`SCROLL_TRIGGER`], the same
//! «догружаем у конца» that the official client does with its `NestedScrollView`.
//!
//! Two iced 0.13 constraints shape the code:
//!
//! * **No emoji or icon glyphs.** The embedded Fira Sans is rendered without a
//!   system font fallback, so a `💬` or a `⌂` would come out as tofu — counts are
//!   spelled out in Russian words instead.
//! * **Buttons never set a text colour.** `Text::color` wins over
//!   `button::Style::text_color`, so a pill that should turn white on the accent
//!   fill would stay grey. The button labels therefore leave the colour to the
//!   style function; the plain texts of a card set theirs explicitly.

use anixart_core::{Collection, EpisodeType, Release};
use iced::widget::scrollable::Viewport;
use iced::widget::{button, container, scrollable, text, Column, Row, Text};
use iced::{alignment, Element, Length};

use crate::app::Message;
use crate::posters::{self, PosterCache};
use crate::theme;
use crate::util;

/// Width of a poster tile in a grid, including its padding.
pub const TILE_WIDTH: u16 = 176;
/// Height of the artwork inside a tile.
pub const TILE_POSTER_HEIGHT: u16 = 232;
/// Width of the artwork on the release page.
pub const HERO_POSTER_WIDTH: u16 = 224;
/// Height of the artwork on the release page.
pub const HERO_POSTER_HEIGHT: u16 = 320;
/// Width of a feed article's thumbnail.
pub const THUMB_WIDTH: u16 = 96;
/// Height of a feed article's thumbnail.
pub const THUMB_HEIGHT: u16 = 132;

/// The qualities the Anixart backend numbers, in id order: `Release`,
/// `EpisodeType` and `Source` all carry `quality` as an index into this list.
pub const QUALITY_LABELS: [&str; 7] =
    ["Default", "360p", "480p", "720p", "1080p", "1440p", "2160p"];

/// Label of a numeric quality id; anything out of range reads as `Default`, the
/// same fallback the shipping client applies.
pub fn quality_label(quality: i32) -> &'static str {
    QUALITY_LABELS
        .get(quality.max(0) as usize)
        .copied()
        .unwrap_or(QUALITY_LABELS[0])
}

/// Rating of a release as the app prints it — `7,8`, comma as the decimal
/// separator. A grade of zero means "not rated", so nothing is shown at all.
pub fn grade_label(grade: f64) -> Option<String> {
    if !grade.is_finite() || grade <= 0.0 {
        return None;
    }
    let tenths = (grade * 10.0).round() as i64;
    Some(format!("{},{}", tenths / 10, tenths.abs() % 10))
}

/// Russian caption of a vote count: `1 лайк`, `2 лайка`, `5 лайков`.
pub fn votes_label(count: i32) -> String {
    util::count_label(i64::from(count), "лайк", "лайка", "лайков")
}

/// Russian caption of a comment count.
pub fn comments_label(count: i64) -> String {
    util::count_label(count, "комментарий", "комментария", "комментариев")
}

/// Russian caption of a view count.
pub fn views_label(count: i64) -> String {
    util::count_label(count, "просмотр", "просмотра", "просмотров")
}

/// Joins the non-empty parts of a meta line with ` · `, so a release without a
/// studio does not produce `2023 ·  · 12 эпизодов`.
pub fn join_meta(parts: Vec<String>) -> String {
    parts
        .into_iter()
        .filter(|part| !part.trim().is_empty())
        .collect::<Vec<_>>()
        .join(" · ")
}

/// Доля прокрутки, на которой бесконечная полка просит следующую страницу.
///
/// 0.88, а не 1.0: уведомление `on_scroll` приходит только когда смещение
/// *изменилось*, так что у самого дна последний кадр может и не успеть пройти
/// через порог — запас в 12 % гарантированно ловится ещё до последнего пикселя.
pub const SCROLL_TRIGGER: f32 = 0.88;

/// Догрели ли пользователь до конца полки — по относительному смещению прокрутки.
///
/// Чистая функция над `f32`, потому что ровно это решение принимает
/// [`crate::app`] на сообщение [`Message::Scrolled`](crate::app::Message::Scrolled):
/// виджет только передаёт смещение, а «пора догружать» живёт рядом с порогом.
pub fn reached_end(offset: f32) -> bool {
    offset >= SCROLL_TRIGGER
}

/// Half-open index ranges of a grid, `columns` tiles wide: `chunk(7, 3)` yields
/// `(0, 3)`, `(3, 6)`, `(6, 7)`. A column count below one means one column.
pub fn chunk(total: usize, columns: usize) -> Vec<(usize, usize)> {
    let columns = columns.max(1);
    (0..total.div_ceil(columns))
        .map(|row| {
            let start = row * columns;
            (start, (start + columns).min(total))
        })
        .collect()
}

/// Заголовок, который реально что-то говорит: `None` для пустого или состоящего
/// из пробелов значения, иначе — обрезанная ссылка.
fn non_blank(text: Option<&str>) -> Option<&str> {
    text.map(str::trim).filter(|value| !value.is_empty())
}

/// То же для опционального поля модели.
fn caption(field: &Option<String>) -> Option<&str> {
    non_blank(field.as_deref())
}

/// Display title of a release: the Russian one when the catalogue has it, then
/// the original, then the alternative, and a neutral caption when none came back.
pub fn release_title(release: &Release) -> String {
    caption(&release.title_ru)
        .or_else(|| caption(&release.title_original))
        .or_else(|| caption(&release.title_alt))
        .unwrap_or("Без названия")
        .to_owned()
}

/// Display name of a voiceover / translation, falling back to what the kind of
/// entry says it is when the API leaves `name` blank.
pub fn voiceover_title(kind: &EpisodeType) -> String {
    let name = kind.name.trim();
    if name.is_empty() {
        if kind.is_sub {
            "Субтитры".to_owned()
        } else {
            "Озвучка".to_owned()
        }
    } else {
        name.to_owned()
    }
}

/// A word-wrapped text of the given size and colour, shrunk to its content.
/// The size is a `u16`: font sizes here are whole points, and `Pixels` is built
/// from a `u16` — an unsuffixed literal would otherwise have to name `15.0`.
fn label<'a>(caption: String, size: u16, color: iced::Color) -> Text<'a> {
    text(caption)
        .size(size)
        .color(color)
        .wrapping(text::Wrapping::Word)
}

/// A text that stretches across its container, so a long caption wraps instead
/// of pushing the card wider.
fn line<'a>(caption: String, size: u16, color: iced::Color) -> Text<'a> {
    label(caption, size, color).width(Length::Fill)
}

/// The label of a button: same shape as [`line`], but the colour is left to the
/// button style, which is what makes the active pill readable on the accent fill.
/// Public because a screen that builds its own wide buttons needs the same rule.
pub fn button_label<'a>(caption: impl Into<String>, size: u16) -> Text<'a> {
    text(caption.into())
        .size(size)
        .wrapping(text::Wrapping::Word)
}

/// A large section heading.
pub fn headline<'a>(caption: String) -> Element<'a, Message> {
    label(caption, 26, theme::TEXT).into()
}

/// A smaller heading above a group of cards.
pub fn subhead<'a>(caption: String) -> Element<'a, Message> {
    label(caption, 17, theme::TEXT).into()
}

/// Secondary text: hints, captions, meta lines.
pub fn muted<'a>(caption: String) -> Element<'a, Message> {
    line(caption, 13, theme::TEXT_MUTED).into()
}

/// Secondary text, centred in the space it is given (tile captions, chips).
pub fn muted_centered<'a>(caption: String) -> Element<'a, Message> {
    line(caption, 13, theme::TEXT_MUTED)
        .align_x(alignment::Horizontal::Center)
        .into()
}

/// Body text of a description: wrapped at the word, at the glyph as a last
/// resort, with the roomier leading Material asks for a paragraph.
pub fn paragraph<'a>(caption: String) -> Element<'a, Message> {
    line(caption, 14, theme::TEXT)
        .wrapping(text::Wrapping::WordOrGlyph)
        .line_height(1.35)
        .into()
}

/// A small rounded label — year, studio, genre, quality.
pub fn chip<'a>(caption: String) -> Element<'a, Message> {
    container(muted_centered(caption))
        .padding([4, 12])
        .style(theme::styles::chip)
        .into()
}

/// Chips of a release, laid out one row per line of text.
pub fn chip_row<'a>(captions: Vec<String>) -> Element<'a, Message> {
    let mut rows = Column::new().spacing(6);
    let mut line = Row::new().spacing(6);
    let mut filled = false;

    for caption in captions {
        if caption.trim().is_empty() {
            continue;
        }
        line = line.push(chip(caption));
        filled = true;
    }
    if filled {
        rows = rows.push(line);
    }
    rows.into()
}

/// Pill button of a tab row — the widget the feed categories, the voiceover
/// filter and the bookmark tabs are all built from.
pub fn pill<'a>(caption: String, active: bool, message: Message) -> Element<'a, Message> {
    let style: fn(&iced::Theme, iced::widget::button::Status) -> iced::widget::button::Style =
        if active {
            theme::styles::pill_active
        } else {
            theme::styles::pill
        };

    button(button_label(caption, 14))
        .padding([8, 16])
        .style(style)
        .on_press_maybe((!active).then_some(message))
        .into()
}

/// Primary action button — «Искать», «Смотреть», «В список». It carries the
/// accent fill, so unlike a pill it is never drawn as "selected": an inert one
/// (`None`) simply cannot be pressed, which is how a loading screen disables
/// itself without changing its shape.
pub fn action<'a>(caption: &str, on_press: Option<Message>) -> Element<'a, Message> {
    button(button_label(caption, 14))
        .padding([10, 18])
        .style(theme::styles::accent)
        .on_press_maybe(on_press)
        .into()
}

/// Hairline separator across the width of its card.
pub fn divider<'a>() -> Element<'a, Message> {
    iced::widget::horizontal_rule(1).style(theme::styles::divider).into()
}

/// A large heading with a muted caption beside it — the profile nickname plus its
/// counters, a release title plus its year.
pub fn headline_row<'a>(caption: String) -> Element<'a, Message> {
    label(caption, 22, theme::TEXT)
        .width(Length::Fill)
        .wrapping(text::Wrapping::WordOrGlyph)
        .into()
}

/// A raised card that holds arbitrary content. The content is taken as
/// `impl Into<Element>` so a caller can hand a bare [`Column`] or [`Row`] — a
/// `.into()` written at the call site would leave iced guessing which
/// [`Element`](iced::Element) it converts to.
pub fn card<'a>(content: impl Into<Element<'a, Message>>) -> Element<'a, Message> {
    container(content)
        .padding(16)
        .width(Length::Fill)
        .style(theme::styles::card)
        .into()
}

/// A heading plus the content it introduces.
pub fn section<'a>(
    caption: String,
    content: impl Into<Element<'a, Message>>,
) -> Element<'a, Message> {
    Column::new()
        .spacing(10)
        .push(subhead(caption))
        .push(content)
        .into()
}

/// The scrollable body of a screen: full width, thin rails.
pub fn scroll_body<'a>(content: Column<'a, Message>) -> Element<'a, Message> {
    scrollable(
        content
            .spacing(14)
            .padding([16, 20])
            .width(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .style(theme::styles::scroll)
    .into()
}

/// Scrollable body of an *endless* shelf.
///
/// Same rails and padding as [`scroll_body`], plus two things the page buttons
/// used to do: a `footer` line under the last row, and — while `more` is `true` —
/// an `on_scroll` notification that turns the viewport offset into
/// [`Message::Scrolled`](crate::app::Message::Scrolled). The app compares that
/// offset with [`reached_end`] and asks for the next page, so a shelf grows by
/// scrolling and never by a «Вперёд» button.
///
/// `more` is switched off once the shelf knows it is done: an exhausted shelf
/// raises no notifications at all, which is the only cheap way to stop asking the
/// endpoint for pages it has already answered with nothing.
pub fn scroll_infinite<'a>(
    content: Column<'a, Message>,
    footer: Option<Element<'a, Message>>,
    more: bool,
) -> Element<'a, Message> {
    let mut column = content
        .spacing(14)
        .padding([16, 20])
        .width(Length::Fill);
    if let Some(footer) = footer {
        column = column.push(footer);
    }

    let mut body = scrollable(column)
        .width(Length::Fill)
        .height(Length::Fill)
        .style(theme::styles::scroll);

    if more {
        body = body.on_scroll(|viewport: Viewport| Message::Scrolled(viewport.relative_offset().y));
    }

    body.into()
}

/// Низ бесконечной полки: чем она закончилась, тем она и говорит.
///
/// Пока догрузка в полёте — строка ожидания, когда страницы кончились — сколько
/// релизов накоплено, а иначе — подсказка, что листать дальше не кнопками.
pub fn shelf_tail<'a>(loading_more: bool, exhausted: bool, loaded: usize) -> Element<'a, Message> {
    if loading_more {
        return busy("Загружаю следующие релизы…".to_owned());
    }
    if exhausted {
        return muted_centered(format!(
            "Показаны все релизы — {}",
            util::count_label(loaded as i64, "релиз", "релиза", "релизов")
        ));
    }
    muted_centered(
        "Прокрутите вниз: следующие релизы догрузятся сами, кнопок «Страница» здесь нет."
            .to_owned(),
    )
}

/// Empty-state card: what the section would hold, and what to do about it.
pub fn empty_state<'a>(caption: String, note: String) -> Element<'a, Message> {
    card(
        Column::new()
            .spacing(8)
            .push(subhead(caption))
            .push(muted(note)),
    )
}

/// A single panel that says the screen is waiting on the network.
pub fn busy<'a>(caption: String) -> Element<'a, Message> {
    container(muted(caption))
        .padding(16)
        .width(Length::Fill)
        .style(theme::styles::panel)
        .into()
}

/// Which colour a banner carries.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Tone {
    /// Informational (browser hand-off, restored session).
    Info,
    /// A warning that still leaves the app usable (clipboard, guest mode).
    Warn,
    /// A failed request.
    Error,
}

impl Tone {
    /// The colour of the caption, and of the tint behind it.
    pub fn color(self) -> iced::Color {
        match self {
            Tone::Info => theme::SUCCESS,
            Tone::Warn => theme::WARNING,
            Tone::Error => theme::ACCENT,
        }
    }
}

/// A dismissible banner above the screen body.
pub fn banner<'a>(caption: String, tone: Tone, dismiss: Message) -> Element<'a, Message> {
    container(
        Row::new()
            .spacing(12)
            .align_y(alignment::Vertical::Center)
            .push(
                text(caption)
                    .size(13)
                    .color(tone.color())
                    .width(Length::Fill)
                    .wrapping(text::Wrapping::WordOrGlyph),
            )
            .push(
                button(button_label("Скрыть", 12))
                    .padding([4, 10])
                    .style(theme::styles::ghost)
                    .on_press(dismiss),
            ),
    )
    .padding([10, 14])
    .width(Length::Fill)
    .style(theme::styles::banner(tone.color()))
    .into()
}

/// Advice that stays on screen while its tab is open — the Ctrl+V warning of the
/// paste-back tabs. Unlike [`banner`] it carries no dismiss button, because there
/// is nothing to dismiss: the limitation is the platform's, not a state of this
/// session, and hiding it would only lose the explanation for the button below.
pub fn note<'a>(caption: String, tone: Tone) -> Element<'a, Message> {
    container(
        text(caption)
            .size(13)
            .color(tone.color())
            .width(Length::Fill)
            .wrapping(text::Wrapping::WordOrGlyph),
    )
    .padding([10, 14])
    .width(Length::Fill)
    .style(theme::styles::banner(tone.color()))
    .into()
}

/// A poster tile of the endless shelves — «Главная», «Обзор», «Закладки». The whole
/// card is the button, so a wall of covers reads as a catalogue rather than as a
/// list of links.
///
/// The caption under the poster is what the official home card says: year, rating,
/// how many series are out, the catalogue status and, when the backend bothered to
/// fill it, the synopsis. A shelf of 25 tiles therefore answers «что это за релиз»
/// without opening it.
pub fn release_tile<'a>(cache: &PosterCache, release: &Release) -> Element<'a, Message> {
    let key = posters::release_key(release.id);
    let title = release_title(release);
    let artwork = posters::poster_element(
        cache.peek(&key),
        &key,
        &title,
        TILE_WIDTH - 24,
        TILE_POSTER_HEIGHT,
    );

    let status = release
        .status
        .as_deref()
        .map(str::trim)
        .filter(|label| !label.is_empty())
        .or(crate::lists::ReleaseStatus::label_of_id(release.status_id))
        .unwrap_or_default();

    let meta = join_meta(vec![
        release.year.clone().unwrap_or_default(),
        grade_label(release.grade).unwrap_or_default(),
        util::episodes_label(release.episodes_released, release.episodes_total),
        status.to_owned(),
    ]);

    let description = caption(&release.description).unwrap_or_default();

    let mut body = Column::new().spacing(6).push(artwork).push(
        container(
            line(title, 14, theme::TEXT)
                .wrapping(text::Wrapping::WordOrGlyph)
                .height(Length::Fixed(40.0)),
        )
        .height(Length::Fixed(42.0))
        .clip(true),
    );
    if !meta.is_empty() {
        body = body.push(muted_centered(meta));
    }
    if !description.is_empty() {
        body = body.push(
            container(
                line(description.to_owned(), 12, theme::TEXT_MUTED)
                    .wrapping(text::Wrapping::Word)
                    .height(Length::Fixed(44.0)),
            )
            .height(Length::Fixed(46.0))
            .clip(true),
        );
    }

    button(body.padding(12))
        .width(Length::Fixed(f32::from(TILE_WIDTH)))
        .style(theme::styles::card_button)
        .on_press(Message::OpenRelease(release.id))
        .into()
}

/// A poster box of arbitrary size, for the release page hero and the profile
/// avatar. `key` decides the placeholder tint, `title` the letter on it.
pub fn artwork<'a>(
    cache: &PosterCache,
    key: &str,
    title: &str,
    width: u16,
    height: u16,
) -> Element<'a, Message> {
    posters::poster_element(cache.peek(key), key, title, width, height)
}

/// Gap between the tiles of a poster grid, both ways.
pub const GRID_GAP: f32 = 12.0;
/// Default number of tiles per row when the window size is unknown.
pub const GRID_COLUMNS: usize = 4;

/// How many tiles fit in a window of the given width, at least one.
///
/// The grid is laid out in whole rows of [`TILE_WIDTH`] tiles, so a narrow window
/// simply gets fewer columns instead of squeezed posters.
pub fn grid_columns(width: f32, gap: f32) -> usize {
    let tile = f32::from(TILE_WIDTH) + gap;
    (((width - gap) / tile).floor() as usize).max(1)
}

/// The release tiles laid out `columns` per row.
///
/// iced 0.13 has no grid widget, so the rows are built from index ranges: a short
/// last row is padded with invisible spacers, which keeps every column the same
/// width instead of stretching the tail tiles.
pub fn poster_grid<'a>(
    cache: &PosterCache,
    releases: &[Release],
    columns: usize,
) -> Element<'a, Message> {
    let columns = columns.max(1);
    let mut rows = Column::new().spacing(GRID_GAP);

    for (start, end) in chunk(releases.len(), columns) {
        let mut row = Row::new().spacing(GRID_GAP);
        for release in &releases[start..end] {
            row = row.push(release_tile(cache, release));
        }
        // the spacers only hold the column widths, they never draw anything
        for _ in end..start + columns {
            row = row.push(
                container(text(""))
                    .width(Length::Fixed(f32::from(TILE_WIDTH)))
                    .height(Length::Shrink),
            );
        }
        rows = rows.push(row);
    }

    rows.into()
}

/// A collection row of the «Коллекции» tab: cover, title, author and two counters.
pub fn collection_card<'a>(cache: &PosterCache, collection: &Collection) -> Element<'a, Message> {
    let key = posters::collection_key(collection.id);
    let title = if collection.title.trim().is_empty() {
        "Коллекция без названия".to_owned()
    } else {
        collection.title.trim().to_owned()
    };
    let artwork = posters::poster_element(
        cache.peek(&key),
        &key,
        &title,
        THUMB_WIDTH,
        THUMB_HEIGHT,
    );

    let author = collection
        .creator
        .as_ref()
        .map(|creator| creator.login.trim())
        .filter(|login| !login.is_empty())
        .unwrap_or("Anixart");

    let description = collection.description.trim();
    let meta = join_meta(vec![
        author.to_owned(),
        util::date_label(collection.creation_date),
        util::count_label(
            collection.releases.len() as i64,
            "релиз",
            "релиза",
            "релизов",
        ),
    ]);

    let mut text_block = Column::new().spacing(6).push(
        line(title, 16, theme::TEXT).wrapping(text::Wrapping::WordOrGlyph),
    );
    if !description.is_empty() {
        text_block = text_block.push(
            container(
                line(description.to_owned(), 13, theme::TEXT_MUTED)
                    .wrapping(text::Wrapping::Word)
                    .height(Length::Fixed(58.0)),
            )
            .height(Length::Fixed(60.0))
            .clip(true),
        );
    }
    text_block = text_block
        .push(muted(meta))
        .push(muted(join_meta(vec![
            util::count_label(i64::from(collection.favorites_count), "лайк", "лайка", "лайков"),
            comments_label(collection.comment_count),
        ])));

    button(
        Row::new()
            .spacing(14)
            .align_y(alignment::Vertical::Center)
            .push(artwork)
            .push(text_block.width(Length::Fill))
            .padding(16),
    )
    .width(Length::Fill)
    .style(theme::styles::card_button)
    .on_press(Message::OpenCollection(collection.id))
    .into()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn quality_ids_follow_the_shipping_clients_list() {
        assert_eq!(quality_label(0), "Default");
        assert_eq!(quality_label(3), "720p");
        assert_eq!(quality_label(4), "1080p");
        assert_eq!(quality_label(6), "2160p");
        // out of range and negative ids fall back to Default, never panic
        assert_eq!(quality_label(7), "Default");
        assert_eq!(quality_label(99), "Default");
        assert_eq!(quality_label(-1), "Default");
        assert_eq!(QUALITY_LABELS.len(), 7);
    }

    #[test]
    fn grades_print_a_comma_and_hide_the_unrated() {
        assert_eq!(grade_label(7.8).as_deref(), Some("7,8"));
        assert_eq!(grade_label(8.0).as_deref(), Some("8,0"));
        assert_eq!(grade_label(9.99).as_deref(), Some("10,0"));
        assert_eq!(grade_label(0.0), None);
        assert_eq!(grade_label(-1.0), None);
        assert_eq!(grade_label(f64::NAN), None);
        assert_eq!(grade_label(f64::INFINITY), None);
        // a tenth of a point is the resolution the API publishes
        assert_eq!(grade_label(0.05).as_deref(), Some("0,1"));
    }

    #[test]
    fn meta_lines_drop_the_empty_parts() {
        assert_eq!(
            join_meta(vec!["2023".into(), String::new(), "12 эпизодов".into()]),
            "2023 · 12 эпизодов"
        );
        assert_eq!(join_meta(vec![]), "");
        assert_eq!(join_meta(vec!["   ".into(), "".into()]), "");
        assert_eq!(join_meta(vec!["Завершён".into()]), "Завершён");
    }

    #[test]
    fn counters_use_the_right_russian_plural_form() {
        assert_eq!(votes_label(1), "1 лайк");
        assert_eq!(votes_label(2), "2 лайка");
        assert_eq!(votes_label(5), "5 лайков");
        assert_eq!(votes_label(21), "21 лайк");
        assert_eq!(votes_label(0), "0 лайков");
        assert_eq!(comments_label(1), "1 комментарий");
        assert_eq!(comments_label(4), "4 комментария");
        assert_eq!(comments_label(11), "11 комментариев");
        assert_eq!(views_label(1_200), "1200 просмотров");
    }

    #[test]
    #[allow(clippy::assertions_on_constants)]
    fn the_scroll_trigger_fires_only_near_the_bottom_and_never_above_it() {
        assert!(!reached_end(0.0), "верх полки — не повод догружать");
        assert!(!reached_end(0.5));
        assert!(!reached_end(SCROLL_TRIGGER - 0.01));
        assert!(reached_end(SCROLL_TRIGGER), "сам порог уже считается дном");
        assert!(reached_end(0.95));
        assert!(reached_end(1.0), "дно — всегда повод");
        // порог оставлен с запасом до единицы: последний кадр прокрутки обязан
        // пойматься, пока смещение ещё не дошло до 1.0
        assert!(SCROLL_TRIGGER > 0.5, "половина полки — ещё не дно");
        assert!(SCROLL_TRIGGER < 1.0, "запас до дна гарантирует срабатывание");
        // отрицательное смещение — не дно, каким бы оно ни было в отладке
        assert!(!reached_end(-1.0));
    }

    #[test]
    fn the_shelf_tail_and_the_infinite_body_build_headlessly() {
        // Никакого рендера не нужно, чтобы собрать дерево: проверка, что нога полки
        // и бесконечное тело строятся без паники во всех трёх состояниях — загрузка,
        // конец выборки, «ещё есть куда идти».
        let _loading = shelf_tail(true, false, 25);
        let _end = shelf_tail(false, true, 48);
        let _more = shelf_tail(false, false, 25);

        let _no_footer = scroll_infinite(Column::new().push(container(text("25 плиток"))), None, true);
        let _with_footer = scroll_infinite(
            Column::new().push(container(text("48 плиток"))),
            Some(shelf_tail(false, true, 48)),
            false,
        );
        let _plain = scroll_body(Column::new().push(container(text("обычный экран"))));
    }

    #[test]
    fn grids_chunk_into_full_rows_and_a_short_tail() {
        assert!(chunk(0, 4).is_empty());
        assert_eq!(chunk(1, 4), vec![(0, 1)]);
        assert_eq!(chunk(4, 4), vec![(0, 4)]);
        assert_eq!(chunk(7, 3), vec![(0, 3), (3, 6), (6, 7)]);
        assert_eq!(
            chunk(5, 1),
            vec![(0, 1), (1, 2), (2, 3), (3, 4), (4, 5)]
        );
        // a zero column count is one column, never a division by zero
        assert_eq!(chunk(2, 0), vec![(0, 1), (1, 2)]);
        let total: usize = chunk(11, 4).iter().map(|(start, end)| end - start).sum();
        assert_eq!(total, 11);
    }

    #[test]
    fn release_titles_prefer_russian_then_original_then_alt() {
        let release = Release {
            title_ru: Some("Наруто".into()),
            title_original: Some("NARUTO".into()),
            ..Default::default()
        };
        assert_eq!(release_title(&release), "Наруто");

        let release = Release {
            title_ru: Some("   ".into()),
            title_original: Some("BLEACH".into()),
            ..Default::default()
        };
        assert_eq!(release_title(&release), "BLEACH");

        let release = Release {
            title_alt: Some("Блич".into()),
            ..Default::default()
        };
        assert_eq!(release_title(&release), "Блич");

        assert_eq!(release_title(&Release::default()), "Без названия");
    }

    #[test]
    fn voiceovers_name_themselves_when_the_api_gives_nothing() {
        let kind = EpisodeType {
            name: "AniLibria".into(),
            ..Default::default()
        };
        assert_eq!(voiceover_title(&kind), "AniLibria");

        let sub = EpisodeType {
            name: "  ".into(),
            is_sub: true,
            ..Default::default()
        };
        assert_eq!(voiceover_title(&sub), "Субтитры");

        let dub = EpisodeType {
            is_sub: false,
            ..Default::default()
        };
        assert_eq!(voiceover_title(&dub), "Озвучка");
    }

    #[test]
    fn tones_map_on_distinct_colours() {
        assert_ne!(Tone::Info.color(), Tone::Warn.color());
        assert_ne!(Tone::Warn.color(), Tone::Error.color());
        assert_ne!(Tone::Info.color(), Tone::Error.color());
        // an error banner is the accent red, an info banner the success green
        assert_eq!(Tone::Error.color(), theme::ACCENT);
        assert_eq!(Tone::Info.color(), theme::SUCCESS);
        for color in [Tone::Info.color(), Tone::Warn.color(), Tone::Error.color()] {
            assert!(color.r > 0.2, "{color:?} would vanish on a dark card");
            assert_eq!(color.a, 1.0);
        }
    }

    #[test]
    fn grid_columns_count_whole_tiles_and_never_drop_below_one() {
        // 1024 px at 176 + 12 gives five columns, 620 px the minimum window gives three
        assert_eq!(grid_columns(1024.0, GRID_GAP), 5);
        assert_eq!(grid_columns(620.0, GRID_GAP), 3);
        assert_eq!(grid_columns(200.0, GRID_GAP), 1);
        assert_eq!(grid_columns(0.0, GRID_GAP), 1);
        assert_eq!(grid_columns(-500.0, GRID_GAP), 1);
        // one tile exactly
        assert_eq!(grid_columns(f32::from(TILE_WIDTH), GRID_GAP), 1);
        // the default is a sane middle of that range, so a screen without a width
        // measurement still lays out like the window it ships in
        assert!((2..=grid_columns(1024.0, GRID_GAP)).contains(&GRID_COLUMNS));
        assert_eq!(GRID_GAP, 12.0);
    }

    #[test]
    fn grids_and_cards_build_headlessly() {
        // No renderer is needed to *compose* the tree, so this is the smoke test that
        // a wall of posters never panics on a short last row or on an empty page.
        let cache = PosterCache::new();
        let releases: Vec<Release> = (0..7)
            .map(|id| Release {
                id,
                title_ru: Some(format!("Релиз {id}")),
                image: Some(format!("https://cdn/anixart/{id}.jpg")),
                ..Default::default()
            })
            .collect();

        for columns in [0, 1, 3, 4, 9] {
            let _grid = poster_grid(&cache, &releases, columns);
        }
        let _empty = poster_grid(&cache, &[], 4);

        let collection = Collection {
            id: 5,
            title: "  ".to_owned(),
            description: "Зимние тайники".to_owned(),
            releases: vec![releases[0].clone()],
            ..Default::default()
        };
        let _card = collection_card(&cache, &collection);

        let with_author = Collection {
            id: 6,
            title: "Коллекция".to_owned(),
            creator: Some(anixart_core::Profile {
                login: "neon".to_owned(),
                ..Default::default()
            }),
            ..Default::default()
        };
        let _card = collection_card(&cache, &with_author);
    }

    #[test]
    #[allow(clippy::assertions_on_constants)]
    fn tile_dimensions_leave_room_for_the_padding() {
        assert!(TILE_WIDTH > 24, "the tile padding must not eat the artwork");
        assert!(TILE_POSTER_HEIGHT > TILE_WIDTH);
        assert!(HERO_POSTER_WIDTH > TILE_WIDTH - 24);
        assert!(HERO_POSTER_HEIGHT > TILE_POSTER_HEIGHT);
        assert!(THUMB_WIDTH < TILE_WIDTH);
        assert!(THUMB_HEIGHT < TILE_POSTER_HEIGHT);
    }
}
