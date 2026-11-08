package jongrai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * How each hero does on each arena map in recorded pro games (pro-drafts.csv), current season of each league only. The draft board uses it to
 * nudge suggestions toward heroes that win or get contested on the chosen map. Games on "Default Map" or with
 * no map are left out, so a map only counts once it has real games behind it.
 */
public class MapStats {

    static final int MIN_PICKS = 3;          // fewer picks than this on a map: no map reason at all
    static final double PRIOR = 3;           // pseudo-games at 50% that pull small samples toward even
    static final double WIN_WEIGHT = 10;     // score per win-rate share above/below 50% (60% -> +1.0)
    static final double PRESENCE_WEIGHT = 4; // score per share of games where the hero was picked or banned

    public record HeroOnMap(int picks, int wins, int bans) {}
    public record ArenaMap(String name, String icon, int games) {}

    private final Map<String, Integer> games = new TreeMap<>();
    private final Map<String, Map<String, int[]>> byMap = new LinkedHashMap<>(); // map -> hero key -> picks, wins, bans

    public MapStats(List<ProLeagueRepository.ProDraft> drafts) {
        for (ProLeagueRepository.ProDraft d : drafts) {
            String map = d.map() == null ? "" : d.map().trim();
            if (map.isEmpty() || map.toLowerCase(Locale.ROOT).startsWith("default")) continue;
            games.merge(map, 1, Integer::sum);
            Map<String, int[]> heroes = byMap.computeIfAbsent(map, k -> new LinkedHashMap<>());
            add(heroes, d.bluePicks(), d.blueTeamId().equals(d.winnerTeamId()));
            add(heroes, d.redPicks(), d.redTeamId().equals(d.winnerTeamId()));
            for (String b : d.blueBans()) bump(heroes, b)[2]++;
            for (String b : d.redBans()) bump(heroes, b)[2]++;
        }
    }

    private static void add(Map<String, int[]> heroes, List<String> picks, boolean won) {
        for (String p : picks) { int[] c = bump(heroes, p); c[0]++; if (won) c[1]++; }
    }

    private static int[] bump(Map<String, int[]> heroes, String hero) {
        return heroes.computeIfAbsent(key(hero), k -> new int[3]);
    }

    static String key(String hero) { return hero.trim().toLowerCase(Locale.ROOT); }

    /** The maps with recorded games, A-Z, with the icon file in web/icons. */
    public List<ArenaMap> maps() {
        List<ArenaMap> out = new ArrayList<>();
        games.forEach((m, n) -> out.add(new ArenaMap(m, m.replace(' ', '_') + ".png", n)));
        return out;
    }

    /** Recorded games on a map; 0 for an unknown map or "any". */
    public int games(String map) { return games.getOrDefault(map, 0); }

    public HeroOnMap hero(String map, String hero) {
        int[] c = byMap.getOrDefault(map, Map.of()).get(key(hero));
        return c == null ? new HeroOnMap(0, 0, 0) : new HeroOnMap(c[0], c[1], c[2]);
    }

    /** Score nudge for this hero on this map, with a short reason; reason is null when there is nothing to say. */
    public Nudge nudge(String map, Hero hero) {
        int n = games(map);
        if (n == 0) return new Nudge(0, null);
        HeroOnMap h = hero(map, hero.name());
        double presence = (double) (h.picks() + h.bans()) / n;
        double score = Math.min(presence, 1) * PRESENCE_WEIGHT;
        if (h.picks() < MIN_PICKS) return new Nudge(score, score >= 1 ? contested(map, presence) : null);
        double wr = (h.wins() + PRIOR / 2) / (h.picks() + PRIOR);
        score += (wr - 0.5) * WIN_WEIGHT;
        String reason = Math.abs((wr - 0.5) * WIN_WEIGHT) >= 0.5
                ? String.format(Locale.ROOT, "%d%% win on %s (%d pro picks)", Math.round(100.0 * h.wins() / h.picks()), map, h.picks())
                : presence >= 0.25 ? contested(map, presence) : null;
        return new Nudge(score, reason);
    }

    private static String contested(String map, double presence) {
        return String.format(Locale.ROOT, "contested on %s (%d%% of games)", map, Math.round(presence * 100));
    }

    public record Nudge(double score, String reason) {}
}
