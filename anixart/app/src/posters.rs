//! In-memory poster cache with a deterministic placeholder.
//!
//! Every card of the home, browse and bookmark grids shows artwork that lives on
//! the Anixart CDN. Downloading it during `view` is not an option — iced rebuilds
//! the whole tree on every frame — so the transfer runs as an iced [`Task`]
//! ([`crate::net::poster_bytes`]) and the decoded
//! [`iced::widget::image::Handle`] is parked here, keyed by a namespaced string.
//!
//! The key is built by [`release_key`] / [`article_key`] / [`collection_key`] /
//! [`profile_key`]: releases, feed articles, collections and avatars all carry
//! independent ID spaces, so `12` is a different object in each of them. The
//! prefix keeps the four from colliding in one map.
//!
//! The state machine a key walks through is
//!
//! ```text
//!        claim(new url)          decode ok
//!   Idle ──────────────► Loading ────────────► Ready
//!    │                      │                    │
//!    │ no url               │ decode err /       │ new url
//!    ▼                      │ empty body          ▼
//!  Failed ◄─────────────────┘                 Loading (re-fetch)
//! ```
//!
//! * `claim` hands back a [`PosterEffect::Fetch`] at most once per URL, which is
//!   what keeps a scrolling grid from firing hundreds of identical requests.
//! * A `Failed` entry is retried only when the caller supplies a *different*
//!   URL, so a broken CDN link is never hammered; [`PosterCache::forget`] is the
//!   explicit way to ask for a re-download.
//! * An object whose `image` field is empty is recorded as `Failed` straight
//!   away and renders the placeholder forever.
//!
//! The placeholder is not a blank box: [`poster_element`] paints the first letter
//! of the title over a per-key tint, so a grid of unloaded posters still reads as
//! a grid of distinct anime.

use std::collections::HashMap;

use iced::widget::image::Handle;

use crate::theme;

/// Cache key of a release poster.
pub fn release_key(release_id: i64) -> String {
    format!("release:{release_id}")
}

/// Cache key of a feed article cover.
pub fn article_key(article_id: i64) -> String {
    format!("article:{article_id}")
}

/// Cache key of a collection cover.
pub fn collection_key(collection_id: i64) -> String {
    format!("collection:{collection_id}")
}

/// Cache key of a profile avatar.
pub fn profile_key(profile_id: i64) -> String {
    format!("profile:{profile_id}")
}

/// What the caller has to do after a [`PosterCache::claim`].
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PosterEffect {
    /// Nothing to do — the poster is cached, in flight, or known to be broken.
    None,
    /// Download `url` and hand the bytes back through
    /// [`crate::app::Message::PosterLoaded`].
    Fetch { key: String, url: String },
}

impl PosterEffect {
    /// `true` when no network work was requested.
    pub fn is_idle(&self) -> bool {
        matches!(self, Self::None)
    }
}

/// Where a poster currently stands.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PosterState {
    /// Never asked for.
    Idle,
    /// A fetch is in flight.
    Loading,
    /// Decoded pixels are ready.
    Ready,
    /// The object has no image, or the download / decode failed.
    Failed,
}

#[derive(Debug, Clone)]
struct Entry {
    state: PosterState,
    /// URL the current state refers to, so a retry can be told from a re-fetch.
    url: Option<String>,
    handle: Option<Handle>,
}

/// Cache of decoded posters, keyed by the namespaced strings above.
#[derive(Debug, Clone, Default)]
pub struct PosterCache {
    entries: HashMap<String, Entry>,
}

impl PosterCache {
    pub fn new() -> Self {
        Self::default()
    }

    /// Cached handle of `key`, if it is ready. Rendering never triggers a fetch,
    /// so this is the only accessor the `view` path uses.
    pub fn peek(&self, key: &str) -> Option<&Handle> {
        let entry = self.entries.get(key)?;
        (entry.state == PosterState::Ready).then_some(entry.handle.as_ref()?)
    }

    /// `true` while a fetch is in flight — the card dims its placeholder.
    pub fn is_loading(&self, key: &str) -> bool {
        self.entries
            .get(key)
            .is_some_and(|entry| entry.state == PosterState::Loading)
    }

    /// Current state of `key`, `Idle` when it was never seen.
    pub fn state(&self, key: &str) -> PosterState {
        self.entries
            .get(key)
            .map_or(PosterState::Idle, |entry| entry.state)
    }

