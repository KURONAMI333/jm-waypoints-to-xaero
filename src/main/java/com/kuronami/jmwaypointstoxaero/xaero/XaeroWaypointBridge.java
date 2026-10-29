package com.kuronami.jmwaypointstoxaero.xaero;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.kuronami.jmwaypointstoxaero.JmWaypointsToXaero;
import com.kuronami.jmwaypointstoxaero.jm.WaypointMapper;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Writes waypoints into Xaero's Minimap through reflection only.
 *
 * <p>Xaero's Minimap has no public Java API, so the chain below was fixed by
 * reading the shipped classes. Every step is behind {@code Throwable} handling:
 * without Xaero, or after an API change, the import stops and the game keeps
 * running.</p>
 *
 * <pre>
 * BuiltInHudModules.MINIMAP
 *   -> HudModule.getCurrentSession()
 *     -> MinimapSession.getWorldManager() / getWorldStateUpdater()
 *        / getWorldManagerIO() / getDimensionHelper()
 *   -> MinimapWorldManager.getCurrentWorld()
 *     -> MinimapSession.getWorldState().getAutoRootContainerPath()
 *   -> MinimapDimensionHelper.getDimensionDirectoryName(dimension)
 *   -> rootPath.resolve(directory).resolve(potentialWorldNode)
 *   -> MinimapWorldManager.getWorld(path)
 *   -> MinimapWorld.getWaypointSet(name) / addWaypointSet(WaypointSet)
 *   -> WaypointSet.add(Waypoint, false)
 *   -> MinimapWorldManagerIO.saveWorld(MinimapWorld)
 * </pre>
 *
 * <p>{@code MinimapWorld.addWaypointSet(String)} is never called: it replaces
 * an existing set of the same name. A set is created with
 * {@code WaypointSet$Builder} only when {@code getWaypointSet(name)} is null.</p>
 */
public final class XaeroWaypointBridge {

    public static final String ERROR_XAERO_MISSING = "xaero_missing";
    public static final String ERROR_NOT_IN_WORLD = "not_in_world";
    public static final String ERROR_FAILED = "failed";

    private static final AtomicBoolean WARNED = new AtomicBoolean(false);

    private static volatile Reflection reflection;

    private XaeroWaypointBridge() {
    }

    /** Counts from one bridge call. {@code error} is null on success. */
    public record Result(int imported, int skipped, int sets, String error) {
    }

    public static synchronized Result importAll(List<WaypointMapper.MappedWaypoint> waypoints) {
        Reflection r = reflection;
        if (r == null) {
            try {
                r = Reflection.resolve();
                reflection = r;
            } catch (Throwable t) {
                warnOnce("resolve", t);
                return new Result(0, 0, 0, ERROR_XAERO_MISSING);
            }
        }
        if (waypoints == null || waypoints.isEmpty()) {
            return new Result(0, 0, 0, null);
        }
        try {
            return r.importAll(waypoints);
        } catch (Throwable t) {
            warnOnce("import", t);
            return new Result(0, 0, 0, ERROR_FAILED);
        }
    }

    private static void warnOnce(String operation, Throwable cause) {
        if (WARNED.compareAndSet(false, true)) {
            JmWaypointsToXaero.LOGGER.warn(
                    "Xaero's Minimap waypoint import is unavailable at '{}': {}",
                    operation, cause.toString());
        }
    }

    private static final class Reflection {

