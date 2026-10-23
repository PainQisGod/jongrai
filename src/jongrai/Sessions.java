package jongrai;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * One draft board per visitor, found by a random cookie, so two people on the site never share a board.
 * A visitor who signs in keeps their board but gets a brand-new id (so an id planted before login is useless)
 * and a longer life. Idle sessions expire and the table is capped so a flood of visitors cannot fill memory.
 */
final class Sessions {

    static final String COOKIE = "jsid";
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{43}"); // 32 random bytes, base64url
    private static final SecureRandom RANDOM = new SecureRandom();

    /** A visitor's board and, once signed in, who they are. {@code lastSeen} is only touched under the Sessions lock. */
    static final class Session {
        final String id;
        final Draft draft;
        final Auth.User user; // null until the visitor signs in
        private long lastSeen;

        private Session(String id, Draft draft, Auth.User user, long now) {
            this.id = id;
            this.draft = draft;
            this.user = user;
            this.lastSeen = now;
        }
    }

    private final Map<String, Hero> pool;
    private final long idleMillis;
    private final long signedInIdleMillis;
    private final int max;
    private final LongSupplier clock;
    private final Map<String, Session> byId = new HashMap<>();

    Sessions(Map<String, Hero> pool) {
        this(pool, 6L * 60 * 60 * 1000, 14L * 24 * 60 * 60 * 1000, 5000, System::currentTimeMillis);
    }

    Sessions(Map<String, Hero> pool, long idleMillis, int max, LongSupplier clock) {
        this(pool, idleMillis, idleMillis * 4, max, clock);
    }

    Sessions(Map<String, Hero> pool, long idleMillis, long signedInIdleMillis, int max, LongSupplier clock) {
        this.pool = pool;
        this.idleMillis = idleMillis;
        this.signedInIdleMillis = signedInIdleMillis;
        this.max = max;
        this.clock = clock;
    }

    private long limit(Session s) { return s.user != null ? signedInIdleMillis : idleMillis; }

    private static String newId() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /** The live session for this id, or null when the id is missing, malformed, unknown or expired. */
    synchronized Session find(String id) {
        if (id == null || !ID.matcher(id).matches()) return null;
        Session s = byId.get(id);
        if (s == null) return null;
        long now = clock.getAsLong();
        if (now - s.lastSeen > limit(s)) {
            byId.remove(id);
            return null;
        }
        s.lastSeen = now;
        return s;
    }

    synchronized Session create() {
        long now = clock.getAsLong();
        if (byId.size() >= max) {
            byId.values().removeIf(s -> now - s.lastSeen > limit(s));
            while (byId.size() >= max) { // still full of live sessions: drop the least recently used visitor
                Session victim = null;
                for (Session s : byId.values()) { // signed-in visitors are dropped last
                    boolean better = victim == null || (victim.user != null && s.user == null)
                            || ((victim.user == null) == (s.user == null) && s.lastSeen < victim.lastSeen);
                    if (better) victim = s;
                }
                byId.remove(victim.id);
            }
        }
        Session s = new Session(newId(), new Draft(pool), null, now);
        byId.put(s.id, s);
        return s;
    }

    /** Signs a visitor in: same board, new id, the old id stops working. */
    synchronized Session login(Session old, Auth.User user) {
        byId.remove(old.id);
        Session s = new Session(newId(), old.draft, user, clock.getAsLong());
        byId.put(s.id, s);
        return s;
    }

    synchronized void remove(Session s) { byId.remove(s.id); }

    synchronized int size() { return byId.size(); }

    /** The jsid value in a Cookie request header ("a=1; jsid=abc; b=2"), or null. */
    static String idFrom(String cookieHeader) {
        if (cookieHeader == null) return null;
        for (String part : cookieHeader.split(";")) {
            String p = part.trim();
            if (p.startsWith(COOKIE + "=")) return p.substring(COOKIE.length() + 1);
        }
        return null;
    }

    /** The Set-Cookie value: HttpOnly so page scripts cannot read it, Lax so other sites cannot drive it. */
    String setCookie(Session s, boolean secure) {
        return COOKIE + "=" + s.id + "; Path=/; Max-Age=" + (limit(s) / 1000) + "; HttpOnly; SameSite=Lax"
                + (secure ? "; Secure" : "");
    }

    /** Tells the browser to forget the cookie (sign out). */
    static String clearCookie(boolean secure) {
        return COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax" + (secure ? "; Secure" : "");
    }
}
