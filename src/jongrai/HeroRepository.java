package jongrai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Loads heroes from a CSV: name,role,lane,tier,winrate,banrate,counters,synergies  (roles/lanes split by '/', lists by ';'). */
public class HeroRepository {

    public static Map<String, Hero> loadCsv(Path file) throws IOException {
        Map<String, Hero> heroes = new LinkedHashMap<>();
        boolean header = true;

        for (String line : Files.readAllLines(file)) {
            if (line.startsWith("\uFEFF")) line = line.substring(1);
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (header) { header = false; continue; }   // skip the column-name row

            String[] parts = line.split(",", -1);
            if (parts.length < 8) {
                throw new IOException("Bad line in " + file + ": " + line);
            }
            Hero hero = new Hero(
                    parts[0].trim(),
                    parseRoles(parts[1]),
                    parseLanes(parts[2]),
                    parts[3].trim().toUpperCase(),
                    parseNumber(parts[4], 50.0),
                    parseNumber(parts[5], 0.0),
                    toSet(parts[6]),
                    toSet(parts[7]),
                    parts.length > 8 ? (int) parseNumber(parts[8], 0) : 0,
                    parts.length > 9 ? (int) parseNumber(parts[9], 0) : 0,
                    parts.length > 10 ? (int) parseNumber(parts[10], 0) : 0,
                    parts.length > 11 ? toSet(parts[11]) : Set.of(),
                    parts.length > 12 && !parts[12].isBlank() ? parts[12].trim().toUpperCase() : "MID",
                    parts.length > 13 && !parts[13].isBlank() ? parts[13].trim() : null);
            heroes.put(hero.key(), hero);
        }
        return heroes;
    }

    private static List<Role> parseRoles(String field) {
        return Arrays.stream(field.split("/")).map(Role::parse).toList();
    }

    private static double parseNumber(String field, double ifBlank) {
        String text = field.trim();
        return text.isEmpty() ? ifBlank : Double.parseDouble(text);
    }

    private static List<Lane> parseLanes(String field) {
        return Arrays.stream(field.split("/")).map(Lane::parse).toList();
    }

    /** Returns warnings for counters/synergies that name a hero not in the roster (typo check). */
    public static List<String> validate(Map<String, Hero> heroes) {
        List<String> warnings = new ArrayList<>();
        for (Hero h : heroes.values()) {
            for (String c : h.counters()) {
                if (!heroes.containsKey(c)) warnings.add(h.name() + " counters unknown hero '" + c + "'");
            }
            for (String s : h.synergies()) {
                if (!heroes.containsKey(s)) warnings.add(h.name() + " synergy with unknown hero '" + s + "'");
            }
        }
        return warnings;
    }

    private static Set<String> toSet(String field) {
        return Arrays.stream(field.split(";"))
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
