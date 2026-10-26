package jongrai;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Google sign-in (OpenID Connect, authorization-code flow with PKCE), JDK only.
 *
 * Settings come from environment variables or auth-config.txt in the project folder (see
 * auth-config.example.txt). With no client id and secret the site simply runs without sign-in.
 * Nobody can become an admin by signing in: admins are the verified emails listed in JONGRAI_ADMIN_EMAILS.
 */
public final class Auth {

    /** A signed-in visitor. {@code sub} is Google's permanent id for the account; the email can change. */
    public record User(String sub, String email, String name, String picture, boolean admin) {}

    record Claims(String sub, String email, String name, String picture) {}

    record Result(Claims claims, String next) {}

    /** A sign-in that failed for a reason worth telling the visitor. */
    static final class AuthException extends Exception {
        private static final long serialVersionUID = 1L;

        AuthException(String message) { super(message); }
    }

    private record Config(String clientId, String clientSecret, Set<String> admins, String publicUrl,
                          String authUrl, String tokenUrl, String source) {}

    private record Pending(String verifier, String nonce, String sessionId, String next, long expiresAtMs) {}

    private static final String GOOGLE_AUTH = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN = "https://oauth2.googleapis.com/token";
    private static final long PENDING_MS = 10 * 60 * 1000L;
    private static final int PENDING_MAX = 2000;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<String, Pending> PENDING = new HashMap<>();

    private static Config cfg = new Config("", "", Set.of(), "", "", "", "not loaded");

    private Auth() {}

    /** Reads the settings from the environment, then from baseDir/auth-config.txt. Call once at startup. */
    public static void init(Path baseDir, int port) {
        Path file = baseDir.resolve("auth-config.txt");
        Map<String, String> all = new HashMap<>(readConfigFile(file));
        for (String name : List.of("GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET", "ADMIN_EMAILS", "PUBLIC_URL",
                "GOOGLE_AUTH_URL", "GOOGLE_TOKEN_URL")) {
            String v = System.getenv("JONGRAI_" + name);
            if (v != null && !v.isBlank()) all.put("JONGRAI_" + name, v.trim());
        }
        String id = all.getOrDefault("JONGRAI_GOOGLE_CLIENT_ID", "");
        String secret = all.getOrDefault("JONGRAI_GOOGLE_CLIENT_SECRET", "");
        if (id.isEmpty() != secret.isEmpty()) {
            throw new IllegalArgumentException("Set both JONGRAI_GOOGLE_CLIENT_ID and JONGRAI_GOOGLE_CLIENT_SECRET, or neither.");
        }
        String publicUrl = all.getOrDefault("JONGRAI_PUBLIC_URL", "http://localhost:" + port).replaceAll("/+$", "");
        String authUrl = all.getOrDefault("JONGRAI_GOOGLE_AUTH_URL", GOOGLE_AUTH);
        String tokenUrl = all.getOrDefault("JONGRAI_GOOGLE_TOKEN_URL", GOOGLE_TOKEN);
        requireSecure("JONGRAI_PUBLIC_URL", publicUrl);
        requireSecure("JONGRAI_GOOGLE_AUTH_URL", authUrl);
        requireSecure("JONGRAI_GOOGLE_TOKEN_URL", tokenUrl);
        Set<String> admins = java.util.Arrays.stream(all.getOrDefault("JONGRAI_ADMIN_EMAILS", "").split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        String source = id.isEmpty() ? "nowhere - no JONGRAI_GOOGLE_CLIENT_ID in the environment or in " + file
                : System.getenv("JONGRAI_GOOGLE_CLIENT_ID") != null ? "environment variables" : file.toString();
        cfg = new Config(id, secret, admins, publicUrl, authUrl, tokenUrl, source);
    }

    /** Anything that carries a login must be HTTPS; plain http is only for a server on this computer. */
    private static void requireSecure(String name, String url) {
        URI u;
        try {
            u = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " is not a valid URL: " + url);
        }
        boolean local = "localhost".equals(u.getHost()) || "127.0.0.1".equals(u.getHost());
        boolean https = "https".equals(u.getScheme());
        if (u.getHost() == null || !(https || ("http".equals(u.getScheme()) && local))) {
            throw new IllegalArgumentException(name + " must be an https URL (http is only allowed for localhost): " + url);
        }
    }

