# wispr-fox-android — holistic audit (2026-07-17)

> Read-only audit. No code was changed. Written against the working tree at `docs/` HEAD,
> app `versionName = "2.0.0"` / `versionCode = 15` (`app/build.gradle.kts:29-30`).
> Cross-referenced against the desktop sibling `D:\Claude Code Projects\wispr-fox`.
>
> Target device assumed: **Galaxy S23 Ultra, fully updated as of July 2026** → One UI 8.5 on
> **Android 16 QPR2** (Samsung shipped One UI 8 / Android 16 to the S23 series in late 2025 and
> One UI 8.5 / Android 16 QPR2 to the S23 Ultra in May 2026).

---

## Verdict

The engine is good. The presentation is not.

Everything below the UI — the provider abstraction, the durable Room+WorkManager pipeline, the
pure-function extractions (`OverlayVisibility`, `DeliveryDecision`, `KeyboardHeuristics`) with real
unit tests, the retry ladder, the length-drift security tripwire in `CleanupOrchestrator` — is
genuinely well-built. Someone thought hard about the three failure modes in `CLAUDE.md` and they are
handled.

The UI layer is where the "hot mess" verdict is earned, and it's earned honestly:

- **There is no spacing scale.** Four screens use four different outer paddings (20/16/12/14dp) and
  card interiors use 14dp, 16dp, 10dp and 8dp with no rule. Nothing is aligned to a grid because
  there is no grid.
- **Settings is a 130-line flat `Column`** with 16 unrelated groups separated by `HorizontalDivider`,
  including **seven API-key fields dumped on screen at once** regardless of which provider is
  selected. It is not designed; it is appended.
- **The avatar jump is real, and it's a one-line arithmetic mistake.** `MENU_WIDTH = 92.dp` vs
  `FOX_SIZE = 70.dp` in a centre-aligned column inside a `WRAP_CONTENT`, left-anchored overlay
  window. The code comment asserting "no horizontal drift" has been false since the day it was
  written. Diagnosed precisely in P0-2.
- **The owner's sprite complaint is exactly right and the code admits it.** The two *Compose-drawn*
  avatars (Clippy, Siri orb) have rich per-state animation. The two *raster* avatars get a single
  shared `breathe` scale and a hard cut between PNGs. And two of the desktop's three packs
  (`codex-fox`, `spark-buddy`) **do not exist on Android at all**.
- **Auto-paste has a specific, findable flakiness cause**: `performAction()` returning `true` is
  treated as proof the text landed. It isn't. There is no verification step, so a no-op paste is
  reported to the user as "Pasted". Ranked list in P0-4.
- **No dark theme.** `Theme.kt` defines exactly one `lightColorScheme`. On a phone that is very
  likely in dark mode, the app is a cream rectangle. Desktop/web ship three themes.
- **Onboarding has no inset handling at all** and the app never calls `enableEdgeToEdge()`, while
  `targetSdk = 35` on Android 16 means edge-to-edge is *enforced*. The first screen a new user sees
  draws under the status bar.

Nothing here is unfixable, and the fixes are mostly mechanical. But the owner is right that this
reads as accreted rather than designed.

---

## P0 findings

### P0-1 · No spacing scale, no type scale, no alignment grid

**What's wrong.** Every screen invents its own metrics. There is no `Spacing` object, no
`Dimens.kt`, nothing.

| Surface | Outer padding | Item spacing | Evidence |
|---|---|---|---|
| Home scroll column | `horizontal = 20.dp` | `spacedBy(14.dp)` | `ui/HomeScreen.kt:173-174` |
| Settings scroll column | `16.dp` | `spacedBy(14.dp)` | `ui/SettingsScreen.kt:86-87` |
| History list | `contentPadding = 12.dp` | `spacedBy(10.dp)` | `ui/HistoryScreen.kt:186-187` |
| Onboarding step body | `start/end = 20.dp, top = 12, bottom = 28` | `spacedBy(12.dp)` | `ui/OnboardingScreen.kt:611-619` |
| Onboarding header | `start/end = 20.dp, top = 18, bottom = 4` | — | `ui/OnboardingScreen.kt:171` |

Card interiors are just as arbitrary: `SectionCard` uses `14.dp` (`HomeScreen.kt:529`), `ImportCard`
uses `horizontal = 14, vertical = 14` (`:337`), `RecentsCard` header uses `horizontal = 14,
vertical = 10` (`:441`) and its rows `horizontal = 14, vertical = 8` (`:472`), Onboarding cards use
`16.dp` (`:328`, `:385`, `:435`), `PermissionRow` uses `14.dp` (`:552`), `HistoryRow` uses `14.dp`
(`:257`). Nine different values across four files.

The visible result on a 6.8" display: card edges don't line up with each other or with the top-bar
title, and the left edge of content shifts by 8dp when you move Home → History.

**Why it matters.** This is the single biggest contributor to the "inefficient space packing and
misalignment" complaint. Every other visual fix is cosmetic until this is fixed, because there's
nothing to align *to*.

**Fix.** Add `ui/Spacing.kt` with a 4dp-based scale and use it everywhere:

```kotlin
object Space {
    val xs = 4.dp; val sm = 8.dp; val md = 12.dp
    val lg = 16.dp; val xl = 24.dp; val xxl = 32.dp
    val screenH = 16.dp   // ONE horizontal screen inset, all screens
    val cardPad = 16.dp   // ONE card interior
    val listGap = 8.dp
}
```

