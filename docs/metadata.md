# Store Metadata

Besides images, a store listing has text: title, descriptions, keywords, release notes. Chameleon Store Studio
reads it from plain text files in your repository, validates it with the
[listing text rules](validation.md#listing-text), shows it in the listing preview, and uploads it
when you [publish](publishing.md).

Metadata is optional. Without it, image validation and publishing of images still work.

## Folder structure

By default files live in `store/metadata/` inside the module (`metadata.directory`), one folder per
locale:

```text
app/store/metadata/
├── en-US/
│   ├── play/
│   │   ├── title.txt
│   │   ├── short_description.txt
│   │   ├── full_description.txt
│   │   └── release_notes.txt
│   └── appstore/
│       ├── name.txt
│       ├── subtitle.txt
│       ├── keywords.txt
│       ├── promotional_text.txt
│       ├── description.txt
│       ├── release_notes.txt
│       ├── support_url.txt
│       └── marketing_url.txt
└── es-MX/
    ├── play/title.txt
    └── appstore/subtitle.txt
```

Every file is optional. Leading and trailing whitespace is trimmed.

### Google Play files

| File | Limit |
|---|---|
| `title.txt` | 30 characters |
| `short_description.txt` | 80 characters |
| `full_description.txt` | 4000 characters |
| `release_notes.txt` | 500 characters |

### App Store files

| File | Limit |
|---|---|
| `name.txt` | 30 characters |
| `subtitle.txt` | 30 characters |
| `keywords.txt` | 100 bytes, comma-separated |
| `promotional_text.txt` | 170 characters |
| `description.txt` | 4000 characters |
| `release_notes.txt` | 4000 characters |
| `support_url.txt` | valid URL |
| `marketing_url.txt` | valid URL |

!!! note "One layout only"
    Only the `<locale>/play/` and `<locale>/appstore/` folders and the file names above are read.
    `storeParseMetadata` warns when it finds files from other layouts (`android/`, `ios/` or
    `appstore/` folders at the top level, or `.txt` files placed directly in a locale folder) and
    ignores them. Rename or move them.

## Fallback to the default locale

Fields missing in a locale are taken from `metadata.defaultLocale` (default `en-US`). Translate only
what differs; for example `es-MX` can provide just a title and short description.

```kotlin
storeStudio {
    metadata {
        directory.set(layout.projectDirectory.dir("store/metadata"))
        defaultLocale.set("en-US")
    }
}
```

## Parsed output

`./gradlew storeParseMetadata` writes the resolved text, after fallback, to
`build/store/metadata/metadata.json`. Validation, the listing preview and publishing read this file.
It runs automatically when needed.
