//! Release page: voiceovers (types) → sources → episodes → mpv playback.

use anixart_core::{Episode, EpisodeType, Release, Source};
use iced::widget::{button, container, horizontal_space, scrollable, text, Column, Row};
use iced::{Element, Length};

use crate::app::Message;

/// State of the release page and of the current drill-down selection.
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
}

impl ReleaseView {
    pub fn view(&self) -> Element<'_, Message> {
        let Some(release) = &self.release else {
            let label = if self.loading {
                "Loading release…"
            } else {
                "No release selected — open one from Search."
            };
            return container(text(label).size(16))
                .center_x(Length::Fill)
                .center_y(Length::Fill)
                .into();
        };

        let title = release
            .title_ru
            .clone()
            .or_else(|| release.title_original.clone())
            .unwrap_or_else(|| "(untitled)".to_owned());
        let original = release.title_original.clone().unwrap_or_default();
        let description = release
            .description
            .clone()
            .unwrap_or_else(|| "(no description)".to_owned());
        let year = release.year.clone().unwrap_or_else(|| "—".to_owned());
        let grade = release.grade;
        let age = release.age_rating;
        let released = release.episodes_released.unwrap_or(0);
        let total = release.episodes_total.unwrap_or(0);

        let mut body = Column::new().spacing(10).push(
            container(
                Column::new()
                    .spacing(4)
                    .push(text(title).size(24))
                    .push(text(original).size(14).color([0.6, 0.6, 0.7]))
                    .push(
                        text(format!(
                            "rating {grade:.1} · {year} · {age}+ · {released}/{total} episodes"
                        ))
                        .size(13),
                    )
                    .push(text(description).size(13)),
            )
            .padding(14)
            .style(container::rounded_box),
        );

        // ── Voiceovers ─────────────────────────────────────────────────────
        let voiceovers = self.types.len();
        body = body.push(text(format!("Voiceovers ({voiceovers})")).size(18));

        if self.types.is_empty() {
            let label = if self.loading_types {
                "Loading voiceovers…"
            } else {
                "No voiceovers available."
            };
            body = body.push(text(label).color([0.6, 0.6, 0.7]));
        }

        for (index, ty) in self.types.iter().enumerate() {
            let active = self.selected_type == Some(index);
            let marker = if active { "✓ " } else { "" };
            let name = ty.name.clone();
            let episodes = ty.episodes_count;
            let quality = ty.quality;
            let sub = if ty.is_sub { " · subs" } else { "" };
            let choose = (!active).then_some(Message::SelectType(index));

            body = push_row(
                body,
                format!("{marker}{name}"),
                format!("{episodes} episodes · quality {quality}{sub}"),
                "Choose",
                choose,
            );
        }

        // ── Sources ────────────────────────────────────────────────────────
        if self.selected_type.is_some() {
            let sources = self.sources.len();
            body = body.push(text(format!("Sources ({sources})")).size(18));

            if self.sources.is_empty() {
                let label = if self.loading_sources {
                    "Loading sources…"
                } else {
                    "No sources for this voiceover."
                };
                body = body.push(text(label).color([0.6, 0.6, 0.7]));
            }

            for (index, source) in self.sources.iter().enumerate() {
                let active = self.selected_source == Some(index);
                let marker = if active { "✓ " } else { "" };
                let name = source.name.clone();
                let episodes = source.episodes_count;
                let quality = source.quality;
                let choose = (!active).then_some(Message::SelectSource(index));

                body = push_row(
                    body,
                    format!("{marker}{name}"),
                    format!("{episodes} episodes · quality {quality}"),
                    "Choose",
                    choose,
                );
            }
        }

        // ── Episodes ───────────────────────────────────────────────────────
        if self.selected_source.is_some() {
            let episodes = self.episodes.len();
            body = body.push(text(format!("Episodes ({episodes})")).size(18));

            if self.episodes.is_empty() {
                let label = if self.loading_episodes {
                    "Loading episodes…"
                } else {
                    "No episodes for this source."
                };
                body = body.push(text(label).color([0.6, 0.6, 0.7]));
            }

            for (index, episode) in self.episodes.iter().enumerate() {
                let position = episode.position;
                let name = episode
                    .name
                    .clone()
                    .unwrap_or_else(|| format!("Episode {position}"));
                let watched = if episode.is_watched { " · watched" } else { "" };
                let quality = episode.quality;
                let marker = if self.playing == Some(index) {
                    "▶ "
                } else if self.pending == Some(index) {
                    "⋯ "
                } else {
                    ""
                };
                let play = self.pending.is_none().then_some(Message::PlayEpisode(index));

                body = push_row(
                    body,
                    format!("{marker}{name}"),
                    format!("ep {position} · quality {quality}{watched}"),
                    "Play in mpv",
                    play,
                );
            }
        }

        body = body.push(button("← Back").on_press(Message::ReleaseBack));

        scrollable(body).height(Length::Fill).into()
    }
}

/// Appends one selectable list entry: title + metadata on the left, action on the right.
fn push_row<'a>(
    body: Column<'a, Message>,
    title: String,
    meta: String,
    action: &'static str,
    on_press: Option<Message>,
) -> Column<'a, Message> {
    body.push(
        Row::new()
            .spacing(10)
            .push(
                Column::new()
                    .spacing(2)
                    .push(text(title).size(15))
                    .push(text(meta).size(12).color([0.6, 0.6, 0.7])),
            )
            .push(horizontal_space())
            .push(button(action).on_press_maybe(on_press)),
    )
}
