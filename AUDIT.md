# Project Jongrai: Code Audit

| | |
|---|---|
| **Repository** | `PainQisGod/jongrai` @ `0fa07f6` (`main`) |
| **Date** | 2026-10-13 |
| **Scope** | All of `src/jongrai/*.java` (15 files, ~2,000 LOC), `web/*.html` + `style.css`, every file in `data/`, repo hygiene, README |
| **Method** | Line-by-line read of all source; `javac -Xlint:all --release 17` (clean); scripted validation of every CSV; live probing of a running server, including fake upstream AI endpoints to exercise error paths |
| **Reference standards** | ISO/IEC 25010:2023 (product quality model), ISO/IEC 25012:2008 (data quality), ISO/IEC 5055:2021 (CWE-based code weaknesses), ISO/IEC 40500:2012 (WCAG) |

> **About "ISO compliance":** ISO/IEC 25010 sets out *quality characteristics*, not pass/fail rules, so no codebase is "ISO certified" by a code review. Each finding below is classified under the 25010 or 25012 characteristic it degrades, with a CWE ID where ISO/IEC 5055 applies. Findings marked **Verified** were reproduced against the running server or the data files. **Inspection** means confirmed by reading the code.

---

## 1. Executive summary

Overall verdict: **not fit to deploy beyond a single trusted user on localhost, and only after the Critical and High items are fixed.**

The domain core is small and readable: `Draft`, `Team`, `Hero`, `SuggestionEngine` and `DraftAnalyzer` are clear and well named. The problems sit around that core:

1. **Security.** A live API key is committed. Every state-changing endpoint is a `GET` with no Origin or Host validation, so any website the user visits can drive the app and spend their paid AI tokens. A debug endpoint can be read cross-origin through DNS rebinding.
2. **Availability.** The whole HTTP server is one global lock. One AI call (up to 90 s × 3 retries) freezes every page, icon and stylesheet. Verified: a stylesheet request took 3.7 s while a 4 s AI call was running.
3. **Correctness.** Hand-built JSON produces invalid output on real error paths. The AI retry ladder always sends 3 requests to non-DeepSeek providers and drops JSON mode. "Re-read the draft" does nothing. The AI prompt labels *enemy* heroes as synergy partners. Two search boxes are case-sensitive.
4. **Data quality.** All 665 builds show "0 gold". 5 item links are broken. One hero's tip is truncated by the parser. One hero icon is missing. The pro-league page shows internally inconsistent placeholder data as if it were real.
5. **Maintainability.** The CSV logic exists in 4 copies, the JSON escaping in 2 inconsistent copies, and the front-end helpers in up to 4 copies. There is a lot of dead code, zero tests, no build file, no CI, and committed build output that no longer matches the source.

| Severity | Count |
|---|---|
| Critical | 1 |
| High | 8 |
| Medium | 21 |
| Low | 16 |

---

## 2. Architecture assessment

### Current shape

```
Browser pages (5 HTML files, each with inline CSS + JS, helpers duplicated)
        │  GET /api/*  (state-changing calls are GET too)
        ▼
WebServer  ── static, synchronized, single thread ── global mutable state:
   │           pool, draft, gear, pro, icons, lastPrompt, lastWisdom
   │           hand-written JSON via String.format (~20 places)
   │           domain logic leaking in (pick-turn order, draft completeness)
   ├── Draft / Team / Hero          (domain, mutable lists exposed)
   ├── SuggestionEngine / DraftAnalyzer   (pure scoring, OK)
   ├── AiCoach   (static, re-reads config file per call, hidden static lastPrompt)
   └── HeroRepository / ItemRepository / ProLeagueRepository
               (three CSV parsers, three error policies)
```

### Architectural defects

