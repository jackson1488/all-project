//! Serde models mirroring Anixart API payloads.

pub mod auth;
pub mod collection;
pub mod common;
pub mod episode;
pub mod feed;
pub mod profile;
pub mod release;

pub use auth::{CheckLoginResponse, ProfileToken, SignInResponse};
pub use collection::{Collection, CollectionResponse};
pub use common::{codes, CodeResponse, Pageable};
pub use episode::{
    Episode, EpisodeResponse, EpisodeType, Source, SourcesResponse, TypesResponse, Voiceover,
};
pub use feed::{FeedChannel, FeedItem};
pub use profile::{Profile, ProfileInfoResponse, ProfileResponse};
pub use release::{Related, Release, ReleaseResponse, ReleaseSearchResponse, SearchRequest};
