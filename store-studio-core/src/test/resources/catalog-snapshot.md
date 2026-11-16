# Store Studio Device and Store Spec Catalog Snapshot

Catalog Version: 1

| Slot | Target | Store API ID | Verified | Families | Default Px | DPI | Default Dp | Size Rule | Count | Formats | Alpha | Max Bytes | Frame | Optional |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| PHONE | PLAY_STORE | phoneScreenshots | true | PHONE | 1080x1920 | 480 | 360x640 | Range(min=320, max=3840, maxAspect=2.0, square=false) | 2..8 (rec: 4) | JPEG, PNG | false | 8 MB | ANDROID_PHONE | false |
| TABLET_7 | PLAY_STORE | sevenInchScreenshots | true | TABLET_7 | 1200x1920 | 320 | 600x960 | Range(min=320, max=3840, maxAspect=2.0, square=false) | 0..8 (rec: 4) | JPEG, PNG | false | 8 MB | ANDROID_TABLET | false |
| TABLET_10 | PLAY_STORE | tenInchScreenshots | true | TABLET | 1600x2560 | 320 | 800x1280 | Range(min=320, max=3840, maxAspect=2.0, square=false) | 0..8 (rec: 4) | JPEG, PNG | false | 8 MB | ANDROID_TABLET | false |
| WEAR | PLAY_STORE | wearScreenshots | true | WATCH | 512x512 | 320 | 256x256 | Range(min=384, max=3840, maxAspect=1.0, square=true) | 1..8 | JPEG, PNG | false | 8 MB | NONE | false |
| FEATURE_GRAPHIC | PLAY_STORE | featureGraphic | true | - | 1024x500 | 320 | 512x250 | Exact(1024x500, transposed=false) | 0..1 | JPEG, PNG | false | 15 MB | NONE | false |
| IPHONE_6_9 | APP_STORE | APP_IPHONE_67 | false | PHONE | 1320x2868 | 480 | 440x956 | Exact(1320x2868, 1290x2796, 1260x2736, transposed=true) | 1..10 | JPEG, PNG | false | unlimited | IPHONE_DYNAMIC_ISLAND | false |
| IPHONE_6_5 | APP_STORE | APP_IPHONE_65 | false | PHONE | 1284x2778 | 480 | 428x926 | Exact(1284x2778, 1242x2688, transposed=true) | 1..10 | JPEG, PNG | false | unlimited | IPHONE_DYNAMIC_ISLAND | true |
| IPAD_13 | APP_STORE | APP_IPAD_PRO_3GEN_129 | false | TABLET | 2064x2752 | 320 | 1032x1376 | Exact(2064x2752, 2048x2732, transposed=true) | 1..10 | JPEG, PNG | false | unlimited | IPAD_MODERN | false |
