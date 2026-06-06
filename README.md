# wispr-fox for Android

**Talk. Tap. Done.** A voice-dictation app that drops what you said straight into whatever text field you're looking at — messages, email, notes, search bars, anything.

Built for **Indian English + Hindi code-switching**, long ramble-y recordings, and patchy networks.

---

## Download & install

Grab the latest APK from the **[Releases page](https://github.com/kumaradarsh1993/wispr-fox-android/releases/latest)**.

1. Tap the `app-debug.apk` link on your phone.
2. Allow "Install from unknown sources" if prompted (Android will guide you).
3. Open **wispr-fox** — the setup guide walks you through the rest.

> **Requires:** Android 12+ (S23 Ultra-era and newer recommended).
> **You'll need:** a free **Groq API key** (the app deep-links you to the signup, takes ~60 seconds).

---

## What you get

- **Floating fox bubble** — tap once to start, tap again to stop. Drag it wherever it's least in the way.
- **Three modes** — `Raw` (verbatim transcript), `Clean` (light cleanup), or `Draft` (turns a spoken brief into a polished message). Long-press the bubble to switch.
- **Auto-paste** — your text lands in the focused text box without you switching apps.
- **History** — every recording is saved on-device with the audio. Replay, retry, or delete from the History tab.
- **Free to run** — Groq's free tier handles dictation forever; bring your own key, your audio never goes to a server we own.

---

## Privacy

- Your API key lives in Android Keystore — never leaves the device except as part of a request *you* triggered.
- Audio + transcripts stay on your phone (default retention: 7 days, 500MB rolling cap; configurable).
- No accounts. No analytics. No telemetry. No cloud sync.

---

## For developers

- **CLAUDE.md** — entry point for fresh agent sessions, current architecture state.
- **HANDOVER.md** — last-known-good state, what was just shipped, what's queued next.
- **PRD.md** — original product spec, locked decisions, open questions.
- **DESKTOP_LINKAGE.md** — relationship to the [desktop sibling](https://github.com/kumaradarsh1993/wispr-fox).

### Build locally

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

CI builds on every tag push (`v*`) and attaches the APK to the GitHub release.

---

## Current version

**v1.1.0** — stable. Home screen rework, bottom nav, history retry + multi-select delete. See the [release notes](https://github.com/kumaradarsh1993/wispr-fox-android/releases/latest) for the full changelog.
