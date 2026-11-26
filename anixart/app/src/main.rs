use iced::{Application, Command, Element, Settings};
use std::process::Command as ProcessCommand;

mod login;

use login::LoginForm;

const TEST_STREAM: &str = "https://example.com/test.mp4";

#[derive(Debug, Clone)]
pub enum AppMessage {
    Login(login::Message),
}

pub fn main() -> iced::Result {
    AnixartApp::run(Settings::default())
}

struct AnixartApp {
    login_form: LoginForm,
}

impl Application for AnixartApp {
    type Executor = iced::executor::Default;
    type Message = AppMessage;
    type Flags = ();

    fn new(_flags: Self::Flags) -> (Self, Command<Self::Message>) {
        (
            AnixartApp {
                login_form: LoginForm::new(),
            },
            Command::none(),
        )
    }

    fn title(&self) -> String {
        String::from("Anixart - Linux Desktop Client")
    }

    fn update(&mut self, message: Self::Message) -> Command<Self::Message> {
        match message {
            AppMessage::Login(login::Message::UsernameChanged(username)) => {
                self.login_form.set_username(username);
            }
            AppMessage::Login(login::Message::PasswordChanged(password)) => {
                self.login_form.set_password(password);
            }
            AppMessage::Login(login::Message::Login) => {
                println!("Logging in via {}", anixart_core::API_BASE_URL);
                if anixart_parsers::is_direct_stream(TEST_STREAM) {
                    let _ = ProcessCommand::new("mpv").arg(TEST_STREAM).spawn();
                } else {
                    eprintln!("Unsupported stream: {TEST_STREAM}");
                }
            }
        }
        Command::none()
    }

    fn view(&mut self) -> Element<'_, Self::Message> {
        self.login_form.view().map(AppMessage::Login)
    }
}
