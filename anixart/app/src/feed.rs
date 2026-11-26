//! «Главная» — the Anixart article feed with its date-window pills.
//!
//! The screen is a list rather than a grid: an article is a headline, a channel
//! and a cover, and the official client shows it full width. The covers still
//! come from the poster cache, so the artwork of a page arrives a frame after the
//! text does — [`Feed::artwork`] is what the app turns into fetches.
//!
//! The six pills are [`FeedCategory`] values. «Последние» hits
//! `GET /article/all/{page}` and every other window hits `GET /feed/all/{page}`
//! with its `date` parameter, which is why the category is part of the fetch and
//! not a post-filter over one page of articles.

use anixart_core::{FeedItem, Pageable};
use iced::widget::{Column, Row};
use iced::Element;

use crate::app::Message;
use crate::lists::FeedCategory;
use crate::posters::{self, PosterCache};
use crate::widgets;

/// State of the home screen: one page of articles plus its category.
#[derive(Debug, Default)]
pub struct Feed {
    pub items: Vec<FeedItem>,
    pub page: usize,
    pub total_pages: usize,
    pub loading: bool,
    /// The date window the page was fetched with.
    pub category: FeedCategory,
}

impl Feed {
    /// Stores a fetched page; `None` content means the request failed and the
    /// previous articles stay on screen.
    pub fn apply(&mut self, page: Pageable<FeedItem>) {
        self.items = page.content;
        self.total_pages = page.total_page_count.max(0) as usize;
        self.page = page.current_page.max(0) as usize;
        self.loading = false;
    }

    /// The page number a fetch of this category should ask for, `0` as soon as the
    /// window changes — a different `date` has a different page count.
    pub fn page_for(&self, category: FeedCategory) -> usize {
        if category == self.category {
            self.page
        } else {
            0
        }
    }

    /// Artwork the visible articles need: `(cache key, image URL)` pairs.
    pub fn artwork(&self) -> Vec<(String, Option<String>)> {
        self.items
            .iter()
            .map(|item| {
                (
                    posters::article_key(item.id),
                    posters::cover_of(&item.image),
                )
            })
            .collect()
    }

    /// The category row, the article list and the pager, in one scroll area.
    pub fn view<'a>(&'a self, cache: &'a PosterCache) -> Element<'a, Message> {
        let pills = FeedCategory::ALL.iter().fold(Row::new().spacing(8), |row, category| {
            row.push(widgets::pill(
                category.label().to_owned(),
                *category == self.category,
                Message::FeedCategory(*category),
            ))
        });

        let mut body: Column<'a, Message> = Column::new()
            .spacing(12)
            .push(widgets::section(
                "Лента по датам".to_owned(),
                pills.wrap(),
            ));

        if self.loading {
            body = body.push(widgets::busy("Загрузка статей…".to_owned()));
        }

        if self.items.is_empty() && !self.loading {
            body = body.push(widgets::empty_state(
                "Статей нет".to_owned(),
                format!(
                    "В окне «{}» пока нечего показать — выберите другое окно или \
                     откройте «Главную» снова.",
                    self.category
                ),
            ));
        }

        for item in &self.items {
            body = body.push(widgets::article_card(cache, item));
        }

        if !self.items.is_empty() {
            body = body.push(widgets::pager(
                self.page,
                self.total_pages,
                Message::FeedPage(self.page.saturating_sub(1)),
                Message::FeedPage(self.page + 1),
            ));
        }

        widgets::scroll_body(body)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn pageable(items: Vec<FeedItem>, current: i32, total: i32) -> Pageable<FeedItem> {
        let total_count = items.len() as i64;
        Pageable {
            code: 1,
            content: items,
            current_page: current,
            total_count,
            total_page_count: total,
        }
    }

    #[test]
    fn a_fetched_page_replaces_the_previous_one() {
        let mut feed = Feed::default();
        feed.apply(pageable(
            vec![
                FeedItem {
                    id: 1,
                    image: Some("https://cdn/anixart/1.jpg".to_owned()),
                    ..Default::default()
                },
                FeedItem {
                    id: 2,
                    image: None,
                    ..Default::default()
                },
            ],
            2,
            9,
        ));

        assert_eq!(feed.items.len(), 2);
        assert_eq!(feed.page, 2);
        assert_eq!(feed.total_pages, 9);
        assert!(!feed.loading);
    }

    #[test]
    fn a_negative_page_count_is_not_a_size() {
        let mut feed = Feed::default();
        feed.apply(pageable(vec![], 0, -1));
        assert_eq!(feed.total_pages, 0);
        feed.apply(pageable(vec![], -5, 3));
        assert_eq!(feed.page, 0, "the API may answer 0-based or clamp");
    }

    #[test]
    fn changing_the_window_resets_the_page_and_staying_does_not() {
        let feed = Feed {
            category: FeedCategory::Week,
            page: 4,
            ..Default::default()
        };
        assert_eq!(feed.page_for(FeedCategory::Week), 4);
        assert_eq!(feed.page_for(FeedCategory::Month), 0);
        assert_eq!(feed.page_for(FeedCategory::Latest), 0);
    }

    #[test]
    fn artwork_names_every_article_once_and_skips_empty_urls() {
        let feed = Feed {
            items: vec![
                FeedItem {
                    id: 7,
                    image: Some("https://cdn/anixart/7.jpg".to_owned()),
                    ..Default::default()
                },
                FeedItem {
                    id: 8,
                    image: Some(String::new()),
                    ..Default::default()
                },
                FeedItem {
                    id: 9,
                    image: None,
                    ..Default::default()
                },
            ],
            ..Default::default()
        };
        let artwork = feed.artwork();
        assert_eq!(artwork.len(), 3);
        assert_eq!(artwork[0].0, posters::article_key(7));
        assert_eq!(artwork[0].1.as_deref(), Some("https://cdn/anixart/7.jpg"));
        assert_eq!(artwork[1].1, None, "an empty URL is no URL");
        assert_eq!(artwork[2].0, posters::article_key(9));
        // keys are unique, so one article never overwrites another
        let mut keys: Vec<&str> = artwork.iter().map(|(key, _)| key.as_str()).collect();
        keys.sort_unstable();
        keys.dedup();
        assert_eq!(keys.len(), 3);
    }
}
