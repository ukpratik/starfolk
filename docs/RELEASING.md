# Releasing Starfolk

Decisions: ADR-011 (distribution, signing, versioning), ADR-013 (gates), ADR-015 (licences), D-028, D-030.
Checklist: [product/08-release/launch-checklist.md](../product/08-release/launch-checklist.md).

## 1. Signing key (once; Play upload key)
```sh
keytool -genkeypair -v -keystore ~/.android-keys/starfolk-upload.jks -alias starfolk-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```
Add to `~/.gradle/gradle.properties` (never the repo):
```
starfolk.upload.storeFile=/Users/<you>/.android-keys/starfolk-upload.jks
starfolk.upload.storePassword=…
starfolk.upload.keyAlias=starfolk-upload
starfolk.upload.keyPassword=…
# optional, enables opt-in analytics in the play build (ADR-006)
starfolk.aptabase.key=A-EU-…
```
Back up the `.jks` and passwords offline (password manager + one offline copy). F-Droid signs its own builds.

## 2. Content gates (must pass)
```sh
./gradlew :tools:content:run --args="review-pack content/v1 product/08-release/expert-review-pack.md"
./gradlew :tools:content:run --args="sign-review content/v1 --by=\"Name, credential\" --on=YYYY-MM-DD all"
./gradlew :tools:content:run --args="verify-helpline content/v1 GB --on=YYYY-MM-DD"   # each of the 12 countries
./gradlew :tools:content:validateContent -Pstarfolk.release=true
```

## 3. Version, profile, build
- `versionName` SemVer; `versionCode = MAJOR×10000 + MINOR×100 + PATCH` (1.0.0 → 10000). Changelog in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
- Regenerate the baseline profile on the release commit (emulator or device attached): `./gradlew :app:generateBaselineProfile`.
- `scripts/ci.sh` green, plus `./gradlew :app:connectedPlayDebugAndroidTest :app:connectedFdroidDebugAndroidTest` (no-network test; needs airplane mode **off**).
- Tag `vX.Y.Z` on a clean commit. Build: `./gradlew :app:bundlePlayRelease :app:assembleFdroidRelease`.

## 4. Archive (never in git)
From the repo root, per `~/Desktop/PersonalProjects/builds/README.md`:
```sh
~/Desktop/PersonalProjects/builds/archive-build.sh starfolk 1.0.0 \
  app/build/outputs/bundle/playRelease/app-play-release.aab=starfolk-1.0.0-play.aab \
  app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk=starfolk-1.0.0-fdroid-unsigned.apk \
  app/build/outputs/mapping/playRelease/mapping.txt=mapping/starfolk-1.0.0-play-mapping.txt \
  app/build/outputs/mapping/fdroidRelease/mapping.txt=mapping/starfolk-1.0.0-fdroid-mapping.txt
```
Then add `RELEASE.md` in that folder and update the Apps table in `builds/README.md`.

## 5. Stores
- **Play:** upload the AAB to a closed test (12+ testers × 14 days for a new personal account), Data safety / Health apps / content rating per `product/08-release/data-safety.md`, then production.
- **F-Droid:** make the repo public, then submit `fdroid/io.github.ukpratik.starfolk.yml` to fdroiddata (expect the NonFreeAssets anti-feature).
