//! Material 3 dark theme of the Anixart desktop client.
//!
//! The palette mirrors the official Android app: near-black `#141414` window
//! background, `#1E1E1E` surfaces (nav rail, banners, app bars), `#2A2A2A` cards
//! and pills, and a warm coral → crimson accent (`#FF5C5C` / `#D82B2B`) in place
//! of iced's blue. Everything is a plain `Color` constant, so the screens never
//! ask the [`Theme`] for a colour and stay independent of whichever palette iced
//! happens to have generated.
//!
//! The styles live in [`styles`]: they are plain functions with the exact
//! signature `iced::widget::{container, button, text_input, rule}` expect from
//! `.style(..)`, so a screen just names one.

use std::sync::OnceLock;

use iced::widget::{button, container, rule, scrollable, text_input};
use iced::{border, Background, Border, Color, Shadow, Theme, Vector};

/// Window / root background — `#141414`.
pub const BACKGROUND: Color = rgb(0x14, 0x14, 0x14);
/// Slightly raised surface — `#1E1E1E` (nav rail, app bar, banners).
pub const SURFACE: Color = rgb(0x1E, 0x1E, 0x1E);
/// Card and pill fill — `#2A2A2A`.
pub const CARD: Color = rgb(0x2A, 0x2A, 0x2A);
/// Hairline separators and inactive outlines — `#3A3A3A`.
pub const DIVIDER: Color = rgb(0x3A, 0x3A, 0x3A);
/// Primary text — `#F2F2F3`.
pub const TEXT: Color = rgb(0xF2, 0xF2, 0xF3);
/// Secondary text — `#9A9AA2`.
pub const TEXT_MUTED: Color = rgb(0x9A, 0x9A, 0xA2);
/// Warm coral accent — the Anixart red-orange.
pub const ACCENT: Color = rgb(0xFF, 0x5C, 0x5C);
/// Deeper crimson half of the accent pair (pressed states, danger).
pub const ACCENT_DEEP: Color = rgb(0xD8, 0x2B, 0x2B);
/// Grade / success green.
pub const SUCCESS: Color = rgb(0x4C, 0xAF, 0x50);
/// Warning amber (clipboard hints).
pub const WARNING: Color = rgb(0xF2, 0xC0, 0x4F);

/// Corner radius of a card.
pub const RADIUS_CARD: f32 = 16.0;
/// Corner radius of a pill button.
pub const RADIUS_PILL: f32 = 999.0;
/// Corner radius of a text input.
pub const RADIUS_INPUT: f32 = 12.0;

/// `Color` from 8-bit components — `from_rgb` is a `const fn`, so every colour
/// above is computed at compile time.
pub const fn rgb(red: u8, green: u8, blue: u8) -> Color {
    Color::from_rgb(
        red as f32 / 255.0,
        green as f32 / 255.0,
        blue as f32 / 255.0,
    )
}

/// Scales a colour by `factor`, clamped to the displayable range.
pub fn shade(color: Color, factor: f32) -> Color {
    Color::from_rgb(
        (color.r * factor).clamp(0.0, 1.0),
        (color.g * factor).clamp(0.0, 1.0),
        (color.b * factor).clamp(0.0, 1.0),
    )
}

/// A flat shadow with a vertical offset — the elevation of Material surfaces.
pub fn shadow(blur: f32) -> Shadow {
    Shadow {
        color: Color::from_rgba(0.0, 0.0, 0.0, 0.45),
        offset: Vector::new(0.0, blur / 4.0),
        blur_radius: blur,
    }
}

/// The palette handed to iced: the coral accent replaces its blue primary, so
/// even the widgets that are left unstyled (scrollbars, checkboxes) come out in
/// the Anixart colours.
pub fn palette() -> iced::theme::Palette {
    iced::theme::Palette {
        background: BACKGROUND,
        text: TEXT,
        primary: ACCENT,
        success: SUCCESS,
        danger: ACCENT_DEEP,
    }
}

/// The application [`Theme`], built once and shared by every window.
pub fn theme() -> Theme {
    static THEME: OnceLock<Theme> = OnceLock::new();
    THEME
        .get_or_init(|| Theme::custom("Anixart Dark".to_owned(), palette()))
        .clone()
}

