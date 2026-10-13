package jongrai;

import java.util.List;

/**
 * One curated build for one hero, from data/hero-builds.csv.
 * "order" is the 1-based position of the build inside the hero's list.
 * "items" is the 5- or 6-item build in the order it should be bought, "core" is the first three of
 * them (the build is defined by those); every item name resolves through {@link ItemRepository#item}.
 * "lane" is the playstyle lane the build targets (blank when the source does not name one).
 * "blessing" is blank for builds that have none, "flex" is the verbatim swap guidance text.
 * "totalCost" is the sum of the item prices, computed at load time.
 */
public record HeroBuild(String hero, int order, String name, String lane,
                        List<String> items, List<String> core,
                        String emblem, String battleSpell, String blessing,
                        String flex, int totalCost) {

    public HeroBuild {
        items = List.copyOf(items);
        core = List.copyOf(core);
    }

    public String heroKey() {
        return hero.toLowerCase();
    }

    /** True when this build treats the item as one of its three core pieces. */
    public boolean isCore(String item) {
        return core.stream().anyMatch(i -> i.equalsIgnoreCase(item));
    }
}