        private Object minimapModule;
        private Method getCurrentSession;
        private Method getWorldManager;
        private Method getWorldManagerIO;
        private Method getWorldStateUpdater;
        private Method getWorldState;
        private Method getAutoRootContainerPath;
        private Method getDimensionHelper;
        private Method getCurrentWorld;
        private Method getWorld;
        private Method resolvePath;
        private Method getDimId;
        private Method getWaypointSet;
        private Method addWaypointSet;
        private Method builderBegin;
        private Method builderSetName;
        private Method builderBuild;
        private Method getWaypoints;
        private Method addWaypoint;
        private Method waypointGetX;
        private Method waypointGetY;
        private Method waypointGetZ;
        private Method waypointGetName;
        private Method setDisabled;
        private Method setVisibility;
        private Method getDimensionDirectoryName;
        private Method getDimensionKeyForDirectoryName;
        private Method getPotentialWorldNode;
        private Method saveWorld;
        private Constructor<?> waypointCtor;
        private Object normalPurpose;
        private Object localVisibility;
        private Object worldMapLocalVisibility;
        private Object whiteColor;
        private Object[] colors;
        private int[] colorHexes;

        private Reflection() {
        }

        static Reflection resolve() throws Exception {
            Reflection r = new Reflection();

            Class<?> builtIn = Class.forName("xaero.hud.minimap.BuiltInHudModules");
            r.minimapModule = builtIn.getField("MINIMAP").get(null);

            Class<?> hudModule = Class.forName("xaero.hud.module.HudModule");
            r.getCurrentSession = hudModule.getMethod("getCurrentSession");

            Class<?> session = Class.forName("xaero.hud.minimap.module.MinimapSession");
            r.getWorldManager = session.getMethod("getWorldManager");
            r.getWorldManagerIO = session.getMethod("getWorldManagerIO");
            r.getWorldStateUpdater = session.getMethod("getWorldStateUpdater");
            r.getWorldState = session.getMethod("getWorldState");
            r.getDimensionHelper = session.getMethod("getDimensionHelper");

            Class<?> path = Class.forName("xaero.hud.path.XaeroPath");
            r.resolvePath = path.getMethod("resolve", String.class);

            Class<?> worldState = Class.forName("xaero.hud.minimap.world.state.MinimapWorldState");
            r.getAutoRootContainerPath = worldState.getMethod("getAutoRootContainerPath");

            Class<?> manager = Class.forName("xaero.hud.minimap.world.MinimapWorldManager");
            r.getCurrentWorld = manager.getMethod("getCurrentWorld");
            r.getWorld = manager.getMethod("getWorld", path);

            Class<?> world = Class.forName("xaero.hud.minimap.world.MinimapWorld");
            r.getDimId = world.getMethod("getDimId");

            Class<?> set = Class.forName("xaero.hud.minimap.waypoint.set.WaypointSet");
            r.getWaypointSet = world.getMethod("getWaypointSet", String.class);
            r.addWaypointSet = world.getMethod("addWaypointSet", set);
            r.getWaypoints = set.getMethod("getWaypoints");

            Class<?> builder = Class.forName("xaero.hud.minimap.waypoint.set.WaypointSet$Builder");
            r.builderBegin = builder.getMethod("begin");
            r.builderSetName = builder.getMethod("setName", String.class);
            r.builderBuild = builder.getMethod("build");

            Class<?> waypoint = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            r.addWaypoint = set.getMethod("add", waypoint, boolean.class);
            r.waypointGetX = waypoint.getMethod("getX");
            r.waypointGetY = waypoint.getMethod("getY");
            r.waypointGetZ = waypoint.getMethod("getZ");
            r.waypointGetName = waypoint.getMethod("getName");
            r.setDisabled = waypoint.getMethod("setDisabled", boolean.class);

            Class<?> visibility = Class.forName("xaero.common.minimap.waypoints.WaypointVisibilityType");
            r.setVisibility = waypoint.getMethod("setVisibility", visibility);
            r.localVisibility = visibility.getField("LOCAL").get(null);
            r.worldMapLocalVisibility = visibility.getField("WORLD_MAP_LOCAL").get(null);

            Class<?> color = Class.forName("xaero.hud.minimap.waypoint.WaypointColor");
            r.whiteColor = color.getField("WHITE").get(null);
            try {
                r.colors = (Object[]) color.getMethod("values").invoke(null);
                Method getHex = color.getMethod("getHex");
                r.colorHexes = new int[r.colors.length];
                for (int i = 0; i < r.colors.length; i++) {
                    r.colorHexes[i] = (int) getHex.invoke(r.colors[i]) & 0xFFFFFF;
                }
            } catch (Throwable t) {
                r.colors = null;
                r.colorHexes = null;
            }

            Class<?> purpose = Class.forName("xaero.hud.minimap.waypoint.WaypointPurpose");
            r.normalPurpose = purpose.getField("NORMAL").get(null);

            r.waypointCtor = waypoint.getConstructor(
                    int.class, int.class, int.class,
                    String.class, String.class,
                    color, purpose,
                    boolean.class, boolean.class);

            Class<?> dimensionHelper = Class.forName("xaero.hud.minimap.world.MinimapDimensionHelper");
            r.getDimensionDirectoryName = dimensionHelper.getMethod("getDimensionDirectoryName", ResourceKey.class);
            r.getDimensionKeyForDirectoryName = dimensionHelper.getMethod("getDimensionKeyForDirectoryName", String.class);

            Class<?> stateUpdater = Class.forName("xaero.hud.minimap.world.state.MinimapWorldStateUpdater");
            r.getPotentialWorldNode = stateUpdater.getMethod("getPotentialWorldNode", ResourceKey.class, boolean.class);

            Class<?> io = Class.forName("xaero.hud.minimap.world.io.MinimapWorldManagerIO");
            r.saveWorld = io.getMethod("saveWorld", world);

            return r;
        }

