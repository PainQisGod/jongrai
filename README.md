# Project Jongrai

An MLBB (Mobile Legends: Bang Bang) draft helper. Pick and ban for both sides and Jongrai suggests the next move, scores the draft, and reads it against the map you play on. It also tracks the pro leagues of Southeast Asia game by game, shows hero stats, items and builds, and has an optional AI coach that comments on the finished draft. The whole site works in **English and Khmer (ខ្មែរ)**.

> Fan-made, non-commercial project. Not affiliated with or endorsed by Moonton. Game names and artwork belong to their owners.

Hero data is from **patch 2.2.16**.

## Features

- **Draft board**: blue and red sides with 5 picks and 5 bans each, in the official ban/pick order (or picks only). Undo, hold-to-reset, lane changes per pick, an optional **50-second timer** per step (a step that runs out is left blank), and background music.
- **Pick and ban suggestions** from counters, synergies, lane coverage, win rate, ban rate and the chosen map.
- **Map plan**: pick one of the seven arena maps and the board explains what that map changes, then lists heroes to **pick**, to **ban** and to **avoid** on it. It weighs each map's mechanic (more bushes favour ambushers, Golden Turret favours pushers, and so on) together with how heroes have done on that map in pro games.
- **Draft analysis**: Edge breakdown (counters, meta power, synergy, lockdown, coordination), the Lotus profile (nine axes, blue against red) and a coordination check per lane.
- **Pro League**: MPL Cambodia, MPL Philippines, MPL Malaysia, MPL Indonesia and MSL Myanmar, with a league bar and a season bar. Each season has standings, every match by round (regular season, play-ins, upper and lower bracket, grand final), and every game's draft with map, duration and MVP. The tracked seasons hold 330 series and 834 drafted games.
- **Hero Statistics**: all 133 heroes with tier, rates, ratings, matchups and Jongrai's tips, plus a page for each hero.
- **Items and builds**: 104 shop items and 5 curated builds per hero.
- **English / Khmer**: switch with the flags in the top bar. The choice is remembered on that browser.
- **Jongrai's Wisdom**: an AI coach that reads the finished draft, in English or Khmer (optional, needs an API key).
- **Google sign-in** (optional) with admin accounts.
- **Console mode**: the same engine from the command line.

## Quick start

Requires **Java 17 or newer**. Run everything from the project folder (the one that contains `src` and `data`).

PowerShell:

```powershell
.\build.ps1
java -cp out jongrai.WebServer
```

macOS / Linux:

```bash
./build.sh
java -cp out jongrai.WebServer
```

Then open **http://localhost:8080**. Use `java -cp out jongrai.WebServer 9000` for another port.

The build scripts compile `src/` into `out/` with `-Xlint:all -Werror`, so any compiler warning fails the build. Re-run one whenever you change the Java code. Editing only the CSV files in `data/` needs a server restart; editing `web/` needs only a browser refresh.

To start the server from another folder, point it at the project: `java -Djongrai.home=/path/to/jongrai -cp /path/to/jongrai/out jongrai.WebServer`.

Check that the data and the core rules still hold with `java -cp out jongrai.SelfCheck`. It checks league standings, draft ids, map data, Khmer translation coverage and more. CI runs the build and this check on every push.

For OBS, add the page as a **Browser Source**.

## Secrets: never commit them

Two files hold private keys and are listed in `.gitignore`:

| File | What it holds | Template |
|---|---|---|
| `ai-config.txt` | the AI coach's API key | `ai-config.example.txt` |
| `auth-config.txt` | the Google sign-in client secret | `auth-config.example.txt` |

Run `git status` before every commit and make sure neither file is listed. Environment variables with the same names override both files.

## AI coach (optional)

The coach only speaks once both sides have all 5 picks. Without a key, the panel simply says the coach is off.

