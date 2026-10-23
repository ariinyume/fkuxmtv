package io.github.ariinyume.fkuxmtv;

/**
 * "Stay on top of the stock launcher" gate. Pure logic, unit tested.
 *
 * The firmware does not let a third-party app claim HOME
 * ({@code MitvSettings.isThirdpartyHomeAllowed} is a system-signed whitelist), and the HOME key
 * is hard-wired to {@code com.mitv.tvhome}. So instead of replacing the launcher we put ourselves
 * back in front whenever the stock home actually becomes visible.
 *
 * Two escapes keep this from becoming a trap:
 * 1. A user-initiated exit (Back key or the 小米原桌面 tile) buys {@link #USER_EXIT_GRACE_MS}
 *    of quiet, so you can use the stock home on purpose.
 * 2. A hard cap of {@link #MAX_ATTEMPTS} per minute, so a firmware bounce loop cannot ping-pong.
 */
final class HomeGuardPolicy {
    static final String STOCK_HOME = "com.mitv.tvhome";

    /** Let CEC/HDMI handovers finish before touching the front. */
    static final long SETTLE_MS = 1500;
    /** Minimum gap between two takeovers. */
    static final long DEBOUNCE_MS = 4000;
    /** Hard cap inside one window, so a bounce loop cannot ping-pong forever. */
    static final int MAX_ATTEMPTS = 3;
    static final long ATTEMPT_WINDOW_MS = 60_000;
    /** Quiet period after the user deliberately switched to the stock home. */
    static final long USER_EXIT_GRACE_MS = 300_000;

    private HomeGuardPolicy() { }

    /**
     * @param sinceUserExitMs  ms since the user deliberately left for the stock home, -1 = never
     * @param attemptsInWindow takeovers already made inside the current window
     * @param sinceAttemptMs   ms since the last takeover, -1 = never
     * @return null when the takeover may proceed, otherwise the reason it must not
     */
    static String blockReason(boolean supported, boolean enabled, boolean interactive,
                              boolean selfVisible, String foreground,
                              long sinceUserExitMs, int attemptsInWindow, long sinceAttemptMs) {
        if (!supported) return "机型未适配";
        if (!enabled) return "桌面守护已关闭";
        if (!interactive) return "屏幕已熄灭";
        if (foreground == null || foreground.isEmpty()) return "读不到前台应用，先不动";
        if (!STOCK_HOME.equals(foreground)) return "前台不是小米桌面（" + foreground + "）";
        if (selfVisible) return "桌面已经在前台";
        if (sinceUserExitMs >= 0 && sinceUserExitMs < USER_EXIT_GRACE_MS) return "你刚自己切到小米桌面，安静期内不接管";
        if (attemptsInWindow >= MAX_ATTEMPTS) return "接管过于频繁，本次已暂停";
        if (sinceAttemptMs >= 0 && sinceAttemptMs < DEBOUNCE_MS) return "刚接管过，忽略这次";
        return null;
    }

    /** Reaching the cap is reported once, not retried in a loop. */
    static boolean capped(String reason) {
        return "接管过于频繁，本次已暂停".equals(reason);
    }
}