        Result importAll(List<WaypointMapper.MappedWaypoint> waypoints) throws Exception {
            Object session = getCurrentSession.invoke(minimapModule);
            if (session == null) {
                return new Result(0, 0, 0, ERROR_NOT_IN_WORLD);
            }
            Object manager = getWorldManager.invoke(session);
            Object io = getWorldManagerIO.invoke(session);
            Object updater = getWorldStateUpdater.invoke(session);
            Object worldState = getWorldState.invoke(session);
            Object dimensionHelper = getDimensionHelper.invoke(session);
            if (manager == null || io == null || updater == null || worldState == null
                    || dimensionHelper == null) {
                return new Result(0, 0, 0, ERROR_NOT_IN_WORLD);
            }

            Object currentWorld = getCurrentWorld.invoke(manager);
            Object rootPath = getAutoRootContainerPath.invoke(worldState);
            if (currentWorld == null || rootPath == null) {
                return new Result(0, 0, 0, ERROR_NOT_IN_WORLD);
            }

            Map<Object, Boolean> touchedWorlds = new LinkedHashMap<>();
            Set<String> touchedSets = new LinkedHashSet<>();
            // Existing (x, y, z, name) keys per set, read once per set instead of
            // rescanning every waypoint for every imported one.
            Map<Object, Set<WaypointKey>> existingKeys = new IdentityHashMap<>();
            int imported = 0;
            int skipped = 0;

            for (WaypointMapper.MappedWaypoint mapped : waypoints) {
                Object world;
                try {
                    world = resolveWorld(mapped.dimension(), manager, updater, dimensionHelper, rootPath);
                } catch (Exception e) {
                    // Dimension lookups can fail for a single unknown dimension key.
                    skipped++;
                    continue;
                }
                if (world == null) {
                    skipped++;
                    continue;
                }

                // Set and waypoint writes are outside the per-waypoint catch:
                // a failure here means the Xaero API drifted, so the whole
                // import must stop and the ZIP must stay in the queue.
                Object set = getWaypointSet.invoke(world, mapped.setName());
                if (set == null) {
                    Object builder = builderBegin.invoke(null);
                    builderSetName.invoke(builder, mapped.setName());
                    set = builderBuild.invoke(builder);
                    if (set == null) {
                        skipped++;
                        continue;
                    }
                    addWaypointSet.invoke(world, set);
                }

                Set<WaypointKey> keys = existingKeys.get(set);
                if (keys == null) {
                    keys = readKeys(set);
                    existingKeys.put(set, keys);
                }
                if (!keys.add(new WaypointKey(mapped.x(), mapped.y(), mapped.z(), mapped.name()))) {
                    skipped++;
                    continue;
                }

                Object waypoint = waypointCtor.newInstance(
                        mapped.x(), mapped.y(), mapped.z(),
                        mapped.name(), mapped.initials(),
                        colorFor(mapped.rgb()), normalPurpose,
                        false, true);
                if (mapped.disabled()) {
                    setDisabled.invoke(waypoint, true);
                }
                setVisibility.invoke(waypoint,
                        mapped.showInWorld() ? localVisibility : worldMapLocalVisibility);
                addWaypoint.invoke(set, waypoint, false);

                imported++;
                touchedWorlds.put(world, Boolean.TRUE);
                touchedSets.add(mapped.setName());
            }

            for (Object world : touchedWorlds.keySet()) {
                saveWorld.invoke(io, world);
            }
            return new Result(imported, skipped, touchedSets.size(), null);
        }

