package io.github.ariinyume.fkuxmtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the wake listener after a full boot when the switch is on. */
public final class WakeBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && WakeHome.enabled(context))
            WakeHome.start(context);
    }
}
