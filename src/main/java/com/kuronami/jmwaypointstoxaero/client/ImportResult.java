package com.kuronami.jmwaypointstoxaero.client;

/**
 * Outcome of one import attempt.
 *
 * @param imported waypoints added to Xaero
 * @param skipped  waypoints that could not be mapped or already existed
 * @param sets     distinct waypoint sets that received at least one waypoint
 * @param error    translation key suffix, or {@code null} on success
 */
public record ImportResult(int imported, int skipped, int sets, String error) {
}