1. Copy the example file: `copy ai-config.example.txt ai-config.txt`
2. Open `ai-config.txt` and fill in `JONGRAI_AI_KEY` (and the model and base URL if you don't use DeepSeek).
3. Start the server. The banner should say `AI coach ON`.

`JONGRAI_AI_BASE_URL` can point at any OpenAI-compatible service:

| Service | Base URL | Notes |
|---|---|---|
| OpenAI | `https://api.openai.com/v1` | needs billing credit |
| DeepSeek | `https://api.deepseek.com/v1` | e.g. `deepseek-v4-flash` |
| Groq | `https://api.groq.com/openai/v1` | free tier |
| OpenRouter | `https://openrouter.ai/api/v1` | |
| Ollama | `http://localhost:11434/v1` | local, no key needed |

The coach is told which map is chosen and what it changes. In Khmer mode it answers in Khmer using the glossary in `web/lang/km.json`. Results are cached per draft, so reloading the page does not call the API again.

## Google sign-in (optional)

1. Copy the example file: `copy auth-config.example.txt auth-config.txt`
2. Create an OAuth client (type: Web application) in Google Cloud Console and add `<your site>/auth/callback` as a redirect URI.
3. Fill in the client id and secret, and list admin emails in `JONGRAI_ADMIN_EMAILS`.

With no client id and secret the site runs without sign-in. Signing in never makes anyone an admin on its own.

## Pages

| Path | File | What it is |
|---|---|---|
| `/` | `web/home.html` | Home page and meta snapshot |
| `/draft` | `web/draft.html` | The draft board |
| `/league` | `web/pro-league.html` | Pro league standings, matches and drafts |
| `/heroes` | `web/hero-statistics.html` | All heroes, or one hero at `/heroes/<slug>` |
| `/items` | `web/items.html` | All items, or one item at `/items/<slug>` |
| `/about` | `web/about.html` | Data sources and credits |

Shared styling lives in `web/kit.css` (the "Dusk at the Temple" design) plus `web/pages.css` for Items, Pro League, About and 404. The shared scripts are `web/app.js` (data loading and the language system) and `web/kit.js` (top bar and language switch). Each page keeps its own styles in `web/css/<page>.css` and its script in `web/js/<page>.js`. Hero, item and map icons go in `web/icons/`, team logos in `web/teams/`, league logos in `web/league/`.

## Languages

English is written straight into the pages. Khmer lives in `web/lang/km.json`:

- Elements carry `data-i18n` (text), `data-i18n-html`, `data-i18n-title`, `data-i18n-label` or `data-i18n-ph` (placeholder) with a key; scripts call `t(key, english)`.
- `_glossary` in `km.json` fixes the game terms (Hero = តួអង្គ, Pick = ការជ្រើសរើស, Ban = ការបិទ, HP = ឈាម, …). Draft, Meta, Patch, Lane and Play-in stay in English, and sentences end with ។.
- Khmer hero tips are in `web/lang/hero-tips-km.json`.

SelfCheck fails if any key used in the pages, map description or map effect has no Khmer string.

## Data files

All plain CSV with `#` comment lines, so you can edit them freely. A malformed row stops the server with `file:line: reason`, and on startup the server prints a warning for every other problem it finds (a build naming an unknown item, a missing icon, a draft naming an unknown hero, …).

**Heroes and items**

- `heroes.csv`: `name,role,lane,tier,winrate,banrate,counters,synergies,durability,offense,control,tags,spike,tip,pickrate`
  - Dual roles and lanes use `/` (first one is the main). Lists use `;`.
  - `counters` are the heroes this hero is good against. Rates are percentages; a ban rate of 15% or more counts as a meta ban.
  - `spike` is `EARLY`, `MID`, `LATE` or `ALL`. `tip` is the English hero tip as `Section: text | Section: text`; quote it if it contains a comma.
- `items.csv`: `name,slug,tier,cost,stats,passives,availability,categories,icon`
- `hero-builds.csv`: `hero,order,build,lane,items,emblem,battleSpell,blessing,flex`. A build's cost is the sum of its item prices, worked out at startup.
- `hero-icons.csv` maps each hero to its icon file.

**Maps**

- `maps.csv`: `map,description`, the seven arena maps and what each one changes.
- `map-effects.csv`: `map,match,weight,reason`. Each row lifts or lowers every matching hero on that map. `match` is `trait:<TRAIT>`, `tag:<tag>`, `spike:<EARLY|MID|LATE>` or `lane:<LANE>`. For scale, a counter is worth 3 and a synergy 2.
- `hero-traits.csv`: `trait,heroes,meaning`, which heroes count as STEALTH, AMBUSH, PUSH, OBJECTIVE, MOBILE, WALL, IMMOBILE or LONGRANGE.

**Pro leagues**

- `pro-leagues.csv`: `league_id,name,region,country,season,year,status,logo`, one row per season (newest first).
- `pro-teams.csv`: `team_id,league_id,season,name,short_name,logo,founded,city,coach,status`
- `pro-matches.csv`: `match_id,league_id,season,week,day,team1_id,team2_id,team1_score,team2_score,status,scheduled_time,completed_time,match_type,best_of,mvp`
- `pro-drafts.csv`: `draft_id,match_id,league_id,season,game_number,blue_team_id,red_team_id,blue_bans,red_bans,blue_picks,red_picks,blue_side,winner_team_id,patch,map,duration,mvp`
  - `draft_id` is the match id plus `_g<game number>`, and every id includes the season.
  - Picks are listed in lane order: EXP, Jungle, Mid, Gold, Roam.

Standings are not stored: they are calculated from the finished `regular` rows in `pro-matches.csv` (1 point per series win), so a result only needs entering once.

## Console commands

```powershell
java -cp out jongrai.Main
```

```
pick <blue|red> <hero>     ban <blue|red> <hero>
suggest <blue|red>         top 3 picks for that side
suggestbans <blue|red>     top 5 bans for that side
show                       both teams
hero <name>                strong against / weak against / synergies
heroes [role|lane]         list heroes, e.g. "heroes jungle"
help, quit
```

## Project layout

```
src/jongrai/   Java source (engine, analyzer, map stats and effects, pro leagues, web server, AI coach, sign-in, SelfCheck)
web/           pages, kit.css, pages.css, app.js, kit.js, css/, js/, lang/, icons/, teams/, league/, video/
data/          heroes, items, builds, icons, maps, map effects, hero traits, pro-*.csv
docs/          readable overview of every hero's counters and synergies
```

## Tuning

- Suggestion bonuses and penalties: the top of `src/jongrai/SuggestionEngine.java`.
- Draft edge category weights: the top of `src/jongrai/DraftAnalyzer.java`.
- How much the pro record on a map counts: the top of `src/jongrai/MapStats.java`.
- How much each map mechanic counts: the `weight` column of `data/map-effects.csv`.

## Data sources and credits

- Official hero relations (counters, synergies) and hero ratings: Moonton's in-game data, via [p3hndrx/MLBB-API](https://github.com/p3hndrx/MLBB-API) (MIT license).
- Early / mid / late power spikes: [Amirul-Hakimi/MLBB-DRAFT-ANALYSIS](https://github.com/Amirul-Hakimi/MLBB-DRAFT-ANALYSIS) (ISC license). This is one author's judgement, not official data.
- Tiers: November 2026 lane tier lists, mapped to S-D (each hero keeps its best lane).
- Win, pick and ban rates: official [mobilelegends.com/rank](https://www.mobilelegends.com/rank), Past 30 days, all ranks (2026-11-06).
- Item data and hero builds: [mlbbhub.com](https://mlbbhub.com), reviewed against patch 2.2.16 on 2026-10-01.
- Pro league results and drafts: official league broadcasts and sites (including mplcambodia.com) and [Liquipedia](https://liquipedia.net/mobilelegends), entered and checked by hand.
- Map descriptions, map effects, hero traits, hero tips and the Khmer translation: written for this project.
- Hero, item and map artwork belongs to Moonton.
- Full third-party license notices are in [NOTICE](NOTICE).

## Roadmap

- MPL Cambodia Season 11 playoffs (from Nov 12)
- Khmer for the Items and About pages
- More hero tips in English and Khmer
- Older seasons, with the hero icons of their time
- Admin page and per-user hero pools
- Head-to-head matchup win rates to weight counters by strength

## License

The code is released under the [MIT License](LICENSE).

That covers this project's own code and writing only. Hero, item and map names and artwork belong to Moonton, and the third-party data keeps its own license (see [NOTICE](NOTICE)).
