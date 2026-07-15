# HANDOVER - wispr-fox-android

> Last update: 2026-07-16. Active nightly: **v2.0.0-nightly.1** (accounts + cross-device sync). Current stable: **v1.4.0** (audio-file import). Last stable before this: v1.1.0 at commit `4cf396b`.

This file is the current state-of-the-world for Android. `CLAUDE.md` is useful historical context, but this handover wins when they disagree. The full audit + rationale behind the v1.3.0 batch lives in `docs/AUDIT_2026-07-06_FABLE.md` — read it before touching overlay/delivery/pipeline code.

## What changed in v2.0.0-nightly.1 (accounts + cross-device sync)

> **⚠ Scope override:** `CLAUDE.md` says "Don't add account signup." The owner **intentionally overrode that on 2026-07-16** — sign-in is now a first-class, *optional* feature. Local-only (signed-out) mode is unchanged; BYOK still works without any account. This handover wins over that older CLAUDE.md rule.

Optional Google (Custom Tab + `wisprfox://auth-callback` deep-link PKCE) or email/password sign-in against a shared **Supabase** backend. Signed-in devices sync *transcripts* + API keys across desktop/web/mobile; **audio never leaves the phone**. Reworked delete (long-press a row or bulk) → dialog: voice files / transcripts × this-device / everywhere (cloud tombstones). Platform badges (Desktop/Web/Mobile) per row.

- **Backend + setup is shared across all three apps** — canonical spec `../wispr-fox-web/docs/SYNC_DESIGN.md`; user setup `../wispr-fox-web/SETUP_ACCOUNTS.md`; **secrets in the gitignored `../wispr-fox-web/SECRETS.local.md`** (Supabase URL/anon key baked into `sync/SupabaseConfig.kt`, Google OAuth client).
- **New `sync/` package:** `SupabaseConfig`, `AuthManager`, `SyncEngine`, `SyncWorker`, `SyncModels`, `SyncTime`. Triggers: post-transcribe choke point in `TranscribeWorker`, `MainActivity.onStart`, a 60s foreground ticker, and a ~15min `SyncWorker`.
- **Room v4→v5** (`MIGRATION_4_5`, `5.json` exported + CI-checked): `recordings` gains `platform`/`device_name`/`dirty`/`remote`/`updated_at`; new `sync_meta` + `sync_exclusions` tables (`SyncMetaEntity`, `SyncExclusionEntity`).
- **UI:** `ui/AccountSection.kt` in Settings (sign in/out, device name, Sync now), a skippable "Sync across your devices" onboarding step (only when `SupabaseConfig.isConfigured()`), `ui/DeleteDialog.kt`, platform badges + remote-row action hiding in `HistoryScreen`.
- Tokens stored in `SecureKeyStore` (AndroidKeyStore-encrypted). No service-role key anywhere — anon key + each user's login JWT only.
- Verified: `./gradlew :app:testDebugUnitTest` BUILD SUCCESSFUL (KSP + Room migration validation); `5.json` exported (9.4 KB). **Real-device QA still owed:** Google OAuth round-trip, cross-device pull, delete-everywhere tombstone propagation.

## What changed in v1.4.0 (audio-file import)

New feature: import existing audio files and run them through the same transcribe → clean/draft pipeline as live dictation. Requested for transcribing longer voice notes and call recordings on an S23 Ultra.

- **Entry point:** an "Import audio file" card on Home opens a bottom sheet (`ui/ImportSheet.kt`) to pick the STT model, output style (Raw/Clean/Draft — single choice, consistent with the rest of the app), and cleanup LLM. Defaults: **Whisper Large v3 / Groq** for STT, **Gemini 3.5 Flash** for cleanup. The button launches SAF `OpenMultipleDocuments("audio/*")`.
- **Per-recording model overrides (new).** The chosen models are stored on the recording row, not global settings, so imports never disturb the live-dictation model. Room **schema v3→v4** (`MIGRATION_3_4`) adds `stt_provider_override`, `stt_model_override`, `llm_provider_override`, `llm_model_override`, `imported`; `4.json` exported and added to the CI schema check. `TranscribeWorker` folds overrides on via `queue/RecordingOverrides.kt` (`AppSettings.withRecordingOverrides`) and forces clipboard-only (no auto-paste) for `imported` rows.
- **Decode path:** `audio/AudioImporter.kt` (MediaExtractor + MediaCodec) decodes any device-supported codec (M4A/AAC, MP3, AMR, 3GP, Ogg/Opus, FLAC, WAV — Samsung + iPhone recordings) to the pipeline's mono 16-bit WAV, streaming to `WavWriter` (low memory, any length), source sample rate kept (WavChunker + Whisper handle it). Pure DSP in `audio/PcmDownmix.kt` with unit tests (`PcmDownmixTest`). `queue/ImportWorker.kt` runs the decode durably then hands off to `TranscribeWorker`; `core/ImportController.kt` wires pick → row → worker. New `RecordingStatus.IMPORTING`.
- **Model due-diligence (2026-07-15):** Groq Whisper Large v3 / v3 Turbo and Gemini 3.5 Flash confirmed current; catalog from 2026-07-07 still accurate, nothing relied-on retired.
- No new manifest permissions (SAF needs none).

**Still needs real-device QA (S23 Ultra):** import a Samsung Voice Recorder note and a call recording; a long (>20 MB decoded) clip to exercise chunking; confirm History shows the imported row progressing importing → transcribing → done and the text lands on the clipboard; try Raw vs Clean (Gemini) vs Draft.

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
