package jongrai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Asks an OpenAI-compatible chat completions API to judge the finished draft.
 * Configure with environment variables or ai-config.txt in the project folder (see ai-config.example.txt):
 *   JONGRAI_AI_KEY          API key (required)
 *   JONGRAI_AI_BASE_URL     default https://api.openai.com/v1
 *   JONGRAI_AI_MODEL        default gpt-4o-mini
 *   JONGRAI_AI_TEMPERATURE  default 0.6
 *   JONGRAI_AI_TIMEOUT      seconds per request, default 90
 * Any OpenAI-compatible endpoint works: OpenAI, DeepSeek, Groq, OpenRouter, Together, Ollama, ...
 * The settings are read once, by {@link #init}.
 */
public class AiCoach {

    /** verdict = overall read, blue / red = per-side comment, closing = one tactical instruction. */
    public record Verdict(String verdict, String blue, String red, String closing) {}

    private record Config(String key, String base, String model, double temperature, int timeout, String source) {}

    private static Config cfg = new Config("", "", "", 0, 0, "not loaded");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    /** Reads the settings from the environment, then from baseDir/ai-config.txt. Call once at startup. */
    public static void init(Path baseDir) {
        Path file = baseDir.resolve("ai-config.txt");
        Map<String, String> fromFile = readConfigFile(file);
        Map<String, String> env = new HashMap<>();
        for (String name : List.of("KEY", "BASE_URL", "MODEL", "TEMPERATURE", "TIMEOUT")) {
            String v = System.getenv("JONGRAI_AI_" + name);
            if (v != null && !v.isBlank()) env.put("JONGRAI_AI_" + name, v.trim());
        }
        Map<String, String> all = new HashMap<>(fromFile);
        all.putAll(env);
        String source = env.containsKey("JONGRAI_AI_KEY") ? "environment variable JONGRAI_AI_KEY"
                : fromFile.getOrDefault("JONGRAI_AI_KEY", "").isEmpty() ? "nowhere - no JONGRAI_AI_KEY in the environment or in " + file
                : file + " (JONGRAI_AI_KEY)";
        double temperature;
        int timeout;
        try {
            temperature = Double.parseDouble(all.getOrDefault("JONGRAI_AI_TEMPERATURE", "0.6"));
            timeout = Integer.parseInt(all.getOrDefault("JONGRAI_AI_TIMEOUT", "90"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("JONGRAI_AI_TEMPERATURE must be a number and JONGRAI_AI_TIMEOUT a whole number of seconds.");
        }
        if (!Double.isFinite(temperature) || timeout <= 0) {
            throw new IllegalArgumentException("JONGRAI_AI_TEMPERATURE must be finite and JONGRAI_AI_TIMEOUT greater than 0.");
        }
        cfg = new Config(all.getOrDefault("JONGRAI_AI_KEY", ""),
                all.getOrDefault("JONGRAI_AI_BASE_URL", "https://api.openai.com/v1"),
                all.getOrDefault("JONGRAI_AI_MODEL", "gpt-4o-mini"), temperature, timeout, source);
    }

    private static Map<String, String> readConfigFile(Path file) {
        try {
            if (!Files.isRegularFile(file)) return Map.of();
            Map<String, String> map = new HashMap<>();
            for (String line : Files.readAllLines(file)) {
                line = line.stripLeading().replace("﻿", "").trim(); // editors may add a BOM
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
            return map;
        } catch (IOException e) {
            return Map.of();
        }
    }

    public static boolean configured() {
        return !cfg.key().isEmpty();
    }

    /** Where the API key was found, for the startup banner. Never prints the key itself. */
    public static String keySource() { return cfg.source(); }

    public static String model() { return cfg.model(); }

    public static String baseUrl() { return cfg.base(); }

    /**
     * Builds the judge prompt for the current draft. Returns the exact prompt text,
     * which doubles as the cache signature for a finished draft.
     */
    public static String prompt(Draft draft, DraftAnalyzer.Analysis a) {
        StringBuilder sb = new StringBuilder();
        sb.append("Draft state (Mobile Legends: Bang Bang patch ").append(HeroRepository.PATCH)
                .append(", ranked draft of 5 picks + 5 bans per side).\n\n");
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
                    .append(" | tags ").append(h.tags().isEmpty() ? "-" : String.join(",", h.tags().stream().sorted().toList()));
            List<String> vs = enemy.picks().stream().filter(h::counters).map(Hero::name).toList();
            if (!vs.isEmpty()) sb.append(" | counters on this draft: ").append(String.join(", ", vs));
            List<String> sync = t.picks().stream().filter(o -> o != h && (h.synergizesWith(o) || o.synergizesWith(h))).map(Hero::name).toList();
            if (!sync.isEmpty()) sb.append(" | has synergy with: ").append(String.join(", ", sync));
            sb.append('\n');
        }
        sb.append("Bans: ").append(t.bans().isEmpty() ? "-" : t.bans().stream().map(Hero::name).collect(Collectors.joining(", "))).append('\n');
        return sb.toString();
    }

    private static final String SYSTEM = """
            You are Jongrai, an honest and sharp MLBB (Mobile Legends: Bang Bang) draft coach.
            You judge a completed draft and give a short, concrete read on BOTH sides.
            Everything you are shown is from Mobile Legends patch %1$s - the roster, tiers and win rates on
            screen are patch %1$s data. Judge the draft as it exists in patch %1$s and never bring in
            heroes, reworks or balance changes from other patches. Mention the patch once if it matters.
            Rules:
            - Judge the draft on screen, not the players. Never say a team "should win"; talk about draft strengths, gaps, win conditions and how each side closes the game.
            - Use the engine readout as supporting evidence only. If it disagrees with your read, trust your read and say why.
            - Be specific: name heroes, lanes and matchups. No generic advice, no filler, no restating the score.
            - Plain prose. No markdown, no bullet characters, no headings.
            Reply with JSON only, exactly this shape:
            {"verdict":"2-3 sentences on the draft as a whole","blue":"3-5 sentences on the Blue side: strengths, gaps, win condition","red":"3-5 sentences on the Red side: strengths, gaps, win condition","closing":"1-2 sentences: the single most important thing to watch or execute"}
            """.formatted(HeroRepository.PATCH);

    /** Calls the model and parses its JSON reply. Throws IllegalStateException on any failure. */
    public static Verdict judge(String prompt) {
        if (!configured()) throw new IllegalStateException("No API key found. Copy ai-config.example.txt to ai-config.txt, add your key, and restart the server.");

        // Flavors {jsonMode, noThinking}, most preferred first. DeepSeek's "thinking" switch is only sent
        // to DeepSeek, and is always dropped before JSON mode is.
        boolean deepseek = cfg.base().toLowerCase(Locale.ROOT).contains("deepseek");
        boolean[][] flavors = deepseek ? new boolean[][]{{true, true}, {true, false}, {false, false}}
                                       : new boolean[][]{{true, false}, {false, false}};
        String raw = "";
        for (boolean[] flavor : flavors) {
            raw = post(requestBody(prompt, flavor[0], flavor[1]));
            if (!raw.startsWith("!HTTP ")) break;
            String code = raw.split(" ", 3)[1];
            if (!(code.equals("400") || code.equals("404") || code.equals("422") || code.equals("501"))) break;
        }
        if (raw.startsWith("!HTTP ")) {
            String[] parts = raw.split(" ", 3);
            throw new IllegalStateException("AI API returned " + parts[1] + ": " + trim(parts.length > 2 ? parts[2] : ""));
        }

        Object content;
        try {
            content = Json.at(Json.parse(raw), "choices", 0, "message", "content");
        } catch (IllegalArgumentException e) {
            content = null;
        }
        if (!(content instanceof String text)) throw new IllegalStateException("Could not read the AI reply: " + trim(raw));
        Map<?, ?> reply = replyObject(text);
        if (reply == null) throw new IllegalStateException("AI reply was not in the expected shape: " + trim(text));
        String verdict = string(reply, "verdict"), blue = string(reply, "blue"), red = string(reply, "red");
        if (verdict == null || blue == null || red == null) {
            throw new IllegalStateException("AI reply was not in the expected shape: " + trim(text));
        }
        String closing = string(reply, "closing");
        return new Verdict(clean(verdict), clean(blue), clean(red), closing == null ? "" : clean(closing));
    }

    /** The JSON object inside the model's text, tolerating ```json fences and chatter around it. */
    private static Map<?, ?> replyObject(String text) {
        int from = text.indexOf('{'), to = text.lastIndexOf('}');
        if (from < 0 || to < from) return null;
        try {
            return Json.parse(text.substring(from, to + 1)) instanceof Map<?, ?> m ? m : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String string(Map<?, ?> map, String key) {
        return map.get(key) instanceof String s ? s : null;
    }

    private static String requestBody(String prompt, boolean jsonMode, boolean noThinking) {
        return "{\"model\":" + Json.str(cfg.model())
                + ",\"temperature\":" + cfg.temperature()
                // DeepSeek V4 thinks by default, which is slow and pointless for a draft read.
                + (noThinking ? ",\"thinking\":{\"type\":\"disabled\"}" : "")
                + (jsonMode ? ",\"response_format\":{\"type\":\"json_object\"}" : "")
                + ",\"messages\":[{\"role\":\"system\",\"content\":" + Json.str(SYSTEM)
                + "},{\"role\":\"user\",\"content\":" + Json.str(prompt) + "}]}";
    }

    /** Returns the response body, or "!HTTP <code> <body>" for a 4xx so the caller can retry. */
    private static String post(String body) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.base().replaceAll("/+$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(cfg.timeout()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.key())
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
            throw new IllegalStateException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), e);
        }
    }

    public static String verdictJson(Verdict v) {
        return "{\"configured\":true,\"ready\":true,\"verdict\":" + Json.str(v.verdict())
                + ",\"blue\":" + Json.str(v.blue())
                + ",\"red\":" + Json.str(v.red())
                + ",\"closing\":" + Json.str(v.closing()) + "}";
    }

    private static String clean(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String trim(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "..." : s);
    }
}