/// Style functions, one per visual role.
pub mod styles {
    use super::*;

    /// The root surface of a screen: `#1E1E1E`, no rounding.
    pub fn surface(_: &Theme) -> container::Style {
        container::Style {
            text_color: Some(TEXT),
            background: Some(Background::Color(SURFACE)),
            border: Border::default(),
            shadow: Shadow::default(),
        }
    }

    /// A raised card — `#2A2A2A`, 16 px corners, soft shadow.
    pub fn card(_: &Theme) -> container::Style {
        container::Style {
            text_color: Some(TEXT),
            background: Some(Background::Color(CARD)),
            border: border::rounded(RADIUS_CARD),
            shadow: shadow(14.0),
        }
    }

    /// A card outlined in the accent colour — the currently selected entry.
    pub fn card_selected(_: &Theme) -> container::Style {
        container::Style {
            text_color: Some(TEXT),
            background: Some(Background::Color(shade(CARD, 1.15))),
            border: Border {
                color: ACCENT,
                width: 1.5,
                radius: border::radius(RADIUS_CARD),
            },
            shadow: shadow(18.0),
        }
    }

    /// A flat panel on the window background — posters and placeholders.
    pub fn panel(_: &Theme) -> container::Style {
        container::Style {
            text_color: Some(TEXT_MUTED),
            background: Some(Background::Color(BACKGROUND)),
            border: Border {
                color: DIVIDER,
                width: 1.0,
                radius: border::radius(RADIUS_CARD),
            },
            shadow: Shadow::default(),
        }
    }

    /// A small rounded label — year, genre, quality. Flat and outlined rather than
    /// raised, so a row of them reads as metadata and not as buttons.
    pub fn chip(_: &Theme) -> container::Style {
        container::Style {
            text_color: Some(TEXT_MUTED),
            background: Some(Background::Color(SURFACE)),
            border: Border {
                color: DIVIDER,
                width: 1.0,
                radius: border::radius(RADIUS_PILL),
            },
            shadow: Shadow::default(),
        }
    }

    /// Banner behind a status / error line.
    pub fn banner(color: Color) -> impl Fn(&Theme) -> container::Style {
        move |_: &Theme| container::Style {
            text_color: Some(color),
            background: Some(Background::Color(shade(color, 0.28))),
            border: Border {
                color: shade(color, 0.6),
                width: 1.0,
                radius: border::radius(12.0),
            },
            shadow: Shadow::default(),
        }
    }

    /// Neutral pill: `#2A2A2A` fill, muted text, hairline outline.
    pub fn pill(_: &Theme, status: button::Status) -> button::Style {
        base(status, CARD, TEXT, DIVIDER, 1.0, RADIUS_PILL)
    }

    /// The selected pill — accent fill, so a tab row reads at a glance.
    pub fn pill_active(_: &Theme, status: button::Status) -> button::Style {
        base(status, ACCENT, Color::WHITE, ACCENT_DEEP, 1.0, RADIUS_PILL)
    }

    /// Primary action button (accent, 12 px corners).
    pub fn accent(_: &Theme, status: button::Status) -> button::Style {
        base(status, ACCENT, Color::WHITE, ACCENT_DEEP, 0.0, 12.0)
    }

    /// Secondary action button on a card.
    pub fn ghost(_: &Theme, status: button::Status) -> button::Style {
        base(status, SURFACE, TEXT, DIVIDER, 1.0, 12.0)
    }

    /// Destructive action (logout, remove from list).
    pub fn danger(_: &Theme, status: button::Status) -> button::Style {
        base(status, ACCENT_DEEP, Color::WHITE, ACCENT_DEEP, 0.0, 12.0)
    }

    /// An entire card that is also a button — the release / article rows.
    pub fn card_button(_: &Theme, status: button::Status) -> button::Style {
        let mut style = base(status, CARD, TEXT, DIVIDER, 1.0, RADIUS_CARD);
        style.shadow = shadow(10.0);
        style
    }

    /// Nav rail entry, inactive.
    pub fn rail(_: &Theme, status: button::Status) -> button::Style {
        base(status, SURFACE, TEXT_MUTED, SURFACE, 0.0, 14.0)
    }

