package jongrai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** One side's picks, bans and lane assignments. Only {@link Draft} changes it, so its rules hold. */
public class Team {
    public static final int MAX_PICKS = 5;
    public static final int MAX_BANS = 5;

    private final String name;
    private final List<Hero> picks = new ArrayList<>();
    private final List<Hero> bans = new ArrayList<>();
    private final Map<String, Lane> assignedLanes = new HashMap<>(); // lane each pick will play

    public Team(String name) {
        this.name = name;
    }

    public String name() { return name; }
    public List<Hero> picks() { return Collections.unmodifiableList(picks); }
    public List<Hero> bans() { return Collections.unmodifiableList(bans); }
    public boolean picksFull() { return picks.size() >= MAX_PICKS; }
    public boolean bansFull() { return bans.size() >= MAX_BANS; }

    void addPick(Hero hero, Lane lane) {
        picks.add(hero);
        assignedLanes.put(hero.key(), lane);
    }

    void clear() {
        picks.clear();
        bans.clear();
        assignedLanes.clear();
    }

    void addBan(Hero hero) {
        bans.add(hero);
    }

    /** Lanes already covered by this team's picks (each hero counts for its assigned lane). */
    public Set<Lane> coveredLanes() {
        return picks.stream().map(this::laneOf).collect(Collectors.toSet());
    }

    /** The lane this pick is assigned to (defaults to the hero's main lane). */
    public Lane laneOf(Hero hero) {
        return assignedLanes.getOrDefault(hero.key(), hero.lane());
    }

    /** Removes a hero from this team's picks or bans (undo a misclick). */
    boolean remove(Hero hero) {
        assignedLanes.remove(hero.key());
        return picks.remove(hero) || bans.remove(hero);
    }

    void assignLane(Hero hero, Lane lane) {
        assignedLanes.put(hero.key(), lane);
    }

    public long countRole(Role role) {
        return picks.stream().filter(h -> h.hasRole(role)).count();
    }
}
