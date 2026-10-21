package io.github.ariinyume.fkuxmtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.widget.Toast;

/**
 * Opt-in "pull this home up when the TV wakes". Only acts while the stock Xiaomi home is the
 * foreground app, so waking straight into HDMI or an app is never hijacked.
 */
public final class WakeHome {
    private static final String PREFS = "wake-home";
    static final int REASON_UNKNOWN = 0, REASON_LAUNCHED = 1, REASON_BLOCKED = 2;

    /** Set from FrameHomeActivity.onResume / onPause. */
    static volatile boolean visible;

    private WakeHome() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false); }

    static void enabled(Context c, boolean value) {
        prefs(c).edit().putBoolean("enabled", value).apply();
        if (value) start(c); else stop(c);
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

    /** Foreground package, or null when usage access is missing or unreadable. */
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

    static boolean overlay(Context c) { return Settings.canDrawOverlays(c); }

    static void start(Context c) {
        Intent intent = new Intent(c, WakeService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(intent); else c.startService(intent);
        } catch (RuntimeException refused) {
            android.util.Log.w("GuiZangWake", "Wake service refused to start", refused);
        }
    }

    static void stop(Context c) {
        try { c.stopService(new Intent(c, WakeService.class)); } catch (RuntimeException ignored) { }
    }

    static String result(Context c) {
        switch (prefs(c).getInt("last-result", REASON_UNKNOWN)) {
            case REASON_LAUNCHED: return "已拉起";
            case REASON_BLOCKED: return "系统拦截，未拉起";
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
        String usage = canReadUsage(a) ? "已授予" : "未授予（必须授予，否则不会抢画面）";
        String overlay = overlay(a) ? "已授予" : "未授予（可选，能提高被系统放行的概率）";
        new AlertDialog.Builder(a).setTitle("唤醒后进入桌面（实验）")
                .setMessage("电视亮屏时，如果当时停在小米原桌面，就把大米电视拉到前台一次。\n\n"
                        + "只认一个条件：亮屏瞬间前台必须是小米桌面。看电影、玩游戏或唤醒直接进 HDMI 时，前台不是小米桌面，一律不动。\n\n"
                        + "状态：" + (enabled(a) ? "已开启" : "已关闭")
                        + "\n使用情况访问：" + usage
                        + "\n悬浮窗权限：" + overlay
                        + "\n上次结果：" + result(a) + " · " + note(a))
                .setPositiveButton(enabled(a) ? "关闭" : "开启", (d, w) -> toggle(a))
                .setNeutralButton("权限设置", (d, w) -> permissionMenu(a))
                .setNegativeButton("返回", null).show();
    }

    private static void permissionMenu(Activity a) {
        new AlertDialog.Builder(a).setTitle("权限")
                .setItems(new String[]{"使用情况访问（必需）", "悬浮窗（可选）"}, (d, i) -> {
                    try {
                        if (i == 0)
                            a.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        else
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + a.getPackageName()))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException unavailable) {
                        Toast.makeText(a, "系统没有这个设置页，可用 adb 授权", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("返回", null).show();
    }

    private static void toggle(Activity a) {
        boolean next = !enabled(a);
        if (next && !canReadUsage(a)) {
            new AlertDialog.Builder(a).setTitle("需要“使用情况访问”")
                    .setMessage("做不到判断前台应用，就不会主动抢画面。\n\n"
                            + "请在系统设置里给「大米电视」开启使用情况访问；如果系统没有这一项，连接电视后执行：\n\n"
                            + "adb shell appops set " + a.getPackageName() + " GET_USAGE_STATS allow")
                    .setPositiveButton("去设置", (d, w) -> permissionMenu(a))
                    .setNegativeButton("返回", null).show();
            return;
        }
        enabled(a, next);
        Toast.makeText(a, next ? "已开启：亮屏停在小米桌面时进入大米电视" : "已关闭唤醒自动进入",
                Toast.LENGTH_LONG).show();
        if (next) start(a);
    }

    static void dump(Context c, java.io.PrintWriter out) {
        out.println("Wake: enabled=" + enabled(c) + " usage=" + canReadUsage(c) + " overlay=" + overlay(c)
                + " visible=" + visible + " result=" + result(c) + " note=" + note(c)
                + " foreground=" + foreground(c));
    }
}
