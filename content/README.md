# Starfolk content (v1)

Licensed CC BY-NC-ND 4.0 (see [LICENSE](LICENSE) and [../NOTICE](../NOTICE)). Format and rules: LLD §3 in
`product/05-architecture/LLD.md`. Writing rules: PRD §5 (evidence-based, warm, never clinical, never "should").

| Path | What |
|---|---|
| `v1/quests/<realm>.json` | Quests per realm (connect, adventure, purpose, care) |
| `v1/shelter/<track>.json` | Shelter tracks: breakup (21 days), work (14), health (14) |
| `v1/sparks.json`, `v1/insights.json` | Daily Sparks; "why this works" cards |
| `v1/helplines.json` | Crisis lines for the 12 launch countries; `verified_on` must be ≤ 90 days old at release |
| `v1/sos.json` | "Reach someone" templates and the 5-4-3-2-1 grounding steps |
| `v1/banned-phrases.txt` | Whole phrases the validator rejects (claims, shame, method terms) |
| `v1/meta.json` | Content version and pack versions (no timestamps, so packing is reproducible) |
| `ids.lock` | Every id ever shipped. Append-only: ids are never reused or removed |

## Workflow
1. Draft (AI-assisted, with real citations in `evidence`), then edit for voice (D-015).
2. `./gradlew :tools:content:run --args="lock content/v1"` to record new ids.
3. `./gradlew :tools:content:run --args="validate content/v1"` (draft) — CI runs it too.
4. Shelter and SOS items need `reviewed_by` / `reviewed_on` from the psychologist before release (US-026).
5. Release: `./gradlew :tools:content:validateContent -Pstarfolk.release=true` must pass (no gaps, all reviewed,
   helplines verified, all 12 countries).
6. `./gradlew :tools:content:run --args="report content/v1"` shows coverage per realm × level and per track.
