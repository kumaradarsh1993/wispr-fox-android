# wispr-fox for Android — v2.0.0 (nightly.1)

## Accounts + cross-device sync

wispr-fox can now follow you across your phone, desktop, and browser — if you
want it to. Sign in once and your dictations show up everywhere. Don't sign in,
and nothing changes: the app works exactly as it did before, all on-device.

### What's new

- **Optional sign-in.** Use Google or an email and password. It's genuinely
  optional — pick "Continue without an account" and you're in local-only mode,
  identical to every build before this one.
- **Your transcripts, everywhere.** Once you're signed in, the text of every
  dictation syncs between your Android phone, the desktop app, and the web
  version. Start a note on your laptop, finish reading it on your phone.
- **Synced API keys and settings.** Paste your Groq (or OpenAI, Deepgram,
  ElevenLabs, Gemini) key on one device and it's there on the others — no more
  re-pasting keys every time you set up a new device.
- **Device badges in History.** Each recording now shows where it came from —
  Desktop, Web, or Mobile — so a long list from three devices stays readable.
- **Press-and-hold to delete, with real choices.** Long-press any recording (or
  use select mode / delete-all) to open a clearer delete dialog: choose whether
  to remove the voice file, the transcript, or both — and, when you're signed
  in, whether to delete just on this phone or everywhere across your devices.

### What hasn't changed (on purpose)

- **Your audio never leaves your phone.** Only transcript text syncs — the WAV
  recordings stay on the device that made them, always.
- **Local-only mode is untouched.** Signed out, there is zero new behaviour, no
  network calls, no account — byte-for-byte the app you already had.
- **Still bring-your-own-key, still no telemetry.** No tracking, no analytics,
  no third-party account required to dictate.

### Notes for testers

- Sign in from **Settings → Account**, or during the new (skippable) onboarding
  step. Signing out keeps everything already on the phone; it just stops syncing.
- Recordings synced from another device have no audio on this phone, so their
  play and re-run controls are hidden — that's expected.
