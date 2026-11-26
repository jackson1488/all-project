//! Reusable Material 3 pieces the Russian screens compose from.
//!
//! Everything here is a plain function that hands back an
//! [`Element`](iced::Element) of [`Message`](crate::app::Message): the screens stay
//! readable because the padding, corner radius and colour of a card are decided
//! once, in this module, instead of being repeated at every call site.
//!
//! The visual values come from [`crate::theme`]; the small text-shaping helpers
//! ([`grade_label`], [`quality_label`], [`join_meta`], [`page_summary`]) are pure
//! and unit-tested, because a Russian interface lives or dies on its plurals and
//! on whether «7,8» is printed with a comma.
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

use anixart_core::{Collection, EpisodeType, FeedItem, Release};
use iced::widget::{button, container, horizontal_space, scrollable, text, Column, Row, Text};
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

/// `Стр. 3 из 12` — or `Стр. 3` while the total is still unknown, because the API
/// answers `total_page_count = 0` for an unpaginated list.
pub fn page_summary(page: usize, total_pages: usize) -> String {
    let shown = page + 1;
    if total_pages == 0 {
        format!("Стр. {shown}")
    } else {
        format!("Стр. {shown} из {}", total_pages.max(shown))
    }
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

/// Display title of a release: the Russian one when the catalogue has it, then
/// the original, then the alternative, and a neutral caption when none came back.
pub fn release_title(release: &Release) -> String {
    let pick = |field: &Option<String>| {
        field
            .as_deref()
            .map(str::trim)
            .filter(|title| !title.is_empty())
            .map(str::to_owned)
    };
    pick(&release.title_ru)
        .or_else(|| pick(&release.title_original))
        .or_else(|| pick(&release.title_alt))
        .unwrap_or_else(|| "Без названия".to_owned())
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
fn label<'a>(caption: String, size: f32, color: iced::Color) -> Text<'a> {
    text(caption)
        .size(size)
        .color(color)
        .wrapping(text::Wrapping::Word)
}

/// A text that stretches across its container, so a long caption wraps instead
/// of pushing the card wider.
fn line<'a>(caption: String, size: f32, color: iced::Color) -> Text<'a> {
    label(caption, size, color).width(Length::Fill)
}

/// The label of a button: same shape as [`line`], but the colour is left to the
/// button style, which is what makes the active pill readable on the accent fill.
/// Public because a screen that builds its own wide buttons needs the same rule.
pub fn button_label<'a>(caption: String, size: f32) -> Text<'a> {
    text(caption).size(size).wrapping(text::Wrapping::Word)
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
    button(button_label(caption.to_owned(), 14))
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

/// A raised card that holds arbitrary content.
pub fn card<'a>(content: Element<'a, Message>) -> Element<'a, Message> {
    container(content)
        .padding(16)
        .width(Length::Fill)
        .style(theme::styles::card)
        .into()
}

/// A heading plus the content it introduces.
pub fn section<'a>(caption: String, content: Element<'a, Message>) -> Element<'a, Message> {
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
            .width(Length::Fill)
            .height(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .style(theme::styles::scroll)
    .into()
}

/// Empty-state card: what the section would hold, and what to do about it.
pub fn empty_state<'a>(caption: String, note: String) -> Element<'a, Message> {
    card(
        Column::new()
            .spacing(8)
            .push(subhead(caption))
            .push(muted(note))
            .into(),
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

/// Page controls under a paginated grid.
pub fn pager<'a>(
    page: usize,
    total_pages: usize,
    prev: Message,
    next: Message,
) -> Element<'a, Message> {
    let has_prev = page > 0;
    let has_next = total_pages == 0 || page + 1 < total_pages;

    Row::new()
        .spacing(10)
        .align_y(alignment::Vertical::Center)
        .push(
            button(button_label("Назад", 14))
                .padding([8, 16])
                .style(theme::styles::pill)
                .on_press_maybe(has_prev.then_some(prev)),
        )
        .push(muted(page_summary(page, total_pages)))
        .push(
            button(button_label("Вперёд", 14))
                .padding([8, 16])
                .style(theme::styles::pill)
                .on_press_maybe(has_next.then_some(next)),
        )
        .push(horizontal_space())
        .into()
}

