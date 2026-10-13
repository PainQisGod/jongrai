package jongrai;

import java.util.List;

/**
 * One shop item from data/items.csv.
 * "tier" is the source's own tier label (1 = basic component, 3 = finished equipment),
 * "cost" is the gold price (0 means the source records no purchase price),
 * "categories" holds the shop categories the item appears under (an item in two categories
 * is listed under both and therefore carries two names here).
 * "icon" is the file name inside web/icons, empty when no icon was uploaded for the item.
 */
public record Item(String name, String slug, int tier, int cost, String stats,
                   String passives, String availability, List<String> categories, String icon) {

    public Item {
        categories = List.copyOf(categories);
    }

    public String key() {
        return name.toLowerCase();
    }
}
