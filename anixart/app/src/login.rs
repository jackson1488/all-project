use iced::{button, text_input, Button, Column, Container, Element, Length, Text, TextInput};

#[derive(Debug, Clone)]
pub enum Message {
    UsernameChanged(String),
    PasswordChanged(String),
    Login,
}

pub struct LoginForm {
    pub username: String,
    pub password: String,
    username_state: text_input::State,
    password_state: text_input::State,
    login_state: button::State,
    pub error: Option<String>,
    pub loading: bool,
}

impl LoginForm {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn set_username(&mut self, username: String) {
        self.username = username;
    }

    pub fn set_password(&mut self, password: String) {
        self.password = password;
    }

    pub fn view(&mut self) -> Element<'_, Message> {
        let username = TextInput::new(
            &mut self.username_state,
            "Enter your username",
            &self.username,
            Message::UsernameChanged,
        );
        let password = TextInput::new(
            &mut self.password_state,
            "Enter your password",
            &self.password,
            Message::PasswordChanged,
        )
        .password();

        let login_btn = if self.loading {
            Button::new(&mut self.login_state, Text::new("Logging in..."))
        } else {
            Button::new(&mut self.login_state, Text::new("Login"))
                .on_press(Message::Login)
        };

        let mut content = Column::new()
            .push(Text::new("Username:"))
            .push(username)
            .push(Text::new("Password:"))
            .push(password);

        if let Some(err) = &self.error {
            content = content.push(Text::new(err).color([1.0, 0.0, 0.0]));
        }

        content = content.push(login_btn);

        Container::new(content)
            .width(Length::Fill)
            .height(Length::Fill)
            .center_x()
            .center_y()
            .into()
    }
}

impl Default for LoginForm {
    fn default() -> Self {
        Self {
            username: String::new(),
            password: String::new(),
            username_state: text_input::State::new(),
            password_state: text_input::State::new(),
            login_state: button::State::new(),
            error: None,
            loading: false,
        }
    }
}
