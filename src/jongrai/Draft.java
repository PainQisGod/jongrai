package jongrai;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The state of one draft: both teams plus the rules (no duplicates, pick/ban limits, pick order). */
public class Draft {

    public enum Side {
        BLUE, RED;

        /** "blue"/"b" or "red"/"r", any case. */
        public static Side parse(String text) {
            return switch (text == null ? "" : text.trim().toLowerCase()) {
                case "blue", "b" -> BLUE;
                case "red", "r" -> RED;
                default -> throw new IllegalArgumentException("Side must be blue or red.");
            };
        }

        public Side other() { return this == BLUE ? RED : BLUE; }
    }

    /** Ranked pick order by total picks made so far: B, RR, BB, RR, BB, R. */
    private static final Side[] PICK_ORDER = {
            Side.BLUE, Side.RED, Side.RED, Side.BLUE, Side.BLUE,
            Side.RED, Side.RED, Side.BLUE, Side.BLUE, Side.RED};

    private final Map<String, Hero> pool;
    private final Team blue = new Team("Blue");
    private final Team red = new Team("Red");
    private long version;

    public Draft(Map<String, Hero> pool) {
        this.pool = pool;
    }

    public Team team(Side side) { return side == Side.BLUE ? blue : red; }
    public Team opponent(Side side) { return side == Side.BLUE ? red : blue; }
    public Collection<Hero> allHeroes() { return pool.values(); }

    /** Bumped on every change, so clients can tell their copy of the board is stale. */
    public long version() { return version; }

    public boolean isComplete() { return blue.picksFull() && red.picksFull(); }

    /**
     * Whose pick it is, from the ranked order. Picks entered out of order are tolerated: if the side
     * the order names is already full, the other side picks. Empty once the draft is complete.
     */
    public Optional<Side> nextPick() {
        if (isComplete()) return Optional.empty();
        int made = blue.picks().size() + red.picks().size();
        Side side = PICK_ORDER[made];
        return Optional.of(team(side).picksFull() ? side.other() : side);
    }

    /** Clears both sides. The version keeps counting, so a client's pre-reset copy stays stale. */
    public void reset() {
        blue.clear();
        red.clear();
        version++;
    }

    public Hero find(String name) {
        Hero hero = pool.get(name.trim().toLowerCase());
        if (hero == null) throw new IllegalArgumentException("Unknown hero: " + name);
        return hero;
    }

    public boolean isAvailable(Hero hero) {
        return !blue.picks().contains(hero) && !blue.bans().contains(hero)
            && !red.picks().contains(hero) && !red.bans().contains(hero);
    }

    public List<Hero> availableHeroes() {
        return pool.values().stream().filter(this::isAvailable).toList();
    }

    public void pick(Side side, String heroName) {
        Hero hero = find(heroName);
        Team team = team(side);
        if (!isAvailable(hero)) throw new IllegalStateException(hero.name() + " is already picked or banned.");
        if (team.picksFull()) throw new IllegalStateException(team.name() + " already has " + Team.MAX_PICKS + " picks.");
        // Flex heroes: default to the first of their lanes that this team has not covered yet.
        var covered = team.coveredLanes();
        Lane lane = hero.lanes().stream().filter(l -> !covered.contains(l)).findFirst().orElse(hero.lane());
        team.addPick(hero, lane);
        version++;
    }

    public void remove(Side side, String heroName) {
        Hero hero = find(heroName);
        if (!team(side).remove(hero)) {
            throw new IllegalStateException(hero.name() + " is not picked or banned by " + team(side).name() + ".");
        }
        version++;
    }

    public void setLane(Side side, String heroName, Lane lane) {
        Hero hero = find(heroName);
        Team team = team(side);
        if (!team.picks().contains(hero)) throw new IllegalStateException(hero.name() + " is not picked by " + team.name() + ".");
        team.assignLane(hero, lane);
        version++;
    }

    public void ban(Side side, String heroName) {
        Hero hero = find(heroName);
        Team team = team(side);
        if (!isAvailable(hero)) throw new IllegalStateException(hero.name() + " is already picked or banned.");
        if (team.bansFull()) throw new IllegalStateException(team.name() + " already has " + Team.MAX_BANS + " bans.");
        team.addBan(hero);
        version++;
    }
}
