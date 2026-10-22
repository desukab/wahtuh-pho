package com.wahtuh.pho.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.wahtuh.pho.MainActivity;
import com.wahtuh.pho.dictation.DictationController;
import com.wahtuh.pho.dictation.DictationListener;
import com.wahtuh.pho.dictation.DictationResult;
import com.wahtuh.pho.text.SensitiveApps;
import com.wahtuh.pho.ui.Ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Floating dictation bubble. It only exists after the user grants overlay
 * permission and starts it from the app. Audio still obeys local-only mode.
 */
public final class DictationService extends Service implements DictationListener {
    public static final String ACTION_STOP = "com.wahtuh.pho.STOP";
    public static final String ACTION_CANCEL = "com.wahtuh.pho.CANCEL";
    public static final String ACTION_UNDO = "com.wahtuh.pho.UNDO";
    public static final String ACTION_CLOSE = "com.wahtuh.pho.CLOSE";
    public static final String ACTION_WAKE = "com.wahtuh.pho.WAKE";
    public static final String ACTION_RESTORE = "com.wahtuh.pho.RESTORE";
    private static final long SNOOZE_SHORT_MS = 5 * 60 * 1000L;
    private static final long SNOOZE_LONG_MS = 10 * 60 * 1000L;
    private static final long HOLD_START_MS = 380L;
    private static final long SESSION_WARN_MS = 4 * 60 * 1000L;
    private static final long SESSION_LIMIT_MS = 5 * 60 * 1000L;

    private static final String CHANNEL = "wahtuh_dictation";
    private static final String RECOVERY_CHANNEL = "wahtuh_recovery";
    private static final int NOTICE = 41;
    private static final int OVERFLOW_NOTICE = 42;
    private static final int RECOVERY_NOTICE = 43;
    public static volatile boolean running;
    private static volatile DictationService instance;

    private static final int CREAM = Ui.SURFACE;
    private static final int INK = Ui.TEXT;
    private static final int PURPLE = Ui.ACCENT;
    private static final int BUSY = Ui.SURFACE2;
    private static final int CYAN = Ui.ACCENT2;
    private static final int PINK = Ui.DANGER;

    private WindowManager wm;
    private View bubble;
    private TextView label;
    private volatile String liveText = "";
    private WaveView wave;
    private WindowManager.LayoutParams params;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private long recordingStart;
    private boolean moved;
    private long downAt;
    private boolean inputTextVisible;
    private boolean sessionActive;
    private boolean sessionWarned;
    private boolean sessionCapped;
    private boolean pointerDown;
    private boolean holdScheduled;
    private boolean holdRecording;
    private boolean commandMode;
    private String commandSelection = "";
    private int bubbleX;
    private int bubbleY;
    private View snoozeTarget;
    private long snoozedUntil;
    private int snoozeTargetY;
    private int snoozeLeftX;
    private int snoozeRightX;
    private int accessibilityMisses;

    private final Runnable wakeTask = this::wake;
    private final Runnable holdStartTask = this::beginHold;

    private final Runnable accessibilityWatch = new Runnable() {
        @Override public void run() {
            if (!running) return;
            boolean ready = InsertAccessibilityService.isEnabled(DictationService.this)
                    && InsertAccessibilityService.get() != null;
            if (ready) {
                accessibilityMisses = 0;
                clearRecoveryNotification();
            } else if (++accessibilityMisses >= 2) {
                hideBubble();
                showRecoveryNotification();
                if (accessibilityMisses == 2) {
                    Toast.makeText(DictationService.this,
                            "Text insertion is off — open Accessibility to restore",
                            Toast.LENGTH_LONG).show();
                }
            }
            main.postDelayed(this, 15000);
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (label == null || recordingStart == 0) return;
            long elapsed = System.currentTimeMillis() - recordingStart;
            long secs = elapsed / 1000L;
            String live = liveText;
            label.setText(live.isEmpty()
                    ? String.format(java.util.Locale.US, "%d:%02d", secs / 60, secs % 60)
                    : live);
            if (elapsed >= SESSION_WARN_MS && !sessionWarned) {
                sessionWarned = true;
                Toast.makeText(DictationService.this, "One minute left in this session",
                        Toast.LENGTH_SHORT).show();
            }
            if (elapsed >= SESSION_LIMIT_MS && !sessionCapped) {
                sessionCapped = true;
                holdRecording = false;
                Toast.makeText(DictationService.this, "5 minute limit reached — transcribing",
                        Toast.LENGTH_SHORT).show();
                DictationController.get(DictationService.this).stop();
                return;
            }
            main.postDelayed(this, 250);
        }
    };

