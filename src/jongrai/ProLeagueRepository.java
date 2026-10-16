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

/** Loads professional league data. Every file is required and read through {@link Csv}. */
public final class ProLeagueRepository {

    private final Map<String, ProLeague> leagues;          // keyed by id|season
    private final Map<String, ProTeam> teams;
    private final Map<String, List<ProStanding>> standings;
    private final Map<String, List<ProMatch>> matches;
    private final Map<String, List<ProDraft>> drafts;

    private ProLeagueRepository(Map<String, ProLeague> leagues, Map<String, ProTeam> teams,
                                Map<String, List<ProStanding>> standings,
                                Map<String, List<ProMatch>> matches,
                                Map<String, List<ProDraft>> drafts) {
        this.leagues = leagues;
        this.teams = teams;
        this.standings = standings;
        this.matches = matches;
        this.drafts = drafts;
    }

    public static ProLeagueRepository load(Path base) throws IOException {
        return new ProLeagueRepository(
                loadLeagues(base.resolve("pro-leagues.csv")),
                loadTeams(base.resolve("pro-teams.csv")),
                loadStandings(base.resolve("pro-standings.csv")),
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

    private static Map<String, List<ProStanding>> loadStandings(Path file) throws IOException {
        Map<String, List<ProStanding>> standings = new LinkedHashMap<>();
        for (Csv.Row r : Csv.read(file, 15)) {
            ProStanding s = new ProStanding(r.text(0), r.text(1), r.text(2),
                    r.integer(3, 0), r.integer(4, 0), r.integer(5, 0), r.integer(6, 0), r.integer(7, 0),
                    r.integer(8, 0), r.integer(9, 0), r.integer(10, 0), r.integer(11, 0),
                    r.text(12), r.text(13), r.text(14));
            standings.computeIfAbsent(s.leagueId(), k -> new ArrayList<>()).add(s);
        }
        standings.values().forEach(list -> list.sort(Comparator.comparingInt(ProStanding::position)));
        return standings;
    }

    private static Map<String, List<ProMatch>> loadMatches(Path file) throws IOException {
        Map<String, List<ProMatch>> matches = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Csv.Row r : Csv.read(file, 13)) {
            ProMatch m = new ProMatch(r.text(0), r.text(1), r.text(2), r.integer(3, 0), r.integer(4, 0),
                    r.text(5), r.text(6), r.integer(7, -1), r.integer(8, -1),
                    r.text(9), r.text(10), r.text(11), r.text(12));
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
                    r.text(11).equals("1"), r.text(12), r.text(13), r.text(14), r.integer(15, -1));
            drafts.computeIfAbsent(d.leagueId(), k -> new ArrayList<>()).add(d);
        }
        return drafts;
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
        for (List<ProStanding> list : standings.values()) {
            for (ProStanding s : list) {
                if (teams.get(s.leagueId() + "|" + s.season() + "|" + s.teamId()) == null) {
                    warnings.add("standings for " + s.leagueId() + " " + s.season() + " list unknown team '" + s.teamId() + "'");
                    continue;
                }
                long played = matches(s.leagueId(), s.season()).stream()
                        .filter(m -> m.status().equals("completed") || m.status().equals("finished"))
                        .filter(m -> m.team1Id().equals(s.teamId()) || m.team2Id().equals(s.teamId())).count();
                if (played != s.played()) {
                    warnings.add("standings say " + s.teamId() + " played " + s.played() + " in " + s.leagueId()
                            + " " + s.season() + " but " + played + " completed matches are listed");
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

    public List<ProStanding> standings(String leagueId, String season) {
        return standings.getOrDefault(leagueId, List.of()).stream()
                .filter(s -> s.season().equals(season)).toList();
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
                           String status, String scheduledTime, String completedTime, String matchType) {}

    public record ProDraft(String id, String matchId, String leagueId, String season, int gameNumber,
                           String blueTeamId, String redTeamId,
                           List<String> blueBans, List<String> redBans,
                           List<String> bluePicks, List<String> redPicks,
                           boolean blueSide, String winnerTeamId, String patch,
                           String map, int duration) {}
}
