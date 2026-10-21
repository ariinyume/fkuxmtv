package io.github.ariinyume.fkuxmtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Starts the home straight from the system boot broadcast (plan A).
 *
 * <p>Android 10+ blocks a background activity start unless the app holds the
 * SYSTEM_ALERT_WINDOW app-op, so this receiver is only useful once that app-op is allowed
 * ({@code adb shell appops set <pkg> SYSTEM_ALERT_WINDOW allow}). Without it the launch is
 * rejected and nothing happens — no crash, no loop.
 *
 * <p>Every gate lives in {@link BootStartPolicy}: switch on, supported model, once per full
 * boot. A retry is only scheduled when the first attempt actually threw, so a device where
 * the start is blocked does not spin.
 */
public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "GuiZangBoot";
    /** Boot is noisy; the TV's own home may still be laying out when we fire. */
    private static final long RETRY_MS = 2500;

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Context app = context.getApplicationContext();
        Log.i(TAG, "BOOT_COMPLETED received");
        if (BootStart.enterFromBoot(app, "系统开机广播")) return;

        // Only on a real exception is a retry worth anything; goAsync keeps the process
        // alive for the wait and is finished no matter what.
        final PendingResult pending = goAsync();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                BootStart.enterFromBoot(app, "系统开机广播 · 重试");
            } finally {
                pending.finish();
            }
        }, RETRY_MS);
    }
}
