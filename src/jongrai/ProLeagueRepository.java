package jongrai;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Loads professional league data. */
public final class ProLeagueRepository {

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final Map<String, ProLeague> leagues;
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

    public static ProLeagueRepository load(Path base) throws java.io.IOException {
        Path leaguesFile = base.resolve("pro-leagues.csv");
        Path teamsFile = base.resolve("pro-teams.csv");
        Path standingsFile = base.resolve("pro-standings.csv");
        Path matchesFile = base.resolve("pro-matches.csv");
        Path draftsFile = base.resolve("pro-drafts.csv");

        Map<String, ProLeague> leagues = loadLeagues(leaguesFile);
        Map<String, ProTeam> teams = loadTeams(teamsFile);
        Map<String, List<ProStanding>> standings = loadStandings(standingsFile);
        Map<String, List<ProMatch>> matches = loadMatches(matchesFile);
        Map<String, List<ProDraft>> drafts = loadDrafts(draftsFile);

        return new ProLeagueRepository(leagues, teams, standings, matches, drafts);
    }

    private static Map<String, ProLeague> loadLeagues(Path file) throws java.io.IOException {
        Map<String, ProLeague> leagues = new LinkedHashMap<>();
        if (!java.nio.file.Files.exists(file)) return leagues;

        boolean header = true;
        for (String line : java.nio.file.Files.readAllLines(file)) {
            if (line.startsWith("\uFEFF")) line = line.substring(1);
            if (line.isBlank() || line.startsWith("#")) continue;
            if (header) { header = false; continue; }

            List<String> p = splitCsv(line);
            if (p.size() < 8) continue;
            ProLeague league = new ProLeague(
                    p.get(0).trim(), p.get(1).trim(), p.get(2).trim(), p.get(3).trim(),
                    p.get(4).trim(), (int) number(p.get(5), 0), p.get(6).trim(), p.get(7).trim());
            leagues.put(league.id(), league);
        }
        return leagues;
    }

    private static Map<String, ProTeam> loadTeams(Path file) throws java.io.IOException {
        Map<String, ProTeam> teams = new LinkedHashMap<>();
        if (!java.nio.file.Files.exists(file)) return teams;

        boolean header = true;
        for (String line : java.nio.file.Files.readAllLines(file)) {
            if (line.startsWith("\uFEFF")) line = line.substring(1);
            if (line.isBlank() || line.startsWith("#")) continue;
            if (header) { header = false; continue; }

            List<String> p = splitCsv(line);
            if (p.size() < 9) continue;
            ProTeam team = new ProTeam(
                    p.get(0).trim(), p.get(1).trim(), p.get(2).trim(), p.get(3).trim(),
                    p.get(4).trim(), (int) number(p.get(5), 0), p.get(6).trim(), p.get(7).trim(), p.get(8).trim());
            teams.put(team.id(), team);
        }
        return teams;
    }

    private static Map<String, List<ProStanding>> loadStandings(Path file) throws java.io.IOException {
        Map<String, List<ProStanding>> standings = new LinkedHashMap<>();
        if (!java.nio.file.Files.exists(file)) return standings;

        boolean header = true;
        for (String line : java.nio.file.Files.readAllLines(file)) {
            if (line.startsWith("\uFEFF")) line = line.substring(1);
            if (line.isBlank() || line.startsWith("#")) continue;
            if (header) { header = false; continue; }

            List<String> p = splitCsv(line);
            if (p.size() < 15) continue;
            ProStanding s = new ProStanding(
                    p.get(0).trim(), p.get(1).trim(), p.get(2).trim(),
                    (int) number(p.get(3), 0), (int) number(p.get(4), 0), (int) number(p.get(5), 0),
                    (int) number(p.get(6), 0), (int) number(p.get(7), 0), (int) number(p.get(8), 0),
                    (int) number(p.get(9), 0), (int) number(p.get(10), 0), (int) number(p.get(11), 0),
                    p.get(12).trim(), p.get(13).trim(), p.get(14).trim());
            standings.computeIfAbsent(s.leagueId(), k -> new ArrayList<>()).add(s);
        }
        standings.values().forEach(list -> list.sort(Comparator.comparingInt(ProStanding::position)));
        return standings;
    }

