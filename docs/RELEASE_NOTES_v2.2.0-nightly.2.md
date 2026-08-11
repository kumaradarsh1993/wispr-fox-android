# wispr-fox for Android — v2.2.0-nightly.2

This supersedes nightly.1 with three reliability corrections found in the final
release audit. The meeting, share-target, focused-reader, compact navigation,
model-default, avatar, and widget features from nightly.1 are all retained.

## What nightly.2 fixes

- **Update identity is exact.** About now shows the installed nightly channel
  and sequence. Update checks compare prerelease sequences correctly, so
  nightly.2 is newer than nightly.1, while a final v2.2.0 correctly outranks
  either nightly.
- **Provider readiness matches real execution.** Upload and History now use the
  same credential resolver as the provider factory, including OpenAI's speech ↔
  LLM key fallback and Groq's LLM fallback. A rerun with a missing key is blocked
  with the exact requirement, and any text-only Clean/Draft/Meeting failure is
  shown on the History row instead of disappearing silently.
- **Shared audio cannot escape Storage.** App-owned files already handed to an
  import worker remain included in file/byte totals. Clear audio first cancels
  every imported decode/transcription chain, then removes visible staged files,
  queued sources, merge cache, and generated WAVs. Interrupted rows are marked
  clearly instead of looking permanently active.

## Please test before stable

Stable remains v2.1.0. On an S23 Ultra, please verify an in-place update from
nightly.1, About/update-channel reporting, OpenAI single-key fallback, a
text-only History rerun failure, and Clear audio during both queued and active
multi-file imports. Then continue the full nightly.1 meeting/share/reader/widget
device checklist before considering v2.2.0 stable.
