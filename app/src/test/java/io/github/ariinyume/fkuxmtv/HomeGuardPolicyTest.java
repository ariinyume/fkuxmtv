package io.github.ariinyume.fkuxmtv;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeGuardPolicyTest {
    /** supported, enabled, interactive, selfVisible, foreground, sinceUserExit, attempts, sinceAttempt */
    private static String check(boolean supported, boolean enabled, boolean interactive, boolean selfVisible,
                                String foreground, long sinceUserExit, int attempts, long sinceAttempt) {
        return HomeGuardPolicy.blockReason(supported, enabled, interactive, selfVisible,
                foreground, sinceUserExit, attempts, sinceAttempt);
    }

    @Test public void stockHomeIsTakenOver() {
        assertNull(check(true, true, true, false, "com.mitv.tvhome", -1, 0, 60_000));
    }

    @Test public void anyOtherForegroundIsNeverHijacked() {
        assertNotNull(check(true, true, true, false, "com.xiaomi.mitv.tvplayer", -1, 0, 60_000));
        assertNotNull(check(true, true, true, false, "com.xiaodianshi.tv.yst", -1, 0, 60_000));
        assertNotNull(check(true, true, true, false, null, -1, 0, 60_000));
        assertNotNull(check(true, true, true, false, "", -1, 0, 60_000));
    }

    @Test public void switchOffOrUnsupportedModelIsSkipped() {
        assertNotNull(check(true, false, true, false, "com.mitv.tvhome", -1, 0, 60_000));
        assertNotNull(check(false, true, true, false, "com.mitv.tvhome", -1, 0, 60_000));
    }

    @Test public void sleepingScreenIsSkipped() {
        assertNotNull(check(true, true, false, false, "com.mitv.tvhome", -1, 0, 60_000));
    }

    @Test public void alreadyVisibleIsSkipped() {
        assertNotNull(check(true, true, true, true, "com.mitv.tvhome", -1, 0, 60_000));
    }

    @Test public void userInitiatedExitBuysQuietTime() {
        assertNotNull(check(true, true, true, false, "com.mitv.tvhome", 0, 0, 60_000));
        assertNotNull(check(true, true, true, false, "com.mitv.tvhome", HomeGuardPolicy.USER_EXIT_GRACE_MS - 1, 0, 60_000));
        assertNull(check(true, true, true, false, "com.mitv.tvhome", HomeGuardPolicy.USER_EXIT_GRACE_MS, 0, 60_000));
    }

    @Test public void debounceStopsDoublePull() {
        assertNotNull(check(true, true, true, false, "com.mitv.tvhome", -1, 1, HomeGuardPolicy.DEBOUNCE_MS - 1));
        assertNull(check(true, true, true, false, "com.mitv.tvhome", -1, 1, HomeGuardPolicy.DEBOUNCE_MS));
        assertNull(check(true, true, true, false, "com.mitv.tvhome", -1, 1, -1));
    }

    @Test public void attemptCapStopsBounceLoop() {
        assertNull(check(true, true, true, false, "com.mitv.tvhome", -1, HomeGuardPolicy.MAX_ATTEMPTS - 1, 60_000));
        String capped = check(true, true, true, false, "com.mitv.tvhome", -1, HomeGuardPolicy.MAX_ATTEMPTS, 60_000);
        assertNotNull(capped);
        assertTrue(HomeGuardPolicy.capped(capped));
    }
}
