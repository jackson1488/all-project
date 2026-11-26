//! Login screen: username / password form backed by `POST /auth/signin`.

use iced::widget::{button, container, text, text_input, Column};
use iced::{Element, Length};

use crate::app::Message;

/// Credentials form state.
#[derive(Debug, Default)]
pub struct Login {
    pub username: String,
    pub password: String,
    pub loading: bool,
}

impl Login {
    pub fn view(&self) -> Element<'_, Message> {
        let username = text_input("Username or e-mail", &self.username)
            .on_input(Message::LoginUsername)
            .on_submit(Message::LoginSubmit)
            .padding(10);

        let password = text_input("Password", &self.password)
            .secure(true)
            .on_input(Message::LoginPassword)
            .on_submit(Message::LoginSubmit)
            .padding(10);

        let submit = if self.loading {
            button("Logging in…").width(Length::Fill).padding(10)
        } else {
            button("Login")
                .width(Length::Fill)
                .padding(10)
                .on_press(Message::LoginSubmit)
        };

        container(
            Column::new()
                .spacing(10)
                .max_width(380)
                .push(text("Anixart").size(30))
                .push(
                    text("Sign in to reach your feed and collections")
                        .size(14)
                        .color([0.6, 0.6, 0.7]),
                )
                .push(username)
                .push(password)
                .push(submit),
        )
        .center_x(Length::Fill)
        .center_y(Length::Fill)
        .into()
    }
}
