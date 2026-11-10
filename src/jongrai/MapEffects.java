package jongrai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What each arena map's mechanic does to hero value (data/maps.csv, map-effects.csv, hero-traits.csv).
 * Unlike MapStats this needs no pro games: it is a hand-written rule per map, e.g. "Dangerous Grass: heroes who
 * ambush from bushes +1.5". MapStats adds it on top of the pro record whenever a map is chosen.
 */
public class MapEffects {

    /** One rule: heroes matching "match" get "weight" on "map", and the board shows "reason". */
    public record Effect(String map, String match, double weight, String reason) {
        /** Stable id for the Khmer string: dr.fx.<id>. */
        public String id() { return Hero.slugOf(reason); }
    }

    private final Map<String, String> descriptions = new LinkedHashMap<>();
    private final List<Effect> effects = new ArrayList<>();
    private final Map<String, Set<String>> traits = new LinkedHashMap<>(); // TRAIT -> hero keys

    public static MapEffects empty() { return new MapEffects(); }

    public static MapEffects load(Path dataDir) throws IOException {
        MapEffects fx = new MapEffects();
        Path maps = dataDir.resolve("maps.csv"), rules = dataDir.resolve("map-effects.csv"), heroTraits = dataDir.resolve("hero-traits.csv");
        if (!Files.isRegularFile(maps) || !Files.isRegularFile(rules) || !Files.isRegularFile(heroTraits)) return fx;
        for (Csv.Row r : Csv.read(maps, 2)) fx.descriptions.put(r.text(0), r.text(1));
        for (Csv.Row r : Csv.read(heroTraits, 2)) {
            Set<String> keys = new TreeSet<>();
            for (String h : r.list(1)) keys.add(h.trim().toLowerCase(Locale.ROOT));
            fx.traits.put(r.text(0).toUpperCase(Locale.ROOT), keys);
        }
        for (Csv.Row r : Csv.read(rules, 4)) {
            if (!fx.descriptions.containsKey(r.text(0))) throw r.fail("unknown map '" + r.text(0) + "' (not in maps.csv)");
            String match = r.text(1);
            if (!match.matches("(?i)(trait|tag|spike|lane):.+")) throw r.fail("match must be trait:, tag:, spike: or lane:, found '" + match + "'");
            if (match.toLowerCase(Locale.ROOT).startsWith("trait:") && !fx.traits.containsKey(match.substring(6).toUpperCase(Locale.ROOT)))
                throw r.fail("unknown trait '" + match.substring(6) + "' (not in hero-traits.csv)");
            fx.effects.add(new Effect(r.text(0), match, r.decimal(2, 0), r.text(3)));
        }
        return fx;
    }

    /** The maps with a description, in file order. */
    public Set<String> maps() { return descriptions.keySet(); }

    public String description(String map) { return descriptions.getOrDefault(map, ""); }

    public List<Effect> effects(String map) { return effects.stream().filter(e -> e.map().equals(map)).toList(); }

    public List<Effect> all() { return List.copyOf(effects); }

    public Map<String, Set<String>> traits() { return traits; }

    public boolean matches(Effect e, Hero h) {
        int c = e.match().indexOf(':');
        String kind = e.match().substring(0, c).toLowerCase(Locale.ROOT), value = e.match().substring(c + 1).trim();
        return switch (kind) {
            case "trait" -> traits.getOrDefault(value.toUpperCase(Locale.ROOT), Set.of()).contains(h.key());
            case "tag" -> h.hasTag(value);
            case "spike" -> h.spike() != null && h.spike().name().equalsIgnoreCase(value);
            case "lane" -> h.lanes().stream().anyMatch(l -> l.name().equalsIgnoreCase(value));
            default -> false;
        };
    }

    /** Every rule on this map that applies to the hero. */
    public List<Effect> hits(String map, Hero h) {
        return effects(map).stream().filter(e -> matches(e, h)).toList();
    }

    /** Hero names (as written in hero-traits.csv) that no hero in the pool matches - typos. */
    public List<String> unknownHeroes(Set<String> poolKeys) {
        List<String> bad = new ArrayList<>();
        traits.forEach((t, keys) -> keys.stream().filter(k -> !poolKeys.contains(k)).forEach(k -> bad.add(t + ":" + k)));
        return bad;
    }
}
