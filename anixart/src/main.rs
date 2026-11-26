use iced::{
    Application, Command, Element, Settings, Text, Window,
};
use std::process::Command;

// Import the LoginForm from the login module
mod login;
use login::{LoginForm, Message as LoginMessage};

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
        (AnixartApp { login_form: LoginForm::new() }, Command::none())
    }

    fn title(&self) -> String {
        String::from("Anixart - Linux Desktop Client")
    }

    fn view(&mut self) -> Element<Self::Message> {
        self.login_form.view().map(AppMessage::Login)
    }

    fn update(&mut self, message: Self::Message) -> Command<Self::Message> {
        match message {
            AppMessage::Login(LoginMessage::Login) => {
                // Handle login logic here
                // For now, we'll just print a message and spawn mpv with a test URL
                println!("Logging in...");
                let _ = Command::new("mpv")
                    .arg("https://example.com/test.mp4")
                    .spawn();
                Command::none()
            }
        }
    }
}

#[derive(Debug, Clone)]
pub enum AppMessage {
    Login(LoginMessage),
}