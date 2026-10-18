# wispr-fox for Android — v2.2.0-nightly.3

`versionName = "2.2.0-nightly.3"` · `versionCode = 20` · channel: nightly

A reliability + currency nightly, built on nightly.2. Still a nightly: install
and test on the S23 Ultra before it is promoted to stable.

## Sign-in and sync work again on Indian ISP networks

Several Indian ISPs were returning a wrong (sinkhole) address for Supabase, so
sign-in and cross-device sync died with a TLS handshake error before they could
start. wispr-fox now resolves its backend hostnames over an encrypted
DNS-over-HTTPS channel (Cloudflare, then Google) that the ISP cannot tamper
with, and only for the backend — your transcription provider traffic (Groq,
Gemini, …) is untouched.

- Email/password sign-in, cross-device sync, and delete-everywhere now go
  through the protected resolver.
- One known gap: the **Google sign-in** button opens your system browser, which
  does its own name lookup outside the app, so on a tampered network the Google
  button may still fail even though email/password works. Use email/password
  sign-in there, or a network where the block isn't present.

## Shared audio is lighter and one tap faster

Sharing a recording from Samsung Voice Recorder or a file manager already ran in
the background and survived leaving the app; this build makes it cheaper and
quicker to start.

- **16 kHz downsample.** Imported audio is now converted to 16 kHz mono (the
  rate every transcription model uses internally anyway) before upload. A
  30-minute 48 kHz recording drops from roughly 170 MB to about 57 MB, which
  means far fewer upload chunks — and so fewer billed Groq requests — for the
  same transcript.
- **One-tap Quick transcribe.** The Upload screen now leads with a Quick
  transcribe button that runs your saved defaults (transcription model + cleaned
  transcript) in one tap. The full options — different models, speaker labels,
  merge, Draft/Meeting — are still right below it when you want them.
- Long recordings are still chunked under the provider's per-request limit, the
  work still survives the app being backgrounded or killed, and when it finishes
  while you're elsewhere you still get a notification with the transcript on your
  clipboard.

## Model catalog refreshed to match desktop

- OpenAI cleanup models are now the **GPT-5.6** family (Terra / Sol / Luna);
  the retired 5.4/5.5 ids self-heal to the new default on load.
- Groq adds **Qwen 3.8 27B**; Gemini adds **3.8 Flash** (new default) and
  **3.7 Flash**.
- **Groq reasoning fix.** Every Groq model offered today is a reasoning model.
  Cleanup/Draft/Meeting now lift the completion budget to 16k, hide the model's
  chain-of-thought (so it is neither pasted into your text nor billed), tolerate
  an empty answer, and strip any stray `<think>` block. This fixes cleanups that
  could previously come back empty or with reasoning text leaked in.
- Any saved model id that no longer exists is coerced to a valid default when
  settings load.

## Smaller fixes

- The "Check for updates" screen now reuses the app's shared network client.
- A failed optional Meeting-notes pass can no longer error the whole recording;
  the transcript and other outputs are kept and Meeting notes are skipped.

## Device QA owed (S23 Ultra)

- Email/password sign-in + sync round-trip on the ISP network that previously
  blocked Supabase; confirm the Google-button limitation above.
- Share a 30-minute Samsung recording → Quick transcribe → leave the app →
  confirm the notification + clipboard, and that History shows the row
  progressing importing → transcribing → done.
- Share several files, merge, and label speakers via the options below Quick
  transcribe.
- Groq cleanup/Draft on gpt-oss and Qwen returns clean text (no `<think>`,
  not empty).
- Build is CI-only (`.github/workflows/android-apk-release.yml`); verify the
  signed APK installs in place over nightly.2.
