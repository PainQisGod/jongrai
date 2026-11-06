package jongrai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads professional league data. Every file is required and read through {@link Csv}; standings are calculated, not stored. */
public final class ProLeagueRepository {

    private final Map<String, ProLeague> leagues;          // keyed by id|season
    private final Map<String, ProTeam> teams;
    private final Map<String, List<ProMatch>> matches;
    private final Map<String, List<ProDraft>> drafts;

    private ProLeagueRepository(Map<String, ProLeague> leagues, Map<String, ProTeam> teams,
                                Map<String, List<ProMatch>> matches,
                                Map<String, List<ProDraft>> drafts) {
        this.leagues = leagues;
        this.teams = teams;
        this.matches = matches;
        this.drafts = drafts;
    }

    public static ProLeagueRepository load(Path base) throws IOException {
        return new ProLeagueRepository(
                loadLeagues(base.resolve("pro-leagues.csv")),
                loadTeams(base.resolve("pro-teams.csv")),
                loadMatches(base.resolve("pro-matches.csv")),
                loadDrafts(base.resolve("pro-drafts.csv")));
    }

    private static Map<String, ProLeague> loadLeagues(Path file) throws IOException {
        Map<String, ProLeague> leagues = new LinkedHashMap<>();
        for (Csv.Row r : Csv.read(file, 8)) {
            ProLeague l = new ProLeague(r.text(0), r.text(1), r.text(2), r.text(3),
                    r.text(4), r.integer(5, 0), r.text(6), r.text(7));
            if (leagues.putIfAbsent(l.id() + "|" + l.season(), l) != null) {
                throw r.fail("duplicate league '" + l.id() + "' season '" + l.season() + "'");
            }
        }
        return leagues;
    }

    private static Map<String, ProTeam> loadTeams(Path file) throws IOException {
        Map<String, ProTeam> teams = new LinkedHashMap<>();
        for (Csv.Row r : Csv.read(file, 10)) {
            ProTeam t = new ProTeam(r.text(0), r.text(1), r.text(2), r.text(3),
                    r.text(4), r.text(5), r.integer(6, 0), r.text(7), r.text(8), r.text(9));
            String key = t.leagueId() + "|" + t.season() + "|" + t.id();
            if (teams.putIfAbsent(key, t) != null) {
                throw r.fail("duplicate team '" + t.id() + "' in " + t.leagueId() + " " + t.season());
            }
        }
        return teams;
    }

