package com.kuronami.jmwaypointstoxaero.jm;

import java.util.List;
import java.util.Map;

/**
 * JourneyMap waypoint JSON to Xaero waypoint fields. Pure functions only:
 * no Minecraft or Xaero classes are touched here.
 */
public final class WaypointMapper {

    /** Set name used when JourneyMap did not name a group. */
    public static final String DEFAULT_SET = "JourneyMap";

    /** Fallback colour (white) when the ZIP carries no colour. */
    private static final int DEFAULT_RGB = 0xFFFFFF;

    private WaypointMapper() {
    }

    /** Everything Xaero needs for one waypoint, already resolved and neutral. */
    public record MappedWaypoint(
            String setName,
            String dimension,
            int x,
            int y,
            int z,
            String name,
            String initials,
            int rgb,
            boolean disabled,
            boolean showInWorld) {
    }

    /**
     * Maps one parsed waypoint, or returns {@code null} when a coordinate or a
     * dimension is missing.
     */
    public static MappedWaypoint map(JmWaypoint waypoint, Map<String, String> groupNames) {
        if (waypoint == null || waypoint.pos == null) {
            return null;
        }
        if (waypoint.pos.x == null || waypoint.pos.y == null || waypoint.pos.z == null) {
            return null;
        }
        String dimension = dimension(waypoint);
        if (dimension == null) {
            return null;
        }
        String name = sanitizeWaypointName(waypoint.name == null ? "" : waypoint.name);
        int rgb = waypoint.color == null ? DEFAULT_RGB : waypoint.color & 0xFFFFFF;
        return new MappedWaypoint(
                groupName(waypoint.groupId, groupNames),
                dimension,
                waypoint.pos.x,
                waypoint.pos.y,
                waypoint.pos.z,
                name,
                initials(name),
                rgb,
                disabled(waypoint.settings),
                showInWorld(waypoint.settings));
    }

    /**
     * {@code pos.primaryDimension}, then {@code dimensions[0]}, then nothing.
     * The second and later dimensions are dropped.
     */
    public static String dimension(JmWaypoint waypoint) {
        if (waypoint == null) {
            return null;
        }
        if (waypoint.pos != null && notBlank(waypoint.pos.primaryDimension)) {
            return waypoint.pos.primaryDimension;
        }
        List<String> dimensions = waypoint.dimensions;
        if (dimensions != null && !dimensions.isEmpty() && notBlank(dimensions.get(0))) {
            return dimensions.get(0);
        }
        return null;
    }

    /**
     * JourneyMap group GUID to set name. The default group, unknown groups, and
     * missing names all become {@link #DEFAULT_SET}.
     *
     * <p>Xaero's waypoint file uses {@code :} as a field separator and does not
     * escape set names, so a colon becomes the fullwidth {@code ：} and control
     * characters (line breaks, tabs, and the rest) become one space. A name that
     * is empty after that falls back to {@link #DEFAULT_SET}.</p>
     */
    public static String groupName(String groupId, Map<String, String> groupNames) {
        if (!notBlank(groupId) || "journeymap_default".equals(groupId)) {
            return DEFAULT_SET;
        }
        String name = groupNames == null ? null : groupNames.get(groupId);
        if (!notBlank(name)) {
            return DEFAULT_SET;
        }
        String sanitized = sanitizeSetName(name);
        return sanitized.isEmpty() ? DEFAULT_SET : sanitized;
    }

    /**
     * First code point of the name, or {@code ?} when there is none or when the
     * first code point would be unsafe in Xaero's waypoint file (colon, control
     * character, or whitespace).
     */
    public static String initials(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        int first = name.codePointAt(0);
        if (first == ':' || Character.isISOControl(first)
                || Character.isWhitespace(first) || Character.isSpaceChar(first)) {
            return "?";
        }
        return new String(Character.toChars(first));
    }

    /** {@code settings.enable == false} becomes a disabled waypoint. */
    public static boolean disabled(JmSettings settings) {
        return settings != null && Boolean.FALSE.equals(settings.enable);
    }

    /** {@code settings.showInWorld == false} hides the waypoint outside the map. */
    public static boolean showInWorld(JmSettings settings) {
        return settings == null || !Boolean.FALSE.equals(settings.showInWorld);
    }

    /**
     * Index of the palette entry closest to {@code rgb} in squared RGB distance.
     * Returns {@code -1} for an empty palette. Ties keep the lower index.
     */
    public static int nearestIndex(int rgb, int[] palette) {
        if (palette == null || palette.length == 0) {
            return -1;
        }
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int best = -1;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int candidate = palette[i] & 0xFFFFFF;
            int dr = r - ((candidate >> 16) & 0xFF);
            int dg = g - ((candidate >> 8) & 0xFF);
            int db = b - (candidate & 0xFF);
            long distance = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /**
     * Keeps the waypoint name inside one line of Xaero's waypoint file: every
     * control character becomes one space and surrounding whitespace is
     * trimmed. A colon is kept as is because Xaero escapes it itself
     * ({@code getNameSafe("§§")}), unlike the set name.
     */
    private static String sanitizeWaypointName(String name) {
        StringBuilder sanitized = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); ) {
            int codePoint = name.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint)) {
                sanitized.append(' ');
            } else {
                sanitized.appendCodePoint(codePoint);
            }
        }
        return sanitized.toString().trim();
    }

    /**
     * Keeps the set name inside one line of Xaero's waypoint file: the fullwidth
     * colon has no separator meaning, and every control character becomes a
     * space so a line break cannot split the entry.
     */
    private static String sanitizeSetName(String name) {
        StringBuilder sanitized = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); ) {
            int codePoint = name.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == ':') {
                sanitized.append('：');
            } else if (Character.isISOControl(codePoint)) {
                sanitized.append(' ');
            } else {
                sanitized.appendCodePoint(codePoint);
            }
        }
        return sanitized.toString().trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
