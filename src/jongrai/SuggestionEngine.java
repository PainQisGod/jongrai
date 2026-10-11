package jongrai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        Team mine = draft.team(side);
        Team theirs = draft.opponent(side);

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

            if (candidate.hasRole(Role.TANK) && mine.countRole(Role.TANK) == 0) {
                score += NEED_TANK_BONUS;
                reasons.add("your team has no tank");
            }
            Set<Lane> covered = mine.coveredLanes();
            boolean fillsOpenLane = false;
            boolean duplicatesLane = false;
            for (Lane lane : candidate.lanes()) {
                if (!covered.contains(lane)) {
                    fillsOpenLane = true;
                    break;
                } else {
                    duplicatesLane = true;
                }
            }
            if (fillsOpenLane) {
                score += OPEN_LANE_BONUS;
                reasons.add("fills open lane");
            }
            if (duplicatesLane && !fillsOpenLane) {
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
                reasons.add(String.format("%s win rate %.1f%%", winRateBonus > 0 ? "high" : "low", candidate.winRate()));
            }

            results.add(new Suggestion(candidate, score, reasons));
        }

        results.sort(Comparator.comparingDouble(Suggestion::score).reversed()
                .thenComparing(s -> s.hero().name()));
        return results.subList(0, Math.min(limit, results.size()));
    }

    /** Who should "side" ban? Popular, strong heroes, plus ones that hurt its picks or help the enemy. */
    public List<Suggestion> suggestBans(Draft draft, Draft.Side side, int limit) {
        Team mine = draft.team(side);
        Team theirs = draft.opponent(side);

        List<Suggestion> results = new ArrayList<>();
        for (Hero candidate : draft.availableHeroes()) {
            double score = 0;
            List<String> reasons = new ArrayList<>();

            if (candidate.banRate() > 0) {
                score += candidate.banRate() * BAN_RATE_WEIGHT;
                reasons.add(String.format("ban rate %.1f%%", candidate.banRate()));
            }
            double winRateBonus = (candidate.winRate() - 50.0) * WINRATE_WEIGHT;
            score += winRateBonus;
            if (winRateBonus >= 1) {
                reasons.add(String.format("high win rate %.1f%%", candidate.winRate()));
            }
            for (Hero mine1 : mine.picks()) {
                if (candidate.counters(mine1)) {
                    score += THREAT_BONUS;
                    reasons.add("counters your " + mine1.name());
                }
            }
            for (Hero enemy : theirs.picks()) {
                if (candidate.synergizesWith(enemy) || enemy.synergizesWith(candidate)) {
                    score += ENEMY_SYNERGY_BONUS;
                    reasons.add("synergy with their " + enemy.name());
                }
            }
            results.add(new Suggestion(candidate, score, reasons));
        }
        results.sort(Comparator.comparingDouble(Suggestion::score).reversed()
                .thenComparing(x -> x.hero().name()));
        return results.subList(0, Math.min(limit, results.size()));
    }
}
