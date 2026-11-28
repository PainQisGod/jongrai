package jongrai;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reproducible global, map-based ELO ratings derived from all archived professional matches. */
public final class EloRankingService {
    public static final double STARTING_RATING = 1500.0;
    public static final double K_FACTOR = 16.0;
    public static final double RATING_SCALE = 400.0;
    public static final double DECAY_HALF_LIFE_DAYS = 365.0;
    public static final int MINIMUM_RATED_MAPS = 10;
    public static final long INACTIVE_AFTER_DAYS = 180;
    private static final Pattern LOCAL_MATCH_TIME = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})\\s*-?\\s*(\\d{1,2}:\\d{2})(?:\\s+([A-Z]{2,4}))$");
    private static final DateTimeFormatter LOCAL_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm", Locale.ROOT);
    private static final DateTimeFormatter LOCAL_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss", Locale.ROOT);
    private static final Map<String, ZoneOffset> TIME_ZONES = Map.of(
            "ICT", ZoneOffset.ofHours(7), "WIB", ZoneOffset.ofHours(7),
            "PHT", ZoneOffset.ofHours(8), "MYT", ZoneOffset.ofHours(8), "SGT", ZoneOffset.ofHours(8),
            "MMT", ZoneOffset.ofHoursMinutes(6, 30), "TRT", ZoneOffset.ofHours(3), "AST", ZoneOffset.ofHours(3));

    public Result calculate(ProLeagueRepository repository, Instant now) {
        Map<String, ProLeagueRepository.ProLeague> leagues = new LinkedHashMap<>();
        repository.leagues().forEach(l -> leagues.putIfAbsent(l.id(), l));
        Map<String, ProLeagueRepository.ProTeam> registrations = new LinkedHashMap<>();
        Map<String, ProLeagueRepository.ProTeam> clubs = new LinkedHashMap<>();
        Map<String, Set<String>> clubCompetitions = new LinkedHashMap<>();
        Map<String, String> registrationClubs = new LinkedHashMap<>();
        for (ProLeagueRepository.ProTeam team : repository.allTeams()) {
            String registration = registrationKey(team.leagueId(), team.season(), team.id());
            String club = teamKey(team.clubId(), team.leagueId(), team.id());
            registrations.put(registration, team);
            registrationClubs.put(registration, club);
            clubs.putIfAbsent(club, team);
            ProLeagueRepository.ProLeague league = leagues.get(team.leagueId());
            clubCompetitions.computeIfAbsent(club, ignored -> new LinkedHashSet<>())
                    .add(league == null ? team.leagueId() : league.name());
        }

        List<TimedMatch> ordered = new ArrayList<>();
        int skipped = 0;
        for (ProLeagueRepository.ProMatch match : repository.allMatches()) {
            Instant at = matchInstant(match);
            String registrationA = registrationKey(match.leagueId(), match.season(), match.team1Id());
            String registrationB = registrationKey(match.leagueId(), match.season(), match.team2Id());
            if (!eligible(match) || at == null || !registrationClubs.containsKey(registrationA)
                    || !registrationClubs.containsKey(registrationB)) {
                skipped++;
                continue;
            }
            ordered.add(new TimedMatch(match, at));
        }
        ordered.sort(Comparator.comparing(TimedMatch::at).thenComparing(t -> t.match().id()));

        Map<String, RatingState> states = new LinkedHashMap<>();
        Map<String, List<HistoryEntry>> history = new LinkedHashMap<>();
        for (TimedMatch timed : ordered) {
            ProLeagueRepository.ProMatch match = timed.match();
            String keyA = registrationClubs.get(registrationKey(match.leagueId(), match.season(), match.team1Id()));
            String keyB = registrationClubs.get(registrationKey(match.leagueId(), match.season(), match.team2Id()));
            RatingState a = states.computeIfAbsent(keyA, ignored -> new RatingState());
            RatingState b = states.computeIfAbsent(keyB, ignored -> new RatingState());
            double beforeA = effectiveAt(a, timed.at());
            double beforeB = effectiveAt(b, timed.at());
            int totalMaps = match.team1Score() + match.team2Score();
            double expectedA = expectedScore(beforeA, beforeB);
            double changeA = K_FACTOR * (match.team1Score() - totalMaps * expectedA);
            double afterA = beforeA + changeA;
            double afterB = beforeB - changeA;

            a.rating = afterA;
            b.rating = afterB;
            a.lastMatch = timed.at();
            b.lastMatch = timed.at();
            a.mapWins += match.team1Score();
            a.mapLosses += match.team2Score();
            b.mapWins += match.team2Score();
            b.mapLosses += match.team1Score();
            a.mapsPlayed += totalMaps;
            b.mapsPlayed += totalMaps;
            a.seriesPlayed++;
            b.seriesPlayed++;

            String nameA = clubs.get(keyA).name();
            String nameB = clubs.get(keyB).name();
            ProLeagueRepository.ProLeague matchLeague = leagues.get(match.leagueId());
            String competition = matchLeague == null ? match.leagueId() : matchLeague.name();
            history.computeIfAbsent(keyA, ignored -> new ArrayList<>()).add(new HistoryEntry(match.id(), timed.at(), nameB,
                    competition, match.leagueId(), match.team1Score() + "-" + match.team2Score(), beforeA, afterA, afterA - beforeA,
                    match.team1Score(), match.team2Score()));
            history.computeIfAbsent(keyB, ignored -> new ArrayList<>()).add(new HistoryEntry(match.id(), timed.at(), nameA,
                    competition, match.leagueId(), match.team2Score() + "-" + match.team1Score(), beforeB, afterB, afterB - beforeB,
                    match.team2Score(), match.team1Score()));
        }

        List<TeamRating> result = new ArrayList<>();
        for (Map.Entry<String, ProLeagueRepository.ProTeam> entry : clubs.entrySet()) {
            String key = entry.getKey();
            ProLeagueRepository.ProTeam team = entry.getValue();
            ProLeagueRepository.ProLeague league = leagues.get(team.leagueId());
            RatingState state = states.getOrDefault(key, new RatingState());
            double current = effectiveAt(state, now);
            long inactiveDays = state.lastMatch == null ? Long.MAX_VALUE
                    : Math.max(0, Duration.between(state.lastMatch, now).toDays());
            String status = state.lastMatch == null ? "unranked"
                    : inactiveDays >= INACTIVE_AFTER_DAYS ? "inactive" : "active";
            String competitionNames = String.join(" · ", clubCompetitions.getOrDefault(key, Set.of()));
            result.add(new TeamRating(key, team.id(), team.leagueId(), competitionNames.isBlank()
                    ? (league == null ? team.leagueId() : league.name()) : competitionNames,
                    league == null ? "" : league.region(), league == null ? "" : league.country(),
                    team.name(), team.shortName(), team.logo(), current, state.mapWins, state.mapLosses,
                    state.mapsPlayed, state.seriesPlayed,
                    state.mapsPlayed == 0 ? 0.0 : (double) state.mapWins / state.mapsPlayed,
                    state.lastMatch == null ? "" : state.lastMatch.toString(), status,
                    state.mapsPlayed >= MINIMUM_RATED_MAPS, 0));
        }
        result.sort(Comparator.comparing(TeamRating::qualified).reversed()
                .thenComparing(TeamRating::rating, Comparator.reverseOrder())
                .thenComparing(TeamRating::mapsPlayed, Comparator.reverseOrder())
                .thenComparing(TeamRating::name, String.CASE_INSENSITIVE_ORDER));
        int rank = 0;
        List<TeamRating> ranked = new ArrayList<>();
        for (TeamRating team : result) {
            int position = team.qualified() ? ++rank : 0;
            ranked.add(new TeamRating(team.teamKey(), team.teamId(), team.leagueId(), team.leagueName(), team.region(),
                    team.country(), team.name(), team.shortName(), team.logo(), team.rating(), team.mapWins(),
                    team.mapLosses(), team.mapsPlayed(), team.seriesPlayed(), team.mapWinRate(), team.lastMatch(),
                    team.status(), team.qualified(), position));
        }
        Map<String, List<HistoryEntry>> immutableHistory = new LinkedHashMap<>();
        history.forEach((key, entries) -> immutableHistory.put(key, List.copyOf(entries)));
        return new Result(now, List.copyOf(ranked), Map.copyOf(immutableHistory), ordered.size(), skipped);
    }

    private static String registrationKey(String leagueId, String season, String teamId) {
        return leagueId + "::" + season + "::" + teamId;
    }

    /** Explicit club IDs link registrations across competitions; blank IDs stay safely competition-scoped. */
    public static String teamKey(String clubId, String leagueId, String teamId) {
        return clubId == null || clubId.isBlank()
                ? leagueId + "::" + teamId
                : "club::" + clubId.trim().toLowerCase(Locale.ROOT);
    }

    public static double expectedScore(double ratingA, double ratingB) {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / RATING_SCALE));
    }

    /** Returns the rating after moving toward the 1500 baseline for the given elapsed days. */
    public static double decayRating(double rating, double elapsedDays) {
        if (elapsedDays <= 0) return rating;
        return STARTING_RATING + (rating - STARTING_RATING)
                * Math.pow(0.5, elapsedDays / DECAY_HALF_LIFE_DAYS);
    }

    private static double effectiveAt(RatingState state, Instant at) {
        if (state.lastMatch == null) return state.rating;
        double days = Math.max(0.0, Duration.between(state.lastMatch, at).toMillis() / 86_400_000.0);
        return decayRating(state.rating, days);
    }

    private static boolean eligible(ProLeagueRepository.ProMatch match) {
        String status = match.status().toLowerCase(Locale.ROOT);
        if (!(status.equals("finished") || status.equals("completed"))) return false;
        if (match.team1Id().isBlank() || match.team2Id().isBlank()
                || match.team1Id().equalsIgnoreCase("TBD") || match.team2Id().equalsIgnoreCase("TBD")
                || match.team1Id().equals(match.team2Id())) return false;
        int first = match.team1Score(), second = match.team2Score();
        if (first < 0 || second < 0 || first + second <= 0 || first + second > 7) return false;
        int bestOf = match.bestOf();
        if (bestOf > 0) {
            int maximumWins = bestOf == 2 ? 2 : (bestOf + 1) / 2;
            if (first > maximumWins || second > maximumWins || first + second > bestOf) return false;
        } else if (Math.max(first, second) > 4) {
            return false;
        }
        return true;
    }

    private static Instant matchInstant(ProLeagueRepository.ProMatch match) {
        Instant completed = parseTimestamp(match.completedTime());
        return completed != null ? completed : parseTimestamp(match.scheduledTime());
    }

    private static Instant parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        try { return Instant.parse(value); } catch (DateTimeParseException ignored) { /* try offset/local forms */ }
        try { return OffsetDateTime.parse(value).toInstant(); } catch (DateTimeParseException ignored) { /* try league local time */ }
        Matcher matcher = LOCAL_MATCH_TIME.matcher(value);
        if (!matcher.matches()) return null;
        ZoneOffset offset = TIME_ZONES.get(matcher.group(3));
        if (offset == null) return null;
        String dateTime = matcher.group(1) + " " + matcher.group(2);
        try {
            LocalDateTime local = LocalDateTime.parse(dateTime, LOCAL_MINUTES);
            return local.toInstant(offset);
        } catch (DateTimeParseException ignored) {
            try {
                LocalDateTime local = LocalDateTime.parse(dateTime, LOCAL_SECONDS);
                return local.toInstant(offset);
            } catch (DateTimeParseException alsoIgnored) {
                return null;
            }
        }
    }

    private static final class RatingState {
        private double rating = STARTING_RATING;
        private int mapWins;
        private int mapLosses;
        private int mapsPlayed;
        private int seriesPlayed;
        private Instant lastMatch;
    }

    private record TimedMatch(ProLeagueRepository.ProMatch match, Instant at) {}

    public record TeamRating(String teamKey, String teamId, String leagueId, String leagueName,
                             String region, String country, String name, String shortName, String logo,
                             double rating, int mapWins, int mapLosses, int mapsPlayed, int seriesPlayed,
                             double mapWinRate, String lastMatch, String status, boolean qualified, int rank) {}

    public record HistoryEntry(String matchId, Instant date, String opponent,
                               String leagueName, String leagueId, String score,
                               double ratingBefore, double ratingAfter, double change,
                               int mapsWon, int mapsLost) {}

    public record Result(Instant generatedAt, List<TeamRating> teams,
                         Map<String, List<HistoryEntry>> history,
                         int includedMatches, int skippedMatches) {}
}
