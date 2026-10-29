# JourneyMap Waypoints to Xaero

Client-side NeoForge 1.21.1 mod. Reads a JourneyMap waypoint export ZIP and adds
the waypoints to Xaero's Minimap. One direction only: JourneyMap to Xaero.

## Use

1. In JourneyMap, export your waypoints as a ZIP.
2. Put the ZIP in `config/kuronami-waypoint-bridge/`.
3. Assign **Import JourneyMap waypoints** in Controls (unbound by default) and
   press it, or run `/jmwaypoints import`.

The newest ZIP is imported. Group names become waypoint sets; colours are
rounded to Xaero's palette and icons become initials. Processed ZIPs move to
`config/kuronami-waypoint-bridge/imported/`.

## Notes

- Client only. No server code, no packets.
- Xaero's Minimap is optional at load time; without it the import reports a
  missing dependency and nothing else changes.
- Existing waypoints and sets are never replaced. Duplicates with the same set,
  name, and coordinates are skipped.
- A `:` in a JourneyMap group name becomes `：` in the set name, because Xaero
  uses `:` as a field separator in its waypoint files.
- Line breaks, tabs, and other ISO control characters in a waypoint name become
  spaces. A `:` in a waypoint name is kept.
- If two group names differ only by `:` vs `：`, they become one set.

## Development

Build and test with the Gradle wrapper:

```
./gradlew build
./gradlew test
```

`JMWAYPOINTS_AUTOTEST=true` makes a dev client run one import 40 ticks after
joining a world and log `AUTOTEST_RESULT`.
