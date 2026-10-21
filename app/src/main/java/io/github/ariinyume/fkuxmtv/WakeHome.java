package io.github.ariinyume.fkuxmtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.widget.Toast;

/**
 * Opt-in "pull this home up when the TV wakes". Only acts while the stock Xiaomi home is the
 * foreground app, and only inside the few seconds after a screen-on, so waking straight into
 * HDMI or an app is never hijacked.
 *
 * Storage plus UI. The trigger/watchdog lives in {@link WakeAccessibilityService}.
 */
public final class WakeHome {
    private static final String PREFS = "wake-home";
    static final int REASON_UNKNOWN = 0, REASON_LAUNCHED = 1, REASON_BLOCKED = 2;
    private static final String A11Y_CLASS = ".WakeAccessibilityService";

    /** Set from FrameHomeActivity.onResume / onPause. */
    static volatile boolean visible;

    private WakeHome() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false); }

    static void enabled(Context c, boolean value) {
        prefs(c).edit().putBoolean("enabled", value).apply();
    }

    static long lastHandled(Context c) {
        return prefs(c).getLong("last-handled", -1L);
    }

    static void handled(Context c, int result) {
        prefs(c).edit().putLong("last-handled", SystemClock.elapsedRealtime())
                .putInt("last-result", result).putLong("last-time", System.currentTimeMillis()).apply();
    }

    static void note(Context c, String value) {
        prefs(c).edit().putString("last-note", value).apply();
    }

    static String note(Context c) { return prefs(c).getString("last-note", "还没有记录"); }

    static ComponentName service(Context c) {
        return new ComponentName(c, WakeAccessibilityService.class);
    }

    /** Is the user's accessibility grant on disk (whether or not it is bound right now)? */
    static boolean accessibilityGranted(Context c) {
        try {
            String enabled = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            String flat = service(c).flattenToString();
            String shortFlat = service(c).flattenToShortString();
            for (String entry : enabled.split(":")) {
                if (entry.equalsIgnoreCase(flat) || entry.equalsIgnoreCase(shortFlat)) return true;
            }
            return false;
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    static String accessibilityState(Context c) {
        if (WakeAccessibilityService.connected) return "已连接";
        return accessibilityGranted(c) ? "已授权但未连接" : "未开启";
    }

    /** Foreground package, or null when it cannot be read. Accessibility events are the primary source. */
    static String foreground(Context c) {
        if (!canReadUsage(c)) return null;
        try {
            UsageStatsManager stats = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (stats == null) return null;
            long now = System.currentTimeMillis();
            UsageEvents events = stats.queryEvents(now - 60_000L, now);
            if (events == null) return null;
            UsageEvents.Event event = new UsageEvents.Event();
            String last = null;
            long at = 0;
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() != UsageEvents.Event.ACTIVITY_RESUMED) continue;
                if (event.getTimeStamp() >= at) { at = event.getTimeStamp(); last = event.getPackageName(); }
            }
            return last;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    static boolean canReadUsage(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode = Build.VERSION.SDK_INT >= 29
                    ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                    : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    static String result(Context c) {
        switch (prefs(c).getInt("last-result", REASON_UNKNOWN)) {
            case REASON_LAUNCHED: return "已拉起";
            case REASON_BLOCKED: return "未拉起";
            default: return "尚未触发";
        }
    }

    static void show(Activity a) {
        if (!XiaomiControls.enabled()) {
            new AlertDialog.Builder(a).setTitle("唤醒后进入桌面（实验）")
                    .setMessage("目前只在小米电视上启用。")
                    .setNegativeButton("返回", null).show();
            return;
        }
        boolean service = WakeAccessibilityService.connected;
        String a11y = accessibilityState(a) + (service ? "" : "（必须开启，否则没有可用的监听通道）");
        String usage = canReadUsage(a) ? "已授予" : "未授予（可选，只影响亮屏探测这一路）";
        new AlertDialog.Builder(a).setTitle("唤醒后进入桌面（实验）")
                .setMessage("电视亮屏后几秒内，如果停在小米原桌面，就把大米电视拉到前台一次。\n\n"
                        + "只认一个条件：亮屏后 8 秒内、前台必须是小米桌面。看电影、玩游戏或唤醒直接进 HDMI 时，前台不是小米桌面，一律不动；过了 8 秒你按 Home 也不会抢。\n\n"
                        + "为什么是无障碍服务：这版固件拒绝一切第三方前台服务（isCallerSystem=false），普通后台服务熄屏即被杀，所以只能用系统保活的无障碍通道。\n\n"
                        + "状态：" + (enabled(a) ? "已开启" : "已关闭")
                        + "\n无障碍服务：" + a11y
                        + "\n使用情况访问：" + usage
                        + "\n上次结果：" + result(a) + " · " + note(a))
                .setPositiveButton(enabled(a) ? "关闭" : "开启", (d, w) -> toggle(a))
                .setNeutralButton("无障碍设置", (d, w) -> openAccessibility(a))
                .setNegativeButton("返回", null).show();
    }

    static void openAccessibility(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException unavailable) {
            try {
                a.startActivity(new Intent("android.settings.SETTINGS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) {
                Toast.makeText(a, "系统没有无障碍设置页", Toast.LENGTH_LONG).show();
            }
        }
    }

    private static void toggle(Activity a) {
        boolean next = !enabled(a);
        if (next && !accessibilityGranted(a)) {
            enabled(a, true);
            new AlertDialog.Builder(a).setTitle("最后一步：开启无障碍服务")
                    .setMessage("在「设置 → 无障碍 → 已下载的服务」里找到「大米电视」，打开开关并允许。\n\n"
                            + "开关已经先打开，服务一连接就会生效；服务断开时会显示“已授权但未连接”。\n\n"
                            + "如果系统里找不到无障碍设置页，用 adb：\n\n"
                            + "adb shell settings put secure enabled_accessibility_services "
                            + a.getPackageName() + "/" + A11Y_CLASS + "\n"
                            + "adb shell settings put secure accessibility_enabled 1")
                    .setPositiveButton("去设置", (d, w) -> openAccessibility(a))
                    .setNegativeButton("稍后", (d, w) -> Toast.makeText(a, "开关已开，等服务连接后生效", Toast.LENGTH_LONG).show())
                    .show();
            return;
        }
        enabled(a, next);
        Toast.makeText(a, next ? "已开启：亮屏停在小米桌面时进入大米电视" : "已关闭唤醒自动进入",
                Toast.LENGTH_LONG).show();
    }

    static void dump(Context c, java.io.PrintWriter out) {
        out.println("Wake: enabled=" + enabled(c) + " a11y=" + accessibilityState(c)
                + " connected=" + WakeAccessibilityService.connected
                + " usage=" + canReadUsage(c) + " visible=" + visible
                + " result=" + result(c) + " note=" + note(c)
                + " foreground=" + foreground(c));
    }
}
