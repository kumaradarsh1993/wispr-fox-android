# THETA_MEMORY — wispr-fox Android (append-only)

Detailed log of what was done, why, and what it changed. Add new entries at the bottom; never rewrite old ones. `HANDOVER.md` is the current state; this is the history. The fuller cross-repo log is `../wispr-fox/THETA_MEMORY.md`.

## 2026-11-03 — v2.3.0 stable, then the v2.4.0-nightly.1 revamp

- **Inputs:** Adarsh's dictated brief (verbatim in `../wispr-fox/docs/briefs/2026-11-03 brief - sync check and Android revamp.md`). He wanted: the phone rebuilt around Voice Recorder uploads; dictation into other apps made opt-in because banking apps warn; a compact single-screen UI; Upload presets with the newest recordings; both transcripts copied for meetings; History actions on one row with Refresh; and cleanup of copied files.
- **Stable first:** v2.3.0 = v2.3.0-nightly.1 unchanged (tag on branch `conversations`). Published as Latest; release notes corrected after publishing to say stable.
- **Decisions:**
  - Banking-app warnings come from the enabled accessibility service, so the switch calls `disableSelf()` rather than only hiding the fox. A package deny list keeps the fox off banking apps even with the switch on.
  - Conversations folds into History as a Notes / Conversations switch, so the bottom bar matches the brief (Speak, Upload, History, Settings).
  - Preset ids reuse the existing `UploadKind` raw values (voice_note, conversation), so a saved choice survives the update. CUSTOM is new.
  - Recent recordings come from MediaStore with `READ_MEDIA_AUDIO`, not a SAF tree, so no file is copied until it's picked.
  - Text scaled to 90% globally rather than per screen, so every screen shrinks consistently.
- **Sync root causes found:** own-row echo overwrite (`pullNotes` → `applyRemoteNote` replacing owned rows as remote, no audio). Conversations stuck un-`done` while a retry was pending. Strict `updated_at > cursor` pull.
- **Effects:** phone rows stop going read-only after their first sync, speaker names sync out, and Rerun keeps its audio. Refresh (`reconcile`) repairs rows damaged before the fix.
- **Process notes:** no JDK on the Mac, so GitHub Actions `workflow_dispatch` on `revamp` is the compile check. Its "Publish GitHub release" step always fails on branches. One compile error (a duplicated named argument from an over-broad text replace) was caught by CI and fixed in `8fc2a28`.
