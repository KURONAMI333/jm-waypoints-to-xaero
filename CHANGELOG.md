# Changelog

## 0.1.0

Initial release. Client-side NeoForge 1.21.1.

- Imports a JourneyMap waypoint export ZIP into Xaero's Minimap.
- Import with the **Import JourneyMap waypoints** key binding (unbound by default) or `/jmwaypoints import`.
- Reads the newest ZIP in `config/kuronami-waypoint-bridge/` and moves processed ZIPs to `imported/`.
- JourneyMap group names become Xaero waypoint sets; colours are rounded to Xaero's palette and icons become initials.
- Existing waypoints and sets are never replaced. Duplicates with the same set, name, and coordinates are skipped.
- A `:` in a group name becomes `：` in the set name, because Xaero uses `:` as a field separator.
- Line breaks, tabs, and other ISO control characters in a waypoint name become spaces; a `:` in a waypoint name is kept.
- One direction only, JourneyMap to Xaero. Not a live sync.
