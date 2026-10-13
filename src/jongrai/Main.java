package jongrai;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/** Console demo for Project Jongrai. Run it and type commands. */
public class Main {

    public static void main(String[] args) throws Exception {
        Path csv = Path.of(args.length > 0 ? args[0] : "data/heroes.csv");
        Map<String, Hero> pool = HeroRepository.loadCsv(csv);
        List<String> warnings = HeroRepository.validate(pool);
        warnings.forEach(w -> System.out.println("WARNING: " + w));
        Draft draft = new Draft(pool);
        SuggestionEngine engine = new SuggestionEngine();

        System.out.println("Project Jongrai - draft helper (" + pool.size() + " heroes loaded)");
        printHelp();

        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.print("\n> ");
            if (!in.hasNextLine()) break;
            String line = in.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] p = line.split("\\s+", 3);
            String cmd = p[0].toLowerCase();

            try {
                switch (cmd) {
                    case "pick", "ban" -> {
                        if (p.length < 3) { System.out.println("Usage: " + cmd + " <blue|red> <hero>"); break; }
                        Draft.Side side = Draft.Side.parse(p[1]);
                        if (cmd.equals("pick")) draft.pick(side, p[2]); else draft.ban(side, p[2]);
                        show(draft);
                    }
                    case "suggest" -> {
                        if (p.length < 2) { System.out.println("Usage: suggest <blue|red>"); break; }
                        suggest(engine, draft, Draft.Side.parse(p[1]));
                    }
                    case "suggestbans" -> {
                        if (p.length < 2) { System.out.println("Usage: suggestbans <blue|red>"); break; }
                        Draft.Side side = Draft.Side.parse(p[1]);
                        printList("Best bans for " + draft.team(side).name() + ":", engine.suggestBans(draft, side, 5));
                    }
                    case "show" -> show(draft);
                    case "hero", "info" -> {
                        if (p.length < 2) { System.out.println("Usage: hero <name>"); break; }
                        heroInfo(draft, line.substring(cmd.length()).trim());
                    }
                    case "heroes" -> listHeroes(draft, p.length > 1 ? p[1] : null);
                    case "help" -> printHelp();
                    case "quit", "exit" -> { return; }
                    default -> System.out.println("Unknown command. Type 'help'.");
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("! " + e.getMessage());
            }
        }
    }

    private static void suggest(SuggestionEngine engine, Draft draft, Draft.Side side) {
        printList("Best next picks for " + draft.team(side).name() + ":", engine.suggest(draft, side, 3));
    }

    private static void printList(String title, List<SuggestionEngine.Suggestion> top) {
        System.out.println(title);
        int i = 1;
        for (SuggestionEngine.Suggestion s : top) {
            String why = s.reasons().isEmpty() ? "no strong reason yet" : String.join(", ", s.reasons());
            System.out.printf(Locale.ROOT, "  %d. %-12s score %+.1f  (%s)%n", i++, s.hero().name(), s.score(), why);
        }
    }

    /** Lists heroes; the optional filter can be a role (tank) or a lane (jungle). */
    private static void listHeroes(Draft draft, String filter) {
        Role role = null;
        Lane lane = null;
        if (filter != null) {
            try { role = Role.parse(filter); } catch (IllegalArgumentException e) { /* not a role */ }
            try { lane = Lane.parse(filter); } catch (IllegalArgumentException e) { /* not a lane */ }
            if (role == null && lane == null) throw new IllegalArgumentException("Filter must be a role or a lane.");
        }
        for (Hero h : draft.allHeroes()) {
            if (role != null && !h.hasRole(role)) continue;
            if (lane != null && !h.hasLane(lane)) continue;
            System.out.println((draft.isAvailable(h) ? "  " : "x ") + h);
        }
    }

    /** Prints one hero's profile: who it is strong against, who counters it, and its synergies. */
    private static void heroInfo(Draft draft, String name) {
        Hero hero = draft.find(name);
        System.out.println(hero + " | tier " + hero.tier() + " | win rate " + String.format(Locale.ROOT, "%.1f", hero.winRate()) + "%"
                + (hero.banRate() > 0 ? " | ban rate " + String.format(Locale.ROOT, "%.1f", hero.banRate()) + "%" : ""));

        List<String> strongAgainst = draft.allHeroes().stream()
                .filter(hero::counters).map(Hero::name).sorted().toList();
        List<String> weakAgainst = draft.allHeroes().stream()
                .filter(h -> h.counters(hero)).map(Hero::name).sorted().toList();
        List<String> worksWith = draft.allHeroes().stream()
                .filter(h -> hero.synergizesWith(h) || h.synergizesWith(hero)).map(Hero::name).sorted().toList();

        System.out.println("  Strong against : " + (strongAgainst.isEmpty() ? "-" : String.join(", ", strongAgainst)));
        System.out.println("  Weak against   : " + (weakAgainst.isEmpty() ? "-" : String.join(", ", weakAgainst)));
        System.out.println("  Works well with: " + (worksWith.isEmpty() ? "-" : String.join(", ", worksWith)));
    }

    private static void show(Draft draft) {
        for (Draft.Side side : Draft.Side.values()) {
            Team t = draft.team(side);
            System.out.println(t.name() + " | picks: " + t.picks() + " | bans: " + t.bans());
        }
    }

    private static void printHelp() {
        System.out.println("""
                Commands:
                  pick <blue|red> <hero>   lock in a pick
                  ban  <blue|red> <hero>   ban a hero
                  suggest <blue|red>       top 3 picks for that side
                  suggestbans <blue|red>   top 5 bans for that side
                  show                     show both teams
                  hero <name>              a hero's counters, weaknesses and synergies
                  heroes [role|lane]       list heroes, e.g. 'heroes jungle' (x = unavailable)
                  help / quit""");
    }
}
