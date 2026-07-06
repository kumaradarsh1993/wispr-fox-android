# HANDOVER - wispr-fox-android

> Last update: 2026-07-07. Current nightly: **v1.3.0-nightly.1** (Fable line). Previous Codex preview: v1.2.0-codex.2. Last stable: **v1.1.0** at commit `4cf396b`.

This file is the current state-of-the-world for Android. `CLAUDE.md` is useful historical context, but this handover wins when they disagree. The full audit + rationale behind the v1.3.0 batch lives in `docs/AUDIT_2026-07-06_FABLE.md` — read it before touching overlay/delivery/pipeline code.

## What changed in v1.3.0-nightly.1 (Fable batch)

**Core reliability (the "hits and misses" fixes — RC-1/RC-2 in the audit):**
- Auto-paste no longer depends on `rootInActiveWindow`: focus is found via the service-level cross-window `findFocus(FOCUS_INPUT)` with node `refresh()`, retried 4x over ~1.5s (`delivery/DeliveryManager.kt`, `DeliveryDecision.kt`).
- Clipboard-only fallback is never silent: if the app isn't foreground, a "Transcript ready" notification posts and deep-links to History (`MainActivity.EXTRA_OPEN`).
- Overlay visibility: pure decision logic in `overlay/OverlayVisibility.kt`. Accessibility OFF no longer pins the fox always-visible (it hides, and Home shows a banner explaining why); keyboard detection requires the IME window to have real on-screen height (>100px, `delivery/KeyboardHeuristics.kt`) and self-heals with a 600ms debounced re-check.
- The WorkManager retry ladder no longer holds the fox/pipeline hostage: on retry the avatar frees to IDLE ("Network hiccup — will retry in background"), the user can dictate again immediately, and a retried delivery is clipboard-only + notification (never a surprise paste). All live-state writes are ownership-guarded by `activeRecordingId`.
- The a11y service now also starts the OverlayService on connect, so the fox survives process death/reboot once accessibility is on.

**Desktop parity ports (P-1…P-4 in the audit):**
- Model catalog synced + live-verified 2026-07-07: removed Groq `distil-whisper-large-v3-en` and invalid `llama-4-maverick` (→ `meta-llama/llama-4-scout-17b-16e-instruct`); ElevenLabs is `scribe_v2` only (v1 removed upstream 2026-07-09); Gemini default is `gemini-3.5-flash` + current 2.5 family + `gemini-3.1-pro-preview`. Stale saved selections self-coerce via the catalog sanitizers.
- Usage tracking: Room v3 `usage_buckets` table (migration 2→3, schema `3.json` exported and checked by CI), tallied in `RecordingRepository` on STT/LLM success; Deepgram lifetime spend estimate ($0.0092/min vs $200 credit). Home + Settings show meters with ok/warn/danger bands vs Groq free-tier caps and a local-time "resets at" label. LLM token counts are TODO (calls tallied; token plumbing needs LlmProvider.complete() to return usage — see `RecordingRepository.tallyLlm` KDoc).
- Avatars: `Avatar { FOX, CLIPPY, ORU_GUJIA, SIRI }` — Oru & Gujia use the desktop raster pack (copied to `drawable-nodpi/oru_gujia_*`), Siri is a Compose-drawn animated orb (`overlay/SiriOrbAvatar.kt`). Avatar size S/M/L (`settings/AvatarScale.kt`).
- CI: release title says "Fable build"; body prefers `docs/RELEASE_NOTES_<tag>.md`; schema check includes 3.json.

## Release signing — PERSISTENT KEY SINCE v1.3.0-nightly.1

The four `ANDROID_SIGNING_*` repo secrets are SET (2026-07-07), so CI signs every build with the permanent key → in-place APK updates work and accessibility survives updates. Keystore + credentials backup: `C:\Users\kadar\wispr-fox-android-signing\` (never commit). Installing v1.3.0-nightly.1 over an older throwaway-key APK requires ONE last uninstall/reinstall; after that, never again.

## Verification done

- `./gradlew.bat testDebugUnitTest` green locally (6 suites incl. new OverlayVisibility / KeyboardHeuristics / DeliveryDecision / ProviderCatalog / UsageMath tests).
- Release APK builds ONLY on GitHub Actions (owner rule — do not run assembleRelease locally).

## Still needs real-device QA (S23 Ultra)

- One-time uninstall → install nightly.1; enable a11y + overlay; then verify a LATER build updates in-place with a11y intact.
- Fox appears with keyboard / hides on dismiss across WhatsApp, Chrome, Samsung Notes; no lingering fox with a11y on OR off.
- Paste lands in a field with existing draft text preserved; app-switch mid-transcription → notification + clipboard, no cross-app paste.
- Airplane mode mid-transcribe → fox frees immediately, can dictate again, notification when back online.
- New avatars cycle states; S/M/L scale; usage meters move after a dictation; Deepgram credit line when Deepgram selected.
- Accessibility-off banner on Home appears/disappears correctly.

## Resume pointers

- Audit/spec: `docs/AUDIT_2026-07-06_FABLE.md`
- Overlay visibility rule: `overlay/OverlayVisibility.kt` (+ test)
- Paste path: `delivery/DeliveryManager.kt`, `delivery/DeliveryDecision.kt`, `delivery/WisprFoxAccessibilityService.kt`
- Pipeline/retry: `queue/TranscribeWorker.kt` (ownership-guarded live state)
- Usage: `history/UsageBucketEntity.kt`, `history/UsageRepository.kt`, `history/UsageMath.kt`, `ui/UsageMeter.kt`
- Catalog: `provider/ProviderCatalog.kt` (live-verified IDs; keep in sync with desktop `wispr-fox/src/lib/provider-options.ts`)
- Release workflow: `.github/workflows/android-apk-release.yml`
