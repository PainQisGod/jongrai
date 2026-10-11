package jongrai;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One curated build for one hero, from data/hero-builds.csv.
 * "order" is the 1-based position of the build inside the hero's list.
 * "items" is the six-item build in the order it should be bought, "core" is the subset of
 * three items the build is defined by; every item name resolves through {@link ItemRepository#item}.
 * "lane" is the playstyle lane the build targets (blank when the source does not name one).
 * "blessing" is blank for builds that have none, "flex" is the verbatim swap guidance text.
 */
public record HeroBuild(String hero, int order, String name, String lane,
                        List<String> items, List<String> core,
                        String emblem, String battleSpell, String blessing,
                        String flex, int totalCost) {

    public String heroKey() {
        return hero.toLowerCase();
    }

    /** True when this build treats the item as one of its three core pieces. */
    public boolean isCore(String item) {
        return core.stream().anyMatch(i -> i.equalsIgnoreCase(item));
    }

    /**
     * The emblem set plus its three talents, in the order the source writes them
     * ("Fighter Emblem with Firmness, Festival of Blood, Brave Smite").
     * The first token is the set; the rest are the talents.
     */
    public List<String> emblemParts() {
        return Arrays.stream(emblem.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** The battle spell, or blank when the build has none. */
    public String spell() {
        return battleSpell == null ? "" : battleSpell;
    }

    /** The blessing, or blank when the build has none. */
    public String bless() {
        return blessing == null ? "" : blessing;
    }
}