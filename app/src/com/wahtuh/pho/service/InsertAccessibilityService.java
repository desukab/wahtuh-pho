package com.wahtuh.pho.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

/**
 * Inserts text into the field that currently has input focus. It reads the
 * focused field only at the moment the user asks to insert, never in the
 * background, and refuses password fields.
 */
public final class InsertAccessibilityService extends AccessibilityService {
    private static volatile InsertAccessibilityService instance;
    private AccessibilityNodeInfo focused;
    private String lastPackage;
    private LastInsert lastInsert;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable bubbleSync = this::syncBubble;

    private static final class LastInsert {
        final String before;
        final String after;
        final int start;
        final int end;

        LastInsert(String before, String after, int start, int end) {
            this.before = before;
            this.after = after;
            this.start = start;
            this.end = end;
        }
    }

    public static InsertAccessibilityService get() { return instance; }

    public static final class Result {
        public final boolean ok;
        public final String message;
        Result(boolean ok, String message) { this.ok = ok; this.message = message; }
    }

    public static boolean isEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> list = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : list) {
            if (info.getId() != null && info.getId().startsWith(context.getPackageName() + "/")) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED
                    | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
        scheduleBubbleSync();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED
                || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            captureFocus();
        }
        if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED
                || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            scheduleBubbleSync();
        }
    }

    public static void refreshBubbleVisibility() {
        InsertAccessibilityService service = instance;
        if (service != null) service.scheduleBubbleSync();
    }

    /** Package of the field showing the bubble. Used only to pick a tone preset. */
    public static String focusedPackage() {
        InsertAccessibilityService service = instance;
        return service == null ? null : service.lastPackage;
    }

    /**
     * Best-effort nearby field text for the opt-in AI term-context feature.
     * The controller immediately reduces this to names/code terms. Passwords
     * and this app's own fields are always refused.
     */
    public static String focusedContext(int maxChars) {
        InsertAccessibilityService service = instance;
        if (service == null) return "";
        AccessibilityNodeInfo node = service.focused;
        if (node == null || !node.isEditable() || !node.isEnabled()
                || !node.isVisibleToUser() || node.isPassword()) return "";
        CharSequence pkg = node.getPackageName();
        if (pkg != null && service.getPackageName().contentEquals(pkg)) return "";
        CharSequence value = node.getText();
        if (value == null) return "";
        String text = value.toString().trim();
        if (text.isEmpty()) return "";
        if (maxChars > 0 && text.length() > maxChars) {
            text = text.substring(text.length() - maxChars);
        }
        return text;
    }

    /** Currently selected text in the focused field, or empty when nothing is selected. */
    public static String selectedText(int maxChars) {
        InsertAccessibilityService service = instance;
        if (service == null) return "";
        AccessibilityNodeInfo node = service.editableFocus();
        if (node == null || node.isPassword()) return "";
        CharSequence pkg = node.getPackageName();
        if (pkg != null && service.getPackageName().contentEquals(pkg)) return "";
        CharSequence value = node.getText();
        if (value == null) return "";
        String text = value.toString();
        int start = node.getTextSelectionStart();
        int end = node.getTextSelectionEnd();
        if (start < 0 || end <= start || end > text.length()) return "";
        String selected = text.substring(start, end);
        if (maxChars > 0 && selected.length() > maxChars) return "";
        return selected;
    }

    /** Replaces the current selection only if it still matches the text we read. */
    public Result replaceSelection(String expected, String replacement) {
        if (expected == null || expected.isEmpty()) {
            return new Result(false, "Select text first");
        }
        if (replacement == null) {
            return new Result(false, "The command returned no text");
        }
        AccessibilityNodeInfo node = editableFocus();
        if (node == null) return new Result(false, "No focused text field");
        if (node.isPassword()) return new Result(false, "Refusing to edit a password field");
        CharSequence pkg = node.getPackageName();
        if (pkg != null && getPackageName().contentEquals(pkg)) {
            return new Result(false, "Open another app, then use the floating bubble");
        }
        CharSequence current = node.getText();
        String old = current == null ? "" : current.toString();
        int start = node.getTextSelectionStart();
        int end = node.getTextSelectionEnd();
        if (start < 0 || end <= start || end > old.length()) {
            return new Result(false, "The selection changed");
        }
        if (!old.substring(start, end).equals(expected)) {
            return new Result(false, "The selection changed");
        }
        String insert = normalize(replacement, node, old, start, end);
        String updated = old.substring(0, start) + insert + old.substring(end);
        int cursor = start + insert.length();

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && !insert.isEmpty()) {
            try {
                cm.setPrimaryClip(ClipData.newPlainText("Wahtuh Pho", insert));
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                    String read = readFocusText();
                    if (read == null || read.equals(updated)) {
                        lastInsert = new LastInsert(old, updated, start, end);
                        if (read != null) placeCursor(cursor);
                        return new Result(true, "Replaced");
                    }
                }
            } catch (Throwable ignored) {
                // Fall through to ACTION_SET_TEXT.
            }
        }

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated);
        if (performVerified(node, updated, args, old, start, end, cursor)) {
            lastInsert = new LastInsert(old, updated, start, end);
            return new Result(true, "Replaced");
        }
        SystemClock.sleep(180);
        if (updated.equals(readFocusText())) {
            lastInsert = new LastInsert(old, updated, start, end);
            placeCursor(cursor);
            return new Result(true, "Replaced");
        }
        if (cm != null && !insert.isEmpty()) {
            cm.setPrimaryClip(ClipData.newPlainText("Wahtuh Pho", insert));
            return new Result(false, "This app refused the replacement; text copied instead");
        }
        return new Result(false, "This app refused the deletion");
    }

    private void scheduleBubbleSync() {
        main.removeCallbacks(bubbleSync);
        main.postDelayed(bubbleSync, 90);
    }

    private void syncBubble() {
        DictationService.onTextInput(keyboardVisible() && editableTextFocused());
    }

    private boolean keyboardVisible() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return false;
        Rect bounds = new Rect();
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;
            bounds.setEmpty();
            window.getBoundsInScreen(bounds);
            if (!bounds.isEmpty()) return true;
        }
        return false;
    }

    private boolean editableTextFocused() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION
                        && windowHasEditableField(window)) return true;
            }
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (field == null && root.isEditable()) field = root;
        boolean usable = usableTextField(field);
        if (usable) rememberPackage(field);
        if (field != null && field != root) field.recycle();
        root.recycle();
        return usable;
    }

    private boolean windowHasEditableField(AccessibilityWindowInfo window) {
        AccessibilityNodeInfo root = window.getRoot();
        if (root == null) return false;
        AccessibilityNodeInfo field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (field == null && root.isEditable()) field = root;
        boolean usable = usableTextField(field);
        if (usable) rememberPackage(field);
        if (field != null && field != root) field.recycle();
        root.recycle();
        return usable;
    }

    private void rememberPackage(AccessibilityNodeInfo field) {
        CharSequence pkg = field == null ? null : field.getPackageName();
        if (pkg != null) lastPackage = pkg.toString();
    }

    private boolean usableTextField(AccessibilityNodeInfo field) {
        if (field == null || !field.isEditable() || !field.isEnabled()
                || !field.isVisibleToUser() || field.isPassword()) return false;
        CharSequence pkg = field.getPackageName();
        return pkg == null || !getPackageName().contentEquals(pkg);
    }

    private void captureFocus() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f == null && root.isEditable()) f = root;
        if (f == null) return;
        AccessibilityNodeInfo old = focused;
        focused = AccessibilityNodeInfo.obtain(f);
        rememberPackage(f);
        if (old != null) old.recycle();
        f.recycle();
    }

    public Result insert(String text) {
        if (text == null || text.isEmpty()) return new Result(false, "Nothing to insert");
        AccessibilityNodeInfo node = editableFocus();
        if (node == null) return new Result(false, "No focused text field");
        if (node.isPassword()) return new Result(false, "Refusing to type into a password field");

        CharSequence pkg = node.getPackageName();
        if (pkg != null && getPackageName().contentEquals(pkg)) {
            return new Result(false, "Open another app, then use the floating bubble");
        }

        CharSequence existing = node.getText();
        String rawOld = existing == null ? "" : existing.toString();
        int start = node.getTextSelectionStart();
        int end = node.getTextSelectionEnd();
        com.wahtuh.pho.text.FieldText.Result field = com.wahtuh.pho.text.FieldText.clean(
                rawOld, asString(node.getHintText()), asString(node.getTooltipText()),
                asString(node.getContentDescription()), node.getViewIdResourceName(),
                asString(node.getPackageName()), start, end);
        String old = field.text;
        boolean placeholder = field.placeholder;
        if (placeholder) {
            start = 0;
            end = 0;
        }
        if (start < 0 || start > old.length()) start = old.length();
        if (end < start || end > old.length()) end = start;

        String insert = normalize(text, node, old, start, end);
        if (insert.isEmpty()) return new Result(false, "Nothing to insert");
        if (start > 0 && start <= old.length() && !insert.isEmpty()) {
            char prev = old.charAt(start - 1);
            char first = insert.charAt(0);
            if (Character.isLetterOrDigit(prev) && Character.isLetterOrDigit(first)) {
                insert = " " + insert;
            }
        }
        String updated = old.substring(0, start) + insert + old.substring(end);
        int cursor = start + insert.length();

        // Paste first: it keeps the field's own undo stack and IME state, which
        // matters in messaging apps and rich editors. ACTION_SET_TEXT is the
        // fallback for fields that reject clipboard paste.
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && !placeholder) {
            try {
                cm.setPrimaryClip(ClipData.newPlainText("Wahtuh Pho", insert));
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                    String read = readFocusText();
                    if (read == null) {
                        lastInsert = new LastInsert(old, updated, start, end);
                        return new Result(true, "Pasted");
                    }
                    if (read.equals(updated)) {
                        lastInsert = new LastInsert(old, updated, start, end);
                        placeCursor(cursor);
                        return new Result(true, "Inserted");
                    }
                }
            } catch (Throwable ignored) {
                // Fall through to ACTION_SET_TEXT.
            }
        }

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated);
        if (performVerified(node, updated, args, old, start, end, cursor)) {
            lastInsert = new LastInsert(old, updated, start, end);
            return new Result(true, "Inserted");
        }

        // Some IMEs commit asynchronously; give the app one more beat and check.
        SystemClock.sleep(180);
        if (updated.equals(readFocusText())) {
            lastInsert = new LastInsert(old, updated, start, end);
            placeCursor(cursor);
            return new Result(true, "Inserted");
        }

        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Wahtuh Pho", text));
        return new Result(false, "This app refused insertion; text copied instead");
    }

    private boolean performVerified(AccessibilityNodeInfo node, String expected, Bundle args,
                                    String old, int start, int end, int cursor) {
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false;
        String read = readFocusText();
        if (read == null) return true;
        if (!read.equals(expected)) return false;
        placeCursor(cursor);
        return true;
    }

    /** Undo only when the field still contains exactly what we inserted. */
    public Result undoLast() {
        LastInsert last = lastInsert;
        if (last == null) return new Result(false, "Nothing to undo");
        AccessibilityNodeInfo node = editableFocus();
        if (node == null) return new Result(false, "Focus the field first");
        CharSequence current = node.getText();
        if (current == null || !last.after.equals(current.toString())) {
            return new Result(false, "The field changed since the last insertion");
        }
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, last.before);
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return new Result(false, "This app does not allow undo");
        }
        Bundle sel = new Bundle();
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, last.start);
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, last.end);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel);
        lastInsert = null;
        return new Result(true, "Undone");
    }

    private AccessibilityNodeInfo editableFocus() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo f = root == null ? null
                : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f == null && root != null && root.isEditable()) f = root;
        if (f == null && focused != null && focused.isEditable()) f = focused;
        if (f == null) return null;
        if (!f.isEditable() || !f.isEnabled()) return null;
        return f;
    }

    private String readFocusText() {
        AccessibilityNodeInfo node = editableFocus();
        if (node == null) return null;
        CharSequence t = node.getText();
        return t == null ? null : t.toString();
    }

    private String normalize(String text, AccessibilityNodeInfo node, String old,
                             int start, int end) {
        String insert = text;
        if (node.getMaxTextLength() > 0) {
            int room = node.getMaxTextLength() - (old.length() - (end - start));
            if (room <= 0) return "";
            if (insert.length() > room) insert = insert.substring(0, room);
        }
        boolean multiline = (node.getInputType() & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if (!multiline) insert = insert.replace('\n', ' ');
        return insert;
    }

    private static String asString(CharSequence value) {
        return value == null ? null : value.toString();
    }

    private void placeCursor(int cursor) {
        AccessibilityNodeInfo node = editableFocus();
        if (node == null) return;
        Bundle sel = new Bundle();
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor);
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel);
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        main.removeCallbacks(bubbleSync);
        DictationService.onTextInput(false);
        instance = null;
        lastPackage = null;
        if (focused != null) {
            focused.recycle();
            focused = null;
        }
        super.onDestroy();
    }
}
