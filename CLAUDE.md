# Base prompt — read this FIRST

> ## 🟢 CURRENT STATE — 2026-06-06
>
> **Codex preview `v1.2.0-codex.2` is the active working checkpoint.** Last Claude stable remains `v1.1.0` (commit `4cf396b`).
>
> Also read [CODEX_HANDOVER_2026-06-29.md](./CODEX_HANDOVER_2026-06-29.md) after [HANDOVER.md](./HANDOVER.md).
>
> Before reading anything else in this file, **read [HANDOVER.md](./HANDOVER.md)** — it's the single source of truth for what ships today, what's queued, and how to resume work. The sections below are kept for historical context (scope evolution, original design rationale) but the "pre-implementation" framing is no longer accurate.

> ## ⚠ SCOPE UPDATE — 2026-05-24 (overrides parts of this file below)
>
> The owner re-scoped the app after the Phase 0/1 work. The "utility-only,
> no Clippy" framing in the older text below is **superseded**. The Android
> app is now a **fuller port of the desktop experience**, not a stripped
> utility. Specifically:
>
> 1. **Clippy/avatar IS in.** A small draggable **floating avatar** (reuse
>    the desktop watercolor **fox** PNGs in `../wispr-fox/static/fox/`) is
>    now the **hero activation surface**, not an opt-in. It changes
>    appearance to clearly show recording vs idle vs processing vs done,
>    mirroring the desktop floater's state→art mapping
>    (`../wispr-fox/src/routes/clippy/+page.svelte`). Start with one avatar
>    (fox); avatar-picker is a later nice-to-have.
> 2. **Gesture model:** **tap** the avatar to start recording, **tap again**
>    to stop (toggle, not push-to-hold). **Long-press (~600ms, haptic +
>    visual)** opens a small popup menu with the **three modes**.
> 3. **Three modes** (port desktop prompts verbatim):
>    **Raw** (transcript, no LLM) · **Cleaned** (LIGHT_SYSTEM cleanup) ·
>    **Reformatted/Construct** (DRAFTING_SYSTEM — the spoken brief becomes a
>    drafted message). The F9 "Advanced" prompt also ported, available.
> 4. **History/Library is in.** Everything dictated is stored locally
>    (Room + WAV on disk, chunked ≤20MB like desktop `stt/chunk.rs`). The
>    library lets you **replay audio**, and view **Raw / Cleaned /
>    Reformatted** versions per recording (generate-on-demand for missing
>    variants). Mirror desktop history schema + retention.
> 5. **Settings mirror desktop's stack:** which Whisper model, which LLM
>    provider (Groq/Gemini) + model, per-mode cleanup defaults, retention.
> 6. **Delivery:** text should land **in the focused field automatically** —
>    AccessibilityService auto-paste is now the **default** (sideload
>    distribution makes the Jan-2026 Play policy moot), clipboard fallback.
> 7. **Target device floor relaxed:** primary target is **S23 Ultra**-era
>    and newer; Note 10+ is nice-to-have, no longer the hard floor. The
>    Phase 1 mic-survival spike risk is correspondingly lower.
>
> Everything else below (provider contracts, the three failure modes,
> language=auto, prompt-injection wrapper as a security boundary, no IME,
> no analytics, no account, BYOK) **still holds**. Revised build plan lives
> in the task list + the session that made this change.

You are bootstrapping into the **wispr-fox-android** project. Nothing
has been built yet. This file is the entry point.

## What this project is, in one sentence

An Android app whose **one job** is: *user presses a button, speaks,
and their words appear in whatever text field they were just looking
at.* Everything else is in service of that one job.

## Read these, in this order, before writing any code

1. **This file (CLAUDE.md)** — you're here.
2. **[PRD.md](./PRD.md)** — what we're building, what we're not,
   target devices, locked decisions, leading hypotheses.
3. **[DESKTOP_LINKAGE.md](./DESKTOP_LINKAGE.md)** — what to port
   line-for-line from the desktop sibling, what to diverge on, what
   to leave behind.
4. **[README.md](./README.md)** — the public-facing one-pager. Short.

Then, before designing anything substantial, **scan the desktop
sibling project** at:

```
D:\Claude Code Projects\wispr-fox\
```

