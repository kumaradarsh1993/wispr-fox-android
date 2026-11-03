# wispr-fox Android v2.4.0-nightly.1

`versionName = "2.4.0-nightly.1"` · `versionCode = 26` · channel: nightly

**The app is rebuilt around turning Voice Recorder files into transcripts. Dictating into other apps becomes something you switch on.**

## Dictate into other apps: off by default

- A **Dictate into other apps** switch sits at the top of Speak.
- **Off:** the floating fox stops, and wispr-fox switches its accessibility service off, as if you'd done it in system settings. Banking apps check for exactly that, which is why ICICI iMobile warned. With the switch off, they no longer see wispr-fox.
- **On:** if accessibility is off, the app opens the accessibility settings page so you can turn it on.
- Even with the switch on, the fox never draws over banking and payment apps (ICICI, HDFC, SBI, Axis, Kotak, Paytm, PhonePe, Google Pay, CRED and others).
- The Quick Settings tile and the home-screen widget still record and copy to the clipboard with the switch off.

## Leaner look

- Text is about 10% smaller (body text 16 → 14.4 sp), with tighter spacing, smaller icons and a slimmer bottom bar.
- The bottom bar is now **Speak, Upload, History, Settings**. Conversations is a Notes / Conversations switch at the top of History. Usage is inside Settings.
- Speak is one screen with no Recent list.

## Upload

- Your **3 newest recordings** from the Voice Recorder folder are listed to tick, plus a **Files** button for anything else. You set the folder in Settings → Storage.
- **Presets:**
  - **Transcript:** Whisper Large v3, no cleanup.
  - **Meeting:** Deepgram Nova-3 labels the speakers, and a Whisper version is kept alongside.
  - **Custom:** Whisper with cleanup. Every option can be changed.
- **Options** sit below the presets, with provider and model menus side by side. Ticking **Label speakers** switches to Deepgram Nova-3 and says so.
- **Sharing from Voice Recorder** opens a sheet with two buttons, **Transcript** and **Meeting**, plus More options.

## Meetings copy both transcripts

A finished meeting copies one block to the clipboard. Deepgram's transcript with speakers comes first, under a line saying what it is. Then a separator, then Whisper's version of the same audio under its own line. Speaker names change only the first part. **Both transcripts** is also the first item in the Copy menu.

## History

- An open note shows **Copy, Read and Rerun** as small icons on the same row as Raw / Clean / Draft.
- A **Refresh** button at the top runs a full sync check and says what it fixed.
- When the audio isn't on the phone, Rerun says why Transcribe and Label speakers aren't available.
- Reading mode has room at the bottom, so the last lines can scroll clear of the gesture bar.

## Storage

Settings → Storage shows kept recordings and leftover copies of shared files as separate rows, each with its own delete button. Copies are deleted on their own once a recording is converted. Your originals in Voice Recorder are never touched.

## Sync fixes

- The phone used to overwrite its own notes with the copy that came back from the server, marking them as pulled from elsewhere. Those notes then stopped syncing, so speaker names you typed never left the phone, and Rerun lost the audio. Refresh repairs notes already affected.
- A conversation whose notes step was waiting to retry never synced. It now syncs as soon as it has a transcript.
- Each pull re-reads the last 15 minutes, so a note stamped by a slower clock isn't skipped.
