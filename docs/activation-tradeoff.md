# Activation surface — trade-off matrix

> **Status:** Phase 0 deliverable. Awaiting user sign-off before any
> activation code lands.

## What we're picking

How does the user trigger a recording from anywhere on the phone? The
PRD's leading hypothesis is a floating overlay bubble; this doc evaluates
that against five alternatives so we make the choice on evidence rather
than vibe.

## Candidates

1. **Overlay bubble** — `SYSTEM_ALERT_WINDOW` + foreground service. A
   draggable Compose surface drawn over every app.
2. **Quick Settings tile** — `TileService`. User swipes down, taps our
   tile, recording starts.
3. **Assistant intent** — register as the device's default voice
   assistant via `VoiceInteractionService`. Long-press home or power
   button → us.
4. **Share target** — user selects a text field, opens share sheet,
   picks "wispr-fox". 2–3 taps per use.
5. **AccessibilityService observation** — observe the focused input,
   show a small action when the user wants to dictate.
6. **Hardware-key mapping** — bind to Samsung side-key long-press or
   Pixel-style assistant key.

## Axes that matter

| Axis | Why it matters |
|---|---|
| Permission cost | High-stigma permissions (overlay, accessibility) reduce install rate and create ongoing trust friction. |
| Play Store risk | A rejection cycle costs weeks. As of **Jan 28 2026** Google enforces a stricter `AccessibilityService` policy — autonomous actions are prohibited unless the app is a verified accessibility tool. |
| Friction per use | Number of gestures from "want to dictate" to "speaking". Sub-1s matters. |
| In-app context | Does the trigger preserve the focus on the text field the user was about to type into? Critical for paste-back. |
| Device coverage | Works on all 5 target devices (Note 10+ → S26)? |
| Ongoing notification | The Android-mandated FG-service notification can't be hidden. Adds permanent UI clutter for some surfaces. |
| Dev cost | Engineer-days to ship cleanly. |

## Matrix

| | Overlay bubble | QS tile | Assistant intent | Share target | Accessibility | Hardware key |
|---|---|---|---|---|---|---|
| **Permission cost** | High (overlay + FG service notif) | Low | High (set as default assistant) | None | **Very high** (post-Jan-26 policy) | Low |
| **Play Store risk** | Medium | Low | Low | Low | **High** | Low |
| **Friction per use** | 1 tap | 2 (swipe + tap) | 1 (long-press hard key) | 3 (select → share → pick) | Variable | 1 (key press) |
| **In-app context** | Yes (floats over) | Lost (shade overlays app) | Lost (assistant takes over) | Yes (returns to field) | Yes (observes field) | Lost (app launch) |
| **Device coverage** | All | All | All | All | All | Partial (no Bixby key on most modern Galaxies) |
| **Ongoing notification** | Yes (permanent) | Only while recording | Only while recording | Only while recording | No | Only while recording |
| **Dev cost** | High (Compose-in-WindowManager, drag, edge-snap, tap-vs-long-press) | Low | Medium | Low | Medium | Low |

## Key findings from research

- **TileService can start a foreground microphone service.** Tile-click
  counts as user-initiated, so the Android 14+ "no FG mic service from
  background" rule does not block this path
  ([restrictions doc](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)).
  Requires the same manifest decl as overlay (`foregroundServiceType="microphone"`).
- **Samsung One UI 6+ honours FG service types** when the app follows
  Android 14 API rules; battery-optimisation exemption is still required
  for survival through the "put unused apps to sleep" reaper
  ([Don't kill my app — Samsung](https://dontkillmyapp.com/samsung)).
  This is platform-level news that applies to *any* activation surface
  that records audio.
- **AccessibilityService policy tightened materially in Jan 2026**
  ([Play Console policy update](https://support.google.com/googleplay/android-developer/answer/16550159?hl=en)).
  Autonomous actions are now prohibited unless the app is a verified
  accessibility tool with `isAccessibilityTool="true"`. A dictation app
  *can* plausibly claim this — speech-to-text is bona fide assistive
  tech for users with motor or cognitive disabilities — but it requires
  positioning the app that way in store copy + a stricter review cycle.
- **Bixby key remapping was deprecated in 2020**; modern Galaxies don't
  expose a generic third-party hardware-key bind. Side-key long-press
  exists but is the OEM's, not ours to claim.

## Cost-of-being-wrong analysis

The activation surface is the **only** thing the user touches directly.
Getting it wrong means rebuilding the trigger end-to-end — the audio
core, queue, and provider clients are insulated from this choice. So
the cost is "redo Phase 5 and the onboarding flow", not the whole app.
That's ~2–3 days of work to redo, which should reduce conservatism in
the pick.

## Recommendation

**Primary: Quick Settings tile. Optional opt-in: overlay bubble.**

Reasoning:

1. **Lowest permission cost path that still hits day-one shipping
   bar.** No `SYSTEM_ALERT_WINDOW`, no accessibility, no assistant
   handoff. Just `RECORD_AUDIO` + `POST_NOTIFICATIONS` + FG service
   declarations + battery-opt exemption (which is needed regardless of
   activation surface).
2. **One swipe + one tap is acceptable** for the dominant use case
   (dictation, not a millisecond-critical reflex). The user already
   habitually opens the shade for notifications.
3. **The overlay bubble path stays available as an opt-in for power
   users** who want the in-app affordance. Add it in v1.1 after
   shipping if demand exists; for v1, the tile alone passes the "I
   would just want it to work" bar.
4. **In-app context loss is mitigated** by clipboard-based delivery
   (the v1 default we already picked): after recording, text is on
   the clipboard, user goes back to their app, long-press paste.

The PRD's overlay-bubble hypothesis is **not wrong** — it's the
correct second-step UX. But starting with it for v1 pays a heavy
permission cost (overlay grant + permanent FG notif) for a feature
that doesn't pull its weight until the user is already converted.

## Open question for the user (revisits v1 scope decision)

**The Jan 2026 Play Store policy materially raises the cost of the
AccessibilityService opt-in path you chose for auto-paste.** Three
options:

1. **Keep accessibility opt-in, position app as accessibility tool.**
   Add `isAccessibilityTool="true"`, write Play Store copy framing
   wispr-fox as assistive tech for users with motor / cognitive /
   speech challenges. Accept the stricter review cycle.
2. **Drop auto-paste from v1.** Clipboard + notification only, no
   accessibility surface. Defer auto-paste to v2 when Play Store
   policy state is clearer or app has user weight to justify review.
3. **Drop the tile primary in favour of overlay bubble + accessibility
   auto-paste,** treating wispr-fox as a power-user tool from the
   start. Heavier permission ask, but you get the "floating over my
   current app + auto-paste back into it" experience.

Sign-off needed on:
(a) activation surface — confirm QS tile primary, overlay optional
later; or pick different;
(b) the accessibility / paste question above.

---

## Sign-off (2026-05-11)

User signed off on:

- **Activation:** Both surfaces in v1. Quick Settings tile is the
  default activation. Overlay bubble is opt-in via a settings toggle
  that requests `SYSTEM_ALERT_WINDOW`.
- **Distribution:** Sideload only. APK from GitHub releases. No Play
  Store submission planned. This makes the Jan 2026 Play Store
  accessibility policy moot — `AccessibilityService` auto-paste is
  freely usable as an opt-in toggle in v1.

Phase 0 complete.
