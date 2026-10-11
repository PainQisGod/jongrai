package jongrai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Loads the item catalog and the per-hero curated builds.
 *
 * Both files are CSV where a field containing a comma, a semicolon or a quote is wrapped in double
 * quotes with internal quotes doubled, because item stat lines and build flex text contain all three.
 * Lists inside one field use ";" as the separator, matching the hero files. Lines starting with "#"
 * are comments and the row right after them is the column-name row.
 */
public final class ItemRepository {

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final Map<String, Item> items;
    private final Map<String, List<HeroBuild>> builds;

    private ItemRepository(Map<String, Item> items, Map<String, List<HeroBuild>> builds) {
        this.items = items;
        this.builds = builds;
    }

    /** Reads both datasets. A missing file yields an empty repository, so the site still serves. */
    public static ItemRepository load(Path itemsFile, Path buildsFile) throws IOException {
        return new ItemRepository(loadItems(itemsFile), loadBuilds(buildsFile));
    }

    private static Map<String, Item> loadItems(Path file) throws IOException {
        Map<String, Item> items = new LinkedHashMap<>();
        if (!Files.exists(file)) return items;

        boolean header = true;
        for (String line : Files.readAllLines(file)) {
            // A UTF-8 BOM would hide the first "# ..." comment from the skip test and eat the
            // column-name row as a data row, so strip it before anything else is tested.
            if (line.startsWith("\uFEFF")) line = line.substring(1);
            if (line.isBlank() || line.startsWith("#")) continue;
            if (header) { header = false; continue; }

            List<String> p = splitCsv(line);
            if (p.size() < 9) throw new IOException("Bad line in " + file + ": " + line);
            Item item = new Item(
                    p.get(0).trim(),
                    p.get(1).trim(),
                    (int) number(p.get(2), 0),
                    (int) number(p.get(3), 0),
                    p.get(4).trim(),
                    p.get(5).trim(),
                    p.get(6).trim(),
                    splitList(p.get(7)),
                    p.get(8).trim());
            items.put(item.key(), item);
        }
        return items;
    }

    private static Map<String, List<HeroBuild>> loadBuilds(Path file) throws IOException {
        Map<String, List<HeroBuild>> builds = new LinkedHashMap<>();
        if (!Files.exists(file)) return builds;

        boolean header = true;
        for (String line : Files.readAllLines(file)) {
            if (line.startsWith("\uFEFF")) line = line.substring(1);
            if (line.isBlank() || line.startsWith("#")) continue;
            if (header) { header = false; continue; }

            List<String> p = splitCsv(line);
            if (p.size() < 10) throw new IOException("Bad line in " + file + ": " + line);
            List<String> items = splitList(p.get(4));
            List<String> core = items.size() >= 3 ? items.subList(0, 3) : items;
            HeroBuild build = new HeroBuild(
                    p.get(0).trim(),
                    (int) number(p.get(1), 0),
                    p.get(2).trim(),
                    p.get(3).trim(),
                    items,
                    core,
                    p.get(5).trim(),
                    p.get(6).trim(),
                    p.get(7).trim(),
                    p.get(8).trim(),
                    (int) number(p.get(9), 0));
            builds.computeIfAbsent(build.heroKey(), k -> new ArrayList<>()).add(build);
        }
        builds.values().forEach(list -> list.sort(Comparator.comparingInt(HeroBuild::order)));
        return builds;
    }

    public List<Item> items() {
        return List.copyOf(items.values());
    }

    /** The item with this exact name, or null. Names are matched case-insensitively. */
    public Item item(String name) {
        return name == null ? null : items.get(name.trim().toLowerCase());
    }

    /** Every curated build of this hero in build order; empty when the hero has none. */
    public List<HeroBuild> builds(String hero) {
        return hero == null ? List.of() : builds.getOrDefault(hero.trim().toLowerCase(), List.of());
    }

    /** Item name -> the heroes that core-build it, both sides sorted by name. */
    public Map<String, List<String>> coreUsersByItem() {
        Map<String, Set<String>> index = new TreeMap<>();
        for (List<HeroBuild> list : builds.values()) {
            for (HeroBuild b : list) {
                for (String item : b.core()) {
                    index.computeIfAbsent(item.toLowerCase(), k -> new TreeSet<>()).add(b.hero());
                }
            }
        }
        return index.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, e -> List.copyOf(e.getValue()), (a, b) -> a, LinkedHashMap::new));
    }

    /** Item name -> every hero that has it in any build, not just the core. */
    public Map<String, List<String>> usersByItem() {
        Map<String, Set<String>> index = new TreeMap<>();
        for (List<HeroBuild> list : builds.values()) {
            for (HeroBuild b : list) {
                for (String item : b.items()) {
                    index.computeIfAbsent(item.toLowerCase(), k -> new TreeSet<>()).add(b.hero());
                }
            }
        }
        return index.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, e -> List.copyOf(e.getValue()), (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Warnings for a dataset that does not hang together: a build naming an item the catalog does
     * not have, a core entry that is not part of its own build, or a build for an unknown hero.
     */
    public List<String> validate(Set<String> heroKeys) {
        List<String> warnings = new ArrayList<>();
        for (List<HeroBuild> list : builds.values()) {
            for (HeroBuild b : list) {
                if (heroKeys != null && !heroKeys.contains(b.heroKey())) {
                    warnings.add(b.hero() + " is not in the roster but has builds");
                }
                for (String name : b.items()) {
                    if (item(name) == null) warnings.add(b.hero() + " build " + b.order() + " uses unknown item '" + name + "'");
                }
                for (String name : b.core()) {
                    if (item(name) == null) warnings.add(b.hero() + " build " + b.order() + " cores unknown item '" + name + "'");
                    if (b.items().stream().noneMatch(i -> i.equalsIgnoreCase(name))) {
                        warnings.add(b.hero() + " build " + b.order() + " cores '" + name + "' which is not in its item list");
                    }
                }
            }
        }
        for (Item item : items.values()) {
            if (item.icon().isBlank()) warnings.add("item '" + item.name() + "' has no icon file");
        }
        return warnings;
    }

    /** Splits one CSV line, honouring the quoting rules described on this class. */
    static List<String> splitCsv(String line) {
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

    /** Splits a ";"-separated list field into trimmed, non-empty entries. */
    private static List<String> splitList(String field) {
        if (field == null || field.isBlank()) return List.of();
        return java.util.Arrays.stream(field.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Reads the first integer in a field, tolerating suffixes such as "11060 gold". */
    private static double number(String field, double ifBlank) {
        String text = field.trim();
        if (text.isEmpty()) return ifBlank;
        var m = DIGITS.matcher(text);
        if (!m.find()) throw new IllegalArgumentException("Not a number: " + field);
        return Double.parseDouble(m.group());
    }
}