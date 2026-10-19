# Conversations: the recorded-audio flow across desktop, Android and web

Status: design for the next nightlies (desktop v3.6.0-nightly, Android
v2.3.0-nightly, web). Written 2026-10-19 after an audit of all three clients.

## Who uses it and how

wispr-fox has three jobs. They need different screens.

| Job | Share of use | Where it starts | What the user wants back |
|---|---|---|---|
| **Dictation** | ~80% | Hotkey on desktop, bubble on Android | Text pasted where the cursor is |
| **Voice note** | ~10% | Phone voice recorder, 30-45 min, one speaker | One clean transcript on the clipboard |
| **Conversation** | ~10%, growing | Phone recording of a physical or Zoom meeting, 2-3 speakers | Who said what, plus short meeting notes |

Two people use the conversation flow:

- **Adarsh, Android (S23 Ultra).** Records in Samsung Voice Recorder, then
  shares the file to wispr-fox or picks it from Upload.
- **An iPhone user with no installable app** (no TestFlight). The web app is
  the only way in. iOS Safari cannot be a share target: the Web Share Target
  API is not supported on iOS, so the path is Voice Memos → Share → Save to
  Files, then Upload in Safari and pick the file.

## What is wrong today

### Android

1. **"Preparing audio…" is a frozen label for 10-15 s.** `ImportWorker`
   decodes the whole file (MediaCodec, then a 16 kHz resample) before anything
   else happens, and shows a fixed string the whole time. A 30-minute file
   looks like a crash.
2. **The upload is far bigger than it needs to be.** The decoded file is
   16 kHz WAV, about 57 MB for 30 minutes. Groq gets it as 3 requests sent one
   after another. Deepgram gets all 57 MB in one request over mobile data.
   The original Samsung M4A is about half that size, and a re-encoded 32 kbps
   mono AAC is about 7 MB, which fits in one Groq request.
3. **Chunks are cut at a fixed byte count** (`WavChunker`, 20 MB), so a word
   can be split across two requests.
4. **No progress after preparing.** The user sees "Transcribing…" for the
   whole upload and processing with no sense of how far along it is.

### Desktop

5. **Deepgram and ElevenLabs time out at 90 s total**, including the upload.
   An hour-long meeting on a slow uplink plus provider processing can exceed
   that.
6. **Conversations live in the same list as dictations.** They are separated
   only by a filter chip and a tint. A list of 40 dictations buries the one
   meeting from this morning.

### Web

7. **No speaker labels at all.** Web can't make a conversation, only display
   one that desktop or Android made.
8. **The new long-upload path splits audio into ~2-minute parts.** That is
   right for a voice note and wrong for a conversation: each part is
   diarized separately, so "Speaker 1" in part 3 need not be the same person
   as "Speaker 1" in part 1.

### All three

9. **The choice between voice note and conversation is buried.** It is a
   "speaker labels" checkbox inside upload options, plus a separate "meeting
   notes" checkbox. The user thinks in two kinds of recording, not in
   provider features.

## The design

### One choice at the start: "Voice note" or "Conversation"

Every upload entry point (Android share sheet, Android Upload, desktop drop or
Upload, web Upload) asks one question first, as two large buttons:

- **Voice note**: one speaker. Whisper (Groq by default), then Clean.
- **Conversation**: several people. A speaker-labelling engine, then speaker
  turns and meeting notes.

The last choice is remembered and preselected. Engine, model and output
details stay available under "Options", as today.

### A separate Conversations screen

Conversations move out of Home into their own tab on all three clients:
**Home · Conversations · Insights · Settings** (desktop and web),
**Speak · Upload · Conversations · History** (Android, replacing Usage in the
bar; Usage moves under Settings).

A conversation card is visibly different from a dictation card:

- Title, date, length, and **speaker chips** (Asha, Rohan, Speaker 3).
- The first 2 lines of the meeting notes, not the raw transcript.
- Opening it shows a reader with two panes: **Notes** (decisions, context,
  next actions) and **Transcript** (speaker turns with timestamps). Speakers
  are named in place, and the names apply to both panes and to copied text.
- Copy offers "Notes", "Transcript" or "Both".

Dictations stay on Home exactly as they are. A conversation never appears
there, so Home stays the fast list it is for the 80% job.

### Upload and progress

- **Never split a conversation.** Diarization needs the whole recording in one
  request, so speaker labels stay consistent. Voice notes may still be split.
- **Send compressed audio.**
  - Android re-encodes to 32 kbps mono AAC (MediaCodec encoder + MediaMuxer)
    while decoding, instead of writing WAV. 30 minutes becomes about 7 MB.
    That is one Groq request for a voice note, and a small single upload for a
    conversation.
  - Desktop already sends the original M4A/MP3 unchanged. It keeps doing that,
    and the Deepgram/ElevenLabs timeout scales with file size instead of a
    flat 90 s.
  - Web sends a **conversation** straight from the browser to the provider
    with the user's own key (Deepgram and ElevenLabs both allow browser
    requests from the app's origin; checked 2026-10-19). That avoids Vercel's
    4.5 MB request limit without splitting. Voice notes keep the server path
    with browser-side splitting. The server's owner fallback keys are never
    sent to the browser.
- **Real progress.** One status line with steps and a percentage where one is
  known: "Preparing 40%" (from the decoder's position), "Uploading 3.2 of
  7 MB", "Transcribing", "Writing notes". Android shows the same line in the
  notification.

### Engines

- **Conversation default: Deepgram Nova-3** with `diarize=true` and
  `utterances=true`. It has the $200 free credit and is already supported by
  desktop and Android. ElevenLabs Scribe v2 is the alternative.
- **Meeting notes** use the Draft engine, as today (GPT-OSS 120B on Groq by
  default).
- A conversation picked with an engine that can't label speakers is switched
  automatically, with a one-line note saying why. Desktop does this already;
  Android and web get the same rule.

### Sync

No schema change. The five meeting columns already exist on the shared
`notes` table (`meeting_notes_text`, `speaker_turns`, `speaker_names`,
`is_meeting`, `diarization_enabled`). The Conversations tab is a view over
`is_meeting = true`. Speaker names edited on one device should sync.
Desktop currently treats them as local-only on rows from other devices; that
rule changes to "name edits sync for every row", since they are presentation
metadata and can't lose data.

## Order of work

1. **Android nightly**: progress line, AAC re-encode, Voice note /
   Conversation choice on share and Upload, Conversations tab and card.
2. **Web**: Conversation upload direct to Deepgram, Conversations tab, reader
   with named speakers. This unblocks the iPhone user.
3. **Desktop nightly**: Conversations tab, the same two-way upload choice,
   size-scaled provider timeout.
4. Stress test on real recordings: a 45-minute one-speaker voice note and a
   30-minute 3-person recording, each from Samsung Voice Recorder and iPhone
   Voice Memos, through every client.
