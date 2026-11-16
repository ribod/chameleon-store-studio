# Stability

!!! warning "Experimental until 1.0.0"
    Chameleon Store Studio is **experimental** in all `0.x` releases.

Until 1.0.0:

- the `storeStudio { }` DSL, the annotations and the runtime API may change between minor
  releases;
- the formats of generated files (`index.json`, `report.json`, `diff.json`) and of the baseline
  (`.store/baseline.json`) may change; when a baseline format changes, record the baseline again;
- task names may change.

Breaking changes are listed in the [Change Log](changelog.md).

## Feature maturity

| Feature | Status |
|---|---|
| Annotations, KSP discovery, runtime API | Experimental |
| Rendering (Layoutlib + Robolectric) | Experimental |
| Export, validation, metadata | Experimental |
| Baselines, diff, reports, CI | Experimental |
| Google Play publishing | Verified in production against Google Play Console (listing & binary) |
| App Store Connect publishing | Experimental, **not yet tested against the real App Store Connect** |
| IDE plugin | Experimental |

Store rules (sizes, counts, text limits) follow the stores' published requirements at the time of
each release. If a store changes its rules before Store Studio is updated, adjust the affected rule
with `validation.severityOverrides` (soft rules) and please open an issue.
