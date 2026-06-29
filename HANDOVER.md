# HANDOVER - wispr-fox-android

> Last update: 2026-06-29. Current Codex preview: **v1.2.0-codex.2**. Last Claude stable: **v1.1.0** at commit `4cf396b`.

This file is the current state-of-the-world for Android. `CLAUDE.md` is useful historical context, but this handover wins when they disagree.

## What changed in the Codex preview

- Added speech-to-text provider selection for **Groq**, **OpenAI**, **Deepgram**, and **ElevenLabs**.
- Added cleanup provider selection for **Groq**, **OpenAI**, and **Gemini**.
- Added secure key slots for OpenAI, Deepgram, and ElevenLabs using the existing Android Keystore-backed storage.
- Updated onboarding and Settings so a new user can start with any supported STT provider, not only Groq.
- Added a target-package paste guard: recordings remember the focused editable app at start, and auto-paste only runs if the focused editable package still matches at delivery time.
- Marked clipboard fallback clips as sensitive to reduce Android clipboard preview leakage.
- Added microphone/foreground-service startup recovery and a partial wake lock while recording.
- Made the overlay setting actually stop the overlay service when disabled.
- Improved basic avatar accessibility semantics.
- Added `.github/workflows/android-apk-release.yml` so tag pushes build/test/sign/upload an installable APK to GitHub Releases with "Codex build" in the release name.

## Published preview release

- Tag: `v1.2.0-codex.2`
- Release URL: `https://github.com/kumaradarsh1993/wispr-fox-android/releases/tag/v1.2.0-codex.2`
- APK asset: `wispr-fox-android-v1.2.0-codex.2.apk`
- APK SHA-256: `e3316147f974e404b6a207e52f8c9f4ef798056dd67023cdef284be6a4bf2c48`
- Release name from CI: `wispr-fox Android v1.2.0-codex.2 - Codex build`
- GitHub Actions run `28379653886` passed on 2026-06-29.
- The workflow uses repository signing secrets when available:
  - `ANDROID_SIGNING_KEY_B64`
  - `ANDROID_SIGNING_STORE_PASSWORD`
  - `ANDROID_SIGNING_KEY_ALIAS`
  - `ANDROID_SIGNING_KEY_PASSWORD`
- The repository signing secrets were absent for `v1.2.0-codex.2`, so CI generated a temporary Codex preview signing key. That produces an installable APK, but upgrades from older APKs may require uninstall/reinstall.

## Verification already done

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat testDebugUnitTest assembleRelease
```

Result: passed locally. The tag workflow also passed and attached the APK to the GitHub prerelease.

## Still needs real-device QA

Run this on the Samsung S23 Ultra path before calling it stable:

- Fresh install, onboarding with each STT key path: Groq, OpenAI, Deepgram, ElevenLabs.
- Denied microphone permission, denied notification permission, denied overlay, denied accessibility.
- Start from overlay, Quick Settings tile, and in-app button.
- Long recording with screen off / battery optimization on and off.
- App-switch during transcription: expected behavior is clipboard-only, not auto-paste into the new app.
- Work-profile or Samsung policy paste-block case.
- Large font and display-size accessibility pass.
- Install/upgrade behavior for the APK produced by GitHub Actions.

## Known tradeoffs

- The target-package paste guard is safer than the previous "paste wherever focus is now" behavior, but it does not yet verify exact field identity. Same-package focus drift can still paste into another field in the same app.
- Release signing secrets are not configured in this repo by code. Until the owner adds them in GitHub settings, the workflow's fallback Codex preview key is not a stable long-term update key.
- The Android avatar work remains simpler than the desktop raster avatar SDK. This pass focused on provider/runtime safety and APK automation.

## Resume pointers

- Provider catalogue: `app/src/main/kotlin/com/wisprfox/android/provider/ProviderCatalog.kt`
- Provider factory: `app/src/main/kotlin/com/wisprfox/android/core/ProviderFactory.kt`
- New provider clients:
  - `app/src/main/kotlin/com/wisprfox/android/provider/openai/`
  - `app/src/main/kotlin/com/wisprfox/android/provider/deepgram/`
  - `app/src/main/kotlin/com/wisprfox/android/provider/elevenlabs/`
- Paste safety: `delivery/DeliveryManager.kt`, `delivery/WisprFoxAccessibilityService.kt`, `history/RecordingEntity.kt`
- Startup hardening: `core/RecordingController.kt`, `audio/RecordingService.kt`
- Release workflow: `.github/workflows/android-apk-release.yml`
