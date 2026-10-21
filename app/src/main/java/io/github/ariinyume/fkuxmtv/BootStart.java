package io.github.ariinyume.fkuxmtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/** Once-per-full-boot launch. Never handles screen-on, HDMI or Home keys. */
public final class BootStart {
    private static final String PREFS = "boot-start";
    private static final String EXTRA = "guizang.boot-count";
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    /** On by default: a fresh install should boot into this home. One switch turns it off. */
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", true); }
    /** Android 10+ lets a background start through only when this app-op is allowed. */
    static boolean overlayAllowed(Context c) { return Settings.canDrawOverlays(c); }
    private static int bootCount(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }
    static void enter(Activity a) {
        int boot = bootCount(a);
        String reason = BootStartPolicy.skipReason(XiaomiControls.enabled(), enabled(a), boot, prefs(a).getInt("attempted", -1),
                SystemClock.elapsedRealtime());
        if (reason != null) { Log.i("GuiZangBoot", reason); return; }
        if (!prefs(a).edit().putInt("attempted", boot).putString("status", "系统已调用启动入口，等待界面确认").commit()) return;
        try {
            a.startActivity(new Intent(a, FrameHomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA, boot));
        } catch (RuntimeException error) {
            status(a, "启动失败：" + error.getClass().getSimpleName());
            Log.w("GuiZangBoot", "Launch rejected", error);
        }
    }
    private static Intent homeIntent(Context c, int boot) {
        return new Intent(c, FrameHomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA, boot);
    }
    /**
     * Same gate as {@link #enter(Activity)}, reachable from a broadcast receiver.
     * Marked only after the start was accepted, so a rejected call can still be retried once.
     */
    static boolean enterFromBoot(Context context, String source) {
        Context app = context.getApplicationContext();
        int boot = bootCount(app);
        String reason = BootStartPolicy.skipReason(XiaomiControls.enabled(), enabled(app), boot,
                prefs(app).getInt("attempted", -1), SystemClock.elapsedRealtime());
        if (reason != null) {
            Log.i("GuiZangBoot", "skip (" + source + "): " + reason);
            return false;
        }
        if (!overlayAllowed(app)) {
            Log.w("GuiZangBoot", "no SYSTEM_ALERT_WINDOW: background launch will be blocked");
        }
        try {
            app.startActivity(homeIntent(app, boot));
        } catch (RuntimeException error) {
            status(app, "后台启动被系统拒绝（" + source + "）：" + error.getClass().getSimpleName());
            Log.w("GuiZangBoot", "Launch rejected via " + source, error);
            return false;
        }
        prefs(app).edit().putInt("attempted", boot)
                .putString("status", "已由" + source + "进入桌面").commit();
        Log.i("GuiZangBoot", "launched via " + source + ", boot=" + boot);
        return true;
    }
    private static boolean configured(Context c) {
        return c.getPackageName().equals(Settings.System.getString(c.getContentResolver(), "start_3rd_app"));
    }
    private static void status(Context c, String value) {
        prefs(c).edit().putString("status", value).apply();
        Log.i("GuiZangBoot", value);
    }
    static void confirm(Activity activity) {
        Intent intent = activity.getIntent();
        int boot = intent.getIntExtra(EXTRA, -1);
        if (boot < 0) return;
        intent.removeExtra(EXTRA);
        if (boot == bootCount(activity) && prefs(activity).getInt("attempted", -1) == boot && enabled(activity)) {
            status(activity, "已在本次开机后进入桌面");
        }
    }
    static void settings(Activity a, Runnable systemSettings) {
        new AlertDialog.Builder(a).setTitle("设置")
                .setItems(new String[]{"电视系统设置", "开机自动进入：" + (enabled(a) ? "已开启" : "已关闭")},
                        (d, i) -> { if (i == 0) systemSettings.run(); else show(a); })
                .setNegativeButton("返回", null).show();
    }
    static void show(Activity a) {
        if (!XiaomiControls.enabled()) {
            new AlertDialog.Builder(a).setTitle("开机自动进入（实验）")
                    .setMessage("目前仅适配 MiTV-MTSU0。其他机型请先使用普通应用入口。")
                    .setNegativeButton("返回", null).show();
            return;
        }
        String state = prefs(a).getString("status", "尚未进行完整重启测试");
        new AlertDialog.Builder(a).setTitle("开机自动进入")
                .setMessage("完整开机时进入本桌面一次；不接管 Home 键，同一次开机只尝试一次。\n\n"
                        + "路径一 · 系统开机广播：" + (overlayAllowed(a) ? "悬浮窗权限已授予" : "缺悬浮窗权限，需电脑授权一次") + "\n"
                        + "路径二 · 小米系统启动入口：" + (configured(a) ? "已配置" : "未配置（可选，作兜底）") + "\n\n"
                        + "上次状态：" + state)
                .setPositiveButton(enabled(a) ? "关闭自动进入" : "开启自动进入", (d, which) -> {
                    boolean value = !enabled(a);
                    prefs(a).edit().putBoolean("enabled", value).apply();
                    Toast.makeText(a, value ? "已开启，下次完整重启时进入" : "已关闭开机自动进入", Toast.LENGTH_LONG).show();
                })
                .setNeutralButton("悬浮窗授权", (d, w) -> overlaySettings(a))
                .setNegativeButton("返回", null).show();
    }
    private static void overlaySettings(Activity a) {
        if (overlayAllowed(a)) {
            Toast.makeText(a, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + a.getPackageName()));
        try {
            if (a.getPackageManager().resolveActivity(intent, 0) != null) {
                a.startActivity(intent);
                return;
            }
        } catch (RuntimeException unavailable) {
            Log.w("GuiZangBoot", "No overlay settings page", unavailable);
        }
        new AlertDialog.Builder(a).setTitle("用电脑授权悬浮窗")
                .setMessage("本机系统里没有悬浮窗授权页，请在电脑上执行：\n\n"
                        + "adb shell appops set " + a.getPackageName() + " SYSTEM_ALERT_WINDOW allow\n\n"
                        + "然后重新开机验证。没有这一步，开机广播会被后台启动限制拦下。")
                .setPositiveButton("返回", null).show();
    }
    static void dump(Context c, java.io.PrintWriter out) {
        out.println("BootStart: enabled=" + enabled(c) + " configured=" + configured(c)
                + " overlay=" + overlayAllowed(c)
                + " boot=" + bootCount(c) + " attempted=" + prefs(c).getInt("attempted", -1)
                + " status=" + prefs(c).getString("status", "none"));
    }
}
