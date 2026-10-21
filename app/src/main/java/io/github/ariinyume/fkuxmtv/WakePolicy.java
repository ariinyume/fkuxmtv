package io.github.ariinyume.fkuxmtv;

/** Why a wake-up may or may not pull this home to the front. Pure logic, unit tested. */
final class WakePolicy {
    static final String STOCK_HOME = "com.mitv.tvhome";
    /** Give the firmware time to settle after SCREEN_ON before probing the foreground app. */
    static final long FIRST_CHECK_DELAY_MS = 1500;
    /** One probe retry only while the foreground answer is still unknown. */
    static final long RETRY_DELAY_MS = 1100;
    /** A wake-up older than this is no longer ours to handle. */
    static final long WINDOW_MS = 8000;
    /** Never pull the home twice in quick succession. */
    static final long DEBOUNCE_MS = 4000;

    private WakePolicy() { }

    static String skipReason(boolean supported, boolean enabled, boolean interactive, boolean handled,
                             long sinceWakeMs, boolean foregroundReadable, String foreground,
                             boolean selfVisible, long sinceHandledMs) {
        if (!supported) return "机型未适配";
        if (!enabled) return "唤醒自动进入已关闭";
        if (!interactive) return "屏幕已熄灭";
        if (handled) return "本次唤醒已处理";
        if (sinceWakeMs < 0) return "没有捕捉到本次亮屏，先不动";
        if (sinceWakeMs > WINDOW_MS) return "超出本次唤醒处理窗口";
        if (!foregroundReadable || foreground == null || foreground.isEmpty()) return "读不到前台应用，先不动";
        if (!STOCK_HOME.equals(foreground)) return "前台不是小米桌面（" + foreground + "）";
        if (selfVisible) return "桌面已经在前台";
        if (sinceHandledMs >= 0 && sinceHandledMs < DEBOUNCE_MS) return "刚拉起过，忽略这次";
        return null;
    }

    /** Unknown foreground is worth one retry; a definite answer ends this wake-up. */
    static boolean retryable(String reason) {
        return "读不到前台应用，先不动".equals(reason);
    }
}
