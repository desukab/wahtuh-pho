# UI Concept — Retro 8-Bit Games Vibe

**Date:** 2026-10-19 · **Status:** concept doc, not final art. All ideas here
are original; nothing is taken from any competitor's visuals or assets.

## Design intent

Wahtuh Pho should *feel* like a game, not a SaaS settings panel. The
teardown taught us the competitor's **flow structure** (educate → try it
yourself → tutorial → permissions; bubble as primary surface; graceful error
states everywhere). We keep the structural lessons and reskin everything in
an original retro-gaming language. Deliberate differentiation: where they are
clean and minimal, we are chunky, playful, and a little loud.

## Typography (user decision — locked)

- **Latin/UI typeface: Fredoka** (rounded sans). NOT a pixel font — the retro
  feel comes from layout, iconography, and feedback, not from the letterforms.
- **Telugu companion: Baloo Tammudu 2.** Fredoka has no Telugu glyphs, so
  Telugu text needs a companion with the same rounded, chunky energy. Baloo
  Tammudu 2 matches Fredoka's rounded feel far better than a neutral system
  font, keeping one visual voice across scripts.
- **Practical constraint:** pixel/bitmap fonts essentially never support
  Telugu script (complex shaping, hundreds of glyphs), which is another reason
  the "8-bit" identity must live in chrome and motion, not in the typeface.
  Rule of thumb: **type in Fredoka/Baloo, chrome in 8-bit.**
- Retro flavor *around* the type: chunky outlined badges, "PRESS START"-style
  labels, dialog boxes with stepped corners, HP-bar-style progress meters.

## Visual language

- **Palette:** limited and bold — deep navy background, hot magenta/cyan
  accents, sunshine yellow for highlights, off-white text. High contrast,
  arcade-cabinet energy.
- **Iconography:** pixel-art style icons (mic, bubble, waveform, dictionary
  book, gear) drawn as chunky low-res sprites with hard 2px outlines. No
  gradients on icons; flat fills + dithered shadows.
- **Shapes:** stepped/aliased corners on cards and dialogs (the "staircase"
  pixel edge), thick borders, drop shadows offset by whole pixels.
- **Motion:** snappy, stepped animations (few frames, no easing curves) for
  state changes; screen transitions that wipe like level changes.

## Flow mapping (their structure → our skin)

### Onboarding → "New Game"
Frame first-run as starting a new game:
- Intro education screens become **cutscene cards** ("A wild voice-to-text
  appeared!").
- Their try-it-yourself dictation (email + note) becomes the **tutorial
  level**: "QUEST: Dictate your first message" with a progress bar styled as
  an XP meter. Completing it grants the first achievement badge.
- Permission requests become **power-up unlocks** ("Unlock MICROPHONE to
  continue"), each with a one-line education card *before* the system dialog
  (keeping their education-before-permission pattern).
- Wrap-up becomes the **"READY PLAYER ONE"** screen with a big START button.

### Dictation overlay/bubble → "Battle HUD"
- The floating bubble becomes a **sprite companion** (original character —
  e.g. a tiny robot or blob, ours, not theirs) with pixel-art state sprites:
  idle (bouncing), listening (sprite "ears" pulsing with a stepped waveform),
  processing (spinning star sprite), error (dizzy sprite + "TRY AGAIN?").
- Draggable like theirs; position persists. Snooze becomes "PAUSE".
- Haptic ticks on state changes (they do haptics too — it's a good pattern,
  ours with chunkier timing).

### Transcript editing & history → "Quest Log"
- History list styled as a quest log: each dictation is an entry with a
  timestamp "date stamp" and a word-count "score".
- Editing view: chunky cursor, word-level confidence shown as HP bars on
  low-confidence words (tap to correct — "heal" the word).
- Their transcription-issue reporting becomes "REPORT A BUG" with a playful
  bug-sprite.

### Settings → "Options Menu"
- Classic game options screen: rows with chunky toggle switches styled as
  arcade DIP switches.
- Bubble size/opacity become sliders styled as volume knobs.
- Language selection: flag-sprite chips (English / Telugu / both).
- Local-data retention: "MEMORY CARD" metaphor (keep / auto-erase 24h /
  never save).

### Paywall (we have none — but the pattern inverts)
- They gate with word caps; we have no caps. Our equivalent "progression"
  surface is **stats as high scores**: words dictated, streaks, longest
  session — a local-only "HIGH SCORES" screen. No accounts, no tiers.

## Game-like feedback systems (local only, no backend)

- **Streaks:** consecutive days with ≥1 dictation → flame sprite counter.
- **XP/levels:** words dictated accumulate XP; level names are playful
  ("Mumbler" → "Chatterbox" → "Town Crier"). Purely local, stored in Room.
- **Achievements:** first dictation, first Telugu dictation, 1000 words,
  7-day streak, first snippet used. Toast-style "ACHIEVEMENT UNLOCKED" banner
  with a jingle-like haptic pattern.
- **Micro-interactions:** button presses "thunk" (scale-pop in stepped
  frames), toggles click like arcade buttons.

## Constraints & honest notes

1. **Telugu shaping is non-negotiable.** Telugu needs proper complex-text
   shaping (conjuncts, vowel signs). Baloo Tammudu 2 handles it; never attempt
   to render Telugu in a bitmap/pixel font or hand-drawn sprite text.
2. **Accessibility service + overlay have hard platform limits** (draw-over
   permission, battery killers) — the playful skin must not obscure the
   serious permission education. Keep their education-before-permission
   pattern; just reskin it.
3. **Performance:** stepped animations are cheap, but the overlay bubble must
   stay under strict frame budgets — sprite animation over property
   animation where possible.
4. **This doc is direction, not spec.** Final palette values, sprite sheets,
   and motion timings get decided when the Android app shell is built.
