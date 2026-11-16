# FAQ

**Do I need an emulator or a device?**
No. Screenshots are rendered on the JVM with Layoutlib (the engine behind Android Studio previews),
with Robolectric as a fallback.

**Do I need a Mac to generate App Store screenshots?**
No. App Store screenshots are rendered on the JVM like the Google Play ones, with iPhone and iPad
frames. Your UI must be Compose. An iOS status bar is drawn when you enable the status bar; it is off
by default (see [Composing Screenshots](composing.md#status-bar)).

**Does it work with apps that aren't Compose?**
The screenshots themselves are composables, but they can host Android Views through `AndroidView`.
Whether a View renders depends on Layoutlib or Robolectric supporting it.

**Is this a screenshot testing library?**
No. It produces store marketing assets. For UI regression testing, use a screenshot testing library
such as Roborazzi or Paparazzi. The baselines here exist to review changes to the store listing.

**Are images committed to the repository?**
No. Baselines contain only hashes and text. Images are generated into `build/`.

**Where do the screenshots go?**
`build/store/export/`, in folders per store, locale and device. See [Export](export.md).

**Can I upload them with other tools?**
Yes. The export is plain folders of images, and the listing texts are exported too (see
[Export](export.md#listing-texts)). Or use Chameleon Store Studio's own [publishing](publishing.md).

**How do I show a different headline per store?**
Read `LocalStoreContext.current.target` inside the composable. See
[Composing Screenshots](composing.md#storecontext).

**Can different locales have different screenshots?**
Yes. Use `locales = [...]` on the annotation to restrict a screenshot to some locales.

**Is it stable?**
Not yet: it is experimental until 1.0.0. See [Stability](stability.md).
