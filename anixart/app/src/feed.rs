use iced::{button, Button, Column, Container, Element, Length, Scrollable, Text};

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
        }
    }

    pub fn view(&mut self) -> Element<'_, Message> {
        let mut col = Column::new()
            .push(Text::new(format!("Feed — page {}", self.page)).size(20));

        if let Some(err) = &self.error {
            col = col.push(Text::new(err).color([1.0, 0.0, 0.0]));
        }

        if self.loading {
            col = col.push(Text::new("Loading…"));
        }

        for item in &self.items {
            let title = item.title.as_deref().unwrap_or("(no title)");
            let label = format!(
                "[{}] {} (votes: {})",
                item.creation_date, title, item.vote_count
            );
            let mut btn = Button::new(&mut button::State::new(), Text::new(label).size(14));
            // FeedItemSelected maps to GoBack in main — we don't handle feed item tap
            col = col.push(btn);
        }

        let nav = iced::Row::new().push(
            Button::new(&mut self.next_btn, Text::new("Next page"))
                .on_press(Message::FeedPage(self.page + 1)),
        );
        col = col.push(nav);

        Scrollable::new(&mut self.scroll)
            .push(col)
            .width(Length::Fill)
            .height(Length::Fill)
            .into()
    }
}
