# wispr-fox for Android — v2.2.0

`versionName = "2.2.0"` · `versionCode = 23` · channel: stable

The stable roll-up of `v2.2.0-nightly.1` through `nightly.5` (nightly.3 failed
to build and was never published). If you are on any of those nightlies,
nothing here is new to you. Update anyway so the app reports itself as stable.

## Meetings

- Uploads can turn on speaker labels. The app switches to a compatible
  Deepgram Nova model and says so, instead of failing later.
- Raw, Clean, Draft and Meeting Notes are separate outputs. Meeting Notes are
  short: decisions, important context and next actions.
- Meeting recordings have a tinted History card, speaker turns, editable
  speaker names and a full-screen reader. Renaming a speaker updates what you
  read and copy without transcribing again.

## Share recordings from other apps

- Share one or several audio files from Samsung Voice Recorder, Files or any
  audio app to **wispr-fox**. Upload opens with the files ready.
- Several files can be processed one by one, or joined into one recording in
  the order you choose.
- Processing keeps running after you leave the app. When it finishes you get a
  notification and the transcript is on your clipboard.
- **Quick transcribe** at the top of Upload runs your saved settings in one
  tap. The full options are below it.
- Shared audio is reduced to 16 kHz before upload. A 30-minute recording drops
  from about 170 MB to about 57 MB, which means about a third as many requests.

## Sync and sign-in

- **Phone transcripts reach your other devices.** Uploads were refused when a
  batch mixed notes with and without some fields, and the refusal was ignored.
  Every batch now lists every field, and uploads are stamped with the time
  they were sent. Stuck transcripts upload on the first sync after updating.
- **Sign-in works on networks that block Supabase.** The app looks up its sync
  server through encrypted DNS (Cloudflare, then Google). The Google button
  opens your browser, which does its own lookup, so on a blocked network use
  email and password.

## Models

Checked against each provider's documentation on 19 October 2026.

- **Groq cleanup works again.** Its default, Llama 3.3 70B, was shut down for
  free-tier accounts on 16 August. The default is GPT-OSS 20B, and Groq's
  reasoning models get a larger reply budget with their reasoning hidden.
- OpenAI lists the GPT-5.6 family. Gemini defaults to 3.8 Flash.
- Retired Qwen 3.6, Gemini 3.7 Flash and Gemini 3.5 Flash are removed. Saved
  choices switch to their replacements on their own.

## Smaller changes

- Navigation is Speak, Upload, History, Usage. Foxy is the tap-to-speak
  control.
- Recent shows three transcript lines. Hold to copy, tap to open that row in
  History.
- About shows the installed channel. Update checks order nightlies correctly,
  and a stable release outranks every nightly of the same version.
- Clear audio cancels running imports before deleting their files.