    /// Nav rail entry, selected.
    pub fn rail_active(_: &Theme, status: button::Status) -> button::Style {
        base(status, CARD, ACCENT, ACCENT, 1.0, 14.0)
    }

    /// Rounded text field.
    pub fn input(_: &Theme, status: text_input::Status) -> text_input::Style {
        let outline = match status {
            text_input::Status::Focused => ACCENT,
            text_input::Status::Hovered => shade(DIVIDER, 1.4),
            text_input::Status::Active => DIVIDER,
            text_input::Status::Disabled => shade(DIVIDER, 0.6),
        };
        text_input::Style {
            background: Background::Color(BACKGROUND),
            border: Border {
                color: outline,
                width: 1.0,
                radius: border::radius(RADIUS_INPUT),
            },
            icon: TEXT_MUTED,
            placeholder: TEXT_MUTED,
            value: TEXT,
            selection: shade(ACCENT, 0.5),
        }
    }

    /// Hairline separator.
    pub fn divider(_: &Theme) -> rule::Style {
        rule::Style {
            color: DIVIDER,
            width: 1,
            radius: border::radius(1.0),
            fill_mode: rule::FillMode::Full,
        }
    }

    /// The scroll area of a screen: the window background, with rails so thin
    /// they read as a hint rather than a chrome bar.
    pub fn scroll(_: &Theme, _: scrollable::Status) -> scrollable::Style {
        scrollable::Style {
            container: container::Style {
                text_color: Some(TEXT),
                background: Some(Background::Color(BACKGROUND)),
                border: Border::default(),
                shadow: Shadow::default(),
            },
            vertical_rail: scroller_rail(),
            horizontal_rail: scroller_rail(),
            gap: Some(Background::Color(BACKGROUND)),
        }
    }

    /// A scrollbar rail with no track of its own and a rounded, dimmed thumb.
    fn scroller_rail() -> scrollable::Rail {
        scrollable::Rail {
            background: None,
            border: Border::default(),
            scroller: scrollable::Scroller {
                color: shade(DIVIDER, 1.35),
                border: border::rounded(RADIUS_PILL),
            },
        }
    }

    /// One styled button state machine: hover lifts the fill, press sinks it,
    /// disabled fades it out. The outline is a plain colour plus its width, so
    /// the corner radius always comes from `radius`.
    fn base(
        status: button::Status,
        fill: Color,
        text: Color,
        outline: Color,
        width: f32,
        radius: f32,
    ) -> button::Style {
        let (fill, text) = match status {
            button::Status::Active => (fill, text),
            button::Status::Hovered => (shade(fill, 1.18), text),
            button::Status::Pressed => (shade(fill, 0.86), text),
            button::Status::Disabled => (fade(fill, 0.45), fade(text, 0.6)),
        };

        button::Style {
            background: Some(Background::Color(fill)),
            text_color: text,
            border: Border {
                color: outline,
                width,
                radius: border::radius(radius),
            },
            shadow: Shadow::default(),
        }
    }

