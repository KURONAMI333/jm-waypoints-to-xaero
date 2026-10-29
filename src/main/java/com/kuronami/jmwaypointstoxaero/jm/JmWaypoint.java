package com.kuronami.jmwaypointstoxaero.jm;

import java.util.List;

/** JourneyMap {@code waypoints/<guid>.json}. Every field is optional. */
public final class JmWaypoint {

    public String version;
    public String guid;
    public String groupId;
    public String modId;
    public String name;
    public JmPos pos;
    public Integer color;
    public JmSettings settings;
    public JmIcon icon;
    public List<String> dimensions;
}
