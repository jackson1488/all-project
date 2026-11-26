use iced::{button, scrollable, Button, Column, Element, Length, Scrollable, Text};

use anixart_core::{Episode, EpisodeType, Release, Source};

#[derive(Debug, Clone)]
pub enum Message {
    SelectType(usize),
    SelectSource(usize),
    SelectEpisode(usize),
    PlayEpisode(Episode),
    BackToRelease,
    BackToTypes,
    BackToSources,
}

pub struct ReleaseScreen {
    pub release: Option<Release>,
    pub types: Vec<EpisodeType>,
    pub sources: Vec<Source>,
    pub episodes: Vec<Episode>,
    pub selected_type_idx: Option<usize>,
    pub selected_source_idx: Option<usize>,
    pub loading_types: bool,
    pub loading_sources: bool,
    pub loading_episodes: bool,
    pub error: Option<String>,
    scroll: scrollable::State,
    type_states: Vec<button::State>,
    source_states: Vec<button::State>,
    episode_states: Vec<button::State>,
    back_to_sources_btn: button::State,
    back_to_types_btn: button::State,
}

impl ReleaseScreen {
    pub fn new() -> Self {
        Self {
            release: None,
            types: Vec::new(),
            sources: Vec::new(),
            episodes: Vec::new(),
            selected_type_idx: None,
            selected_source_idx: None,
            loading_types: false,
            loading_sources: false,
            loading_episodes: false,
            error: None,
            scroll: scrollable::State::new(),
            type_states: Vec::new(),
            source_states: Vec::new(),
            episode_states: Vec::new(),
            back_to_sources_btn: button::State::new(),
            back_to_types_btn: button::State::new(),
        }
    }

    pub fn view(&mut self) -> Element<'_, Message> {
        while self.type_states.len() < self.types.len() {
            self.type_states.push(button::State::new());
        }
        while self.source_states.len() < self.sources.len() {
            self.source_states.push(button::State::new());
        }
        while self.episode_states.len() < self.episodes.len() {
            self.episode_states.push(button::State::new());
        }

        let mut col = Column::new();

        if let Some(ref rel) = self.release {
            let title_ru = rel.title_ru.as_deref().unwrap_or("(no ru title)");
            let title_orig = rel.title_original.as_deref().unwrap_or("");
            let desc = rel.description.as_deref().unwrap_or("(no description)");

            col = col
                .push(Text::new(title_ru).size(22))
                .push(Text::new(title_orig).size(16).color([0.5, 0.5, 0.5]))
                .push(Text::new(format!(
                    "Rating: {} | Year: {} | Age: {}",
                    rel.grade,
                    rel.year.as_deref().unwrap_or("?"),
                    rel.age_rating
                )))
                .push(Text::new(desc).size(13));

            if let Some(err) = &self.error {
                col = col.push(Text::new(err).color([1.0, 0.0, 0.0]));
            }

            // Types / Voiceovers
            if !self.types.is_empty() {
                col = col.push(Text::new("— Voiceovers —").size(16));
                for (i, t) in self.types.iter().enumerate() {
                    let marker = if self.selected_type_idx == Some(i) { " >" } else { "" };
                    let label = format!("{}{} ({} ep, sub: {})", t.name, marker, t.episodes_count, t.is_sub);
                    col = col.push(
                        Button::new(&mut self.type_states[i], Text::new(label).size(13))
                            .on_press(Message::SelectType(i)),
                    );
                }
            } else if self.loading_types {
                col = col.push(Text::new("Loading voiceovers…"));
            }

            // Sources
            if self.selected_type_idx.is_some() && !self.sources.is_empty() {
                col = col.push(Text::new("—— Sources ——").size(16));
                for (i, s) in self.sources.iter().enumerate() {
                    let marker = if self.selected_source_idx == Some(i) { " >" } else { "" };
                    let label = format!("{}{} ({} ep)", s.name, marker, s.episodes_count);
                    col = col.push(
                        Button::new(&mut self.source_states[i], Text::new(label).size(13))
                            .on_press(Message::SelectSource(i)),
                    );
                }
            } else if self.selected_type_idx.is_some() && self.loading_sources {
                col = col.push(Text::new("Loading sources…"));
            }

            // Episodes
            if self.selected_source_idx.is_some() && !self.episodes.is_empty() {
                col = col.push(Text::new("——— Episodes ———").size(16));
                for (i, ep) in self.episodes.iter().enumerate() {
                    let ep_name = ep.name.as_deref().unwrap_or(&format!("Ep #{}", ep.position));
                    let watched = if ep.is_watched { " ✓" } else { "" };
                    let label = format!("{ep_name}{watched}");
                    col = col.push(
                        Button::new(&mut self.episode_states[i], Text::new(label).size(13))
                            .on_press(Message::PlayEpisode(ep.clone())),
                    );
                }
            } else if self.selected_source_idx.is_some() && self.loading_episodes {
                col = col.push(Text::new("Loading episodes…"));
            }

            // Back navigation
            if self.selected_source_idx.is_some() {
                col = col.push(
                    Button::new(&mut self.back_to_sources_btn, Text::new("← Back to sources"))
                        .on_press(Message::BackToSources),
                );
            } else if self.selected_type_idx.is_some() {
                col = col.push(
                    Button::new(&mut self.back_to_types_btn, Text::new("← Back to types"))
                        .on_press(Message::BackToRelease),
                );
            }
        } else {
            col = col.push(Text::new("No release loaded"));
        }

        Scrollable::new(&mut self.scroll)
            .push(col)
            .width(Length::Fill)
            .height(Length::Fill)
            .into()
    }
}
