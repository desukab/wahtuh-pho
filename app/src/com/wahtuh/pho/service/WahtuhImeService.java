package com.wahtuh.pho.service;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.wahtuh.pho.MainActivity;
import com.wahtuh.pho.data.Settings;
import com.wahtuh.pho.dictation.DictationController;
import com.wahtuh.pho.dictation.DictationListener;
import com.wahtuh.pho.dictation.DictationResult;
import com.wahtuh.pho.text.SensitiveApps;
import com.wahtuh.pho.ui.Ui;

/** Voice-first keyboard. Every key is a normal key; dictation is one tap away. */
public final class WahtuhImeService extends InputMethodService implements DictationListener {
    private LinearLayout root;
    private TextView status;
    private TextView preview;
    private String composing = "";
    private Button mic;
    private LinearLayout keys;
    private boolean shifted;
    private boolean numeric;

    private static final String[][] LETTER_ROWS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"z", "x", "c", "v", "b", "n", "m"}
    };

    private static final String[][] NUMBER_ROWS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"-", "/", ":", ";", "(", ")", "$", "&", "@", "\""},
            {".", ",", "?", "!", "'", "*", "#", "%", "+", "="}
    };

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        mic = Ui.button(this, "Dictate", Ui.ACCENT, Ui.INK);
        mic.setOnClickListener(v -> toggleDictation());
        bar.addView(mic, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1.2f));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 6), 0);
        status = Ui.text(this, dictationLabel(), 12, Ui.MUTED, false);
        preview = Ui.text(this, "", 12, Ui.TEXT, false);
        preview.setSingleLine(true);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(status);
        texts.addView(preview);
        bar.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.6f));

        Button setup = Ui.button(this, "Setup", Ui.SURFACE2, Ui.TEXT);
        setup.setOnClickListener(v -> openSettings());
        bar.addView(setup, new LinearLayout.LayoutParams(Ui.dp(this, 78), Ui.dp(this, 46)));
        root.addView(bar);

        keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.VERTICAL);
        keys.setPadding(0, Ui.dp(this, 6), 0, 0);
        root.addView(keys, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        renderKeys();
        return root;
    }

    private void renderKeys() {
        keys.removeAllViews();
        String[][] rows = numeric ? NUMBER_ROWS : LETTER_ROWS;
        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            if (!numeric && r == 2) {
                row.addView(key(shifted ? "SHIFT" : "shift", v -> {
                    shifted = !shifted;
                    renderKeys();
                }, 1.25f, Ui.SURFACE2));
            }
            for (String label : rows[r]) {
                String shown = shifted && !numeric ? label.toUpperCase() : label;
                row.addView(key(shown, v -> type(label), 1f, Ui.SURFACE2));
            }
            if (!numeric && r == 2) {
                row.addView(key("DEL", v -> backspace(), 1.25f, Ui.SURFACE2));
            }
            if (numeric && r == 2) {
                row.addView(key("DEL", v -> backspace(), 1.1f, Ui.SURFACE2));
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
            rp.topMargin = Ui.dp(this, 5);
            keys.addView(row, rp);
        }

        LinearLayout last = new LinearLayout(this);
        last.setOrientation(LinearLayout.HORIZONTAL);
        last.setGravity(Gravity.CENTER);
        last.addView(key(numeric ? "ABC" : "?123", v -> {
            numeric = !numeric;
            renderKeys();
        }, 1.3f, Ui.SURFACE2));
        last.addView(key(",", v -> type(","), 0.8f, Ui.SURFACE2));
        last.addView(key("space", v -> type(" "), 4f, Ui.SURFACE2));
        last.addView(key(".", v -> type("."), 0.8f, Ui.SURFACE2));
        last.addView(key("enter", v -> enter(), 1.5f, Ui.ACCENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
        lp.topMargin = Ui.dp(this, 5);
        keys.addView(last, lp);
    }

    private Button key(String label, View.OnClickListener action, float weight, int bg) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(label.length() > 2 ? 12 : 16);
        b.setTextColor(label.equals("enter") ? Ui.INK : Ui.TEXT);
        b.setBackground(Ui.rounded(bg, 9, this));
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setOnClickListener(action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void type(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.commitText(s, 1);
        if (shifted && !numeric) {
            shifted = false;
            renderKeys();
        }
    }

    private void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before == null || before.length() == 0) {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        } else {
            ic.deleteSurroundingText(1, 0);
        }
    }

    private void enter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        android.view.inputmethod.EditorInfo info = getCurrentInputEditorInfo();
        int options = info == null ? 0 : info.imeOptions;
        int action = options & android.view.inputmethod.EditorInfo.IME_MASK_ACTION;
        boolean noEnterAction = (options
                & android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        if (!noEnterAction
                && action != android.view.inputmethod.EditorInfo.IME_ACTION_NONE
                && action != android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
        } else {
            ic.commitText("\n", 1);
        }
    }

    private void toggleDictation() {
        if (blockedTarget()) {
            status.setText("Sensitive field — dictation off");
            Toast.makeText(this, "Dictation is off in banking, wallet, and password fields",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Microphone permission needed");
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra("request_mic", true);
            startActivity(i);
            return;
        }
        DictationController c = DictationController.get(this);
        if (c.busy()) c.stop();
        else c.start(this);
    }

    private String dictationLabel() {
        Settings settings = new Settings(this);
        return settings.providerLabel() + " dictation";
    }

    private boolean blockedTarget() {
        android.view.inputmethod.EditorInfo info = getCurrentInputEditorInfo();
        if (info == null) return false;
        if (passwordInput(info.inputType)) return true;
        return new Settings(this).blockSensitiveApps()
                && SensitiveApps.blocks(info.packageName);
    }

    private static boolean passwordInput(int type) {
        int kind = type & InputType.TYPE_MASK_CLASS;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        if (kind == InputType.TYPE_CLASS_TEXT) {
            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        }
        return kind == InputType.TYPE_CLASS_NUMBER
                && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    private void openSettings() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    @Override
    public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        boolean blocked = blockedTarget();
        if (status != null) status.setText(blocked
                ? "Sensitive field — dictation off" : dictationLabel());
        if (mic != null) {
            mic.setEnabled(!blocked);
            mic.setAlpha(blocked ? 0.4f : 1f);
        }
        if (preview != null) preview.setText("");
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        clearComposing();
        DictationController c = DictationController.get(this);
        if (c.busy()) c.cancel();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onState(String state, String message) {
        if (status == null) return;
        status.setText(message == null ? state : message);
        if (DictationController.RECORDING.equals(state)) {
            clearComposing();
            if (preview != null) preview.setText("");
            mic.setText("Stop");
            mic.setBackground(Ui.rounded(Ui.DANGER, 14, this));
        } else if (DictationController.TRANSCRIBING.equals(state)
                || DictationController.PREPARING.equals(state)) {
            clearComposing();
            if (preview != null) preview.setText("");
            mic.setText("...");
            mic.setBackground(Ui.rounded(Ui.ACCENT2, 14, this));
        } else {
            clearComposing();
            if (preview != null) preview.setText("");
            mic.setText("Dictate");
            mic.setBackground(Ui.rounded(Ui.ACCENT, 14, this));
            mic.setAlpha(1f);
        }
    }

    @Override
    public void onPartial(String text) {
        if (preview == null || text == null) return;
        if (!DictationController.RECORDING.equals(DictationController.get(this).state())) return;
        String clean = text.trim();
        if (clean.isEmpty() || blockedTarget()) return;
        preview.setText(clean);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.setComposingText(clean, 1);
            composing = clean;
        }
    }

    private void clearComposing() {
        if (composing.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.setComposingText("", 1);
            ic.finishComposingText();
        }
        composing = "";
    }

    @Override
    public void onLevel(float rms) {
        if (mic == null) return;
        mic.setAlpha(0.65f + Math.min(0.35f, rms * 6f));
    }

    @Override
    public void onResult(DictationResult result) {
        if (result == null || result.text.isEmpty()) return;
        if (blockedTarget()) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Wahtuh Pho", result.text));
            if (status != null) status.setText("Sensitive field — copied instead");
            Toast.makeText(this, "Sensitive field: text copied, not inserted",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        clearComposing();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            CharSequence before = ic.getTextBeforeCursor(1, 0);
            boolean needsSpace = before != null && before.length() > 0
                    && Character.isLetterOrDigit(before.charAt(0));
            ic.commitText((needsSpace ? " " : "") + result.text, 1);
            if (preview != null) preview.setText(result.text);
            if (status != null) status.setText("Inserted");
        } else {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Wahtuh Pho", result.text));
            Toast.makeText(this, "Copied: no active text field", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onError(String message) {
        clearComposing();
        if (status != null) status.setText(message);
        if (mic != null) {
            mic.setText("Dictate");
            mic.setBackground(Ui.rounded(Ui.ACCENT, 14, this));
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
