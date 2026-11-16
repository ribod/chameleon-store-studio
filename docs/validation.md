# Validation

`./gradlew storeValidate` checks the exported images and listing text against Google Play and App
Store rules, before you upload anything. The report is written to
`build/store/validation/report.json`, printed in the console, and shown in the IDE's
[Validation tab](ide/validation.md).

## Severities

| Severity | Meaning |
|---|---|
| `ERROR` | The store would reject it. Fails the task. |
| `WARNING` | Allowed, but probably a mistake. Fails only with `failOnWarning`. |
| `INFO` | For your information. |

Some rules are **hard requirements** (marked *hard* below): their severity can't be lowered below
`ERROR`.

```kotlin
storeStudio {
    validation {
        failOnWarning.set(true)
        featureGraphicMissing.set(Severity.WARNING)          // e.g. while the banner is in progress
        severityOverrides.put("PLAY_PROMO_ELIGIBILITY", Severity.WARNING)
        attachToCheck.set(true)                              // run with ./gradlew check
    }
}
```

## Application and library modules

Rules about the **listing as a whole** (how many screenshots, required sets, feature graphic,
locale coverage, listing text) only apply to the module that produces the listing: the
**application** module. Library modules (`:feature:*`, a KMP `:shared` module) only contribute some
screenshots, so they run the **per-image** rules (format, size, transparency, text overflow). This
lets `./gradlew storeValidate` pass in multi-module projects.

## Rules

### Every image

| Rule | Severity | Description |
|---|---|---|
| `ASSET_RENDER_FAILED` | ERROR, hard | The screenshot failed in every rendering engine. |
| `ASSET_RENDER_FALLBACK_USED` | INFO | Rendered by Robolectric instead of Layoutlib. |
| `ASSET_DIMENSIONS_MISMATCH` | ERROR, hard | Size differs from the slot's exact dimensions. |
| `ASSET_ALPHA_CHANNEL` | ERROR, hard | The exported file still has transparency. |
| `ASSET_INDEX_STALE` | ERROR, hard | The export index points to a file that no longer exists. |
| `SEMANTICS_TEXT_OVERFLOW` | WARNING | Text is cut off or overflowing (often a long translation). |
| `ORDER_DUPLICATE` | WARNING | Two screenshots share the same `order` in a set. |

### Google Play

| Rule | Severity | Description |
|---|---|---|
| `PLAY_SCREENSHOT_FORMAT` | ERROR, hard | Must be PNG or JPEG. |
| `PLAY_SCREENSHOT_ALPHA` | ERROR, hard | No transparency. |
| `PLAY_SCREENSHOT_FILE_SIZE` | ERROR, hard | At most 8 MB (8,000,000 bytes). |
| `PLAY_SCREENSHOT_MIN_SIDE` | ERROR, hard | Short side at least 320 px. |
| `PLAY_SCREENSHOT_MAX_SIDE` | ERROR, hard | Long side at most 3840 px. |
| `PLAY_SCREENSHOT_ASPECT_RATIO` | ERROR, hard | Long side at most twice the short side. |
| `PLAY_WEAR_SQUARE` | ERROR, hard | Wear OS screenshots are square, at least 384 px. |
| `PLAY_SCREENSHOT_COUNT_MIN` | ERROR, hard | At least 2 phone screenshots per locale. Flags a locale with exactly one. |
| `PLAY_SCREENSHOT_COUNT_MAX` | ERROR, hard | At most 8 screenshots per device type and locale. |
| `PLAY_PROMO_ELIGIBILITY` | INFO | Promotion requires at least 4 phone screenshots with a short side of 1080 px or more. |
| `PLAY_FEATURE_GRAPHIC_REQUIRED` | ERROR (configurable) | A 1024 × 500 feature graphic per locale. Set the severity with `featureGraphicMissing`. |
| `PLAY_FEATURE_GRAPHIC_DIMENSIONS` | ERROR, hard | Exactly 1024 × 500 px. |
| `PLAY_FEATURE_GRAPHIC_FORMAT` | ERROR, hard | Opaque PNG or JPEG, without transparency. |
| `PLAY_FEATURE_GRAPHIC_FILE_SIZE` | ERROR, hard | At most 15 MB (15,000,000 bytes). |
| `PLAY_FEATURE_GRAPHIC_DUPLICATE` | ERROR, hard | One feature graphic per locale. |
| `PLAY_TABLET_PARTIAL` | WARNING | Some locales have tablet screenshots and others don't. |

