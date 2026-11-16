# Adopting in an Existing Project

Chameleon Store Studio renders composables without running your app, so the main work is making the screens
you want to show renderable in isolation. These steps keep the first adoption small.

## 1. Pick three or four screens

Choose the screens you'd show in the store, usually the home feed, a detail screen and one feature
that makes the app special. Start in the application module, or in the feature module that owns
those screens (see [Multi-Module Projects](../multi-module.md)).

## 2. Make the screens stateless

Screenshots are parameterless composables, and they can't use your DI graph, network or database.
The usual pattern, also recommended for Compose previews, is to split each screen in two:

```kotlin
@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {     // wiring: not rendered
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state = state, onItemClick = viewModel::open)
}

@Composable
fun HomeScreen(state: HomeState, onItemClick: (Item) -> Unit) { // pure UI: rendered
    /* ... */
}
```

Then build realistic sample state for the screenshot:

```kotlin
@StoreScreenshot(id = "home", order = 1, name = "Home")
@Composable
fun HomeStoreScreenshot() {
    StoreScreenshotCanvas {
        Headline(stringResource(R.string.store_home_headline))
        DeviceMockup {
            AppTheme { HomeScreen(state = SampleData.homeState, onItemClick = {}) }
        }
    }
}
```

!!! tip "Sample data is marketing copy"
    Store screenshots are read by potential users: use realistic names, content and numbers, and
    translate them like any other string.

## 3. Put headlines in resources

Add the headlines to `strings.xml` (or Compose Multiplatform `composeResources`) so every locale gets
translated screenshots.

## 4. Export and look

```bash
./gradlew :app:storeExport :app:storeGallery
```

Open the IDE tool window or `build/store/reports/gallery.html`. Screens that couldn't render in
Layoutlib show a `ROBOLECTRIC` badge; screens that couldn't render at all fail the task with the
cause. See [Troubleshooting](troubleshooting.md).

## 5. Make validation pass

Run `./gradlew :app:storeValidate` and work through the errors: usually adding screenshots (Google
Play needs at least two per locale), a [feature graphic](../annotations.md#storefeaturegraphic) and
the required device sets. While you're still building the set, you can lower
`validation.featureGraphicMissing` to `WARNING`.

## 6. Add CI

Add [`storeCi`](../ci.md) to your pull request workflow, comparing each pull request with its base
branch rendered on the same runner. From then on, every screen change that affects the store
listing is visible in review, without recording or committing a baseline.

## 7. Add metadata and publishing (optional)

Move your listing text into [`store/metadata`](../metadata.md) to validate its length and keep it
versioned, and use [publishing](../publishing.md) when you're comfortable with the dry runs.
