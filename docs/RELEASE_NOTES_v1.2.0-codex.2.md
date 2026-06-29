# wispr-fox Android v1.2.0-codex.2 - Codex preview

This is a Codex preview build. It is meant for testing before any stable promotion.

## What's new

- Speech-to-text providers: Groq, OpenAI, Deepgram, and ElevenLabs.
- Cleanup providers: Groq, OpenAI, and Gemini.
- Provider/model pickers in Settings.
- Onboarding accepts any supported STT key, not only Groq.
- Safer auto-paste: the app checks that the focused editable app still matches the app where dictation started.
- Clipboard fallback is marked sensitive.
- Recording startup now handles microphone/foreground-service failures more cleanly.
- Floating overlay setting is now honored.
- GitHub Actions can build and attach an installable APK from a tag push.

## Before stable

Run the S23 Ultra matrix in `HANDOVER.md`, configure persistent GitHub signing secrets, and verify upgrade/install behavior from the produced APK.
