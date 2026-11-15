package jongrai;

import java.util.HashMap;
import java.util.Map;

/**
 * How one league season awards table points for a finished regular-season series. It is read from
 * the points_rule column of data/pro-leagues.csv, so each league (and each season of a league) can
 * have its own rule without any code change.
 *
 * <p>The rule is a ";"-separated list of settings. Anything not mentioned keeps its default
 * (win=1, draw=0, loss=0), so a blank rule means "1 point per series win":
 * <ul>
 *   <li>{@code win=3}: points for the winner of any series</li>
 *   <li>{@code draw=1}: points for each team when a series ends level (a Bo2 that finishes 1-1)</li>
 *   <li>{@code loss=0}: points for the loser of any series</li>
 *   <li>{@code 2-1=2:1}: an override for one exact score, written winner's games - loser's games,
 *       then winner's points : loser's points. It beats win= and loss= for that score.</li>
 * </ul>
 * Examples: {@code win=3} / {@code 2-0=3:0;2-1=2:1} / {@code 2-0=3:0;2-1=2:1;1-1=1:1}.
 */
public final class PointsRule {

    /** The default: 1 point per series win, nothing for a loss or a draw. */
    public static final PointsRule DEFAULT = new PointsRule(1, 0, 0, Map.of());

    private final int win;
    private final int draw;
    private final int loss;
    private final Map<String, int[]> scores; // "2-1" -> {winner points, loser points}

    private PointsRule(int win, int draw, int loss, Map<String, int[]> scores) {
        this.win = win;
        this.draw = draw;
        this.loss = loss;
        this.scores = scores;
    }

    /** Reads a rule. Blank gives {@link #DEFAULT}. Throws IllegalArgumentException with a readable reason. */
    public static PointsRule parse(String text) {
        if (text == null || text.isBlank()) return DEFAULT;
        int win = 1, draw = 0, loss = 0;
        Map<String, int[]> scores = new HashMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String raw : text.split(";")) {
            String token = raw.trim();
            if (token.isEmpty()) continue;
            int eq = token.indexOf('=');
            if (eq < 0) throw new IllegalArgumentException("'" + token + "' needs an '=' (e.g. win=3 or 2-1=2:1)");
            String key = token.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT);
            String value = token.substring(eq + 1).trim();
            if (!seen.add(key)) throw new IllegalArgumentException("'" + key + "' is given twice");
            switch (key) {
                case "win" -> win = points(value, token);
                case "draw" -> draw = points(value, token);
                case "loss" -> loss = points(value, token);
                default -> {
                    if (!key.matches("\\d+-\\d+")) {
                        throw new IllegalArgumentException("unknown setting '" + key
                                + "' (use win, draw, loss, or a score like 2-1)");
                    }
                    String[] g = key.split("-");
                    if (Integer.parseInt(g[0]) <= Integer.parseInt(g[1])) {
                        throw new IllegalArgumentException("'" + key + "' must list the winner's games first, so "
                                + "the first number has to be bigger (a level score uses draw=)");
                    }
                    String[] p = value.split(":", -1);
                    if (p.length != 2) {
                        throw new IllegalArgumentException("'" + token + "' needs winnerPoints:loserPoints (e.g. " + key + "=2:1)");
                    }
                    scores.put(key, new int[] {points(p[0].trim(), token), points(p[1].trim(), token)});
                }
            }
        }
        return new PointsRule(win, draw, loss, Map.copyOf(scores));
    }

    private static int points(String v, String token) {
        if (!v.matches("\\d{1,3}")) throw new IllegalArgumentException("'" + token + "' needs a whole number of points (0-999)");
        return Integer.parseInt(v);
    }

    /** Points for the team that won a series {@code winnerGames}-{@code loserGames}. */
    public int winnerPoints(int winnerGames, int loserGames) {
        int[] s = scores.get(winnerGames + "-" + loserGames);
        return s != null ? s[0] : win;
    }

    /** Points for the team that lost a series {@code winnerGames}-{@code loserGames}. */
    public int loserPoints(int winnerGames, int loserGames) {
        int[] s = scores.get(winnerGames + "-" + loserGames);
        return s != null ? s[1] : loss;
    }

    /** Points for each team when a series ends level. */
    public int drawPoints() {
        return draw;
    }
}