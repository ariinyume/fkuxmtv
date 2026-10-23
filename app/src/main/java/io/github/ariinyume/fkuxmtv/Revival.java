package io.github.ariinyume.fkuxmtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Puts the accessibility grant back after the firmware strips it.
 *
 * Power-key standby runs {@code PowerManagerService.killAllThirdApp}, which force-stops every
 * third-party app. Android reacts to a force-stop by removing that app's services from
 * {@code enabled_accessibility_services} — permanently, with a "crashed services" mark — so the
 * guard and the wake handler die with it and the TV comes back on the stock launcher.
 *
 * Two things are needed to survive that:
 *
 * 1. <b>A trigger.</b> A force-stopped app gets no broadcasts, no jobs and no alarms, so nothing
 *    of ours can run. The system still rebinds approved notification listeners after the process
 *    dies, which is why {@link RevivalListener} exists: being bound is our way back into a process.
 * 2. <b>The write.</b> {@link #ensure(Context, String)} rewrites the entry into
 *    {@code enabled_accessibility_services} with {@code WRITE_SECURE_SETTINGS}, which has to be
 *    granted once over adb ({@code pm grant}). Android then binds the service again by itself.
 *
 * Nothing here pulls the home screen to the front: whether to take over is decided by
 * {@link HomeGuardPolicy} / {@link WakePolicy}, which only act when the stock launcher is actually
 * in front. A revival that grabs focus on its own would fight HDMI-CEC and the user.
 */
final class Revival {
    private static final String TAG = "GuiZangRevive";
    private static final String PREFS = "revival";
    static final String PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";

    /** Give Android a moment to bind the service on its own before forcing a rebind. */
    private static final long BIND_GRACE_MS = 4000;
    /** Two writes in a row get coalesced; the remove has to land before the add. */
    private static final long REBIND_GAP_MS = 1500;
    /** One forced rebind per half minute is plenty; more would just fight the system binder. */
    private static final long REBIND_COOLDOWN_MS = 30_000;
    /**
     * Rounds in one repair chain: check → write the entry back if it was stripped → force a
     * rebind. Three is enough for "stripped entries" (restore, then bind) and for "entry kept but
     * nothing bound" (splice once), and bounded so a firmware that never binds cannot spin.
     */
    private static final int MAX_ROUNDS = 3;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Context app;
    private static boolean repairRunning;

    private Revival() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** On by default: on this firmware the guard is useless without it. */
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", true); }

    static void enabled(Context c, boolean value) {
        prefs(c).edit().putBoolean("enabled", value).apply();
    }

    /** {@code Settings.Secure.ENABLED_NOTIFICATION_LISTENERS} is hidden in the SDK; the key is stable. */
    private static final String LISTENERS = "enabled_notification_listeners";

    static boolean granted(Context c) {
        try {
            return c.getPackageManager().checkPermission(PERMISSION, c.getPackageName())
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException unknown) {
            return false;
        }
    }

    /** Is the notification-listener approval still on disk? Survives force-stop; that is the point. */
    static boolean listenerGranted(Context c) {
        try {
            String list = Settings.Secure.getString(c.getContentResolver(), LISTENERS);
            if (list == null) return false;
            for (String entry : list.split(":")) {
                ComponentName name = ComponentName.unflattenFromString(entry);
                if (name != null && c.getPackageName().equals(name.getPackageName())) return true;
            }
            return false;
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    /** Only repair what the user asked for: both switches off means hands off. */
    static boolean wanted(Context c) {
        return XiaomiControls.enabled() && enabled(c) && (HomeGuard.enabled(c) || WakeHome.enabled(c));
    }

    static String own(Context c) {
        return new ComponentName(c, WakeAccessibilityService.class).flattenToString();
    }

    /**
     * Called from every entry into a process (application start, listener bind, service bind) and
     * from the settings page. Never launches anything; it only restores the grant and asks Android
     * to bind the service when it is on disk but not running.
     */
    static boolean ensure(Context context, String source) {
        Context c = context.getApplicationContext();
        app = c;
        if (!wanted(c)) {
            Log.i(TAG, "skip (" + source + "): automatic revival is off");
            return false;
        }
        if (!granted(c)) {
            Log.w(TAG, "no " + PERMISSION + "; cannot restore the grant (" + source + ")");
            note(c, "缺少写设置权限，无法自行恢复（" + source + "）");
            return false;
        }
        boolean restored;
        try {
            restored = restoreEntry(c, source);
            if (!restored && !WakeAccessibilityService.connected) {
                // Listed, but nothing bound: the master switch may have been flipped off, or the
                // service was killed while its entry survived. Nudging the switch is enough.
                Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            }
        } catch (RuntimeException refused) {
            Log.w(TAG, "could not write the accessibility list (" + source + ")", refused);
            note(c, "写设置被拒绝（" + source + "）：" + refused.getClass().getSimpleName());
            return false;
        }
        if (!WakeAccessibilityService.connected) startRepair(c, source);
        return restored;
    }

    /**
     * Puts the entry back if the firmware stripped it. Separate from {@link #ensure} because the
     * repair chain needs it on every round: the force-stop that removes the entry lands a few
     * hundred milliseconds *after* the listener binds, so the first read often still sees the old
     * list and the entry only turns up missing on the next round.
     *
     * @return true when the entry had been missing and has just been written back.
     */
    private static boolean restoreEntry(Context c, String source) {
        String current = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String me = own(c);
        if (A11yList.has(current, me)) return false;
        String next = A11yList.with(current, me);
        Settings.Secure.putString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
        Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        bump(c, "恢复", source);
        Log.i(TAG, "grant restored via " + source + ": " + next);
        return true;
    }

    /** Starts at most one repair chain per process; rounds re-arm themselves while they run. */
    private static void startRepair(Context c, String source) {
        if (repairRunning) return;
        repairRunning = true;
        round(c, source, 0);
    }

    private static void round(Context c, String source, int n) {
        HANDLER.postDelayed(() -> {
            if (WakeAccessibilityService.connected) { repairRunning = false; return; }
            if (!granted(c) || !wanted(c)) { repairRunning = false; return; }
            try {
                if (restoreEntry(c, source)) {
                    // Freshly written: Android binds it by itself within a second or two. If it
                    // does not, the next round does the remove/add dance.
                    round(c, source, n + 1);
                    return;
                }
                if (n == 0 && recentlyRebound(c)) { repairRunning = false; return; }
                if (n >= MAX_ROUNDS) {
                    Log.w(TAG, "gave up repairing the grant after " + n + " rounds (" + source + ")");
                    note(c, "本轮恢复没成功，下次开机或手动打开应用时再试（" + source + "）");
                    repairRunning = false;
                    return;
                }
                rebind(c, source);
                round(c, source, n + 1);
            } catch (RuntimeException failed) {
                Log.w(TAG, "repair round failed (" + source + ")", failed);
                note(c, "恢复过程出错（" + source + "）：" + failed.getClass().getSimpleName());
                repairRunning = false;
            }
        }, BIND_GRACE_MS);
    }

    private static boolean recentlyRebound(Context c) {
        long since = SystemClock.elapsedRealtime() - prefs(c).getLong("rebind-at", -1L);
        return since >= 0 && since < REBIND_COOLDOWN_MS;
    }

    /**
     * Enabled but not running. Writing the list without us and then with us is what makes Android
     * bind the service again; a single identical write is coalesced into a no-op.
     */
    static void rebind(Context c, String source) {
        if (!granted(c) || WakeAccessibilityService.connected) return;
        String me = own(c);
        try {
            String current = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (!A11yList.has(current, me)) return;   // nothing to rebind; ensure() owns that case
            prefs(c).edit().putLong("rebind-at", SystemClock.elapsedRealtime()).apply();
            Settings.Secure.putString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, A11yList.without(current, me));
            HANDLER.postDelayed(() -> {
                try {
                    // Re-read: another accessibility service may have been added meanwhile.
                    String now = Settings.Secure.getString(c.getContentResolver(),
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                    Settings.Secure.putString(c.getContentResolver(),
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, A11yList.with(now, me));
                    bump(c, "重绑", source);
                    Log.i(TAG, "asked Android to bind the service again (" + source + ")");
                } catch (RuntimeException refused) {
                    Log.w(TAG, "rebind failed", refused);
                }
            }, REBIND_GAP_MS);
        } catch (RuntimeException unreadable) {
            Log.w(TAG, "could not read the accessibility list", unreadable);
        }
    }

    /** Entry point for {@link App}: a new process is the cheapest place to notice a missing grant. */
    static void onProcessStart(Context context) {
        Context c = context.getApplicationContext();
        if (!wanted(c)) return;
        // Let the process finish starting; a settings write during Application.onCreate competes
        // with the very binder call that started us.
        HANDLER.postDelayed(() -> ensure(c, "进程启动"), 400);
    }

    private static void bump(Context c, String kind, String source) {
        SharedPreferences p = prefs(c);
        p.edit()
                .putInt("count", p.getInt("count", 0) + 1)
                .putLong("last-at", SystemClock.elapsedRealtime())
                .putLong("last-wall", System.currentTimeMillis())
                .putString("last-source", source)
                .putString("last-note", kind + "成功（" + source + "）· " + stamp())
                .apply();
    }

    static int count(Context c) { return prefs(c).getInt("count", 0); }

    static String lastAt(Context c) {
        long wall = prefs(c).getLong("last-wall", -1L);
        if (wall < 0) return "还没有记录";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(wall));
    }

    static void note(Context c, String value) {
        prefs(c).edit().putString("last-note", value + " · " + stamp()).apply();
        Log.i(TAG, value);
    }

    static String note(Context c) { return prefs(c).getString("last-note", "还没有记录"); }

    static String state(Context c) {
        if (!enabled(c)) return "已关闭";
        if (!granted(c)) return "未就绪 · 缺少写设置权限";
        if (!listenerGranted(c)) return "部分就绪 · 没有通知监听（被强杀后回不来）";
        return "已就绪 · 已恢复 " + count(c) + " 次";
    }

    static void show(Activity a) {
        new AlertDialog.Builder(a).setTitle("被系统杀掉后自动恢复")
                .setMessage("按遥控器电源键待机时，小米固件会强制停止所有第三方应用，并把本应用的无障碍授权从系统设置里删掉。"
                        + "没有这一步，唤醒后只能看到小米桌面。\n\n"
                        + "恢复方式：系统杀掉进程后会重新绑定「通知监听」，我们借这次绑定把无障碍授权写回去，"
                        + "守护随即重新生效。全程不读取任何通知内容。\n\n"
                        + "写设置权限：" + (granted(a) ? "已授予" : "未授予（只能用 adb 授予一次）")
                        + "\n通知监听：" + (listenerGranted(a) ? "已授权" : "未授权")
                        + "\n自动恢复开关：" + (enabled(a) ? "已开启" : "已关闭")
                        + "\n已恢复次数：" + count(a)
                        + "\n上次恢复：" + lastAt(a)
                        + "\n上次记录：" + note(a)
                        + (granted(a) && listenerGranted(a) ? "" : "\n\n在电脑上执行（两个包名都要）：\n"
                        + "adb shell pm grant " + a.getPackageName() + " " + PERMISSION + "\n"
                        + "adb shell cmd notification allow_listener "
                        + a.getPackageName() + "/" + a.getPackageName() + ".RevivalListener"))
                .setPositiveButton(enabled(a) ? "关闭自动恢复" : "开启自动恢复", (d, w) -> {
                    boolean next = !enabled(a);
                    enabled(a, next);
                    if (next) ensure(a, "手动");
                    Toast.makeText(a, next ? "已开启：被强杀后会自行恢复" : "已关闭自动恢复，被强杀后需手动打开应用",
                            Toast.LENGTH_LONG).show();
                })
                .setNeutralButton("立即自检", (d, w) -> {
                    boolean restored = ensure(a, "手动自检");
                    if (!WakeAccessibilityService.connected) rebind(a, "手动自检");
                    Toast.makeText(a, restored ? "已把无障碍授权写回" : "授权已在，已要求系统重新绑定",
                            Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("返回", null).show();
    }

    static void dump(Context c, java.io.PrintWriter out) {
        out.println("Revival: enabled=" + enabled(c) + " wanted=" + wanted(c)
                + " writeSettings=" + granted(c) + " listener=" + listenerGranted(c)
                + " count=" + count(c) + " last=" + lastAt(c)
                + " source=" + prefs(c).getString("last-source", "-")
                + " started=" + (app != null)
                + " note=" + note(c));
    }

    private static String stamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
    }
}
