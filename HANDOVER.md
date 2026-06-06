# HANDOVER — wispr-fox-android

> Last update: 2026-06-06 · current stable: **v1.1.0** (commit `4cf396b`, tag `v1.1.0`)

This doc is the single source of truth for "what's the app at right now and what's next." If anything in `CLAUDE.md` or `PRD.md` contradicts this, **this wins** — those older docs were written pre-implementation.

---

## What ships today (v1.1.0)

Stable, installed and verified on **Samsung S23 Ultra (RZCWA1TB26Z)** running Android 14.

### Core flow (works end-to-end)

- **Activation:** draggable floating fox bubble (overlay service) + Quick Settings tile as secondary trigger.
- **Gesture model:** tap = start/stop record; long-press = mode picker (Raw / Clean / Draft).
- **Pipeline:** record → WAV on disk → `TranscribeWorker` (WorkManager, unique-per-id) → Groq Whisper-large-v3-turbo → optional LLM cleanup (Groq Llama or Gemini) → delivery.
- **Delivery:** AccessibilityService auto-paste into the focused field, clipboard as fallback.
- **History:** Room DB, audio WAVs on disk, retention sweep (default 7 days / 500MB rolling cap).

### Screens

- **Onboarding** — 3-step multi-step flow (Welcome → Setup → Grant). Mirrors desktop design. Live permission state via lifecycle observer. Family setup-code featured when `BuildConfig.FAMILY_BLOB` is present.
- **Home** — hero mic, collapsed Setup banner (only shows when permissions missing), Recents card (last 3), bottom NavigationBar (Speak / History).
- **History** — per-row Retry chip (immediate on ERROR, confirm on success rows), Select mode for multi-delete, overflow → Delete all. All destructive ops wipe the WAV from disk too.
- **Settings** — provider/model selection (Groq Whisper + Groq Llama/Gemini), per-mode cleanup defaults, retention sliders, "Replay setup guide" link back to onboarding.

### Security baseline

- Secrets in **Android Keystore** (`SecureKeyStore`), no file fallback.
- API keys never logged or transmitted except in the request the user triggered.

---

## What's queued (not started)

| Task | Notes |
|---|---|
| **#17 Clipboard-vs-insert randomness** | Confirmed bug, **backlog** per owner. Suspected triggers: Samsung work profile ("your organization does not allow pasting content"), screen-off/app-switch return, very long recordings. Workaround = long-press to paste manually. Need reliable repro before fixing. |
| **#10 Build hardening** | Release-signed APK + ProGuard sweep + crash boundary review. Currently shipping debug APK to sideloaders. |

---

## Release & distribution

- **Repo:** https://github.com/kumaradarsh1993/wispr-fox-android
- **Tags shipped:** `v1.0` (initial stable at d3391cc), `v1.0.1` (baseline pre-v1.1 at e282baf), `v1.1.0` (current at 4cf396b).
- **Distribution channel:** sideload only (no Play Store — auto-paste via AccessibilityService is incompatible with current Play policy).
- **GitHub release:** v1.1.0 has the debug APK attached for end-user download. README points there.

---

## How to resume work (for a fresh Claude session)

1. **Read this file first** (you're here).
2. Read `CLAUDE.md` for the original architectural framing — but treat the "pre-implementation" sections as historical; the code is built.
3. The desktop sibling at `D:\Claude Code Projects\wispr-fox\` is still the canonical reference for **provider request shapes** (Groq Whisper multipart, Groq Llama, Gemini), **prompt strings** (`src-tauri/src/llm/prompts.rs`), and **retry semantics** (recent `HistoryRow.svelte`).
4. Build target: **don't run `./gradlew assembleRelease` locally** — RAM-tight machine, rustc-style OOM risk. Debug builds (`assembleDebug`) are fine. CI handles release builds on tag push.
5. Adb identity: device `RZCWA1TB26Z` (S23 Ultra). Debug variant installs as `com.wisprfox.android.debug`.

### Common commands

```bash
# Build + install + launch
cd "D:/Claude Code Projects/wispr-fox-android"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.wisprfox.android.debug -c android.intent.category.LAUNCHER 1

# Inspect device logs
adb logcat -s WisprFox:* AndroidRuntime:E
```

### Git identity for commits

The repo's commit identity is `wispr-fox dev <dev@wispr-fox.local>`. Use:

```bash
git -c user.name="wispr-fox dev" -c user.email="dev@wispr-fox.local" commit -m "..."
```

---

## Key files (where the logic lives)

| Area | File |
|---|---|
| Activity / nav | `app/src/main/kotlin/com/wisprfox/android/MainActivity.kt` |
| Bottom nav (shared) | `app/src/main/kotlin/com/wisprfox/android/ui/Nav.kt` |
| Home | `app/src/main/kotlin/com/wisprfox/android/ui/HomeScreen.kt` |
| History | `app/src/main/kotlin/com/wisprfox/android/ui/HistoryScreen.kt` |
| Onboarding | `app/src/main/kotlin/com/wisprfox/android/ui/OnboardingScreen.kt` |
| Settings | `app/src/main/kotlin/com/wisprfox/android/ui/SettingsScreen.kt` |
| Overlay service | `app/src/main/kotlin/com/wisprfox/android/overlay/OverlayService.kt` |
| Accessibility paste | `app/src/main/kotlin/com/wisprfox/android/delivery/` |
| Recording pipeline | `app/src/main/kotlin/com/wisprfox/android/queue/TranscribeWorker.kt` |
| Recording repo / DAO | `app/src/main/kotlin/com/wisprfox/android/history/` |
| Secrets | `app/src/main/kotlin/com/wisprfox/android/settings/SecureKeyStore.kt` |
| Providers | `app/src/main/kotlin/com/wisprfox/android/provider/` |

---

## Decisions that should not be re-litigated

- **AccessibilityService auto-paste is the default delivery path.** Clipboard is fallback. (Play Store distribution is out of scope; sideload only.)
- **Bring-your-own-key.** No accounts, no proxy, no telemetry, no crash reporter.
- **Three modes only** (Raw / Clean / Draft). No prompt editor in-app.
- **Bottom NavigationBar is 2 tabs** (Speak / History). Settings stays as a top-bar gear — less frequent destination, doesn't earn bottom-bar real estate.
- **Retry is available on ALL statuses**, not just ERROR (with confirm on non-error). Mirrors desktop; covers stranded mid-pipeline rows.
- **Delete always removes the WAV from disk** (mirrors retention sweep). Mental model: "delete = gone everywhere."
