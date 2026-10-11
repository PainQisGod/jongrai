package jongrai;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** The state of one draft: both teams plus the rules (no duplicates, pick/ban limits). */
public class Draft {

    public enum Side { BLUE, RED }

    private final Map<String, Hero> pool;
    private final Team blue = new Team("Blue");
    private final Team red = new Team("Red");

    public Draft(Map<String, Hero> pool) {
        this.pool = pool;
    }

    public Team team(Side side) { return side == Side.BLUE ? blue : red; }
    public Team opponent(Side side) { return side == Side.BLUE ? red : blue; }
    public Collection<Hero> allHeroes() { return pool.values(); }

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
        if (team.picks().size() >= Team.MAX_PICKS) throw new IllegalStateException(team.name() + " already has 5 picks.");
        // Flex heroes: default to the first of their lanes that this team has not covered yet.
        var covered = team.coveredLanes();
        Lane lane = hero.lanes().stream().filter(l -> !covered.contains(l)).findFirst().orElse(hero.lane());
        team.picks().add(hero);
        team.assignLane(hero, lane);
    }

    public void remove(Side side, String heroName) {
        Hero hero = find(heroName);
        if (!team(side).remove(hero)) {
            throw new IllegalStateException(hero.name() + " is not picked or banned by " + team(side).name() + ".");
        }
    }

    public void setLane(Side side, String heroName, Lane lane) {
        Hero hero = find(heroName);
        Team team = team(side);
        if (!team.picks().contains(hero)) throw new IllegalStateException(hero.name() + " is not picked by " + team.name() + ".");
        team.assignLane(hero, lane);
    }

    public void ban(Side side, String heroName) {
        Hero hero = find(heroName);
        Team team = team(side);
        if (!isAvailable(hero)) throw new IllegalStateException(hero.name() + " is already picked or banned.");
        if (team.bans().size() >= Team.MAX_BANS) throw new IllegalStateException(team.name() + " already has 5 bans.");
        team.bans().add(hero);
    }
}
