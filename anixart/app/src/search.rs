use iced::{button, scrollable, text_input, Button, Column, Element, Length, Row, Scrollable, Text, TextInput};

use anixart_core::Release;

#[derive(Debug, Clone)]
pub enum Message {
    QueryChanged(String),
    Search,
    SetPage(usize),
    SelectResultIdx(String),
    OpenResult,
}

pub struct SearchScreen {
    pub query: String,
    pub results: Vec<Release>,
    pub page: usize,
    pub loading: bool,
    pub error: Option<String>,
    query_state: text_input::State,
    search_btn: button::State,
    scroll: scrollable::State,
    selected_idx_input: text_input::State,
    pub selected_idx_str: String,
    open_btn: button::State,
    next_btn: button::State,
}

impl SearchScreen {
    pub fn new() -> Self {
        Self {
            query: String::new(),
            results: Vec::new(),
            page: 0,
            loading: false,
            error: None,
            query_state: text_input::State::new(),
            search_btn: button::State::new(),
            scroll: scrollable::State::new(),
            selected_idx_input: text_input::State::new(),
            selected_idx_str: String::new(),
            open_btn: button::State::new(),
            next_btn: button::State::new(),
        }
    }

    pub fn view(&mut self) -> Element<'_, Message> {
        let input = TextInput::new(
            &mut self.query_state,
            "Search releases…",
            &self.query,
            Message::QueryChanged,
        );

        let mut col = Column::new()
            .push(Text::new("Search").size(20))
            .push(Row::new().push(input).push(
                Button::new(&mut self.search_btn, Text::new("Search"))
                    .on_press(Message::Search),
            ));

        if let Some(err) = &self.error {
            col = col.push(Text::new(err).color([1.0, 0.0, 0.0]));
        }

        if self.loading {
            col = col.push(Text::new("Searching…"));
        }

        // Show results as numbered text
        for (i, rel) in self.results.iter().enumerate() {
            let title = rel.title_ru.as_deref().unwrap_or("(no title)");
            let year = rel.year.as_deref().unwrap_or("?");
            let label = format!("[{i}] {title} ({year}) — {:.1}", rel.grade);
            col = col.push(Text::new(label).size(14));
        }

        if !self.results.is_empty() {
            let idx_input = TextInput::new(
                &mut self.selected_idx_input,
                "Enter # to open",
                &self.selected_idx_str,
                Message::SelectResultIdx,
            ).size(14).width(Length::Units(120));
            col = col.push(
                Row::new()
                    .push(idx_input)
                    .push(
                        Button::new(&mut self.open_btn, Text::new("Open"))
                            .on_press(Message::OpenResult),
                    ),
            );
            col = col.push(
                Button::new(&mut self.next_btn, Text::new("Next page"))
                    .on_press(Message::SetPage(self.page + 1)),
            );
        }

        Scrollable::new(&mut self.scroll)
            .push(col)
            .width(Length::Fill)
            .height(Length::Fill)
            .into()
    }
}
