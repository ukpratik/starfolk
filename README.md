# Starfolk

> Do one human thing a day. A free, private Android app: one small, evidence-based real-world quest a day, a hand-made
> sky that grows with every quest, and a quiet Shelter for breakups, work storms and health scares.

Codename HumanIntelligence. Product docs, decisions and designs live in [`product/`](product/README.md).

## Build
Requirements: JDK 21+, Android SDK (platform 37). Everything else comes through the Gradle wrapper.

```sh
./gradlew :app:assemblePlayDebug        # debug build (play flavour)
./gradlew :app:assembleFdroidDebug      # the F-Droid flavour: no network permission, no Play libraries
scripts/ci.sh                           # every CI gate: tests, content validator, lint, release builds, permission + size gates
```

| Module | What |
|---|---|
| `:core` | Pure Kotlin rules: app-day (04:00 rollover), content model and rules, sky layout |
| `:app` | The Android app (Jetpack Compose) |
| `:tools:content` | Content CLI: `validate`, `pack`, `report`, `lock` |
| `:baselineprofile` | Baseline profile, startup and sky frame-timing benchmarks (device only) |

Content is authored in [`content/`](content/README.md) and packed into the app at build time.

## Licence
Code: GPL-3.0-or-later ([LICENSE](LICENSE)). Written content: CC BY-NC-ND 4.0 ([content/LICENSE](content/LICENSE)).
See [NOTICE](NOTICE).

Starfolk is for wellbeing and self-help. It is not a medical device and does not diagnose, treat, cure, or prevent any
medical condition.
