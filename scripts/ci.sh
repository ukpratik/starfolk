#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Every CI gate (ADR-013, LLD §8). GitHub Actions runs exactly this script; run it locally before pushing.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${ANDROID_HOME:-}" && ! -f local.properties && -d "$HOME/Library/Android/sdk" ]]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
fi

GRADLE=(./gradlew --stacktrace)
step() { printf '\n==> %s\n' "$*"; }

step "1/7 ids.lock is append-only (content ids are never reused, LLD §3.2)"
if git rev-parse --verify -q HEAD~1 >/dev/null && git diff HEAD~1 -- content/ids.lock | grep -q '^-[a-z]'; then
  echo "FAIL: lines were removed from content/ids.lock" >&2; exit 1
fi
echo "ok"

step "2/7 Unit tests (:core, :tools:content, :app Robolectric)"
"${GRADLE[@]}" test

step "3/7 Content validator (draft rules; pass -Pstarfolk.release=true for a release)"
"${GRADLE[@]}" :tools:content:validateContent

step "4/7 Android Lint, both flavours"
"${GRADLE[@]}" lintPlayDebug lintFdroidDebug

step "5/7 Release builds, both flavours (R8) + AboutLibraries licence gate"
"${GRADLE[@]}" assemblePlayRelease assembleFdroidRelease bundlePlayRelease

step "6/7 Permission gate, per flavour (fdroid: no network, no Play classes)"
scripts/check-permissions.sh play app/build/outputs/apk/play/release/app-play-release*.apk
scripts/check-permissions.sh fdroid app/build/outputs/apk/fdroid/release/app-fdroid-release*.apk

step "7/7 Size gate: Play download ≤ 30 MB (NFR-003)"
scripts/check-size.sh app/build/outputs/bundle/playRelease/app-play-release.aab 30000000

printf '\nAll gates passed.\n'
