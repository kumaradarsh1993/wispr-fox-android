# wispr-fox for Android — v2.3.0-nightly.1

`versionName = "2.3.0-nightly.1"` · `versionCode = 24` · channel: nightly

**Conversations get their own tab, and long recordings stop looking stuck.**

## No more frozen "Preparing audio…"

A shared 30-minute recording used to sit on "Preparing audio…" for 10-15
seconds with no change, which looked like a crash. You now see the real
progress: "Preparing audio 40%", then "Uploading 3.2 of 7.0 MB", "Labelling
speakers…", "Writing notes…". The same line shows in the notification.

## Much smaller uploads

Imported audio is now compressed to AAC (about 32 kbps) instead of being sent
as uncompressed audio. A 30-minute recording is about **7 MB instead of 57
MB**: one Groq request instead of three, and far quicker on mobile data. A
recording too large for one Whisper request (over about 80 minutes) is split
at a quiet moment, so words aren't cut in half.

## Voice note or Conversation

The Upload screen and the share sheet now ask one question first: is this
**just you talking**, or a **meeting or call**? The app remembers your answer.

- **Voice note** works as before. Quick transcribe is still there.
- **Conversation** runs three steps, each on its own:
  1. **Speakers.** Deepgram Nova-3 (or ElevenLabs Scribe) labels who said
     what, on the whole recording at once, so "Speaker 2" stays the same
     person throughout.
  2. **Whisper transcript** (on when you have a Groq key). Usually more
     accurate wording, kept next to the speaker version.
  3. **Meeting notes** (on when you have a cleanup key).

When it finishes, the meeting notes are on your clipboard (or the speaker
transcript, if you skipped notes).

## The Conversations tab

The bottom bar is now **Speak · Upload · Conversations · History**. Usage
moved into Settings. Conversations no longer appear in History.

- Each card shows the title, length, the speakers, and the start of the notes.
- Opening one shows **Notes**, **Speakers** (with timestamps) and **Whisper**.
- Tap a speaker to name them. The naming sheet shows the first thing each
  person said, so you can tell who is who. Names sync to your other devices.
- A failed step shows on the card, with its own retry button.
- **Copy** Notes, Speakers, Whisper or everything. **Export** shares one
  Markdown file with the notes and both transcripts, laid out for an AI
  assistant, to Drive, Claude or any app.
