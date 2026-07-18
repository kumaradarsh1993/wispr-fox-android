# wispr-fox for Android

**Talk. Tap. Done.** A voice-dictation app that drops what you said straight into whatever text field you're looking at: messages, email, notes, search bars, anything.

Built for **Indian English + Hindi code-switching**, long ramble-y recordings, and patchy networks.

Website: https://kumaradarsh1993.github.io/wispr-fox-android/

**Download:** the **[Releases page](https://github.com/kumaradarsh1993/wispr-fox-android/releases)** always has the latest APK + notes. Current stable: **v1.4.0** (audio-file import). Latest nightly: **v2.1.0-nightly.1** (UI redesign + bug fixes, on top of the v2.0.0 accounts/sync line).

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
- Audio is sent only to the speech-to-text provider you choose. **Voice files never leave your phone** — audio is never synced.
- Transcripts and saved recordings stay on your phone by default (7 days / 500MB rolling cap, configurable).
- No analytics. No telemetry.
- **Accounts + sync are optional.** Signed out, the app is fully local — no account needed (bring-your-own-key only). Sign in (Google or email/password) and your *transcripts* and API keys sync across your desktop / web / phone via Supabase; sign out and it goes back to local-only.

---

## For developers

- **HANDOVER.md** - current state, release notes, and next work.
- **CLAUDE.md** - older session framing and architecture notes.
- **CODEX_HANDOVER_2026-06-29.md** - *archived/historical* Codex checkpoint (superseded; kept for the audit trail).
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

**Stable: v1.4.0** — import existing audio files through the transcribe → clean/draft pipeline (on top of multi-provider STT, OpenAI cleanup, safer recording/paste, and the Fable reliability batch).

**Latest nightly: v2.1.0-nightly.1** — optional accounts + cross-device transcript sync (v2.0.0 line), then a full UI redesign on a real design system with dark theme, plus fixes for avatar drift, a wedging pipeline, and auto-paste falsely reporting success. See the Releases page for notes.