    private static Map<String, String> readConfigFile(Path file) {
        try {
            if (!Files.isRegularFile(file)) return Map.of();
            Map<String, String> map = new HashMap<>();
            for (String line : Files.readAllLines(file)) {
                line = line.stripLeading().replace("\uFEFF", "").trim(); // editors may add a BOM
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
            return map;
        } catch (IOException e) {
            return Map.of();
        }
    }

    public static boolean configured() { return !cfg.clientId().isEmpty(); }

    /** For the startup banner. Never prints the secret. */
    public static String summary() {
        return configured() ? "ON - Google sign-in, " + cfg.admins().size() + " admin email(s), site address " + cfg.publicUrl()
                : "OFF - no client id found (" + cfg.source() + ")";
    }

    public static String publicUrl() { return cfg.publicUrl(); }

    /** host[:port] of the public address, the one extra Host header the server accepts. */
    public static String publicAuthority() {
        return cfg.publicUrl().isEmpty() ? "" : URI.create(cfg.publicUrl()).getAuthority();
    }

    /** True when the site is served over HTTPS, so the session cookie gets the Secure flag. */
    public static boolean secureCookies() { return cfg.publicUrl().startsWith("https://"); }

    public static boolean isAdmin(String email) {
        return email != null && cfg.admins().contains(email.toLowerCase(Locale.ROOT));
    }

    private static String random() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** PKCE S256: base64url(SHA-256(verifier)). */
    static String pkceChallenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this Java.", e);
        }
    }

    private static String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /** Only a path on this site: anything that could send the visitor elsewhere becomes "/". */
    static String safeNext(String next) {
        if (next == null || next.isEmpty() || next.length() > 300 || next.charAt(0) != '/'
                || next.startsWith("//") || next.contains("\\")) return "/";
        for (int i = 0; i < next.length(); i++) {
            if (next.charAt(i) < 0x20 || next.charAt(i) == 0x7f) return "/";
        }
        return next;
    }

    /** Starts a sign-in for this browser session and returns the Google address to send the visitor to. */
    static String beginLogin(String sessionId, String next) {
        String state = random(), nonce = random(), verifier = random();
        long now = System.currentTimeMillis();
        synchronized (PENDING) {
            PENDING.values().removeIf(p -> p.expiresAtMs() < now);
            if (PENDING.size() >= PENDING_MAX) PENDING.clear(); // only a flood of sign-in starts gets here
            PENDING.put(state, new Pending(verifier, nonce, sessionId, safeNext(next), now + PENDING_MS));
        }
        Map<String, String> q = new LinkedHashMap<>();
        q.put("client_id", cfg.clientId());
        q.put("redirect_uri", cfg.publicUrl() + "/auth/callback");
        q.put("response_type", "code");
        q.put("scope", "openid email profile");
        q.put("state", state);
        q.put("nonce", nonce);
        q.put("code_challenge", pkceChallenge(verifier));
        q.put("code_challenge_method", "S256");
        q.put("prompt", "select_account");
        return cfg.authUrl() + "?" + form(q);
    }

    /**
     * Finishes a sign-in. The state must be one we issued, unused, unexpired and started by this same
     * browser session, which stops someone else's sign-in being planted in a victim's browser.
     */
    static Result finishLogin(String sessionId, String code, String state) throws AuthException {
        Pending p;
        synchronized (PENDING) {
            p = state == null ? null : PENDING.remove(state);
        }
        if (p == null || p.expiresAtMs() < System.currentTimeMillis() || sessionId == null
                || !MessageDigest.isEqual(p.sessionId().getBytes(StandardCharsets.UTF_8), sessionId.getBytes(StandardCharsets.UTF_8))) {
            throw new AuthException("This sign-in expired or was started in a different browser. Please try again.");
        }
        if (code == null || code.isBlank()) throw new AuthException("Google did not send a sign-in code.");
        String idToken = exchange(code, p.verifier());
        return new Result(validateIdToken(idToken, cfg.clientId(), p.nonce(), System.currentTimeMillis() / 1000), p.next());
    }

    private static String exchange(String code, String verifier) throws AuthException {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("grant_type", "authorization_code");
        f.put("code", code);
        f.put("redirect_uri", cfg.publicUrl() + "/auth/callback");
        f.put("client_id", cfg.clientId());
        f.put("client_secret", cfg.clientSecret());
        f.put("code_verifier", verifier);
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.tokenUrl())).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(f))).build();
        try {
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            Object json = Json.parse(r.body());
            if (r.statusCode() != 200) {
                String err = Json.at(json, "error") instanceof String e ? e : "status " + r.statusCode();
                throw new AuthException("Google refused the sign-in (" + err + ").");
            }
            if (!(Json.at(json, "id_token") instanceof String token) || token.isEmpty()) {
                throw new AuthException("Google did not return an ID token.");
            }
            return token;
        } catch (IOException e) {
            throw new AuthException("Could not reach Google to finish signing in.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthException("The sign-in was interrupted. Please try again.");
        } catch (IllegalArgumentException e) {
            throw new AuthException("Google sent a reply we could not read.");
        }
    }

    private static String text(Object node, String key) {
        return Json.at(node, key) instanceof String s ? s : "";
    }

    /**
     * Checks the claims of an ID token: issuer, audience, expiry, our nonce, and a verified email.
     * The signature is not checked on purpose: the token came straight from Google's token endpoint over
     * TLS in the exchange above, which OpenID Connect Core 3.1.3.7 accepts in place of a signature check.
     * Do not reuse this for a token that arrived any other way.
     */
    static Claims validateIdToken(String jwt, String clientId, String nonce, long nowSec) throws AuthException {
        String[] parts = jwt == null ? new String[0] : jwt.split("\\.", -1);
        if (parts.length != 3) throw new AuthException("Google sent an ID token we could not read.");
        Object payload;
        try {
            payload = Json.parse(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new AuthException("Google sent an ID token we could not read.");
        }
        String iss = text(payload, "iss");
        if (!iss.equals("https://accounts.google.com") && !iss.equals("accounts.google.com")) {
            throw new AuthException("The ID token came from an unexpected issuer.");
        }
        Object aud = Json.at(payload, "aud");
        if (!(clientId.equals(aud) || (aud instanceof List<?> l && l.contains(clientId)))) {
            throw new AuthException("The ID token was not issued for this site.");
        }
        if (!(Json.at(payload, "exp") instanceof Number exp) || exp.longValue() + 60 < nowSec) {
            throw new AuthException("The ID token has expired. Please try again.");
        }
        if (!nonce.equals(text(payload, "nonce"))) throw new AuthException("The ID token does not match this sign-in.");
        String sub = text(payload, "sub"), email = text(payload, "email");
        if (sub.isEmpty() || email.isEmpty() || email.length() > 200) throw new AuthException("Google did not share an email address.");
        Object verified = Json.at(payload, "email_verified");
        if (!(Boolean.TRUE.equals(verified) || "true".equals(verified))) {
            throw new AuthException("Your Google email address is not verified.");
        }
        String name = text(payload, "name");
        if (name.isEmpty()) name = email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : email.length());
        if (name.length() > 100) name = name.substring(0, 100);
        String picture = text(payload, "picture");
        return new Claims(sub, email, name, picture.startsWith("https://") && picture.length() <= 500 ? picture : "");
    }
}
