# wispr-fox-android — Product Requirements

> **New here?** Read [CLAUDE.md](./CLAUDE.md) first. It bootstraps a
> fresh agent and tells you which parts of the desktop sibling at
> `D:\Claude Code Projects\wispr-fox\` to mine for context.

Sibling to **wispr-fox** (Windows/Mac desktop dictation app). Same brand,
same provider stack, same press-and-go philosophy — but Android only,
and stripped down. No Clippy, no character, no theming dance. Just
fast, reliable voice-to-text everywhere on the phone.

This doc is the source of truth for what we're building and — just as
importantly — what we are explicitly **not** building.

---

## 0. The one job

If you read nothing else, read this.

The app exists to do **one thing well**: the user presses a button,
speaks, and the words appear in whatever text field they were last
looking at. With one secondary gesture for "I gave a brief, write me
a draft."

That's the entire product. Every other feature in this PRD is in
service of those two gestures working reliably under real-world
mobile conditions (long sessions, network drops, OS battery-killers).
If a feature doesn't serve those two gestures, it doesn't ship in v1.

The closest desktop analog is wispr-fox's **F8 (Light)** and **F10
(Drafting)** modes. F9 (strict copy-edit) is intentionally absent
on mobile — the mobile use case for "fix my grammar but don't
change anything" is too narrow to justify a third gesture. See
`D:\Claude Code Projects\wispr-fox\src-tauri\src\llm\prompts.rs`
for the exact prompts to mirror.

---

## 1. Why this exists

The user already uses wispr-fox on desktop. On Android, every existing
option is broken in a specific way:

| Tool | What breaks |
|---|---|
| Gboard voice typing | 2-minute hard cap, dies silently mid-thought |
| Samsung voice input | Garbles Indian-accented English + Hindi code-switching |
| WhisprFlow / Aqua / etc | iOS first, Android is an afterthought or absent |
| Whisper-based 3rd party apps | UI is hostile, no system-wide hook, no overlay |

The opening is: a system-wide overlay button, backed by Whisper, that
just works for long-form dictation in Indian English + Hindi, and
survives network drops.

---

## 2. Target devices (non-negotiable floor)

Must run cleanly on the user's actual phones, oldest first:

- **Note 10+** (Android 12, 8GB RAM) — oldest device that must work
- **S20 / S20+** — minimum-spec mainstream target
- **S21 / S22 / S23** — sweet spot
- **S23 Ultra** — primary daily driver
- **S26** (May 2026, latest) — newest, latest Android API levels

**Minimum API:** Android 12 (API 31). Most permission/overlay UX is
stable from 12 onward.

**Target API:** Android 15 (API 35) for May 2026 Play Store compliance.

---

## 3. Scope — what we ARE building

### 3.1 Activation model — LEADING HYPOTHESIS, validate before committing

> ⚠️ This subsection describes the **leading hypothesis**, not a
> locked decision. WhisprFlow + a few Android voice tools use the
> overlay-bubble pattern; that's where this came from. **Before
> implementing, do a half-day investigation** of whether a
> lighter-permission alternative gets us 80% of the way there.
> Candidates to evaluate explicitly:
>
> - **Quick Settings tile** (`TileService`) — one-tap from anywhere
>   without `SYSTEM_ALERT_WINDOW`. Loses the "floating in your
>   current app" affordance but trades enormous permission cost for
>   one swipe.
> - **Assistant intent** (`VoiceInteractionService`) — register as
>   the default voice assistant. Long-press home / power button →
>   our app. Heaviest permission ask but most natural.
> - **Share target / "Send to wispr-fox"** — user selects text field
>   and shares-in. Adds one tap per use, but zero overlay permission.
> - **Accessibility service** — could observe focused input field
>   and inject directly. High permission stigma, Play Store scrutiny.
> - **Hardware key mapping** (Samsung Bixby button on supported
>   devices, Google Assistant button) — fastest of all on devices
>   that have it. Limited device coverage.
>
> Pick whichever wins on (a) frequency-of-use friction and
> (b) permission-surface. Document the trade-off before coding.

We do **not** build an IME (input method). IMEs are a tar pit on
Android: replacing the user's keyboard is hostile, the permission flow
is awful, and every app handles input quirkily. This one is locked.

The overlay-bubble flow, **if we end up with it**:

- **System-wide floating bubble** (think Messenger chat heads, or
  Google Assistant's old mic). Tap to start, tap again to stop.
- Drawn via `SYSTEM_ALERT_WINDOW` (overlay permission). User grants
  this once during onboarding.
- Persistent **foreground service** keeps the bubble alive across
  app switches. The Android-mandated ongoing notification ("wispr-fox
  is showing on top of other apps") is unavoidable — accept it,
  make the notification look intentional, not apologetic.
- Bubble is **draggable**, snaps to screen edge, remembers position
  per orientation.

### 3.2 Two modes, mapped to two gestures

The desktop app has F8/F9/F10. On the phone we collapse to two — the
ones that actually matter for mobile dictation:

| Mode | Gesture | What it does |
|---|---|---|
| **Narrate** | single tap bubble | Speak → transcribe → insert raw text into focused input |
| **Construct** | long-press bubble (~400ms) | Speak a brief → LLM elaborates into a full reply/message/post |

No "drafting" mode separate from construct. No "advanced cleanup"
mode separate from narrate. Two modes, two gestures, done.

### 3.3 Audio capture — survive the 2-minute problem

The chronic bug across every Android voice-typing tool: speak for
>2 min and it fails silently. Root cause is usually a combination of:
- Speech recognizer service kills the mic after a timeout
- App goes to background → OS aggressively releases mic
- Network upload happens at the end instead of streaming

Our approach:

- **Direct mic access** via `AudioRecord` (PCM 16kHz mono), NOT
  `SpeechRecognizer`. We control the lifecycle.
- **Foreground service with `microphone` type** keeps mic held while
  user's focus is on another app (Slack, WhatsApp, Gmail).
- **Hard cap at 10 min** per recording. Beyond that, auto-segment
  and queue. (Whisper API itself takes up to 25MB per file ≈ ~30min
  of 16kHz mono; we cap shorter for latency.)
- Visual countdown in the bubble after 1 min so the user isn't
  surprised when it auto-segments.

### 3.4 Network drop tolerance — offline-first queue

User's stated pain: "lot of network drops happen there." Indian mobile
network reality. Our handling:

- **Audio always written to disk first** (`/Android/data/<pkg>/files/audio/`).
- Transcription enqueued. If network is up → upload immediately.
  If down or fails → retry with exponential backoff (5s, 30s, 2min,
  10min) until success or user-aborted.
- Queue is **WorkManager-backed** so it survives app kills + reboots.
- User sees a small queue indicator in the bubble: `2 pending`. Tap
  for queue view (delete, force-retry).
- **Result delivery**: even if user has moved to a different app,
  finished transcription is offered via a notification + auto-paste
  if the destination is still focused; otherwise copied to clipboard
  with a toast.

### 3.5 Audio retention

- All raw recordings kept on device for 7 days (configurable).
- 500MB rolling cap (oldest deleted first).
- User can disable retention (live-only mode).
- Files are PCM WAV, ~30MB/hour. Roughly 16 hours of dictation fits
  in 500MB.

### 3.6 Provider stack — same as desktop

- **STT**: Groq Whisper (`whisper-large-v3-turbo`), default.
- **LLM** (for Construct mode): Groq Llama 3.3 70B or Gemini 2.5 Pro.
- API keys stored via **Android Keystore** (hardware-backed on S20+).
- Same `language_hint: null` default — let Whisper auto-detect for
  the user's English/Hindi code-switching.

**Port the request shapes verbatim** from the desktop project:
- Whisper multipart shape: `D:\Claude Code Projects\wispr-fox\src-tauri\src\stt\groq.rs`
- Groq chat completions: `…\src-tauri\src\llm\groq.rs`
- Gemini generateContent: `…\src-tauri\src\llm\gemini.rs`
- Prompt templates + injection-defense wrapper: `…\src-tauri\src\llm\prompts.rs`

For v1, Groq alone is sufficient. Gemini can come in v2.

### 3.7 Permissions — onboarding flow

Explicitly walk the user through each one, with screenshots:

1. **`RECORD_AUDIO`** (runtime) — required, no fallback.
2. **`SYSTEM_ALERT_WINDOW`** (settings deep-link) — required for bubble.
3. **`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`** (manifest) — automatic on install.
4. **`POST_NOTIFICATIONS`** (runtime, API 33+) — for queue status.
5. **`INTERNET`** (manifest) — automatic.
6. **Battery optimization exception** (settings deep-link) — strongly
   prompted, not enforced. Without this, OEM (Samsung especially)
   will kill the foreground service in the background.

Onboarding cannot be skipped past a permission. Either grant or quit.

---

### 3.8 V1 minimum settings surface — keep it brutally small

The user's directive for v1 is *"I would just want it to work."*
The settings screen for v1 contains exactly these controls and
nothing else:

1. **Groq API key** — paste field + test button + masked display
2. **Permissions status panel** — one row per required permission
   (mic, overlay, notifications, battery optimization), each with
   a deep-link button to the OS settings page if not granted
3. **Activation gesture** — single-tap vs long-press mapping to
   Narrate vs Construct (defaults match PRD §3.2; the toggle exists
   only because someone will inevitably want to swap them)
4. **Audio retention** — toggle (on/off) + a single slider for days
   kept (default 7, range 0–30)
5. **About** — version, link to GitHub, link to bring-your-own-key
   provider setup guide

That's the entire settings screen. The following are explicitly
**deferred to v2 or later**:

- Provider switching UI (Gemini, custom endpoint)
- Per-mode prompt editor
- Audio cue customization
- Theme picker (use system light/dark, no override)
- Usage / token-spend dashboard
- Bubble appearance customization
- Queue inspector UI (basic notification suffices)

Backend services that DO need to exist on day one (regardless of UI):

- Foreground service holding the mic with `microphone` service type
- `AudioRecord` PCM-16kHz-mono → WAV writer
- WorkManager queue with retry policy (5s, 30s, 2min, 10min)
- Keystore wrapper for API key storage
- Room DB indexing queued + completed recordings
- Rolling-retention file cleanup (default 7d / 500MB cap)
- Single `TranscribeWorker` doing Whisper → optional LLM cleanup → deliver

UI for inspecting / managing those services is v2.

## 4. Scope — what we are NOT building

These are deliberate exclusions. Don't drift.

- ❌ **No Clippy character.** No mascot, no animations, no skins.
  The desktop app's personality is the desktop app's. Mobile is utility.
- ❌ **No IME / keyboard replacement.** Overlay button only.
- ❌ **No themes.** System dark/light only. No retro, no custom.
- ❌ **No history UI as a primary feature.** Recordings are debug
  artifacts, not a journaling product. Bury the list in settings.
- ❌ **No sharing / export of recordings.** Audio files exist for
  retry on failure, not as a feature.
- ❌ **No on-device Whisper.** We tested — `whisper.cpp` tiny.en
  on a Note 10+ is 0.5x realtime for a 30-second clip. Useless for
  long-form. Cloud only.
- ❌ **No tablet-specific layout.** Phones only.
- ❌ **No wearable companion.** Phone only.
- ❌ **No "always listening" wake word.** Tap the bubble.
- ❌ **No Android < 12 support.** Permission UX is too inconsistent
  before 12.

---

## 5. Tech stack

- **Kotlin + Jetpack Compose** for UI. (Native, not Flutter / not
  RN — overlay + foreground service work is the bulk of the app
  and benefits from staying on platform.)
- **Compose for the bubble overlay too** — yes, this works since
  Compose 1.6. Avoids the old View-system overlay code.
- **OkHttp + Coroutines + Flow** for networking.
- **WorkManager** for the offline queue.
- **DataStore (Preferences)** for settings.
- **Room** for queue + retention metadata (NOT recordings themselves
  — those are files on disk, Room just indexes them).
- **AudioRecord** + raw WAV writer (port the desktop `hound`-equivalent
  logic).
- **Android Keystore** for API keys.
- No Hilt — manual DI is fine for an app this size.

---

## 6. Architecture sketch

```
┌──────────────────────────────────────────────────────┐
│  MainActivity (settings, onboarding, history)        │
└──────────────────────────────────────────────────────┘
              │ starts/stops
              ▼
