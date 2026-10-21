package io.github.ariinyume.fkuxmtv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

/**
 * Holds the screen-on listener. SCREEN_ON/SCREEN_OFF are not delivered to manifest receivers,
 * so a small foreground service keeps one registered while the switch is on.
 */
public final class WakeService extends Service {
    private static final String TAG = "GuiZangWake";
    private static final String CHANNEL = "wake";
    private static final int NOTIFICATION = 1201;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager power;
    private long wakeAt;
    private long handledWakeAt;
    private int retries;
    private boolean registered;

    private final BroadcastReceiver screens = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                handler.removeCallbacksAndMessages(null);
                wakeAt = 0;
                handledWakeAt = 0;
                retries = 0;
                return;
            }
            if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                if (wakeAt == 0) {
                    wakeAt = SystemClock.elapsedRealtime();
                    retries = 0;
                    handler.postDelayed(WakeService.this::evaluate, WakePolicy.FIRST_CHECK_DELAY_MS);
                }
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        power = (PowerManager) getSystemService(POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (notifications != null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL, "唤醒自动进入",
                        NotificationManager.IMPORTANCE_MIN);
                channel.setShowBadge(false);
                notifications.createNotificationChannel(channel);
            }
        }
        start();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screens, filter);
        registered = true;
    }

    private void start() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, FrameHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification notification = builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("唤醒后进入桌面已开启").setContentText("停在小米桌面时亮屏，会进入大米电视一次")
                .setContentIntent(open).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else
            startForeground(NOTIFICATION, notification);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!WakeHome.enabled(this)) { stopSelf(); return START_NOT_STICKY; }
        return START_STICKY;
    }

    /** Runs once per wake-up, at most one retry, and only while the answer is still unknown. */
    private void evaluate() {
        if (wakeAt == 0 || !WakeHome.enabled(this)) return;
        long now = SystemClock.elapsedRealtime();
        String foreground = WakeHome.foreground(this);
        long sinceHandled = WakeHome.lastHandled(this);
        String reason = WakePolicy.skipReason(XiaomiControls.enabled(), WakeHome.enabled(this),
                power != null && power.isInteractive(), handledWakeAt == wakeAt, now - wakeAt,
                WakeHome.canReadUsage(this), foreground, WakeHome.visible,
                sinceHandled < 0 ? -1 : now - sinceHandled);
        if (reason == null) { launch(); return; }
        Log.i(TAG, "wake skipped: " + reason);
        WakeHome.note(this, reason + " · " + new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                .format(new java.util.Date()));
        if (WakePolicy.retryable(reason) && retries == 0) {
            retries = 1;
            handler.postDelayed(this::evaluate, WakePolicy.RETRY_DELAY_MS);
        }
    }

    private void launch() {
        handledWakeAt = wakeAt;          // one shot per wake-up, even if the launch is refused
        WakeHome.handled(this, WakeHome.REASON_UNKNOWN);
        try {
            startActivity(new Intent(this, FrameHomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (RuntimeException refused) {
            Log.w(TAG, "wake launch refused", refused);
            WakeHome.handled(this, WakeHome.REASON_BLOCKED);
            WakeHome.note(this, "系统拒绝后台拉起（" + refused.getClass().getSimpleName() + "）");
            return;
        }
        handler.postDelayed(this::confirm, 1200);
    }

    private void confirm() {
        if (WakeHome.visible) {
            WakeHome.handled(this, WakeHome.REASON_LAUNCHED);
            WakeHome.note(this, "已在亮屏后进入桌面");
        } else {
            WakeHome.handled(this, WakeHome.REASON_BLOCKED);
            WakeHome.note(this, "系统拦截了后台拉起；可尝试授予悬浮窗权限");
        }
    }

    @Override public void onDestroy() {
        if (registered) { try { unregisterReceiver(screens); } catch (RuntimeException ignored) { } registered = false; }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