| # | Defect | ISO 25010 | Why it matters |
|---|---|---|---|
| A1 | The **transport layer holds domain rules.** Pick-turn order (`WebServer.java:342-354`) and draft completeness (`:305`) live in the HTTP class, not in `Draft`. The console `Main` cannot use them. | Maintainability: modularity, reusability | Rules drift between UIs. The console already lacks turn awareness. |
| A2 | **Everything is static and global** (`WebServer` fields `:25-35`, `AiCoach.lastPrompt :66`). There is one `Draft` per server process, shared by every tab and client. | Reliability: fault tolerance; Maintainability: testability | You cannot unit-test without the filesystem. Two tabs (or the README's recommended OBS browser source plus a control tab) silently overwrite each other. |
| A3 | **A single global lock serializes all I/O** (see H2). | Performance efficiency: time behaviour, capacity | One slow upstream call takes down the whole UI. |
| A4 | **No serialization boundary.** JSON is assembled by `String.format` in ~20 places, with two different escapers (`WebServer.str` is wrong, `AiCoach.quote` is right). | Security: integrity; Maintainability | Invalid JSON and injection bugs are structural, not one-offs. |
| A5 | **No data-access abstraction.** The CSV loop (BOM strip, comment skip, header skip, split, parse) is copy-pasted 8 times across 4 classes, with 3 error policies: throw, silently skip the row, or silently return empty. | Maintainability: modifiability; ISO 25012: consistency | One bug fix needs 4 edits. Bad data fails silently in some files and loudly in others. |
| A6 | **Front-end has no shared script.** `$`, `esc`, `slug`, `laneIcon` (×3), tier colors (×4) and the patch string (×10+) are duplicated. `draft.html` has no `esc()` at all. | Maintainability | This is the root cause of H5 (XSS) and H7 (broken links). |

### Recommended target (still JDK-only, no frameworks needed)

Keep the "no libraries" constraint. It suits this project's size. Add three small internal classes:

- `Json` holds one correct string escaper and a tiny writer. All `String.format` JSON goes through it.
- `Csv.read(Path)` returns rows with line numbers. All repositories use it, and all of them fail loudly on malformed rows, citing the line number.
- `App` is an instance holding `pool`, `gear`, `pro`, a `DraftStore` and an `AiCoach` instance. `WebServer` routes to it. This makes the code testable and removes the statics.

On top of that: move turn order into `Draft` (`Draft.nextTurn()` returning `Optional<Phase>`), create one shared `web/app.js`, and add one assert-based self-check class. That is enough. Do **not** add Spring, a DI container or a database: YAGNI at ~2 kLOC.

---

## 3. Findings

Format: **ID — title** · *severity* · location · ISO characteristic · CWE · status. Each finding gives the evidence, then the fix.

### Critical

#### C1 — Live API key committed to Git history
*Critical* · `ai-config.txt` (commit `504381c`) · Security: confidentiality · CWE-798, CWE-540 · **Verified**

`ai-config.txt` is listed in `.gitignore`, but it was committed anyway through a GitHub web upload. Its `JONGRAI_AI_KEY` line holds a real value. Every clone and fork carries it.

**Fix:**
1. **Revoke the key with the provider first.** Rewriting history does not un-leak it.
2. Rewrite history and replace the file with a template:
```bash
git filter-repo --path ai-config.txt --invert-paths
```
3. Add `ai-config.example.txt`. The README already tells users to copy it (README, AI coach step 1), but the file does not exist.

### High

#### H1 — CSRF and DNS rebinding: any website can drive the app and spend the user's AI credit
*High* · `WebServer.java:47, 61-92` · Security: integrity, authenticity · CWE-352, CWE-346 · **Verified**

- Every mutating endpoint (`/api/pick`, `/api/ban`, `/api/remove`, `/api/lane`, `/api/reset`) is reachable by `GET`, so `<img src="http://localhost:8080/api/reset">` on any page works. The HTTP method is never checked: `DELETE /api/reset` returns `200`.
- `/api/wisdom?force=1` triggers a **paid** upstream LLM call. Any page can loop it.
- No `Host` check. `curl -H 'Host: attacker.example' localhost:8080/api/aidebug` returns `200` with the absolute config path, the working directory and the config key names. Through DNS rebinding, a malicious site can read every API response.

**Fix** (top of `handle`):
```java
String host = ex.getRequestHeaders().getFirst("Host");
if (host == null || !(host.equals("localhost:" + port) || host.equals("127.0.0.1:" + port))) {
    send(ex, 421, "text/plain", "Misdirected request"); return;
}
boolean mutating = path.matches("/api/(pick|ban|lane|remove|reset|wisdom)");
if (mutating && !"POST".equals(ex.getRequestMethod())) { send(ex, 405, "text/plain", "POST only"); return; }
String site = ex.getRequestHeaders().getFirst("Sec-Fetch-Site");
if (mutating && site != null && !site.equals("same-origin")) { send(ex, 403, "text/plain", "Cross-site"); return; }
```
Delete `/api/aidebug`, or serve it only when `-Djongrai.debug=true` is set. The client `call()` in `draft.html` already sends `POST` for mutations, so the front end needs no change.

#### H2 — One global lock: an AI call freezes the entire site
*High* · `WebServer.java:47-48, 61` · Performance efficiency: time behaviour, capacity; Reliability: availability · **Verified**

`HttpServer.create(...)` gets no executor, so it runs a single dispatcher thread, and `handle` is also `synchronized`. `wisdomJson` performs a blocking HTTP call of up to `TIMEOUT_SECONDS = 90`, retried up to 3 times (H4), so the worst case is ~270 s with the whole server frozen. Measured with a fake upstream that answers in 4 s: `GET /style.css` took **3.71 s**.

**Fix:** give the server a thread pool. Lock only the draft mutation and the snapshot. Run the AI call outside the lock and cache its future:
```java
server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()); // JDK 21+; else newFixedThreadPool(8)
// handle(): not synchronized. Mutations: synchronized (draftLock) { draft.pick(...); json = stateJson(); }
// wisdom: String prompt; synchronized (draftLock) { prompt = AiCoach.prompt(draft, analysis); }
//         CompletableFuture<String> f = wisdomCache.computeIfAbsent(prompt, p -> CompletableFuture.supplyAsync(() -> coach.judge(p)));
```
Also raise the README's minimum to Java 21, or use a fixed pool.

#### H3 — Hand-built JSON is invalid on real error paths
*High* · `WebServer.java:375-377` (`str`) · Functional suitability: correctness; Security: integrity · CWE-116 · **Verified**

`str()` escapes only `\` and `"`. Control characters, including `\n`, pass through raw. Upstream providers commonly return pretty-printed error bodies. With a fake upstream returning a 3-line `500`, `/api/wisdom?force=1` returned a body that Python's `json.load` rejects with `Invalid control character at: line 1 column 64`. In the browser, `r.json()` throws, so the user sees **"Coach unreachable."** and the real error is hidden. A correct escaper already exists in `AiCoach.quote` (`:326`), so the codebase has two escapers and the server uses the wrong one.

**Fix:** delete `WebServer.str`. Move `AiCoach.quote` into a `Json` utility (`static String str(String s)`, which also maps `null` to `null` instead of throwing an NPE) and use it everywhere.

#### H4 — The AI retry ladder always makes 3 calls to non-DeepSeek providers and drops JSON mode
*High* · `AiCoach.java:212-219` · Performance efficiency: resource utilisation; Functional suitability: correctness · **Verified**

The fallback order is `{json:true, thinking:off}` → `{json:false, thinking:off}` → `{json:false, thinking:default}`. A provider that rejects the non-standard `thinking` field returns 400 on attempts 1 **and** 2, and attempt 3 succeeds **without** `response_format`. The fake upstream logged exactly that sequence. That means 3 round trips (and up to 3 × 90 s inside the global lock) on every call, plus less reliable JSON output. The README's own default (OpenAI, `gpt-4o-mini`) is such a provider.

**Fix:** drop the vendor-specific field before dropping JSON mode, and send it only when it applies:
```java
boolean deepseek = BASE.contains("deepseek");
boolean[][] flavors = deepseek ? new boolean[][]{{true,true},{true,false},{false,false}}
                               : new boolean[][]{{true,false},{false,false}};
```

#### H5 — XSS sinks: untrusted text inserted with `innerHTML`
*High* · `web/draft.html:127, 171, 175-177`; `web/pro-league.html` (all render functions) · Security: integrity · CWE-79 · **Inspection**

- LLM output (`w.verdict`, `w.blue`, `w.red`, `w.closing`) and upstream error text (`w.error`, which includes up to 300 chars of the provider's response body) go straight into `innerHTML`. LLM output is untrusted by definition.
- The team-name `<input>` is re-rendered as `value="${teamNames.blue}"`. Typing a `"` breaks the markup, and the input can inject attributes (self-XSS). It is also re-rendered by every `draw()` call.
- `draft.html` defines no `esc()`, while `hero-statistics.html:113` and `items.html:89` do.

**Fix:** put one `esc()` in a shared `web/app.js` and apply it to every interpolation of data that is not a constant. Set the AI text through `textContent`. Build the team-name inputs once, outside `drawMpl()`.

#### H6 — Every build shows "0 gold"
*High* · `data/hero-builds.csv` column `totalCost`; `ItemRepository.java:96` · ISO 25012: accuracy, completeness · **Verified**

All 665 rows contain `0 gold`. The UI shows "0 gold" on every build card. The real values can be derived: for example, Aamon build 1 sums to 11,710.

**Fix:** compute the total at load time and stop storing it:
```java
int total = items.stream().map(n -> itemsByKey.get(n.toLowerCase())).filter(Objects::nonNull).mapToInt(Item::cost).sum();
```
This requires loading items before builds, which already happens.

#### H7 — Five item links from hero builds are broken
*High* · `web/hero-statistics.html:268` vs `data/items.csv` · Functional suitability: correctness · **Verified**

Build slots link to `/items/${slug(i.name)}`. The JS slug turns `Athena's Shield` into `athena-s-shield`, but the items page looks up the CSV slug `athenas-shield`. The link fails and silently falls back to the full list. The same happens for Berserker's Fury, Haas's Claws, Queen's Wings and Hero's Ring.

**Fix:** include `"slug"` in `WebServer.buildItemsJson` (`it.slug()`) and link with `i.slug`. Never re-derive a key the server already owns.

#### H8 — Committed build output, scraped third-party pages and broken `.gitignore`
*High* · repo root · Maintainability; Compliance (licensing) · **Verified**

- `out/` (compiled classes) is committed although it is ignored, and it is **stale**: it contains `DraftAnalyzer$Note.class`, which no longer exists in the source. Anyone running the committed classes runs code that differs from `src/`.
- `page_text.txt`, `script_debug.txt` and `large_script.json` are raw mlbb.io page captures. They are third-party content with terms-of-service and copyright exposure. `.gitignore` says "never commit the raw captures", but its entry is typo'd as `large_script.json4`.
- 7.7 MB of game-footage MP4s plus 6.1 MB of icons give a 27 MB `.git`. The repo also has no LICENSE or NOTICE file, although it credits MIT- and ISC-licensed sources, and MIT requires including the copyright notice.

**Fix:**
```bash
git rm -r --cached out page_text.txt script_debug.txt large_script.json
```
Then fix the typo (`large_script.json`) in `.gitignore`. Add `LICENSE` and a `NOTICE` that reproduces the p3hndrx/MLBB-API MIT notice and the Amirul-Hakimi ISC notice. Consider Git LFS for media.

### Medium

#### M1 — "Re-read the draft" does nothing
`WebServer.java:328`; `draft.html:178-179` · Functional suitability: correctness · **Inspection**. `force=1` only bypasses the completeness check. The cache is keyed by prompt, so the same draft returns the cached text with no new call. **Fix:** add `refresh=1`, which evicts `wisdomCache.remove(prompt)`, and send it from `#askAgain`.

#### M2 — The AI prompt calls enemy heroes "synergy" partners
`AiCoach.java:181` · Functional suitability: correctness · **Inspection**. `enemy.picks().stream().filter(h::synergizesWith)` is labelled "has synergy with", so the LLM is told the enemy's heroes are this hero's combo partners. **Fix:** use `t.picks().stream().filter(o -> o != h && (h.synergizesWith(o) || o.synergizesWith(h)))`.

#### M3 — The wisdom client can loop forever
`draft.html:172` · Reliability: fault tolerance · **Inspection**. `if(!w.ready){wkey='';return wisdom(force)}` retries immediately, with no limit or backoff, whenever the server says the draft is incomplete. That happens whenever server state is behind client state, for example when another tab removed a pick (A2). **Fix:** render the "waiting" panel instead of recursing.

#### M4 — One draft shared by all clients, with no concurrency control
`WebServer.java:26` · Reliability; Functional suitability: appropriateness · **Inspection**. Last write wins, and clients never learn about other clients' changes. **Fix:** at minimum, add a `version` counter to `/api/state` and reject mutations that carry a stale version (`409`). Ideally, keep one draft per browser session (a cookie mapped to a `Draft`).

#### M5 — Search is case-sensitive on Heroes and Items
`hero-statistics.html:296`, `items.html:177` · Interaction capability: operability · **Inspection**. `f.q` is stored as typed but compared against `toLowerCase()` names, so typing "Aamon" or "War Axe" finds nothing. `draft.html` gets this right. **Fix:** `f.q = e.target.value.toLowerCase()`.

#### M6 — Every keystroke in the draft search fires 6 API calls and rebuilds the videos
`draft.html:220, 107-114` · Performance efficiency · **Inspection**. `oninput=draw` re-renders both teams, the MPL board (re-creating `<video>` elements) and the grid, and calls `sugg()` (2 requests) and `analyze()` (2 requests plus 2 duplicate `auto-suggest` calls). All of these queue on the global lock. **Fix:** split into `drawGrid()`, which is local only and bound to the filters, and `refresh()`, which runs only after a state change. Call `autoSuggest` once.

#### M7 — Static media: no validators, no Range support, whole file in memory
`WebServer.java:401-426, 412, 424` · Performance efficiency; Compatibility: interoperability · **Verified**. `Cache-Control: no-cache` without `ETag` or `Last-Modified` turns every revalidation into a full download. `Range: bytes=0-99` gets a `200` with all 1,896,823 bytes. Safari and iOS require range support to play MP4, and seeking is broken everywhere. Every request does `readAllBytes`. **Fix:** send `Last-Modified` from `Files.getLastModifiedTime`, honour `If-Modified-Since`, use `max-age=86400` for icons and video, implement single-range `206` responses, and stream with `Files.copy(file, ex.getResponseBody())`.

#### M8 — Suggestions ignore the draft phase
`WebServer.java:91-92, 340-369`; `SuggestionEngine.java:27` · Functional suitability: appropriateness · **Verified**. `/api/suggest` still returns picks for a team that already has 5. Auto-suggest derives the turn only from the total pick count, so it ignores the ban phase and any manual out-of-order picks. **Fix:** move turn logic into `Draft.nextTurn()` (A1) and return `[]` when the side cannot pick or ban.

#### M9 — Analyzer reports nonsense for empty and edge cases
`DraftAnalyzer.java:248-255` · Functional suitability: correctness · **Verified**. An empty team reports `spikeTiming: "EARLY"`, and the `"MIXED"` branch can never run because one of three counts is always the maximum. **Fix:** return `"NONE"` when there are no picks. Return `"MIXED"` when the maximum is tied, or drop the value from the API contract.

#### M10 — "Has tank" and "missing roles" contradict each other
`DraftAnalyzer.java:239-260` · Functional suitability: correctness · **Inspection**. `hasTank` uses any role (`countRole` → `hasRole`), but `missingRoles` uses only the primary role. A team with Fredrinn (FIGHTER/TANK) shows "✓ Tank" and "MISSING ROLES: TANK" side by side. This affects Carmilla, Chip, Fredrinn, Hilda, Lolita and others. **Fix:** build `roles` from `h.roles()` (flatMap), or use primary roles in both places, and document the choice.

#### M11 — Undocumented `ALL` spike value is handled three different ways
`data/heroes.csv` (2 heroes), `Hero.java:133` doc, `DraftAnalyzer.java:301`, `hero-statistics.html:105, 160` · ISO 25012: consistency · **Verified**. The docs say spikes are EARLY, MID or LATE. In practice `ALL` gets a neutral 6/6 in the profile, is ignored by coordination, has its own label on one page, and is shown as "MID GAME" in the tip. **Fix:** make the spike an enum `Spike {EARLY, MID, LATE, ALL}` parsed at load time (which rejects typos) and handle `ALL` explicitly.

#### M12 — The hero CSV parser truncates text; the `tip` field is dead
`HeroRepository.java:236, 254`; `WebServer.heroesJson` · ISO 25012: accuracy · **Verified**. `split(",")` cuts Aamon's tip ("Your job is to wait for the backline, then burst...") at the first comma, because that row parses as 15 fields. The tip is never sent to the client anyway, and the page generates a synthetic one instead. **Fix:** use the shared quote-aware CSV reader. Then either serve `tip` and prefer it over the generated text, or delete the column and record field.

#### M13 — Four CSV parsers, three error policies
`HeroRepository`, `ItemRepository.splitCsv`, `ProLeagueRepository.splitCsv` (verbatim copy), `WebServer.loadIcons` · Maintainability: modifiability · **Inspection**. `number()` and `splitList()` are also duplicated. Heroes and items throw on a short row. Pro data **silently skips** it. Missing item or pro files silently produce an empty site section. **Fix:** one `Csv` class (A5) with a single policy: fail at startup with `file:line: reason`.

#### M14 — `number()` silently misparses
`ItemRepository.java:209`, `ProLeagueRepository.java:247` · ISO 25012: accuracy · **Inspection**. It reads the first run of digits, so `-5` becomes 5, `1.5` becomes 1 and `TBD 3` becomes 3, all without warning. **Fix:** strip a known unit suffix explicitly (`gold`), then call `Integer.parseInt`, and report the line number on failure.

#### M15 — The web server never validates hero data; asset references are broken
`WebServer.java:45`; `data/hero-icons.csv`; `data/hero-builds.csv` · ISO 25012: completeness · **Verified**. `HeroRepository.validate` runs only in the console `Main`, although the README says the server checks the data. `ML_icon_Yi_Sun-shin.png` is referenced but missing. Two talents are misspelt, `Impure Range` and `Master ASsassin`, so their icons 404. `ItemRepository.validate`'s "core not in item list" check can never fire, because `core` is defined as `items.subList(0,3)` (`:84`). **Fix:** call both validators at server start. Add icon-file existence to validation. Fix the three data typos. Remove the tautological check, or make `core` an explicit CSV column.

#### M16 — Encapsulation leaks; "immutable" records hold mutable collections
`Team.java:24-25`; `Draft.java:47, 70`; `Hero` (sets from `Collectors.toSet()`); `HeroBuild` (`ArrayList` plus a `subList` view) · Maintainability: modularity; Reliability · **Inspection**. Any caller can `team.picks().add(...)` and bypass `MAX_PICKS` and the uniqueness rules. **Fix:** make `picks()` and `bans()` return `List.copyOf`/`unmodifiableList` and add `Team.addPick` and `addBan`. Use `Set.copyOf`/`List.copyOf` in the record constructors.

#### M17 — Paths depend on the working directory
`WebServer.java:38-42, 121, 129, 402, 418` vs `AiCoach.locateConfig` · Flexibility: installability · **Inspection**. Launching from any other folder fails with `NoSuchFileException`, while the AI config alone gets an elaborate multi-location lookup. **Fix:** resolve one `baseDir` (system property `jongrai.home`, otherwise `codeDir().getParent()`, otherwise the CWD) and use it everywhere.

#### M18 — Unhandled exceptions produce no response at all
`WebServer.java:102-104` · Reliability: fault tolerance · **Inspection**. Only `IllegalArgumentException` and `IllegalStateException` are caught. Any `NullPointerException` (for example from a CSV field) or `UncheckedIOException` escapes, the JDK server drops the exchange, and the client's `r.json()` throws. Also, `str(e.getMessage())` throws an NPE if the message is `null`, so the error handler itself can crash. **Fix:** add a final `catch (RuntimeException e)` that logs and returns `500 {"error":"Internal error"}`, and use the null-safe `Json.str`.

#### M19 — The front end has no network-error handling and can fail to start
`draft.html:96-97, 225` · Reliability · **Inspection**. `call()` always does `await r.json()`. A dropped connection or an HTML error causes an unhandled rejection and the board freezes silently. `localStorage.getItem` throws `SecurityError` when site storage is blocked. That call runs in the init IIFE *before* `state` is loaded, so the board never draws. **Fix:** wrap `call()` in try/catch and show `#msg`. Wrap storage access in `try{}catch{}`.

#### M20 — Pro-league data is inconsistent placeholder data shown as fact
`data/pro-*.csv`, `/league` · ISO 25012: accuracy, credibility · **Verified**. The standings show every team with 14 played, but only 4 matches per team exist. Coaches are named "Coach Kim" and "Coach Lee". The README says the page is "not built yet", yet it is linked in every nav bar. Leagues are keyed by `id` alone, so a second season of the same league overwrites the first. **Fix:** label the page as sample data, or remove it from the nav until it has a real source. Key leagues by `(id, season)`. Derive standings from matches instead of storing both.

#### M21 — Accessibility gaps (ISO/IEC 40500 / WCAG 2.1 AA)
`draft.html:105-106, 184`; `style.css` · Interaction capability: inclusivity, operability · **Inspection**.
- "Remove pick" rows and the suggestion `<li>` items are clickable `div`/`li` elements with no `tabindex`, role or key handler, so a keyboard user cannot undo a pick or accept a suggestion (WCAG 2.1.1).
- The remove affordance appears only on hover (1.4.13).
- No `:focus-visible` styling is defined anywhere.
- Background music autoplays in a hidden iframe (1.4.2 requires a mechanism to stop it, and the default is muted, which passes, but the iframe still loads YouTube on every visit once a track is saved).

**Fix:** use `<button>` for every clickable element, add a visible focus ring, and load `youtube-nocookie.com` only after a user gesture.

### Low

| ID | Finding | Location | ISO 25010 | Fix |
|---|---|---|---|---|
| L1 | Locale-dependent number formatting (`String.format` without `Locale.ROOT`) prints `59,4%` on id_ID/de_DE systems, which matters for MLBB's main markets. The rest of the code uses `Locale.ROOT`. | `SuggestionEngine.java:85, 108, 113`; `Main.java:82` | Flexibility: adaptability | Pass `Locale.ROOT`. |
| L2 | Error messages leak internals: `No enum constant jongrai.Draft.Side.GREEN`. | `WebServer.java:133` | Security: confidentiality | Parse the side explicitly like `Main.parseSide`. |
| L3 | Soft 404: `/heroes/anything` and `/items/anything` return `200`. | `WebServer.java:96-98` | Functional suitability | Return 404 when the slug is unknown (the server has the data). |
| L4 | The key is re-read from disk on every `configured()` call (`debugJson` reads the file 4×), while model and base URL are frozen at startup, so half the config hot-reloads. The UI says "restart the server". | `AiCoach.java:72-101` | Maintainability | Read the config once into a record at startup. |
| L5 | Hidden temporal coupling: `judge()` stores `lastPrompt` in a static that `requestBody()` reads, which is only safe because of the global lock (H2). | `AiCoach.java:66, 207, 244` | Reliability | Pass the prompt as a parameter. |
| L6 | The hand-rolled JSON field scanner returns the *first* `"blue"` anywhere. If the verdict text contains `"blue"` in quotes, it extracts the wrong span. | `AiCoach.java:287-324` | Correctness | Locate `choices[0].message.content` structurally, then parse the 4 known keys in order. |
| L7 | The prompt includes "Available picks still open", the first 25 heroes alphabetically. The prompt is only sent for a finished draft, so this is meaningless and wastes tokens. | `AiCoach.java:159-161` | Performance efficiency | Delete it. |
| L8 | Redundant work: the analysis is computed twice per wisdom call, coordination is computed by two endpoints, `coveredLanes()` and `countRole()` are recomputed for each of 133 candidates, and `/api/items` rebuilds both indexes per request. | various | Performance efficiency | Hoist them. Merge `/api/coordination` into `/api/analysis`. Precompute the indexes at load. |
| L9 | Dead code: `iconFiles`, `heroKeys()`, `AiCoach.settingsJson`, `ProLeagueRepository.activeLeagues/upcomingMatches/league/team/match/draft`, `HeroBuild.emblemParts/spell/bless`, `Item.hasCategory`, the `Hero.tip()` override, the duplicate `/style.css` branch (`WebServer.java:94`), `PAGES."/"`, and `isRoamBlessing` (both branches render the same chip, `hero-statistics.html:274-275`). | various | Maintainability: analysability | Delete it. |
| L10 | Magic indexes: `cats.get(2)`/`cats.get(3)` and the `axis(t, i)` switch over a parallel `names[]` array. Reordering silently corrupts the score. | `DraftAnalyzer.java:213, 278-297` | Maintainability | Use named locals, or a `List<Axis>` built from `(name, Function<Team,Double>)` pairs. |
| L11 | Hard-coded facts: "Patch 2.2.16" in 10+ places across Java and HTML, "All 133 heroes", "All 104 shop items". | various | Maintainability | Serve `/api/meta {patch, heroCount, itemCount}` and set `PATCH` in one Java constant. |
| L12 | Documentation drift: the README references a missing `ai-config.example.txt`, shows OpenAI defaults while the shipped config targets DeepSeek, says `/league` is "not built yet", and omits `tip` from the heroes.csv columns. The startup banner prints `gpt-4o-mini`. Comments are stale: "seven traits" (there are nine), "six-item build" (5 builds have five), "serves web/index.html". | README; `WebServer.java:22, 54-57`; `DraftAnalyzer.java:276`; `HeroBuild.java:247` | Maintainability | Correct them. |
| L13 | Tier and category anchors have `href` *and* a JS click handler without `preventDefault`, so the page fully reloads and the JS work is wasted. A selected item category cannot be cleared. | `hero-statistics.html:292, 312`; `items.html:172-174, 185` | Interaction capability | Call `e.preventDefault()` and `pushState`. Add an "All" chip. |
| L14 | Sequential fetches where parallel would do. `teamsById` is never cleared between leagues. | `items.html:180-181`; `pro-league.html` | Performance efficiency | Use `Promise.all` and reset the map. |
| L15 | `.vscode/settings.json` with personal JVM flags (`-Xmx4G`) is committed. | `.vscode/` | Maintainability | Ignore it, or keep only the shared cSpell words. |
| L16 | The port argument is unvalidated (`Integer.parseInt` stack trace on bad input). | `WebServer.java:43` | Interaction capability: user error protection | Validate and print usage. |

---

## 4. Testability and process (ISO 25010 maintainability: testability)

| Gap | Status | Minimum fix |
|---|---|---|
| Automated tests | **None.** Every scoring rule, parser and the turn order are untested. | One `src/jongrai/SelfCheck.java` with `assert`-based checks: CSV quoting, the Aamon tip round-trip, the slug parity of H7, empty-team analysis (M9), the role contradiction (M10), JSON escaping of `\n` (H3), and pick-turn order. Run it with `java -ea`. |
| Build definition | **None.** `javac` by hand, with different commands per OS in the README. | Either a 10-line `build.sh`/`build.ps1`, or a minimal `pom.xml`. |
| CI | **None.** | A GitHub Action: `javac -Xlint:all -Werror` + `java -ea jongrai.SelfCheck` + the data validators. |
| Secret scanning | **None.** This is how C1 happened. | Enable GitHub secret scanning and push protection, and add a pre-commit `gitleaks` hook. |

---

## 5. ISO/IEC 25010:2023 scorecard

Rated against what a local single-user tool reasonably needs, on a scale of 1 (poor) to 5 (strong).

| Characteristic | Score | Main drivers |
|---|---|---|
| Functional suitability | 2 | H3, H6, H7, M1, M2, M5, M8, M9, M10 |
| Performance efficiency | 2 | H2, H4, M6, M7 |
| Compatibility | 3 | M7 (Safari/iOS video), L1 |
| Interaction capability | 3 | M5, M21, L13 |
| Reliability | 2 | H2, M3, M4, M18, M19 |
| Security | 1 | C1, H1, H5 |
| Maintainability | 2 | A1-A6, M13, M16, L9, no tests |
| Flexibility | 2 | M17, L1, L11 |
| Safety | n/a | No safety-relevant function |
| *Data quality (ISO 25012)* | 2 | H6, M11, M12, M14, M15, M20 |

---

## 6. Recommended fix order

1. **Today:** C1 (revoke and purge the key), H1 (Host check plus POST-only mutations; this also stops the token-spend abuse).
2. **This week:** H2 + H4 (server thread pool and retry order), H3 (one JSON escaper), H5 (shared `esc`), H6 + H7 + M15 (data fixes and validators at startup), H8 (repo hygiene).
3. **Next:** M1-M3 and M5-M10 (visible behaviour bugs), then the A5/M13 CSV consolidation and A1 turn-order move, then the self-check and CI from section 4.
4. **Backlog:** the Low table.


---

## 7. Resolution

Verified with `./build.sh` (`-Xlint:all -Werror`), `java -cp out jongrai.SelfCheck`, the probes in the handoff (421, 405, multi-line upstream error, 206 range, 404 slug, a static request during a 4 s AI call) and a click-through of every page.

### Critical and High

- **C1**: **Owner action required.** Revoke the key, then purge `ai-config.txt` from history with `git filter-repo`. Done here: the file is untracked (`git rm --cached`, the local copy is untouched) and `ai-config.example.txt` exists.
- **H1**: Fixed. `Host` is checked (421). Mutating routes need `POST` (405) and a same-origin `Sec-Fetch-Site` when present (403). Every other route is `GET` only. `/api/wisdom` is now `POST`. `/api/aidebug` and `AiCoach.debugJson` are deleted.
- **H2**: Fixed. A fixed pool of 8 threads, `handle` is no longer `synchronized`, and one `DRAFT_LOCK` guards the draft. The AI call runs outside the lock through a cached `CompletableFuture`. A static file took 4 ms during a 4 s AI call. The README minimum stays Java 17, so no virtual threads.
- **H3**: Fixed. `WebServer.str`/`strList` are gone and everything uses `Json.str`/`Json.list`. An upstream error body with newlines now round-trips as valid JSON.
- **H4**: Fixed. DeepSeek: `{json+noThinking}, {json}, {plain}`. Other providers: `{json}, {plain}`. JSON mode is never dropped while `thinking` is still sent.
- **H5**: Fixed. Every data interpolation in `draft.html` and `pro-league.html` goes through the shared `esc()`, including AI text and errors. The team-name inputs are created once outside the redraw.
- **H6**: Fixed. `totalCost` is computed at load from the item prices and the CSV column is removed (Aamon build 1 = 11,710).
- **H7**: Fixed. The server sends `slug` for items and heroes, and the browser no longer derives slugs.
- **H8**: Fixed. `out/`, `page_text.txt`, `script_debug.txt` and `large_script.json` are untracked and the `.gitignore` entry is correct. `NOTICE` added. **Owner action required** for a project LICENSE and the Git LFS migration.

### Medium

- **M1**: Fixed. `refresh=1` evicts the cached read, and "Re-read the draft" sends it.
- **M2**: Fixed. The prompt lists own-team synergy partners, in either direction.
- **M3**: Fixed. A `ready:false` reply renders the waiting panel and never recurses.
- **M4**: Fixed. `version` is in the state, and a stale `v` gets a 409 carrying the current state. The client adopts it and shows the message. The board is still one shared draft per server (see A2).
- **M5**: Fixed. Both search boxes lowercase the query.
- **M6**: Fixed. `drawGrid()` is local only and `refresh()` fetches suggestions and analysis once per state change (measured: 10 picks = 10 analysis calls and 10 auto-suggest calls).
- **M7**: Fixed. `Last-Modified`/304, a single `Range` (206/416), `max-age=86400` for icons and video, and streaming instead of `readAllBytes`. Pages and scripts keep `no-cache` with `Last-Modified`.
- **M8**: Fixed. `suggest` and `suggestBans` return nothing for a full side, and auto-suggest uses `Draft.nextPick()`.
- **M9**: Fixed. An empty team gives `NONE`, and a tied top spike gives `MIXED`.
- **M10**: Fixed. Roles are built from all of a hero's roles, so `hasTank` and `missingRoles` agree.
- **M11**: Fixed. `Spike` is an enum parsed at load, and `ALL` is handled explicitly in the analyzer (7/7) and on the hero page.
- **M12**: Fixed. The quote-aware reader loads Aamon's full tip (the CSV field is now quoted), and the tip is served and shown ahead of the generated text.
- **M13**: Fixed. One `Csv` class. Every data file is required and a bad row fails with `file:line`.
- **M14**: Fixed. Numbers are strict (an optional `gold` suffix only), with the line number on failure.
- **M15**: Fixed. All validators run at startup, including icon-file existence. The three data typos are corrected (`Yi_Sun-Shin`, `Impure Rage`, `Master Assassin`). The tautological `core` checks are removed.
- **M16**: Fixed. Team lists are read-only views with package-private mutators, and the records copy their collections.
- **M17**: Fixed. One `baseDir` (`-Djongrai.home`, else the working directory, else the folder above `out/`) is used for data, web and the AI config.
- **M18**: Fixed. A final `catch (RuntimeException)` logs and returns 500, and `Json.str` is null-safe.
- **M19**: Fixed. `call()` never throws and `store` wraps `localStorage`.
- **M20**: Fixed as labelled and validated. A "Sample data, not official results" banner, leagues keyed by `id|season`, and startup warnings for pro drafts with unknown heroes, standings that disagree with the match list, and unknown teams. The four "played" warnings are expected for the placeholder data, which was not changed. The nav link stays.
- **M21**: Fixed. Remove rows and suggestions are `<button>`s with visible "remove" text, a `:focus-visible` ring is added, and YouTube is loaded from `youtube-nocookie.com` only after the user picks a track or presses play.

### Low

- **L1**: Fixed. `Locale.ROOT` everywhere, including `Main`.
- **L2**: Fixed. `Draft.Side.parse`, and `Lane`/`Role`/`Spike.parse` give friendly messages.
- **L3**: Fixed. Unknown hero and item slugs return the 404 page.
- **L4**: Fixed. The AI config is read once into a record, with `timeout > 0` and a finite temperature validated.
- **L5**: Fixed. The static `lastPrompt` is deleted and the prompt is passed as a parameter.
- **L6**: Fixed. The reply is parsed with `Json.parse` and read from `choices[0].message.content`, then the first-`{`-to-last-`}` object. A reply containing a quoted "blue" parses correctly.
- **L7**: Fixed. "Available picks still open" is removed from the prompt.
- **L8**: Fixed. Lane and role counts are hoisted, the item indexes are precomputed, coordination is merged into `/api/analysis` and analysis is computed once per wisdom request.
- **L9**: Fixed. The dead code listed is deleted (`coordTeam` in `draft.html` too).
- **L10**: Fixed. Named locals and a list of `(name, function)` axes replace the magic indexes.
- **L11**: Fixed. `HeroRepository.PATCH` and `/api/meta` feed the pages through `data-patch`, `data-hero-count` and `data-item-count`.
- **L12**: Fixed. The README, banner and stale Javadoc are corrected.
- **L13**: Fixed. `preventDefault` plus `pushState` on the tier bar, and `preventDefault` on the category chips, with an "All" chip.
- **L14**: Fixed. `Promise.all` on the items page, and `teamsById` is reset per league.
- **L15**: Fixed. The JVM flags are removed from `.vscode/settings.json` (cSpell words kept).
- **L16**: Fixed. An invalid port prints usage and exits 2.

### Architecture

- **A1**: Fixed. Turn order and completeness live in `Draft` (`nextPick`, `isComplete`).
- **A2**: Partly fixed. Statics remain, but the draft is guarded by one lock and carries a version. **Won't fix**: per-session drafts and an `App` instance, which would be over-engineering for a single local user at this size.
- **A3**: Fixed (see H2).
- **A4**: Fixed. `Json` is the one serialization boundary.
- **A5**: Fixed. `Csv` is the one data-access path and fails loudly.
- **A6**: Fixed. `web/app.js` holds the shared helpers, and the per-page copies are deleted.

### Also fixed (not in the audit)

- `home.html` had one stray `}` that was a syntax error, so the home page's stats and tier chart never rendered. Removed.
- Added `SelfCheck`, `build.sh`/`build.ps1` and a CI workflow (build, `SelfCheck`, gitleaks). **Owner action required** to enable GitHub secret scanning and push protection.
