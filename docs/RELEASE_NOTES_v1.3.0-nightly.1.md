## wispr-fox Android v1.3.0-nightly.1 — Fable build

This nightly is all about making the core promise reliable: tap, speak, and the words land in the box — every time, not most times.

### The fox now behaves
- The floating fox appears when the keyboard is up and leaves when it's dismissed — and it no longer gets stuck on screen. Keyboard detection is smarter (Samsung's ghost keyboard windows don't fool it anymore) and self-heals if it misses a transition.
- If the accessibility service is off, the fox now stays out of the way instead of squatting on your screen forever. The app's home screen tells you when that's the case and takes you straight to the switch — accessibility is what makes both the appear-with-keyboard trick and auto-paste work.
- A slow network can no longer hold the fox (and your next dictation) hostage. If transcription needs to retry, the fox frees up immediately, you can keep dictating, and the finished transcript arrives as a notification with the text already on your clipboard.

### Paste that actually pastes
- Auto-paste now finds the text box the robust way (across app windows, with retries over ~1.5 seconds) instead of a single fragile attempt. The "worked yesterday, not today" flakiness should be gone.
- When auto-paste genuinely can't land (you switched apps mid-transcription, for example), you get a notification instead of silence — the text is on your clipboard, one long-press away.

### Models that exist
- Fixed a sneaky one: the "Llama 4 Maverick" cleanup option pointed at a model ID Groq doesn't serve, so Clean/Draft silently gave you raw text. Replaced with the real Llama 4 Scout. If you had it selected, you're moved to the default automatically.
- Gemini gets its current line-up, with Gemini 3.5 Flash as the new default. ElevenLabs moves fully to Scribe v2 (v1 retires upstream this week). Stale selections migrate themselves.

### New faces and sizes
- **Oru & Gujia** — the two-cat team from the desktop app, same art, fully state-animated.
- **Siri-style orb** — a minimal, Apple-like glowing button for when you want zero personality and all business.
- The black paperclip and the watercolor fox are still here, and you can now size the floating avatar: Small, Medium, or Large.

### Usage at a glance
- Home now shows today's usage for your active speech and cleanup models — with green/amber/red meters against Groq's free-tier limits, a Deepgram credit tracker (estimated spend against the $200 free credit), and the exact local time your daily quota resets.

### One-time housekeeping
- Release builds are moving to a permanent signing key so future updates install **in place** — no more uninstall-reinstall, no more re-enabling accessibility after every update. Moving onto the new key takes one last uninstall → install of this build. After that, you're done with that ritual for good.