Particularly:
- `GETTING_STARTED.md` — desktop architecture, locked decisions, the
  "lessons baked into the code" section. Many of those lessons are
  platform-agnostic and apply here too (e.g. `language=auto` for
  Whisper, prompt-injection wrapping, F8-style raw-by-default).
- `src-tauri/src/stt/groq.rs` — exact Groq Whisper request shape.
  Port the multipart structure to Kotlin/OkHttp verbatim.
- `src-tauri/src/llm/prompts.rs` — `LIGHT_SYSTEM` and `DRAFTING_SYSTEM`
  prompts. Map to Narrate / Construct on Android.
- `src-tauri/src/llm/gemini.rs` and `groq.rs` — the LLM provider
  request shapes.
- `src-tauri/src/clippy.rs` — orchestration logic (raw vs cleaned,
  timeout-to-raw fallback). Same pattern applies on Android.

You do **not** need to port the desktop UI, Clippy character,
theming, hotkey capture, tray icon, or onboarding flow — those are
desktop-only concerns.

## The mental model to hold while working

This is a **utility app**, not a product with personality. The
desktop project earns its character with Clippy because the user is
sitting at a desk and the moment is leisurely. On Android the user
is mid-task, mid-message, mid-walk — there is no room for charm.
The bar is: *invisible until invoked, instant when invoked, never
loses my recording.*

Three things WILL go wrong and the app must survive each:

1. The user speaks for >2 minutes (every other Android voice tool
   silently dies here).
2. The network drops mid-upload (Indian mobile reality).
3. The OS kills the foreground service to save battery (Samsung
   especially aggressive).

If a change you're considering would make any of those three worse,
push back or ask before doing it.

## The leading hypothesis — and what's open

The PRD proposes a **floating overlay bubble + foreground service**
as the activation surface. That's the leading hypothesis, drawn
from how WhisprFlow and a few others approach it. It is **not
locked**. Before implementing, take 30 minutes to investigate:

- Is there a less-permission-heavy way (e.g. Quick Settings tile,
  Assistant intent, share-target) that gets us close enough?
- Has Android 14/15 introduced anything that obsoletes the
  overlay approach?
- What do current best-in-class Android voice tools do in May 2026?

Then either confirm the overlay approach or propose the better one
with a short trade-off doc. **Do not just implement the PRD's first
suggestion without checking.**

## Backend / settings — for v1, the floor is

The user explicitly said *"I would just want it to work"* for v1.
Translate that into a hard minimum-viable settings surface:

- API key paste field (one provider — Groq — is enough for v1)
- Permission status indicators (granted / not granted, with deep-link
  to fix)
- Mode toggle: Narrate vs Construct (the two gestures from the PRD)
- Audio retention: on/off + slider (default 7 days, 500MB)
- Nothing else. No theming, no skin picker, no usage dashboard, no
  prompt editor, no provider comparison page. Those are v2.

Backend services that must exist for v1:
- Foreground service holding the mic
- WorkManager queue for upload retries
- Keystore-backed key storage
- Local file storage for WAV recordings with rolling retention
- A single `TranscribeWorker` doing Groq Whisper → optionally LLM cleanup

That's it. If a v1 task isn't directly serving the one job, defer it.

## Things to NOT do, ever

- Don't add Clippy in any form. The desktop has him; Android doesn't.
- Don't replace the system keyboard (no IME).
- Don't ship analytics, crash reporters, or telemetry.
- Don't add account signup. Bring-your-own-API-key only.
- Don't add "always listening" / wake-word activation.
- Don't pin Whisper `language=en` — user code-switches Hindi.
- Don't try to share code with the desktop project via FFI / shared
  Rust crate. The platforms are different enough that the cost
  outweighs the benefit. Port the contracts (request shapes, prompts)
  by hand.

## When in doubt

Ask the user. They are deeply opinionated and have lived this
problem long enough that their preferences are usually load-bearing.
A 30-second clarifying question is cheaper than rebuilding a screen.

## Status as of session start

- Folder exists at `D:\Claude Code Projects\wispr-fox-android\`
- Four docs in place: this file, PRD.md, DESKTOP_LINKAGE.md, README.md
- **Zero Android code written.** Gradle project not initialized.
- First implementation step is likely: `gradle init` with the
  Compose template, then permission scaffolding.
