package jongrai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** Tiny web server (JDK only, no libraries): serves the pages in web/ and a JSON API over the draft engine. */
public class WebServer {

    private static Path home;                               // folder holding data/ and web/
    private static int port;
    private static Map<String, Hero> pool;
    private static final Map<String, Hero> heroBySlug = new HashMap<>();
    private static final Map<String, Item> itemBySlug = new HashMap<>();
    private static Draft draft;
    private static ItemRepository gear;
    private static ProLeagueRepository pro;
    private static final Map<String, String> icons = new HashMap<>(); // hero name -> icon file name
    private static final SuggestionEngine engine = new SuggestionEngine();

    /** Guards every read and write of {@link #draft}. Never held across the AI call. */
    private static final Object DRAFT_LOCK = new Object();
    /** Finished AI reads by prompt. A failed future is removed so the user can retry. */
    private static final ConcurrentHashMap<String, CompletableFuture<String>> wisdomCache = new ConcurrentHashMap<>();

    /** Server start time, shown in the page footer so a stale browser copy is obvious. */
    private static final Instant START = Instant.now();
    private static final String BUILD = java.time.LocalDateTime.now()
            .withNano(0).toString().replace('T', ' ');

    private static final Set<String> MUTATING = Set.of(
            "/api/pick", "/api/ban", "/api/lane", "/api/remove", "/api/reset", "/api/wisdom");
    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);

    /** URL path -> HTML file. Static assets are served from web/ by name. */
    private static final Map<String, String> PAGES = Map.of(
            "/home", "home.html",
            "/heroes", "hero-statistics.html",
            "/league", "pro-league.html",
            "/draft", "draft.html",
            "/items", "items.html",
            "/about", "about.html",
            "/index.html", "draft.html"); // old bookmark

    public static void main(String[] args) throws Exception {
        port = parsePort(args);
        home = resolveHome();
        AiCoach.init(home);

        pool = HeroRepository.loadCsv(home.resolve("data/heroes.csv"));
        draft = new Draft(pool);
        gear = ItemRepository.load(home.resolve("data/items.csv"), home.resolve("data/hero-builds.csv"));
        pro = ProLeagueRepository.load(home.resolve("data"));
        loadIcons(home.resolve("data/hero-icons.csv"));
        pool.values().forEach(h -> heroBySlug.put(h.slug(), h));
        gear.items().forEach(i -> itemBySlug.put(i.slug(), i));

        List<String> warnings = new ArrayList<>(HeroRepository.validate(pool));
        warnings.addAll(gear.validate(pool.keySet(), home.resolve("web/icons")));
        warnings.addAll(pro.validate(pool.keySet(), home.resolve("web/teams")));
        icons.forEach((hero, file) -> {
            if (!Files.isRegularFile(home.resolve("web/icons").resolve(file))) warnings.add("hero icon '" + file + "' for " + hero + " is missing");
        });
        warnings.forEach(w -> System.out.println("warning: " + w));

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/", WebServer::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Jongrai draft board running at http://localhost:" + port + "  (Ctrl+C to stop)");
        System.out.println("AI coach " + (AiCoach.configured() ? "ON - model " + AiCoach.model() + " via " + AiCoach.baseUrl()
                : "OFF - no key found (" + AiCoach.keySource() + ")"));
        if (!AiCoach.configured()) {
            System.out.println("  To turn it on, copy ai-config.example.txt to ai-config.txt, add your key, and restart.");
        }
    }

    private static int parsePort(String[] args) {
        try {
            int p = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
            if (p >= 1 && p <= 65535) return p;
        } catch (NumberFormatException e) {
            // falls through to the usage message
        }
        System.err.println("Usage: java -cp out jongrai.WebServer [port 1-65535]");
        System.exit(2);
        return 0;
    }

    /** -Djongrai.home, else the working directory if it holds data/ and web/, else the folder above out/. */
    private static Path resolveHome() {
        String prop = System.getProperty("jongrai.home");
        List<Path> candidates = new ArrayList<>();
        if (prop != null && !prop.isBlank()) candidates.add(Path.of(prop));
        else {
            candidates.add(Path.of(""));
            try {
                Path code = Path.of(WebServer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                if (code.getParent() != null) candidates.add(code.getParent());
            } catch (Exception e) {
                // not running from a folder or jar we can locate; the working directory is all we have
            }
        }
        return candidates.stream().map(p -> p.toAbsolutePath().normalize())
                .filter(p -> Files.isDirectory(p.resolve("data")) && Files.isDirectory(p.resolve("web")))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Cannot find the project folder (it holds data/ and web/). Run from it, or pass -Djongrai.home=<folder>."));
    }

    private static void handle(HttpExchange ex) throws IOException {
        try {
            route(ex);
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendJson(ex, 400, "{\"error\":" + Json.str(e.getMessage()) + "}");
        } catch (RuntimeException e) {
            e.printStackTrace();
            sendJson(ex, 500, "{\"error\":\"Internal error\"}");
        }
    }

    private static void route(HttpExchange ex) throws IOException {
        String host = ex.getRequestHeaders().getFirst("Host");
        if (!Set.of("localhost:" + port, "127.0.0.1:" + port, "[::1]:" + port).contains(host)) {
            send(ex, 421, "text/plain", "Misdirected request");
            return;
        }
        String path = ex.getRequestURI().getPath();
        boolean mutating = MUTATING.contains(path);
        if (!ex.getRequestMethod().equals(mutating ? "POST" : "GET")) {
            ex.getResponseHeaders().set("Allow", mutating ? "POST" : "GET");
            send(ex, 405, "text/plain", mutating ? "POST only" : "GET only");
            return;
        }
        String site = ex.getRequestHeaders().getFirst("Sec-Fetch-Site");
        if (mutating && site != null && !site.equals("same-origin")) {
            send(ex, 403, "text/plain", "Cross-site request refused");
            return;
        }

        Map<String, String> q = query(ex.getRequestURI().getRawQuery());
        if (mutating) q.putAll(query(new String(ex.getRequestBody().readNBytes(65536), StandardCharsets.UTF_8)));

        if (path.startsWith("/icons/")) { serveFile(ex, "icons", path.substring(7), true); return; }
        if (path.startsWith("/teams/")) { serveFile(ex, "teams", path.substring(7), true); return; }
        if (path.startsWith("/video/")) { serveFile(ex, "video", path.substring(7), true); return; }
        switch (path) {
            case "/" -> servePage(ex, "home.html");
            case "/home", "/heroes", "/league", "/draft", "/about", "/items", "/index.html" -> servePage(ex, PAGES.get(path));
            case "/style.css", "/app.js" -> serveFile(ex, "", path.substring(1), false);
            case "/api/meta" -> sendJson(ex, 200, "{\"patch\":" + Json.str(HeroRepository.PATCH)
                    + ",\"heroes\":" + pool.size() + ",\"items\":" + gear.items().size() + "}");
            case "/api/heroes" -> sendJson(ex, 200, heroesJson());
            case "/api/items" -> sendJson(ex, 200, itemsJson());
            case "/api/builds" -> sendJson(ex, 200, buildsJson(q.getOrDefault("hero", "")));
            case "/api/leagues" -> sendJson(ex, 200, leaguesJson());
            case "/api/teams" -> sendJson(ex, 200, teamsJson(q.getOrDefault("league", ""), q.getOrDefault("season", "")));
            case "/api/standings" -> sendJson(ex, 200, standingsJson(q.getOrDefault("league", ""), q.getOrDefault("season", "")));
            case "/api/matches" -> sendJson(ex, 200, matchesJson(q.getOrDefault("league", ""), q.getOrDefault("season", ""), q.getOrDefault("status", "")));
            case "/api/drafts" -> sendJson(ex, 200, draftsJson(q.getOrDefault("league", ""), q.getOrDefault("season", ""), q.getOrDefault("match", "")));
            case "/api/state" -> sendJson(ex, 200, locked(WebServer::stateJson));
            case "/api/pick", "/api/ban", "/api/lane", "/api/remove", "/api/reset" -> mutate(ex, path, q);
            case "/api/analysis" -> sendJson(ex, 200, locked(WebServer::analysisJson));
            case "/api/wisdom" -> sendJson(ex, 200, wisdomJson(q.getOrDefault("force", "0").equals("1"),
                    q.getOrDefault("refresh", "0").equals("1")));
            case "/api/auto-suggest" -> sendJson(ex, 200, locked(WebServer::autoSuggestJson));
            case "/api/suggest" -> { Draft.Side s = Draft.Side.parse(q.getOrDefault("side", "blue"));
                sendJson(ex, 200, locked(() -> suggestionsJson(engine.suggest(draft, s, 5)))); }
            case "/api/bans" -> { Draft.Side s = Draft.Side.parse(q.getOrDefault("side", "blue"));
                sendJson(ex, 200, locked(() -> suggestionsJson(engine.suggestBans(draft, s, 5)))); }
            default -> {
                // Per-hero pages such as /heroes/obsidia render client-side from the same file.
                if (path.startsWith("/heroes/") && heroBySlug.containsKey(path.substring(8))) servePage(ex, "hero-statistics.html");
                else if (path.startsWith("/items/") && itemBySlug.containsKey(path.substring(7))) servePage(ex, "items.html");
                else servePage(ex, 404, "404.html");
            }
        }
    }

    private static String locked(java.util.function.Supplier<String> body) {
        synchronized (DRAFT_LOCK) { return body.get(); }
    }

    /** One draft change. A client that sends v=<version> is refused with 409 when its board is out of date. */
    private static void mutate(HttpExchange ex, String path, Map<String, String> q) throws IOException {
        String hero = q.getOrDefault("hero", "");
        String body;
        synchronized (DRAFT_LOCK) {
            if (q.containsKey("v") && !q.get("v").equals(Long.toString(draft.version()))) {
                sendJson(ex, 409, "{\"error\":\"The board changed in another window.\",\"state\":" + stateJson() + "}");
                return;
            }
            if (path.equals("/api/reset")) draft.reset();
            else {
                Draft.Side side = Draft.Side.parse(q.getOrDefault("side", "blue"));
                switch (path) {
                    case "/api/pick" -> draft.pick(side, hero);
                    case "/api/ban" -> draft.ban(side, hero);
                    case "/api/lane" -> draft.setLane(side, hero, Lane.parse(q.getOrDefault("lane", "")));
                    default -> draft.remove(side, hero);
                }
            }
            body = stateJson();
        }
        sendJson(ex, 200, body);
    }

    private static String heroesJson() {
        return pool.values().stream().map(h -> String.format(Locale.ROOT,
                "{\"name\":%s,\"slug\":%s,\"roles\":%s,\"lanes\":%s,\"tier\":%s,\"winRate\":%.2f,\"banRate\":%.2f,\"icon\":%s"
                        + ",\"durability\":%d,\"offense\":%d,\"control\":%d,\"tags\":%s,\"spike\":%s,\"tip\":%s"
                        + ",\"counters\":%s,\"synergies\":%s}",
                Json.str(h.name()), Json.str(h.slug()), Json.list(h.roles().stream().map(Enum::name).toList()),
                Json.list(h.lanes().stream().map(Enum::name).toList()), Json.str(h.tier()), h.winRate(), h.banRate(),
                Json.str(icons.getOrDefault(h.name(), "")), h.durability(), h.offense(), h.control(),
                Json.list(h.tags().stream().sorted().toList()), Json.str(h.spike().name()), Json.str(h.tip()),
                Json.list(h.counters().stream().sorted().toList()), Json.list(h.synergies().stream().sorted().toList())))
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** The whole item catalog, each item carrying the heroes that core-build it and that merely use it. */
    private static String itemsJson() {
        Map<String, List<String>> cores = gear.coreUsersByItem();
        Map<String, List<String>> users = gear.usersByItem();
        return gear.items().stream().map(it -> String.format(Locale.ROOT,
                "{\"name\":%s,\"slug\":%s,\"tier\":%d,\"cost\":%d,\"stats\":%s,\"statList\":%s,\"passives\":%s,"
                        + "\"availability\":%s,\"categories\":%s,\"icon\":%s,\"cores\":%s,\"users\":%s}",
                Json.str(it.name()), Json.str(it.slug()), it.tier(), it.cost(),
                Json.str(it.stats()), Json.list(statList(it.stats())), Json.str(it.passives()), Json.str(it.availability()),
                Json.list(it.categories()), Json.str(it.icon()),
                Json.list(cores.getOrDefault(it.key(), List.of())), Json.list(users.getOrDefault(it.key(), List.of()))))
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** "Physical Attack +160, Movement Speed +5%" -> one entry per stat, for rendering as chips. */
    private static List<String> statList(String stats) {
        if (stats == null || stats.isBlank()) return List.of();
        return Arrays.stream(stats.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * The curated builds of one hero, each item carrying its own icon and price so the hero page
     * can render a full build without also loading the whole catalog. An unknown hero is an empty
     * list rather than an error, so a bad query string just shows nothing.
     */
    private static String buildsJson(String hero) {
        Hero h = hero == null ? null : pool.get(hero.trim().toLowerCase());
        if (h == null) return "[]";

        return gear.builds(h.name()).stream().map(b -> String.format(Locale.ROOT,
                "{\"order\":%d,\"name\":%s,\"lane\":%s,\"items\":%s,\"emblem\":%s,\"battleSpell\":%s,"
                        + "\"blessing\":%s,\"flex\":%s,\"totalCost\":%d}",
                b.order(), Json.str(b.name()), Json.str(b.lane()), buildItemsJson(b), Json.str(b.emblem()),
                Json.str(b.battleSpell()), Json.str(b.blessing()), Json.str(b.flex()), b.totalCost()))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String buildItemsJson(HeroBuild b) {
        return b.items().stream().map(name -> {
            Item it = gear.item(name);
            return String.format(Locale.ROOT, "{\"name\":%s,\"slug\":%s,\"icon\":%s,\"cost\":%d,\"core\":%b}",
                    Json.str(name), Json.str(it == null ? "" : it.slug()), Json.str(it == null ? "" : it.icon()),
                    it == null ? 0 : it.cost(), b.isCore(name));
        }).collect(Collectors.joining(",", "[", "]"));
    }

    private static String leaguesJson() {
        return pro.leagues().stream().map(l -> String.format(Locale.ROOT,
                "{\"id\":%s,\"name\":%s,\"region\":%s,\"country\":%s,\"season\":%s,\"year\":%d,\"status\":%s,\"logo\":%s}",
                Json.str(l.id()), Json.str(l.name()), Json.str(l.region()), Json.str(l.country()),
                Json.str(l.season()), l.year(), Json.str(l.status()), Json.str(l.logo())))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String teamsJson(String leagueId, String season) {
        if (leagueId == null || leagueId.isBlank() || season == null || season.isBlank()) return "[]";
        return pro.teamsInLeague(leagueId, season).stream().map(WebServer::teamJson).collect(Collectors.joining(",", "[", "]"));
    }

    private static String teamJson(ProLeagueRepository.ProTeam t) {
        return String.format(Locale.ROOT,
                "{\"id\":%s,\"leagueId\":%s,\"season\":%s,\"name\":%s,\"shortName\":%s,\"logo\":%s,\"founded\":%d,\"city\":%s,\"coach\":%s,\"status\":%s}",
                Json.str(t.id()), Json.str(t.leagueId()), Json.str(t.season()), Json.str(t.name()), Json.str(t.shortName()), Json.str(t.logo()),
                t.founded(), Json.str(t.city()), Json.str(t.coach()), Json.str(t.status()));
    }

    private static String standingsJson(String leagueId, String season) {
        if (leagueId == null || leagueId.isBlank() || season == null || season.isBlank()) return "[]";
        return pro.standings(leagueId, season).stream().map(s -> String.format(Locale.ROOT,
                "{\"leagueId\":%s,\"teamId\":%s,\"season\":%s,\"position\":%d,\"played\":%d,\"won\":%d,\"lost\":%d,\"draw\":%d,\"gf\":%d,\"ga\":%d,\"gd\":%d,\"points\":%d,\"streak\":%s,\"form\":%s,\"lastUpdated\":%s}",
                Json.str(s.leagueId()), Json.str(s.teamId()), Json.str(s.season()), s.position(), s.played(), s.won(), s.lost(),
                s.draw(), s.gf(), s.ga(), s.gd(), s.points(), Json.str(s.streak()), Json.str(s.form()), Json.str(s.lastUpdated())))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String matchesJson(String leagueId, String season, String status) {
        if (leagueId == null || leagueId.isBlank() || season == null || season.isBlank()) return "[]";
        return pro.matches(leagueId, season).stream()
                .filter(m -> status == null || status.isBlank() || m.status().equals(status))
                .map(m -> String.format(Locale.ROOT,
                        "{\"id\":%s,\"leagueId\":%s,\"season\":%s,\"week\":%d,\"day\":%d,\"team1Id\":%s,\"team2Id\":%s,\"team1Score\":%d,\"team2Score\":%d,\"status\":%s,\"scheduledTime\":%s,\"completedTime\":%s,\"matchType\":%s}",
                        Json.str(m.id()), Json.str(m.leagueId()), Json.str(m.season()), m.week(), m.day(),
                        Json.str(m.team1Id()), Json.str(m.team2Id()), m.team1Score(), m.team2Score(),
                        Json.str(m.status()), Json.str(m.scheduledTime()), Json.str(m.completedTime()), Json.str(m.matchType())))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String draftsJson(String leagueId, String season, String matchId) {
        if (leagueId == null || leagueId.isBlank() || season == null || season.isBlank()) return "[]";
        return pro.drafts(leagueId, season).stream()
                .filter(d -> matchId == null || matchId.isBlank() || d.matchId().equals(matchId))
                .map(d -> String.format(Locale.ROOT,
                        "{\"id\":%s,\"matchId\":%s,\"leagueId\":%s,\"season\":%s,\"gameNumber\":%d,\"blueTeamId\":%s,\"redTeamId\":%s,\"blueBans\":%s,\"redBans\":%s,\"bluePicks\":%s,\"redPicks\":%s,\"blueSide\":%b,\"winnerTeamId\":%s,\"patch\":%s,\"map\":%s,\"duration\":%d}",
                        Json.str(d.id()), Json.str(d.matchId()), Json.str(d.leagueId()), Json.str(d.season()), d.gameNumber(),
                        Json.str(d.blueTeamId()), Json.str(d.redTeamId()), Json.list(d.blueBans()), Json.list(d.redBans()),
                        Json.list(d.bluePicks()), Json.list(d.redPicks()), d.blueSide(), Json.str(d.winnerTeamId()), Json.str(d.patch()),
                        Json.str(d.map()), d.duration()))
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** Caller holds {@link #DRAFT_LOCK}. */
    private static String stateJson() {
        return "{\"version\":" + draft.version() + ",\"blue\":" + teamJson(draft.team(Draft.Side.BLUE))
                + ",\"red\":" + teamJson(draft.team(Draft.Side.RED)) + "}";
    }

    private static String teamJson(Team t) {
        String picks = t.picks().stream()
                .map(h -> "{\"name\":" + Json.str(h.name()) + ",\"lane\":" + Json.str(t.laneOf(h).name()) + "}")
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"picks\":" + picks
                + ",\"bans\":" + Json.list(t.bans().stream().map(Hero::name).toList()) + "}";
    }

    private static String suggestionsJson(List<SuggestionEngine.Suggestion> list) {
        return list.stream().map(s -> String.format(Locale.ROOT, "{\"hero\":%s,\"score\":\"%+.1f\",\"reasons\":%s}",
                Json.str(s.hero().name()), s.score(), Json.list(s.reasons())))
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** Caller holds {@link #DRAFT_LOCK}. The analysis, with team coordination folded in. */
    private static String analysisJson() {
        DraftAnalyzer analyzer = new DraftAnalyzer();
        DraftAnalyzer.Analysis a = analyzer.analyze(draft);
        DraftAnalyzer.Coordination c = analyzer.coordination(draft);
        String cats = a.categories().stream().map(x -> String.format(Locale.ROOT,
                "{\"name\":%s,\"blueShare\":%.1f,\"detail\":%s}", Json.str(x.name()), x.blueShare(), Json.str(x.detail())))
                .collect(Collectors.joining(",", "[", "]"));
        String profile = a.profile().stream().map(x -> String.format(Locale.ROOT,
                "{\"name\":%s,\"blue\":%.1f,\"red\":%.1f}", Json.str(x.name()), x.blue(), x.red()))
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"blueEdge\":" + a.blueEdge() + ",\"redEdge\":" + a.redEdge() + ",\"lead\":" + Json.str(a.lead())
                + ",\"headline\":" + Json.str(a.headline()) + ",\"categories\":" + cats
                + ",\"profile\":" + profile
                + ",\"coordination\":{\"blue\":" + sideJson(c.blue()) + ",\"red\":" + sideJson(c.red()) + "}}";
    }

    private static String sideJson(DraftAnalyzer.TeamSide t) {
        return String.format(Locale.ROOT,
                "{\"lanesCovered\":%d,\"hasTank\":%b,\"hasMarksmanOrMage\":%b,\"roleVariety\":%d,"
                + "\"synergyPairs\":%d,\"heavyControl\":%d,\"avgWinRate\":%.1f,\"spikeTiming\":%s,"
                + "\"missingLanes\":%s,\"missingRoles\":%s,\"score\":%.1f}",
                t.lanesCovered(), t.hasTank(), t.hasMarksmanOrMage(), t.roleVariety(),
                t.synergyPairs(), t.heavyControl(), t.avgWinRate(), Json.str(t.spikeTiming()),
                Json.list(t.missingLanes()), Json.list(t.missingRoles()), t.score());
    }

    /**
     * AI read on the draft. Held back until the draft is finished (unless force=1), so no tokens are
     * spent judging a half-built board. Cached per prompt, so re-rendering the page never re-bills the
     * API; refresh=1 drops the cached read and asks again. The upstream call runs outside the draft lock.
     */
    private static String wisdomJson(boolean force, boolean refresh) {
        String prompt;
        synchronized (DRAFT_LOCK) {
            if (!AiCoach.configured()) {
                return "{\"configured\":false,\"error\":\"Coach off: no API key. Copy ai-config.example.txt to ai-config.txt, add your key, then restart the server.\"}";
            }
            if (!draft.isComplete() && !force) {
                return "{\"configured\":true,\"ready\":false,\"progress\":" + Json.str(draft.team(Draft.Side.BLUE).picks().size()
                        + " vs " + draft.team(Draft.Side.RED).picks().size() + " picks") + ",\"complete\":false}";
            }
            prompt = AiCoach.prompt(draft, new DraftAnalyzer().analyze(draft));
        }
        if (refresh) wisdomCache.remove(prompt);
        if (wisdomCache.size() > 32) wisdomCache.clear(); // ponytail: crude cap; an LRU only if drafts get replayed a lot
        CompletableFuture<String> read = wisdomCache.computeIfAbsent(prompt,
                p -> CompletableFuture.supplyAsync(() -> AiCoach.verdictJson(AiCoach.judge(p))));
        try {
            return read.get();
        } catch (ExecutionException | InterruptedException e) {
            wisdomCache.remove(prompt, read);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            return "{\"configured\":true,\"ready\":true,\"error\":" + Json.str(message) + "}";
        }
    }

    /** Caller holds {@link #DRAFT_LOCK}. Up to 3 suggestions for whoever picks next, or none once the draft is done. */
    private static String autoSuggestJson() {
        Optional<Draft.Side> next = draft.nextPick();
        if (next.isEmpty()) return "{\"active\":false,\"reason\":\"Draft complete\",\"suggestions\":[]}";
        Draft.Side side = next.get();
        return "{\"active\":true,\"turn\":\"" + side.name().toLowerCase(Locale.ROOT) + "\",\"suggestions\":"
                + suggestionsJson(engine.suggest(draft, side, 3)) + "}";
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return map;
    }

    private static void loadIcons(Path file) throws IOException {
        for (Csv.Row r : Csv.read(file, 2)) {
            if (!r.text(1).isEmpty()) icons.put(r.text(0), r.text(1));
        }
    }

    private static void servePage(HttpExchange ex, String file) throws IOException {
        servePage(ex, 200, file);
    }

    /** An HTML page with the build stamp in its footer. Revalidates every time, answering 304 when unchanged. */
    private static void servePage(HttpExchange ex, int code, String file) throws IOException {
        Path path = home.resolve("web").resolve(file);
        String html = Files.readString(path);
        String stamp = "<span>build " + BUILD + "</span>";
        html = html.contains("</footer>")
                ? html.replace("</footer>", stamp + "</footer>")
                : html.replace("</body>", "<span class='build'>" + BUILD + "</span></body>");
        // The stamp changes on every restart, so a restart must also invalidate the browser's copy.
        Instant modified = Files.getLastModifiedTime(path).toInstant();
        if (modified.isBefore(START)) modified = START;
        ex.getResponseHeaders().set("Cache-Control", "no-cache, must-revalidate");
        if (code == 200 && notModified(ex, modified)) return;
        send(ex, code, "text/html; charset=utf-8", html, modified);
    }

    /**
     * A file under web/ (or web/<dir>/). Media and icons cache for a day; CSS and JS revalidate.
     * Honours If-Modified-Since (304) and a single Range (206, or 416 when out of range).
     */
    private static void serveFile(HttpExchange ex, String dir, String name, boolean longCache) throws IOException {
        Path root = home.resolve("web").resolve(dir).normalize();
        Path file = root.resolve(name).normalize();
        // Apostrophes and spaces are allowed: several asset names carry one (Item_Athena's_Shield.png).
        if (!name.matches("[A-Za-z0-9._' -]+") || name.contains("..") || !file.startsWith(root) || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain", "Not found");
            return;
        }
        long size = Files.size(file);
        Instant modified = Files.getLastModifiedTime(file).toInstant();
        ex.getResponseHeaders().set("Cache-Control", longCache ? "public, max-age=86400" : "no-cache, must-revalidate");
        ex.getResponseHeaders().set("Content-Type", contentType(name));
        ex.getResponseHeaders().set("Accept-Ranges", "bytes");
        if (notModified(ex, modified)) return;
        ex.getResponseHeaders().set("Last-Modified", HTTP_DATE.format(modified));

        long start = 0, end = size - 1;
        int code = 200;
        String range = ex.getRequestHeaders().getFirst("Range");
        if (range != null && range.startsWith("bytes=") && !range.contains(",")) {
            long[] r = parseRange(range.substring(6).trim(), size);
            if (r == null) {
                ex.getResponseHeaders().set("Content-Range", "bytes */" + size);
                ex.sendResponseHeaders(416, -1);
                ex.close();
                return;
            }
            start = r[0];
            end = r[1];
            code = 206;
            ex.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + size);
        }
        long length = end - start + 1;
        ex.sendResponseHeaders(code, length == 0 ? -1 : length);
        try (OutputStream out = ex.getResponseBody(); InputStream in = Files.newInputStream(file)) {
            in.skipNBytes(start);
            byte[] buf = new byte[65536];
            for (long left = length; left > 0; ) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) break;
                out.write(buf, 0, n);
                left -= n;
            }
        }
    }

    /** "a-b", "a-" or "-n" against a file of this size; {start, end} inclusive, or null when it cannot be served. */
    private static long[] parseRange(String spec, long size) {
        try {
            int dash = spec.indexOf('-');
            if (dash < 0) return null;
            String from = spec.substring(0, dash), to = spec.substring(dash + 1);
            long start, end;
            if (from.isEmpty()) {                       // suffix: the last n bytes
                long n = Long.parseLong(to);
                if (n <= 0) return null;
                start = Math.max(0, size - n);
                end = size - 1;
            } else {
                start = Long.parseLong(from);
                end = to.isEmpty() ? size - 1 : Math.min(Long.parseLong(to), size - 1);
            }
            return start < 0 || start >= size || end < start ? null : new long[]{start, end};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Answers 304 and returns true when the browser's copy is as new as {@code modified}. */
    private static boolean notModified(HttpExchange ex, Instant modified) throws IOException {
        String since = ex.getRequestHeaders().getFirst("If-Modified-Since");
        if (since == null) return false;
        try {
            if (modified.getEpochSecond() > HTTP_DATE.parse(since, Instant::from).getEpochSecond()) return false;
        } catch (DateTimeException e) {
            return false;
        }
        ex.getResponseHeaders().set("Last-Modified", HTTP_DATE.format(modified));
        ex.sendResponseHeaders(304, -1);
        ex.close();
        return true;
    }

    private static String contentType(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "application/javascript; charset=utf-8";
        return "application/octet-stream";
    }

    private static void sendJson(HttpExchange ex, int code, String body) throws IOException {
        send(ex, code, "application/json; charset=utf-8", body);
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        send(ex, code, type, body, null);
    }

    private static void send(HttpExchange ex, int code, String type, String body, Instant modified) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        if (modified != null) ex.getResponseHeaders().set("Last-Modified", HTTP_DATE.format(modified));
        ex.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
