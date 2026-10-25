#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Privacy gate (ADR-013): fails if a release APK's merged manifest requests a permission outside the allowlist for its
# flavour, or contains the advertising ID. Usage: scripts/check-permissions.sh <play|fdroid> <apk>
set -euo pipefail
FLAVOUR=${1:?usage: $0 <play|fdroid> <apk>}
APK=${2:?usage: $0 <play|fdroid> <apk>}
# Shared: WorkManager (wake lock, foreground service, boot), notifications, haptics.
COMMON="android.permission.POST_NOTIFICATIONS android.permission.RECEIVE_BOOT_COMPLETED android.permission.WAKE_LOCK android.permission.FOREGROUND_SERVICE android.permission.VIBRATE"
case "$FLAVOUR" in
  play) ALLOWED="$COMMON android.permission.INTERNET android.permission.ACCESS_NETWORK_STATE com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE" ;;
  fdroid) ALLOWED="$COMMON" ;;  # D-028: the F-Droid build has no network permission at all
  *) echo "unknown flavour: $FLAVOUR" >&2; exit 2 ;;
esac
# Added by AndroidX core to guard unexported receivers; a signature-level, app-private permission.
ALLOWED_SUFFIX=".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
AAPT2=$(ls -d "$SDK"/build-tools/*/aapt2 | sort -V | tail -1)
[[ -f "$APK" ]] || { echo "FAIL: no such APK: $APK" >&2; exit 2; }
echo "Checking $APK ($FLAVOUR)"
status=0
while read -r perm; do
  [[ -z "$perm" ]] && continue
  if [[ "$perm" == "com.google.android.gms.permission.AD_ID" ]]; then
    echo "  BLOCK $perm (advertising ID is never allowed)"; status=1
  elif [[ " $ALLOWED " == *" $perm "* || "$perm" == *"$ALLOWED_SUFFIX" ]]; then
    echo "  ok    $perm"
  else
    echo "  BLOCK $perm"; status=1
  fi
done < <("$AAPT2" dump permissions "$APK" | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p")

# Required, not just allowed: the play build needs INTERNET for opt-in analytics (ADR-006); fdroid must not have it (D-028).
has_internet=$("$AAPT2" dump permissions "$APK" | grep -c "name='android.permission.INTERNET'" || true)
if [[ "$FLAVOUR" == "play" && "$has_internet" -eq 0 ]]; then echo "  BLOCK play build is missing android.permission.INTERNET"; status=1; fi
if [[ "$FLAVOUR" == "fdroid" && "$has_internet" -gt 0 ]]; then echo "  BLOCK fdroid build has android.permission.INTERNET"; status=1; fi

if [[ "$FLAVOUR" == "fdroid" ]]; then
  # D-028: no proprietary Google Play classes in the F-Droid build.
  DEXDUMP=$(ls -d "$SDK"/build-tools/*/dexdump | sort -V | tail -1)
  TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
  unzip -q -o "$APK" 'classes*.dex' -d "$TMP"
  refs=$(for dex in "$TMP"/classes*.dex; do "$DEXDUMP" "$dex"; done | grep -c 'Lcom/google/android/play/' || true)
  if [[ "$refs" -gt 0 ]]; then echo "  BLOCK $refs com/google/android/play references"; status=1; else echo "  ok    no com/google/android/play classes"; fi
fi
exit $status
