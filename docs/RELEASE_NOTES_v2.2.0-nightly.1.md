# wispr-fox for Android — v2.2.0-nightly.1

This is the first Android meeting-workflow build. It also reshapes the app around
the four things you actually do: **Speak, Upload, History, and Usage**.

## Meetings arrive properly

- Uploads can turn on speaker labeling. Foxy automatically uses a compatible
  Deepgram Nova model and explains the switch instead of failing later.
- Raw, Clean, Draft, and Meeting Notes remain separate outputs. Meeting Notes
  are deliberately brief: decisions, important context, and next actions.
- Meeting recordings have a calm tinted History card, versioned speaker turns,
  editable speaker names, and a focused full-screen reader. Renaming a speaker
  updates reading and copied text without retranscribing.
- Re-run is one dependency-ordered recipe: transcription and speaker labels
  first, then Clean, Draft, and Meeting Notes with the current model defaults.

## Share recordings straight from Android

Share one or several audio files from Samsung Voice Recorder, Files, or another
audio app to **wispr-fox**. Foxy immediately copies the granted content into its
own storage, opens Upload with the files preloaded, and shows every output and
model choice. Multiple files can be reordered, processed separately, or joined
locally into one recording in the selected order.

## Faster recovery and easier reading

- Recent shows three transcript lines. Hold to copy; tap to open that exact row
  expanded in History; Back returns to the preserved Speak screen.
- Expanded History rows no longer collapse when recycled during a long scroll.
- Copy is the primary row action; Play, Re-run, and Delete share one compact row.
- Long cleanup blobs are visually segmented without changing the words, and the
  full-screen reader is available for every output.

## A denser, clearer phone app

- Compact bottom navigation is now Speak, Upload, History, Usage. The duplicate
  Speak button and Home usage/import clutter are gone; Foxy itself remains the
  tap-to-speak control.
- Settings has one compact **AI & models** page with independent defaults for
  transcription, speaker labels, cleanup, titles, and stronger Draft/Meeting
  generation. Model cards on Speak link directly there.
- Samsung setup now names the exact path: Accessibility → Installed apps →
  wispr-fox → On → Allow, while showing live on/off status.
- Storage reports real audio file count and bytes and can clear audio while
  retaining text. About checks both stable and nightly releases and opens the
  corresponding GitHub download page.
- Foxy gains the existing desktop Explorer Fox and Spark Buddy packs, and a
  compact home-screen widget provides Speak and Upload shortcuts. The widget is
  explicit tap-to-record; it adds no always-listening behavior.

## Please test before stable

This is a nightly built from green debug compilation and unit tests, but it still
needs S23 Ultra device QA. Please exercise Samsung accessibility setup, Voice
Recorder sharing (single, multiple, and merged files), a long diarized meeting,
speaker renaming/copying, the full-screen reader, widget start/stop, in-place APK
update/signature behavior, and stable/nightly update links. Stable v2.1.0 remains
the recommended build until those journeys pass on-device.
