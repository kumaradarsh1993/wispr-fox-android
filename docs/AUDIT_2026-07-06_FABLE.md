# wispr-fox-android — Fable audit & upgrade plan (2026-07-06)

> Written by Claude Fable 5 after a full code + UX audit of the repo at
> `v1.2.0-codex.2` (HEAD `87a0929`), cross-referenced against the desktop
> sibling `D:\Claude Code Projects\wispr-fox` (stable v2.0.0 / nightly
> v2.1.0-nightly.1). **If tokens run out mid-implementation, an Opus model can
> resume from this file alone.** Read HANDOVER.md first for repo orientation.
>
> Owner asks (verbatim intent):
> 1. Truly solve the CORE reliability bugs first: (a) the floating avatar
>    "sticking around even when there is no text box in sight", (b) auto-paste
>    "sometimes works, other times doesn't".
> 2. Port key desktop features: Deepgram STT (already present — verify),
>    updated valid model lists per provider, usage-limit highlighters,
>    avatars (black clippy ✅ exists, Oru & Gujia cats, Apple-like Siri
>    button — same assets as desktop), avatar scale (S/M/L).
> 3. Builds on GitHub Actions ONLY (no local release builds), and they must
>    go green.

---

## Part 1 — Root-cause analysis of the core bugs

### RC-0 (META, fixes half of everything): CI signs every build with a THROWAWAY key

`.github/workflows/android-apk-release.yml:56-75` — when the four
`ANDROID_SIGNING_*` secrets are absent, CI **generates a brand-new random
keystore per run**. `gh secret list` on 2026-07-06 confirms the repo has **zero
secrets**. Consequences, per update:

- Signature mismatch → Android refuses in-place update → user must
  **uninstall + reinstall**.
- Uninstall wipes app data AND revokes the **Accessibility service** and
  resets special app grants. History, settings, API keys — gone every update.
- After reinstall, until the user manually re-enables Accessibility:
  - `WisprFoxAccessibilityService.isConnected()` == false →
    `OverlayService.observeVisibility()` (OverlayService.kt:74-90) falls back
    to **always-visible** → *"fox sticks around with no text box in sight"*.
  - `DeliveryManager.deliver()` (DeliveryManager.kt:30-42) can never
    auto-paste → *"paste sometimes doesn't work"* (clipboard-only, silently).

