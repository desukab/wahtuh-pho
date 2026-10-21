package com.wahtuh.pho.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Inserts text into the field that currently has input focus. It reads the
 * focused field only at the moment the user asks to insert, never in the
 * background, and refuses password fields.
 */
public final class InsertAccessibilityService extends AccessibilityService {
    private static volatile InsertAccessibilityService instance;
    private AccessibilityNodeInfo focused;

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
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED
                || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            captureFocus();
        }
    }

    private void captureFocus() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f == null && root.isEditable()) f = root;
        if (f == null) return;
        AccessibilityNodeInfo old = focused;
        focused = AccessibilityNodeInfo.obtain(f);
        if (old != null) old.recycle();
        f.recycle();
    }

    public Result insert(String text) {
        if (text == null || text.isEmpty()) return new Result(false, "Nothing to insert");
        AccessibilityNodeInfo node = focused;
        if (node == null || !node.isEditable()) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            node = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        }
        if (node == null) return new Result(false, "No focused text field");
        if (!node.isEditable() || !node.isEnabled()) return new Result(false, "The focused field is not editable");
        if (node.isPassword()) return new Result(false, "Refusing to type into a password field");

        CharSequence pkg = node.getPackageName();
        if (pkg != null && getPackageName().contentEquals(pkg)) {
            return new Result(false, "Open another app, then use the floating bubble");
        }

        CharSequence existing = node.getText();
        String old = existing == null ? "" : existing.toString();
        int start = node.getTextSelectionStart();
        int end = node.getTextSelectionEnd();
        if (start < 0 || start > old.length()) start = old.length();
        if (end < start || end > old.length()) end = start;

        String insert = text;
        if (node.getMaxTextLength() > 0) {
            int room = node.getMaxTextLength() - (old.length() - (end - start));
            if (room <= 0) return new Result(false, "The field is full");
            if (insert.length() > room) insert = insert.substring(0, room);
        }
        boolean multiline = (node.getInputType() & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if (!multiline) insert = insert.replace('\n', ' ');

        String updated = old.substring(0, start) + insert + old.substring(end);
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated);
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return new Result(true, "Inserted");
        }

        // Some WebViews and custom editors reject ACTION_SET_TEXT; paste is the fallback.
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return new Result(false, "Insertion failed");
        cm.setPrimaryClip(ClipData.newPlainText("Wahtuh Pho", insert));
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            return new Result(true, "Pasted");
        }
        return new Result(false, "This app does not accept accessibility insertion; text copied instead");
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        if (focused != null) {
            focused.recycle();
            focused = null;
        }
        super.onDestroy();
    }
}
