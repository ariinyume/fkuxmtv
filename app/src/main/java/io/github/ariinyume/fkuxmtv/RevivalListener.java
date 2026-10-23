package io.github.ariinyume.fkuxmtv;

import android.service.notification.NotificationListenerService;
import android.util.Log;

/**
 * The way back into a process after the firmware kills it.
 *
 * Power-key standby force-stops every third-party app (see {@link Revival}). A force-stopped app
 * gets no broadcasts, no jobs and no alarms, so nothing we own can start itself again — but Android
 * keeps the notification-listener approval and binds the listener again shortly after the process
 * dies. That rebind is the only reliable handle we have, so it is used for exactly one thing:
 * restoring the accessibility grant.
 *
 * No notification is read, logged, stored or touched. None of the notification callbacks are
 * overridden, which is also why the service declares no notification access intent beyond the one
 * the system requires.
 */
public final class RevivalListener extends NotificationListenerService {
    private static final String TAG = "GuiZangRevive";

    @Override public void onListenerConnected() {
        Log.i(TAG, "notification listener bound: restarting from a killed process");
        Revival.ensure(this, "通知监听");
    }

    @Override public void onListenerDisconnected() {
        // The system unbinds us when it wants us to rebind (approval still on disk). Asking for it
        // costs nothing and saves a full standby cycle when the binding is merely stale.
        try {
            requestRebind(new android.content.ComponentName(this, RevivalListener.class));
        } catch (RuntimeException ignored) {
            // Older firmwares do not implement it; the next bind happens by itself.
        }
    }
}