    /// Registers interest in `key`'s poster and, when the bytes are not known
    /// yet, marks them as in flight and asks for the download.
    ///
    /// `url` is the raw `image` field of the object; an empty or whitespace value
    /// counts as no image at all.
    pub fn claim(&mut self, key: impl Into<String>, url: Option<&str>) -> PosterEffect {
        let key = key.into();
        let url = url.map(str::trim).filter(|candidate| !candidate.is_empty());
        let entry = self.entries.entry(key.clone()).or_insert_with(|| Entry {
            state: PosterState::Idle,
            url: None,
            handle: None,
        });

        let Some(url) = url else {
            // Nothing to download: settle on the placeholder for good.
            entry.state = PosterState::Failed;
            entry.url = None;
            entry.handle = None;
            return PosterEffect::None;
        };

        let same_url = entry.url.as_deref() == Some(url);
        if same_url && entry.state != PosterState::Idle {
            return PosterEffect::None;
        }

        entry.state = PosterState::Loading;
        entry.url = Some(url.to_owned());
        entry.handle = None;
        PosterEffect::Fetch { key, url: url.to_owned() }
    }

    /// Stores the outcome of a fetch and reports whether the poster is ready.
    ///
    /// `None` bytes mean the request failed or the body was empty. Bytes that do
    /// not decode (a captive portal's HTML, a truncated JPEG) are treated the same
    /// way instead of being handed to the renderer, which would log a decode error
    /// on every single frame.
    pub fn record(&mut self, key: &str, bytes: Option<&[u8]>) -> bool {
        let Some(entry) = self.entries.get_mut(key) else {
            // The user navigated away before the reply landed: nothing to do.
            return false;
        };
        if entry.state != PosterState::Loading {
            return entry.state == PosterState::Ready;
        }

        match bytes.and_then(decode) {
            Some(handle) => {
                entry.state = PosterState::Ready;
                entry.handle = Some(handle);
                true
            }
            None => {
                entry.state = PosterState::Failed;
                entry.handle = None;
                false
            }
        }
    }

    /// Drops `key` from the cache, so the next [`PosterCache::claim`] starts a
    /// fresh download. Used by the reload paths.
    pub fn forget(&mut self, key: &str) {
        self.entries.remove(key);
    }

    /// Empties the cache — called on sign out, because the next account may see
    /// different artwork.
    pub fn clear(&mut self) {
        self.entries.clear();
    }

    pub fn len(&self) -> usize {
        self.entries.len()
    }

    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }

    /// Number of entries in a given state, for the status line.
    pub fn count_state(&self, state: PosterState) -> usize {
        self.entries
            .values()
            .filter(|entry| entry.state == state)
            .count()
    }
}

/// Decodes poster bytes into an iced handle of RGBA pixels.
fn decode(bytes: &[u8]) -> Option<Handle> {
    let decoded = ::image::load_from_memory(bytes).ok()?;
    let rgba = decoded.to_rgba8();
    let (width, height) = rgba.dimensions();
    Some(Handle::from_rgba(width, height, rgba.into_raw()))
}

/// Deterministic tint behind a placeholder, so neighbouring cards do not all get
/// the same grey. The key is hashed with FNV-1a: cheap, stable across runs, and
/// spread well enough over six buckets.
pub fn placeholder_tint(key: &str) -> iced::Color {
    const TINTS: [iced::Color; 6] = [
        theme::rgb(0x2E, 0x1F, 0x22),
        theme::rgb(0x1F, 0x2A, 0x33),
        theme::rgb(0x24, 0x30, 0x24),
        theme::rgb(0x30, 0x28, 0x1C),
        theme::rgb(0x2A, 0x22, 0x33),
        theme::rgb(0x1F, 0x30, 0x2E),
    ];

    let mut hash: u64 = 0xcbf2_9ce4_8422_2325;
    for byte in key.as_bytes() {
        hash ^= u64::from(*byte);
        hash = hash.wrapping_mul(0x0000_0100_0000_01b3);
    }
    TINTS[(hash % TINTS.len() as u64) as usize]
}

