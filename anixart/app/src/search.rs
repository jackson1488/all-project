use iced::{button, scrollable, text_input, Button, Column, Element, Length, Row, Scrollable, Text, TextInput};

use anixart_core::Release;

#[derive(Debug, Clone)]
pub enum Message {
    QueryChanged(String),
    Search,
    SetPage(usize),
    SelectResult(Release),
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
    result_states: Vec<button::State>,
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
            result_states: Vec::new(),
            next_btn: button::State::new(),
        }
    }

    pub fn view(&mut self) -> Element<'_, Message> {
        while self.result_states.len() < self.results.len() {
            self.result_states.push(button::State::new());
        }

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

        for (i, rel) in self.results.iter().enumerate() {
            let title = rel.title_ru.as_deref().unwrap_or("(no title)");
            let year = rel.year.as_deref().unwrap_or("?");
            let label = format!("{title} ({year}) — {:.1}", rel.grade);
            col = col.push(
                Button::new(&mut self.result_states[i], Text::new(label).size(14))
                    .on_press(Message::SelectResult(rel.clone())),
            );
        }

        if !self.results.is_empty() {
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
