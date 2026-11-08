package jongrai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Scores every available hero for one side and returns the best few with reasons. */
public class SuggestionEngine {

    // Tweak these numbers to change how the engine "thinks".
    static final int COUNTER_BONUS = 3;     // candidate counters an enemy pick
    static final int COUNTERED_PENALTY = 3; // an enemy pick counters the candidate
    static final int SYNERGY_BONUS = 2;     // candidate works well with an ally
    static final int NEED_TANK_BONUS = 2;   // team has no tank yet
    static final int DUPLICATE_ROLE_PENALTY = 1; // team already has 2+ of this role
    static final int OPEN_LANE_BONUS = 8;   // candidate can play a lane nobody on the team covers
    static final int DUPLICATE_LANE_PENALTY = 5; // candidate's main lanes already covered
    static final double WINRATE_WEIGHT = 0.25; // score per win-rate point above/below 50% (59% -> +2.25)

    static final double BAN_RATE_WEIGHT = 0.1; // score per ban-rate point (65.8% -> +6.6)
    static final int THREAT_BONUS = 3;         // candidate counters one of YOUR picks
    static final int ENEMY_SYNERGY_BONUS = 2;  // candidate works well with an enemy pick

    public record Suggestion(Hero hero, double score, List<String> reasons) {}

    public List<Suggestion> suggest(Draft draft, Draft.Side side, int limit) {
        return suggest(draft, side, limit, null, "");
    }

    /** Like suggest(), and when a map is chosen, heroes that win or get contested on it in pro games score higher. */
    public List<Suggestion> suggest(Draft draft, Draft.Side side, int limit, MapStats maps, String map) {
        Team mine = draft.team(side);
        if (mine.picksFull()) return List.of();
        Team theirs = draft.opponent(side);
        Set<Lane> covered = mine.coveredLanes();
        boolean needsTank = mine.countRole(Role.TANK) == 0;

        List<Suggestion> results = new ArrayList<>();
        for (Hero candidate : draft.availableHeroes()) {
            double score = 0;
            List<String> reasons = new ArrayList<>();

            for (Hero enemy : theirs.picks()) {
                if (candidate.counters(enemy)) {
                    score += COUNTER_BONUS;
                    reasons.add("counters " + enemy.name());
                }
                if (enemy.counters(candidate)) {
                    score -= COUNTERED_PENALTY;
                    reasons.add("weak into " + enemy.name());
                }
            }

            for (Hero ally : mine.picks()) {
                if (candidate.synergizesWith(ally) || ally.synergizesWith(candidate)) {
                    score += SYNERGY_BONUS;
                    reasons.add("synergy with " + ally.name());
                }
            }

            if (candidate.hasRole(Role.TANK) && needsTank) {
                score += NEED_TANK_BONUS;
                reasons.add("your team has no tank");
            }
            boolean fillsOpenLane = candidate.lanes().stream().anyMatch(l -> !covered.contains(l));
            if (fillsOpenLane) {
                score += OPEN_LANE_BONUS;
                reasons.add("fills open lane");
            } else {
                score -= DUPLICATE_LANE_PENALTY;
                reasons.add("lane already covered");
            }
            if (mine.countRole(candidate.role()) >= 2) {
                score -= DUPLICATE_ROLE_PENALTY;
                reasons.add("already have 2+ " + candidate.role());
            }

            double winRateBonus = (candidate.winRate() - 50.0) * WINRATE_WEIGHT;
            score += winRateBonus;
            if (Math.abs(winRateBonus) >= 1) {
                reasons.add(String.format(Locale.ROOT, "%s win rate %.1f%%", winRateBonus > 0 ? "high" : "low", candidate.winRate()));
            }
            score += onMap(maps, map, candidate, reasons, false);

            results.add(new Suggestion(candidate, score, reasons));
        }
        return best(results, limit);
    }

    /** Who should "side" ban? Popular, strong heroes, plus ones that hurt its picks or help the enemy. */
    public List<Suggestion> suggestBans(Draft draft, Draft.Side side, int limit) {
        return suggestBans(draft, side, limit, null, "");
    }

    public List<Suggestion> suggestBans(Draft draft, Draft.Side side, int limit, MapStats maps, String map) {
        Team mine = draft.team(side);
        if (mine.bansFull()) return List.of();
        Team theirs = draft.opponent(side);

        List<Suggestion> results = new ArrayList<>();
        for (Hero candidate : draft.availableHeroes()) {
            double score = 0;
            List<String> reasons = new ArrayList<>();

            score += candidate.banRate() * BAN_RATE_WEIGHT;
            if (candidate.metaBan()) reasons.add(String.format(Locale.ROOT, "ban rate %.1f%%", candidate.banRate()));
            double winRateBonus = (candidate.winRate() - 50.0) * WINRATE_WEIGHT;
            score += winRateBonus;
            if (winRateBonus >= 1) {
                reasons.add(String.format(Locale.ROOT, "high win rate %.1f%%", candidate.winRate()));
            }
            for (Hero ally : mine.picks()) {
                if (candidate.counters(ally)) {
                    score += THREAT_BONUS;
                    reasons.add("counters your " + ally.name());
                }
            }
            for (Hero enemy : theirs.picks()) {
                if (candidate.synergizesWith(enemy) || enemy.synergizesWith(candidate)) {
                    score += ENEMY_SYNERGY_BONUS;
                    reasons.add("synergy with their " + enemy.name());
                }
            }
            score += onMap(maps, map, candidate, reasons, true);
            results.add(new Suggestion(candidate, score, reasons));
        }
        return best(results, limit);
    }

    /** The map nudge; for bans only the upside counts (a hero that is weak on the map is not worth a ban anyway). */
    private static double onMap(MapStats maps, String map, Hero hero, List<String> reasons, boolean ban) {
        if (maps == null || map == null || map.isBlank()) return 0;
        MapStats.Nudge n = maps.nudge(map, hero);
        double s = ban ? Math.max(0, n.score()) : n.score();
        if (n.reason() != null && (!ban || n.score() > 0)) reasons.add(n.reason());
        return s;
    }

    private static List<Suggestion> best(List<Suggestion> results, int limit) {
        results.sort(Comparator.comparingDouble(Suggestion::score).reversed()
                .thenComparing(s -> s.hero().name()));
        return results.subList(0, Math.min(limit, results.size()));
    }
}