    /// Multiplies the alpha channel.
    fn fade(color: Color, factor: f32) -> Color {
        Color::from_rgba(color.r, color.g, color.b, color.a * factor)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn channel(color: Color) -> (u8, u8, u8) {
        (
            (color.r * 255.0).round() as u8,
            (color.g * 255.0).round() as u8,
            (color.b * 255.0).round() as u8,
        )
    }

    /// The flat fill of a button / container background, for the assertions below.
    fn fill(background: Option<Background>) -> Color {
        match background {
            Some(Background::Color(color)) => color,
            other => panic!("expected a flat background, got {other:?}"),
        }
    }

    #[test]
    fn rgb_builds_the_anixart_palette_bytes() {
        assert_eq!(channel(BACKGROUND), (0x14, 0x14, 0x14));
        assert_eq!(channel(SURFACE), (0x1E, 0x1E, 0x1E));
        assert_eq!(channel(CARD), (0x2A, 0x2A, 0x2A));
        assert_eq!(channel(DIVIDER), (0x3A, 0x3A, 0x3A));
        assert_eq!(channel(ACCENT), (0xFF, 0x5C, 0x5C));
        assert_eq!(channel(ACCENT_DEEP), (0xD8, 0x2B, 0x2B));
        assert_eq!(rgb(0, 0, 0), Color::BLACK);
        assert_eq!(rgb(255, 255, 255), Color::WHITE);
    }

    #[test]
    #[allow(clippy::assertions_on_constants)]
    fn the_surface_stack_gets_lighter_as_it_rises() {
        assert!(BACKGROUND.r < SURFACE.r);
        assert!(SURFACE.r < CARD.r);
        assert!(CARD.r < DIVIDER.r);
        // muted text is dimmer than primary text, but still readable on a card
        assert!(TEXT.r > TEXT_MUTED.r);
        assert!(TEXT_MUTED.r > CARD.r);
    }

    #[test]
    #[allow(clippy::assertions_on_constants)]
    fn the_accent_is_warm_and_darker_when_pressed() {
        // coral, not iced blue: red dominates, green sits below it
        assert!(ACCENT.r > ACCENT.g && ACCENT.g > ACCENT.b);
        assert!(ACCENT_DEEP.r > ACCENT_DEEP.g);
        assert!(ACCENT_DEEP.r < ACCENT.r, "crimson must sit below coral");
    }

    #[test]
    fn shade_clamps_instead_of_wrapping() {
        assert_eq!(shade(Color::WHITE, 2.0), Color::WHITE);
        assert_eq!(shade(Color::WHITE, 0.0), Color::BLACK);
        assert_eq!(shade(Color::BLACK, 5.0), Color::BLACK);
        let half = shade(ACCENT, 0.5);
        assert!((half.r - ACCENT.r / 2.0).abs() < 1e-6);
    }

    #[test]
    fn shadow_points_downwards() {
        let low = shadow(8.0);
        let high = shadow(24.0);
        assert!(low.offset.y > 0.0);
        assert!(high.offset.y > low.offset.y);
        assert!(high.blur_radius > low.blur_radius);
        assert_eq!(low.color.a, high.color.a);
        assert!(low.color.a > 0.0 && low.color.a <= 1.0);
    }

    #[test]
    fn the_theme_is_custom_dark_and_stable() {
        // привязка называется не `theme`, иначе `theme()` ниже станет вызовом
        // значения, а не функции
        let dark = theme();
        assert_eq!(dark.to_string(), "Anixart Dark");
        assert_eq!(dark.palette().background, BACKGROUND);
        assert_eq!(dark.palette().primary, ACCENT);
        assert_eq!(dark.palette().danger, ACCENT_DEEP);
        assert!(dark.extended_palette().is_dark);
        // built once: two calls hand back the same arc
        assert_eq!(dark.clone(), theme());
        assert_eq!(dark, palette_theme());
    }

    fn palette_theme() -> Theme {
        Theme::custom("Anixart Dark".to_owned(), palette())
    }

    #[test]
    fn cards_are_rounded_and_pills_are_not() {
        let card = styles::card(&theme());
        assert_eq!(card.border.radius.top_left, RADIUS_CARD);
        assert_eq!(card.background, Some(Background::Color(CARD)));
        assert_eq!(card.text_color, Some(TEXT));
        assert!(card.shadow.blur_radius > 0.0);

        let pill = styles::pill(&theme(), button::Status::Active);
        assert!(pill.border.radius.top_left >= RADIUS_PILL);
        let active = styles::pill_active(&theme(), button::Status::Active);
        assert_eq!(active.background, Some(Background::Color(ACCENT)));
        assert_eq!(active.text_color, Color::WHITE);
        assert_ne!(pill.background, active.background);
    }

    #[test]
    fn selected_cards_outweigh_plain_ones() {
        let plain = styles::card(&theme());
        let selected = styles::card_selected(&theme());
        assert!(selected.border.width > plain.border.width);
        assert_eq!(selected.border.color, ACCENT);
        assert!(selected.shadow.blur_radius >= plain.shadow.blur_radius);
    }

    #[test]
    fn buttons_answer_to_the_pointer() {
        let idle = styles::accent(&theme(), button::Status::Active);
        let hovered = styles::accent(&theme(), button::Status::Hovered);
        let pressed = styles::accent(&theme(), button::Status::Pressed);
        let disabled = styles::accent(&theme(), button::Status::Disabled);
        assert_ne!(idle, hovered);
        assert_ne!(idle, pressed);
        assert_ne!(idle, disabled);
        assert_eq!(idle.text_color, Color::WHITE);
        // hover lifts the fill, press sinks it, disable fades it out
        assert!(fill(hovered.background).r > fill(idle.background).r);
        assert!(fill(pressed.background).r < fill(idle.background).r);
        assert!(fill(disabled.background).a < fill(idle.background).a);
    }

    #[test]
    fn the_nav_rail_marks_the_selected_entry() {
        let idle = styles::rail(&theme(), button::Status::Active);
        let current = styles::rail_active(&theme(), button::Status::Active);
        assert_eq!(idle.background, Some(Background::Color(SURFACE)));
        assert_eq!(current.background, Some(Background::Color(CARD)));
        assert_eq!(current.text_color, ACCENT);
        assert_eq!(idle.text_color, TEXT_MUTED);
        assert!(current.border.width > 0.0);
        assert_eq!(idle.border.width, 0.0);
    }

    #[test]
    fn inputs_gain_an_accent_outline_when_focused() {
        let idle = styles::input(&theme(), text_input::Status::Active);
        let focused = styles::input(&theme(), text_input::Status::Focused);
        let disabled = styles::input(&theme(), text_input::Status::Disabled);
        assert_eq!(idle.border.color, DIVIDER);
        assert_eq!(focused.border.color, ACCENT);
        assert!(disabled.border.color.r < idle.border.color.r);
        assert_eq!(idle.value, TEXT);
        assert_eq!(idle.placeholder, TEXT_MUTED);
        assert_eq!(idle.background, Background::Color(BACKGROUND));
    }

    #[test]
    fn banners_tint_their_own_background() {
        let error = styles::banner(ACCENT_DEEP)(&theme());
        let ok = styles::banner(SUCCESS)(&theme());
        assert_eq!(error.text_color, Some(ACCENT_DEEP));
        assert_ne!(error.background, ok.background);
        assert!(error.shadow.blur_radius == 0.0);
    }

    #[test]
    fn chips_are_flat_and_pill_rounded() {
        let chip = styles::chip(&theme());
        assert_eq!(chip.background, Some(Background::Color(SURFACE)));
        assert_eq!(chip.text_color, Some(TEXT_MUTED));
        assert_eq!(chip.border.width, 1.0);
        assert!(chip.border.radius.top_left >= RADIUS_PILL);
        // no elevation: a chip is metadata, not a raised card
        assert_eq!(chip.shadow.blur_radius, 0.0);
        assert_ne!(styles::card(&theme()).background, chip.background);
    }

    #[test]
    fn dividers_are_a_single_hairline() {
        let rule = styles::divider(&theme());
        assert_eq!(rule.width, 1);
        assert_eq!(rule.color, DIVIDER);
        assert!(matches!(rule.fill_mode, rule::FillMode::Full));
    }

    #[test]
    fn scroll_areas_are_flat_and_their_thumbs_are_visible() {
        let style = styles::scroll(&theme(), scrollable::Status::Active);
        assert_eq!(style.container.background, Some(Background::Color(BACKGROUND)));
        assert_eq!(style.container.text_color, Some(TEXT));
        assert_eq!(style.gap, Some(Background::Color(BACKGROUND)));
        // no track, only a rounded thumb a little brighter than a divider
        assert_eq!(style.vertical_rail.background, None);
        assert_eq!(style.horizontal_rail.background, None);
        assert!(style.vertical_rail.scroller.color.r > DIVIDER.r);
        assert!(style.vertical_rail.scroller.border.radius.top_left >= RADIUS_PILL);
        assert_eq!(style.vertical_rail.border.width, 0.0);
    }

    #[test]
    fn card_buttons_keep_the_card_fill_but_gain_elevation() {
        let card = styles::card_button(&theme(), button::Status::Active);
        assert_eq!(card.background, Some(Background::Color(CARD)));
        assert!(card.shadow.blur_radius > 0.0);
        assert_eq!(card.border.radius.top_left, RADIUS_CARD);
    }
}
