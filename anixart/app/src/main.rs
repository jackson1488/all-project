//! Anixart Linux binary entry point.
//!
//! The whole client lives in the `anixart_app` library (see `src/lib.rs`); this
//! executable only starts it. Built on iced 0.13 → winit 0.30 → wayland-client
//! 0.31, so the window talks a modern Wayland protocol and no longer crashes on
//! Fedora 43 (`interface wl_surface has no event 2` came from iced 0.4 / winit
//! 0.24 emitting events the compositor does not know).

fn main() -> iced::Result {
    anixart_app::run()
}
