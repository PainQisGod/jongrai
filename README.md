# Project Jongrai

An MLBB (Mobile Legends: Bang Bang) draft helper. Pick and ban heroes for both sides, and Jongrai suggests picks and bans, scores the draft, and shows hero stats, items and builds. It also has an optional AI coach that comments on the finished draft.

> Fan-made, non-commercial project. Not affiliated with or endorsed by Moonton. Game names and artwork belong to their owners.

Data is from **patch 2.2.16**.

## Features

- **Draft board**: blue and red sides with 5 picks and 5 bans each. Click a filled slot to undo a misclick, and change a pick's lane with the dropdown.
- **Pick and ban suggestions**: based on counters, synergies, lane coverage, win rate and ban rate.
- **Draft analysis**: overall draft edge, a tactical profile (radar chart and bars) and short coach notes.
- **Hero Statistics**: all 133 heroes with tier, rates, ratings and counters, plus a page for each hero.
- **Items and builds**: 104 shop items and 5 curated builds per hero (665 in total).
- **Jongrai's Wisdom**: an AI coach that reads the finished draft (optional, needs an API key).
- **Console mode**: the same engine from the command line.

## Quick start

Requires **Java 17 or newer**. Run everything from the project folder (the one that contains `src` and `data`).

macOS / Linux:

```bash
./build.sh
java -cp out jongrai.WebServer
```

PowerShell:

```powershell
.\build.ps1
java -cp out jongrai.WebServer
```

Then open **http://localhost:8080**. Use `java -cp out jongrai.WebServer 9000` for another port.

The build scripts compile `src/` into `out/` with `-Xlint:all -Werror`, so any compiler warning fails the build. Re-run one whenever you change the code. Editing only the CSV files in `data/` needs just a server restart.

To start the server from another folder, point it at the project: `java -Djongrai.home=/path/to/jongrai -cp /path/to/jongrai/out jongrai.WebServer`.

Check that the data and the core rules still hold with `java -cp out jongrai.SelfCheck`. CI runs the build and this check on every push.

For OBS, add the page as a **Browser Source**.

## AI coach (optional)

The coach only speaks once both sides have all 5 picks. Without a key, the panel simply says the coach is off.

1. Copy the example file:

```powershell
   copy ai-config.example.txt ai-config.txt
```

2. Open `ai-config.txt` and fill in your key:

```
   JONGRAI_AI_KEY=your-key
   JONGRAI_AI_MODEL=deepseek-v4-flash
   JONGRAI_AI_BASE_URL=https://api.deepseek.com/v1
   JONGRAI_AI_TIMEOUT=90
```

3. Start the server. The banner should say `AI coach ON`.

**Never commit `ai-config.txt`.** It is listed in `.gitignore`. Environment variables with the same names override the file.

`JONGRAI_AI_BASE_URL` can point at any OpenAI-compatible service:

| Service | Base URL | Notes |
|---|---|---|
| OpenAI | `https://api.openai.com/v1` | needs billing credit |
| DeepSeek | `https://api.deepseek.com/v1` | e.g. `deepseek-v4-flash` |
| Groq | `https://api.groq.com/openai/v1` | free tier |
| OpenRouter | `https://openrouter.ai/api/v1` | |
| Ollama | `http://localhost:11434/v1` | local, no key needed |

Results are cached per draft, so reloading the page does not call the API again.

## Pages

| Path | File | What it is |
|---|---|---|
| `/` | `web/home.html` | Home page and meta snapshot |
| `/heroes` | `web/hero-statistics.html` | All heroes, or one hero at `/heroes/<slug>` |
| `/items` | `web/items.html` | All items, or one item at `/items/<slug>` |
| `/draft` | `web/draft.html` | The draft board |
| `/league` | `web/pro-league.html` | Pro league standings, matches and drafts (sample data) |
| `/about` | `web/about.html` | Data sources and credits |
| `/mockups/` | `mockups/` | "Dusk at the Temple" UI mockups, on the same live draft |

Shared styling lives in `web/style.css` and shared script helpers in `web/app.js`. Hero and item icons go in `web/icons/`.

## Data files

All plain CSV, so you can edit them freely.

- `data/heroes.csv`: `name,role,lane,tier,winrate,banrate,counters,synergies,durability,offense,control,tags,spike,tip`
  - Dual roles and lanes use `/` (first one is the main). Lists use `;`.
  - `counters` means the heroes this hero is good against.
  - A blank `banrate` means under 15% or unknown.
  - `spike` is `EARLY`, `MID`, `LATE` or `ALL`. `tip` is an optional hand-written hero tip; quote it if it contains a comma.
- `data/items.csv`: `name,slug,tier,cost,stats,passives,availability,categories,icon`
- `data/hero-builds.csv`: `hero,order,build,lane,items,emblem,battleSpell,blessing,flex`
  - Fields containing a comma or a quote are wrapped in double quotes. A build's total cost is the sum of its item prices, worked out at startup.
- `data/hero-icons.csv` maps each hero to its icon file.

Every data file is required, and a malformed row stops the server with `file:line: reason`. On startup the server also validates all of the data and prints a warning for each problem, such as a build naming an item that doesn't exist or an icon file that is missing. The pro-league sample data currently prints a few warnings because its standings do not match its match list; that is expected.

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
src/jongrai/   Java source (engine, analyzer, web server, AI coach, SelfCheck)
web/           HTML pages, style.css, app.js, icons/
data/          heroes.csv, items.csv, hero-builds.csv, hero-icons.csv, pro-*.csv
docs/          readable overview of every hero's counters and synergies
```

## Tuning

The bonus and penalty numbers for suggestions are at the top of `src/jongrai/SuggestionEngine.java`. The category weights for the draft edge are at the top of `src/jongrai/DraftAnalyzer.java`.

## Data sources and credits

- Official hero relations (counters, synergies) and hero ratings: Moonton's in-game data, via [p3hndrx/MLBB-API](https://github.com/p3hndrx/MLBB-API) (MIT license).
- Early / mid / late power spikes: [Amirul-Hakimi/MLBB-DRAFT-ANALYSIS](https://github.com/Amirul-Hakimi/MLBB-DRAFT-ANALYSIS) (ISC license). This is one author's judgement, not official data.
- Tiers and win rates: tier-list screenshots, patch 2.2.16.
- Item data and hero builds: [mlbbhub.com](https://mlbbhub.com), reviewed against patch 2.2.16 on 2026-10-01.
- Hero and item artwork belongs to Moonton.
- Full third-party license notices are in [NOTICE](NOTICE).

## Roadmap

- Pro League page with real pro draft data
- Head-to-head matchup win rates to weight counters by strength
- Per-lane meta tiers

## License

[Add your license here]
