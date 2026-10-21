# UI notes

The layout follows the interaction model users expect from modern dictation apps:

- **Home** is a transcript dashboard, not a settings form. It leads with a serif headline, one primary dictation card, setup status, stats, recent transcripts, and a single privacy toggle.
- **Bottom navigation** has three destinations: Home, Dictionary, Settings. Configuration lives in Settings.
- **Cards** are deep charcoal with 18–24 dp radii; the accent is periwinkle on cream text; type pairs a serif display face with sans body.
- **Bubble** is a cream circle with a purple waveform. While recording it expands into a pill with a live timer and animated bars. Transcribing turns it periwinkle.
- **History** groups entries under Today/Yesterday/date and keeps a one-tap Copy plus a raw-transcript action.
- **Dictionary** is a focused editor for `from=to` lines, not a buried preference.

These are original layouts and drawables. No Wispr Flow assets, fonts, copy, or code are included. The goal is to match the *flow* — speak, see the bubble, get text, review history — while keeping Wahtuh Pho's local-first, no-account behavior.
