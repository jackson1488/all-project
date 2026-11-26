//! Small formatting helpers shared by the Russian screens: dates without a
//! chrono dependency and the plural forms a Russian interface cannot avoid.

/// Gregorian calendar date `(year, month, day)` of a day counted from
/// 1970-01-01 (negative days count back).
///
/// Howard Hinnant's `days_from_civil` inverse: it shifts the epoch to the first
/// of March so the leap day falls at the end of the year, which makes the
/// month arithmetic a single division.
pub fn civil_from_days(days: i64) -> (i64, u32, u32) {
    let z = days + 719_468;
    let era = if z >= 0 { z } else { z - 146_096 } / 146_097;
    let day_of_era = (z - era * 146_097).rem_euclid(146_097);
    let year_of_era =
        (day_of_era - day_of_era / 1460 + day_of_era / 36_524 - day_of_era / 146_096) / 365;
    let yoe = year_of_era + era * 400;
    let day_of_year = day_of_era - (365 * year_of_era + year_of_era / 4 - year_of_era / 100);
    let mp = (5 * day_of_year + 2) / 153;
    let day = day_of_year - (153 * mp + 2) / 5 + 1;
    let month = if mp < 10 { mp + 3 } else { mp - 9 };

    (if month <= 2 { yoe + 1 } else { yoe }, month as u32, day as u32)
}

/// `14.11.2023` out of a UNIX timestamp; `—` for the zero timestamps the API
/// sends for entries that were never dated.
pub fn date_label(timestamp: i64) -> String {
    if timestamp <= 0 {
        return "—".to_owned();
    }
    let (year, month, day) = civil_from_days(timestamp / 86_400);
    format!("{day:02}.{month:02}.{year}")
}

/// Russian plural form of a count: `1 эпизод`, `3 эпизода`, `7 эпизодов`.
pub fn plural<'a>(count: i64, one: &'a str, few: &'a str, many: &'a str) -> &'a str {
    let last = count.rem_euclid(10);
    let last_two = count.rem_euclid(100);
    if last == 1 && last_two != 11 {
        one
    } else if (2..=4).contains(&last) && !(12..=14).contains(&last_two) {
        few
    } else {
        many
    }
}

/// `«7 эпизодов»` / `«3 эпизода»` — the count and its noun in one step.
pub fn count_label(count: i64, one: &str, few: &str, many: &str) -> String {
    format!("{count} {}", plural(count, one, few, many))
}

/// `1 – 5`, `1 – 50`, `0` — episode counts of a voiceover, the way the official
/// client shows them.
pub fn episodes_label(released: Option<i32>, total: Option<i32>) -> String {
    match (released, total) {
        (Some(released), Some(total)) if total > 0 && released != total => {
            format!("{released} – {total}")
        }
        (Some(released), _) => released.to_string(),
        (_, Some(total)) => total.to_string(),
        (None, None) => "—".to_owned(),
    }
}

/// Compacts a big count for a badge: `1,2 тыс.` instead of `1234`.
pub fn compact_count(count: i64) -> String {
    if count < 1000 {
        return count.to_string();
    }
    let tenths = count / 100;
    format!("{},{} тыс.", tenths / 10, tenths % 10)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn civil_from_days_covers_the_epoch_and_both_directions() {
        assert_eq!(civil_from_days(0), (1970, 1, 1));
        assert_eq!(civil_from_days(-1), (1969, 12, 31));
        assert_eq!(civil_from_days(19_722), (2023, 12, 31));
        assert_eq!(civil_from_days(20_088), (2024, 12, 31));
        // year boundaries
        assert_eq!(civil_from_days(19_357), (2022, 12, 31));
        assert_eq!(civil_from_days(19_358), (2023, 1, 1));
        assert_eq!(civil_from_days(20_087), (2024, 12, 30));
    }

    #[test]
    fn date_label_formats_timestamps_and_refuses_zeros() {
        assert_eq!(date_label(1_700_000_000), "14.11.2023");
        assert_eq!(date_label(0), "—");
        assert_eq!(date_label(-3_600), "—");
        // the first second of 1970 and of 2000
        assert_eq!(date_label(1), "01.01.1970");
        assert_eq!(date_label(946_684_800), "01.01.2000");
    }

    #[test]
    fn russian_plural_forms_follow_the_last_digits() {
        let forms = |n| plural(n, "эпизод", "эпизода", "эпизодов");
        for n in [1i64, 21, 31, 41, 101, 1021] {
            assert_eq!(forms(n), "эпизод", "{n}");
        }
        for n in [2i64, 3, 4, 22, 34, 104] {
            assert_eq!(forms(n), "эпизода", "{n}");
        }
        // 11 – 14 are the exceptions, and so are zero and the tens
        for n in [0i64, 5, 6, 11, 12, 13, 14, 20, 25, 111, 112, 115, 1111] {
            assert_eq!(forms(n), "эпизодов", "{n}");
        }
        // negative counts never reach the UI, but must not panic either
        assert_eq!(forms(-1), "эпизод");
        assert_eq!(forms(-5), "эпизодов");
    }

    #[test]
    fn count_label_glues_number_and_noun() {
        assert_eq!(count_label(1, "озвучка", "озвучки", "озвучек"), "1 озвучка");
        assert_eq!(count_label(4, "озвучка", "озвучки", "озвучек"), "4 озвучки");
        assert_eq!(count_label(9, "озвучка", "озвучки", "озвучек"), "9 озвучек");
    }

    #[test]
    fn episodes_label_shows_progress_or_a_single_number() {
        assert_eq!(episodes_label(Some(3), Some(12)), "3 – 12");
        assert_eq!(episodes_label(Some(12), Some(12)), "12");
        assert_eq!(episodes_label(Some(0), Some(12)), "0 – 12");
        assert_eq!(episodes_label(None, Some(12)), "12");
        assert_eq!(episodes_label(Some(7), None), "7");
        assert_eq!(episodes_label(None, None), "—");
    }

    #[test]
    fn compact_count_keeps_small_numbers_exact() {
        assert_eq!(compact_count(0), "0");
        assert_eq!(compact_count(999), "999");
        assert_eq!(compact_count(1000), "1,0 тыс.");
        assert_eq!(compact_count(1234), "1,2 тыс.");
        assert_eq!(compact_count(12_345), "12,3 тыс.");
        assert_eq!(compact_count(-42), "-42");
    }
}