    public static void onTextInput(boolean visible) {
        DictationService service = instance;
        if (service != null) service.main.post(() -> service.setInputTextVisible(visible));
    }

    public static void onAccessibilityLost() {
        DictationService service = instance;
        if (service != null) service.main.post(service::handleAccessibilityLost);
    }

    public static void onAccessibilityAvailable() {
        DictationService service = instance;
        if (service != null) service.main.post(service::handleAccessibilityAvailable);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        running = true;
        new com.wahtuh.pho.data.Settings(this).setBubbleEnabled(true);
        bubbleX = getSharedPreferences("wahtuh_bubble", MODE_PRIVATE)
                .getInt("x", Ui.dp(this, 12));
        bubbleY = getSharedPreferences("wahtuh_bubble", MODE_PRIVATE)
                .getInt("y", Ui.dp(this, 180));
        createChannel();
        createRecoveryChannel();
        main.postDelayed(accessibilityWatch, 15000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_WAKE.equals(action)) {
            wake();
            return START_NOT_STICKY;
        }
        if (ACTION_CLOSE.equals(action)) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(action)) {
            DictationController.get(this).cancel();
            updateLabel("wp");
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action)) {
            DictationController.get(this).stop();
            return START_NOT_STICKY;
        }
        if (ACTION_UNDO.equals(action)) {
            undo();
            return START_NOT_STICKY;
        }
        boolean restored = ACTION_RESTORE.equals(action);
        if (!startInForeground(!restored)) {
            new com.wahtuh.pho.data.Settings(this).setBubbleEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_LONG).show();
            new com.wahtuh.pho.data.Settings(this).setBubbleEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!InsertAccessibilityService.isEnabled(this)) {
            Toast.makeText(this, "Enable text insertion so the bubble knows when to appear",
                    Toast.LENGTH_LONG).show();
            new com.wahtuh.pho.data.Settings(this).setBubbleEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        InsertAccessibilityService.refreshBubbleVisibility();
        if (inputTextVisible && !sessionActive && bubble == null && !snoozed()) showBubble();
        return START_NOT_STICKY;
    }

    private boolean startInForeground(boolean microphone) {
        Notification n = notification("Wahtuh Pho is ready", "Appears when a text field opens");
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                int types = microphone
                        ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        | ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        : ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
                startForeground(NOTICE, n, types);
            } else if (Build.VERSION.SDK_INT >= 29 && microphone) {
                startForeground(NOTICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTICE, n);
            }
        } catch (Exception e) {
            Toast.makeText(this, microphone
                    ? "Microphone permission is required first"
                    : "Android refused to restore the bubble",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    /** Adds the microphone FGS type when a session starts; boot restore skips it. */
    private boolean promoteMicrophone() {
        if (Build.VERSION.SDK_INT < 29) return true;
        try {
            int types = Build.VERSION.SDK_INT >= 34
                    ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    : ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            startForeground(NOTICE, notification("Wahtuh Pho is ready",
                    "Getting the microphone ready"), types);
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Microphone permission is required first",
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private Notification notification(String title, String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true);
        Icon icon = Icon.createWithResource(this, android.R.drawable.ic_btn_speak_now);
        if (snoozed()) {
            Intent wake = new Intent(this, DictationService.class).setAction(ACTION_WAKE);
            Intent close = new Intent(this, DictationService.class).setAction(ACTION_CLOSE);
            b.addAction(new Notification.Action.Builder(icon, "Wake",
                    PendingIntent.getService(this, 3, wake,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
            b.addAction(new Notification.Action.Builder(icon, "Close",
                    PendingIntent.getService(this, 4, close,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
        } else {
            Intent stop = new Intent(this, DictationService.class).setAction(ACTION_STOP);
            Intent cancel = new Intent(this, DictationService.class).setAction(ACTION_CANCEL);
            Intent undo = new Intent(this, DictationService.class).setAction(ACTION_UNDO);
            b.addAction(new Notification.Action.Builder(icon, "Stop",
                    PendingIntent.getService(this, 1, stop,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
            b.addAction(new Notification.Action.Builder(icon, "Cancel",
                    PendingIntent.getService(this, 2, cancel,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
            b.addAction(new Notification.Action.Builder(icon, "Undo",
                    PendingIntent.getService(this, 5, undo,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
        }
        return b.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel c = new NotificationChannel(CHANNEL, "Dictation bubble",
                NotificationManager.IMPORTANCE_LOW);
        c.setDescription("Keeps the floating dictation bubble available");
        c.setShowBadge(false);
        nm.createNotificationChannel(c);
    }

    private void createRecoveryChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel c = new NotificationChannel(RECOVERY_CHANNEL,
                "Insertion recovery", NotificationManager.IMPORTANCE_DEFAULT);
        c.setDescription("Warns when Android turns off text insertion");
        c.setShowBadge(true);
        nm.createNotificationChannel(c);
    }

    private void handleAccessibilityLost() {
        if (!running) return;
        accessibilityMisses = 2;
        if (sessionActive || DictationController.get(this).busy()) {
            DictationController.get(this).stop();
        }
        hideBubble();
        showRecoveryNotification();
        Toast.makeText(this, "Text insertion is off — open Accessibility to restore",
                Toast.LENGTH_LONG).show();
    }

    private void handleAccessibilityAvailable() {
        accessibilityMisses = 0;
        clearRecoveryNotification();
        if (inputTextVisible && !sessionActive && !snoozed()) showBubble();
    }

    private void showRecoveryNotification() {
        if (!running || !new com.wahtuh.pho.data.Settings(this).bubbleEnabled()) return;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            Intent fix = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            PendingIntent pi = PendingIntent.getActivity(this, 6, fix,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, RECOVERY_CHANNEL)
                    : new Notification.Builder(this);
            Icon icon = Icon.createWithResource(this, android.R.drawable.ic_btn_speak_now);
            b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentTitle("Text insertion is off")
                    .setContentText("Tap to restore it so dictation can type for you")
                    .setContentIntent(pi)
                    .addAction(new Notification.Action.Builder(icon, "FIX", pi).build())
                    .setAutoCancel(true);
            nm.notify(RECOVERY_NOTICE, b.build());
        } catch (Exception ignored) {}
    }

    private void clearRecoveryNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(RECOVERY_NOTICE);
        } catch (Exception ignored) {}
    }

    private void setInputTextVisible(boolean visible) {
        inputTextVisible = visible;
        if (visible) {
            if (bubble == null && !snoozed()) showBubble();
        } else {
            if (sessionActive && sensitiveFocused()) {
                DictationController.get(this).cancel();
            }
            if (!sessionActive) hideBubble();
        }
    }

    private boolean sensitiveFocused() {
        return new com.wahtuh.pho.data.Settings(this).blockSensitiveApps()
                && SensitiveApps.blocks(InsertAccessibilityService.focusedPackage());
    }

    private boolean snoozed() {
        return snoozedUntil > System.currentTimeMillis();
    }

    private void hideBubble() {
        cancelHold();
        if (bubble != null && wm != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
        }
        bubble = null;
        label = null;
        wave = null;
        params = null;
    }

    private void showBubble() {
        if (!running || snoozed()) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return;

        com.wahtuh.pho.data.Settings settings = new com.wahtuh.pho.data.Settings(this);
        float scale = settings.bubbleSize() == 0 ? 0.8f
                : settings.bubbleSize() == 2 ? 1.2f : 1f;
        int alpha = Math.max(40, Math.min(100, settings.bubbleAlpha()));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(Ui.dp(this, Math.round(13 * scale)), Ui.dp(this, Math.round(10 * scale)),
                Ui.dp(this, Math.round(13 * scale)), Ui.dp(this, Math.round(10 * scale)));
        wrap.setElevation(Ui.dp(this, 10));

        wave = new WaveView(this);
        wrap.addView(wave, new LinearLayout.LayoutParams(
                Ui.dp(this, Math.round(30 * scale)), Ui.dp(this, Math.round(22 * scale))));

        label = new TextView(this);
        label.setTextColor(INK);
        label.setTextSize(10 * scale);
        label.setTypeface(Ui.pixel(this));
        label.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(this, Math.round(9 * scale));
        wrap.addView(label, lp);

        bubble = wrap;

        params = new WindowManager.LayoutParams(
                Ui.dp(this, Math.round(64 * scale)), Ui.dp(this, Math.round(64 * scale)),
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = bubbleX;
        params.y = bubbleY;
        params.alpha = alpha / 100f;
        setBubble(CREAM, "");

        wrap.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean dragging;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = params.x;
                        startY = params.y;
                        dragging = false;
                        moved = false;
                        downAt = System.currentTimeMillis();
                        pointerDown = true;
                        cancelHold();
                        if (!sessionActive
                                && !DictationController.get(DictationService.this).busy()) {
                            holdScheduled = true;
                            main.postDelayed(holdStartTask, HOLD_START_MS);
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (holdRecording) return true;
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (Math.abs(dx) > Ui.dp(DictationService.this, 8)
                                || Math.abs(dy) > Ui.dp(DictationService.this, 8)) {
                            dragging = true;
                            moved = true;
                            cancelHold();
                        }
                        if (dragging && params != null) {
                            params.x = startX + Math.round(dx);
                            params.y = startY + Math.round(dy);
                            bubbleX = params.x;
                            bubbleY = params.y;
                            try { wm.updateViewLayout(bubble, params); } catch (Exception ignored) {}
                            if (!sessionActive) showSnoozeTarget(); else hideSnoozeTarget();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        long held = System.currentTimeMillis() - downAt;
                        pointerDown = false;
                        cancelHold();
                        if (sessionCapped) {
                            sessionCapped = false;
                            hideSnoozeTarget();
                            return true;
                        }
                        if (params == null || bubble == null) {
                            hideSnoozeTarget();
                            return true;
                        }
                        if (holdRecording) {
                            holdRecording = false;
                            commandMode = false;
                            moved = false;
                            hideSnoozeTarget();
                            DictationController.get(DictationService.this).stop();
                            return true;
                        }
                        if (dragging) {
                            int minutes = snoozeDrop();
                            hideSnoozeTarget();
                            if (minutes > 0) {
                                snooze(minutes);
                                moved = false;
                                return true;
                            }
                            moved = false;
                            getSharedPreferences("wahtuh_bubble", MODE_PRIVATE).edit()
                                    .putInt("x", bubbleX).putInt("y", bubbleY).apply();
                            return true;
                        }
                        if (sessionActive || DictationController.get(DictationService.this).busy()) {
                            toggle();
                        } else if (held < HOLD_START_MS) {
                            toggle();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        pointerDown = false;
                        boolean wasHolding = holdRecording;
                        holdRecording = false;
                        commandMode = false;
                        commandSelection = "";
                        cancelHold();
                        hideSnoozeTarget();
                        moved = false;
                        if (wasHolding && DictationController.get(DictationService.this).busy()) {
                            DictationController.get(DictationService.this).cancel();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
        try {
            wm.addView(wrap, params);
        } catch (Exception e) {
            Toast.makeText(this, "Could not show bubble: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void beginHold() {
        holdScheduled = false;
        if (!pointerDown || moved || bubble == null || sessionActive) return;
        DictationController c = DictationController.get(this);
        if (c.busy()) return;
        com.wahtuh.pho.data.Settings settings = new com.wahtuh.pho.data.Settings(this);
        if (settings.blockSensitiveApps()
                && SensitiveApps.blocks(InsertAccessibilityService.focusedPackage())) {
            setBubble(PINK, "OFF");
            Toast.makeText(this, "Dictation is off in this sensitive app",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String selection = settings.commandMode()
                ? InsertAccessibilityService.selectedText(12000) : "";
        if (!selection.isEmpty() && c.commandReady()) {
            if (!promoteMicrophone()) return;
            commandMode = true;
            commandSelection = selection;
            holdRecording = true;
            sessionActive = true;
            bubble.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            setBubble(CYAN, "CMD");
            updateNotification("Command mode", "Speak an edit command, then release");
            c.startCommand(this, selection);
            return;
        }
        if (!selection.isEmpty() && !settings.localOnly()
                && !com.wahtuh.pho.data.Settings.LOCAL.equals(settings.provider())) {
            Toast.makeText(this, "Command Mode needs a Groq, OpenAI, or Gemini key",
                    Toast.LENGTH_SHORT).show();
        }
        if (!promoteMicrophone()) return;
        holdRecording = true;
        sessionActive = true;
        bubble.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        setBubble(CREAM, "HOLD");
        c.start(this);
    }

    private void cancelHold() {
        holdScheduled = false;
        main.removeCallbacks(holdStartTask);
    }

    private void toggle() {
        if (sessionCapped) return;
        if (sensitiveFocused()) {
            setBubble(PINK, "OFF");
            Toast.makeText(this, "Dictation is off in this sensitive app",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        DictationController c = DictationController.get(this);
        if (c.busy()) {
            c.stop();
        } else {
            commandMode = false;
            commandSelection = "";
            if (!promoteMicrophone()) return;
            sessionActive = true;
            c.start(this);
        }
    }

    @Override
    public void onState(String state, String message) {
        if (DictationController.RECORDING.equals(state)) {
            sessionActive = true;
            sessionWarned = false;
            sessionCapped = false;
            liveText = "";
            recordingStart = System.currentTimeMillis();
            main.removeCallbacks(ticker);
            main.post(ticker);
            setBubble(CREAM, "0:00");
            if (wave != null) {
                wave.setColor(PURPLE);
                wave.setActive(true);
            }
            if (commandMode) {
                updateNotification("Command mode", "Speak an edit command, then release");
            } else {
                updateNotification("Listening", "Release or tap the bubble to stop");
            }
        } else if (DictationController.TRANSCRIBING.equals(state)
                || DictationController.PREPARING.equals(state)) {
            sessionActive = true;
            liveText = "";
            stopTimer();
            setBubble(BUSY, "WORKING...");
            if (wave != null) {
                wave.setActive(false);
                wave.setColor(CYAN);
            }
            updateNotification("Working", message == null ? "" : message);
        } else if (DictationController.ERROR.equals(state)) {
            sessionActive = false;
            sessionWarned = false;
            sessionCapped = false;
            commandMode = false;
            commandSelection = "";
            liveText = "";
            stopTimer();
            setBubble(BUSY, "RETRY");
            if (wave != null) {
                wave.setActive(false);
                wave.setColor(PINK);
            }
            updateNotification("Dictation error", message == null ? "" : message);
            if (!inputTextVisible) hideBubble();
        } else {
            sessionActive = false;
            sessionWarned = false;
            sessionCapped = false;
            commandMode = false;
            commandSelection = "";
            liveText = "";
            stopTimer();
            setBubble(CREAM, "");
            if (wave != null) {
                wave.setActive(false);
                wave.setColor(PURPLE);
            }
            updateNotification("Bubble armed", "Appears when you open a text field");
            if (!inputTextVisible) hideBubble();
        }
    }

    private void stopTimer() {
        recordingStart = 0;
        main.removeCallbacks(ticker);
    }

    private void showSnoozeTarget() {
        if (snoozeTarget != null || wm == null || snoozed()) return;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int width = Ui.dp(this, 208);
        int height = Ui.dp(this, 112);
        int top = Ui.dp(this, 44);
        snoozeTargetY = top + Ui.dp(this, 34);
        snoozeLeftX = screenW / 2 - Ui.dp(this, 52);
        snoozeRightX = screenW / 2 + Ui.dp(this, 52);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        cell.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        bar.addView(snoozeCard("5 MIN"), cell);
        bar.addView(snoozeCard("10 MIN"), new LinearLayout.LayoutParams(cell));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, height,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = top;
        try {
            wm.addView(bar, lp);
            snoozeTarget = bar;
        } catch (Exception ignored) {}
    }

    private View snoozeCard(String duration) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 4));
        card.setBackground(Ui.panel(0x00000000, Ui.DANGER, this));

        TextView x = Ui.pixel(this, "X", 14, Ui.DANGER);
        x.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xCC08081A);
        circle.setStroke(Ui.dp(this, 3), Ui.DANGER);
        x.setBackground(circle);
        card.addView(x, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));

        TextView label = Ui.pixel(this, duration, 7, Ui.MUTED);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, Ui.dp(this, 5), 0, 0);
        card.addView(label);
        return card;
    }

    private int snoozeDrop() {
        if (snoozeTarget == null || params == null) return 0;
        float cx = params.x + params.width / 2f;
        float cy = params.y + params.height / 2f;
        int reach = Ui.dp(this, 60);
        int leftDx = Math.round(cx) - snoozeLeftX;
        int rightDx = Math.round(cx) - snoozeRightX;
        int dy = Math.round(cy) - snoozeTargetY;
        if (leftDx * leftDx + dy * dy <= reach * reach) return 5;
        if (rightDx * rightDx + dy * dy <= reach * reach) return 10;
        return 0;
    }

    private void snooze(int minutes) {
        long duration = minutes == 5 ? SNOOZE_SHORT_MS : SNOOZE_LONG_MS;
        snoozedUntil = System.currentTimeMillis() + duration;
        main.removeCallbacks(wakeTask);
        main.postDelayed(wakeTask, duration);
        hideSnoozeTarget();
        hideBubble();
        String until = new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(snoozedUntil));
        updateNotification("Bubble snoozed", "Back at " + until);
        Toast.makeText(this, "Bubble snoozed for " + minutes + " min", Toast.LENGTH_SHORT).show();
    }

    private void wake() {
        if (snoozedUntil == 0) return;
        snoozedUntil = 0;
        main.removeCallbacks(wakeTask);
        hideSnoozeTarget();
        updateNotification("Wahtuh Pho is ready", "Appears when a text field opens");
        InsertAccessibilityService.refreshBubbleVisibility();
    }

    private void hideSnoozeTarget() {
        if (snoozeTarget != null && wm != null) {
            try { wm.removeView(snoozeTarget); } catch (Exception ignored) {}
        }
        snoozeTarget = null;
    }

    private void setBubble(int background, String text) {
        main.post(() -> {
            if (bubble == null) return;
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(Ui.dp(this, 14));
            shape.setColor(background);
            shape.setStroke(Ui.dp(this, 2), Ui.LINE);
            bubble.setBackground(shape);
            boolean idle = text == null || text.isEmpty();
            label.setVisibility(idle ? View.GONE : View.VISIBLE);
            if (text != null) label.setText(text);
            int width = idle ? 64 : (text != null && text.length() > 7 ? 196 : 136);
            params.width = Ui.dp(this, width);
            params.height = Ui.dp(this, 64);
            try { wm.updateViewLayout(bubble, params); } catch (Exception ignored) {}
        });
    }

    private void updateLabel(String text) {
        setBubble(CREAM, "");
    }

    private void showOverflow(String overflow) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Wahtuh Pho", overflow));
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            Intent open = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CHANNEL)
                    : new Notification.Builder(this);
            b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentTitle(overflow.length() + " characters didn't fit")
                    .setContentText("The rest is on your clipboard")
                    .setStyle(new Notification.BigTextStyle().bigText(overflow))
                    .setAutoCancel(true)
                    .setContentIntent(pi);
            nm.notify(OVERFLOW_NOTICE, b.build());
        } catch (Exception ignored) {}
    }

    private void updateNotification(String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTICE, notification(title, text));
        } catch (Exception ignored) {}
    }

    @Override
    public void onLevel(float rms) {
        if (wave != null) wave.setLevel(rms);
        if (label != null) label.setAlpha(0.75f + Math.min(0.25f, rms * 5f));
    }

    @Override
    public void onPartial(String text) {
        if (text == null) return;
        if (!DictationController.RECORDING.equals(DictationController.get(this).state())) return;
        String clean = text.trim();
        if (clean.isEmpty()) return;
        String tail = clean.length() > 22 ? "..." + clean.substring(clean.length() - 19) : clean;
        liveText = tail;
        setBubble(CREAM, tail);
        updateNotification("Listening", clean);
    }

    @Override
    public void onResult(DictationResult result) {
        if (result == null) return;
        if (result.text.isEmpty() && !result.command) return;
        if (result.command) {
            final String expected = commandSelection;
            commandSelection = "";
            commandMode = false;
            setBubble(BUSY, "APPLY");
            updateNotification("Command mode", "Replacing the selected text");
            io.execute(() -> {
                InsertAccessibilityService svc = InsertAccessibilityService.get();
                String outcome;
                String overflow = "";
                boolean replaced = false;
                if (svc != null) {
                    InsertAccessibilityService.Result r =
                            svc.replaceSelection(expected, result.text);
                    replaced = r.ok;
                    outcome = r.message;
                    overflow = r.overflow;
                } else {
                    outcome = "Accessibility service is off";
                }
                if (!replaced && !result.text.isEmpty()) {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("Wahtuh Pho", result.text));
                    }
                    outcome = outcome + " — text copied";
                    overflow = "";
                } else if (!overflow.isEmpty()) {
                    showOverflow(overflow);
                }
                final String missing = overflow;
                final String extra = missing.isEmpty() ? ""
                        : " · " + missing.length() + " characters didn't fit, remainder copied";
                final String msg = replaced
                        ? outcome + extra + " · undo from the notification" : outcome;
                final int duration = missing.isEmpty() ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
                main.post(() -> {
                    Toast.makeText(this, msg, duration).show();
                    stopTimer();
                    setBubble(CREAM, "");
                    if (wave != null) {
                        wave.setActive(false);
                        wave.setColor(PURPLE);
                    }
                    updateNotification("Wahtuh Pho is ready",
                            "Hold to dictate · select text + hold to command");
                });
            });
            return;
        }
        setBubble(BUSY, "TYPING...");
        updateNotification("Inserting", "Putting your words in the focused field");
        io.execute(() -> {
            InsertAccessibilityService svc = InsertAccessibilityService.get();
            String outcome;
            String overflow = "";
            boolean inserted = false;
            if (svc != null) {
                InsertAccessibilityService.Result r = svc.insert(result.text);
                inserted = r.ok;
                outcome = r.message;
                overflow = r.overflow;
            } else {
                outcome = "Accessibility service is off";
            }
            if (!inserted) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Wahtuh Pho", result.text));
                outcome = outcome + " — text copied";
                overflow = "";
            } else if (!overflow.isEmpty()) {
                showOverflow(overflow);
            }
            final String missing = overflow;
            final String extra = missing.isEmpty() ? ""
                    : " · " + missing.length() + " characters didn't fit, remainder copied";
            final String msg = inserted
                    ? outcome + extra + " · undo from the notification" : outcome;
            final int duration = missing.isEmpty() ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
            main.post(() -> {
                Toast.makeText(this, msg, duration).show();
                stopTimer();
                setBubble(CREAM, "");
                if (wave != null) {
                    wave.setActive(false);
                    wave.setColor(PURPLE);
                }
                updateNotification("Wahtuh Pho is ready", "Hold to dictate · tap for hands-free");
            });
        });
    }

    private void undo() {
        if (DictationController.get(this).busy()) return;
        io.execute(() -> {
            InsertAccessibilityService svc = InsertAccessibilityService.get();
            String msg = svc == null ? "Accessibility service is off" : svc.undoLast().message;
            main.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
        });
    }

    @Override
    public void onError(String message) {
        main.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
        stopTimer();
        setBubble(CREAM, "");
        if (wave != null) {
            wave.setActive(false);
            wave.setColor(PURPLE);
        }
    }

    private void shutdown() {
        running = false;
        new com.wahtuh.pho.data.Settings(this).setBubbleEnabled(false);
        pointerDown = false;
        sessionWarned = false;
        sessionCapped = false;
        holdRecording = false;
        commandMode = false;
        commandSelection = "";
        cancelHold();
        stopTimer();
        main.removeCallbacks(wakeTask);
        main.removeCallbacks(accessibilityWatch);
        clearRecoveryNotification();
        hideSnoozeTarget();
        io.shutdownNow();
        DictationController.get(this).cancel();
        if (bubble != null && wm != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
            bubble = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        instance = null;
        pointerDown = false;
        sessionWarned = false;
        sessionCapped = false;
        holdRecording = false;
        commandMode = false;
        commandSelection = "";
        cancelHold();
        io.shutdownNow();
        stopTimer();
        main.removeCallbacks(wakeTask);
        main.removeCallbacks(accessibilityWatch);
        hideSnoozeTarget();
        DictationController c = DictationController.get(this);
        if (c.busy()) c.cancel();
        if (bubble != null && wm != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
            bubble = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
