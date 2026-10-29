package com.kuronami.jmwaypointstoxaero.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.kuronami.jmwaypointstoxaero.JmWaypointsToXaero;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client wiring: one key binding, the {@code /jmwaypoints import} command, and
 * the development autotest. Loaded on the client only.
 */
@Mod(value = JmWaypointsToXaero.MODID, dist = Dist.CLIENT)
public final class JmWaypointsToXaeroClient {

    private static final String KEY_CATEGORY = "key.categories.jmwaypoints_to_xaero";
    private static final String KEY_IMPORT = "key.jmwaypoints_to_xaero.import";
    private static final String AUTOTEST_PROPERTY = "jmwaypoints.autotest";
    private static final int AUTOTEST_DELAY_TICKS = 40;

    private static KeyMapping importKey;
    private static int ticksInWorld;
    private static boolean autotestDone;

    public JmWaypointsToXaeroClient(IEventBus modEventBus) {
        modEventBus.addListener(JmWaypointsToXaeroClient::registerKeys);
        NeoForge.EVENT_BUS.addListener(JmWaypointsToXaeroClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(JmWaypointsToXaeroClient::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(JmWaypointsToXaeroClient::onLoggingOut);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        importKey = new KeyMapping(
                KEY_IMPORT,
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY);
        event.register(importKey);
    }

    private static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("jmwaypoints")
                .then(Commands.literal("import")
                        .executes(context -> {
                            ImportService.importLatest(true);
                            return 1;
                        })));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            ticksInWorld = 0;
            return;
        }

        while (importKey != null && importKey.consumeClick()) {
            ImportService.importLatest(true);
        }

        if (!autotestDone && Boolean.getBoolean(AUTOTEST_PROPERTY)) {
            ticksInWorld++;
            if (ticksInWorld >= AUTOTEST_DELAY_TICKS) {
                autotestDone = true;
                ImportResult result = ImportService.importLatest(true);
                JmWaypointsToXaero.LOGGER.info(
                        "AUTOTEST_RESULT imported={} skipped={} sets={} error={}",
                        result.imported(), result.skipped(), result.sets(), result.error());
            }
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ticksInWorld = 0;
    }
}
