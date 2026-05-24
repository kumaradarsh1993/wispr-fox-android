# Phase 1 — mic-survival spike instructions

> **Goal:** confirm on a Note 10+ in normal battery mode that
> 10 minutes of continuous AudioRecord capture survives five
> app-switches without the foreground service being killed or the
> mic being released. This gates everything downstream.

## Prerequisites (one-time setup)

1. **Android Studio** Koala / Ladybug / newer (May 2026 build).
   Install onto `D:` since `C:` is tight. AS bundles its own JDK and
   Gradle, so you don't need to install those separately.
2. **Phone in developer mode + USB debugging.** Note 10+ →
   Settings → About → tap Build number 7 times → back → Developer
   options → enable USB debugging.
3. **adb on PATH** (bundled with Android Studio's `platform-tools` —
   add `%LOCALAPPDATA%\Android\Sdk\platform-tools` to PATH).

## Build + install

1. Open `D:\Claude Code Projects\wispr-fox-android` in Android Studio.
2. Let it sync. First sync downloads AGP 8.7.3, Kotlin 2.0.21, and
   Gradle 8.10.2 — ~700 MB into `%GRADLE_USER_HOME%`.
3. Connect Note 10+ via USB. Accept the USB-debug prompt on the phone.
4. Click **Run** (▶). AS builds the debug APK
   (`com.wisprfox.android.debug`) and installs it.

If you'd rather use the command line: `./gradlew :app:installDebug`
(requires `gradle wrapper` to have generated the wrapper jar first;
easiest path is to run `gradle wrapper` once with system Gradle, or
let AS do its first sync).

## The test

On the phone:

1. Launch **wispr-fox** (the icon will be the default Android icon —
   spike, not final art).
2. Grant **Microphone** permission when prompted.
3. Grant **Notifications** permission when prompted (API 33+).
4. Tap **Grant battery-optimisation exemption** → confirm in the
   system dialog. Without this, One UI will kill the FG service
   within 10–30 minutes of backgrounding.
5. Tap **Start recording.** The notification appears.
6. Now run the survival drill:
   - Speak (anything — Hindi + English code-switch encouraged) for
     ~30 seconds.
   - Press Home. Open WhatsApp. Wait 30s.
   - Open Chrome. Wait 30s.
   - Open Gmail. Wait 30s.
   - Open Settings. Wait 30s.
   - Open Camera. Wait 30s.
   - Return to wispr-fox. Speak for another minute.
   - Total recording duration ≥ 10 minutes.
7. Tap **Stop** (either in-app or via the notification action).

## What to check

While the recording is running, in a terminal:

```
adb logcat -s SPIKE/RecordingService SPIKE/AudioRecorder
```

You should see a heartbeat line every ~30 seconds like:

```
SPIKE/AudioRecorder  I  heartbeat elapsedMs=120034 bytes=3840768
```

**Pass criteria:**

- Heartbeats continue across all five app-switches with no gap > 60s.
- At Stop, you see a `recorder stopped totalBytes=…` line whose
  `durationMs` matches the elapsed wall clock.
- The WAV file exists at
  `/storage/emulated/0/Android/data/com.wisprfox.android.debug/files/audio/spike-YYYYMMDD-HHMMSS.wav`
  and is roughly `duration_seconds × 32_000` bytes (16 kHz × 2 B mono).
- Pull and play it to confirm audio integrity:

```
adb pull /storage/emulated/0/Android/data/com.wisprfox.android.debug/files/audio/spike-*.wav .
```

**Fail signals (any of these means we redesign before Phase 2):**

- Heartbeats stop mid-recording without a Stop action.
- A logcat line containing `Force-stopped` or `Process … died` for our
  package while recording.
- The WAV file ends well before the wall-clock duration.
- `AudioRecord.read returned -3` (ERROR_INVALID_OPERATION) or `-6`
  (ERROR_DEAD_OBJECT) appearing in logs.

## Report back

Once the test runs, paste me:

- Total wall-clock recording duration vs the final `elapsedMs` in logs.
- Number of heartbeat lines you saw vs the number you expected
  (one every 30s).
- Any unexpected log lines (especially `Force-stopped`, `killing`,
  `ANR`, or `Service is restricted`).
- File size of the resulting WAV.

If everything passes, we move to Phase 2 (full scaffolding) and reuse
the audio core verbatim. If anything fails, the failure mode tells us
which Phase 2 architectural choice has to change.