Pick `16.dp` as the universal screen inset (M3's default and what One UI uses). Delete every literal
`dp` in a `padding()` call that isn't from this object.

---

### P0-2 · The avatar anchor jump — root cause and exact fix

**What's wrong.** The overlay window is `WRAP_CONTENT` × `WRAP_CONTENT`, anchored
`Gravity.BOTTOM or Gravity.START`, with `x` = distance from the **left screen edge**:

```
overlay/OverlayService.kt:125-138
params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT, ...
).apply {
    gravity = Gravity.BOTTOM or Gravity.START
    x = prefs.getInt("bx", 24)   // distance from left
    y = prefs.getInt("by", 320)  // distance from bottom
}
```

The content is a `Column(horizontalAlignment = Alignment.CenterHorizontally)`
(`overlay/AvatarOverlay.kt:125`) whose children are the menu, the bubble, and the fox.

So: **the window's left edge is pinned at `x`, the window's width is the width of its widest child,
and the fox is centred inside that width.** The moment a wider child appears, the window widens
rightward and the fox — being centre-aligned — slides right by half the growth.

The numbers, at the default `MEDIUM` scale (`settings/AvatarScale.kt:10`, multiplier `1.0f`):

| Content | Column width | Fox centre offset from `x` | Jump |
|---|---|---|---|
| Fox alone | `FOX_SIZE` = **70dp** (`AvatarOverlay.kt:64`) | 35dp | — |
| Long-press menu open | `MENU_WIDTH` = **92dp** (`AvatarOverlay.kt:66`) | 46dp | **+11dp right** |
| "listening…" bubble | ≈102dp | ≈51dp | **≈+16dp right** |
| "how long is this going to go?" bubble (`AvatarArt.kt:61`) | ≈194dp | ≈97dp | **≈+62dp right** |

The bubble `Surface` (`AvatarOverlay.kt:149-168`) wraps its `Row` with no width constraint, so its
width tracks the **text**. That means the fox doesn't just jump once — it **drifts continuously**
as `listeningLabel(seconds)` steps through its five thresholds (`AvatarArt.kt:57-64`) during a long
dictation.

The bug is scale-dependent, which is a good confirmation signal:
- `SMALL` (0.8×): fox = 56dp vs menu 92dp → **+18dp**
- `MEDIUM` (1.0×): fox = 70dp vs menu 92dp → **+11dp**
- `LARGE` (1.25×): fox = 87.5dp vs menu 92dp → **+2.25dp** (nearly invisible)

**The comments are wrong.** `AvatarOverlay.kt:66` claims *"pills sized so the column width ≈ Foxy →
no horizontal drift"* — 92 ≠ 70, so this has never been true. `AvatarOverlay.kt:124` claims
*"opening it grows the window upward (not sideways) and Foxy doesn't move"* — the **vertical** half
of that claim is correct (`Gravity.BOTTOM` pins the bottom edge and the fox is the bottom-most
child, so upward growth genuinely doesn't move it). Only the horizontal claim is false. This
matches the owner's report exactly: *"it just jumps slightly to the **right** side."*

**Render sites.** Only the overlay is affected:
- `overlay/AvatarOverlay.kt:202` — **the bug lives here.**
- `ui/HomeScreen.kt:198-203` — in-app hero, `Box(size(180.dp))` with `AvatarView(size(160.dp))`.
  Fixed box, centred column, **stable**. No jump.
- `ui/HomeScreen.kt:219-231` and `ui/SettingsScreen.kt:236-247` — static picker previews. Stable.

**Fix.** The obvious fix — give the `Column` a fixed width — **is wrong**, because a wider overlay
window swallows touches in its transparent region (Android delivers a touch to the topmost window
whose bounds contain it; a `ComposeView` returning `false` does *not* forward it to the window
below). You'd break tapping the app underneath the fox.

The correct minimal fix keeps `WRAP_CONTENT` and **recomputes `x` so the fox's centre stays put** —
which is literally implementing the manifest's `"anchor": "bottom-center"`:

1. Persist the **fox centre X** (`fcx`), not the window's left edge. Migrate the existing `bx`
   pref once: `fcx = bx + foxSizePx / 2`.
2. Put `Modifier.onSizeChanged { }` on the root `Column` in `AvatarOverlay`, plumbed out via a new
   `onContentWidth: (Int) -> Unit` callback.
3. In `OverlayService`, on each width change:
   ```kotlin
   params.x = (fcx - width / 2).coerceIn(0, screenWidthPx - width)
   windowManager.updateViewLayout(view, params)
   ```
4. In `onDrag`, update `fcx` (not `params.x`) and re-derive; in `onDragEnd`, persist `fcx`.

This pins the fox for the menu, the bubble, *and* the live label drift, at every scale, with no
touch regression.

**Longer-term (matches desktop).** The desktop uses a separate bubble band anchored at the pack's
`head` offset (`wispr-fox/src/routes/clippy/+page.svelte:219-238, 358-360`). The Android equivalent
is **two overlay windows**: one `WRAP_CONTENT` window for the fox that never changes size, and a
second for the bubble/menu positioned relative to it. That also fixes the secondary issue that the
bubble currently **swallows taps** in the region above the fox while recording.

---

### P0-3 · Settings is one flat scroll with seven API keys on screen at once

**What's wrong.** `ui/SettingsScreen.kt:85-220` is a single `Column(verticalScroll)` containing 16
consecutive groups separated by `HorizontalDivider`:

Speech-to-text (`:89`) → provider chips (`:91`) → model chips (`:96`) → **4 key fields**
(`:101-104`) → Cleanup (`:107`) → provider chips (`:109`) → model chips (`:114`) → **3 key fields**
(`:127-129`) → Usage today (`:132`) → Default tap mode (`:145`) → Delivery & avatar (`:154`) →
auto-paste toggle (`:155`) → overlay toggle (`:158`) → Avatar picker (`:163`) → Avatar size (`:169`)
→ Haptics (`:179`) → Accessibility button (`:182`) → Overlay button (`:186`) → Retention (`:194`) →
Storage cap (`:202`) → Account (`:211`) → About (`:215`).

The worst offender is the key fields. **All seven are rendered unconditionally** — a user on Groq
sees empty OpenAI, Deepgram, ElevenLabs and Gemini fields for providers they will never use. That's
~7 × 64dp ≈ 450dp of dead vertical space, more than a full screen height on the S23 Ultra, sitting
directly between the two things a user actually changes.

Second: **"Delivery & avatar" (`:154`) is a made-up category.** It merges auto-paste (a delivery
behaviour), the overlay toggle, the avatar skin, the avatar size, haptics, and two system-settings
deep-links. Those are four unrelated concerns under one heading, and the heading is only there
because someone needed somewhere to put them.

Third: **the avatar picker exists twice with different metrics.** `SettingsScreen.kt:234-249` uses
56dp chips with 40dp previews; `HomeScreen.kt:214-232` uses 54dp chips with 38dp previews. Same
control, two sizes, two files, no shared component.

**Why it matters.** Owner's words: *"just dumped altogether without any thought into segregating
it."* Correct.

**Fix.** See **Proposed Settings IA** below.

---

### P0-4 · Auto-paste flakiness — ranked causes

The user's report ("sometimes it pastes, sometimes it doesn't") is consistent with several
independent causes. Ranked by confidence × impact:

---

**#1 — `performAction()` returning `true` is treated as proof the text landed. It isn't.**
`delivery/WisprFoxAccessibilityService.kt:169-202`

```kotlin
if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)) {
    setCursorEnd(node, text.length)
    return true            // ← "the action was accepted", NOT "the text is there"
}
...
if (clipboardFallbackReady && node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
    return true            // ← same
}
```

`performAction` reports that the target view *handled* the action, not that the result is what you
wanted. A `WebView`-backed field (Chrome, Gmail web, any in-app browser), a custom `InputConnection`,
or a Compose `BasicTextField` in certain states can accept and then no-op. Because `attemptPasteWithRetry`
(`DeliveryManager.kt:78-92`) returns on the first `true`, a no-op paste **stops the retry loop, skips
the clipboard-only notification (`:66`), and reports `Channel.ACCESSIBILITY`** — which `TranscribeWorker.kt:144`
renders to the user as **"Pasted"**. The user is told it worked. Nothing is there. This is the single
best explanation for "sometimes it pastes, sometimes it doesn't" *with no error shown*.

*Fix:* verify. After the action, `node.refresh()` and re-read `node.text`; only return `true` if it
now contains `text`. If not, fall through to the next attempt and ultimately to clipboard + notification.

---

**#2 — All accessibility node access happens off the service's main thread.**

- `RecordingController.start()` calls `WisprFoxAccessibilityService.currentEditablePackage()`
  (`core/RecordingController.kt:86`) from `scope.launch` where `scope` is
  `AppContainer.applicationScope` = `CoroutineScope(SupervisorJob() + Dispatchers.Default)`
  (`core/AppContainer.kt:34`).
- `DeliveryManager.deliver()` → `tryPaste()` → `pasteInternal()` runs inside
  `TranscribeWorker.doWork()` (`queue/TranscribeWorker.kt:127`), i.e. on WorkManager's background
  executor.

So `findFocus()`, `getWindows()`, `refresh()` and `performAction()` (`WisprFoxAccessibilityService.kt:143-154,
169-202`) all execute on arbitrary background threads, never on the service's main looper. The
framework's `AccessibilityInteractionClient` caches per-thread and its window/node snapshots are
refreshed on the service's main thread — off-thread reads can see stale or empty state
non-deterministically. This is a classic source of exactly this symptom.

*Fix:* marshal all node work onto the service's main thread. Give the service a
`Handler(Looper.getMainLooper())` or reuse `serviceScope` (already `Dispatchers.Main.immediate`,
`:44`) and make `tryPaste`/`currentEditablePackage` `suspend` functions that `withContext(Dispatchers.Main)`.

---

**#3 — `ACTION_SET_TEXT` on an empty field is destructive and app-hostile.**
`WisprFoxAccessibilityService.kt:177-184`

For an empty field the code prefers `ACTION_SET_TEXT` over `ACTION_PASTE`. `ACTION_SET_TEXT` replaces
the editable's contents wholesale via the accessibility path, which **bypasses the `InputConnection`**.
Apps whose send-button enablement is driven off IME/TextWatcher events (WhatsApp and Telegram are the
classic cases) can end up with the text visible but the Send button still greyed out — which the user
would experience as "it pasted but didn't work". It also fails outright in `WebView` inputs.

*Fix:* invert the preference. Try `ACTION_PASTE` first in both branches (the clipboard is already
populated at `DeliveryManager.kt:49`), and fall back to `ACTION_SET_TEXT` only if paste is
unavailable/unverified. Check `node.actionList` for `ACTION_PASTE` before attempting.

---

**#4 — The retry window is too short and has no settle delay.**
`DeliveryManager.kt:78-92, 164-166`

`PASTE_ATTEMPTS = 4`, `PASTE_RETRY_STEP_MS = 300` → attempts at 0/300/600/900ms, ~0.9s total (the
comment says ~1.5s; it's actually 900ms of delay). Attempt 0 fires with **zero settle time** after
`copyToClipboard` at `:49`. On One UI, dismissing the recording state, the IME re-settling, and the
clipboard service round-trip can all still be in flight. A 150–250ms initial settle plus a longer
tail (e.g. 0/150/400/800/1400ms) costs the user nothing perceptible and covers far more of the
transition window.

---

**#5 — `expectedPackage` is captured at recording *start*, when it may be null.**
`RecordingController.kt:86` → `AppState.targetPackage` → `TranscribeWorker.kt:130`

If the accessibility service isn't connected yet, or focus is momentarily unreadable, `targetPackage`
is `null`. `DeliveryDecision.decide` then **disables the cross-app guard entirely** (`DeliveryDecision.kt:65`:
the check requires `expectedPackage != null`) and returns `ATTEMPT_PASTE`. That's the opposite of
conservative: the case where we know least is the case where we guard least. Combined with #2 (the
capture runs off-thread and can spuriously return null), this is reachable.

*Fix:* re-capture `expectedPackage` on the service main thread; if still null at delivery, prefer
clipboard-only + notification rather than pasting blind.

---

**#6 — Hint detection can wipe real text.** `WisprFoxAccessibilityService.kt:172-175`

```kotlin
val showingHint = node.isShowingHintText || (hint != null && rawText == hint)
val existing = if (showingHint || rawText == null) "" else rawText
```

If a field legitimately contains text identical to its hint (search boxes where the user typed the
placeholder word), `existing` becomes `""` and the code takes the `ACTION_SET_TEXT` branch, **replacing
the user's text** instead of appending. Narrow, but it's data loss.

*Fix:* trust `isShowingHintText` alone; drop the `rawText == hint` heuristic.

---

**#7 — One UI clipboard-read behaviour.** Android 12+ shows a "pasted from your clipboard" toast on
clipboard reads; One UI additionally surfaces its own clipboard chip. Neither breaks paste, but they
add visual noise. `EXTRA_IS_SENSITIVE` is correctly set (`DeliveryManager.kt:97-108`), which is the
right mitigation. No action needed — noted so it isn't mistaken for a bug.

---

## P1 findings

### P1-1 · No dark theme — at all

`ui/Theme.kt:24-54` defines exactly one `lightColorScheme` and `WisprFoxTheme` applies it
unconditionally:

```kotlin
@Composable
fun WisprFoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FoxyLight, content = content)
}
```

Verified: `grep -rn "isSystemInDarkTheme|dynamicColor|darkColorScheme"` across `app/src/main` returns
**nothing**. The comment at `:11-12` says "dark mode can come later". It's later.

Compounding it, `res/values/themes.xml` parents off `@android:style/Theme.Material.Light.NoActionBar`
— a light-only platform theme, not `DayNight`. So the splash and window background are hard-light too.
And `AndroidManifest.xml:26` lists `uiMode` in `configChanges`, meaning the activity claims to handle
theme changes itself — which it does by ignoring them.

**Gap vs siblings:** desktop and web ship three themes (Foxy light, Dark, Retro). Android ships one.

**Fix.** Add `FoxyDark` (a proper dark scheme — deep warm brown/charcoal surfaces, the same
`FoxOrange` primary, brightened for contrast on dark), switch `WisprFoxTheme` on
`isSystemInDarkTheme()` with an explicit override in Settings (Light / Dark / System), and re-parent
`themes.xml` to a `DayNight` theme. `minSdk = 31` (`build.gradle.kts:27`) means
`dynamicLightColorScheme`/`dynamicDarkColorScheme` are available with **no version gate** if an
optional Material You mode is wanted later — but the brand argues for keeping Foxy as the default
and offering dynamic as opt-in, matching desktop's theme-picker model.

---

### P1-2 · Edge-to-edge is enforced but never handled; onboarding has zero inset handling

**Facts.** `compileSdk = 35`, `targetSdk = 35`, `minSdk = 31` (`build.gradle.kts:22, 27-28`). Per the
[Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16),
edge-to-edge was enforced for `targetSdk = 35` on Android 15+ (with `windowOptOutEdgeToEdgeEnforcement`
as an escape hatch), and at `targetSdk = 36` the opt-out is **deprecated and disabled entirely**. On
the owner's Android 16 device, this app is edge-to-edge whether it planned to be or not.

`enableEdgeToEdge()` is never called (verified by grep — no hits for `enableEdgeToEdge` or
`WindowCompat` anywhere in `app/src/main`).

Home, History and Settings survive by accident because they use `Scaffold` and apply
`.padding(inner)` (`HomeScreen.kt:171`, `HistoryScreen.kt:173/185`, `SettingsScreen.kt:86`).

**`OnboardingScreen` does not.** `ui/OnboardingScreen.kt:117-121`:

```kotlin
Column(
    Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background),
) {
    OnboardingHeader(step, totalSteps)   // padding(top = 18.dp) — that's ALL
```

No `Scaffold`, no `WindowInsets`, no `systemBarsPadding()`. `StepScaffold` (`:611-619`) pads
`top = 12.dp, bottom = 28.dp`. On an S23 Ultra the status bar is ~24dp and gesture nav ~24dp, so the
brand wordmark renders **under the status bar** and the "Get started" / "Start dictating" buttons sit
**under the gesture bar**. This is the very first screen a new user sees.

**Fix.** Call `enableEdgeToEdge()` in `MainActivity.onCreate` before `setContent`, and wrap the
onboarding root in `Modifier.safeDrawingPadding()` (or give it a `Scaffold`). Then bump
`compileSdk`/`targetSdk` to 36 — see P1-8.

---

### P1-3 · Raster avatars have no animation; two desktop packs are missing entirely

This is the owner's complaint, and it is precisely correct.

**Inventory — Android** (`app/src/main/res/drawable-nodpi/`):

| Prefix | Files | States |
|---|---|---|
| `fox_*` | `sitting`, `recording`, `curious`, `success`, `error`, `favicon`, `logo` | **5** art states |
| `oru_gujia_*` | `idle`, `listening`, `thinking`, `writing`, `pasting`, `excited`, `error`, `thumbnail` | **7** + thumbnail |

**Inventory — desktop** (`wispr-fox/static/avatars/`): three packs — `codex-fox`, `spark-buddy`,
`oru-gujia` — each with `avatar.json` + **8 states** (`idle`, `listening`, `thinking`, `writing`,
`pasting`, `error`, `sleeping`, `excited`) + `thumbnail.png`.

**What's missing on Android:**

1. **`codex-fox` — the entire pack.** Android's `Avatar.FOX` is *not* codex-fox; it's the legacy
   5-state watercolour set (`overlay/AvatarArt.kt:14-22`). Because it has only 5 arts for 7 pipeline
   states, `TRANSCRIBING` and `CLEANING` both map to `fox_curious`, and `INJECTING` and `DONE` both
   map to `fox_success`. Two collapsed pairs.
2. **`spark-buddy` — the entire pack.** This is "the fire" the owner referred to. It does not appear
   in `settings/Avatar.kt:14` (`enum class Avatar { FOX, CLIPPY, ORU_GUJIA, SIRI }`), has no
   drawables, and has no `AvatarArt` mapper. It was never ported.
3. **`oru-gujia/sleeping.png`.** `AvatarArt.kt:28` admits it: *"(sleeping is unused for now.)"* There
   is also no idle-timeout anywhere in the codebase to *drive* a sleeping state — desktop has one.
4. **`avatar.json` itself.** No manifest is ported. The desktop shape is:
   ```json
   { "manifestVersion": 2, "renderer": "raster-state-pack", "id": "codex-fox",
     "art": { "width": 136, "height": 146, "head": 146, "anchor": "bottom-center" },
     "states": { "idle": "idle.png", ... }, "thumbnail": "thumbnail.png" }
   ```
   `art.head` = distance from the window bottom up to where the bubble's tail sits;
   `anchor: "bottom-center"` is the positioning contract (`wispr-fox/src/routes/clippy/+page.svelte:234-252`).
   Android has none of this — `AvatarArt.kt` hardcodes a `when` per pack, so adding a pack means
   editing three files.

**The animation gap.** Compare the two families:

- **Compose-drawn avatars are richly animated.** `ClippyAvatar.kt:57-95` — spring-loaded ear unfurl,
  forward lean, randomised blink loop with a double-blink while recording, darting pupils, a
  continuous ear flap. `SiriOrbAvatar.kt:48-83` — per-state spin rate (4500/2200/11000ms), breathing
  scale, green DONE ring pulse, red ERROR flash.
- **Raster avatars get one shared animation and a hard cut.** `AvatarOverlay.kt:112-121` applies a
  single `breathe` scale to the whole `Box`, faster while recording:
  ```kotlin
  val breathe by transition.animateFloat(
      initialValue = 1f,
      targetValue = if (snapshot.pipeline == PipelineState.RECORDING) 1.1f else 1.03f, ...)
  ```
  and `AvatarView.kt:23-33` swaps the `painterResource` with **no `Crossfade`, no `AnimatedContent`** —
  so every state change is a 1-frame pop.

That's the whole complaint: the inherited sprites are static; only the two vector avatars move.

**What porting to Compose requires:**

1. **Assets** — copy 8 PNGs each for `codex-fox` and `spark-buddy` + their thumbnails, plus
   `oru_gujia_sleeping.png`, into `drawable-nodpi/` (17 new files). Note these are ~200KB each; at
   ~26 new drawables that's ~5MB of APK. Consider WebP conversion.
2. **A data-driven pack model** mirroring `avatar.json` — replace the hardcoded `when` blocks:
   ```kotlin
   enum class AvatarState { IDLE, LISTENING, THINKING, WRITING, PASTING, ERROR, SLEEPING, EXCITED }
   data class AvatarArt(val width: Dp, val height: Dp, val head: Dp, val anchor: Anchor)
   data class AvatarPack(
       val id: String, val name: String, val art: AvatarArt,
       @DrawableRes val states: Map<AvatarState, Int>,
       @DrawableRes val thumbnail: Int,
   )
   ```
3. **One** `PipelineState → AvatarState` mapper (currently duplicated per-pack across
   `AvatarArt.kt:14-22` and `:31-39`), matching desktop: `IDLE→idle`, `RECORDING→listening`,
   `TRANSCRIBING→thinking`, `CLEANING→writing`, `INJECTING→pasting`, `DONE→excited`, `ERROR→error`,
   plus a new idle-timeout → `sleeping`.
4. **An animation layer.** `Crossfade` (or `AnimatedContent` with fade) between state arts, plus
   per-state motion via `graphicsLayer` driven by `rememberInfiniteTransition` keyed on state:
   idle = slow breathe; listening = faster bob + slight `rotationZ` lean; thinking = gentle rock;
   pasting = quick forward nudge; excited = a spring hop (`translationY`); error = a short shake
   (decaying `translationX`); sleeping = very slow breathe + reduced alpha. This gives raster packs
   parity with what Clippy/Siri already do, without needing sprite sheets.
5. **Use `art.head`** to anchor the bubble above the character's head (as desktop does at
   `clippy/+page.svelte:358-360`) and `art.anchor = bottom-center` to drive the P0-2 fix.

---

### P1-4 · The pipeline can lock the user out of recording, permanently

`core/RecordingController.kt:48-56`:

```kotlin
fun toggle(modeOverride: DictationMode? = null) {
    scope.launch {
        when (AppState.state.value.pipeline) {
            PipelineState.RECORDING -> stop()
            PipelineState.IDLE, PipelineState.DONE, PipelineState.ERROR -> start(modeOverride)
            else -> { /* busy in a foreground transcribe/clean/inject — ignore the double-tap */ }
        }
    }
}
```

`TRANSCRIBING`, `CLEANING` and `INJECTING` all fall into the silent `else` branch. `AppState` is a
process-wide singleton (`core/AppState.kt:25`) with **no timeout and no `try/finally`** restoring it.

`TranscribeWorker.doWork()` (`queue/TranscribeWorker.kt:43-153`) sets `AppState.setPipeline(CLEANING)`
at `:82` and then does work that is only *partly* guarded. `CleanupOrchestrator.clean` is safe by
contract (it catches provider failures and returns raw text — `provider/CleanupOrchestrator.kt:13-15`),
but these are **not** inside any `catch`:

- `container.providerFactory.llm(settings)` (`:85`) — only `MissingKeyException` is caught (`:96`)
- `recordings.setAlt(...)` (`:94`) — a Room write
- `container.delivery.deliver(...)` (`:127`)
- `recordings.setStatus(id, RecordingStatus.DONE)` (`:132`)

If any of those throws, `doWork()` throws, WorkManager marks the work **failed** (a thrown exception
is not `Result.retry()`), and **nothing ever resets `AppState.pipeline`**. Consequences:

1. The fox is stuck rendering "polishing…" (`AvatarArt.kt:47`) forever.
2. `OverlayVisibility.isBusy()` (`overlay/OverlayVisibility.kt:32-36`) returns `true` for `CLEANING`,
   so the fox is **pinned on screen permanently** — which is the *exact* "fox sticks around with no
   text box in sight" bug RC-1.1 was supposed to have fixed, reachable by a different route.
3. `toggle()` hits the `else` branch, so **the user cannot start a new recording until the process
   is killed**.
4. The Room row is stranded in `CLEANING` and is only cleaned up by `recoverStranded()` on next
   process start (`WisprFoxApp.kt:24-26`).

**Fix.** Wrap the whole of `doWork()` in `try { ... } catch (t: Throwable) { fail(id, ...) }` — `fail`
(`:193-206`) already does the right thing. Additionally, add a watchdog: if `pipeline` has been in a
non-`IDLE`, non-`RECORDING` state for > 90s with no progress, reset to `IDLE`. Belt and braces on the
one state that can brick the app.

---

### P1-5 · The "Network hiccup" bubble never clears

`overlay/AvatarOverlay.kt:95-100`:

```kotlin
LaunchedEffect(snapshot.pipeline, snapshot.message) {
    showBubble = snapshot.pipeline != PipelineState.IDLE || snapshot.message != null
    if (snapshot.pipeline == PipelineState.DONE || snapshot.pipeline == PipelineState.ERROR) {
        delay(2500); showBubble = false
    }
}
```

The auto-hide only fires for `DONE`/`ERROR`. But `TranscribeWorker.retryOrFail` (`:176-186`) sets:

```kotlin
copy(pipeline = PipelineState.IDLE, ..., message = "Network hiccup — will retry in background",
     messageIsError = false)
```

`pipeline == IDLE` **and** `message != null` → `showBubble = true`, and neither auto-hide branch is
taken. `AppState.clearMessage()` (`core/AppState.kt:93`) exists but is **never called from anywhere**
on this path. The bubble persists until some other state change happens to overwrite `message`.

It's partially masked because `OverlayVisibility.shouldShow` hides the whole window at `IDLE` with the
keyboard down — but the moment the keyboard comes back up, a stale "Network hiccup" bubble reappears
next to the fox for a recording that finished minutes ago.

**Fix.** Auto-hide on `message != null` regardless of pipeline state, and have `retryOrFail`
schedule a `clearMessage()`.

---

### P1-6 · `runBlocking` on the main thread in the recording stop path

`audio/RecordingService.kt:116-130`:

```kotlin
runBlocking {
    container.recordings.setDuration(id, durationMs)
    ...
    container.recordings.setStatus(id, RecordingStatus.TRANSCRIBING)
    AppState.setPipeline(PipelineState.TRANSCRIBING)
    TranscribeWorker.enqueue(applicationContext, id)
}
```

`Service.onStartCommand` runs on the **main thread**. This blocks it on two Room writes plus
`WorkManager.enqueueUniqueWork` (itself a database write). Same pattern in `failStartup`
(`:150-152`). On a loaded device this is an ANR waiting to happen, and it sits on the single most
latency-sensitive path in the app: the moment the user taps to stop.

**Fix.** Launch on `container.applicationScope` instead. `stopSelf()` is already called by the caller
(`:52-54`); use `stopSelf(startId)` after the coroutine completes so the service isn't torn down
mid-write.

---

### P1-7 · `runCatching` doesn't guard what it looks like it guards

`delivery/WisprFoxAccessibilityService.kt:102-114`:

```kotlin
private fun startOverlayIfAllowed() {
    runCatching {
        val container = WisprFoxApp.container(this)
        serviceScope.launch {                       // ← returns immediately
            val settings = container.currentSettings()
            if (Settings.canDrawOverlays(...) && settings.overlayBubbleEnabled) {
                startService(Intent(..., OverlayService::class.java))
            }
        }
    }
}
```

`runCatching` completes the moment `launch` returns. Anything thrown **inside** the coroutine —
including a background-service-start `IllegalStateException` from `startService`, or a DataStore read
failure — escapes it entirely. `serviceScope` has a `SupervisorJob` but **no `CoroutineExceptionHandler`
(`:44`)**, so an uncaught throwable propagates to the default handler and can take down the process
hosting the accessibility service. Since RC-1.4 made this the mechanism that keeps the fox alive
across process death, a crash here is self-defeating.

(In practice the app having a bound accessibility service usually exempts it from the Android 8+
background-service-start restriction, so the `startService` call itself probably succeeds. The
defective error handling is the definite finding; the crash is the risk it fails to contain.)

**Fix.** Move `runCatching` (or a `try/catch`) *inside* the `launch`, and add a
`CoroutineExceptionHandler` to `serviceScope`.

---

### P1-8 · Toolchain is ~1 platform generation behind the target device

| Item | Current | Evidence | Assessment |
|---|---|---|---|
| `compileSdk` | 35 | `build.gradle.kts:22` | One behind (36 is Android 16) |
| `targetSdk` | 35 | `:28` | One behind — device runs Android 16 QPR2 |
| `minSdk` | 31 | `:27` | Fine; enables dynamic color with no gate |
| AGP | 8.7.3 | `libs.versions.toml:2` | Stale; cannot build `compileSdk 36` (needs AGP 8.9+) |
| Kotlin | 2.0.21 | `:3` | Stale |
| Compose BOM | 2024.11.00 | `:9` | **~20 months old.** Predates M3 Expressive entirely. |
| Room | 2.6.1 | `:14` | Stale |
| WorkManager | 2.9.1 | `:16` | Stale |
| Java target | 17 | `:71-77` | Fine |

**What this implies on a July-2026 S23 Ultra:**

- **Edge-to-edge** is enforced (P1-2). At `targetSdk 36` the `windowOptOutEdgeToEdgeEnforcement`
  escape hatch is gone, so this must be fixed *before* bumping.
- **Foreground-service types** are correctly declared: `FOREGROUND_SERVICE_MICROPHONE`
  (`AndroidManifest.xml:8`) + `foregroundServiceType="microphone"` (`:44`) + the API-34 branch in
  `RecordingService.kt:75-79`. Compliant.
- **Notification permission** is requested (`MainActivity.kt:198-201`) and delivery degrades
  gracefully without it (`DeliveryManager.kt:119-125`). Compliant.
- **Predictive back**: enabled by default at `targetSdk 35+`. Navigation Compose 2.8.4 supports it,
  but no `NavHost` transitions are configured, so back is functional but visually flat. Untested.
- **`MainActivity` background-FGS start** from the overlay is exempted by `SYSTEM_ALERT_WINDOW`
  (`AndroidManifest.xml:11`) — this is also what keeps mic-while-in-background legal, since a visible
  overlay window counts as a visible app state. **Fragility worth noting:** `OverlayService` sets
  `composeView.visibility = View.GONE` when hidden (`overlay/OverlayService.kt:105`). If the user
  starts a recording via the **Quick Settings tile** (`tile/DictationTileService.kt:19`) while the fox
  is `GONE` and the app is backgrounded, there may be no visible overlay to confer the while-in-use
  state. The tile's comment at `:11-13` asserts a tile tap "counts as user-initiated, so it's allowed
  to start the foreground microphone service" — that's true for the *service start* but is not the same
  exemption as mic *data access*. **Needs device verification**: does tile-initiated dictation actually
  capture audio, or silence?
- **Compose BOM 2024.11.00** is the biggest gap. It predates M3 Expressive, the newer
  `NavigationBar`/`FloatingToolbar` components, and shape/motion tokens. Any "modern, not
  vibe-coded" redesign is hamstrung until this moves.

**Fix.** Bump AGP → 8.9+, Kotlin → 2.1.x, Compose BOM → current, `compileSdk`/`targetSdk` → 36 — but
**land P1-2 (edge-to-edge) first**, since 36 removes the opt-out.

---

### P1-9 · TalkBack: four identically-labelled avatar buttons

`ui/SettingsScreen.kt:252-268` and `ui/HomeScreen.kt:537-552` build picker chips as a bare
`Box(...).clickable { }` with **no `contentDescription` and no `Role.Button`**. The only semantics
come from the inner `AvatarView`, which returns `"wispr-fox ready"` for `PipelineState.IDLE`
(`overlay/AvatarView.kt:14`) — **the same string for all four**. TalkBack announces four identical
"wispr-fox ready" buttons with no way to tell them apart or know which is selected.

The Oru & Gujia chip is worse: `Image(painterResource(R.drawable.oru_gujia_thumbnail), null, ...)`
(`SettingsScreen.kt:243`, `HomeScreen.kt:227`) passes `null` — completely unlabelled.

Also: `ui/OnboardingScreen.kt:296` sets `Modifier.size(width = 52.dp, height = 18.dp)` on a **`Text`**.
A hard-sized text label clips at increased font scale — and One UI's display-size and font-size
sliders are heavily used.

**Fix.** Give each chip `Modifier.semantics { role = Role.RadioButton; contentDescription = "Fox
avatar"; selected = isSel }`. Replace the fixed-size `Text` with `widthIn(min = 52.dp)`.

---

### P1-10 · The overlay's tap and drag gesture detectors compete

`overlay/AvatarOverlay.kt:185-199` attaches **two separate `pointerInput(Unit)` modifiers** to the
same `Box` — the first running `detectTapGestures` (with `onTap`, `onLongPress`, `onDoubleTap`), the
second running `detectDragGestures`.

Both handlers receive events in parallel and race to consume. Because `detectTapGestures` with an
`onDoubleTap` must wait out the double-tap timeout, and with `onLongPress` must wait out the
long-press timeout, drag initiation can feel laggy or drop entirely depending on which detector wins
the down event. This is a well-known Compose pitfall.

**Fix.** Use a single `pointerInput` block and either `detectDragGesturesAfterLongPress`, or run both
detectors in separate coroutines within one `awaitPointerEventScope` with explicit consumption order.
Given long-press already opens the mode menu, the cleanest model is: tap = toggle, long-press = menu,
drag = reposition (initiated from `awaitFirstDown` + slop, ahead of the tap detector).

---

## P2 findings

- **`EXTRA_MODE` is dead.** Written at `audio/RecordingService.kt:230`, declared at `:216`, and
  **never read** — `onStartCommand` (`:45-57`) only reads `EXTRA_PATH` and `EXTRA_ID`. Mode comes
  from the Room row instead. Remove it or wire it.

- **Both banners fire together and say the same thing.** `ui/HomeScreen.kt:179-193`. When
  accessibility is off, `missingPermissions()` (`:556-563`) already counts `"auto-paste"`, so
  `SetupBanner` says *"2 permissions still need to be granted"* **and** `AccessibilityBanner` renders
  a three-line prose paragraph (`:417-423`) immediately below it. Two banners, ~180dp, saying
  overlapping things, pushing the hero below the fold. Merge into one.

- **`a11yConnected` is read two different ways.** `AppState.a11yConnected` exists as a reactive flow
  field (`core/AppState.kt:52`, set at `WisprFoxAccessibilityService.kt:50/63/72`) — and `HomeScreen`
  ignores it, polling the singleton on resume instead: `remember(permTick) {
  !WisprFoxAccessibilityService.isConnected() }` (`:188`). The flow is the correct source; the poll is
  stale between resumes.

- **Hardcoded colours bypass the theme.** `GREEN = Color(0xFF6CB16D)` (`ui/HomeScreen.kt:94`),
  `Color(0xFF2FB170)` (`overlay/AvatarOverlay.kt:282`), `Color(0xFFB3261E)` (`:151`, which is
  literally M3's baseline error red — `MaterialTheme.colorScheme.error` is right there). These will
  all break in dark mode (P1-1).

- **Redundant status text.** `StatusPill` renders "Recording" (`ui/HomeScreen.kt:581-589`) directly
  above `heroTitle` rendering "Listening…" (`:565-573`) — two labels, same information, stacked.

- **Bespoke `dp` extension.** `ui/Nav.kt:55`: `private fun Int.dp() = Dp(this.toFloat())`, used once
  at `:28` as `0.dp()`. Shadows the standard `.dp`. Just use `0.dp`.

- **State lost on process death.** `showImport` / `importConfig` (`ui/HomeScreen.kt:123-134`) and
  `selectMode` / `selected` (`ui/HistoryScreen.kt:101-102`) use `remember`, not `rememberSaveable`.
  Onboarding's `step` correctly uses `rememberSaveable` (`:98`) — so the pattern is known, just
  inconsistently applied.

- **`hasAnySttKey()` is not reactive.** `MainActivity.kt:63` calls it as a plain function inside
  `setContent`; it never recomposes. Harmless for `startDestination` (computed once, and `onDone`
  navigates explicitly) but the `LaunchedEffect(deepLink)` guard at `:70` reads a potentially stale
  `hasKey`.

- **Possible delete-semantics bug.** `ui/HistoryScreen.kt:115-122`:
  ```kotlin
  when {
      transcripts -> container.recordings.deleteTranscripts(ids, scope2)
      voiceFiles -> container.recordings.deleteAudioOnly(ids)
  }
  ```
  If the user checks **both** "voice files" and "transcripts" in `DeleteDialog`, only the first branch
  runs — audio deletion is skipped unless `deleteTranscripts` happens to cascade. The same `when` is
  duplicated at `:422-427`. **Verify `RecordingRepository.deleteTranscripts` semantics**; if it
  doesn't remove the WAV, users who ask for both get their audio silently retained.

- **`confirmDeleteSelected = anyChecked`** (`ui/HistoryScreen.kt:144`) — tapping delete with nothing
  selected sets the flag to `false`, so the button silently does nothing. Disable the button instead.

- **`isKeyboardVisible()` does full window enumeration on every accessibility event.**
  `WisprFoxAccessibilityService.kt:87-89` calls it from `onAccessibilityEvent` with
  `notificationTimeout="100"` (`res/xml/accessibility_service_config.xml`). `getWindows()` is a
  cross-process call. At 10 events/sec during scrolling this is measurable battery/CPU. Debounce the
  immediate call too, or filter by event type first.

---

## Proposed Settings IA

**Recommendation: a hub-and-spoke list, not tabs, not a flat scroll.**

**Why not tabs.** M3 tabs are for switching between *peer content* of the same kind. Settings groups
aren't peers — Account and Avatar Size have nothing in common. One UI's own Settings app uses zero
tabs anywhere. Tabs would also cap us at ~4–5 groups; we have 8.

**Why not the current flat scroll.** M3 guidance is to nest once you exceed roughly 5–7 groups. We
have 16.

**Why hub-and-spoke.** It is exactly what One UI's Settings does: a scrollable list of rows, each
`icon + title + summary-of-current-value`, opening a sub-page with a large collapsing title. It's the
convention the owner's muscle memory already has, it makes the hub scannable in one screen with no
scrolling, and each sub-page is short enough to need no scrolling either.

### The hub (`SettingsScreen`)

A `LazyColumn` of 8 rows. Each shows the current value as a summary — so the hub *answers questions*
rather than just being a menu:

```
┌─────────────────────────────────────────────┐
│  Settings                        [large title]
│                                             │
│  🎙  Transcription                          │
│      Groq · Whisper Large v3 Turbo        › │
│                                             │
│  ✨  Cleanup & modes                        │
│      Clean · Llama 70B on Groq            › │
│                                             │
│  🦊  Foxy                                   │
│      Fox · Medium · Floating on           › │
│                                             │
│  📋  Delivery                               │
│      Auto-paste on · Accessibility on      › │
│                                             │
│  📊  Usage                                  │
│      412 / 2000 today                     › │
│                                             │
│  💾  Storage                                │
│      Keep 7 days · 500 MB cap             › │
│                                             │
│  ☁️  Account                                │
│      adarsh@… · Synced 2 min ago          › │
│                                             │
│  ℹ️  About                                  │
│      v2.0.0                               › │
└─────────────────────────────────────────────┘
```

Eight rows at ~72dp = ~576dp. Fits an S23 Ultra with no scroll. Compare to today's ~2,400dp scroll.

### The sub-pages

**1 · Transcription** — `settings/transcription`
- Provider: segmented button (Groq / OpenAI / Deepgram / ElevenLabs) — replaces the `FlowRow` of
  chips at `SettingsScreen.kt:91`
- Model: chips filtered to the selected provider (`ProviderCatalog.sttModelsFor`)
- **API key: ONE field, for the selected provider only.** This is the key change — it kills ~450dp
  of dead space from `SettingsScreen.kt:101-104`.
- Collapsed "Other provider keys ▸" expander for the other three, with a ✓/— status dot each.
- Footer: "Audio is sent only to the provider selected here."

**2 · Cleanup & modes** — `settings/cleanup`
- **Default tap mode** (Raw / Clean / Draft) — moved here from `:145`, where it currently sits
  orphaned between "Usage today" and "Delivery & avatar". It belongs with the modes it names.
- Each mode gets a one-line say→get example, reusing `ModeCard` from `OnboardingScreen.kt:264-286`.
  The user already saw these in onboarding; reinforce, don't re-explain.
- Cleanup provider: segmented (Groq / OpenAI / Gemini)
- Cleanup model: chips for the selected provider
- One key field for the selected cleanup provider + "Other keys ▸"

**3 · Foxy** — `settings/avatar`
- Avatar picker — **one shared component**, extracted from the two divergent copies
  (`SettingsScreen.kt:234-249` @ 56dp/40dp and `HomeScreen.kt:214-232` @ 54dp/38dp). A 2×3 grid of
  large live previews (once `codex-fox` and `spark-buddy` land there are 6 skins), each animating in
  its idle state — this is where the sprite work pays off visually.
- Size: segmented S / M / L with a **live preview** that resizes
- Show floating fox: switch
- Haptics on long-press: switch

**4 · Delivery** — `settings/delivery`
- Auto-paste: switch
- **Accessibility status card** — live ✓/✗ + inline "Enable" button (reuse `PermissionRow` from
  `OnboardingScreen.kt:546-573`), replacing the naked `OutlinedButton` at `SettingsScreen.kt:182`
- **Overlay status card** — same treatment, replacing `:186`
- **Battery exemption status card** — currently only in onboarding (`OnboardingScreen.kt:513`) and
  never re-surfaced in Settings, despite `missingPermissions()` counting it (`HomeScreen.kt:561`).
  That's a real hole: a user who skipped it has no path back.
- Footer: "Text is always copied to the clipboard as a fallback."

**5 · Usage** — `settings/usage`
- Today's Speech + Cleanup meters (existing `UsageMeterRow`)
- Reset label
- *Consider dropping this sub-page entirely* — it's already on Home (`HomeScreen.kt:300-305`) and is
  a dashboard, not a setting. Keep the hub row as a read-only summary that deep-links to Home.

**6 · Storage** — `settings/storage`
- Retention slider (0–30 days) + cap slider (100–1000 MB) — from `SettingsScreen.kt:194-208`
- **Add: current usage** ("312 MB of 500 MB · 41 recordings") — a cap slider with no indication of
  where you are against it is not actionable.
- "Delete all recordings" → routes to the existing `DeleteDialog`

**7 · Account** — `settings/account`
- Existing `AccountSection(container)` verbatim (`SettingsScreen.kt:212`)
- Only render the hub row when `SupabaseConfig.isConfigured()`, mirroring the onboarding gate at
  `OnboardingScreen.kt:115`

**8 · About** — `settings/about`
- Version + build
- Privacy blurb (`SettingsScreen.kt:216`)
- "Replay setup guide" (`:217`)
- Links: GitHub, releases

### Structural notes

- Use **Navigation Compose** for the spokes (`nav.navigate("settings/transcription")`) — the
  `NavHost` already exists at `MainActivity.kt:97-132`, so this is 8 new `composable {}` entries.
- Each spoke uses `LargeTopAppBar` with `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()` —
  this is the One UI large-title convention, and it also fixes reachability: on a 6.8" display the
  title starts near the middle of the screen and the back button stays in the bottom-thumb arc.
- Extract a shared `SettingsRow(icon, title, summary, onClick)` and `SettingsGroup` — currently every
  row is bespoke.

---

## Proposed redesign direction

### Home — cut the hero, promote the content

**Today** (`ui/HomeScreen.kt:168-311`), top to bottom: up to 2 banners (~180dp) → `Spacer(2dp)` →
`StatusPill` → `Box(180dp)` hero avatar → `headlineSmall` title → `bodyMedium` subtitle → a row of
four 54dp avatar chips + a 56dp mic button → "Recording style" card → "Speech-to-text" card → "Smart
cleanup" card → "Import audio file" card → "Usage today" card → "Recent" card.

That's **~400dp of hero** before any content, plus four labels (`StatusPill` "Recording",
`heroTitle` "Listening…", plus the bubble on the overlay) all saying the same thing.

**Problems, precisely:**
1. `Box(180.dp)` containing `AvatarView(160.dp)` (`:198-203`) — 20dp of pure padding, and 180dp is
   ~22% of the viewport for a decorative element.
2. **The avatar-skin picker sits next to the record button** (`:214-242`). Skin selection is a
   settings concern; putting it adjacent to the primary action implies they're related. It's the
   clearest single example of "no thought into segregating."
3. `StatusPill` (`:496-502`) + `heroTitle` (`:204`) are redundant.
4. "Recording style" (`:244`), "Speech-to-text" (`:252`) and "Smart cleanup" (`:265`) are three
   separate cards that are all **settings**, duplicating the Settings screen inline. Home is
   currently 60% settings by area.

**Proposed:**

```
┌─────────────────────────────────────────────┐
│ 🦊 wisprfox                            ⚙️   │  ← existing top bar, keep
│                                             │
│ ⚠️ Finish setup — 2 permissions        ›   │  ← ONE merged banner, only if needed
│                                             │
│         ╭───────────────╮                   │
│         │   [avatar]    │                   │  ← 120dp, animated per state
│         ╰───────────────╯                   │
│           Tap to speak                      │  ← ONE label. Kill StatusPill.
│                                             │
│   ┌─────┐  ┌───────┐  ┌───────┐            │
│   │ Raw │  │ Clean │  │ Draft │            │  ← mode = the ONLY inline setting.
│   └─────┘  └───────┘  └───────┘               It's per-utterance, so it earns Home.
│                                             │
│   Groq · Turbo → Llama 70B          ⚙️     │  ← ONE read-only summary line,
│                                                deep-links to Settings.
│                                                Replaces 2 whole cards.
│  ┌───────────────────────────────────────┐ │
│  │ 📊 412 / 2000 today  ▓▓▓▓░░░░░░       │ │  ← compact strip, not a card
│  └───────────────────────────────────────┘ │
│                                             │
│  Recent                            See all ›│
│  ┌───────────────────────────────────────┐ │
│  │ "the meeting is at 4pm…"   2m · Clean │ │
│  │ "tell saurabh i'll be…"   1h · Draft  │ │  ← give this the space the
│  │ "quick note about the…"   3h · Raw    │ │     hero was wasting
│  └───────────────────────────────────────┘ │
│                                             │
│  ⬆️ Import audio file                   ›  │  ← compact row, not a card
├─────────────────────────────────────────────┤
│      🎤 Speak          🕘 History           │  ← keep; it's correct
└─────────────────────────────────────────────┘
```

Changes: avatar 160→120dp; delete `StatusPill`; move the avatar picker to Settings → Foxy; collapse
the "Speech-to-text" + "Smart cleanup" cards into one tappable summary line; demote "Import" from
card to row; merge the two banners. **Net: ~380dp reclaimed**, all of it going to Recent — the only
thing on this screen with actual content.

Keep the record FAB, but as a proper M3 **extended FAB** anchored bottom-right above the nav bar —
that's the reachability position on a 6.8" display, and it removes the awkward chips+button hybrid row.

### History — good bones, needs density and grouping

`ui/HistoryScreen.kt` is the best screen in the app. The select mode (`:142-165`), the always-visible
retry chip (`:369-390`), and the confirm dialogs are all well-judged.

Fixes:
1. **Date group headers** — "Today" / "Yesterday" / "Last week" as `stickyHeader`s. Currently every
   row prints an absolute timestamp (`:279`, `timeFmt = "MMM d, h:mm a"` at `:491`), which is both
   noisy and hard to scan.
2. **Row density.** Each collapsed row is `Card` + 14dp padding + timestamp + badge row + 2-line
   preview ≈ 120dp. ~6 rows per screen. Drop to ~88dp: put the time inline with the preview's first
   line, move the platform badge + duration to a single meta line.
3. **`RecordingStatus.DONE` renders "done" as a status label on every row** (`:487`) — noise. Only
   show status when it's *not* done.
4. **Search.** There's no way to find anything. `RecordingDao` already backs a Flow; a `SearchBar`
   filtering on `transcript` is cheap and this is a growing corpus.
5. **Swipe actions.** `SwipeToDismissBox` for delete is the platform convention; long-press-to-delete
   (`:264`) is undiscoverable.

### Settings — see the IA above.

### Onboarding — fix insets, then cut

`ui/OnboardingScreen.kt` is well-written prose and the 3–4 step arc is right. Two problems:

1. **P1-2 (insets)** — highest priority; it's the first screen anyone sees.
2. **Step 2 is enormous.** `SetupStep` (`:305-454`) stacks: intro text → setup-code card → "or" divider
   → `HowIsThisFree` (expanded by default, `:458`) → Groq card containing **four key fields**
   (`:406-421`) → Gemini card → nav buttons. That's ~1,400dp of scroll on the step where a new user
   is most likely to bail.

   Fix: show **one** key field (Groq — the recommended path). Put the other three behind "Use a
   different provider ▸". Default `HowIsThisFree` to **collapsed** (`:458` currently
   `mutableStateOf(true)`) — it answers a question the user hasn't asked yet.

