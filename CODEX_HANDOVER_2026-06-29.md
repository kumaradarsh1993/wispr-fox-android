> ## 🗄 ARCHIVED — historical record only (do not action)
>
> The Codex agent is retired. This is a **dated 2026-06-29 checkpoint** kept for
> the audit trail: the owner's original prompt and what Codex changed on the way
> to the `v1.2.0-codex.2` preview. **Everything below is superseded** — that
> preview was overtaken by Claude stable `v1.4.0` and the `v2.x` accounts/sync
> line. For current state read [HANDOVER.md](./HANDOVER.md); nothing still-live
> is unique to this file. Do not treat its "Follow-up" list as open work.

# Codex Handover - 2026-06-29

## User prompt that triggered this checkpoint

The owner asked Codex to continue after the desktop wispr-fox work and then handle the Android sibling:

> Once you're done with this, you would find that there is another project in there called Whisper Vox for Android. And also we might want to do a thorough audit, increasing the same number of things that we did for this Whisper Vox, right? Which is Deepgram, etc., etc., integrations, checking whatever APIs we are using there are up to date, doing a full-on audit to be compatible with something like S23 Ultra as of June 2026. And yeah, things where you think might break, might not have been implemented correctly, and also the avatars, SDK, and the kind of avatar that is going in there. Even the avatar size, scaling, and the nuances sort of to messaging, etc., etc., that has been implemented. No, I think that you can leave, but I think the scaling is important, and the accessibility and all the permissions that are required, that working fine, copy paste always working, and the sort of insert and things always working, right? All of those cases are done, and then commit it, and create an executable same Android APK should be created virtually on GitHub so that anyone can download and then create a landing page for it as well, please. i mean github landing page with well formatted readme
>
> commit existing project first - if not already existing
> then do changes adn commit a v0.2 (or whatever)
> and a handover document to claide code or anyobne picking it up for full build - i am thinking i will spinoff another session with you to dedicatedly work on it

## Baseline before Codex Android edits

- Repo: `D:\Claude Code Projects\wispr-fox-android`
- Worktree was clean.
- Head before Android edits: `a219c5a Fix landing page scroll demo`
- Existing public stable: `v1.1.0` at `4cf396b`
- There was no uncommitted baseline to commit before starting.

## What Codex changed

- Added runtime STT providers: OpenAI, Deepgram, ElevenLabs, alongside existing Groq.
- Added OpenAI cleanup via the Responses API, alongside existing Groq/Gemini cleanup.
- Added a shared provider/model catalogue so Settings and Home do not hard-code Groq-only choices.
- Extended secure key storage with per-provider key entries.
- Updated onboarding to accept any STT key and set the matching provider.
- Updated Settings with provider/model pickers and key fields.
- Added safer delivery behavior:
  - Capture focused editable package at recording start.
  - Auto-paste only if the focused editable package still matches at delivery.
  - Mark clipboard fallback as sensitive.
- Hardened recording startup:
  - Preflight microphone permission.
  - Catch foreground-service/audio startup failures.
  - Use a partial wake lock while recording.
- Honored the overlay enabled/disabled setting.
- Added basic avatar/overlay accessibility semantics.
- Added Room migration v1 -> v2 for `target_package`.
- Added GitHub Actions workflow to build/test/sign/upload APKs on `v*` tags.
- Updated README and GitHub Pages landing download metadata.
- Rewrote HANDOVER.md to make the Codex preview vs Claude stable split explicit.

## Why this is a Codex preview, not stable

The code compiles, unit tests pass, and GitHub Actions published `v1.2.0-codex.2` as a prerelease. No physical S23 Ultra QA was run in this session. The APK used a temporary Codex preview signing key because repository signing secrets were not configured, so treat this as a preview release until real-device install, permission, recording, paste, and provider-key paths are validated.

## Verification run

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat testDebugUnitTest assembleRelease
```

Result: passed locally. GitHub Actions run `28379653886` also passed and published `wispr-fox-android-v1.2.0-codex.2.apk` with SHA-256 `e3316147f974e404b6a207e52f8c9f4ef798056dd67023cdef284be6a4bf2c48`.

## Follow-up for Claude or another Codex session

1. Install the APK on a real S23 Ultra or current Samsung device.
2. Configure persistent Android signing secrets in GitHub before stable promotion.
3. Do the S23 Ultra matrix in `HANDOVER.md`.
4. If the preview signing key causes an upgrade failure, uninstall the older APK first or rebuild after adding persistent signing secrets.
5. If avatar polish becomes the next focus, port the desktop raster-pack manifest idea rather than only scaling the current PNG/Compose avatar.
