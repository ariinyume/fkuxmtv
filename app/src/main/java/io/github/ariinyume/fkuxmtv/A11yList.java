package io.github.ariinyume.fkuxmtv;

import java.util.ArrayList;
import java.util.List;

/**
 * String handling for {@code Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES}.
 *
 * The value is a colon-separated list of flattened component names, and the firmware writes them
 * in the short form ({@code pkg/.Cls}) while {@code ComponentName.flattenToString()} produces the
 * long form ({@code pkg/pkg.Cls}). Comparing raw strings would therefore decide "not enabled"
 * about a service that is enabled, so everything goes through {@link #normalize(String)} first.
 *
 * Plain Java on purpose: this is the part that decides whether we rewrite the list, so it is unit
 * tested without a device.
 */
final class A11yList {
    private A11yList() { }

    /** Both forms collapse to {@code pkg/cls}; already-qualified names pass through unchanged. */
    static String normalize(String entry) {
        if (entry == null) return null;
        String value = entry.trim();
        if (value.isEmpty()) return null;
        int slash = value.indexOf('/');
        if (slash <= 0 || slash == value.length() - 1) return value;
        String pkg = value.substring(0, slash);
        String cls = value.substring(slash + 1);
        // ".Cls" (and the odd "..Cls") is package relative; anything else is already full.
        if (cls.startsWith(".")) {
            while (cls.startsWith(".")) cls = cls.substring(1);
            return pkg + "/" + pkg + "." + cls;
        }
        return value;
    }

    /** Parsed, normalized, de-duplicated entries in their original order. */
    static List<String> entries(String list) {
        List<String> out = new ArrayList<>();
        if (list == null) return out;
        for (String part : list.split(":")) {
            String normalized = normalize(part);
            if (normalized != null && !out.contains(normalized)) out.add(normalized);
        }
        return out;
    }

    static boolean has(String list, String service) {
        String target = normalize(service);
        return target != null && entries(list).contains(target);
    }

    /** Appends the service when missing. Existing entries keep their order, so other apps stay put. */
    static String with(String list, String service) {
        String target = normalize(service);
        if (target == null) return list;
        List<String> out = entries(list);
        if (!out.contains(target)) out.add(target);
        return join(out);
    }

    /** Drops the service, leaving everything else untouched. */
    static String without(String list, String service) {
        String target = normalize(service);
        if (target == null) return list;
        List<String> out = entries(list);
        out.remove(target);
        return join(out);
    }

    private static String join(List<String> entries) {
        StringBuilder out = new StringBuilder();
        for (String entry : entries) {
            if (out.length() > 0) out.append(':');
            out.append(entry);
        }
        return out.toString();
    }
}