3. `WelcomeStep` renders three `ModeCard`s (`:222-236`) plus a "Draft is the hidden superpower" card
   (`:238-251`) — four cards before the "Get started" button. Cut to the three mode cards; fold the
   superpower line into the Draft card itself.

### Import sheet — keep as-is

`ui/ImportSheet.kt` wasn't a source of complaints and the flow (pick config → SAF picker) is sound.

### Cross-cutting

- **Extract shared components**: `SettingsRow`, `AvatarPicker`, `StatusCard`/`PermissionRow`,
  `SectionCard`. Right now `SectionCard` (`HomeScreen.kt:527`), `PermissionRow`
  (`OnboardingScreen.kt:546`) and the Settings groups are three unrelated implementations of the same
  visual idea.
- **Corner radii**: currently `14.dp` (`HomeScreen.kt:547`), `12.dp` (`AvatarOverlay.kt:150`), `7.dp`
  (`OnboardingScreen.kt:272`), `6.dp` (`HistoryScreen.kt:290`, `:438`), `1.5.dp` (`AvatarOverlay.kt:248`).
  Adopt the M3 shape scale (4/8/12/16/28) and let One UI's preference for generous radii push toward
  the 16/28 end for cards and sheets.
- **Motion**: `MaterialTheme` currently supplies no custom typography or shapes at all — `Theme.kt:53`
  passes only `colorScheme`. Add `typography` and `shapes` so the system has a voice beyond colour.

