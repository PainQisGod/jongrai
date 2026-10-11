package jongrai;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One hero.
 * "roles" and "lanes" hold 1 or 2 entries; the first one is the primary one.
 * "tier" is S/A/B/C/D, "winRate" is the overall win rate in % (50.0 if unknown),
 * "spike" is EARLY, MID or LATE (when the hero is strongest).
 * "banRate" is the ban rate in % (0 means under 15% or unknown).
 * "counters" = heroes this hero is good against.
 * "synergies" = heroes this hero works well with.
 * Names inside the counter/synergy sets are stored in lowercase.
 */
public record Hero(String name, List<Role> roles, List<Lane> lanes,
                   String tier, double winRate, double banRate,
                   Set<String> counters, Set<String> synergies,
                   int durability, int offense, int control, Set<String> tags,
                   String spike, String tip) {

    public String key() {
        return name.toLowerCase();
    }

    public Role role() {
        return roles.get(0);
    }

    public Lane lane() {
        return lanes.get(0);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean hasLane(Lane lane) {
        return lanes.contains(lane);
    }

    /** True if the hero has this speciality tag (e.g. "burst", "push"). */
    public boolean hasTag(String tag) {
        return tags.contains(tag.toLowerCase());
    }

    public boolean counters(Hero other) {
        return counters.contains(other.key());
    }

    public boolean synergizesWith(Hero other) {
        return synergies.contains(other.key());
    }

    /** A hand-written tip for the hero page, or null when none was supplied. */
    public String tip() {
        return tip;
    }

    @Override
    public String toString() {
        return name + " (" + join(roles) + " | " + join(lanes) + ")";
    }

    private static String join(List<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).collect(Collectors.joining("/"));
    }
}