┌──────────────────────────────────────────────────────┐
│  OverlayService (foreground, type=microphone)        │
│  ├─ BubbleView (Compose overlay)                     │
│  ├─ AudioRecorder (AudioRecord → WAV)                │
│  ├─ QueueManager (WorkManager dispatch)              │
│  └─ ResultDelivery (paste / clipboard / notif)       │
└──────────────────────────────────────────────────────┘
              │ enqueues
              ▼
┌──────────────────────────────────────────────────────┐
│  TranscribeWorker (WorkManager)                      │
│  └─ GroqClient.transcribe(wav) → text                │
│      └─ (if construct mode) → LlmClient.elaborate    │
└──────────────────────────────────────────────────────┘
```

---

## 7. Success criteria

We ship v1 when:

- [ ] Note 10+ can dictate continuously for 10 minutes without mic loss
- [ ] Recording survives WiFi → cellular handoff mid-dictation
- [ ] Queue retries succeed after airplane-mode toggle
- [ ] Indian-accented English + Hindi code-switch produces sensible
      transcription (subjective: user accepts >80% of outputs
      without major edit)
- [ ] Bubble persists across 10 app switches without OS killing it
      on S23 Ultra in normal battery mode
- [ ] Total install size < 30MB
- [ ] Cold-launch to ready-to-record < 1s on S20

---

## 8. Open questions (for later, not now)

- Auto-paste into focused input field — how reliable is
  `AccessibilityService`-based injection? Worth the permission cost?
  (User may prefer clipboard-only for v1 to keep permission surface small.)
- Streaming Whisper (Groq doesn't expose it yet; OpenAI does).
  Worth adding once available — would let user see partial transcripts
  while still speaking.
- Construct-mode prompt customization (desktop has this for F10).
  v1: hardcoded. v2: editable.

---

## 9. Out of scope for this PRD

- Specific UI mockups (separate doc).
- App Store listing copy (separate doc, once shipped).
- Monetization. (Mirrors desktop: free + open source, bring your
  own API key.)
