### Goal

<!-- Why this change? Link the issue it fixes, if any. -->

### Changes

<!-- What changed and how. Mention anything reviewers should look at closely. -->

### Checklist

- [ ] `./gradlew check` passes (run `./gradlew spotlessApply` to fix formatting)
- [ ] If the public API of the annotations, runtime or Gradle plugin changed on purpose:
      `./gradlew updateKotlinAbi` and the updated `api/` dumps are committed
- [ ] If the IDE plugin changed: `./gradlew -p store-studio-idea check`
- [ ] Documentation in `docs/` updated for user-visible changes