    private static Map<String, List<ProMatch>> loadMatches(Path file) throws IOException {
        Map<String, List<ProMatch>> matches = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Csv.Row r : Csv.read(file, 13)) {
            ProMatch m = new ProMatch(r.text(0), r.text(1), r.text(2), r.integer(3, 0), r.integer(4, 0),
                    r.text(5), r.text(6), r.integer(7, -1), r.integer(8, -1),
                    r.text(9), r.text(10), r.text(11), r.text(12), r.integer(13, 0));
            if (!seen.add(m.id())) throw r.fail("duplicate match '" + m.id() + "'");
            matches.computeIfAbsent(m.leagueId(), k -> new ArrayList<>()).add(m);
        }
        matches.values().forEach(list -> list.sort(Comparator.comparing(ProMatch::scheduledTime)));
        return matches;
    }

    private static Map<String, List<ProDraft>> loadDrafts(Path file) throws IOException {
        Map<String, List<ProDraft>> drafts = new LinkedHashMap<>();
        for (Csv.Row r : Csv.read(file, 16)) {
            ProDraft d = new ProDraft(r.text(0), r.text(1), r.text(2), r.text(3), r.integer(4, 0),
                    r.text(5), r.text(6), r.list(7), r.list(8), r.list(9), r.list(10),
                    r.text(11).equals("1"), r.text(12), r.text(13), r.text(14), duration(r, 15), r.text(16));
            drafts.computeIfAbsent(d.leagueId(), k -> new ArrayList<>()).add(d);
        }
        return drafts;
    }

    /** A game length as "MM:SS" ("35:21"), "H:MM:SS", or plain seconds ("2121"). Blank gives -1. */
    private static int duration(Csv.Row r, int i) throws IOException {
        String t = r.text(i);
        if (t.isEmpty()) return -1;
        if (!t.contains(":")) return r.integer(i, -1);
        String[] p = t.split(":", -1);
        try {
            if (p.length < 2 || p.length > 3) throw new NumberFormatException();
            int[] n = new int[p.length];
            for (int k = 0; k < p.length; k++) n[k] = Integer.parseInt(p[k].trim());
            int sec = n[n.length - 1], min = n[n.length - 2], hour = p.length == 3 ? n[0] : 0;
            if (sec < 0 || sec > 59 || min < 0 || (p.length == 3 && min > 59) || hour < 0) throw new NumberFormatException();
            return hour * 3600 + min * 60 + sec;
        } catch (NumberFormatException e) {
            throw r.fail("column " + (i + 1) + " is not a game length (use MM:SS, e.g. 35:21): '" + t + "'");
        }
    }

    /**
     * Warnings for data that does not add up: pro drafts naming a hero outside the roster, standings
     * for a team that is not listed, standings whose "played" disagrees with the match list, and
     * team logo files that are missing from the given folder.
     */
    public List<String> validate(Set<String> heroKeys, Path logoDir) throws IOException {
        List<String> warnings = new ArrayList<>();
        for (List<ProDraft> list : drafts.values()) {
            for (ProDraft d : list) {
                List<String> named = new ArrayList<>();
                for (List<String> part : List.of(d.blueBans(), d.redBans(), d.bluePicks(), d.redPicks())) named.addAll(part);
                for (String hero : named) {
                    if (!heroKeys.contains(hero.toLowerCase())) warnings.add("pro draft " + d.id() + " names unknown hero '" + hero + "'");
                }
            }
        }
        for (ProTeam t : teams.values()) {
            if (!t.logo().isBlank() && !Files.isRegularFile(logoDir.resolve(t.logo()))) {
                warnings.add("team logo '" + t.logo() + "' for " + t.id() + " is missing");
            }
        }
        for (List<ProMatch> list : matches.values()) {
            for (ProMatch m : list) {
                for (String id : List.of(m.team1Id(), m.team2Id())) {
                    if (teams.get(m.leagueId() + "|" + m.season() + "|" + id) == null) {
                        warnings.add("match " + m.id() + " names unknown team '" + id + "'");
                    }
                }
            }
        }
        return warnings;
    }

    public List<ProLeague> leagues() {
        return List.copyOf(leagues.values());
    }

    public List<ProTeam> teamsInLeague(String leagueId, String season) {
        return teams.values().stream()
                .filter(t -> t.leagueId().equals(leagueId) && t.season().equals(season)).toList();
    }

    /**
     * The table for one season, calculated from its finished regular-season series: 1 point per series win
     * (the MPL KH rule), gf/ga are games won and lost. Ties are broken by game difference, then games won,
     * then team name. Every listed team appears, so a team that has not played yet shows 0-0.
     */
    public List<ProStanding> standings(String leagueId, String season) {
        Map<String, int[]> row = new LinkedHashMap<>();           // won, lost, gf, ga
        Map<String, StringBuilder> results = new LinkedHashMap<>(); // W/L in date order
        for (ProTeam t : teamsInLeague(leagueId, season)) {
            row.put(t.id(), new int[4]);
            results.put(t.id(), new StringBuilder());
        }
        String last = "";
        for (ProMatch m : matches(leagueId, season)) {          // already sorted by scheduled time
            boolean done = m.status().equals("finished") || m.status().equals("completed");
            if (!done || !m.matchType().equals("regular") || m.team1Score() < 0 || m.team2Score() < 0) continue;
            int[] a = row.computeIfAbsent(m.team1Id(), k -> new int[4]);
            int[] b = row.computeIfAbsent(m.team2Id(), k -> new int[4]);
            boolean aWon = m.team1Score() > m.team2Score();
            a[aWon ? 0 : 1]++;
            b[aWon ? 1 : 0]++;
            a[2] += m.team1Score(); a[3] += m.team2Score();
            b[2] += m.team2Score(); b[3] += m.team1Score();
            results.computeIfAbsent(m.team1Id(), k -> new StringBuilder()).append(aWon ? 'W' : 'L');
            results.computeIfAbsent(m.team2Id(), k -> new StringBuilder()).append(aWon ? 'L' : 'W');
            last = m.scheduledTime().length() >= 10 ? m.scheduledTime().substring(0, 10) : m.scheduledTime();
        }
        Map<String, String> names = new LinkedHashMap<>();
        teamsInLeague(leagueId, season).forEach(t -> names.put(t.id(), t.name()));
        List<String> order = new ArrayList<>(row.keySet());
        order.sort(Comparator.<String>comparingInt(id -> -row.get(id)[0])
                .thenComparingInt(id -> -(row.get(id)[2] - row.get(id)[3]))
                .thenComparingInt(id -> -row.get(id)[2])
                .thenComparing(id -> names.getOrDefault(id, id)));
        List<ProStanding> table = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            String id = order.get(i);
            int[] r = row.get(id);
            String res = results.getOrDefault(id, new StringBuilder()).toString();
            table.add(new ProStanding(leagueId, id, season, i + 1, r[0] + r[1], r[0], r[1], 0,
                    r[2], r[3], r[2] - r[3], r[0], streak(res), res.length() > 5 ? res.substring(res.length() - 5) : res, last));
        }
        return table;
    }

    /** "WWLWW" -> "W2" (the current run), "" -> "". */
    static String streak(String results) {
        if (results.isEmpty()) return "";
        char c = results.charAt(results.length() - 1);
        int n = 0;
        for (int i = results.length() - 1; i >= 0 && results.charAt(i) == c; i--) n++;
        return c + Integer.toString(n);
    }

    public List<ProMatch> matches(String leagueId, String season) {
        return matches.getOrDefault(leagueId, List.of()).stream()
                .filter(m -> m.season().equals(season)).toList();
    }

    public List<ProDraft> drafts(String leagueId, String season) {
        return drafts.getOrDefault(leagueId, List.of()).stream()
                .filter(d -> d.season().equals(season)).toList();
    }

    public record ProLeague(String id, String name, String region, String country,
                            String season, int year, String status, String logo) {}

    public record ProTeam(String id, String leagueId, String season, String name, String shortName,
                          String logo, int founded, String city, String coach, String status) {}

    public record ProStanding(String leagueId, String teamId, String season, int position,
                              int played, int won, int lost, int draw, int gf, int ga, int gd,
                              int points, String streak, String form, String lastUpdated) {}

    public record ProMatch(String id, String leagueId, String season, int week, int day,
                           String team1Id, String team2Id, int team1Score, int team2Score,
                           String status, String scheduledTime, String completedTime, String matchType,
                           int bestOf) {}

    public record ProDraft(String id, String matchId, String leagueId, String season, int gameNumber,
                           String blueTeamId, String redTeamId,
                           List<String> blueBans, List<String> redBans,
                           List<String> bluePicks, List<String> redPicks,
                           boolean blueSide, String winnerTeamId, String patch,
                           String map, int duration, String mvp) {}
}