---

## Doc inventory + recommendation

| Doc | Lines | Modified | Covers | Status | Recommendation |
|---|---|---|---|---|---|
| `CLAUDE.md` | 179 | 2026-07-07 | Bootstrap prompt; two stacked "SCOPE UPDATE" banners; original PRD framing | **Actively self-contradicting.** Says *"Don't add Clippy in any form"* — Clippy shipped (`ClippyAvatar.kt`). Says *"Don't add account signup"* — accounts shipped (`sync/`, overridden 2026-07-16 per HANDOVER). Says *"Zero Android code written. Gradle project not initialized"* — 60+ Kotlin files exist. A new agent reading top-to-bottom is actively misled. | **Rewrite from scratch.** Keep only: the three failure modes (long recordings / network drops / OS kills), `language=auto`, the prompt-injection boundary, no-IME, no-telemetry. Delete the "pre-implementation" body and both scope-update banners — fold their *conclusions* in as plain present-tense statements. Point to `HANDOVER.md` for state. |
| `HANDOVER.md` | 73 | 2026-07-16 | v2.0.0-nightly.1 accounts/sync, Room v4→v5, sync triggers, what's owed | **Accurate and current.** The best doc here. Correctly asserts it wins over `CLAUDE.md`. | **Keep. Promote to the entry point.** Make `CLAUDE.md` a thin pointer to it. Add the outstanding real-device QA (Google OAuth round-trip, cross-device pull, tombstones) plus the tile-mic question from P1-8. |
| `PRD.md` | 356 | **2026-05-11** | Original product requirements | **Two months stale and contradicted throughout.** Opens with *"No Clippy, no character, no theming dance"* and *"stripped down"*. The app now has 4 avatars, an onboarding character, sync, import, and usage meters. Describes a product that was deliberately abandoned. | **Retire** → `docs/archive/PRD_2026-05-11.md` with a header: "Historical. Superseded by HANDOVER.md; scope was expanded 2026-05-24 and again 2026-07-16." Valuable as a record of *why* decisions were made; actively harmful as a spec. |
| `README.md` | 70 | 2026-06-29 | Public one-pager, download links | **Stale links.** Advertises *"Codex preview download: v1.2.0-codex.2"* and *"Last Claude stable: v1.1.0"*. Actual stable is **v1.4.0**, actual nightly **v2.0.0-nightly.1** (per HANDOVER). A visitor downloads a 3-versions-old APK. | **Keep, update now.** Fix version links; drop the "Codex preview" framing (see below). Point at the Releases page rather than pinned URLs so it can't rot again. |
| `CODEX_HANDOVER_2026-06-29.md` | 63 | 2026-06-29 | Codex's session log; verbatim owner voice-transcript; pre-edit baseline commit | **Agent-specific and obsolete.** Its entire purpose was handing from Codex to the next agent — that handoff completed months ago and Codex is being retired. Reads as a transcript of a dictated prompt, not a spec. Its baseline (`a219c5a`) is long superseded. | **Retire** → `docs/archive/`. `CLAUDE.md:7` currently instructs agents to *"read CODEX_HANDOVER_2026-06-29.md after HANDOVER.md"* — **delete that instruction**. This is the clearest example of a doc that only made sense to the retired agent. |
| `DESKTOP_LINKAGE.md` | 86 | **2026-05-11** | What to port from desktop: Groq shape, prompts, provider abstraction | **Partly stale, still useful.** Says *"Two providers: Groq (Llama 3.3 70B), Gemini (2.5 Pro)"* — there are now four STT providers (`ProviderCatalog`) and three LLM providers. Doesn't mention the avatar packs, which is precisely the linkage that's now broken (P1-3). | **Merge + refresh.** Fold into a new `docs/DESKTOP_PARITY.md` with a live gap table: providers ✅, prompts ✅, avatar packs ❌ (2 of 3 missing), themes ❌ (1 of 3), avatar animation ❌, sleeping state ❌. Make it the standing scorecard. |
| `docs/AUDIT_2026-07-06_FABLE.md` | 312 | 2026-07-06 | Previous audit; RC-1.x / RC-2.x reliability fixes | **Historically important, load-bearing.** RC-1.1/1.2/1.3/1.4/2.1/2.2/2.3/2.4 are cited by ID in ~15 code comments (`OverlayVisibility.kt:12`, `DeliveryManager.kt:26`, `WisprFoxAccessibilityService.kt:31`, `KeyboardHeuristics.kt:8`, `DeliveryDecision.kt:5`…). Deleting it orphans them. But note P1-4 shows RC-1.1's "fox sticks around" bug is **still reachable** via the stuck-pipeline path. | **Keep as historical record.** Add a header pointing here as the successor. Don't delete — the RC-* references make it a live glossary. |
| `docs/activation-tradeoff.md` | 150 | **2026-05-11** | Phase 0: overlay vs QS tile vs 4 alternatives | **Stale but its conclusion is load-bearing.** Header says *"Awaiting user sign-off before any activation code lands"* — that shipped 14 months of commits ago. But it's the only record of *why* the overlay was chosen over the alternatives, and P1-8 shows the overlay is now doing double duty (activation surface **and** the FGS/mic-while-in-background exemption). | **Keep, re-header.** Change status to "Decided 2026-05: overlay + QS tile. Retained for rationale." Add a line recording the SYSTEM_ALERT_WINDOW/while-in-use dependency — that's a load-bearing consequence nobody has written down. |
| `docs/SPIKE_INSTRUCTIONS.md` | 107 | **2026-05-11** | Phase 1 mic-survival spike on a Note 10+ | **Fully obsolete.** Targets the Note 10+, which `CLAUDE.md`'s own scope update explicitly demoted (*"Note 10+ is nice-to-have, no longer the hard floor"*). The spike was executed; the app ships. Also recommends Android Studio "Koala / Ladybug (May 2026 build)" — those are 2024 releases. | **Retire** → `docs/archive/`. |
| `docs/RELEASE_NOTES_v*.md` (×4) | — | various | v1.2.0-codex.2, v1.3.0-nightly.1, v1.4.0, v2.0.0-nightly.1 | Fine. Per-release, correctly scoped, no rot. | **Keep.** Consider consolidating into `CHANGELOG.md` once there are ~8+. |
| `docs/sitemap.xml` | 9 | 2026-06-29 | GitHub Pages sitemap | Trivial; `lastmod` is stale but harmless. | **Keep.** |

