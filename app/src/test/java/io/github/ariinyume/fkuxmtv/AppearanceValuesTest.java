package io.github.ariinyume.fkuxmtv;
import org.junit.Test;
import static org.junit.Assert.*;
public class AppearanceValuesTest {
    @Test public void oldInstallKeepsPhotoListTextLightFont() {
        for(String key:new String[]{"background","layout","apps","controls","font","focus"})
            assertEquals(0,AppearanceValues.defaultValue(key));
        assertEquals(1,AppearanceValues.defaultValue("size"));
        assertEquals(1,AppearanceValues.defaultValue("dim"));
    }
    @Test public void corruptOrFutureValuesFallBackWithoutChangingOtherKeys() {
        assertEquals(0,AppearanceValues.normalize("layout",9));
        assertEquals(1,AppearanceValues.normalize("size",-1));
        assertEquals(1,AppearanceValues.normalize("dim",Integer.MAX_VALUE));
        assertEquals(2,AppearanceValues.normalize("font",2));
        assertEquals(0,AppearanceValues.normalize("unknown",8));
    }
}
