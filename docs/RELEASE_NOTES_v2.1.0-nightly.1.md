# wispr-fox for Android — v2.1.0-nightly.1

The app worked, but it didn't look like anyone had thought about it. This
build is mostly about fixing that — plus three real bugs underneath.

## The app looks like the desktop app now

Every screen has been rebuilt on a single spacing system. Before this, the
four screens each used a different page margin and there were nine different
card paddings, which is why nothing ever quite lined up. Now it does.

**Themes.** The three palettes from the desktop app — Foxy (cream), Dark, and
Retro — are finally here, plus an Auto option that follows your phone. Your
phone and your laptop look related again. If you'd rather the app match your
wallpaper, Material You is available too, but it's off by default: the fox is
cream-and-orange, and a blue build stops looking like wispr-fox.

**Settings makes sense.** It used to be one very long scroll of sixteen
unrelated groups with all seven API key fields sitting on screen whether you
used those providers or not. It's now eight rows — Transcription, Cleanup &
modes, Foxy, Delivery, Usage, Storage, Account, About — each showing its
current setting, each opening its own page. Only the key for the provider
you've actually chosen is shown; the rest are tucked behind an expander.

**Home and History** lost the duplicated banners and the wasted space at the
top, and History gained date headers and a search box.

The first screen of onboarding no longer draws underneath the status bar.

## Three bugs

**The fox stopped wandering off.** Tap it and it would slide to the right —
further when the speech bubble appeared, and it kept creeping as the status
text changed. The floating window is only as wide as its widest part, so
anything that appeared next to the fox pushed the fox itself sideways. It's
now pinned to where you left it, whatever else is on screen.

**A failed transcription no longer bricks recording.** If a transcription
died at the wrong moment, the app quietly got stuck and wouldn't record again
until you force-closed it. It now always recovers.

**Auto-paste stopped lying.** This is the big one. The app asked the text
field to paste and took "yes" as proof it worked — but "yes" only meant the
field had heard the request, not that your words arrived. So when a paste
silently did nothing, the app skipped its own retry, skipped the clipboard
fallback, and cheerfully told you "Pasted". That's why it felt like a coin
flip. It now checks that the text actually landed before saying so.

## Please keep an eye out

The paste fix waits a short moment to confirm your text arrived, then falls
back to another method if it hasn't. That wait is currently an educated guess.
If an app is slower than expected, the fallback could run just as the original
paste lands — and you'd see your text **twice**. If that happens (WhatsApp is
the most likely place), say so and the timing gets tuned to your device rather
than guessed at.

You may also occasionally see "Copied" instead of "Pasted" on a paste that
actually worked, in apps that don't report their contents back. Harmless, but
worth knowing it's the app being cautious rather than broken.

Everything here is verified by the automated tests (83 passing) and by the
build — but no one has held this build in their hands yet. That's what this
nightly is for.