**Fix (do FIRST, before any code):** generate ONE persistent keystore, set the
4 repo secrets (`ANDROID_SIGNING_KEY_B64`, `ANDROID_SIGNING_STORE_PASSWORD`,
`ANDROID_SIGNING_KEY_ALIAS`, `ANDROID_SIGNING_KEY_PASSWORD`) via
`gh secret set -R kumaradarsh1993/wispr-fox-android`. Keep the keystore +
passwords backed up locally OUTSIDE the repo (e.g.
`C:\Users\kadar\wispr-fox-android-signing\`) and note the location in
HANDOVER.md. One final uninstall/reinstall will be needed on the S23 Ultra to
move onto the stable key — say so in the release notes.

### RC-1: Overlay visibility logic is fragile ("fox sticks around")

File: `overlay/OverlayService.kt`, `delivery/WisprFoxAccessibilityService.kt`.

1. **Always-visible fallback when a11y is off** (OverlayService.kt:83
   `busy || (if (a11yOn) snap.keyboardVisible else true)`). Any time the
   accessibility service is not connected — never enabled, killed by OneUI
   battery management, or revoked by reinstall (RC-0) — the fox is pinned to
   the screen permanently. The user reads this as a bug, not a fallback.
   **Fix:** when a11y is off, show the fox only while `busy` OR within a short
   grace window after user interaction; surface an in-app banner "Enable
   accessibility for appear-with-keyboard + auto-paste" instead of the
   always-on fallback. (Home screen already deep-links permissions; add a
   passive nudge.)
2. **Keyboard detection is event-starved and coarse**
   (WisprFoxAccessibilityService.kt:45-54). `keyboardVisible` is recomputed
   only when one of `typeViewFocused|typeWindowStateChanged|typeWindowsChanged`
   fires; a missed/queued event leaves `AppState.keyboardVisible` stale-true →
   fox lingers. Also "IME window present in `windows`" ≠ "keyboard visible":
   Samsung keyboard can keep a zero/minimal-height IME window listed.
   **Fix:** (a) also check the IME window's `getBoundsInScreen()` height >
   ~100px; (b) re-evaluate on `TYPE_WINDOWS_CHANGED` **and** add a cheap
   debounce re-check ~600ms after each event (a coroutine in the service) so
   a missed transition self-heals; (c) on `onServiceConnected` and
   `onUnbind`, reset `AppState.setKeyboardVisible(false)` so stale state
   can't outlive the service.
3. **The busy flag pins the fox for the whole WorkManager retry ladder.**
   `TranscribeWorker` (queue/TranscribeWorker.kt:131-138) retries up to 5
   times with exponential backoff; `AppState.pipeline` stays
   TRANSCRIBING/CLEANING the entire time → fox visible (and taps ignored —
   `RecordingController.toggle()` ignores non-RECORDING busy states,
   RecordingController.kt:44) potentially for many minutes on flaky network.
   **Fix:** when the worker returns `Result.retry()`, set pipeline to IDLE
   with a toast "Network hiccup — will retry in background; check History".
   Deliver-on-retry then goes clipboard + a **notification** ("Transcript
   ready — copied"), never a surprise paste. The fox must never be held
   hostage by a background retry, and the user must be able to start a new
   dictation immediately.
4. **Overlay only starts from MainActivity.onResume** (MainActivity.kt:83-94).
   After reboot or process death the fox is gone until the app is opened.
   **Fix:** also `startService` from `WisprFoxAccessibilityService.
   onServiceConnected()` (the enabled a11y service keeps our process alive and
   is the natural companion signal), guarded by `Settings.canDrawOverlays` +
   `overlayBubbleEnabled`. Optional: `BOOT_COMPLETED` receiver, same guards.

### RC-2: Auto-paste single-shot + `rootInActiveWindow` flakiness

File: `delivery/WisprFoxAccessibilityService.kt:56-60`, `delivery/DeliveryManager.kt:30-42`.

1. **`rootInActiveWindow` is the wrong lens.** At delivery time the "active"
   window is frequently the IME, our own overlay, or momentarily null →
   `focusedEditable()` returns null → auto-paste silently degrades to
   clipboard. This is THE classic Android a11y paste flake.
   **Fix:** use the service-level `findFocus(AccessibilityNodeInfo.FOCUS_INPUT)`
   (searches across all interactive windows), fall back to iterating
   `windows` → `window.root?.findFocus(FOCUS_INPUT)`; call `refresh()` on the
   node before acting.
2. **Single-shot, zero retry.** The paste attempt happens at one instant. If
   focus is transitioning (user just switched back to the chat), it misses.
   **Fix:** retry loop — up to ~4 attempts over ~1.5s (e.g. 0/300/600/900ms)
   before declaring clipboard fallback.
3. **`targetPackage` guard uses the same flaky read at BOTH ends**
   (RecordingController.kt:77 capture; DeliveryManager.kt:33 check). A null
   read at either end silently disables or falsely trips the guard.
   **Fix:** capture via the robust cross-window lookup too; when delivery-time
   focus is null but was non-null at start, allow the retry loop to resolve it
   before giving up. Keep the cross-app mismatch → clipboard rule (it's
   correct and desktop-aligned).
4. **Silent degradation.** When auto-paste fails the only signal is the bubble
   text "Copied — paste anywhere" (invisible if the fox is hidden/overlay off).
   **Fix:** when delivery falls back to clipboard AND the app is not
   foreground, post a normal-priority notification "Transcript copied — tap to
   view" (opens History). Never fail silently.
5. Nice-to-have: `pasteInternal`'s non-empty path (WisprFoxAccessibilityService.kt:91-105)
   uses ACTION_PASTE at cursor — good — but confirm cursor-position behaviour
   in Compose text fields on real device QA.

### RC-3: LLM model list contains likely-invalid IDs → "cleanup randomly does nothing"

`CleanupOrchestrator.clean()` swallows every LLM failure and returns the raw
transcript with a note (CleanupOrchestrator.kt:65-67 — correct desktop
contract). Side effect: an **invalid model id fails 4xx on every call** and the
user just sees uncleaned text — reads as "Clean mode is flaky".

- `ProviderCatalog.kt:59` ships Groq id `llama-4-maverick`. Groq's real id is
  `meta-llama/llama-4-maverick-17b-128e-instruct` (desktop has the same bug in
  `provider-options.ts:57`). **Verify live against
  https://console.groq.com/docs/models and fix on BOTH platforms** (desktop
  fix = separate follow-up; Android in this batch).
- ElevenLabs `scribe_v2` (ProviderCatalog.kt:50): verify against ElevenLabs
  docs; keep `scribe_v1` fallback.
- Gemini list is stale vs desktop: desktop default is now `gemini-3.5-flash`,
  plus `gemini-3-flash-preview`, `gemini-3.1-pro-preview` options
  (desktop `provider-options.ts:64-71`). Port the desktop list verbatim after
  live-verifying ids via https://ai.google.dev/gemini-api/docs/models.
- OpenAI lists (STT `gpt-4o-transcribe` family, LLM `gpt-5.4/5.5` family)
  match desktop; verify quickly, don't churn.
- History rows store `sttProvider`/model names — no migration needed; the
  catalog `sanitize*` functions already coerce stale saved values.

### RC-4: Release/CI hygiene

- `android-apk-release.yml:96-109` hardcodes release name "… - Codex build"
  and a fixed Codex body. **Fix:** derive the name suffix from the tag or a
  workflow input; for this batch use "Fable build"; read the body from
  `docs/RELEASE_NOTES_<tag>.md` when present (desktop convention), fall back
  to a generic body.
- `versionCode`/`versionName` are hardcoded (app/build.gradle.kts:28-29).
  Bump both every tag (versionCode must strictly increase for in-place
  updates). Optional (fox-cull lesson): stamp versionName from the git tag in
  CI.
- Unit tests run in CI (`testDebugUnitTest`) — keep. Local machine: unit
  tests OK, **no local `assembleRelease`** (owner rule: builds happen on
  GitHub).

### Minor code notes (fix opportunistically, don't force)

- `RecordingService` notification uses system icons
  (`android.R.drawable.ic_btn_speak_now`, RecordingService.kt:201) — use the
  fox asset.
- `EXTRA_MODE` is passed to RecordingService but never read (dead code).
- `AvatarOverlay` local 1s ticker + 30s heartbeat is fine; leave.
- `keys.properties`/`local.properties` are gitignored and untracked ✅.
- Room schemas 1+2 are tracked for CI ✅.

---

## Part 2 — Desktop → Android ports (what "parity" means here)

Desktop reference files:
- Models: `wispr-fox/src/lib/provider-options.ts`
- Usage meters: `wispr-fox/src/routes/+layout.svelte:174-264`,
  `src/lib/usage-store.svelte.ts` (buckets: per-day per provider+model; STT =
  calls + audio_seconds, LLM = calls + tokens; Deepgram = lifetime estimated
  spend at $0.0092/min Nova-3 multilingual vs $200 free credit)
- Avatars: `wispr-fox/static/avatars/{oru-gujia,spark-buddy,codex-fox}/`
  (manifest-v2 raster packs, 8 states + thumbnail); Siri orb is pure CSS in
  `src/routes/clippy/+page.svelte` (`.siri-orb`, ~line 2115+)
- Scale: `src/lib/floater-scale.svelte.ts` (S/M/L presets)

### P-1: Model catalog sync (ProviderCatalog.kt)

Copy the desktop lists (with the RC-3 live-verification pass). Keep
`sanitizeSttModel`/`sanitizeLlmModel` coercion so stale prefs self-heal. Add
Gemini 3.5 Flash as the new Gemini default. Do NOT add new providers — the
four STT + three LLM already match desktop.

### P-2: Usage tracking + limit highlighters

Android has **zero usage tracking** today. Implement:

- New Room entity `UsageBucketEntity` (day `yyyy-MM-dd` UTC, stage stt|llm,
  provider, model, calls, audioSeconds, inputTokens, outputTokens,
  totalTokens) + DAO + migration 2→3 (**export schema 3.json; CI checks
  schemas exist — update the workflow's schema check to include 3.json**).
- Record: STT success → calls+1, audioSeconds += recording duration
  (duration is already stored on the Room row). LLM success → calls+1 and
  token counts when the provider response includes usage (Groq/OpenAI do;
  Gemini usageMetadata). Deepgram: maintain a **lifetime** cumulative
  audio-seconds counter (DataStore) → estimated spend = minutes × $0.0092
  against $200 credit.
- UI: a compact usage strip on Home (today's STT minutes / LLM tokens for the
  active provider+model) with the desktop's ok/warn/danger coloring at
  <50/<85/≥85% of the Groq free-tier caps (2000 STT calls/day, 3600s
  audio/day proxy, 200k tokens/day) — bars ONLY for groq + deepgram
  (deepgram shows $spend/$200 lifetime), number-only for other providers
  (desktop rule, +layout.svelte:211-217). Include "resets at HH:MM (Xh Ym)"
  converting UTC midnight to local (IST) — port `nextUtcMidnightLocal()`.
  Mirror the same detail in Settings → per-provider section.

### P-3: Avatars (same assets as desktop) + scale

Current Android avatars: FOX (watercolor PNGs, drawable-nodpi) and CLIPPY
(Compose-drawn black paperclip — this IS the "black clippy" the owner wants;
keep it). Add:

- **ORU_GUJIA** — copy the 8 state PNGs + thumbnail from
  `wispr-fox/static/avatars/oru-gujia/` into `drawable-nodpi` (prefix
  `oru_gujia_*`). State mapping (desktop manifest → PipelineState):
  idle→IDLE, listening→RECORDING, thinking→TRANSCRIBING, writing→CLEANING,
  pasting→INJECTING, excited→DONE, error→ERROR (sleeping unused for now).
- **SIRI** ("Apple-like button") — Compose implementation: circular orb,
  layered animated sweep gradients (`Brush.sweepGradient` + infinite rotation,
  colors sampled from desktop CSS vars `--sc1/--sc2/--sc3` in
  clippy/+page.svelte), state-driven spin speed (listening fast, thinking
  medium, idle slow), green pulse on DONE, red blink on ERROR. No bubble
  suppression complexity needed on Android (bubble already compact).
- Extend `enum Avatar { FOX, CLIPPY, ORU_GUJIA, SIRI }` (Avatar.kt) —
  `SettingsStore` already `valueOf`-parses with fallback, so old prefs are
  safe. Update the avatar picker UI (SettingsScreen/HomeScreen) with
  thumbnails.
- **Scale S/M/L**: new setting `avatarScale` (S=0.8, M=1.0, L=1.25 of the
  current 70dp). Apply in AvatarOverlay (FOX_SIZE/FOX_IMG multiplier) and
  persist via SettingsStore. Picker chips next to the avatar picker.

### P-4: CI/release polish

Per RC-4: parametrized release title ("Fable build"), release-notes-file
body, versionCode/Name bump, schema-check update to include 3.json.

---

## Part 3 — Execution plan (phased, CI-gated)

Version target: **v1.3.0-nightly.1**, tagged and built ONLY on GitHub Actions.
Release title must show it's a Fable build (mirror of the Codex convention).

**Phase 0 — signing (owner-visible infra, do first):**
generate persistent keystore (JDK 17 keytool at `D:\android-dev`), back up to
`C:\Users\kadar\wispr-fox-android-signing\` (never in the repo), set the 4
GitHub secrets via gh CLI. Document in HANDOVER.md.

**Phase 1 — core reliability (Opus subagent A):** RC-1 + RC-2 + retry-ladder
decoupling. Unit tests for: keyboard-visibility heuristic (bounds threshold),
delivery retry/fallback decision table, worker retry → IDLE+notification
path. No UI redesign.

**Phase 2 — parity ports (Opus subagent B, after A merges):** P-1 (with live
model-id verification via web), P-2, P-3, P-4. Unit tests for usage bucket
math + percent/threshold coloring + catalog sanitizers.

**Phase 3 — ship:** review both diffs, run `./gradlew.bat testDebugUnitTest`
locally (allowed; NOT assembleRelease), update HANDOVER.md + CLAUDE.md current-
state banner + `docs/RELEASE_NOTES_v1.3.0-nightly.1.md` (user-friendly prose,
must mention the one-time uninstall/reinstall for the new signing key), bump
versionCode=13 / versionName=1.3.0-nightly.1, commit, tag, push, **watch the
Actions run to green**, verify the APK asset on the release.

**Real-device QA checklist for the owner (S23 Ultra)** — carry over the
HANDOVER list, plus: (1) install nightly.1 fresh (one-time), enable a11y +
overlay; (2) install a subsequent build WITHOUT uninstall — must update
in-place, a11y stays on; (3) fox appears with keyboard / disappears on
dismiss across WhatsApp, Chrome, Notes; (4) paste lands in WhatsApp reply
field with existing draft text preserved; (5) airplane-mode mid-transcribe →
fox frees up immediately, notification arrives when back online; (6) Oru &
Gujia + Siri orb avatars cycle states; (7) S/M/L scale.

---

## Subagent directives (quality bar)

Common to both agents:
- Work in `D:\Claude Code Projects\wispr-fox-android`. Kotlin + Compose,
  match existing style (KDoc headers explaining WHY, desktop-contract
  references). Never touch `pa-fox_archive` or the desktop repo (read-only
  reference).
- NO local `assembleRelease`/`bundleRelease`. `testDebugUnitTest` is the only
  local gate. Do not commit — leave the working tree for review.
- Preserve locked decisions: language=auto (never pin en), prompt-injection
  drift tripwire is a security boundary, no IME, no analytics, BYOK, no
  always-listening.
- Every behavioural claim about Android APIs (a11y windows, clipboard,
  foreground-service rules) must hold for API 31–35; when uncertain, prefer
  the defensive path and leave a comment.
- New user-facing copy: short, friendly, no jargon ("Enable accessibility so
  words land in the box automatically").

Agent A scope guard: only `overlay/`, `delivery/`, `core/`, `queue/`,
`audio/RecordingService.kt`, manifest/a11y config, + tests. Agent B scope
guard: `provider/ProviderCatalog.kt`, `settings/`, `history/` (usage entity +
migration), `ui/`, `overlay/Avatar*`, new drawables, `.github/workflows/`,
`app/build.gradle.kts`, + tests.
