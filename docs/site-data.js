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
      note: "Stable v1.1.0",
      href: "https://github.com/kumaradarsh1993/wispr-fox-android/releases/download/v1.1.0/app-debug.apk"
    },
    {
      label: "Beta builds",
      note: "Advanced releases",
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
  downloadTitle: "APK first, beta second",
  downloadIntro: "Start with the stable APK. Beta builds are for people testing newer Android dictation behavior before promotion.",
  panels: [
    {
      title: "Stable APK",
      body: "The recommended download for regular phone use."
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
    { title: "Add your provider key", body: "Enter your Groq or Gemini key, then start dictating from the floating bubble." }
  ],
  footer: "Open-source Android dictation for fast capture and paste-anywhere workflows."
};