        /**
         * Resolves the {@code MinimapWorld} for one dimension, verifying the
         * dimension name round trip and the world's own dimension id before
         * any waypoint is written. Returns {@code null} when the dimension
         * cannot be mapped safely.
         *
         * <p>{@code getPotentialWorldNode} only names the world node; in
         * singleplayer it always returns {@code waypoints}. The target
         * dimension's container has to be built from the auto root path and
         * the dimension directory, otherwise every dimension resolves to the
         * current world.</p>
         */
        private Object resolveWorld(String dimensionId, Object manager, Object updater,
                Object dimensionHelper, Object rootPath) throws Exception {
            ResourceKey<Level> dimension = dimensionKey(dimensionId);
            if (dimension == null) {
                return null;
            }

            String directory = (String) getDimensionDirectoryName.invoke(dimensionHelper, dimension);
            ResourceKey<?> roundTrip = directory == null ? null
                    : (ResourceKey<?>) getDimensionKeyForDirectoryName.invoke(dimensionHelper, directory);
            if (roundTrip == null || !dimension.equals(roundTrip)) {
                return null;
            }

            String node = (String) getPotentialWorldNode.invoke(updater, dimension, false);
            if (node == null || node.isBlank()) {
                return null;
            }

            Object containerPath = resolvePath.invoke(rootPath, directory);
            Object fullPath = containerPath == null ? null : resolvePath.invoke(containerPath, node);
            Object world = fullPath == null ? null : getWorld.invoke(manager, fullPath);
            if (world == null) {
                return null;
            }

            Object worldDimension = getDimId.invoke(world);
            return dimension.equals(worldDimension) ? world : null;
        }

        /** Reads all existing keys of one set exactly once. */
        private Set<WaypointKey> readKeys(Object set) throws Exception {
            Set<WaypointKey> keys = new LinkedHashSet<>();
            for (Object waypoint : (Iterable<?>) getWaypoints.invoke(set)) {
                keys.add(new WaypointKey(
                        (int) waypointGetX.invoke(waypoint),
                        (int) waypointGetY.invoke(waypoint),
                        (int) waypointGetZ.invoke(waypoint),
                        (String) waypointGetName.invoke(waypoint)));
            }
            return keys;
        }

        /** Duplicate identity inside one set: same coordinates and same name. */
        private record WaypointKey(int x, int y, int z, String name) {
        }

        private Object colorFor(int rgb) {
            if (colors == null || colorHexes == null || colorHexes.length == 0) {
                return whiteColor;
            }
            int index = WaypointMapper.nearestIndex(rgb, colorHexes);
            return index < 0 ? whiteColor : colors[index];
        }

        private static ResourceKey<Level> dimensionKey(String dimensionId) {
            ResourceLocation location = ResourceLocation.tryParse(dimensionId);
            if (location == null) {
                return null;
            }
            return ResourceKey.create(Registries.DIMENSION, location);
        }
    }
}
