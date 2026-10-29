package com.kuronami.jmwaypointstoxaero.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.kuronami.jmwaypointstoxaero.JmWaypointsToXaero;
import com.kuronami.jmwaypointstoxaero.jm.JmWaypoint;
import com.kuronami.jmwaypointstoxaero.jm.JmZipReader;
import com.kuronami.jmwaypointstoxaero.jm.WaypointMapper;
import com.kuronami.jmwaypointstoxaero.xaero.XaeroWaypointBridge;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * One import pass: find the newest ZIP, map its waypoints, hand them to Xaero,
 * then move the ZIP out of the queue.
 *
 * <p>Runs on the client thread. Never touches the server and never opens a UI.</p>
 */
public final class ImportService {

    private static final String CONFIG_SUBDIR = "config/kuronami-waypoint-bridge";
    private static final String ERROR_NO_ZIP = "no_zip";
    private static final String ERROR_PARSE_FAILED = "parse_failed";

    private ImportService() {
    }

    public static synchronized ImportResult importLatest(boolean announce) {
        try {
            Path directory = bridgeDirectory();
            Files.createDirectories(directory);

            Path zip = ImportFolder.latestZip(directory);
            if (zip == null) {
                return finish(new ImportResult(0, 0, 0, ERROR_NO_ZIP), announce);
            }

            JmZipReader.Result parsed;
            try {
                parsed = JmZipReader.read(zip);
            } catch (IOException | RuntimeException e) {
                JmWaypointsToXaero.LOGGER.warn("Could not read {}", zip.getFileName(), e);
                return finish(new ImportResult(0, 0, 0, ERROR_PARSE_FAILED), announce);
            }

            Map<String, String> groupNames = parsed.groupNames();
            List<WaypointMapper.MappedWaypoint> mapped = new ArrayList<>();
            int skipped = parsed.malformed();
            for (JmWaypoint waypoint : parsed.waypoints()) {
                WaypointMapper.MappedWaypoint candidate = WaypointMapper.map(waypoint, groupNames);
                if (candidate == null) {
                    skipped++;
                } else {
                    mapped.add(candidate);
                }
            }

            XaeroWaypointBridge.Result bridge = XaeroWaypointBridge.importAll(mapped);
            int totalSkipped = skipped + bridge.skipped();
            ImportResult result = new ImportResult(bridge.imported(), totalSkipped, bridge.sets(), bridge.error());
            if (bridge.error() != null) {
                return finish(result, announce);
            }

            try {
                ImportFolder.moveToImported(directory, zip);
            } catch (IOException e) {
                JmWaypointsToXaero.LOGGER.warn("Could not move {} to imported/", zip.getFileName(), e);
            }
            return finish(result, announce);
        } catch (Throwable t) {
            JmWaypointsToXaero.LOGGER.warn("Waypoint import failed", t);
            return finish(new ImportResult(0, 0, 0, XaeroWaypointBridge.ERROR_FAILED), announce);
        }
    }

    private static Path bridgeDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(CONFIG_SUBDIR);
    }

    private static ImportResult finish(ImportResult result, boolean announce) {
        if (!announce) {
            return result;
        }
        Component message = message(result);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        } else {
            JmWaypointsToXaero.LOGGER.info(message.getString());
        }
        return result;
    }

    private static Component message(ImportResult result) {
        if (result.error() == null) {
            return Component.translatable(
                    "jmwaypoints_to_xaero.import.done",
                    result.imported(), result.sets(), result.skipped());
        }
        return Component.translatable("jmwaypoints_to_xaero.import.error." + result.error());
    }
}
