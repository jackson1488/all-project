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
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct FilterRequest {
    /// 0 = new update descending, 1 = grade desc, 2 = year desc, 3 = popular desc,
    /// 4 = date update asc, 5 = grade asc, 6 = year asc, 7 = popular asc.
    #[serde(default)]
    pub sort: i32,

    #[serde(default, alias = "categoryId", skip_serializing_if = "Option::is_none")]
    pub category_id: Option<i64>,

    #[serde(default, alias = "statusId", skip_serializing_if = "Option::is_none")]
    pub status_id: Option<i64>,

    #[serde(default, alias = "startYear", skip_serializing_if = "Option::is_none")]
    pub start_year: Option<i32>,

    #[serde(default, alias = "endYear", skip_serializing_if = "Option::is_none")]
    pub end_year: Option<i32>,

    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub country: Option<String>,

    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub studio: Option<String>,

    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source: Option<String>,

    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub season: Option<i32>,

    #[serde(default, alias = "genresMode", skip_serializing_if = "Option::is_none")]
    pub genres_mode: Option<i32>,

    #[serde(default, alias = "episodesFrom", skip_serializing_if = "Option::is_none")]
    pub episodes_from: Option<i32>,

    #[serde(default, alias = "episodesTo", skip_serializing_if = "Option::is_none")]
    pub episodes_to: Option<i32>,

    #[serde(default, alias = "episodeDurationFrom", skip_serializing_if = "Option::is_none")]
    pub episode_duration_from: Option<i32>,

    #[serde(default, alias = "episodeDurationTo", skip_serializing_if = "Option::is_none")]
    pub episode_duration_to: Option<i32>,

    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub genres: Vec<String>,

    #[serde(default, alias = "profileListExclusions", skip_serializing_if = "Vec::is_empty")]
    pub profile_list_exclusions: Vec<i32>,

    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub types: Vec<i64>,

    #[serde(default, alias = "ageRatings", skip_serializing_if = "Vec::is_empty")]
    pub age_ratings: Vec<i32>,

    #[serde(
        default,
        alias = "isGenresExcludeModeEnabled",
        skip_serializing_if = "std::ops::Not::not"
    )]
    pub is_genres_exclude_mode_enabled: bool,
}

