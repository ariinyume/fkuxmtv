package io.github.ariinyume.fkuxmtv;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Wake-up engine.
 *
 * This firmware refuses third-party foreground services outright
 * ({@code AppsRelaunchController: Refused to start foreground service, isCallerSystem=false}),
 * so the screen-on listener lives in an accessibility service instead. The system binds it,
 * keeps it alive across sleep, delivers window-state changes in real time, and lets it start
 * an activity — none of which a background service can do here.
 *
 * Two triggers feed the same gate:
 * 1. {@code TYPE_WINDOW_STATE_CHANGED} for the stock home right after a wake-up (event driven);
 * 2. a single usage-stats probe ~1.5s after SCREEN_ON, for firmware that does not re-emit a
 *    window event when the launcher never lost focus.
 */
public final class WakeAccessibilityService extends AccessibilityService {
    private static final String TAG = "GuiZangWake";

    /** Runtime truth: is the system currently holding this service? */
    static volatile boolean connected;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager power;
    /** elapsedRealtime of the SCREEN_ON that opened the current wake-up; 0 when none captured. */
    private long wakeAt;
    /** The wake-up already acted on. One shot per wake-up. */
    private long handledWakeAt;
    private int probes;
    private long lastEventAt;
    private String lastEventPackage = "";

    private final BroadcastReceiver screens = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                handler.removeCallbacksAndMessages(null);
                wakeAt = 0;
                handledWakeAt = 0;
                probes = 0;
                return;
            }
            if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action))
                beginWake("亮屏广播");
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        power = (PowerManager) getSystemService(POWER_SERVICE);
        if (!registered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            try {
                registerReceiver(screens, filter);
                registered = true;
            } catch (RuntimeException refused) {
                Log.w(TAG, "screen filter refused", refused);
            }
        }
        // The service may be bound after SCREEN_ON (boot, or the user just enabled it).
        // Treat "connected while the screen is on" as the start of a wake-up window.
        if (wakeAt == 0 && power != null && power.isInteractive()) beginWake("服务连接");
        WakeHome.note(this, "无障碍服务已连接");
        Log.i(TAG, "accessibility service connected, wakeAt=" + wakeAt);
    }

    private void beginWake(String source) {
        if (wakeAt != 0) return;
        wakeAt = SystemClock.elapsedRealtime();
        probes = 0;
        Log.i(TAG, "wake window opened by " + source);
        handler.postDelayed(this::probe, WakePolicy.FIRST_CHECK_DELAY_MS);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !WakeHome.enabled(this)) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        CharSequence name = event.getPackageName();
        if (name == null) return;
        lastEventPackage = name.toString();
        lastEventAt = SystemClock.elapsedRealtime();
        if (!WakePolicy.STOCK_HOME.equals(lastEventPackage)) return;
        evaluate(lastEventPackage, "无障碍事件");
    }

    /** Foreground probe. Only runs inside a wake window, at most once (plus one retry). */
    private void probe() {
        if (wakeAt == 0 || !WakeHome.enabled(this)) return;
        String foreground = WakeHome.foreground(this);
        if (foreground != null) { evaluate(foreground, "前台探测"); return; }
        if (probes++ > 0) {
            WakeHome.note(this, "读不到前台应用，先不动 · " + stamp());
            return;
        }
        handler.postDelayed(this::probe, WakePolicy.RETRY_DELAY_MS);
    }

    private void evaluate(String foreground, String source) {
        if (wakeAt == 0 && !"无障碍事件".equals(source)) return;
        long now = SystemClock.elapsedRealtime();
        boolean handled = handledWakeAt != 0 && handledWakeAt == wakeAt;
        long sinceHandled = WakeHome.lastHandled(this);
        String reason = WakePolicy.skipReason(XiaomiControls.enabled(), WakeHome.enabled(this),
                power != null && power.isInteractive(), handled,
                wakeAt == 0 ? -1 : now - wakeAt, foreground != null, foreground,
                WakeHome.visible, sinceHandled < 0 ? -1 : now - sinceHandled);
        if (reason == null) { launch(source); return; }
        Log.i(TAG, "wake skipped (" + source + ", " + foreground + "): " + reason);
        if (!WakePolicy.retryable(reason)) return;
        // Unknown foreground: the probe path owns the retry, events just record the miss.
        WakeHome.note(this, reason + " · " + stamp());
    }

    private void launch(String source) {
        handledWakeAt = wakeAt;                 // one shot per wake-up, even if refused
        handler.removeCallbacksAndMessages(null);
        WakeHome.handled(this, WakeHome.REASON_UNKNOWN);
        try {
            startActivity(new Intent(this, FrameHomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (RuntimeException refused) {
            Log.w(TAG, "wake launch refused", refused);
            WakeHome.handled(this, WakeHome.REASON_BLOCKED);
            WakeHome.note(this, "系统拒绝拉起（" + refused.getClass().getSimpleName() + "）");
            return;
        }
        handler.postDelayed(() -> confirm(source), 1200);
    }

    private void confirm(String source) {
        if (WakeHome.visible) {
            WakeHome.handled(this, WakeHome.REASON_LAUNCHED);
            WakeHome.note(this, "已进入桌面 · " + source);
        } else {
            WakeHome.handled(this, WakeHome.REASON_BLOCKED);
            WakeHome.note(this, "发出启动但界面没出来 · " + source);
        }
    }

    @Override public void onInterrupt() { }

    @Override public boolean onUnbind(Intent intent) {
        connected = false;
        if (registered) {
            try { unregisterReceiver(screens); } catch (RuntimeException ignored) { }
            registered = false;
        }
        handler.removeCallbacksAndMessages(null);
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        connected = false;
        super.onDestroy();
    }

    private boolean registered;

    private static String stamp() {
        return new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                .format(new java.util.Date());
    }
}
