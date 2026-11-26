use iced::{
    button, Button, Column, Container, Element, Length, Row, Text,
};

#[derive(Debug, Clone, Copy)]
pub enum Message {
    Login,
}

pub struct LoginForm {
    username: String,
    password: String,
    button_state: button::State,
}

impl LoginForm {
    pub fn new() -> Self {
        LoginForm {
            username: String::new(),
            password: String::new(),
            button_state: button::State::new(),
        }
    }

    pub fn view(&mut self) -> Element<Message> {
        let content = Column::new()
            .push(Text::new("Username:"))
            .push(iced::TextInput::new(
                &mut self.username,
                "Enter your username",
                "username",
                None,
            ))
            .push(Text::new("Password:"))
            .push(iced::Password::new(
                &mut self.password,
                "Enter your password",
                "password",
                None,
            ))
            .push(Row::new().push(Button::new(&mut self.button_state, Text::new("Login")).on_press(Message::Login)));

        Container::new(content)
            .width(Length::Fill)
            .height(Length::Fill)
            .center_x()
            .center_y()
            .into()
    }
}