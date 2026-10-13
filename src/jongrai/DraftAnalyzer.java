package jongrai;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Compares the two drafts: an overall edge, team coordination, and the tactical profile. */
public class DraftAnalyzer {

    // How much each category counts toward the overall edge (adds up to 1.0).
    static final double W_COUNTERS = 0.25, W_META = 0.20, W_SYNERGY = 0.15, W_LOCKDOWN = 0.15, W_COORDINATION = 0.25;
    static final int HEAVY_CONTROL = 70; // control rating that counts as a lockdown hero

    /** blueShare = blue's share of the bar (0-100); 50 means even. */
    public record Category(String name, double blueShare, String detail) {}
    public record Axis(String name, double blue, double red) {}   // team scores on a 0-10 scale
    public record Analysis(int blueEdge, int redEdge, String lead, String headline,
                           List<Category> categories, List<Axis> profile) {}

    /** Team coordination metrics for one side. */
    public record TeamSide(
        int lanesCovered,
        boolean hasTank,
        boolean hasMarksmanOrMage,
        int roleVariety,
        int synergyPairs,
        int heavyControl,
        double avgWinRate,
        String spikeTiming, // EARLY, MID, LATE, MIXED (tied), NONE (no picks)
        List<String> missingLanes,
        List<String> missingRoles,
        double score
    ) {}

    /** Team coordination metrics for the draft UI. */
    public record Coordination(
        TeamSide blue, TeamSide red
    ) {}

    public Analysis analyze(Draft draft) {
        Team blue = draft.team(Draft.Side.BLUE), red = draft.team(Draft.Side.RED);
        List<Category> cats = new ArrayList<>();

        // Counters: how many pick-vs-pick matchups each side wins
        int bc = 0, rc = 0;
        for (Hero b : blue.picks()) {
            for (Hero r : red.picks()) {
                if (b.counters(r)) bc++;
                if (r.counters(b)) rc++;
            }
        }
        double counterShare = smooth(bc, rc);
        cats.add(new Category("COUNTERS", counterShare, bc == rc ? "Counter matchups are even (" + bc + " vs " + rc + ")"
                : (bc > rc ? "Blue" : "Red") + " leads the counter matchups (" + bc + " vs " + rc + ")"));

        // Meta power: average overall win rate of the picks
        double bw = avgWinRate(blue), rw = avgWinRate(red);
        double metaShare = Math.max(5, Math.min(95, 50 + (bw - rw) * 10));
        cats.add(new Category("META POWER", metaShare, String.format(Locale.ROOT, "Avg win rate %.1f%% vs %.1f%%", bw, rw)));

        // Synergy: pairs on the same team that work well together
        int bs = synergyPairs(blue), rs = synergyPairs(red);
        double synergyShare = smooth(bs, rs);
        cats.add(new Category("SYNERGY", synergyShare, bs + " combo pairs vs " + rs));

        // Lockdown: heroes with a strong control rating
        int bl = heavyControl(blue), rl = heavyControl(red);
        double lockdownShare = smooth(bl, rl);
        cats.add(new Category("LOCKDOWN", lockdownShare, "Heavy-control heroes " + bl + " vs " + rl));

        // Coordination rewards drafts that can execute together, rather than merely filling slots.
        TeamSide blueCoord = coord(blue), redCoord = coord(red);
        double coordinationShare = blueCoord.score() + redCoord.score() == 0 ? 50
                : blueCoord.score() / (blueCoord.score() + redCoord.score()) * 100;
        cats.add(new Category("TEAM COORDINATION", coordinationShare,
                String.format(Locale.ROOT, "Coordination %.0f/100 vs %.0f/100", blueCoord.score(), redCoord.score())));

        boolean empty = blue.picks().isEmpty() && red.picks().isEmpty();
        double edge = empty ? 50 : counterShare * W_COUNTERS + metaShare * W_META
                + synergyShare * W_SYNERGY + lockdownShare * W_LOCKDOWN
                + coordinationShare * W_COORDINATION;
        int blueEdge = (int) Math.round(edge);
        int gap = Math.abs(blueEdge - 50) * 2;
        String lead = gap < 4 ? "even" : blueEdge > 50 ? "blue" : "red";
        String side = blueEdge > 50 ? "Blue" : "Red";
        String headline = gap < 4 ? "The draft is even"
                : side + " has a " + (gap >= 24 ? "commanding" : gap >= 12 ? "clear" : "slight") + " draft edge";
        return new Analysis(blueEdge, 100 - blueEdge, lead, headline, cats, profile(blue, red));
    }

    /** Returns team coordination metrics for both sides. */
    public Coordination coordination(Draft draft) {
        Team blue = draft.team(Draft.Side.BLUE);
        Team red = draft.team(Draft.Side.RED);
        return new Coordination(coord(blue), coord(red));
    }