### Documentation recommendation, summarised

The doc set has the classic problem: **four documents claim to be the entry point** (`CLAUDE.md` says
read it first, then says read `HANDOVER.md` first, then says also read `CODEX_HANDOVER`; `PRD.md` says
read `CLAUDE.md` first; `README.md` is public-facing). Collapse to:

1. **`CLAUDE.md`** — thin. Fifteen lines: what this is, the three failure modes, the hard rules that
   still hold, and "read `HANDOVER.md` for current state."
2. **`HANDOVER.md`** — the single source of truth. Already is; just make it official.
3. **`docs/DESKTOP_PARITY.md`** — new, replaces `DESKTOP_LINKAGE.md`, with the live gap scorecard.
4. **`docs/archive/`** — `PRD.md`, `CODEX_HANDOVER_2026-06-29.md`, `SPIKE_INSTRUCTIONS.md`.
5. Keep `AUDIT_2026-07-06_FABLE.md`, `activation-tradeoff.md` (re-headered), release notes, this file.

**Codex-specific cleanup** (the agent is being retired):
- `CLAUDE.md:7` — remove the "read CODEX_HANDOVER after HANDOVER" instruction.
- `README.md:10` — remove the "Codex preview download" link and framing. Users shouldn't be pointed at
  an agent-branded build.
