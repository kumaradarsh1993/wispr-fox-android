# wispr-fox for Android

**Talk. Tap. Done.** A voice-dictation app that drops what you said straight into whatever text field you're looking at: messages, email, notes, search bars, anything.

Built for **Indian English + Hindi code-switching**, long ramble-y recordings, and patchy networks.

Website: https://kumaradarsh1993.github.io/wispr-fox-android/

Codex preview download: [wispr-fox-android-v1.2.0-codex.1.apk](https://github.com/kumaradarsh1993/wispr-fox-android/releases/download/v1.2.0-codex.1/wispr-fox-android-v1.2.0-codex.1.apk)

Last Claude stable: [v1.1.0 app-debug.apk](https://github.com/kumaradarsh1993/wispr-fox-android/releases/download/v1.1.0/app-debug.apk)

---

## Download & install

Grab the APK above, or open the **[Releases page](https://github.com/kumaradarsh1993/wispr-fox-android/releases)** for release notes.

1. Tap the APK link on your phone.
2. Allow "Install from unknown sources" if prompted.
3. Open **wispr-fox**. The setup guide walks you through the rest.

> **Requires:** Android 12+ (S23 Ultra-era and newer recommended).
> **You'll need:** one speech-to-text API key. Groq, OpenAI, Deepgram, and ElevenLabs are supported.

---

## What you get

- **Floating fox bubble** - tap once to start, tap again to stop. Drag it wherever it's least in the way.
- **Three modes** - `Raw` transcript, `Clean` light cleanup, or `Draft` for turning a spoken brief into a polished message.
- **Provider choice** - Groq, OpenAI, Deepgram, and ElevenLabs for speech-to-text; Groq, OpenAI, or Gemini for cleanup.
- **Safer auto-paste** - text only auto-pastes when the focused target still matches the app you started dictating into; otherwise it stays on the clipboard.
- **History** - every recording is saved on-device with the audio. Replay, retry, or delete from the History tab.

---

## Privacy

- API keys live in Android Keystore and never leave the device except as part of a request you triggered.
- Audio is sent only to the speech-to-text provider you choose.
- Transcripts and saved recordings stay on your phone by default (7 days / 500MB rolling cap, configurable).
- No accounts. No analytics. No telemetry. No cloud sync.

---

## For developers

- **HANDOVER.md** - current state, release notes, and next work.
- **CODEX_HANDOVER_2026-06-29.md** - Codex checkpoint for Claude/Codex handoff.
- **CLAUDE.md** - older session framing and architecture notes.
- **PRD.md** - original product spec, locked decisions, open questions.
- **DESKTOP_LINKAGE.md** - relationship to the [desktop sibling](https://github.com/kumaradarsh1993/wispr-fox).

### Build locally

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

CI builds on every tag push (`v*`) and attaches an installable APK to the GitHub release.

---

## Current version

**v1.2.0-codex.1** - Codex preview. Adds multi-provider STT, OpenAI cleanup, safer Android recording/paste handling, and GitHub APK automation.

**v1.1.0** remains the last Claude stable checkpoint.
