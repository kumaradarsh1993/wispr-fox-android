# wispr-fox-android

Android sibling to [wispr-fox](../wispr-fox/) — system-wide voice dictation
for Indian English + Hindi, surviving long sessions and patchy networks.

## Status

**Pre-implementation.** This folder contains the spec; no Android code
has been written yet.

## Documents

Read in this order:

1. **[CLAUDE.md](./CLAUDE.md)** — base prompt. If you're a fresh
   Claude Code session, start here. It tells you what to read and
   in what order, and points at the desktop sibling for shared
   context.
2. [PRD.md](./PRD.md) — what we're building, what we're not, target
   devices, tech stack, success criteria. Notes which decisions are
   locked vs. which are leading hypotheses to validate.
3. [DESKTOP_LINKAGE.md](./DESKTOP_LINKAGE.md) — what's shared with
   wispr-fox desktop and what diverges.

## Relationship to wispr-fox (desktop)

| | Desktop (wispr-fox) | Android (this) |
|---|---|---|
| Stack | Tauri 2 + Svelte 5 + Rust | Kotlin + Compose |
| Activation | F8/F9/F10 hotkeys | Floating overlay bubble |
| Modes | 3 (light/advanced/drafting) | 2 (narrate/construct) |
| Character | Clippy + skins + themes | None — utility only |
| STT | Groq Whisper turbo | Groq Whisper turbo (same) |
| LLM | Groq Llama / Gemini | Groq Llama / Gemini (same) |
| Queue | Inline retry | Offline-first WorkManager queue |

## Why a separate repo / folder

The platform realities are different enough that sharing code would
create more friction than it saves. The **provider contracts** (Groq
endpoint shapes, prompt templates) are what we copy across; the rest
is platform-native.

## Building

Once Android Studio Hedgehog (or later) is installed:

```
./gradlew assembleDebug
```

(Nothing to build yet. Code starts after PRD review.)
