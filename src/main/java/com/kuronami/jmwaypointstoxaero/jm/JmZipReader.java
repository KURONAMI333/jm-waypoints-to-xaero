package com.kuronami.jmwaypointstoxaero.jm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.Gson;

/**
 * Reads a JourneyMap Export ZIP. The layout is one JSON document per entry:
 * {@code groups/<guid>.json} and {@code waypoints/<guid>.json}.
 *
 * <p>No JourneyMap classes are used. A malformed document is counted and
 * skipped; a ZIP with no waypoint entry at all is rejected.</p>
 */
public final class JmZipReader {

    private static final Gson GSON = new Gson();
    private static final String GROUPS_PREFIX = "groups/";
    private static final String WAYPOINTS_PREFIX = "waypoints/";

    private JmZipReader() {
    }

    /** Parsed contents of one export. */
    public record Result(List<JmWaypoint> waypoints, Map<String, String> groupNames, int malformed) {
        public Result {
            waypoints = List.copyOf(waypoints);
            // A JourneyMap group may omit "name"; Map.copyOf rejects null values.
            groupNames = Collections.unmodifiableMap(new LinkedHashMap<>(groupNames));
        }
    }

    public static Result read(Path zipPath) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            Map<String, String> groupNames = readGroups(zip);
            List<JmWaypoint> waypoints = new ArrayList<>();
            int waypointEntries = 0;
            int malformed = 0;

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!isJsonFile(entry, WAYPOINTS_PREFIX)) {
                    continue;
                }
                waypointEntries++;
                try (InputStream in = zip.getInputStream(entry);
                        Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JmWaypoint waypoint = GSON.fromJson(reader, JmWaypoint.class);
                    if (waypoint == null) {
                        malformed++;
                    } else {
                        waypoints.add(waypoint);
                    }
                } catch (RuntimeException e) {
                    malformed++;
                }
            }

            if (waypointEntries == 0) {
                throw new IOException("No waypoint entries in " + zipPath.getFileName());
            }
            if (waypoints.isEmpty() && malformed > 0) {
                throw new IOException("No readable waypoint entries in " + zipPath.getFileName());
            }
            return new Result(waypoints, groupNames, malformed);
        }
    }

    private static Map<String, String> readGroups(ZipFile zip) throws IOException {
        Map<String, String> groupNames = new LinkedHashMap<>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!isJsonFile(entry, GROUPS_PREFIX)) {
                continue;
            }
            try (InputStream in = zip.getInputStream(entry);
                    Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JmGroup group = GSON.fromJson(reader, JmGroup.class);
                if (group == null) {
                    continue;
                }
                String guid = group.guid;
                if (guid == null || guid.isBlank()) {
                    guid = stripJsonSuffix(entry.getName().substring(GROUPS_PREFIX.length()));
                }
                groupNames.put(guid, group.name);
            } catch (RuntimeException e) {
                // A broken group document only means its name is unknown.
            }
        }
        return groupNames;
    }

    private static boolean isJsonFile(ZipEntry entry, String prefix) {
        return !entry.isDirectory()
                && entry.getName().startsWith(prefix)
                && entry.getName().endsWith(".json");
    }

    private static String stripJsonSuffix(String name) {
        return name.substring(0, name.length() - ".json".length());
    }
}
