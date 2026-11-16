# Export

`./gradlew storeExport` renders (if needed) and writes the final, store-ready images to
`build/store/export/`, together with an `index.json` that describes every asset.

## What export does to each image

1. **Checks the size** against the exact dimensions of its store slot. Deviations of up to
   `sizeTolerancePx` (0–2 px) are fixed with `sizeMismatchPolicy`: `CROP_OR_PAD` trims or pads
   symmetrically with the background color, `FAIL` stops the export. Chameleon Store Studio never rescales
   images.
2. **Removes transparency**, which both stores reject, by flattening onto `background`.
3. **Encodes** a 24-bit PNG. If the PNG exceeds the store's size limit and `jpegFallback` is
   enabled, it re-encodes as JPEG, lowering the quality from `jpegQuality` until it fits.
4. **Hashes** the image (exact pixel hash and perceptual hash) for baselines and diffs, and records
   the text captured while rendering.

Images are processed in parallel, so export time grows slowly with the number of screenshots.

## Folder layout

```text
build/store/export/
├── play_store/
│   ├── en-US/
│   │   ├── phone/01_home.png
│   │   ├── tablet_10/01_home.png
│   │   └── feature_graphic/01_feature_graphic.png
│   └── es-419/…
├── app_store/
│   └── en-US/
│       ├── iphone_6_9/01_home.png
│       └── ipad_13/01_home.png
└── index.json
```

| `layout` | Structure |
|---|---|
| `STORE_LOCALE_DEVICE` (default) | `play_store/en-US/phone/01_home.png` |
| `LOCALE_STORE_DEVICE` | `en-US/play_store/phone/01_home.png` |
| `LOCALE_DEVICE` | `en-US/phone/01_home.png` |

The default layout is plain folders per store, locale and device, so you can upload them with your
own tooling if you don't use Store Studio's [publishing](publishing.md).

## Listing texts

After `storeExport`, the `storeExportMetadata` task runs automatically and writes the
[metadata](metadata.md) texts as plain files to `build/store/export-metadata/`. It is a sibling of
`build/store/export/` with the same inner layout, so the two trees merge with a plain copy. Only the
enabled targets are written, and empty fields are skipped.

```text
build/store/export-metadata/
├── play_store/
│   ├── en-US/metadata/
│   │   ├── title.txt
│   │   ├── short_description.txt
│   │   ├── full_description.txt
│   │   └── release_notes.txt
│   └── release-notes/
│       ├── whatsnew-en-US
│       └── whatsnew-es-MX
└── app_store/
    └── en-US/metadata/
        ├── name.txt
        ├── subtitle.txt
        └── …
```

- File names are the same as the [input files](metadata.md#folder-structure).
- `play_store/release-notes/whatsnew-<locale>` (no extension) is written for each Google Play locale
  with release notes. It is the flat format commonly expected by Google Play upload steps in CI.
- With `layout` set to `LOCALE_STORE_DEVICE` the texts follow the same order (`en-US/play_store/metadata/`).
  With `LOCALE_DEVICE`, where both stores share the locale folder, they go to
  `<locale>/metadata/<store>/`.

You can also run `./gradlew :app:storeExportMetadata` on its own.

## Configuration

```kotlin
storeStudio {
    export {
        background.set("#19191C")          // flatten onto your app's background color
        sizeTolerancePx.set(0)             // require exact sizes
        sizeMismatchPolicy.set(SizeMismatchPolicy.FAIL)
        jpegFallback.set(true)
        jpegQuality.set(0.92f)
        layout.set(ExportLayout.STORE_LOCALE_DEVICE)
        lowercaseFolders.set(true)
    }
}
```

`exportStoreAssets` is an alias of `storeExport`.
