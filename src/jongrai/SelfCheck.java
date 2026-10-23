package jongrai;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
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
        sessions(pool);
        auth();
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

    private static void sessions(Map<String, Hero> pool) {
        long[] now = {1000};
        Sessions s = new Sessions(pool, 100, 3, () -> now[0]);
        Sessions.Session a = s.create();
        Sessions.Session b = s.create();
        a.draft.pick(Draft.Side.BLUE, "Fredrinn");
        check(b.draft.team(Draft.Side.BLUE).picks().isEmpty(), "one visitor's picks never show on another visitor's board");
        check(s.find(a.id) == a && s.find(b.id) == b, "a session is found again by its id");
        check(!a.id.equals(b.id) && a.id.length() == 43, "session ids are 43 characters and distinct");
        check(s.find(null) == null && s.find("nope") == null && s.find("A".repeat(43)) == null,
                "a missing, malformed or unknown id gives no session");
        now[0] += 60;
        check(s.find(a.id) == a, "using a session keeps it alive");
        now[0] += 101;
        check(s.find(a.id) == null && s.find(b.id) == null, "a session idle past the limit expires");

        Sessions capped = new Sessions(pool, 1_000_000, 2, () -> ++now[0]);
        Sessions.Session first = capped.create();
        Sessions.Session second = capped.create();
        capped.find(first.id);           // first is now more recent than second
        capped.create();
        check(capped.size() == 2 && capped.find(second.id) == null && capped.find(first.id) == first,
                "a full table drops the least recently used session");

        check("abc".equals(Sessions.idFrom("theme=dark; jsid=abc; x=1")) && Sessions.idFrom("a=1") == null
                && Sessions.idFrom(null) == null, "the jsid cookie is picked out of a Cookie header");
        Sessions.Session probe = new Sessions(pool).create();
        String cookie = new Sessions(pool).setCookie(probe, false);
        check(cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax") && !cookie.contains("Secure"),
                "the session cookie is HttpOnly and SameSite=Lax");
        check(new Sessions(pool).setCookie(probe, true).endsWith("; Secure"), "the Secure flag is added behind HTTPS");
        check(Sessions.clearCookie(false).contains("Max-Age=0"), "signing out clears the cookie");

        // signing in: same board, new id, old id dead, longer life
        long[] t0 = {1000};
        Sessions login = new Sessions(pool, 100, 1000, 5, () -> t0[0]);
        Sessions.Session anon = login.create();
        anon.draft.pick(Draft.Side.RED, "Fredrinn");
        Auth.User user = new Auth.User("sub1", "a@example.com", "A", "", true);
        Sessions.Session in = login.login(anon, user);
        check(in.draft == anon.draft && in.user == user, "signing in keeps the visitor's board and records who they are");
        check(!in.id.equals(anon.id) && login.find(anon.id) == null && login.find(in.id) == in,
                "signing in gives a new session id and the old one stops working");
        t0[0] += 500;
        check(login.find(in.id) == in, "a signed-in session outlives an anonymous one");
        t0[0] += 1001; // past the signed-in limit, counted from the last use
        check(login.find(in.id) == null, "a signed-in session still expires when idle long enough");
        login.remove(login.create());
        check(login.size() == 0, "signing out removes the session");

        Sessions mixed = new Sessions(pool, 1_000_000, 1_000_000, 2, () -> ++t0[0]);
        Sessions.Session member = mixed.login(mixed.create(), user);
        Sessions.Session guest = mixed.create();
        mixed.create();
        check(mixed.find(member.id) == member && mixed.find(guest.id) == null,
                "when the table is full, a guest is dropped before a signed-in visitor");
    }

    private static String jwt(String payloadJson) {
        Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
        return e.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + e.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8)) + ".sig";
    }

    private static boolean rejected(String jwt, String nonce, long now) {
        try {
            Auth.validateIdToken(jwt, "client-1", nonce, now);
            return false;
        } catch (Auth.AuthException e) {
            return true;
        }
    }

    private static void auth() throws Exception {
        check(Auth.pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk").equals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"),
                "PKCE S256 matches the RFC 7636 example");
        check(Auth.safeNext("/league?x=1").equals("/league?x=1") && Auth.safeNext("/").equals("/"), "a path on this site is kept after sign-in");
        for (String bad : new String[]{null, "", "//evil.com", "https://evil.com", "/\\evil.com", "evil", "/a\nb"}) {
            check(Auth.safeNext(bad).equals("/"), "an unsafe next address is replaced by /: " + bad);
        }

        String ok = "{\"iss\":\"https://accounts.google.com\",\"aud\":\"client-1\",\"exp\":2000,\"nonce\":\"n1\","
                + "\"sub\":\"123\",\"email\":\"A@Example.com\",\"email_verified\":true,\"name\":\"Ann\",\"picture\":\"https://x.test/p.png\"}";
        Auth.Claims c = Auth.validateIdToken(jwt(ok), "client-1", "n1", 1500);
        check(c.sub().equals("123") && c.email().equals("A@Example.com") && c.name().equals("Ann") && c.picture().startsWith("https://"),
                "a good ID token gives the visitor's claims");
        check(rejected(jwt(ok.replace("client-1", "other")), "n1", 1500), "an ID token for another app is refused");
        check(rejected(jwt(ok), "n2", 1500), "an ID token with the wrong nonce is refused");
        check(rejected(jwt(ok), "n1", 2061), "an expired ID token is refused");
        check(!rejected(jwt(ok), "n1", 2059), "a minute of clock drift is tolerated");
        check(rejected(jwt(ok.replace("https://accounts.google.com", "https://evil.test")), "n1", 1500), "an ID token from another issuer is refused");
        check(rejected(jwt(ok.replace("\"email_verified\":true", "\"email_verified\":false")), "n1", 1500), "an unverified email is refused");
        check(rejected(jwt(ok.replace("\"email\":\"A@Example.com\",", "")), "n1", 1500), "an ID token without an email is refused");
        check(rejected("not.a.jwt", "n1", 1500) && rejected("a.b", "n1", 1500) && rejected(null, "n1", 1500), "a malformed ID token is refused");
        check(Auth.validateIdToken(jwt(ok.replace("https://x.test/p.png", "http://x.test/p.png")), "client-1", "n1", 1500).picture().isEmpty(),
                "a non-https picture address is dropped");

        Path dir = Files.createTempDirectory("jongrai-auth");
        try {
            Files.writeString(dir.resolve("auth-config.txt"),
                    "JONGRAI_GOOGLE_CLIENT_ID=id\nJONGRAI_GOOGLE_CLIENT_SECRET=secret\nJONGRAI_ADMIN_EMAILS= Boss@Example.com , other@example.com\n");
            Auth.init(dir, 8080);
            check(Auth.configured() && Auth.isAdmin("boss@example.com") && Auth.isAdmin("OTHER@example.com") && !Auth.isAdmin("x@example.com")
                    && !Auth.isAdmin(null), "admins are the listed emails, compared without case");
            check(Auth.publicUrl().equals("http://localhost:8080") && !Auth.secureCookies(), "the public address defaults to this computer");
            String url = Auth.beginLogin("sess", "/league");
            check(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?") && url.contains("code_challenge_method=S256")
                    && url.contains("response_type=code") && url.contains("scope=openid+email+profile") && url.contains("client_id=id"),
                    "the sign-in address asks Google for a PKCE code flow");
            boolean refused = false;
            try {
                Auth.finishLogin("sess", "code", "never-issued");
            } catch (Auth.AuthException e) {
                refused = true;
            }
            check(refused, "a state we never issued is refused");
            Files.writeString(dir.resolve("auth-config.txt"), "JONGRAI_GOOGLE_CLIENT_ID=id\n");
            boolean half = false;
            try {
                Auth.init(dir, 8080);
            } catch (IllegalArgumentException e) {
                half = true;
            }
            check(half, "a client id without a secret is a configuration error");
            Files.writeString(dir.resolve("auth-config.txt"), "JONGRAI_PUBLIC_URL=http://example.com\n");
            boolean plain = false;
            try {
                Auth.init(dir, 8080);
            } catch (IllegalArgumentException e) {
                plain = true;
            }
            check(plain, "a public address over plain http is refused unless it is this computer");
            Files.writeString(dir.resolve("auth-config.txt"), "JONGRAI_PUBLIC_URL=https://jongrai.example.com/\n");
            Auth.init(dir, 8080);
            check(Auth.secureCookies() && Auth.publicAuthority().equals("jongrai.example.com") && !Auth.configured(),
                    "an https public address turns on Secure cookies; without a client id sign-in stays off");
        } finally {
            Files.deleteIfExists(dir.resolve("auth-config.txt"));
            Files.deleteIfExists(dir);
            Auth.init(Path.of(System.getProperty("java.io.tmpdir")), 8080); // leave the shared settings off
        }
    }
}
