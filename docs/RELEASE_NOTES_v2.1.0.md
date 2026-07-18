# wispr-fox for Android — v2.1.0

The first stable on the accounts line. Since v1.4.0, the phone app gained an
optional account that syncs across your devices, a full visual redesign with
a dark theme, and delete behavior that matches the desktop and web apps.

## Your dictation, on every device

Sign in (optional — Google or email/password) and your transcripts and API
keys travel with you across the phone app, the desktop app, and the web app.
Set your keys once; every signed-in device has them. **Your audio never
leaves the phone** — only the text syncs. Signed out, nothing changes: BYOK,
no account, no telemetry, exactly as before.

## A real redesign — and a dark theme

Every screen was rebuilt on one consistent spacing and type scale. Three
palettes carried over from the desktop (Foxy cream / Dark / Retro) plus an
Auto option that follows your system — Android used to ship a single light
theme. Settings became a clean hub-and-spoke: eight rows, each opening its
own page and showing its current value, instead of one long flat scroll.

## Delete now means "mine"

The old delete dialog asked a lot — this device or everywhere, the voice file
or the text. It's one rule now: **you can delete what this phone recorded,
and nothing else.** Deleting a transcript takes its recording with it.
Transcripts synced from your desktop or the web app still show in your
history, but there's no delete on them here — they belong to those devices.
"Delete all" clears only what this phone made.

## Purge — the clean-slate button

That rule left a hole: a transcript from a device you no longer have — an old
laptop, a reset phone — could never be deleted, because no remaining device
owns it. **Settings → Account → Purge** fixes it. Purge wipes every
transcript on your account, across every device, including ones you no longer
have. Your other devices clear their copies on their next sync. It asks twice
— hold, then confirm — because it reaches everywhere and can't be undone. Try
it deliberately the first time.

## Reliability fixes carried in from the v2.x nightlies

- The overlay fox no longer drifts sideways when its menu, bubble, or label
  appears — the fox centre is the persisted anchor.
- A failed transcription no longer wedges the pipeline; recording stays
  usable.
- Auto-paste stops falsely reporting success — it verifies the text actually
  landed before saying "Pasted."

---

*One thing to know: if you sign this phone into an account that's already been
purged, its local history is wiped to match — that's the reset doing its job,
not a bug.*

*wispr-fox is three apps sharing one backend: this Android app, the
[desktop app](https://github.com/kumaradarsh1993/wispr-fox), and the
[web app](https://wispr-fox-web.vercel.app). They all share the delete and
purge behavior above.*
