package com.wahtuh.pho.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.wahtuh.pho.data.Settings;

/** Restarts the floating bubble after reboot when the user had it running. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Settings settings = new Settings(context);
        if (!settings.onboarded() || !settings.bubbleEnabled()) return;
        if (!android.provider.Settings.canDrawOverlays(context)) return;
        try {
            context.startForegroundService(new Intent(context, DictationService.class)
                    .setAction(DictationService.ACTION_RESTORE));
        } catch (Exception ignored) {
            // Android only allows this from a few contexts; the user can reopen the app.
        }
    }
}
