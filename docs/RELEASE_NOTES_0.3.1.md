# Wahtuh Pho 0.3.1

Follow-up to 0.3.0 with character-limit aware insertion. When a destination
field reports a character limit, the app now inserts the words that fit, copies
the remainder, and shows the dropped words in a notification instead of losing
them silently. This came from feedback on the 0.3.0 launch.

UI, permissions, and privacy behavior are unchanged from 0.3.0. The screenshots
below were captured from the 0.3.0 build on a vivo V2251 (1260 × 2800).

## Screenshots

- [Home](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/home.png)
- [Dictionary](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/words.png)
- [Settings](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/settings.png)
- [Offline arcade](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/arcade.png)
- [Snippets](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/snips.png) · [Styles](https://github.com/desukab/wahtuh-pho/blob/main/docs/screenshots/style.png)

## What changed in 0.3.1

- **Character-limit overflow.** The focused field's reported limit is respected
  before insertion. Text that does not fit is copied to the clipboard and shown
  in a BigText notification, with the dropped character count in the toast.
- **Silent truncation detection.** Fields that enforce a limit without exposing
  it are detected after insertion by comparing the read-back text. A partial
  insert is treated as a success with the missing tail reported, rather than a
  failed insert that overwrites the field.
- **Command Mode replacements** use the same fit-and-report path.
- **No silent data loss.** The existing undo, cursor placement, password-field
  refusal, and clipboard fallback behavior is preserved.

## Verification

- Text pipeline: **51/51** checks.
- VAD/audio: **11/11** checks.
- Character-fit: **18/18** checks.
- Final APK: `wahtuh-pho-0.3.1.apk`, 18 MB, arm64-v8a, minSdk 26, targetSdk 35,
  versionCode 4.
- `zipalign -c -v 4` passed.
- `apksigner verify` passed. Project signing certificate SHA-256:
  `4a8cf0a7bfaee36ce7a51ebc954e74ae16753a55d14640f2c625ba10c717f0a4`.
- SHA-256: `1f18f1c3adfa6affabf5d99b00bda6aee5d396a57cfcc948798ff2c499ef7381`

## Known gaps

Character-limit reporting is best-effort: fields that hide their text or
truncate without exposing a limit may still drop words. Streaming partials, local
Hinglish/Telugu breadth, dictionary auto-learn/sync, banking-app stand-down, and
per-app web-editor shims remain out of scope. Voice, Command Mode, retention, boot
recovery, Field Context, and selected-text replacement still need a broader field
pass. See [FEATURE_PARITY.md](https://github.com/desukab/wahtuh-pho/blob/main/docs/FEATURE_PARITY.md)
and [PROJECT_STATUS.md](https://github.com/desukab/wahtuh-pho/blob/main/docs/PROJECT_STATUS.md).

## Install

```sh
adb install -r wahtuh-pho-0.3.1.apk
```

Or open the APK on the phone. Grant microphone, overlay, accessibility, and
notification permissions in Settings, then start the bubble.