/// The first letter of a title, uppercased — `«Наруто»` yields `Н`.
///
/// Leading guillemets, quotes, brackets and spaces are skipped so the letter is
/// the actual name; an empty title falls back to `?` so the tile is never blank.
pub fn title_initial(title: &str) -> char {
    title
        .trim_start_matches(['«', '»', '"', '(', '[', ' ', '\u{00a0}'])
        .chars()
        .next()
        .map(|letter| letter.to_uppercase().next().unwrap_or(letter))
        .unwrap_or('?')
}

/// Absolute cover URL of an object: `None` for a blank value and for a relative
/// path, because the CDN answers those with a 404 and the placeholder is the
/// honest result.
pub fn cover_url(raw: &str) -> Option<String> {
    let trimmed = raw.trim();
    if trimmed.starts_with("http") {
        Some(trimmed.to_owned())
    } else {
        None
    }
}

/// The same for the `Option<String>` image fields of a release or an article.
pub fn cover_of(field: &Option<String>) -> Option<String> {
    field.as_deref().and_then(cover_url)
}

/// A poster box of the given size: the cached pixels when they are ready,
/// otherwise the tinted placeholder with the initial of the title.
pub fn poster_element<'a>(
    handle: Option<&Handle>,
    key: &str,
    title: &str,
    width: u16,
    height: u16,
) -> iced::Element<'a, crate::app::Message> {
    poster_box(
        handle,
        key,
        title,
        iced::Length::Fixed(f32::from(width)),
        iced::Length::Fixed(f32::from(height)),
    )
}

/// The same box, but sized by [`Length`]: the tiles of a responsive grid stretch
/// across their column, so their artwork is `Fill` wide and only as tall as the
/// row asks. Everything else about the box — the tint, the letter, the rounded
/// corners, the `Cover` crop — is identical to [`poster_element`].
pub fn poster_box<'a>(
    handle: Option<&Handle>,
    key: &str,
    title: &str,
    width: iced::Length,
    height: iced::Length,
) -> iced::Element<'a, crate::app::Message> {
    use iced::widget::{center, container, text, Image};
    use iced::{Background, Element, Length};

    // Два разных виджета (картинка и центр буквы) сходятся в одном типе: без
    // явной подписи `Element` iced 0.13 не может выбрать, в какой именно
    // `Renderer` их конвертировать. `Image::<Handle>` пишется целиком, потому
    // что свободный `image()` сам обобщён по `Handle` и вывод снова застревает.
    let content: Element<'_, crate::app::Message> = match handle {
        Some(handle) => Image::<Handle>::new(handle.clone())
            .width(Length::Fill)
            .height(Length::Fill)
            .content_fit(iced::ContentFit::Cover)
            .into(),
        None => center(
            text(title_initial(title))
                .size(30)
                .color(theme::TEXT_MUTED)
                .width(Length::Fill)
                .align_x(iced::alignment::Horizontal::Center),
        )
        .into(),
    };

    let tint = placeholder_tint(key);
    container(content)
        .width(width)
        .height(height)
        .style(move |_: &iced::Theme| container::Style {
            text_color: Some(theme::TEXT_MUTED),
            background: Some(Background::Color(tint)),
            border: iced::border::rounded(12.0),
            shadow: iced::Shadow::default(),
        })
        .into()
}

#[cfg(test)]
mod tests {
    use super::*;
    use iced::Color;

    /// A real PNG, encoded by the very `image` crate iced decodes with.
    fn png_bytes() -> Vec<u8> {
        let buffer = image::RgbImage::from_pixel(4, 6, image::Rgb([0x99, 0x22, 0x11]));
        let mut bytes: Vec<u8> = Vec::new();
        buffer
            .write_to(
                &mut std::io::Cursor::new(&mut bytes),
                image::ImageFormat::Png,
            )
            .expect("a 4x6 RGB buffer encodes to PNG without trouble");
        assert!(!bytes.is_empty());
        bytes
    }

    fn url(id: i64) -> String {
        format!("https://dist.anixsekai.com/release/{id}/poster.jpg")
    }

    #[test]
    fn keys_separate_the_four_id_spaces() {
        assert_eq!(release_key(12), "release:12");
        assert_eq!(article_key(12), "article:12");
        assert_eq!(collection_key(12), "collection:12");
        assert_eq!(profile_key(12), "profile:12");
        // the same numeric id never shares a slot across kinds
        assert_ne!(release_key(12), article_key(12));
        assert_ne!(article_key(12), collection_key(12));
        assert_ne!(collection_key(12), profile_key(12));
    }

