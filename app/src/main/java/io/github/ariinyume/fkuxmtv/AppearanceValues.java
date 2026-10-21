package io.github.ariinyume.fkuxmtv;

/** Shared validation for old/corrupt preferences, independent of Android. */
final class AppearanceValues {
    static int defaultValue(String key) { return "size".equals(key) || "dim".equals(key) ? 1 : 0; }
    static int normalize(String key, int value) {
        int count;
        switch (key) {
            case "layout": case "focus": count = 2; break;
            case "background": case "apps": case "controls": case "font": case "size":
            case "material": case "dim": count = 3; break;
            default: return 0;
        }
        return value >= 0 && value < count ? value : defaultValue(key);
    }
}
