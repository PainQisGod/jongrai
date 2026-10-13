package jongrai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain checks against the real data files, no test framework: java -cp out jongrai.SelfCheck
 * Run from the project folder, or pass -Djongrai.home=<folder>. Exits non-zero on the first failure.
 */
public final class SelfCheck {

    private static int checks;

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        Path home = Path.of(System.getProperty("jongrai.home", ".")).toAbsolutePath();
        Map<String, Hero> pool = HeroRepository.loadCsv(home.resolve("data/heroes.csv"));
        ItemRepository gear = ItemRepository.load(home.resolve("data/items.csv"), home.resolve("data/hero-builds.csv"));

        csv(home);
        data(pool, gear);
        json();
        draftRules(pool);
        analysis(pool);
        System.out.println("SelfCheck: " + checks + " checks passed");
    }

    private static void csv(Path home) throws Exception {
        check(Csv.split("a,\"b,c\",\"d\"\"e\"").equals(List.of("a", "b,c", "d\"e")), "Csv.split honours quotes and doubled quotes");
        Path tmp = Files.createTempFile("jongrai", ".csv");
        try {
            Files.writeString(tmp, "name,tip\nAamon,wait, then burst\n");
            boolean failed = false;
            try {
                Csv.read(tmp, 1);
            } catch (java.io.IOException e) {
                failed = e.getMessage().contains(":2:");
            }
            check(failed, "a row wider than the header fails with file:line");
        } finally {
            Files.delete(tmp);
        }
    }

    private static void data(Map<String, Hero> pool, ItemRepository gear) {
        check(pool.size() == 133, "all 133 heroes load");
        String tip = pool.get("aamon").tip();
        check(tip != null && tip.startsWith("Your job is to wait") && tip.endsWith("slip away."), "Aamon's tip loads in full, comma included");

        Map<String, Item> bySlug = new HashMap<>();
        gear.items().forEach(i -> check(bySlug.put(i.slug(), i) == null, "item slug is unique: " + i.slug()));
        long apostrophes = gear.items().stream().filter(i -> i.name().contains("'")).count();
        check(apostrophes == 5, "five items have an apostrophe in the name");
        for (Item i : gear.items()) {
            if (i.name().contains("'")) check(bySlug.get(i.slug()) == i && !i.slug().contains("-s-"), "/items/" + i.slug() + " resolves to " + i.name());
        }
        for (HeroBuild b : gear.builds("Aamon")) {
            for (String name : b.items()) check(gear.item(name) != null && bySlug.containsKey(gear.item(name).slug()), "build item links resolve: " + name);
        }
        check(gear.builds("Aamon").get(0).totalCost() == 11710, "Aamon build 1 totals 11710 gold");
    }

    private static void json() {
        Object parsed = Json.parse("{\"m\":" + Json.str("a\nb \"q\" \\ <x>") + "}");
        check("a\nb \"q\" \\ <x>".equals(Json.at(parsed, "m")), "Json.str output parses back to the same text");
        check("null".equals(Json.str(null)), "Json.str(null) is the JSON null");
    }

    private static void draftRules(Map<String, Hero> pool) {
        Draft draft = new Draft(pool);
        Draft.Side[] order = {Draft.Side.BLUE, Draft.Side.RED, Draft.Side.RED, Draft.Side.BLUE, Draft.Side.BLUE,
                Draft.Side.RED, Draft.Side.RED, Draft.Side.BLUE, Draft.Side.BLUE, Draft.Side.RED};
        List<Hero> heroes = List.copyOf(pool.values());
        for (int i = 0; i < order.length; i++) {
            check(draft.nextPick().orElseThrow() == order[i], "pick " + (i + 1) + " belongs to " + order[i]);
            draft.pick(order[i], heroes.get(i).name());
        }
        check(draft.nextPick().isEmpty() && draft.isComplete(), "no pick is left after ten");

        boolean immutable = false;
        try {
            draft.team(Draft.Side.BLUE).picks().add(heroes.get(20));
        } catch (UnsupportedOperationException e) {
            immutable = true;
        }
        check(immutable, "a team's pick list cannot be changed from outside");
        check(new SuggestionEngine().suggest(draft, Draft.Side.BLUE, 3).isEmpty(), "no pick suggestions for a full team");

        long before = draft.version();
        draft.reset();
        check(draft.version() > before && draft.team(Draft.Side.BLUE).picks().isEmpty(), "reset clears the board and bumps the version");
        for (int i = 0; i < Team.MAX_BANS; i++) draft.ban(Draft.Side.BLUE, heroes.get(i).name());
        check(new SuggestionEngine().suggestBans(draft, Draft.Side.BLUE, 3).isEmpty(), "no ban suggestions once the bans are full");
    }

    private static void analysis(Map<String, Hero> pool) {
        DraftAnalyzer analyzer = new DraftAnalyzer();
        Draft draft = new Draft(pool);
        check(analyzer.coordination(draft).blue().spikeTiming().equals("NONE"), "an empty team has spike timing NONE");

        draft.pick(Draft.Side.BLUE, "Fredrinn");   // FIGHTER/TANK
        DraftAnalyzer.TeamSide blue = analyzer.coordination(draft).blue();
        check(blue.hasTank() && !blue.missingRoles().contains("TANK"), "a FIGHTER/TANK hero counts as a tank everywhere");
    }
}