    private static Map<String, List<ProMatch>> loadMatches(Path file) throws java.io.IOException {
        Map<String, List<ProMatch>> matches = new LinkedHashMap<>();
        if (!java.nio.file.Files.exists(file)) return matches;

        boolean header = true;
        for (String line : java.nio.file.Files.readAllLines(file)) {
            if (line.startsWith("\uFEFF")) line = line.substring(1);
            if (line.isBlank() || line.startsWith("#")) continue;
            if (header) { header = false; continue; }

            List<String> p = splitCsv(line);
            if (p.size() < 13) continue;
            ProMatch m = new ProMatch(
                    p.get(0).trim(), p.get(1).trim(), p.get(2).trim(),
                    (int) number(p.get(3), 0), (int) number(p.get(4), 0),
                    p.get(5).trim(), p.get(6).trim(),
                    (int) number(p.get(7), -1), (int) number(p.get(8), -1),
                    p.get(9).trim(), p.get(10).trim(), p.get(11).trim(), p.get(12).trim());
            matches.computeIfAbsent(m.leagueId(), k -> new ArrayList<>()).add(m);
        }
        matches.values().forEach(list -> list.sort(Comparator.comparing(ProMatch::scheduledTime)));
        return matches;
    }

    private static Map<String, List<ProDraft>> loadDrafts(Path file) throws java.io.IOException {
        Map<String, List<ProDraft>> drafts = new LinkedHashMap<>();
        if (!java.nio.file.Files.exists(file)) return drafts;

        boolean header = true;
        for (String line : java.nio.file.Files.readAllLines(file)) {
            if (line.startsWith("\uFEFF")) line = line.substring(1);
            if (line.isBlank() || line.startsWith("#")) continue;
            if (header) { header = false; continue; }

            List<String> p = splitCsv(line);
            if (p.size() < 14) continue;
            ProDraft d = new ProDraft(
                    p.get(0).trim(), p.get(1).trim(), p.get(2).trim(), p.get(3).trim(),
                    (int) number(p.get(4), 0), p.get(5).trim(), p.get(6).trim(),
                    splitList(p.get(7)), splitList(p.get(8)), splitList(p.get(9)), splitList(p.get(10)),
                    p.get(11).trim().equals("1"), p.get(12).trim(), p.get(13).trim());
            drafts.computeIfAbsent(d.leagueId(), k -> new ArrayList<>()).add(d);
        }
        return drafts;
    }

    public List<ProLeague> leagues() {
        return List.copyOf(leagues.values());
    }

    public List<ProLeague> activeLeagues() {
        return leagues.values().stream().filter(l -> l.status().equals("active")).toList();
    }

    public ProLeague league(String id) {
        return leagues.get(id);
    }

    public ProTeam team(String id) {
        return teams.get(id);
    }

    public List<ProTeam> teamsInLeague(String leagueId) {
        return teams.values().stream().filter(t -> t.leagueId().equals(leagueId)).toList();
    }

    public List<ProTeam> teams() {
        return List.copyOf(teams.values());
    }

    public List<ProStanding> standings(String leagueId, String season) {
        return standings.getOrDefault(leagueId, List.of()).stream()
                .filter(s -> s.season().equals(season)).toList();
    }

    public List<ProMatch> matches(String leagueId, String season) {
        return matches.getOrDefault(leagueId, List.of()).stream()
                .filter(m -> m.season().equals(season)).toList();
    }

    public List<ProMatch> upcomingMatches(String leagueId, String season, int limit) {
        return matches.getOrDefault(leagueId, List.of()).stream()
                .filter(m -> m.season().equals(season))
                .filter(m -> m.status().equals("scheduled"))
                .limit(limit).toList();
    }

    public List<ProDraft> drafts(String leagueId, String season) {
        return drafts.getOrDefault(leagueId, List.of()).stream()
                .filter(d -> d.season().equals(season)).toList();
    }

    public ProDraft draft(String draftId) {
        return drafts.values().stream().flatMap(List::stream)
                .filter(d -> d.id().equals(draftId)).findFirst().orElse(null);
    }

    public ProMatch match(String matchId) {
        return matches.values().stream().flatMap(List::stream)
                .filter(m -> m.id().equals(matchId)).findFirst().orElse(null);
    }

    private static List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else quoted = false;
                } else cell.append(c);
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    private static List<String> splitList(String field) {
        if (field == null || field.isBlank()) return List.of();
        return java.util.Arrays.stream(field.split(";"))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(ArrayList::new));
    }

    private static double number(String field, double ifBlank) {
        String text = field.trim();
        if (text.isEmpty()) return ifBlank;
        var m = DIGITS.matcher(text);
        if (!m.find()) throw new IllegalArgumentException("Not a number: " + field);
        return Double.parseDouble(m.group());
    }

    public record ProLeague(String id, String name, String region, String country,
                            String season, int year, String status, String logo) {}

    public record ProTeam(String id, String leagueId, String name, String shortName,
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
                           boolean blueSide, String winnerTeamId, String patch) {}
}