    #[test]
    fn a_missing_url_settles_on_the_placeholder_without_a_fetch() {
        let mut cache = PosterCache::new();
        assert_eq!(cache.claim(release_key(1), None), PosterEffect::None);
        assert_eq!(cache.state(&release_key(1)), PosterState::Failed);
        // claiming again stays quiet: no image means no requests, ever
        assert_eq!(cache.claim(release_key(1), None), PosterEffect::None);
        assert_eq!(cache.claim(release_key(1), Some("")), PosterEffect::None);
        assert_eq!(cache.claim(release_key(1), Some("   ")), PosterEffect::None);
        assert_eq!(cache.state(&release_key(1)), PosterState::Failed);
        assert!(cache.peek(&release_key(1)).is_none());
    }

    #[test]
    fn a_url_is_claimed_exactly_once_while_it_is_in_flight() {
        let mut cache = PosterCache::new();
        let key = release_key(2);
        let link = url(2);
        assert_eq!(
            cache.claim(key.clone(), Some(&link)),
            PosterEffect::Fetch {
                key: key.clone(),
                url: link.clone()
            }
        );
        assert_eq!(cache.state(&key), PosterState::Loading);
        assert!(cache.is_loading(&key));
        // the grid re-renders dozens of times before the reply lands
        for _ in 0..25 {
            assert_eq!(cache.claim(key.clone(), Some(&link)), PosterEffect::None);
        }
        assert_eq!(cache.count_state(PosterState::Loading), 1);
    }

    #[test]
    fn decoding_moves_the_entry_to_ready_and_keeps_the_handle() {
        let mut cache = PosterCache::new();
        let key = release_key(3);
        let link = url(3);
        cache.claim(key.clone(), Some(&link));
        assert!(cache.record(&key, Some(&png_bytes())));
        assert_eq!(cache.state(&key), PosterState::Ready);
        assert!(cache.peek(&key).is_some(), "the card can draw the poster");
        // a ready entry is never re-fetched for the same URL
        assert_eq!(cache.claim(key.clone(), Some(&link)), PosterEffect::None);
        // ...but a new URL (the release changed its artwork) does re-fetch
        let other = format!("{link}?v=2");
        assert_eq!(
            cache.claim(key.clone(), Some(&other)),
            PosterEffect::Fetch {
                key: key.clone(),
                url: other.clone()
            }
        );
        assert_eq!(cache.state(&key), PosterState::Loading);
        assert!(cache.peek(&key).is_none(), "the stale handle is gone");
    }

    #[test]
    fn broken_bytes_fail_once_and_are_not_retried_on_the_same_url() {
        let mut cache = PosterCache::new();
        let key = release_key(4);
        let link = url(4);
        cache.claim(key.clone(), Some(&link));
        assert!(!cache.record(&key, Some(b"<html>not an image</html>")));
        assert_eq!(cache.state(&key), PosterState::Failed);
        assert!(!cache.record(&key, None));
        assert_eq!(cache.claim(key.clone(), Some(&link)), PosterEffect::None);
        // a different URL is worth another attempt
        assert!(matches!(
            cache.claim(key.clone(), Some("https://dist.anixsekai.com/backup.jpg")),
            PosterEffect::Fetch { .. }
        ));
        // forgetting the entry is what makes an exact retry possible
        cache.forget(&key);
        assert_eq!(
            cache.claim(key.clone(), Some(&link)),
            PosterEffect::Fetch { key, url: link }
        );
    }

    #[test]
    fn a_truncated_image_is_rejected_instead_of_reaching_the_renderer() {
        let mut cache = PosterCache::new();
        let key = release_key(5);
        cache.claim(key.clone(), Some(&url(5)));
        let mut bytes = png_bytes();
        bytes.truncate(bytes.len() / 2);
        assert!(!cache.record(&key, Some(&bytes)));
        assert_eq!(cache.state(&key), PosterState::Failed);
        assert!(cache.peek(&key).is_none());
    }

    #[test]
    fn a_late_reply_for_a_forgotten_key_is_dropped() {
        let mut cache = PosterCache::new();
        let key = article_key(6);
        cache.claim(key.clone(), Some(&url(6)));
        cache.forget(&key);
        assert!(!cache.record(&key, Some(&png_bytes())));
        assert_eq!(cache.state(&key), PosterState::Idle);
        assert!(cache.is_empty());
    }