    private TeamSide coord(Team t) {
        Set<Lane> covered = t.coveredLanes();
        Set<Lane> allLanes = EnumSet.allOf(Lane.class);
        List<String> missingLanes = allLanes.stream()
            .filter(l -> !covered.contains(l))
            .map(Lane::name)
            .toList();

        Set<Role> roles = EnumSet.noneOf(Role.class);
        t.picks().forEach(h -> roles.addAll(h.roles()));
        EnumSet<Role> allRoles = EnumSet.allOf(Role.class);
        List<String> missingRoles = allRoles.stream()
            .filter(r -> !roles.contains(r))
            .map(Role::name)
            .toList();

        return new TeamSide(
            covered.size(),
            t.countRole(Role.TANK) > 0,
            t.countRole(Role.MARKSMAN) > 0 || t.countRole(Role.MAGE) > 0,
            roles.size(),
            synergyPairs(t),
            heavyControl(t),
            avgWinRate(t),
            spikeTiming(t),
            missingLanes,
            missingRoles,
            coordinationScore(t, covered, roles)
        );
    }

    /** The spike most of the team shares; MIXED when the top count is tied, NONE with no picks. ALL heroes don't vote. */
    private static String spikeTiming(Team t) {
        if (t.picks().isEmpty()) return "NONE";
        Spike best = null;
        long top = -1;
        boolean tied = false;
        for (Spike s : List.of(Spike.EARLY, Spike.MID, Spike.LATE)) {
            long n = countSpike(t, s);
            if (n > top) { top = n; best = s; tied = false; }
            else if (n == top) tied = true;
        }
        return tied ? "MIXED" : best.name();
    }

    private static long countSpike(Team t, Spike spike) {
        return t.picks().stream().filter(h -> h.spike() == spike).count();
    }

    private static int heavyControl(Team t) {
        return (int) t.picks().stream().filter(h -> h.control() >= HEAVY_CONTROL).count();
    }

    /** One profile axis: its name and how it scores a (non-empty) list of picks, 0-10. */
    private record AxisDef(String name, Function<List<Hero>, Double> score) {}

    private static final List<AxisDef> AXES = List.of(
            new AxisDef("Control", p -> p.stream().mapToInt(Hero::control).average().orElse(0) / 10),
            new AxisDef("Damage", p -> p.stream().mapToInt(Hero::offense).average().orElse(0) / 10),
            new AxisDef("Durability", p -> p.stream().mapToInt(Hero::durability).average().orElse(0) / 10),
            new AxisDef("Burst", p -> tagScore(p, "burst")),
            new AxisDef("Engage", p -> tagScore(p, "chase", "initiator", "charge")),
            new AxisDef("Sustain", p -> tagScore(p, "regen", "guard", "support")),
            new AxisDef("Push", p -> tagScore(p, "push")),
            new AxisDef("Early", p -> p.stream().mapToDouble(h -> spikeScore(h, true)).average().orElse(0)),
            new AxisDef("Late", p -> p.stream().mapToDouble(h -> spikeScore(h, false)).average().orElse(0)));

    /** Team scores (0-10) on nine traits, from Moonton's hero ratings and speciality tags. */
    private static List<Axis> profile(Team b, Team r) {
        return AXES.stream().map(a -> new Axis(a.name(), axis(a, b), axis(a, r))).toList();
    }

    private static double axis(AxisDef a, Team t) {
        return t.picks().isEmpty() ? 0 : a.score().apply(t.picks());
    }

    /** Early/late strength from the hero's power spike: EARLY 9/3, MID 6/6, LATE 3/9, ALL 7/7 (strong throughout). */
    private static double spikeScore(Hero h, boolean early) {
        return switch (h.spike()) {
            case EARLY -> early ? 9 : 3;
            case LATE -> early ? 3 : 9;
            case MID -> 6;
            case ALL -> 7;
        };
    }

    /** Heroes with any of the tags: 4 or more heroes = 10/10, each hero is worth 2.5. */
    private static double tagScore(List<Hero> picks, String... tags) {
        long n = picks.stream().filter(h -> java.util.Arrays.stream(tags).anyMatch(h::hasTag)).count();
        return Math.min(10, n * 2.5);
    }

    private static double smooth(int a, int b) { return (a + 1.0) / (a + b + 2.0) * 100; }

    private static double avgWinRate(Team t) {
        return t.picks().isEmpty() ? 50.0 : t.picks().stream().mapToDouble(Hero::winRate).average().orElse(50.0);
    }

    private static int synergyPairs(Team t) {
        int count = 0;
        List<Hero> p = t.picks();
        for (int i = 0; i < p.size(); i++) {
            for (int j = i + 1; j < p.size(); j++) {
                if (p.get(i).synergizesWith(p.get(j)) || p.get(j).synergizesWith(p.get(i))) count++;
            }
        }
        return count;
    }

    /**
     * 0-100: lane assignment, complementary jobs, pair synergies, reliable control,
     * and a game plan that is not all-in on one power spike.
     */
    private static double coordinationScore(Team t, Set<Lane> covered, Set<Role> roles) {
        if (t.picks().isEmpty()) return 0;
        double score = covered.size() / 5.0 * 30;
        if (t.countRole(Role.TANK) > 0) score += 10;
        if (t.countRole(Role.MARKSMAN) > 0 || t.countRole(Role.MAGE) > 0) score += 10;
        score += Math.min(roles.size(), 4) / 4.0 * 15;
        score += Math.min(synergyPairs(t), 3) / 3.0 * 15;
        score += Math.min(heavyControl(t), 2) / 2.0 * 10;
        long early = countSpike(t, Spike.EARLY);
        long late = countSpike(t, Spike.LATE);
        if (early > 0 && late > 0) score += 10;
        else if (t.picks().size() < 3 || early > 0 || late > 0) score += 5;
        return score;
    }
}
