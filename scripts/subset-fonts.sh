#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Rebuilds app/src/main/res/font/*.ttf (design-system §3, §8): static Latin subsets of the google/fonts originals
# (all SIL OFL 1.1). Needs fonttools (pip install fonttools). Fonts are bundled; the app never downloads one (US-020).
# Usage: scripts/subset-fonts.sh <dir with BricolageGrotesque[opsz,wdth,wght].ttf, CourierPrime-{Regular,Bold}.ttf, Caveat[wght].ttf>
set -euo pipefail
SRC=${1:?usage: $0 <source dir>}
FT=${FONTTOOLS:-fonttools}
OUT="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/res/font"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
# Basic Latin, Latin-1, and the punctuation the copy uses (– — ‘ ’ “ ” … • → ← ↗ ↓ ⇄ ✓ ✦ ★ ♡ × −).
U="U+0020-007E,U+00A0-00FF,U+2013,U+2014,U+2018,U+2019,U+201C,U+201D,U+2026,U+2022,U+2190,U+2192,U+2193,U+2197,U+21C4,U+2713,U+2726,U+2605,U+2661,U+00D7,U+2212"
for w in 500 800; do
  "$FT" varLib.instancer "$SRC/BricolageGrotesque[opsz,wdth,wght].ttf" wght=$w opsz=24 wdth=100 -o "$TMP/bg$w.ttf" -q
  "$FT" subset "$TMP/bg$w.ttf" --unicodes="$U" --layout-features='kern,liga' --name-IDs='*' --output-file="$OUT/bricolage_$w.ttf"
done
"$FT" subset "$SRC/CourierPrime-Regular.ttf" --unicodes="$U" --layout-features='kern' --name-IDs='*' --output-file="$OUT/courier_prime_400.ttf"
"$FT" subset "$SRC/CourierPrime-Bold.ttf" --unicodes="$U" --layout-features='kern' --name-IDs='*' --output-file="$OUT/courier_prime_700.ttf"
for w in 500 700; do
  "$FT" varLib.instancer "$SRC/Caveat[wght].ttf" wght=$w -o "$TMP/cv$w.ttf" -q
  "$FT" subset "$TMP/cv$w.ttf" --unicodes="$U" --layout-features='kern,liga,calt' --name-IDs='*' --output-file="$OUT/caveat_$w.ttf"
done
ls -l "$OUT"
