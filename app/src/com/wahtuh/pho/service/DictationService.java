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
import com.wahtuh.pho.ui.Ui;

/**
 * Floating dictation bubble. It only exists after the user grants overlay
 * permission and starts it from the app. Audio still obeys local-only mode.
 */
public final class DictationService extends Service implements DictationListener {
    public static final String ACTION_STOP = "com.wahtuh.pho.STOP";
    public static final String ACTION_CANCEL = "com.wahtuh.pho.CANCEL";
    public static final String ACTION_CLOSE = "com.wahtuh.pho.CLOSE";

    private static final String CHANNEL = "wahtuh_dictation";
    private static final int NOTICE = 41;
    public static volatile boolean running;

    private static final int CREAM = 0xFFF7F0E4;
    private static final int INK = 0xFF17151B;
    private static final int PURPLE = 0xFF7A5AD6;

    private WindowManager wm;
    private View bubble;
    private TextView label;
    private WaveView wave;
    private WindowManager.LayoutParams params;
    private final Handler main = new Handler(Looper.getMainLooper());
    private long recordingStart;
    private boolean moved;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (label == null || recordingStart == 0) return;
            long secs = (System.currentTimeMillis() - recordingStart) / 1000L;
            label.setText(String.format(java.util.Locale.US, "%d:%02d", secs / 60, secs % 60));
            main.postDelayed(this, 250);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
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
        try {
            startInForeground();
        } catch (SecurityException e) {
            Toast.makeText(this, "Microphone permission is required first", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (bubble == null) showBubble();
        return START_NOT_STICKY;
    }

    private void startInForeground() {
        Notification n = notification("Wahtuh Pho is ready", "Tap the bubble to dictate");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTICE, n);
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
        Intent stop = new Intent(this, DictationService.class).setAction(ACTION_STOP);
        Intent cancel = new Intent(this, DictationService.class).setAction(ACTION_CANCEL);
        Icon icon = Icon.createWithResource(this, android.R.drawable.ic_btn_speak_now);
        b.addAction(new Notification.Action.Builder(icon, "Stop",
                PendingIntent.getService(this, 1, stop,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
        b.addAction(new Notification.Action.Builder(icon, "Cancel",
                PendingIntent.getService(this, 2, cancel,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
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

    private void showBubble() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(Ui.dp(this, 13), Ui.dp(this, 10), Ui.dp(this, 13), Ui.dp(this, 10));
        wrap.setElevation(Ui.dp(this, 10));

        wave = new WaveView(this);
        wrap.addView(wave, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 22)));

        label = new TextView(this);
        label.setTextColor(INK);
        label.setTextSize(13);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        label.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(this, 9);
        wrap.addView(label, lp);

        bubble = wrap;

        params = new WindowManager.LayoutParams(
                Ui.dp(this, 64), Ui.dp(this, 64),
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Ui.dp(this, 12);
        params.y = Ui.dp(this, 180);
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
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (Math.abs(dx) > Ui.dp(DictationService.this, 8)
                                || Math.abs(dy) > Ui.dp(DictationService.this, 8)) {
                            dragging = true;
                            moved = true;
                        }
                        if (dragging) {
                            params.x = startX + Math.round(dx);
                            params.y = startY + Math.round(dy);
                            try { wm.updateViewLayout(bubble, params); } catch (Exception ignored) {}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) toggle();
                        else moved = false;
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

    private void toggle() {
        DictationController c = DictationController.get(this);
        if (c.busy()) c.stop();
        else c.start(this);
    }

    @Override
    public void onState(String state, String message) {
        if (DictationController.RECORDING.equals(state)) {
            recordingStart = System.currentTimeMillis();
            main.removeCallbacks(ticker);
            main.post(ticker);
            setBubble(CREAM, "0:00");
            if (wave != null) {
                wave.setColor(PURPLE);
                wave.setActive(true);
            }
            updateNotification("Listening", "Tap the bubble to stop");
        } else if (DictationController.TRANSCRIBING.equals(state)
                || DictationController.PREPARING.equals(state)) {
            stopTimer();
            setBubble(0xFFD5C2FF, "Transcribing…");
            if (wave != null) {
                wave.setActive(false);
                wave.setColor(INK);
            }
            updateNotification("Working", message == null ? "" : message);
        } else if (DictationController.ERROR.equals(state)) {
            stopTimer();
            setBubble(0xFFF3D4D9, "Try again");
            if (wave != null) {
                wave.setActive(false);
                wave.setColor(0xFF9B5B66);
            }
            updateNotification("Dictation error", message == null ? "" : message);
        } else {
            stopTimer();
            setBubble(CREAM, "");
            if (wave != null) {
                wave.setActive(false);
                wave.setColor(PURPLE);
            }
            updateNotification("Wahtuh Pho is ready", "Tap the bubble to dictate");
        }
    }

    private void stopTimer() {
        recordingStart = 0;
        main.removeCallbacks(ticker);
    }

    private void setBubble(int background, String text) {
        main.post(() -> {
            if (bubble == null) return;
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(Ui.dp(this, 32));
            shape.setColor(background);
            bubble.setBackground(shape);
            boolean idle = text == null || text.isEmpty();
            label.setVisibility(idle ? View.GONE : View.VISIBLE);
            if (text != null) label.setText(text);
            int width = idle ? 64 : (text != null && text.length() > 7 ? 188 : 132);
            params.width = Ui.dp(this, width);
            params.height = Ui.dp(this, 64);
            try { wm.updateViewLayout(bubble, params); } catch (Exception ignored) {}
        });
    }

    private void updateLabel(String text) {
        setBubble(CREAM, "");
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
    public void onResult(DictationResult result) {
        if (result == null || result.text.isEmpty()) return;
        InsertAccessibilityService svc = InsertAccessibilityService.get();
        String outcome;
        boolean inserted = false;
        if (svc != null) {
            InsertAccessibilityService.Result r = svc.insert(result.text);
            inserted = r.ok;
            outcome = r.message;
        } else {
            outcome = "Accessibility service is off";
        }
        if (!inserted) {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Wahtuh Pho", result.text));
            outcome = outcome + " — text copied";
        }
        final String msg = outcome;
        main.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
        stopTimer();
        setBubble(CREAM, "");
        if (wave != null) {
            wave.setActive(false);
            wave.setColor(PURPLE);
        }
        updateNotification("Wahtuh Pho is ready", "Tap the bubble to dictate");
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
        stopTimer();
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
        stopTimer();
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
