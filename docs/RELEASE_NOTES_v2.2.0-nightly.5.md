# wispr-fox Android v2.2.0-nightly.5

`versionName = "2.2.0-nightly.5"` · `versionCode = 22` · channel: nightly

**Phone transcripts now reach your other devices.**

## What was wrong

The phone sends its finished transcripts to the sync service in one batch.
The sync service refuses a batch unless every transcript in it lists the same
fields. The app left out empty fields, so a batch that mixed, for example, a
transcript with a title and one without was refused (HTTP 400, "All object keys
must match"). The app ignored the refusal without saying anything, kept the
transcripts queued, and sent the same refused batch on every sync. One mixed
batch blocked every phone transcript after it.

The phone could still download the laptop's history, because downloading was
never affected. That is why sync looked fine in one direction only.

## The fix

- Every transcript in a batch now lists every field, with empty ones sent as
  empty. The sync service accepts the batch.
- Each upload is stamped with the time it was sent, not the time the
  transcript was last edited (the desktop already does this). Without this, a
  transcript uploaded late could carry a time older than the point other
  devices had already read up to, and they would skip it.
- A refused upload is now written to the Android log instead of being ignored.

Transcripts that were stuck upload on the first sync after updating. Nothing
needs to be redone.