- Release notes `v1.2.0-codex.2` — keep the file (it's history), but the README shouldn't surface it.
- The `-codex` version-tag convention should stop; `HANDOVER.md`'s `-nightly.N` scheme matches the
  workspace-wide release discipline in the root `CLAUDE.md`.
- Note: `avatar.json` files in the desktop repo carry `"author": "Codex"` and
  `"version": "1.0.0-codex"`. Cosmetic, but if the packs are ported to Android (P1-3), that's the
  moment to drop the agent branding from the manifests.

---

## Suggested sequencing

Ordered by the owner's stated priorities, with dependencies respected:

1. **P0-2 avatar anchor jump** — self-contained, ~20 lines, highest visible-annoyance-per-effort.
2. **P0-1 spacing scale** — must land before any redesign; everything else aligns to it.
3. **P1-2 edge-to-edge + insets**, then **P1-8 toolchain bump** to SDK 36 / current Compose BOM.
   Strictly ordered: 36 removes the opt-out.
4. **P1-1 dark theme** — needs the BOM bump for M3 Expressive tokens to be worth it.
5. **P0-3 Settings IA** — the big one, but it wants the spacing scale and shared components first.
6. **P0-4 paste reliability** — items #1 (verify) and #2 (main thread) are independent of all UI work
   and can proceed in parallel.
7. **P1-3 sprites + animation** — largest asset lift; do after the pack model is designed.
8. **P1-4 / P1-5 / P1-6 / P1-7** — small, independent correctness fixes; land opportunistically.
9. Home / History / Onboarding redesign.

---

*Sources for platform facts:*
- [Behavior changes: Apps targeting Android 16 or higher — Android Developers](https://developer.android.com/about/versions/16/behavior-changes-16)
- [Galaxy S23 Ultra Receives One UI 8.5 Update — Samsung Members](https://r1.community.samsung.com/t5/galaxy-s/galaxy-s23-ultra-receives-one-ui-8-5-update/td-p/38088201)
- [Samsung's Rolls Out One UI 8.5 Update To More Older Galaxy Phones — Forbes](https://www.forbes.com/sites/jaymcgregor/2026/05/23/samsung-one-ui-85-release-date-for-galaxy-s23-galaxy-a56/)
- [One UI 8 update with Android 16 — Samsung](https://www.samsung.com/latin_en/support/mobile-devices/one-ui-8-upgrade-with-android-16/)