### App Store

Apple publishes no hard file-size limit, so the App Store size check is a recommendation only.

| Rule | Severity | Description |
|---|---|---|
| `ASC_SCREENSHOT_FORMAT` | ERROR, hard | PNG or JPEG. |
| `ASC_SCREENSHOT_ALPHA` | ERROR, hard | No transparency. |
| `ASC_SCREENSHOT_COLOR_SPACE` | ERROR, hard | sRGB color profile. |
| `ASC_SCREENSHOT_DIMENSIONS` | ERROR, hard | An accepted size for the display type. |
| `ASC_SCREENSHOT_FILE_SIZE` | WARNING | Above 10 MiB, the recommended App Store limit. |
| `ASC_SCREENSHOT_COUNT_MIN` | ERROR, hard | At least 1 screenshot per display set. |
| `ASC_SCREENSHOT_COUNT_MAX` | ERROR, hard | At most 10 screenshots per display set. |
| `ASC_ORIENTATION_CONSISTENCY` | ERROR, hard | One orientation per display set. |
| `ASC_IPHONE_69_REQUIRED` | ERROR, hard | The iPhone 6.9" set is required. |
| `ASC_IPAD_13_REQUIRED` | ERROR, hard | The iPad 13" set is required when the app supports iPad. |
| `ASC_IPAD_UNEXPECTED` | WARNING | iPad images exist but `appStore.supportsIpad` is `false`. |

### Locales

| Rule | Severity | Description |
|---|---|---|
| `LOCALE_COVERAGE` | ERROR, hard | A configured store has no images for a locale. |
| `LOCALE_COVERAGE_INCONSISTENT` | WARNING | Locales have different numbers of images in the same set. |

### Listing text

These run when [store metadata](metadata.md) is present.

| Rule | Severity | Description |
|---|---|---|
| `PLAY_TEXT_TITLE_LENGTH` | ERROR, hard | Title up to 30 characters. |
| `PLAY_TEXT_SHORT_DESC_LENGTH` | ERROR, hard | Short description up to 80 characters. |
| `PLAY_TEXT_FULL_DESC_LENGTH` | ERROR, hard | Full description up to 4000 characters. |
| `PLAY_TEXT_RELEASE_NOTES_LENGTH` | ERROR, hard | Release notes up to 500 characters. |
| `METADATA_POLICY_CLAIMS` | WARNING | Ranking or price claims ("best", "#1", "free") that Google Play policy discourages. |
| `METADATA_POLICY_PUNCTUATION` | WARNING | Excessive punctuation such as `!!!`. |
| `APPSTORE_TEXT_NAME_LENGTH` | ERROR, hard | Name up to 30 characters. |
| `APPSTORE_TEXT_SUBTITLE_LENGTH` | ERROR, hard | Subtitle up to 30 characters. |
| `APPSTORE_TEXT_KEYWORDS_LENGTH` | ERROR, hard | Keywords up to 100 bytes. |
| `APPSTORE_TEXT_PROMO_LENGTH` | ERROR, hard | Promotional text up to 170 characters. |
| `APPSTORE_TEXT_DESC_LENGTH` | ERROR, hard | Description up to 4000 characters. |
| `APPSTORE_TEXT_WHATS_NEW_LENGTH` | ERROR, hard | What's New up to 4000 characters. |
| `APPSTORE_TEXT_SUPPORT_URL` | ERROR, hard | A valid `http://` or `https://` URL, when set. |
| `APPSTORE_TEXT_MARKETING_URL` | ERROR, hard | A valid `http://` or `https://` URL, when set. |
| `APPSTORE_TEXT_EMOJI_FORBIDDEN` | WARNING | Emoji in name, subtitle or keywords. |
