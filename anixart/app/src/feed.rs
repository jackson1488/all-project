//! Feed screen: latest Anixart articles with pagination.

use anixart_core::FeedItem;
use iced::widget::{button, container, horizontal_space, scrollable, text, Column, Row};
use iced::{Element, Length};

use crate::app::Message;

/// Feed state (one page of articles at a time).
#[derive(Debug, Default)]
pub struct Feed {
    pub items: Vec<FeedItem>,
    pub page: usize,
    pub total_pages: usize,
    pub loading: bool,
}

impl Feed {
    pub fn view(&self) -> Element<'_, Message> {
        let page = self.page;
        let total_pages = self.total_pages;
        let page_number = page + 1;

        let mut body = Column::new().spacing(8).push(
            Row::new()
                .spacing(10)
                .push(text(format!("Feed — page {page_number} of {total_pages}")).size(18))
                .push(horizontal_space()),
        );

        if self.loading {
            body = body.push(text("Loading feed…").color([0.6, 0.6, 0.7]));
        }

        if self.items.is_empty() && !self.loading {
            body = body.push(
                text("No articles yet — sign in and open the Feed tab.")
                    .color([0.6, 0.6, 0.7]),
            );
        }

        for item in &self.items {
            let title = item
                .title
                .clone()
                .unwrap_or_else(|| "(no title)".to_owned());
            let channel = item
                .channel
                .as_ref()
                .and_then(|c| c.title.clone().or_else(|| c.name.clone()))
                .unwrap_or_else(|| "Anixart".to_owned());
            let created = item.creation_date;
            let votes = item.vote_count;
            let comments = item.comment_count;

            body = body.push(
                container(
                    Column::new().spacing(4).push(text(title).size(16)).push(
                        text(format!("{channel} · published {created} · ▲ {votes} · 💬 {comments}"))
                            .size(12)
                            .color([0.6, 0.6, 0.7]),
                    ),
                )
                .padding(12)
                .style(container::rounded_box),
            );
        }

        let has_prev = page > 0;
        let has_next = total_pages == 0 || page + 1 < total_pages;
        let prev = if has_prev {
            Some(Message::FeedPage(page - 1))
        } else {
            None
        };
        let next = if has_next {
            Some(Message::FeedPage(page + 1))
        } else {
            None
        };

        body = body.push(
            Row::new()
                .spacing(10)
                .push(button("← Prev").on_press_maybe(prev))
                .push(button("Next →").on_press_maybe(next)),
        );

        scrollable(body).height(Length::Fill).into()
    }
}
