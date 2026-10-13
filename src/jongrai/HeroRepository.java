package jongrai;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads heroes from a CSV:
 * name,role,lane,tier,winrate,banrate,counters,synergies,durability,offense,control,tags,spike,tip
 * (roles/lanes split by '/', lists by ';'; quote any field that contains a comma).
 */
public class HeroRepository {

    /** The game patch every shipped data file describes. Change it here only. */
    public static final String PATCH = "2.2.16";

    private static final Set<String> TIERS = Set.of("S", "A", "B", "C", "D");

    public static Map<String, Hero> loadCsv(Path file) throws IOException {
        Map<String, Hero> heroes = new LinkedHashMap<>();
        for (Csv.Row r : Csv.read(file, 8)) {
            String tier = r.text(3).toUpperCase();
            if (!TIERS.contains(tier)) throw r.fail("tier must be one of " + TIERS + ", found '" + tier + "'");
            Hero hero;
            try {
                hero = new Hero(
                        r.text(0),
                        parse(r.text(1), Role::parse),
                        parse(r.text(2), Lane::parse),
                        tier,
                        r.decimal(4, 50.0),
                        r.decimal(5, 0.0),
                        toSet(r.text(6)),
                        toSet(r.text(7)),
                        r.integer(8, 0),
                        r.integer(9, 0),
                        r.integer(10, 0),
                        toSet(r.text(11)),
                        r.text(12).isEmpty() ? Spike.MID : Spike.parse(r.text(12)),
                        r.text(13).isEmpty() ? null : r.text(13));
            } catch (IllegalArgumentException e) {
                throw r.fail(e.getMessage());   // unknown role, lane or spike
            }
            if (heroes.putIfAbsent(hero.key(), hero) != null) throw r.fail("duplicate hero '" + hero.name() + "'");
        }
        return heroes;
    }

    private static <T> List<T> parse(String field, java.util.function.Function<String, T> parser) {
        if (field.isEmpty()) throw new IllegalArgumentException("empty role/lane field");
        return Arrays.stream(field.split("/")).map(parser).toList();
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
