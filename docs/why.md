# Why Chameleon Store Studio

## The problem

Store screenshots are some of the most visible pixels an app ships, yet they are usually produced
far away from the code:

- Someone runs the app on an emulator, takes screenshots, and pastes them into a design tool to add
  headlines and device frames.
- Every store wants different sizes: Google Play phone, 7-inch and 10-inch tablets, the iPhone 6.9"
  and iPad 13" sets, a 1024×500 feature graphic. Multiply that by every language you support.
- Each store has strict rules (no transparency, exact dimensions, file size limits, a minimum and
  maximum number of images, text length limits) that are only discovered when an upload is rejected.
- After a redesign or a copy change, nobody remembers to update them, and the listing quietly drifts
  away from the real app.

## The idea

Chameleon Store Studio treats store assets like any other part of your codebase:

- **They are code.** A screenshot is a `@Composable` that reuses your real screens, theme and
  resources, with marketing headlines and device frames from the runtime library.
- **They are rendered, not captured.** Layoutlib (the engine behind Android Studio previews) renders
  every screenshot headlessly on the JVM, with Robolectric as an automatic fallback. No emulator,
  device or design tool is involved.
- **They are generated for every store, device and locale.** One composable produces all the sizes
  the stores require, in every language you configure, using your localized resources.
- **They are checked.** The export step enforces exact dimensions and removes transparency, and the
  validation step applies about fifty Google Play and App Store rules before you upload anything.
- **They are reviewed.** Each pull request is compared with its base branch rendered on the same
  machine, so every visual and text change shows up as a diff report or a sticky comment, without
  committing images to the repository.
- **They are close to where you work.** The IDE plugin shows the gallery, a simulated store listing,
  the visual diff and validation results next to your code, and jumps to the composable that
  produced each image.

## What it is not

- **Not a UI testing or screenshot testing library.** It doesn't assert on your screens' behavior.
  It produces marketing assets; tools like Roborazzi or Paparazzi are better suited for regression
  tests of your UI.
- **Not a design tool.** Layouts are written in Compose. If you need hand-crafted illustrations, you
  can still draw them in Compose or include them as images.
- **Not a replacement for your release process.** Publishing is optional; you can stop after
  `storeExport` and upload the generated folders with any tool you already use.

## When it fits best

- Apps whose screens can be rendered with sample data (the same requirement as Compose previews).
- Teams that ship in several languages or to both stores and want the listing to stay in sync with
  the app.
- Projects that review UI changes in pull requests and want store assets to be part of that review.
