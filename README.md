# Starfolk

> Do one human thing a day. A free, private Android app: one small, evidence-based real-world quest a day, a hand-made
> sky that grows with every quest, and a quiet Shelter for breakups, work storms and health scares.

- **One small quest a day**: a real-world thing to do (reach out to a friend, take a short walk somewhere new), sized to your energy.
- **Your sky**: every quest you finish adds a star you can name.
- **The Shelter**: short daily walks through a breakup, a work storm or a health scare.
- **Help on every screen**: a calming exercise, messages to send to someone you trust, and crisis lines for your country.
- **Private**: no account, no ads. What you write stays on your phone. The F-Droid build has no internet permission at all.

## Get it

| Where | Status |
|---|---|
| F-Droid | In review |
| Google Play | Coming later |

Android 8.0 or later.

## Please read: safety and limits

- Starfolk is for wellbeing and self-help. It is **not a medical device**, and it doesn't diagnose, treat, cure or prevent any medical condition. It is not a substitute for a doctor, therapist or other professional.
- **The Shelter and SOS texts haven't been reviewed by a mental-health professional yet.** Read them as general self-help, not professional advice.
- **Crisis-line numbers can be wrong or out of date.** They were checked against public sources (the date is shown in the app), but numbers and hours change. Always double-check with a trusted local source. In an emergency, call your local emergency number.
- **Use Starfolk at your own risk.** It is provided "as is", without warranty (see sections 15 and 16 of the [licence](LICENSE)).

Privacy policy: [ukpratik.github.io/starfolk-site/privacy](https://ukpratik.github.io/starfolk-site/privacy/) (also in the app under Settings › About).

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

Content is authored in [`content/`](content/README.md) and packed into the app at build time. Crisis lines are in
[`content/v1/helplines.json`](content/v1/helplines.json): corrections are very welcome. Please open an issue with a link to the line's official source.

## Licence
Code: GPL-3.0-or-later ([LICENSE](LICENSE)). Written content: CC BY-NC-ND 4.0 ([content/LICENSE](content/LICENSE)).
See [NOTICE](NOTICE).
