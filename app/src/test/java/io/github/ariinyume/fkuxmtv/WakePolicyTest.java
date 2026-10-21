package io.github.ariinyume.fkuxmtv;

import org.junit.Test;
import static org.junit.Assert.*;

public class WakePolicyTest {
    /** supported, enabled, interactive, handled, sinceWake, usageReadable, foreground, selfVisible, sinceHandled */
    private static String check(boolean supported, boolean enabled, boolean interactive, boolean handled,
                                long sinceWake, boolean usageReadable, String foreground,
                                boolean selfVisible, long sinceHandled) {
        return WakePolicy.skipReason(supported, enabled, interactive, handled, sinceWake,
                usageReadable, foreground, selfVisible, sinceHandled);
    }

    @Test public void stockHomeOnWakeIsPulledUp() {
        assertNull(check(true, true, true, false, 1600, true, "com.mitv.tvhome", false, 60_000));
    }

    @Test public void hdmiOrAnyOtherForegroundIsNeverHijacked() {
        assertNotNull(check(true, true, true, false, 1600, true, "com.xiaomi.mitv.tvplayer", false, 60_000));
        assertNotNull(check(true, true, true, false, 1600, true, "com.xiaodianshi.tv.yst", false, 60_000));
    }

    @Test public void unreadableForegroundFailsClosed() {
        assertNotNull(check(true, true, true, false, 1600, false, "com.mitv.tvhome", false, 60_000));
        assertNotNull(check(true, true, true, false, 1600, true, null, false, 60_000));
        assertNotNull(check(true, true, true, false, 1600, true, "", false, 60_000));
    }

    @Test public void unknownForegroundIsRetriedOnce() {
        assertTrue(WakePolicy.retryable(check(true, true, true, false, 1600, true, null, false, 60_000)));
        assertFalse(WakePolicy.retryable(check(true, true, true, false, 1600, true, "com.mitv.tvplayer", false, 60_000)));
    }

    @Test public void oneShotPerWakeUp() {
        assertNotNull(check(true, true, true, true, 1600, true, "com.mitv.tvhome", false, 60_000));
    }

    @Test public void alreadyVisibleIsSkipped() {
        assertNotNull(check(true, true, true, false, 1600, true, "com.mitv.tvhome", true, 60_000));
    }

    @Test public void switchOffOrUnsupportedModelIsSkipped() {
        assertNotNull(check(true, false, true, false, 1600, true, "com.mitv.tvhome", false, 60_000));
        assertNotNull(check(false, true, true, false, 1600, true, "com.mitv.tvhome", false, 60_000));
    }

    @Test public void sleepingScreenIsSkipped() {
        assertNotNull(check(true, true, false, false, 1600, true, "com.mitv.tvhome", false, 60_000));
    }

    @Test public void lateWakeUpIsSkipped() {
        assertNull(check(true, true, true, false, WakePolicy.WINDOW_MS, true, "com.mitv.tvhome", false, 60_000));
        assertNotNull(check(true, true, true, false, WakePolicy.WINDOW_MS + 1, true, "com.mitv.tvhome", false, 60_000));
    }

    @Test public void debounceStopsDoublePull() {
        assertNotNull(check(true, true, true, false, 1600, true, "com.mitv.tvhome", false, WakePolicy.DEBOUNCE_MS - 1));
        assertNull(check(true, true, true, false, 1600, true, "com.mitv.tvhome", false, WakePolicy.DEBOUNCE_MS));
        assertNull(check(true, true, true, false, 1600, true, "com.mitv.tvhome", false, -1));
    }
}
