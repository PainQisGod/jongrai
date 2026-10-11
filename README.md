# Project Jongrai - MLBB draft helper (core engine)

Hero roster, tiers and win rates are **patch 2.2.16** data. The web board shows this in the header and in
the coach panel, and the coach prompt pins the model to that patch.

Requires Java 17+. Run from the folder that contains `src` and `data`.

PowerShell:

    mkdir out
    javac -d out (Get-ChildItem src\jongrai\*.java).FullName
    java -cp out jongrai.Main              # uses data/heroes.csv
    java -cp out jongrai.Main my.csv       # or your own hero file

Re-run the javac line whenever you change the code. Editing only data/heroes.csv does not need a recompile.

## Commands
    pick <blue|red> <hero>    ban <blue|red> <hero>
    suggest <blue|red>        top 3 picks for that side
    suggestbans <blue|red>    top 5 bans for that side
    show                      both teams
    hero <name>               a hero's strong-against / weak-against / synergies
    heroes [role|lane]        list heroes, e.g. "heroes jungle"
    help, quit

## data/heroes.csv columns
    name,role,lane,tier,winrate,banrate,counters,synergies,durability,offense,control,tags,spike

- role / lane: dual values use '/', first one is the main (e.g. TANK/MAGE, EXP/JUNGLE)
- winrate / banrate in %; blank banrate = under 15% or unknown
- durability / offense / control = Moonton's in-game hero ratings (10-100); tags = Moonton speciality tags (Burst, Push, ...)
- spike = EARLY / MID / LATE power spike (community dataset by Amirul-Hakimi, ISC license - one author's judgement, edit freely)
- counters = heroes THIS hero is good against; lists use ';'
- counters and synergies come from Moonton's official in-game relations (via github.com/p3hndrx/MLBB-API, MIT)

docs/hero-counters-synergies.md is a readable overview of every hero.

## Tuning the engine
Bonus/penalty numbers are at the top of src/jongrai/SuggestionEngine.java.

## Web draft board

    java -cp out jongrai.WebServer         # then open http://localhost:8080
    java -cp out jongrai.WebServer 9000    # custom port

Run it from the project folder (it reads data/heroes.csv and the pages in web/). Pages:

| Path | File | What it is |
|---|---|---|
| `/` | `web/home.html` | Home page and meta snapshot |
| `/heroes` | `web/hero-statistics.html` | All 133 heroes: tier, rates, ratings, counters |
| `/heroes/<slug>` | same file | One hero, rendered client-side (`/heroes/obsidia`, `/heroes/yi-sun-shin`) |
| `/items` | `web/items.html` | All 104 shop items: tier, cost, stats, passives |
| `/items/<slug>` | same file | One item, rendered client-side (`/items/war-axe`) |
| `/league` | `web/pro-league.html` | Pro draft data (not built yet) |
| `/draft` | `web/draft.html` | The draft board |
| `/about` | `web/about.html` | Data sources and credits |

`web/style.css` holds the shared shell (palette, topbar, buttons, footer); each page keeps its own
layout CSS inline. `/index.html` still resolves to the draft board so old bookmarks keep working.

Hero Statistics is served from `data/heroes.csv` and the icons in `web/icons/`, not scraped from the
web - the official mobilelegends.com site is a JavaScript app whose tier list is not in the served HTML.

Pick a side and Pick/Ban mode, click heroes (or the suggestions) to fill the board. For OBS, add the
page as a Browser Source.

## Items and core builds

`data/items.csv` holds the shop catalog and `data/hero-builds.csv` holds the curated builds, so a hero
page can show what to actually build. Both are plain files - editing them needs no recompile, only a
server restart.

- Every hero has **5 curated builds** (665 in total), each the full item build in buying order, the 3
  core pieces (the first three items), an emblem, a battle spell, an optional blessing and the flex
  swaps. The hero page shows them with the core items marked, and each slot links to that item's page.
- Item and build data are fetched from `mlbbhub.com` (structural item data derived from Moonton and
  Liquipedia, builds hand-curated by that site and reviewed against patch 2.2.16 on 2026-10-01).
- `data/items.csv` columns: `name,slug,tier,cost,stats,passives,availability,categories,icon`.
  Tier 3 is finished equipment, 2 an intermediate piece, 1 components and hero specials.
  `cost` 0 means the source records no purchase price. An item in two shop categories appears once and
  carries both names in `categories`.
- `data/hero-builds.csv` columns: `hero,order,build,lane,items,emblem,battleSpell,blessing,flex,totalCost`.
  `core` is not a column - the engine derives it as the first three items of `items`.
- A field containing a comma, a semicolon or a quote is wrapped in double quotes, with internal quotes
  doubled, because item stat lines and flex text contain all three. Lists inside one field use `;`.
- `icon` is the file in `web/icons/`, matched by name. 102 of the 104 items have one; Demon Boots and
  Magic Boots do not, so they render without an image. Filenames with an apostrophe
  (`Item_Athena's_Shield.png`) are served as-is.
- On startup the server validates the pair and prints a warning per problem: a build naming an item the
  catalog does not have, a core piece missing from its own build, a build for a hero outside the roster,
  or an item with no icon. A clean start prints nothing.

Endpoints: `GET /api/items` returns the catalog with the heroes that core-build (`cores`) or merely use
(`users`) each item. `GET /api/builds?hero=<name>` returns one hero's builds with each item's own icon,
price and `core` flag, so the hero page never has to load the whole catalog. An unknown hero is `[]`.

## Jongrai's Wisdom (AI coach)

The bottom panel is filled by an AI coach instead of the old rule-based notes. It judges the finished
draft and comments on both sides.

Put your key in `ai-config.txt` in the project folder (it is gitignored):

    JONGRAI_AI_KEY=sk-your-key
    JONGRAI_AI_MODEL=gpt-4o-mini
    JONGRAI_AI_BASE_URL=https://api.openai.com/v1

Then start the server and check the banner:

    java -cp out jongrai.WebServer
    # AI coach ON - model gpt-4o-mini via https://api.openai.com/v1

`JONGRAI_AI_BASE_URL` can point at any OpenAI-compatible service:

| Service | Base URL | Model IDs |
|---|---|---|
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-v4-flash`, `deepseek-v4-pro` |
| OpenAI | `https://api.openai.com/v1` | e.g. `gpt-4o-mini`, needs billing credit |
| Groq | `https://api.groq.com/openai/v1` | free tier |
| OpenRouter | `https://openrouter.ai/api/v1` | |
| Ollama | `http://localhost:11434/v1` | local, no key needed |

An environment variable of the same name overrides `ai-config.txt`, so you can also run:

    $env:JONGRAI_AI_KEY = "sk-..."
    java -cp out jongrai.WebServer

Environment variables only reach the server if you set them in the same shell you launch from, before
starting it - the config file has no such requirement and is the easier option on Windows.

Without a key the panel says the coach is off instead of failing. `GET /api/wisdom` is cached per draft,
so re-rendering the page does not re-bill the API. The prompt lives in src/jongrai/AiCoach.java (SYSTEM)
and the engine readout it is given comes from src/jongrai/DraftAnalyzer.java.

**The coach only speaks once the draft is done** - it stays quiet until both sides have all five picks,
so nothing is spent judging a half-built board. `GET /api/wisdom?force=1` overrides that, which is what
the "Read it now anyway" / "Re-read the draft" buttons use.

The request sends `"thinking":{"type":"disabled"}`, so DeepSeek answers without a reasoning trace.