/// A poster tile of the browse / bookmarks grids. The whole card is the button,
/// so a wall of covers reads as a catalogue rather than as a list of links.
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

    let meta = join_meta(vec![
        release.year.clone().unwrap_or_default(),
        grade_label(release.grade).unwrap_or_default(),
        util::episodes_label(release.episodes_released, release.episodes_total),
    ]);

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

    button(body.padding(12))
        .width(Length::Fixed(f32::from(TILE_WIDTH)))
        .style(theme::styles::card_button)
        .on_press(Message::OpenRelease(release.id))
        .into()
}

/// A feed article row: cover, title, channel, date and the two counters.
pub fn article_card<'a>(cache: &PosterCache, item: &FeedItem) -> Element<'a, Message> {
    let key = posters::article_key(item.id);
    let title = item
        .title
        .as_deref()
        .map(str::trim)
        .filter(|t| !t.is_empty())
        .unwrap_or("Статья без названия")
        .to_owned();
    let artwork = posters::poster_element(
        cache.peek(&key),
        &key,
        &title,
        THUMB_WIDTH,
        THUMB_HEIGHT,
    );

    let channel = item
        .channel
        .as_ref()
        .and_then(|channel| channel.title.clone().or_else(|| channel.name.clone()))
        .map(|name| name.trim().to_owned())
        .filter(|name| !name.is_empty())
        .unwrap_or_else(|| "Anixart".to_owned());

    let mut parts = vec![channel, util::date_label(item.creation_date)];
    if item.is_pinned {
        parts.push("Закреплено".to_owned());
    }

    let counters = join_meta(vec![
        if item.vote_count == 0 {
            String::new()
        } else {
            votes_label(item.vote_count)
        },
        if item.comment_count == 0 {
            String::new()
        } else {
            comments_label(item.comment_count)
        },
    ]);

    card(
        Row::new()
            .spacing(14)
            .align_y(alignment::Vertical::Center)
            .push(artwork)
            .push(
                Column::new()
                    .spacing(6)
                    .push(
                        line(title, 16, theme::TEXT).wrapping(text::Wrapping::WordOrGlyph),
                    )
                    .push(muted(join_meta(parts)))
                    .push_maybe((!counters.is_empty()).then(|| muted(counters)))
                    .into(),
            )
            .into(),
    )
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
    let mut rows = Column::new().spacing(GRID_GAP as u16);

    for (start, end) in chunk(releases.len(), columns) {
        let mut row = Row::new().spacing(GRID_GAP as u16);
        for release in &releases[start..end] {
            row = row.push(release_tile(cache, release));
        }
        // the spacers only hold the column widths, they never draw anything
        for _ in end..start + columns {
            row = row.push(
                container(text("").size(1))
                    .width(Length::Fixed(f32::from(TILE_WIDTH)))
                    .into(),
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
        .map(|creator| creator.login.trim().to_owned())
        .filter(|login| !login.is_empty())
        .unwrap_or_else(|| "Anixart".to_owned());

    let description = collection.description.trim();
    let meta = join_meta(vec![
        author,
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
    fn page_counts_are_one_based_and_survive_an_unknown_total() {
        assert_eq!(page_summary(0, 0), "Стр. 1");
        assert_eq!(page_summary(0, 12), "Стр. 1 из 12");
        assert_eq!(page_summary(4, 12), "Стр. 5 из 12");
        // the API may report fewer pages than the one being displayed
        assert_eq!(page_summary(4, 2), "Стр. 5 из 5");
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
        assert!(GRID_COLUMNS >= 2 && GRID_COLUMNS <= grid_columns(1024.0, GRID_GAP));
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
    fn tile_dimensions_leave_room_for_the_padding() {
        assert!(TILE_WIDTH > 24, "the tile padding must not eat the artwork");
        assert!(TILE_POSTER_HEIGHT > TILE_WIDTH);
        assert!(HERO_POSTER_WIDTH > TILE_WIDTH - 24);
        assert!(HERO_POSTER_HEIGHT > TILE_POSTER_HEIGHT);
        assert!(THUMB_WIDTH < TILE_WIDTH);
        assert!(THUMB_HEIGHT < TILE_POSTER_HEIGHT);
    }
}
