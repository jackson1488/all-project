use serde::{Deserialize, Serialize};

/// Watch-status values used by `/profile/list/*` endpoints.
///
/// | ID  | Meaning        |
/// |-----|----------------|
/// | 1   | Watching       |
/// | 2   | Planned        |
/// | 3   | Watched        |
/// | 4   | Delayed        |
/// | 5   | Dropped        |
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[repr(i32)]
pub enum WatchStatus {
    Watching = 1,
    Planned = 2,
    Watched = 3,
    Delayed = 4,
    Dropped = 5,
}

impl WatchStatus {
    /// All known status variants in ascending order.
    pub const ALL: [WatchStatus; 5] = [
        WatchStatus::Watching,
        WatchStatus::Planned,
        WatchStatus::Watched,
        WatchStatus::Delayed,
        WatchStatus::Dropped,
    ];

    pub fn as_i32(self) -> i32 {
        self as i32
    }

    /// Parse from integer, returning `None` for unknown values.
    pub fn from_i32(v: i32) -> Option<Self> {
        match v {
            1 => Some(WatchStatus::Watching),
            2 => Some(WatchStatus::Planned),
            3 => Some(WatchStatus::Watched),
            4 => Some(WatchStatus::Delayed),
            5 => Some(WatchStatus::Dropped),
            _ => None,
        }
    }
}

impl From<WatchStatus> for i32 {
    fn from(s: WatchStatus) -> i32 {
        s.as_i32()
    }
}

// Custom serde: serialize/deserialize as i32.
impl Serialize for WatchStatus {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.serialize_i32(self.as_i32())
    }
}

impl<'de> Deserialize<'de> for WatchStatus {
    fn deserialize<D: serde::Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        let v = i32::deserialize(deserializer)?;
        WatchStatus::from_i32(v).ok_or_else(|| {
            serde::de::Error::custom(format!("unknown watch status value: {v}"))
        })
    }
}

/// Body of `POST /filter/{page}`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct FilterRequest {
    /// 0 = new update descending, 1 = grade desc, 2 = year desc, 3 = popular desc,
    /// 4 = date update asc, 5 = grade asc, 6 = year asc, 7 = popular asc.
    #[serde(default)]
    pub sort: i32,

    #[serde(default, alias = "category_id", skip_serializing_if = "Option::is_none")]
    pub category_id: Option<i64>,

    #[serde(default, alias = "status_id", skip_serializing_if = "Option::is_none")]
    pub status_id: Option<i64>,

    #[serde(default, alias = "start_year", skip_serializing_if = "Option::is_none")]
    pub start_year: Option<i32>,

    #[serde(default, alias = "end_year", skip_serializing_if = "Option::is_none")]
    pub end_year: Option<i32>,

    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub country: Option<String>,

    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub studio: Option<String>,

    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source: Option<String>,

    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub season: Option<i32>,

    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub genres_mode: Option<i32>,

    #[serde(default, alias = "episodes_from", skip_serializing_if = "Option::is_none")]
    pub episodes_from: Option<i32>,

    #[serde(default, alias = "episodes_to", skip_serializing_if = "Option::is_none")]
    pub episodes_to: Option<i32>,

    #[serde(default, alias = "episode_duration_from", skip_serializing_if = "Option::is_none")]
    pub episode_duration_from: Option<i32>,

    #[serde(default, alias = "episode_duration_to", skip_serializing_if = "Option::is_none")]
    pub episode_duration_to: Option<i32>,

    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub genres: Vec<String>,

    #[serde(default, alias = "profile_list_exclusions", skip_serializing_if = "Vec::is_empty")]
    pub profile_list_exclusions: Vec<i32>,

    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub types: Vec<i64>,

    #[serde(default, alias = "age_ratings", skip_serializing_if = "Vec::is_empty")]
    pub age_ratings: Vec<i32>,

    #[serde(
        default,
        alias = "is_genres_exclude_mode_enabled",
        skip_serializing_if = "std::ops::Not::not"
    )]
    pub is_genres_exclude_mode_enabled: bool,
}

impl Default for FilterRequest {
    fn default() -> Self {
        Self {
            sort: 0,
            category_id: None,
            status_id: None,
            start_year: None,
            end_year: None,
            country: None,
            studio: None,
            source: None,
            season: None,
            genres_mode: None,
            episodes_from: None,
            episodes_to: None,
            episode_duration_from: None,
            episode_duration_to: None,
            genres: Vec::new(),
            profile_list_exclusions: Vec::new(),
            types: Vec::new(),
            age_ratings: Vec::new(),
            is_genres_exclude_mode_enabled: false,
        }
    }
}
