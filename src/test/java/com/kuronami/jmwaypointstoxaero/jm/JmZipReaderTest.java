package com.kuronami.jmwaypointstoxaero.jm;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmZipReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsGroupsAndWaypoints() throws Exception {
        Path zip = zip("export.zip", Map.of(
                "groups/g1.json", "{\"version\":\"1\",\"guid\":\"g1\",\"name\":\"Mining\"}",
                "waypoints/w1.json",
                "{\"version\":\"1\",\"guid\":\"w1\",\"groupId\":\"g1\",\"name\":\"Base\","
                        + "\"pos\":{\"x\":1,\"y\":64,\"z\":-2,\"primaryDimension\":\"minecraft:overworld\"},"
                        + "\"color\":1634560,\"settings\":{\"enable\":true,\"showInWorld\":true},"
                        + "\"dimensions\":[\"minecraft:overworld\"]}"));

        JmZipReader.Result result = JmZipReader.read(zip);

        assertEquals(1, result.waypoints().size());
        assertEquals(0, result.malformed());
        assertEquals("Mining", result.groupNames().get("g1"));
        JmWaypoint waypoint = result.waypoints().get(0);
        assertEquals("Base", waypoint.name);
        assertEquals("g1", waypoint.groupId);
        assertEquals(1, waypoint.pos.x);
        assertEquals(64, waypoint.pos.y);
        assertEquals(-2, waypoint.pos.z);
        assertEquals("minecraft:overworld", waypoint.pos.primaryDimension);
    }

    @Test
    void missingFieldsStayNull() throws Exception {
        Path zip = zip("bare.zip", Map.of(
                "waypoints/w1.json", "{\"name\":\"bare\"}"));

        JmZipReader.Result result = JmZipReader.read(zip);

        assertEquals(1, result.waypoints().size());
        JmWaypoint waypoint = result.waypoints().get(0);
        assertEquals("bare", waypoint.name);
        assertNull(waypoint.pos);
        assertNull(waypoint.groupId);
        assertNull(WaypointMapper.map(waypoint, Map.of()));
    }

    @Test
    void brokenJsonIsCountedAndSkipped() throws Exception {
        Path zip = zip("broken.zip", Map.of(
                "groups/g1.json", "{\"guid\":\"g1\",\"name\":\"Mining\"}",
                "waypoints/good.json",
                "{\"groupId\":\"g1\",\"name\":\"Good\",\"pos\":{\"x\":0,\"y\":0,\"z\":0,"
                        + "\"primaryDimension\":\"minecraft:overworld\"}}",
                "waypoints/bad.json", "{\"name\":"));

        JmZipReader.Result result = JmZipReader.read(zip);

        assertEquals(1, result.waypoints().size());
        assertEquals(1, result.malformed());
        assertEquals("Good", result.waypoints().get(0).name);
    }

    @Test
    void zipWithoutWaypointsIsRejected() throws Exception {
        Path zip = zip("empty.zip", Map.of(
                "groups/g1.json", "{\"guid\":\"g1\",\"name\":\"Mining\"}"));

        assertThrows(IOException.class, () -> JmZipReader.read(zip));
    }

    @Test
    void zipWithOnlyBrokenWaypointsIsRejected() throws Exception {
        Path zip = zip("all-broken.zip", Map.of(
                "waypoints/bad.json", "{\"name\":"));

        assertThrows(IOException.class, () -> JmZipReader.read(zip));
    }

    @Test
    void groupWithoutNameIsKeptWithANullValue() throws Exception {
        Path zip = zip("unnamed-group.zip", Map.of(
                "groups/g1.json", "{\"guid\":\"g1\"}",
                "waypoints/w1.json",
                "{\"groupId\":\"g1\",\"pos\":{\"x\":0,\"y\":0,\"z\":0,"
                        + "\"primaryDimension\":\"minecraft:overworld\"}}"));

        JmZipReader.Result result = JmZipReader.read(zip);

        assertEquals(1, result.waypoints().size());
        assertTrue(result.groupNames().containsKey("g1"));
        assertNull(result.groupNames().get("g1"));
        assertEquals("JourneyMap", WaypointMapper.groupName("g1", result.groupNames()));
    }

    private Path zip(String name, Map<String, String> entries) throws IOException {
        Path path = tempDir.resolve(name);
        try (OutputStream out = Files.newOutputStream(path);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return path;
    }
}
