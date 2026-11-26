use iced::{button, scrollable, Button, Column, Element, Length, Row, Scrollable, Text};

use anixart_core::FeedItem;

#[derive(Debug, Clone)]
pub enum Message {
    FeedPage(usize),
    FeedItemSelected(FeedItem),
}

pub struct FeedScreen {
    pub items: Vec<FeedItem>,
    pub page: usize,
    pub loading: bool,
    pub error: Option<String>,
    scroll: scrollable::State,
    next_btn: button::State,
    item_states: Vec<button::State>,
}

impl FeedScreen {
    pub fn new() -> Self {
        Self {
            items: Vec::new(),
            page: 0,
            loading: false,
            error: None,
            scroll: scrollable::State::new(),
            next_btn: button::State::new(),
            item_states: Vec::new(),
        }
    }

    pub fn view(&mut self) -> Element<'_, Message> {
        while self.item_states.len() < self.items.len() {
            self.item_states.push(button::State::new());
        }

        let mut col = Column::new()
            .push(Text::new(format!("Feed — page {}", self.page)).size(20));

        if let Some(err) = &self.error {
            col = col.push(Text::new(err).color([1.0, 0.0, 0.0]));
        }

        if self.loading {
            col = col.push(Text::new("Loading…"));
        }

        for (i, item) in self.items.iter().enumerate() {
            let title = item.title.as_deref().unwrap_or("(no title)");
            let label = format!(
                "[{}] {} (votes: {})",
                item.creation_date, title, item.vote_count
            );
            col = col.push(
                Button::new(&mut self.item_states[i], Text::new(label).size(14)),
            );
        }

        col = col.push(
            Row::new().push(
                Button::new(&mut self.next_btn, Text::new("Next page"))
                    .on_press(Message::FeedPage(self.page + 1)),
            ),
        );

        Scrollable::new(&mut self.scroll)
            .push(col)
            .width(Length::Fill)
            .height(Length::Fill)
            .into()
    }
}
