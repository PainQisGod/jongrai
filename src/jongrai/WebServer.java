package jongrai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Tiny web server (JDK only, no libraries): serves web/index.html and a JSON API over the draft engine. */
public class WebServer {

    private static Map<String, Hero> pool;
    private static Draft draft;
    private static ItemRepository gear;
    private static ProLeagueRepository pro;
    private static final Set<String> iconFiles = new HashSet<>(); // every icon basename in web/icons

    /** Server start time, shown in the page footer so a stale browser copy is obvious. */
    private static final String BUILD = java.time.LocalDateTime.now()
            .withNano(0).toString().replace('T', ' ');
    private static final Map<String, String> icons = new HashMap<>(); // hero name -> icon file name
    private static final SuggestionEngine engine = new SuggestionEngine();

    public static void main(String[] args) throws Exception {
        pool = HeroRepository.loadCsv(Path.of("data/heroes.csv"));
        draft = new Draft(pool);
        gear = ItemRepository.load(Path.of("data/items.csv"), Path.of("data/hero-builds.csv"));
        pro = ProLeagueRepository.load(Path.of("data"));
        loadIcons(Path.of("data/hero-icons.csv"));
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        for (String w : gear.validate(heroKeys())) System.out.println("gear warning: " + w);

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/", WebServer::handle);
        server.start();
        System.out.println("Jongrai draft board running at http://localhost:" + port + "  (Ctrl+C to stop)");
        System.out.println("AI coach " + (AiCoach.configured() ? "ON - model " + AiCoach.model() + " via " + AiCoach.baseUrl()
                : "OFF - no key found (" + AiCoach.keySource() + ")"));
        if (!AiCoach.configured()) {
            System.out.println("  To turn it on, create ai-config.txt next to this project with:");
            System.out.println("    JONGRAI_AI_KEY=sk-your-key");
            System.out.println("    JONGRAI_AI_MODEL=gpt-4o-mini");
            System.out.println("    JONGRAI_AI_BASE_URL=https://api.openai.com/v1");
        }
    }

    private static synchronized void handle(HttpExchange ex) throws IOException {
        Map<String, String> q = query(ex.getRequestURI().getRawQuery());
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/icons/")) { serveIcon(ex, path.substring(7)); return; }
            if (path.startsWith("/video/")) { serveVideo(ex, path.substring(7)); return; }
            switch (path) {
                case "/" -> send(ex, 200, "text/html; charset=utf-8", page("home.html"));
                case "/home", "/heroes", "/league", "/draft", "/about", "/items", "/index.html" ->
                        send(ex, 200, "text/html; charset=utf-8", page(PAGES.get(path)));
                case "/style.css" -> send(ex, 200, "text/css; charset=utf-8", asset("style.css"));
                case "/api/heroes" -> send(ex, 200, "application/json", heroesJson());
                case "/api/items" -> send(ex, 200, "application/json", itemsJson());
                case "/api/builds" -> send(ex, 200, "application/json", buildsJson(q.getOrDefault("hero", "")));
                case "/api/leagues" -> send(ex, 200, "application/json", leaguesJson());
                case "/api/teams" -> send(ex, 200, "application/json", teamsJson(q.getOrDefault("league", "")));
                case "/api/standings" -> send(ex, 200, "application/json", standingsJson(q.getOrDefault("league", ""), q.getOrDefault("season", "")));
                case "/api/matches" -> send(ex, 200, "application/json", matchesJson(q.getOrDefault("league", ""), q.getOrDefault("season", ""), q.getOrDefault("status", "")));
                case "/api/drafts" -> send(ex, 200, "application/json", draftsJson(q.getOrDefault("league", ""), q.getOrDefault("season", ""), q.getOrDefault("match", "")));
                case "/api/state" -> send(ex, 200, "application/json", stateJson());
                case "/api/pick" -> { draft.pick(side(q), q.getOrDefault("hero", "")); send(ex, 200, "application/json", stateJson()); }
                case "/api/ban" -> { draft.ban(side(q), q.getOrDefault("hero", "")); send(ex, 200, "application/json", stateJson()); }
                case "/api/lane" -> { draft.setLane(side(q), q.getOrDefault("hero", ""), Lane.parse(q.getOrDefault("lane", ""))); send(ex, 200, "application/json", stateJson()); }
                case "/api/remove" -> { draft.remove(side(q), q.getOrDefault("hero", "")); send(ex, 200, "application/json", stateJson()); }
                case "/api/analysis" -> send(ex, 200, "application/json", analysisJson());
                case "/api/coordination" -> send(ex, 200, "application/json", coordinationJson());
                case "/api/wisdom" -> send(ex, 200, "application/json", wisdomJson(q.getOrDefault("force", "0").equals("1")));
                case "/api/aidebug" -> send(ex, 200, "application/json", AiCoach.debugJson());
                case "/api/auto-suggest" -> send(ex, 200, "application/json", autoSuggestJson(q));
                case "/api/reset" -> { draft = new Draft(pool); send(ex, 200, "application/json", stateJson()); }
                case "/api/suggest" -> send(ex, 200, "application/json", suggestionsJson(engine.suggest(draft, side(q), 5)));
                case "/api/bans" -> send(ex, 200, "application/json", suggestionsJson(engine.suggestBans(draft, side(q), 5)));
                default -> {
                    if (path.equals("/style.css")) send(ex, 200, "text/css; charset=utf-8", asset("style.css"));
                    // Per-hero pages such as /heroes/obsidia render client-side from this same file.
                    else if (path.startsWith("/heroes/")) send(ex, 200, "text/html; charset=utf-8", page("hero-statistics.html"));
                    // Likewise /items/war-axe.
                    else if (path.startsWith("/items/")) send(ex, 200, "text/html; charset=utf-8", page("items.html"));
                    else send(ex, 404, "text/html; charset=utf-8", page("404.html"));
                }
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            send(ex, 400, "application/json", "{\"error\":" + str(e.getMessage()) + "}");
        }
    }

