# wispr-fox for Android — v2.1.0-nightly.2

This build brings Android's delete in line with the desktop and web apps, and
adds a way to wipe your whole account clean.

## Delete now means "mine"

The old delete dialog asked a lot — this device or everywhere, the voice file
or the text. It's one rule now: **you can delete what this phone recorded, and
nothing else.** Deleting a transcript takes its recording with it.

Transcripts synced from your desktop or the web app still show in your history,
but there's no delete on them here — they belong to those devices. "Delete all"
clears only what this phone made; your other devices are untouched.

## Purge — the clean-slate button

There was a hole in that rule: a transcript from a device you no longer have —
an old laptop, a reset phone — could never be deleted, because no remaining
device owns it.

**Settings → Account → Purge** fixes that. It wipes every transcript on your
account, across every device, including ones you no longer have. Your other
devices clear their copies on their next sync. It asks twice — hold, then
confirm — because it reaches everywhere and can't be undone.

## Please keep an eye out

This is a nightly, and the delete and purge round-trips haven't been tried
against a live account yet — only the automated tests and the build have run.
Purge is worth a careful first try, since it's meant to reach every device.

One thing to know: if you sign this phone into an account that's already been
purged, its local history is wiped to match — that's the reset doing its job,
not a bug.