    #[test]
    fn recording_twice_is_idempotent() {
        let mut cache = PosterCache::new();
        let key = release_key(7);
        cache.claim(key.clone(), Some(&url(7)));
        assert!(cache.record(&key, Some(&png_bytes())));
        assert!(
            cache.record(&key, Some(&png_bytes())),
            "a duplicate reply keeps the ready state"
        );
        assert_eq!(cache.count_state(PosterState::Ready), 1);
        assert!(!cache.is_loading(&key));
    }

    #[test]
    fn clear_forgets_every_poster_of_the_previous_account() {
        let mut cache = PosterCache::new();
        cache.claim(release_key(8), Some(&url(8)));
        cache.claim(profile_key(8), Some(&url(8)));
        cache.claim(collection_key(9), None);
        cache.record(&release_key(8), Some(&png_bytes()));
        assert_eq!(cache.len(), 3);
        assert_eq!(cache.count_state(PosterState::Ready), 1);
        cache.clear();
        assert!(cache.is_empty());
        assert_eq!(cache.count_state(PosterState::Ready), 0);
        assert!(cache.peek(&release_key(8)).is_none());
        assert_eq!(cache.state(&release_key(8)), PosterState::Idle);
    }

    #[test]
    fn entries_are_counted_by_state() {
        let mut cache = PosterCache::new();
        cache.claim(release_key(1), Some(&url(1)));
        cache.claim(article_key(1), Some(&url(2)));
        cache.claim(release_key(3), None);
        cache.record(&release_key(1), Some(&png_bytes()));
        cache.record(&article_key(1), Some(b"junk"));
        assert_eq!(cache.count_state(PosterState::Ready), 1);
        assert_eq!(cache.count_state(PosterState::Failed), 2);
        assert_eq!(cache.count_state(PosterState::Loading), 0);
        assert_eq!(cache.count_state(PosterState::Idle), 0);
        assert!(cache.peek(&article_key(1)).is_none());
        assert!(!cache.is_loading(&release_key(3)));
        // an unseen key reads as Idle without having been inserted
        assert_eq!(cache.state(&release_key(99)), PosterState::Idle);
        assert_eq!(cache.len(), 3);
    }

    #[test]
    fn the_effect_of_a_claim_tells_the_caller_what_to_do() {
        let mut cache = PosterCache::new();
        assert!(!cache.claim(release_key(1), Some(&url(1))).is_idle());
        assert!(cache.claim(release_key(1), Some(&url(1))).is_idle());
        assert!(cache.claim(release_key(2), None).is_idle());
    }

    #[test]
    fn placeholder_tints_are_stable_and_vary_between_keys() {
        assert_eq!(placeholder_tint(&release_key(7)), placeholder_tint(&release_key(7)));
        let keys: Vec<String> = (0..24).map(release_key).collect();
        let tints: Vec<Color> = keys.iter().map(|key| placeholder_tint(key)).collect();
        let mut distinct = tints.clone();
        distinct.dedup();
        assert!(distinct.len() > 1, "a grid must not look like one flat block");
        for tint in &tints {
            // dark enough that a muted letter stays readable on top
            assert!(tint.r < 0.3 && tint.g < 0.3 && tint.b < 0.3);
            assert_eq!(tint.a, 1.0);
        }
        // the four namespaces differ for the same numeric id
        assert_ne!(placeholder_tint(&release_key(5)), placeholder_tint(&profile_key(5)));
        assert_eq!(placeholder_tint(""), placeholder_tint(""));
    }

    #[test]
    fn title_initials_survive_quotes_brackets_and_empty_titles() {
        assert_eq!(title_initial("«Наруто»"), 'Н');
        assert_eq!(title_initial("Naruto"), 'N');
        assert_eq!(title_initial("  (Bleach)"), 'B');
        assert_eq!(title_initial("[ERROR]"), 'E');
        assert_eq!(title_initial("One Piece"), 'O');
        assert_eq!(title_initial(""), '?');
        assert_eq!(title_initial("   "), '?');
        assert_eq!(title_initial("999"), '9');
        // both scripts are uppercased, cyrillic included
        assert_eq!(title_initial("shinobi"), 'S');
        assert_eq!(title_initial("шиноби"), 'Ш');
        assert_eq!(title_initial("«»"), '?');
    }
}
