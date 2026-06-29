window.PRODUCT_SITE = {
  name: "wispr-fox Android",
  mark: "WA",
  kicker: "Open-source Android dictation",
  headline: "A floating voice button for the apps you already use.",
  subhead: "Tap the bubble, speak naturally, then paste clean text into chats, notes, forms, and documents. Built for quick capture, code-switching, long recordings, and bring-your-own AI keys.",
  repoUrl: "https://github.com/kumaradarsh1993/wispr-fox-android",
  scene: "android",
  theme: {
    bg: "#f8f7f3",
    ink: "#171814",
    accent: "#2267d8",
    accent2: "#11856f",
    accent3: "#cb4d5a"
  },
  downloads: [
    {
      label: "Download APK",
      note: "Codex preview v1.2.0-codex.2",
      href: "https://github.com/kumaradarsh1993/wispr-fox-android/releases/download/v1.2.0-codex.2/wispr-fox-android-v1.2.0-codex.2.apk"
    },
    {
      label: "All releases",
      note: "Stable and preview builds",
      href: "https://github.com/kumaradarsh1993/wispr-fox-android/releases"
    },
    {
      label: "View source",
      note: "GitHub repo",
      href: "https://github.com/kumaradarsh1993/wispr-fox-android"
    }
  ],
  secondary: [
    { label: "All releases", href: "https://github.com/kumaradarsh1993/wispr-fox-android/releases" },
    { label: "View source", href: "https://github.com/kumaradarsh1993/wispr-fox-android" }
  ],
  stage: {
    title: "Android dictation lane",
    status: "APK ready",
    rail: [["Bubble", "Tap"], ["Modes", "Raw or clean"], ["Paste", "Anywhere"]],
    surfaceTitle: "Phone overlay",
    tiles: ["bubble", "record", "history", "mode", "paste", "settings"],
    note: "Made for real phone workflows where opening a separate editor is too much friction."
  },
  storyTitle: "Voice input that follows you around Android",
  storyIntro: "The goal is simple: speak where you are, clean the text if needed, and put it into the app you meant to use.",
  chapters: [
    {
      title: "Tap the floating bubble",
      body: "Start dictation without digging through an app drawer or moving your text into a separate workspace."
    },
    {
      title: "Choose the text style",
      body: "Use raw transcription when speed matters, cleanup for readable text, or drafting when the spoken thought needs shape."
    },
    {
      title: "Paste and keep history",
      body: "Send text into the current app, then revisit recent dictations when you need to recover or reuse something."
    }
  ],
  downloadTitle: "APK first, source always visible",
  downloadIntro: "Start with the Codex preview APK for the newest provider and paste-safety work, or use the release history for the last Claude stable.",
  panels: [
    {
      title: "Codex preview APK",
      body: "The newest Android build with multi-provider speech-to-text and safer paste handling."
    },
    {
      title: "Permissions",
      body: "Android may ask for microphone, overlay, and accessibility permissions so the bubble and paste flow can work."
    },
    {
      title: "Open source",
      body: "The repo is public, so the app code and release history are visible."
    }
  ],
  setupTitle: "Install on Android",
  setupIntro: "Because this is an APK release, Android may ask you to confirm installation from your browser or file manager.",
  setup: [
    { title: "Download the APK", body: "Use the stable APK link above from your Android phone." },
    { title: "Allow installation", body: "If Android asks, permit this source for APK installation." },
    { title: "Grant permissions", body: "Enable microphone, overlay, and accessibility permissions as prompted." },
    { title: "Add your provider key", body: "Enter a Groq, OpenAI, Deepgram, or ElevenLabs key, then start dictating from the floating bubble." }
  ],
  footer: "Open-source Android dictation for fast capture and paste-anywhere workflows."
};
