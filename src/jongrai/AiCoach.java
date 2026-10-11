package jongrai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Asks an OpenAI-compatible chat completions API to judge the finished draft.
 * Configure with env vars (see {@link #configured()}):
 *   JONGRAI_AI_KEY     API key (required)
 *   JONGRAI_AI_BASE_URL  default https://api.openai.com/v1
 *   JONGRAI_AI_MODEL     default gpt-4o-mini
 * Any OpenAI-compatible endpoint works: OpenAI, Groq, OpenRouter, Together, Ollama, ...
 */
public class AiCoach {

    /** verdict = overall read, blue / red = per-side comment, closing = one tactical instruction. */
    public record Verdict(String verdict, String blue, String red, String closing) {}

    private static final Path CONFIG_FILE = locateConfig();
    private static final String BASE = config("JONGRAI_AI_BASE_URL", "https://api.openai.com/v1");
    private static final String MODEL = config("JONGRAI_AI_MODEL", "gpt-4o-mini");
    private static final double TEMPERATURE = configDouble("JONGRAI_AI_TEMPERATURE", 0.6);
    private static final int TIMEOUT_SECONDS = (int) configDouble("JONGRAI_AI_TIMEOUT", 90);

    /**
     * Finds ai-config.txt: next to the running process first, then next to the code (out/), then in
     * the parent of out/. Launching from a different folder (IDE run config, another cwd) must still work.
     */
    private static Path locateConfig() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("ai-config.txt").toAbsolutePath());
        Path here = codeDir();
        if (here != null) {
            candidates.add(here.resolve("ai-config.txt"));
            Path parent = here.getParent();
            if (parent != null) candidates.add(parent.resolve("ai-config.txt"));
        }
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(candidates.get(0));
    }

    /** Folder holding this class file, i.e. the out/ directory. Null if it came from a jar. */
    private static Path codeDir() {
        try {
            return Path.of(AiCoach.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            return null;
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    /** The prompt the current call is judging, kept for {@link #requestBody(boolean)}. */
    private static String lastPrompt = "";

    /**
     * Reads a setting from the environment first, then from ai-config.txt in the project folder.
     * Returns "" for the key when it is not set anywhere.
     */
    static String config(String name, String fallback) {
        String fromEnv = System.getenv(name);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();
        String fromFile = readConfigFile().get(name);
        return fromFile != null ? fromFile : fallback;
    }

    private static Map<String, String> readConfigFile() {
        try {
            if (!Files.isRegularFile(CONFIG_FILE)) return Map.of();
            Map<String, String> map = new HashMap<>();
            for (String line : Files.readAllLines(CONFIG_FILE)) {
                line = line.stripLeading().replace("\uFEFF", "").trim(); // editors may add a BOM
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
            return map;
        } catch (IOException e) {
            return Map.of();
        }
    }

    private static String key() {
        return config("JONGRAI_AI_KEY", "");
    }

    public static boolean configured() {
        return !key().isEmpty();
    }

    /** Where the API key was found, for the startup banner. Never prints the key itself. */
    public static String keySource() {
        if (System.getenv("JONGRAI_AI_KEY") != null && !System.getenv("JONGRAI_AI_KEY").isBlank()) {
            return "environment variable JONGRAI_AI_KEY";
        }
        String v = readConfigFile().get("JONGRAI_AI_KEY");
        if (v != null && !v.isBlank()) return CONFIG_FILE + " (JONGRAI_AI_KEY)";
        return "nowhere - no JONGRAI_AI_KEY in the environment and no key in " + CONFIG_FILE;
    }

    /** Diagnostics for debugging a setup problem. Never includes the key itself. */
    public static String debugJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"configured\":").append(configured());
        sb.append(",\"model\":").append(quote(MODEL));
        sb.append(",\"baseUrl\":").append(quote(BASE));
        sb.append(",\"configFile\":").append(quote(CONFIG_FILE.toString()));
        sb.append(",\"configFileExists\":").append(Files.isRegularFile(CONFIG_FILE));
        sb.append(",\"workingDir\":").append(quote(Path.of("").toAbsolutePath().toString()));
        sb.append(",\"envKeyPresent\":").append(System.getenv("JONGRAI_AI_KEY") != null && !System.getenv("JONGRAI_AI_KEY").isBlank());
        sb.append(",\"fileKeyPresent\":").append(readConfigFile().getOrDefault("JONGRAI_AI_KEY", "").matches("\\S+"));
        sb.append(",\"fileKeys\":").append(quote(String.join(",", new java.util.TreeSet<>(readConfigFile().keySet()))));
        sb.append(",\"keySource\":").append(quote(keySource()));
        return sb.append('}').toString();
    }

    /** Public config for the UI, so the board can tell the user how to turn the coach on. */
    public static String settingsJson() {
        return "{\"configured\":" + configured() + ",\"model\":" + quote(MODEL)
                + ",\"baseUrl\":" + quote(BASE) + "}";
    }

    public static String model() { return MODEL; }

    public static String baseUrl() { return BASE; }

    /**
     * Builds the judge prompt for the current draft. Returns the exact prompt text,
     * which doubles as the cache signature for a finished draft.
     */
    public static String prompt(Draft draft, DraftAnalyzer.Analysis a) {
        StringBuilder sb = new StringBuilder();
        sb.append("Draft state (Mobile Legends: Bang Bang patch 2.2.16, ranked draft of 5 picks + 5 bans per side).\n\n");
        sb.append("BLUE SIDE\n").append(side(draft.team(Draft.Side.BLUE), draft.team(Draft.Side.RED))).append("\n\n");
        sb.append("RED SIDE\n").append(side(draft.team(Draft.Side.RED), draft.team(Draft.Side.BLUE))).append("\n\n");
        sb.append("ENGINE READOUT (rule-based, use it as evidence but judge the draft yourself)\n");
        sb.append("Overall draft edge: BLUE ").append(a.blueEdge()).append(" / RED ").append(a.redEdge())
                .append(" (").append(a.headline()).append(")\n");
        for (DraftAnalyzer.Category c : a.categories()) {
            sb.append("- ").append(c.name()).append(": blue share ").append(String.format(Locale.ROOT, "%.0f", c.blueShare()))
                    .append("% - ").append(c.detail()).append('\n');
        }
        sb.append("\nTactical profile (0-10, Blue vs Red): ");
        sb.append(a.profile().stream()
                .map(x -> x.name() + " " + String.format(Locale.ROOT, "%.1f", x.blue()) + "/" + String.format(Locale.ROOT, "%.1f", x.red()))
                .collect(Collectors.joining(", ")));
        sb.append("\n\nAvailable picks still open: ");
        sb.append(draft.availableHeroes().isEmpty() ? "none"
                : draft.availableHeroes().stream().limit(25).map(Hero::name).collect(Collectors.joining(", ")));
        return sb.toString();
    }

    private static String side(Team t, Team enemy) {
        StringBuilder sb = new StringBuilder();
        if (t.picks().isEmpty()) sb.append("(no picks yet)\n");
        for (Hero h : t.picks()) {
            sb.append("- ").append(h.name())
                    .append(" | ").append(h.roles().stream().map(Enum::name).collect(Collectors.joining("/")))
                    .append(" | lane ").append(t.laneOf(h).name())
                    .append(h.hasLane(t.laneOf(h)) && t.laneOf(h) != h.lane() ? " (flex, main " + h.lane().name() + ")" : "")
                    .append(" | tier ").append(h.tier())
                    .append(" | win ").append(String.format(Locale.ROOT, "%.1f%%", h.winRate()))
                    .append(String.format(Locale.ROOT, " | ban %.1f%%", h.banRate()))
                    .append(" | power spike ").append(h.spike())
                    .append(" | ratings dur/off/ctl ").append(h.durability()).append('/').append(h.offense()).append('/').append(h.control())
                    .append(" | tags ").append(h.tags().isEmpty() ? "-" : String.join(",", h.tags()));
            List<String> vs = enemy.picks().stream().filter(h::counters).map(Hero::name).toList();
            if (!vs.isEmpty()) sb.append(" | counters on this draft: ").append(String.join(", ", vs));
            List<String> sync = enemy.picks().stream().filter(h::synergizesWith).map(Hero::name).toList();
            if (!sync.isEmpty()) sb.append(" | has synergy with: ").append(String.join(", ", sync));
            sb.append('\n');
        }
        sb.append("Bans: ").append(t.bans().isEmpty() ? "-" : t.bans().stream().map(Hero::name).collect(Collectors.joining(", "))).append('\n');
        return sb.toString();
    }

    private static final String SYSTEM = """
            You are Jongrai, an honest and sharp MLBB (Mobile Legends: Bang Bang) draft coach.
            You judge a completed draft and give a short, concrete read on BOTH sides.
            Everything you are shown is from Mobile Legends patch 2.2.16 - the roster, tiers and win rates on
            screen are patch 2.2.16 data. Judge the draft as it exists in patch 2.2.16 and never bring in
            heroes, reworks or balance changes from other patches. Mention the patch once if it matters.
            Rules:
            - Judge the draft on screen, not the players. Never say a team "should win"; talk about draft strengths, gaps, win conditions and how each side closes the game.
            - Use the engine readout as supporting evidence only. If it disagrees with your read, trust your read and say why.
            - Be specific: name heroes, lanes and matchups. No generic advice, no filler, no restating the score.
            - Plain prose. No markdown, no bullet characters, no headings.
            Reply with JSON only, exactly this shape:
            {"verdict":"2-3 sentences on the draft as a whole","blue":"3-5 sentences on the Blue side: strengths, gaps, win condition","red":"3-5 sentences on the Red side: strengths, gaps, win condition","closing":"1-2 sentences: the single most important thing to watch or execute"}
            """;

    /** Calls the model and parses its JSON reply. Throws IllegalStateException on any failure. */
    public static Verdict judge(Draft draft, DraftAnalyzer.Analysis a) {
        if (!configured()) throw new IllegalStateException("No API key found. Put JONGRAI_AI_KEY=sk-... in " + CONFIG_FILE + " (or set it as an environment variable) and restart the server.");
        lastPrompt = prompt(draft, a);

        String raw = "";
        // Flavors, most preferred first: some OpenAI-compatible servers reject response_format or
        // DeepSeek's thinking switch, so fall back rather than fail.
        boolean[][] flavors = {{true, true}, {false, true}, {false, false}};
        for (boolean[] flavor : flavors) {
            raw = post(requestBody(flavor[0], flavor[1]));
            if (!raw.startsWith("!HTTP ")) break;
            String code = raw.split(" ", 3)[1];
            boolean badRequest = code.equals("400") || code.equals("404") || code.equals("422") || code.equals("501");
            if (!badRequest) break;
        }
        if (raw.startsWith("!HTTP ")) {
            String[] parts = raw.split(" ", 3);
            throw new IllegalStateException("AI API returned " + parts[1] + ": " + trim(parts.length > 2 ? parts[2] : ""));
        }

        String content = field(raw, "content");
        if (content == null) throw new IllegalStateException("Could not read the AI reply: " + trim(raw));
        String verdict = field(content, "verdict");
        String blue = field(content, "blue");
        String red = field(content, "red");
        String closing = field(content, "closing");
        if (verdict == null || blue == null || red == null) {
            throw new IllegalStateException("AI reply was not in the expected shape: " + trim(content));
        }
        return new Verdict(clean(verdict), clean(blue), clean(red), closing == null ? "" : clean(closing));
    }

    private static String requestBody(boolean jsonMode, boolean noThinking) {
        return "{\"model\":" + quote(MODEL)
                + ",\"temperature\":" + TEMPERATURE
                // DeepSeek V4 thinks by default, which is slow and pointless for a draft read.
                + (noThinking ? ",\"thinking\":{\"type\":\"disabled\"}" : "")
                + (jsonMode ? ",\"response_format\":{\"type\":\"json_object\"}" : "")
                + ",\"messages\":[{\"role\":\"system\",\"content\":" + quote(SYSTEM)
                + "},{\"role\":\"user\",\"content\":" + quote(lastPrompt) + "}]}";
    }

    /** Returns the response body, or "!HTTP <code> <body>" for a 4xx so the caller can retry. */
    private static String post(String body) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE.replaceAll("/+$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key())
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 == 2) return res.body();
            if (res.statusCode() / 100 == 4) return "!HTTP " + res.statusCode() + " " + res.body();
            throw new IllegalStateException("AI API returned " + res.statusCode() + ": " + trim(res.body()));
        } catch (IllegalStateException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI request interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public static String verdictJson(Verdict v) {
        return "{\"configured\":true,\"ready\":true,\"verdict\":" + quote(v.verdict())
                + ",\"blue\":" + quote(v.blue())
                + ",\"red\":" + quote(v.red())
                + ",\"closing\":" + quote(v.closing()) + "}";
    }

    private static String clean(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String trim(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "..." : s);
    }

    /** Reads the first "key":"value" string field out of a JSON object, unescaping the value. */
    private static String field(String json, String key) {
        if (json == null) return null;
        int i = 0;
        while ((i = json.indexOf('"' + key + '"', i)) >= 0) {
            int colon = json.indexOf(':', i + key.length() + 2);
            if (colon < 0) return null;
            int p = colon + 1;
            while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
            if (p >= json.length() || json.charAt(p) != '"') { i += key.length() + 2; continue; }
            StringBuilder sb = new StringBuilder();
            for (p++; p < json.length(); p++) {
                char c = json.charAt(p);
                if (c == '\\' && p + 1 < json.length()) {
                    char e = json.charAt(++p);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (p + 4 < json.length()) {
                                sb.append((char) Integer.parseInt(json.substring(p + 1, p + 5), 16));
                                p += 4;
                            }
                        }
                        default -> sb.append(e);
                    }
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
            return null;
        }
        return null;
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private static double configDouble(String name, double fallback) {
        try {
            return Double.parseDouble(config(name, Double.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}