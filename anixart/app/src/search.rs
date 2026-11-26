//! Search screen: release query, numbered results, and page stepping.

use anixart_core::Release;
use iced::widget::{
    button, container, horizontal_space, scrollable, text, text_input, Column, Row,
};
use iced::{Element, Length};

use crate::app::Message;

/// Search state (query + one page of results).
#[derive(Debug, Default)]
pub struct Search {
    pub query: String,
    pub results: Vec<Release>,
    pub page: usize,
    pub loading: bool,
}

impl Search {
    pub fn view(&self) -> Element<'_, Message> {
        let page = self.page;
        let loading = self.loading;

        let input = text_input("Search releases…", &self.query)
            .on_input(Message::SearchQuery)
            .on_submit(Message::SearchSubmit)
            .padding(10);

        let submit = if loading {
            button("Searching…").padding(10)
        } else {
            button("Search").padding(10).on_press(Message::SearchSubmit)
        };

        let mut body = Column::new()
            .spacing(8)
            .push(Row::new().spacing(10).push(input).push(submit));

        if loading {
            body = body.push(text("Searching…").color([0.6, 0.6, 0.7]));
        }

        if self.results.is_empty() && !loading {
            body = body.push(
                text("Type a query and press Enter — releases open on the right tab.")
                    .color([0.6, 0.6, 0.7]),
            );
        }

        for (index, release) in self.results.iter().enumerate() {
            let number = index + 1;
            let title = release
                .title_ru
                .clone()
                .or_else(|| release.title_original.clone())
                .unwrap_or_else(|| "(untitled)".to_owned());
            let original = release.title_original.clone().unwrap_or_default();
            let year = release.year.clone().unwrap_or_else(|| "—".to_owned());
            let grade = release.grade;
            let age = release.age_rating;
            let id = release.id;

            body = body.push(
                container(
                    Row::new()
                        .spacing(10)
                        .push(
                            Column::new().spacing(2).push(text(title).size(16)).push(
                                text(format!("#{number} {original} · {year} · rating {grade:.1} · {age}+"))
                                    .size(12)
                                    .color([0.6, 0.6, 0.7]),
                            ),
                        )
                        .push(horizontal_space())
                        .push(button("Open").on_press(Message::OpenRelease(id))),
                )
                .padding(12)
                .style(container::rounded_box),
            );
        }

        let has_results = !self.results.is_empty();
        let prev = if page > 0 {
            Some(Message::SearchPage(page - 1))
        } else {
            None
        };
        let next = if has_results {
            Some(Message::SearchPage(page + 1))
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
