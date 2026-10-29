package com.kuronami.jmwaypointstoxaero.jm;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointMapperTest {

    @Test
    void initialsUseTheFirstCodePoint() {
        assertEquals("H", WaypointMapper.initials("Home"));
        assertEquals("拠", WaypointMapper.initials("拠点"));
        assertEquals("🍺", WaypointMapper.initials("🍺 Bar"));
        assertEquals("\uD867\uDE3D", WaypointMapper.initials("\uD867\uDE3D定食"));
        assertEquals("?", WaypointMapper.initials(""));
        assertEquals("?", WaypointMapper.initials(null));
    }

    @Test
    void initialsFallBackForUnsafeFirstCodePoints() {
        assertEquals("?", WaypointMapper.initials(":Home"));
        assertEquals("?", WaypointMapper.initials("\nHome"));
        assertEquals("?", WaypointMapper.initials("\tHome"));
        assertEquals("?", WaypointMapper.initials("\u0001Home"));
        assertEquals("?", WaypointMapper.initials(" Home"));
        assertEquals("?", WaypointMapper.initials("\u00A0Home"));
    }

    @Test
    void groupNameFallsBackToTheDefaultSet() {
        Map<String, String> groups = Map.of("g1", "Mining");
        assertEquals("Mining", WaypointMapper.groupName("g1", groups));
        assertEquals("JourneyMap", WaypointMapper.groupName("missing", groups));
        assertEquals("JourneyMap", WaypointMapper.groupName(null, groups));
        assertEquals("JourneyMap", WaypointMapper.groupName("journeymap_default", groups));
        assertEquals("JourneyMap", WaypointMapper.groupName("g2", Map.of("g2", " ")));
    }

    @Test
    void groupNameKeepsSetNamesSafeForXaeroFiles() {
        Map<String, String> groups = Map.of(
                "colon", "Mining:Ores",
                "fullwidth", "Mining：Ores",
                "lines", "Line\r\nBreak\tEnd",
                "control", "\u0001Mining\u0002",
                "empty", "\u0001\u0002",
                "spaces", "  Mining  ");

        assertEquals("Mining：Ores", WaypointMapper.groupName("colon", groups));
        assertEquals("Mining：Ores", WaypointMapper.groupName("fullwidth", groups));
        assertEquals("Line  Break End", WaypointMapper.groupName("lines", groups));
        assertEquals("Mining", WaypointMapper.groupName("control", groups));
        assertEquals("JourneyMap", WaypointMapper.groupName("empty", groups));
        assertEquals("Mining", WaypointMapper.groupName("spaces", groups));
    }

    @Test
    void dimensionUsesPrimaryThenFirstDimension() {
        JmWaypoint waypoint = new JmWaypoint();
        waypoint.pos = new JmPos();
        waypoint.pos.primaryDimension = "minecraft:overworld";
        waypoint.dimensions = List.of("minecraft:the_nether");
        assertEquals("minecraft:overworld", WaypointMapper.dimension(waypoint));

        waypoint.pos.primaryDimension = " ";
        assertEquals("minecraft:the_nether", WaypointMapper.dimension(waypoint));

        waypoint.pos.primaryDimension = null;
        waypoint.dimensions = List.of();
        assertNull(WaypointMapper.dimension(waypoint));

        waypoint.dimensions = null;
        assertNull(WaypointMapper.dimension(waypoint));
    }

    @Test
    void settingsMapToDisabledAndVisibility() {
        JmSettings settings = new JmSettings();
        settings.enable = false;
        settings.showInWorld = false;
        assertTrue(WaypointMapper.disabled(settings));
        assertFalse(WaypointMapper.showInWorld(settings));

        settings.enable = true;
        settings.showInWorld = true;
        assertFalse(WaypointMapper.disabled(settings));
        assertTrue(WaypointMapper.showInWorld(settings));

        assertFalse(WaypointMapper.disabled(null));
        assertTrue(WaypointMapper.showInWorld(null));
    }

    @Test
    void nearestIndexPicksTheClosestRgbValue() {
        int[] palette = { 0x000000, 0xFFFFFF, 0xFF0000 };
        assertEquals(2, WaypointMapper.nearestIndex(0xEE1010, palette));
        assertEquals(1, WaypointMapper.nearestIndex(0xF0F0F0, palette));
        assertEquals(0, WaypointMapper.nearestIndex(0x050505, palette));
        assertEquals(-1, WaypointMapper.nearestIndex(0xFFFFFF, new int[0]));
        assertEquals(-1, WaypointMapper.nearestIndex(0xFFFFFF, null));
    }

    @Test
    void mapBuildsEveryXaeroField() {
        JmWaypoint waypoint = new JmWaypoint();
        waypoint.groupId = "g1";
        waypoint.name = "Base";
        waypoint.color = 0xFF0000;
        waypoint.pos = new JmPos();
        waypoint.pos.x = 10;
        waypoint.pos.y = 64;
        waypoint.pos.z = -5;
        waypoint.pos.primaryDimension = "minecraft:overworld";
        waypoint.settings = new JmSettings();
        waypoint.settings.enable = false;
        waypoint.settings.showInWorld = false;

        WaypointMapper.MappedWaypoint mapped = WaypointMapper.map(waypoint, Map.of("g1", "Mining"));

        assertNotNull(mapped);
        assertEquals("Mining", mapped.setName());
        assertEquals("minecraft:overworld", mapped.dimension());
        assertEquals(10, mapped.x());
        assertEquals(64, mapped.y());
        assertEquals(-5, mapped.z());
        assertEquals("Base", mapped.name());
        assertEquals("B", mapped.initials());
        assertEquals(0xFF0000, mapped.rgb());
        assertTrue(mapped.disabled());
        assertFalse(mapped.showInWorld());
    }

    @Test
    void mapSanitizesTheSetName() {
        JmWaypoint waypoint = new JmWaypoint();
        waypoint.groupId = "g1";
        waypoint.pos = new JmPos();
        waypoint.pos.x = 0;
        waypoint.pos.y = 0;
        waypoint.pos.z = 0;
        waypoint.pos.primaryDimension = "minecraft:overworld";

        WaypointMapper.MappedWaypoint mapped = WaypointMapper.map(waypoint, Map.of("g1", "A:B\nC"));

        assertNotNull(mapped);
        assertEquals("A：B C", mapped.setName());
    }

    @Test
    void mapKeepsColonInWaypointName() {
        WaypointMapper.MappedWaypoint mapped = WaypointMapper.map(validWaypoint("A:B"), Map.of());

        assertNotNull(mapped);
        assertEquals("A:B", mapped.name());
        assertEquals("A", mapped.initials());
    }

    @Test
    void mapSanitizesWaypointNameControlChars() {
        assertEquals("Line Break", mappedName("Line\nBreak"));
        assertEquals("Line  Break End", mappedName("Line\r\nBreak\tEnd"));
        assertEquals("Tab Name", mappedName("Tab\tName"));
        assertEquals("Home", mappedName("\tHome"));
        assertEquals("H", mappedInitials("\tHome"));
        assertEquals("Name", mappedName("\u000BName"));
        assertEquals("Name", mappedName("\u007FName"));
        assertEquals("Name", mappedName("\u0085Name"));
        assertEquals("", mappedName("\u0001"));
        assertEquals("?", mappedInitials("\u0001"));
    }

    @Test
    void mapRejectsMissingPositionOrDimension() {
        JmWaypoint noPos = new JmWaypoint();
        assertNull(WaypointMapper.map(noPos, Map.of()));

        JmWaypoint noDim = new JmWaypoint();
        noDim.pos = new JmPos();
        noDim.pos.x = 0;
        noDim.pos.y = 0;
        noDim.pos.z = 0;
        assertNull(WaypointMapper.map(noDim, Map.of()));
    }

    @Test
    void mapDefaultsColourAndName() {
        JmWaypoint waypoint = new JmWaypoint();
        waypoint.pos = new JmPos();
        waypoint.pos.x = 1;
        waypoint.pos.y = 2;
        waypoint.pos.z = 3;
        waypoint.pos.primaryDimension = "minecraft:overworld";

        WaypointMapper.MappedWaypoint mapped = WaypointMapper.map(waypoint, Map.of());

        assertNotNull(mapped);
        assertEquals("JourneyMap", mapped.setName());
        assertEquals("", mapped.name());
        assertEquals("?", mapped.initials());
        assertEquals(0xFFFFFF, mapped.rgb());
        assertFalse(mapped.disabled());
        assertTrue(mapped.showInWorld());
    }

    private static JmWaypoint validWaypoint(String name) {
        JmWaypoint waypoint = new JmWaypoint();
        waypoint.name = name;
        waypoint.pos = new JmPos();
        waypoint.pos.x = 0;
        waypoint.pos.y = 64;
        waypoint.pos.z = 0;
        waypoint.pos.primaryDimension = "minecraft:overworld";
        return waypoint;
    }

    private static String mappedName(String rawName) {
        return WaypointMapper.map(validWaypoint(rawName), Map.of()).name();
    }

    private static String mappedInitials(String rawName) {
        return WaypointMapper.map(validWaypoint(rawName), Map.of()).initials();
    }
}
