# Contributing

Contributions are welcome: bug reports, rule updates when the stores change their requirements,
documentation fixes and features.

## Reporting issues

Open an [issue](https://github.com/ribod/chameleon-store-studio/issues) with:

- the output of `./gradlew :app:storeDoctor`;
- the Gradle, AGP, Kotlin and KSP versions;
- for rendering problems, the renderer log from `build/store/render/work/` and, if possible, a
  minimal composable that reproduces it.

## Building from source

Requires JDK 17+.

```bash
git clone https://github.com/ribod/chameleon-store-studio.git
cd chameleon-store-studio
./gradlew check                     # libraries, KSP processor and Gradle plugin
./gradlew checkIdeaPlugin           # IDE plugin (a separate build in store-studio-idea/)
```

The samples include the repository as a composite build, so they always use your local changes:

```bash
./gradlew -p samples/sample-kotlinconf :app:storeExport
```

## Documentation

The site is built with [Zensical](https://zensical.org). With Python 3.11+:

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r docs/requirements.txt
./scripts/docs.sh serve
```

Documentation pages live in `docs/`.

## Pull requests

- Keep changes focused, with tests for new behavior.
- Run `./gradlew check` before opening the pull request.
- The public API of `store-studio-annotations`, `store-studio-runtime` and the Gradle plugin is
  tracked in their `api/` folders. If you change it on purpose, run
  `./gradlew updateKotlinAbi` and commit the updated dumps; `check` fails otherwise.

## Continuous integration

| Workflow | Runs | What it checks |
|---|---|---|
| `ci.yml` | Every pull request and push to `main` | `./gradlew check` (formatting, tests, public API) on JDK 17 and 21, the iOS targets (cross-compiled on Linux), and the IDE plugin's tests and build. |
| `docs.yml` | Changes to the documentation | Builds the site in strict mode and deploys it from `main`. |
| `release.yml` | Publishing a GitHub Release | Runs `ci.yml`, publishes to Maven Central and attaches the IDE plugin zip to the release. |

Before cutting a release or after large changes, run the heavier checks locally:

```bash
# IDE Plugin Verifier against supported IDEs
./gradlew -p store-studio-idea verifyPlugin

# Full pipeline on both samples
./gradlew -p samples/sample-kotlinconf :app:storeExport :app:storeValidate :app:storeVerify
./gradlew -p samples/sample-nowinandroid :app:storeExport :app:storeValidate :app:storeVerify
```

## Releasing (maintainers)

Everything is published to **Maven Central**: the libraries and the Gradle plugin, with its plugin
marker, so `id("dev.ribod.storestudio")` resolves from `mavenCentral()`.

1. Set the same version in `gradle.properties` (`version=`) and `store-studio-idea/build.gradle.kts`,
   and update `docs/changelog.md`.
2. Create a GitHub Release with the tag `v<version>` and publish it. `release.yml` checks that the tag
   and both versions match, runs the CI and IDE checks, and waits for approval of the `release`
   environment if it requires one.
3. The deployment stays in the Central Portal for review: check it and publish it from
   [central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments).

The `release` environment needs these secrets: `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`
(a Central Portal user token), and `SIGNING_KEY`, `SIGNING_KEY_ID` and `SIGNING_KEY_PASSWORD` (an
ASCII-armored GPG private key). Running the workflow manually is a dry run that publishes to Maven
Local only.

To try a release locally, `./gradlew publishToMavenLocal` publishes unsigned artifacts that a project
can consume with `mavenLocal()` instead of the composite build.

- Write commit messages in the [Conventional Commits](https://www.conventionalcommits.org/) style
  (`feat(gradle-plugin): …`, `fix(idea): …`).
