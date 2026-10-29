package com.kuronami.jmwaypointstoxaero;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;

/**
 * Client-only mod. There are no items, blocks, or server-side code;
 * {@link com.kuronami.jmwaypointstoxaero.client.JmWaypointsToXaeroClient} owns all behaviour.
 */
@Mod(JmWaypointsToXaero.MODID)
public final class JmWaypointsToXaero {

    public static final String MODID = "jm_waypoints_to_xaero";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JmWaypointsToXaero() {
    }
}
