package io.github.ariinyume.fkuxmtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/**
 * The recovery path rewrites a system setting other apps also live in, so the list handling is
 * pinned down here: a wrong entry would either break Xiaomi's voice services or leave us unbindable.
 *
 * Note the expected strings: every write goes out in the long {@code pkg/pkg.Cls} form, because
 * the list is re-serialized from the normalized entries.
 */
public class A11yListTest {
    private static final String VOICE = "com.xiaomi.voicecontrol/.VoiceAccessibilityService";
    private static final String VOICE_LONG =
            "com.xiaomi.voicecontrol/com.xiaomi.voicecontrol.VoiceAccessibilityService";
    private static final String KARAOKE =
            "com.xiaomi.mitv.karaoke.service/com.loostone.puremic.voice.service.accessibility.PuremicKeyService";
    private static final String ME =
            "io.github.ariinyume.fkuxmtv/io.github.ariinyume.fkuxmtv.WakeAccessibilityService";
    private static final String ME_SHORT = "io.github.ariinyume.fkuxmtv/.WakeAccessibilityService";

    @Test public void shortFormBecomesFullPackageName() {
        assertEquals("a.b/a.b.C", A11yList.normalize("a.b/.C"));
        assertEquals("a.b/a.b.C", A11yList.normalize("a.b/a.b.C"));
        assertEquals("a.b/a.b.x.C", A11yList.normalize("a.b/.x.C"));
    }

    @Test public void malformedEntriesPassThroughOrVanish() {
        assertNull(A11yList.normalize(null));
        assertNull(A11yList.normalize("   "));
        assertEquals("plain.package", A11yList.normalize("plain.package"));
        assertEquals("weird/", A11yList.normalize("weird/"));
    }

    @Test public void hasUnderstandsBothForms() {
        String list = VOICE + ":" + KARAOKE;
        assertTrue(A11yList.has(list, VOICE));
        assertTrue(A11yList.has(list, VOICE_LONG));
        assertFalse(A11yList.has(list, ME));
        // The firmware writes the short form; our own component name is the long one.
        assertTrue(A11yList.has(list + ":" + ME_SHORT, ME));
    }

    @Test public void withAppendsWithoutMovingTheOthers() {
        String list = VOICE + ":" + KARAOKE;
        assertEquals(VOICE_LONG + ":" + KARAOKE + ":" + ME, A11yList.with(list, ME_SHORT));
        // Already there in either form: the value must not change at all.
        assertEquals(VOICE_LONG + ":" + ME, A11yList.with(VOICE + ":" + ME, ME_SHORT));
    }

    @Test public void withOnAnEmptyListIsJustTheService() {
        assertEquals(ME, A11yList.with(null, ME));
        assertEquals(ME, A11yList.with("", ME));
        assertEquals(ME, A11yList.with("::", ME));
    }

    @Test public void withoutKeepsXiaomiServices() {
        String list = VOICE + ":" + ME + ":" + KARAOKE;
        assertEquals(VOICE_LONG + ":" + KARAOKE, A11yList.without(list, ME_SHORT));
        // Removing what is not there is a no-op, not a wipe.
        assertEquals(VOICE_LONG + ":" + KARAOKE, A11yList.without(VOICE + ":" + KARAOKE, ME));
    }

    @Test public void removeThenAddRoundTrips() {
        String list = VOICE + ":" + KARAOKE + ":" + ME;
        String on = A11yList.with(A11yList.without(list, ME), ME);
        assertEquals(Arrays.asList(VOICE_LONG, KARAOKE, ME), A11yList.entries(on));
    }

    @Test public void duplicatesCollapse() {
        String list = ME + ":" + ME_SHORT + ":" + VOICE;
        assertEquals(Arrays.asList(ME, VOICE_LONG), A11yList.entries(list));
    }
}
