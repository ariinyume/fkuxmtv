package io.github.ariinyume.fkuxmtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Storage and UI for the home guard. The trigger lives in {@link WakeAccessibilityService};
 * the decisions live in {@link HomeGuardPolicy}.
 *
 * Kept separate from {@link WakeHome} on purpose: waking into HDMI must not be disturbed by this,
 * and turning the wake feature off must not silently turn the guard off too.
 */
final class HomeGuard {
    private static final String PREFS = "home-guard";

    private HomeGuard() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** On by default on Xiaomi TVs: this build exists to keep our home in front. Off is one click away. */
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", true); }

    static void enabled(Context c, boolean value) {
        prefs(c).edit().putBoolean("enabled", value).apply();
    }

    /** The user deliberately left for the stock home (Back key or the 小米原桌面 tile). */
    static void userExited(Context c) {
        prefs(c).edit().putLong("user-exit", SystemClock.elapsedRealtime()).apply();
    }

    /**
     * The quiet period protects one visit to the stock home, not the rest of the session: as soon
     * as something else takes the foreground, the next Home press is ours again.
     */
    static void clearUserExit(Context c) {
        SharedPreferences p = prefs(c);
        if (p.contains("user-exit")) p.edit().remove("user-exit").apply();
    }

    static long sinceUserExit(Context c, long now) {
        long at = prefs(c).getLong("user-exit", -1L);
        if (at < 0) return -1;
        long delta = now - at;
        // A reboot resets elapsedRealtime: treat a negative delta as "no recent exit".
        return delta < 0 ? -1 : delta;
    }

    /** Records a takeover and returns how many happened inside the current window. */
    static int noteAttempt(Context c, long now) {
        SharedPreferences p = prefs(c);
        long start = p.getLong("window-start", -1L);
        int count = p.getInt("window-count", 0);
        if (start < 0 || now - start < 0 || now - start > HomeGuardPolicy.ATTEMPT_WINDOW_MS) {
            p.edit().putLong("window-start", now).putInt("window-count", 1)
                    .putLong("attempt-at", now).apply();
            return 1;
        }
        count++;
        p.edit().putInt("window-count", count).putLong("attempt-at", now).apply();
        return count;
    }

    static int attempts(Context c, long now) {
        long start = prefs(c).getLong("window-start", -1L);
        if (start < 0 || now - start < 0 || now - start > HomeGuardPolicy.ATTEMPT_WINDOW_MS) return 0;
        return prefs(c).getInt("window-count", 0);
    }

    static long sinceAttempt(Context c, long now) {
        long at = prefs(c).getLong("attempt-at", -1L);
        if (at < 0) return -1;
        long delta = now - at;
        return delta < 0 ? -1 : delta;
    }

    /** A fresh screen-on deserves a fresh budget; otherwise one bad boot would disable the guard. */
    static void resetAttempts(Context c) {
        prefs(c).edit().remove("window-start").remove("window-count").apply();
    }

    static void note(Context c, String value) {
        prefs(c).edit().putString("last-note", value).apply();
    }

    static String note(Context c) { return prefs(c).getString("last-note", "还没有记录"); }

    static String state(Context c) {
        if (!enabled(c)) return "已关闭";
        return WakeAccessibilityService.connected ? "已开启 · 守护中" : "已开启 · 无障碍未连接，暂不生效";
    }

    static void show(Activity a) {
        if (!XiaomiControls.enabled()) {
            new AlertDialog.Builder(a).setTitle("桌面守护（实验）")
                    .setMessage("目前只在小米电视上启用。").setNegativeButton("返回", null).show();
            return;
        }
        boolean on = enabled(a);
        new AlertDialog.Builder(a).setTitle("桌面守护（实验）")
                .setMessage("按下 Home、或从任何应用退回电视首页时，只要前台出现小米原桌面，"
                        + "就自动切回大米电视。\n\n"
                        + "不会抢的情况：切换 HDMI / 打开电影 / 应用把自己拉到前台时，前台不是小米桌面，一律不动；"
                        + "每次接管前会先等 1.5 秒再确认一次，避免打断 CEC 切源。\n\n"
                        + "想用原来的首页：在大米电视里按返回键，或选「小米原桌面」磁贴——"
                        + "这样主动过去的，5 分钟内不会被抢回来。\n\n"
                        + "状态：" + state(a)
                        + "\n电视型号：" + XiaomiControls.model()
                        + "\n上次记录：" + note(a))
                .setPositiveButton(on ? "关闭守护" : "开启守护", (d, w) -> toggle(a))
                .setNeutralButton("无障碍设置", (d, w) -> settings(a))
                .setNegativeButton("返回", null).show();
    }

    private static void toggle(Activity a) {
        boolean next = !enabled(a);
        enabled(a, next);
        if (next) resetAttempts(a);
        Toast.makeText(a, next ? "桌面守护已开启：回到小米桌面会自动切回" : "桌面守护已关闭，Home 键回原桌面",
                Toast.LENGTH_LONG).show();
        if (next && !WakeAccessibilityService.connected) {
            new AlertDialog.Builder(a).setTitle("还差一步：无障碍服务")
                    .setMessage("守护要靠无障碍服务接收窗口切换事件。现在它没有连接。\n\n"
                            + "在「设置 → 无障碍」里打开「大米电视 · 唤醒进入」；"
                            + "如果系统里没有这个页面，用 adb：\n\n"
                            + "adb shell settings put secure enabled_accessibility_services "
                            + a.getPackageName() + "/" + a.getPackageName() + ".WakeAccessibilityService\n"
                            + "adb shell settings put secure accessibility_enabled 1")
                    .setPositiveButton("去设置", (d, w) -> settings(a))
                    .setNegativeButton("稍后", null).show();
        }
    }

    private static void settings(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException unavailable) {
            Toast.makeText(a, "系统没有无障碍设置页", Toast.LENGTH_LONG).show();
        }
    }

    static void dump(Context c, java.io.PrintWriter out) {
        long now = SystemClock.elapsedRealtime();
        out.println("Guard: enabled=" + enabled(c) + " connected=" + WakeAccessibilityService.connected
                + " attempts=" + attempts(c, now) + " sinceAttempt=" + sinceAttempt(c, now)
                + " sinceUserExit=" + sinceUserExit(c, now) + " note=" + note(c));
    }
}