    /** URL path -> HTML file. Static assets are served from web/ by name. */
    private static final Map<String, String> PAGES = Map.of(
            "/", "home.html",
            "/home", "home.html",
            "/heroes", "hero-statistics.html",
            "/league", "pro-league.html",
            "/draft", "draft.html",
            "/items", "items.html",
            "/about", "about.html",
            // old bookmark
            "/index.html", "draft.html");

    private static String page(String file) throws IOException {
        // Stamp the build into the footer so "what version am I looking at" is always answerable.
        String html = Files.readString(Path.of("web", file));
        String stamp = "<span>build " + BUILD + "</span>";
        return html.contains("</footer>")
                ? html.replace("</footer>", stamp + "</footer>")
                : html.replace("</body>", "<span class='build'>" + BUILD + "</span></body>");
    }

    private static String asset(String name) throws IOException {
        return Files.readString(Path.of("web", name));
    }

    private static Draft.Side side(Map<String, String> q) {
        return Draft.Side.valueOf(q.getOrDefault("side", "blue").toUpperCase());
    }

    private static String heroesJson() {
        return pool.values().stream().map(h -> String.format(Locale.ROOT,
                "{\"name\":%s,\"roles\":%s,\"lanes\":%s,\"tier\":%s,\"winRate\":%.2f,\"banRate\":%.2f,\"icon\":%s"
                        + ",\"durability\":%d,\"offense\":%d,\"control\":%d,\"tags\":%s,\"spike\":%s"
                        + ",\"counters\":%s,\"synergies\":%s}",
                str(h.name()), strList(h.roles().stream().map(Enum::name).toList()),
                strList(h.lanes().stream().map(Enum::name).toList()), str(h.tier()), h.winRate(), h.banRate(),
                str(icons.getOrDefault(h.name(), "")), h.durability(), h.offense(), h.control(),
                strList(h.tags().stream().sorted().toList()), str(h.spike()),
                strList(h.counters().stream().sorted().toList()), strList(h.synergies().stream().sorted().toList())))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static Set<String> heroKeys() {
        return pool.keySet();
    }

    /** The whole item catalog, each item carrying the heroes that core-build it and that merely use it. */
    private static String itemsJson() {
        Map<String, List<String>> cores = gear.coreUsersByItem();
        Map<String, List<String>> users = gear.usersByItem();
        return gear.items().stream().map(it -> String.format(Locale.ROOT,
                "{\"name\":%s,\"slug\":%s,\"tier\":%d,\"cost\":%d,\"stats\":%s,\"statList\":%s,\"passives\":%s,"
                        + "\"availability\":%s,\"categories\":%s,\"icon\":%s,\"cores\":%s,\"users\":%s}",
                str(it.name()), str(it.slug()), it.tier(), it.cost(),
                str(it.stats()), strList(statList(it.stats())), str(it.passives()), str(it.availability()),
                strList(it.categories()), str(it.icon()),
                strList(cores.getOrDefault(it.key(), List.of())), strList(users.getOrDefault(it.key(), List.of()))))
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

        List<HeroBuild> builds = gear.builds(h.name());
        return builds.stream().map(b -> String.format(Locale.ROOT,
                "{\"order\":%d,\"name\":%s,\"lane\":%s,\"items\":%s,\"emblem\":%s,\"battleSpell\":%s,"
                        + "\"blessing\":%s,\"flex\":%s,\"totalCost\":%d}",
                b.order(), str(b.name()), str(b.lane()), buildItemsJson(b), str(b.emblem()),
                str(b.battleSpell()), str(b.blessing()), str(b.flex()), b.totalCost()))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String buildItemsJson(HeroBuild b) {
        return b.items().stream().map(name -> {
            Item it = gear.item(name);
            return String.format(Locale.ROOT, "{\"name\":%s,\"icon\":%s,\"cost\":%d,\"core\":%s}",
                    str(name), str(it == null ? "" : it.icon()), it == null ? 0 : it.cost(), b.isCore(name));
        }).collect(Collectors.joining(",", "[", "]"));
    }

    private static String leaguesJson() {
        return pro.leagues().stream().map(l -> String.format(Locale.ROOT,
                "{\"id\":%s,\"name\":%s,\"region\":%s,\"country\":%s,\"season\":%s,\"year\":%d,\"status\":%s,\"logo\":%s}",
                str(l.id()), str(l.name()), str(l.region()), str(l.country()),
                str(l.season()), l.year(), str(l.status()), str(l.logo())))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String teamsJson(String leagueId) {
        if (leagueId == null || leagueId.isBlank()) {
            return pro.teams().stream().map(t -> teamJson(t)).collect(Collectors.joining(",", "[", "]"));
        }
        return pro.teamsInLeague(leagueId).stream().map(t -> teamJson(t)).collect(Collectors.joining(",", "[", "]"));
    }

    private static String teamJson(ProLeagueRepository.ProTeam t) {
        return String.format(Locale.ROOT,
                "{\"id\":%s,\"leagueId\":%s,\"name\":%s,\"shortName\":%s,\"logo\":%s,\"founded\":%d,\"city\":%s,\"coach\":%s,\"status\":%s}",
                str(t.id()), str(t.leagueId()), str(t.name()), str(t.shortName()), str(t.logo()),
                t.founded(), str(t.city()), str(t.coach()), str(t.status()));
    }

    private static String standingsJson(String leagueId, String season) {
        if (leagueId == null || leagueId.isBlank() || season == null || season.isBlank()) return "[]";
        return pro.standings(leagueId, season).stream().map(s -> String.format(Locale.ROOT,
                "{\"leagueId\":%s,\"teamId\":%s,\"season\":%s,\"position\":%d,\"played\":%d,\"won\":%d,\"lost\":%d,\"draw\":%d,\"gf\":%d,\"ga\":%d,\"gd\":%d,\"points\":%d,\"streak\":%s,\"form\":%s,\"lastUpdated\":%s}",
                str(s.leagueId()), str(s.teamId()), str(s.season()), s.position(), s.played(), s.won(), s.lost(),
                s.draw(), s.gf(), s.ga(), s.gd(), s.points(), str(s.streak()), str(s.form()), str(s.lastUpdated())))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String matchesJson(String leagueId, String season, String status) {
        if (leagueId == null || leagueId.isBlank() || season == null || season.isBlank()) return "[]";
        return pro.matches(leagueId, season).stream()
                .filter(m -> status == null || status.isBlank() || m.status().equals(status))
                .map(m -> String.format(Locale.ROOT,
                        "{\"id\":%s,\"leagueId\":%s,\"season\":%s,\"week\":%d,\"day\":%d,\"team1Id\":%s,\"team2Id\":%s,\"team1Score\":%d,\"team2Score\":%d,\"status\":%s,\"scheduledTime\":%s,\"completedTime\":%s,\"matchType\":%s}",
                        str(m.id()), str(m.leagueId()), str(m.season()), m.week(), m.day(),
                        str(m.team1Id()), str(m.team2Id()), m.team1Score(), m.team2Score(),
                        str(m.status()), str(m.scheduledTime()), str(m.completedTime()), str(m.matchType())))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String draftsJson(String leagueId, String season, String matchId) {
        if (leagueId == null || leagueId.isBlank() || season == null || season.isBlank()) return "[]";
        return pro.drafts(leagueId, season).stream()
                .filter(d -> matchId == null || matchId.isBlank() || d.matchId().equals(matchId))
                .map(d -> String.format(Locale.ROOT,
                        "{\"id\":%s,\"matchId\":%s,\"leagueId\":%s,\"season\":%s,\"gameNumber\":%d,\"blueTeamId\":%s,\"redTeamId\":%s,\"blueBans\":%s,\"redBans\":%s,\"bluePicks\":%s,\"redPicks\":%s,\"blueSide\":%b,\"winnerTeamId\":%s,\"patch\":%s}",
                        str(d.id()), str(d.matchId()), str(d.leagueId()), str(d.season()), d.gameNumber(),
                        str(d.blueTeamId()), str(d.redTeamId()), strList(d.blueBans()), strList(d.redBans()),
                        strList(d.bluePicks()), strList(d.redPicks()), d.blueSide(), str(d.winnerTeamId()), str(d.patch())))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String stateJson() {
        return "{\"blue\":" + teamJson(draft.team(Draft.Side.BLUE)) + ",\"red\":" + teamJson(draft.team(Draft.Side.RED)) + "}";
    }

    private static String teamJson(Team t) {
        String picks = t.picks().stream()
                .map(h -> "{\"name\":" + str(h.name()) + ",\"lane\":" + str(t.laneOf(h).name()) + "}")
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"picks\":" + picks
                + ",\"bans\":" + strList(t.bans().stream().map(Hero::name).toList()) + "}";
    }

    private static String suggestionsJson(List<SuggestionEngine.Suggestion> list) {
        return list.stream().map(s -> String.format(Locale.ROOT, "{\"hero\":%s,\"score\":\"%+.1f\",\"reasons\":%s}",
                str(s.hero().name()), s.score(), strList(s.reasons())))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String analysisJson() {
        DraftAnalyzer.Analysis a = new DraftAnalyzer().analyze(draft);
        String cats = a.categories().stream().map(c -> String.format(Locale.ROOT,
                "{\"name\":%s,\"blueShare\":%.1f,\"detail\":%s}", str(c.name()), c.blueShare(), str(c.detail())))
                .collect(Collectors.joining(",", "[", "]"));
        String profile = a.profile().stream().map(x -> String.format(Locale.ROOT,
                "{\"name\":%s,\"blue\":%.1f,\"red\":%.1f}", str(x.name()), x.blue(), x.red()))
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"blueEdge\":" + a.blueEdge() + ",\"redEdge\":" + a.redEdge() + ",\"lead\":" + str(a.lead())
                + ",\"headline\":" + str(a.headline()) + ",\"categories\":" + cats
                + ",\"profile\":" + profile + "}";
    }

    private static String coordinationJson() {
        DraftAnalyzer.Coordination c = new DraftAnalyzer().coordination(draft);
        return String.format(Locale.ROOT,
                "{\"blue\":%s,\"red\":%s}",
                sideJson(c.blue()), sideJson(c.red()));
    }

    private static String sideJson(DraftAnalyzer.TeamSide t) {
        return String.format(Locale.ROOT,
                "{\"lanesCovered\":%d,\"hasTank\":%b,\"hasMarksmanOrMage\":%b,\"roleVariety\":%d,"
                + "\"synergyPairs\":%d,\"heavyControl\":%d,\"avgWinRate\":%.1f,\"spikeTiming\":%s,"
                + "\"missingLanes\":%s,\"missingRoles\":%s,\"score\":%.1f}",
                t.lanesCovered(), t.hasTank(), t.hasMarksmanOrMage(), t.roleVariety(),
                t.synergyPairs(), t.heavyControl(), t.avgWinRate(), str(t.spikeTiming()),
                strList(t.missingLanes()), strList(t.missingRoles()), t.score());
    }

    private static String lastPrompt = "", lastWisdom = "";

    /** A draft is finished once both sides have all five picks. */
    private static boolean draftComplete() {
        return draft.team(Draft.Side.BLUE).picks().size() >= Team.MAX_PICKS
                && draft.team(Draft.Side.RED).picks().size() >= Team.MAX_PICKS;
    }

    /**
     * AI read on the draft. Held back until the draft is finished, so no tokens are spent judging a
     * half-built board. Cached per prompt, so re-rendering the page never re-bills the API.
     */
    private static String wisdomJson(boolean force) {
        DraftAnalyzer.Analysis a = new DraftAnalyzer().analyze(draft);
        int bp = draft.team(Draft.Side.BLUE).picks().size(), rp = draft.team(Draft.Side.RED).picks().size();
        String progress = "{\"ready\":false,\"progress\":" + str(bp + " vs " + rp + " picks")
                + ",\"complete\":" + draftComplete() + "}";

        if (!AiCoach.configured()) {
            return "{\"configured\":false,\"error\":\"Coach off: no API key. Create ai-config.txt in the project folder with a line JONGRAI_AI_KEY=... then restart the server.\"}";
        }
        if (!draftComplete() && !force) {
            return "{\"configured\":true," + progress.substring(1);
        }

        String prompt = AiCoach.prompt(draft, a);
        if (!prompt.equals(lastPrompt) || lastWisdom.isEmpty()) {
            try {
                lastWisdom = AiCoach.verdictJson(AiCoach.judge(draft, a));
                lastPrompt = prompt;
            } catch (RuntimeException e) {
                return "{\"configured\":true,\"ready\":true,\"error\":" + str(e.getMessage()) + "}";
            }
        }
        return lastWisdom;
    }

    /** Returns 3 hero suggestions for the side whose turn it is to pick, or empty if not pick turn. */
    private static String autoSuggestJson(Map<String, String> q) {
        // Determine if it's this side's pick turn
        // Pick order: Blue1, Red1-2, Blue2-3, Red3-4, Blue4-5, Red5
        int bp = draft.team(Draft.Side.BLUE).picks().size();
        int rp = draft.team(Draft.Side.RED).picks().size();
        int totalPicks = bp + rp;

        Draft.Side pickTurn;
        if (totalPicks == 0) pickTurn = Draft.Side.BLUE;
        else if (totalPicks <= 2) pickTurn = Draft.Side.RED;
        else if (totalPicks <= 4) pickTurn = Draft.Side.BLUE;
        else if (totalPicks <= 6) pickTurn = Draft.Side.RED;
        else if (totalPicks <= 8) pickTurn = Draft.Side.BLUE;
        else if (totalPicks <= 9) pickTurn = Draft.Side.RED;
        else pickTurn = null; // draft complete

        if (pickTurn == null) return "{\"active\":false,\"reason\":\"Draft complete\",\"suggestions\":[]}";
        Draft.Side side = pickTurn;
        Team team = draft.team(side);
        if (team.picks().size() >= Team.MAX_PICKS) {
            return "{\"active\":false,\"reason\":\"Team already has 5 picks\",\"suggestions\":[]}";
        }

        List<SuggestionEngine.Suggestion> suggestions = engine.suggest(draft, side, 3);
        String json = suggestions.stream().map(s -> String.format(Locale.ROOT,
                "{\"hero\":%s,\"score\":\"%+.1f\",\"reasons\":%s}",
                str(s.hero().name()), s.score(), strList(s.reasons())))
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"active\":true,\"turn\":\"" + side.name().toLowerCase() + "\",\"suggestions\":" + json + "}";
    }

    private static String strList(List<String> items) {
        return items.stream().map(WebServer::str).collect(Collectors.joining(",", "[", "]"));
    }

    private static String str(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null) return map;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return map;
    }

    private static void loadIcons(Path file) throws IOException {
        if (!Files.exists(file)) return;
        for (String line : Files.readAllLines(file)) {
            if (line.startsWith("\uFEFF")) line = line.substring(1);
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("name,")) continue;
            String[] p = line.split(",", 2);
            if (p.length == 2 && !p[1].isBlank()) icons.put(p[0].trim(), p[1].trim());
        }
    }

    private static void serveIcon(HttpExchange ex, String name) throws IOException {
        Path file = Path.of("web", "icons", name);
        // Apostrophes and spaces are allowed: several asset names carry one (Item_Athena's_Shield.png, season 42.jpg).
        if (!name.matches("[A-Za-z0-9._' -]+") || name.contains("..") || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain", "No such icon");
            return;
        }
        String n = name.toLowerCase();
        String type = n.endsWith(".png") ? "image/png" : n.endsWith(".webp") ? "image/webp"
                : n.endsWith(".svg") ? "image/svg+xml" : n.endsWith(".gif") ? "image/gif"
                : (n.endsWith(".jpg") || n.endsWith(".jpeg")) ? "image/jpeg" : "application/octet-stream";
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        sendBytes(ex, 200, type, Files.readAllBytes(file));
    }

    /** Serves the small, local hero clips used by the optional MPL-style draft presentation. */
    private static void serveVideo(HttpExchange ex, String name) throws IOException {
        Path file = Path.of("web", "video", name);
        if (!name.matches("[A-Za-z0-9._' -]+") || name.contains("..")
                || !name.toLowerCase().endsWith(".mp4") || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain", "No such video");
            return;
        }
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        sendBytes(ex, 200, "video/mp4", Files.readAllBytes(file));
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        // Pages and CSS change without a recompile, so never let a browser hold a stale copy.
        if (type.startsWith("text/html") || type.startsWith("text/css") || type.startsWith("application/javascript")) {
            ex.getResponseHeaders().set("Cache-Control", "no-cache, must-revalidate");
        }
        sendBytes(ex, code, type, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(HttpExchange ex, int code, String type, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
