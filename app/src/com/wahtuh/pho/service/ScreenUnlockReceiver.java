package com.wahtuh.pho.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/** Restores the bubble after a reboot or an Android-initiated service stop. */
public final class ScreenUnlockReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_USER_PRESENT.equals(intent.getAction())) return;
        com.wahtuh.pho.data.Settings settings = new com.wahtuh.pho.data.Settings(context);
        if (!settings.bubbleEnabled() || DictationService.running) return;
        if (!Settings.canDrawOverlays(context)) return;
        if (!InsertAccessibilityService.isEnabled(context)) return;
        Intent service = new Intent(context, DictationService.class)
                .setAction(DictationService.ACTION_RESTORE);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (Exception ignored) {
            // Some OEMs block foreground-service starts from the background.
        }
    }
}
