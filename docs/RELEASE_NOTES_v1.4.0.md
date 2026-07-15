## wispr-fox for Android 1.4.0 — Import audio files

You can now transcribe audio you already have, not just what you dictate live.

### Import any voice note or call recording

Tap **Import audio file** on the home screen, pick one or more files, and wispr-fox transcribes them for you. Before it starts, you choose:

- **Transcription model** — defaults to **Whisper Large v3 on Groq**.
- **Output** — Raw transcript, Clean-up, or Draft (the same three styles you already use for dictation).
- **Clean-up model** — defaults to **Gemini 3.5 Flash** (a free-tier Google model; add a Gemini key in Settings to use it, otherwise the raw transcript is kept).

Your picks here apply just to the import — they don't change the model you use for live dictation.

### Built for real recordings

- Works with the formats Samsung and iPhone actually produce — Voice Recorder notes, call recordings, and Voice Memos (M4A/AAC, MP3, AMR, 3GP, Ogg/Opus, FLAC, WAV).
- Long clips are split and transcribed in pieces automatically, so a full-length call goes through fine.
- Imports run in the background and show up in **History** as they finish, where you can copy the text or switch between Raw / Clean / Draft. Imported text is copied to your clipboard (never auto-pasted, since you're not in a text field when you import).

### Notes

- Import uses the **Groq** transcription path by default, which handles long files best. Very large files with a different transcription provider selected may hit that provider's size limit.
- Model line-ups were re-checked on 2026-07-15: Whisper Large v3 / v3 Turbo (Groq) and Gemini 3.5 Flash are current, and nothing you rely on has been retired.
