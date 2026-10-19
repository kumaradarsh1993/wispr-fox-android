# Conversations: shared contract (v2)

Authoritative for desktop, Android and web. Read with
`docs/DESIGN_conversations.md`. Written 2026-10-19.

## Three jobs, three surfaces

| Job | Where it lives | Not on |
|---|---|---|
| **Dictation** (hotkey / bubble, ~80%) | Home | Conversations |
| **Voice note** (one speaker, long, uploaded) | Home, with the Uploaded chip | Conversations |
| **Conversation** (2+ speakers, uploaded) | **Conversations** tab | Home |

Every upload entry point asks one question first: **Voice note** or
**Conversation**. The last answer is remembered. Everything else
(engines, outputs) sits under Options.

A row is a conversation when `is_meeting = true`. That is the only test.

## Pipeline for a conversation

1. **Speakers pass** (required): a diarizing engine on the whole file, never
   split. Default Deepgram Nova-3 (`diarize=true&utterances=true&smart_format=true`).
   Alternatives: ElevenLabs `scribe_v2` with `diarize=true`, OpenAI
   `gpt-4o-transcribe-diarize`. Result: speaker turns with start/end seconds,
   and `transcript` = the turns rendered as `Speaker N: text` blocks.
2. **Accuracy pass** (optional, default ON when a Groq key exists): Whisper
   (`whisper-large-v3` on Groq, split if needed, which is fine because it has
   no speakers) on the same audio. Stored as the second transcript.
3. **Notes pass** (optional, default ON when an LLM key exists): meeting notes
   from the speakers transcript, with the accuracy transcript given as
   reference when it exists. Stored in `meeting_notes_text`.

Each pass succeeds or fails on its own. A failed pass never discards a pass
that worked. Each pass can be retried alone from the conversation screen.

## Storage: no schema change

The shared `notes` table cannot gain columns from the clients (they hold only
the anon key), so the second transcript rides inside the existing
`speaker_turns` text column. All three clients already ignore unknown keys.

`speaker_turns` JSON, version 2:

```json
{
  "version": 2,
  "turns": [{ "speaker": "Speaker 1", "text": "…", "start": 0.0, "end": 4.2 }],
  "engine": { "provider": "deepgram", "model": "nova-3" },
  "accuracy": {
    "text": "Whisper's full transcript …",
    "provider": "groq",
    "model": "whisper-large-v3"
  },
  "passes": {
    "speakers": "done",
    "accuracy": "done",
    "notes": "failed"
  },
  "errors": { "notes": "Groq cleanup 429: rate limit" }
}
```

- `turns` keeps the v1 shape, so old clients still read speakers.
- `accuracy`, `engine`, `passes`, `errors` are optional. Absent `accuracy`
  means no second transcript was made.
- `passes` values: `done`, `failed`, `skipped`, `running`.
- Readers MUST accept `version` 1 or 2 and treat a missing field as absent.
  Writers write version 2.

`speaker_names`: unchanged, `{"Speaker 1": "Asha"}`. Name edits are
presentation metadata and sync for every row, including rows another device
made (the originating device's push carries them; a non-originating device
may push only `speaker_names` for that row).

## The conversation screen

- **List**: one card per conversation. Title, date, length, speaker chips
  (named or "Speaker N"), first 2 lines of meeting notes (or of the
  transcript if there are no notes), and a status line for any failed pass.
- **Reader**, three views:
  - **Notes**: meeting notes.
  - **Speakers**: turns with `mm:ss` timestamps and the speaker name in the
    accent colour. Tap a speaker label to rename that speaker everywhere.
  - **Whisper**: the accuracy transcript, plain paragraphs. Only shown when
    it exists.
- **Speaker naming**: a panel listing each speaker with the first thing they
  said, so the user can tell who is who, and a text field each.
- **Retry**: each failed or skipped pass has its own button ("Add Whisper
  transcript", "Retry speakers", "Write notes").
- **Copy**: Notes / Speakers / Whisper / Everything.
- **Export**: one Markdown file, built for pasting into an AI assistant
  (format below). Desktop saves via a save dialog; Android shares the file
  through the share sheet; web downloads it.

## Export format (identical on all clients)

Filename: `<YYYY-MM-DD> <title>.md` (title sanitised for filenames).

```markdown
# <title>

- Date: <ISO date and local time>
- Length: <h:mm:ss>
- Speakers: Asha, Rohan, Speaker 3
- Speaker engine: Deepgram nova-3
- Word engine: Groq whisper-large-v3

> Two transcripts of the same recording follow. The speaker transcript
> knows who spoke but can mishear words. The Whisper transcript has more
> accurate wording but no speakers. Use the speaker transcript for who said
> what, and the Whisper transcript to correct the wording.

## Meeting notes

<meeting notes, or "(not generated)">

## Speaker transcript

**[00:00] Asha:** …

**[00:42] Rohan:** …

## Whisper transcript

<accuracy text, or "(not generated)">
```

Speaker names are substituted. Sections with no content still appear with
"(not generated)", so a reader can tell missing from empty.
