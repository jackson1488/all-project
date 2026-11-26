use iced::{button, scrollable, text_input, Button, Column, Container, Element, Length, Row, Scrollable, Text, TextInput};

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
        }
    }

    pub fn view(&mut self) -> Element<'_, Message> {
        let input = TextInput::new(
            &mut self.query_state,
            "Search releases…",
            &self.query,
            Message::QueryChanged,
        );
        let search_btn = Button::new(&mut self.search_btn, Text::new("Search"))
            .on_press(Message::Search);

        let mut col = Column::new()
            .push(Text::new("Search").size(20))
            .push(Row::new().push(input).push(search_btn));

        if let Some(err) = &self.error {
            col = col.push(Text::new(err).color([1.0, 0.0, 0.0]));
        }

        if self.loading {
            col = col.push(Text::new("Searching…"));
        }

        for rel in &self.results {
            let title = rel.title_ru.as_deref().unwrap_or("(no title)");
            let year = rel.year.as_deref().unwrap_or("?");
            let label = format!("{title} ({year}) — {:.1}", rel.grade);
            let btn = Button::new(&mut button::State::new(), Text::new(label).size(14))
                .on_press(Message::SelectResult(rel.clone()));
            col = col.push(btn);
        }

        if !self.results.is_empty() {
            let next_btn = Button::new(&mut button::State::new(), Text::new("Next page"))
                .on_press(Message::SetPage(self.page + 1));
            col = col.push(next_btn);
        }

        Scrollable::new(&mut self.scroll)
            .push(col)
            .width(Length::Fill)
            .height(Length::Fill)
            .into()
    }
}
