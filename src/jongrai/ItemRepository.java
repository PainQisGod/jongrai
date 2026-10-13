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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads the item catalog and the per-hero curated builds.
 *
 * Both files go through {@link Csv}: a field containing a comma or a quote is wrapped in double
 * quotes, because item stat lines and build flex text contain commas. Lists inside one field use ";".
 * Both files are required; a missing or malformed file stops the server with file:line.
 */
public final class ItemRepository {

    private final Map<String, Item> items;
    private final Map<String, List<HeroBuild>> builds;
    private final Map<String, List<String>> coreUsers;
    private final Map<String, List<String>> users;

    private ItemRepository(Map<String, Item> items, Map<String, List<HeroBuild>> builds) {
        this.items = items;
        this.builds = builds;
        this.coreUsers = index(builds, HeroBuild::core);
        this.users = index(builds, HeroBuild::items);
    }

    /** Reads both datasets. Items load first, because a build's total cost is the sum of its items. */
    public static ItemRepository load(Path itemsFile, Path buildsFile) throws IOException {
        Map<String, Item> items = loadItems(itemsFile);
        return new ItemRepository(items, loadBuilds(buildsFile, items));
    }

    private static Map<String, Item> loadItems(Path file) throws IOException {
        Map<String, Item> items = new LinkedHashMap<>();
        for (Csv.Row r : Csv.read(file, 9)) {
            Item item = new Item(r.text(0), r.text(1), r.integer(2, 0), r.integer(3, 0),
                    r.text(4), r.text(5), r.text(6), r.list(7), r.text(8));
            if (items.putIfAbsent(item.key(), item) != null) throw r.fail("duplicate item '" + item.name() + "'");
        }
        return items;
    }

    private static Map<String, List<HeroBuild>> loadBuilds(Path file, Map<String, Item> items) throws IOException {
        Map<String, List<HeroBuild>> builds = new LinkedHashMap<>();
        for (Csv.Row r : Csv.read(file, 9)) {
            List<String> names = r.list(4);
            int total = names.stream().map(n -> items.get(n.toLowerCase())).filter(java.util.Objects::nonNull)
                    .mapToInt(Item::cost).sum();
            HeroBuild build = new HeroBuild(r.text(0), r.integer(1, 0), r.text(2), r.text(3),
                    names, names.subList(0, Math.min(3, names.size())),
                    r.text(5), r.text(6), r.text(7), r.text(8), total);
            builds.computeIfAbsent(build.heroKey(), k -> new ArrayList<>()).add(build);
        }
        builds.values().forEach(list -> list.sort(Comparator.comparingInt(HeroBuild::order)));
        return builds;
    }

    /** Item name (lowercase) -> the heroes with that item in the chosen part of a build, sorted by name. */
    private static Map<String, List<String>> index(Map<String, List<HeroBuild>> builds,
                                                   Function<HeroBuild, List<String>> part) {
        Map<String, Set<String>> index = new TreeMap<>();
        for (List<HeroBuild> list : builds.values()) {
            for (HeroBuild b : list) {
                for (String item : part.apply(b)) {
                    index.computeIfAbsent(item.toLowerCase(), k -> new TreeSet<>()).add(b.hero());
                }
            }
        }
        return index.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, e -> List.copyOf(e.getValue())));
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

    /** Item name (lowercase) -> the heroes that core-build it. */
    public Map<String, List<String>> coreUsersByItem() {
        return coreUsers;
    }

    /** Item name (lowercase) -> every hero that has it in any build, not just the core. */
    public Map<String, List<String>> usersByItem() {
        return users;
    }

    /**
     * Warnings for a dataset that does not hang together: a build naming an item the catalog does
     * not have, a build for an unknown hero, or an item whose icon file is missing from iconDir.
     */
    public List<String> validate(Set<String> heroKeys, Path iconDir) {
        List<String> warnings = new ArrayList<>();
        for (List<HeroBuild> list : builds.values()) {
            for (HeroBuild b : list) {
                if (heroKeys != null && !heroKeys.contains(b.heroKey())) {
                    warnings.add(b.hero() + " is not in the roster but has builds");
                }
                for (String name : b.items()) {
                    if (item(name) == null) warnings.add(b.hero() + " build " + b.order() + " uses unknown item '" + name + "'");
                }
            }
        }
        for (Item item : items.values()) {
            if (item.icon().isBlank()) warnings.add("item '" + item.name() + "' has no icon file");
            else if (!Files.isRegularFile(iconDir.resolve(item.icon()))) {
                warnings.add("item '" + item.name() + "' icon '" + item.icon() + "' is missing from " + iconDir);
            }
        }
        return warnings;
    }
}
