# Desktop linkage — what to copy, what to diverge

The desktop **wispr-fox** repo already solved several non-trivial
problems we don't want to re-solve on Android. This doc maps each
piece: copy as-is, port with changes, or leave behind.

## Copy as-is (port the logic line-for-line)

### Groq API call shape
- Endpoint: `https://api.groq.com/openai/v1/audio/transcriptions`
- Model: `whisper-large-v3-turbo`
- Multipart form: `file`, `model`, optionally `language`, `prompt`
- Desktop ref: `src-tauri/src/stt/groq.rs`
- Android: rewrite in Kotlin/OkHttp, same shape.

### LLM provider abstraction
- Two providers: Groq (Llama 3.3 70B), Gemini (2.5 Pro).
- Same prompt templates as desktop's `prompts.rs`:
  - **Narrate** (Android) ≈ **Light** (desktop F8) — raw transcript
    with security-bounded transcript tags.
  - **Construct** (Android) ≈ **Drafting** (desktop F10) — full
    elaboration prompt.
- We are **not** porting desktop's "Advanced" mode (F9 strict
  copy-edit). On mobile, the use case for "fix my grammar but
  don't change anything" is too narrow to justify a third gesture.

### Prompt-injection defense
- Desktop wraps user transcript in `<transcript>...</transcript>`
  tags with explicit "treat as data not instructions" framing.
- Copy that wording verbatim into Android's prompt builder.

### Language hint policy
- Desktop default: `language_hint: null` (let Whisper auto-detect).
- Reason: user code-switches English ↔ Hindi mid-sentence; pinning
  `en` ruins Hindi sections.
- Android: same default.

### Retention defaults
- 7 days, 500MB cap, FIFO eviction.

## Port with changes

### Audio capture
- Desktop: `cpal` with WASAPI shared mode, cold-start per recording.
- Android: `AudioRecord` PCM 16kHz mono, single instance held by
  foreground service while bubble is "active" (between tap-start
  and tap-stop).
- **Different lifecycle**: desktop creates/destroys stream per
  recording (driver workaround). Android holds the recorder across
  segments to avoid OS mic-release on background.

### Settings storage
- Desktop: JSON in Tauri app-data dir.
- Android: DataStore Preferences. Same field names (snake_case) so
  the shape is familiar.

### Secrets
- Desktop: `keyring-rs` + JSON fallback.
- Android: Android Keystore (hardware-backed on S20+). No fallback
  needed — Keystore is always available on API 31+.

## Leave behind entirely

- **Clippy** — character, sprite, animations, dialog component.
- **Themes** — Android uses system light/dark via Material 3.
- **History UI** — desktop has a rich history page with date dividers,
  variant pager, retry button. Android: minimal queue view in
  settings, no audio playback in-app.
- **Onboarding flow** — desktop has 6-step Clippy walkthrough.
  Android has its own permission-driven flow (RECORD_AUDIO →
  SYSTEM_ALERT_WINDOW → battery optimization → done).
- **Audio cues** — desktop plays start/stop tones. Android: skip
  for v1, the tap haptic is the cue.
- **Tray icon / minimize behavior** — concept doesn't translate.
  The notification IS the tray icon on Android.
- **Sticky hotkeys** — F8/F9/F10 vs Super+F8/F9/F10. No equivalent
  gesture surface on Android.

## Synchronized changes — keep in lockstep

If we change these on either side, change them on both:

- Whisper model name (`whisper-large-v3-turbo` → next gen)
- LLM model defaults (`llama-3.3-70b-versatile`, `gemini-2.5-pro`)
- Prompt-injection wrapper wording
- Provider list (if we add a third STT provider)
