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

PowerShell:

```powershell
mkdir out
javac -d out (Get-ChildItem src\jongrai\*.java).FullName
java -cp out jongrai.WebServer
```

macOS / Linux:

```bash
mkdir -p out
javac -d out src/jongrai/*.java
java -cp out jongrai.WebServer
```

Then open **http://localhost:8080**. Use `java -cp out jongrai.WebServer 9000` for another port.

Re-run the `javac` line whenever you change the code. Editing only the CSV files in `data/` needs just a server restart.

For OBS, add the page as a **Browser Source**.

## AI coach (optional)

The coach only speaks once both sides have all 5 picks. Without a key, the panel simply says the coach is off.

1. Copy the example file:

```powershell
   copy ai-config.example.txt ai-config.txt
```

2. Open `ai-config.txt` and fill in your key:

```
   JONGRAI_AI_KEY=sk-your-key
   JONGRAI_AI_MODEL=gpt-4o-mini
   JONGRAI_AI_BASE_URL=https://api.openai.com/v1
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
| `/league` | `web/pro-league.html` | Pro draft data (not built yet) |
| `/about` | `web/about.html` | Data sources and credits |

Shared styling lives in `web/style.css`. Hero and item icons go in `web/icons/`.

## Data files

All plain CSV, so you can edit them freely.

- `data/heroes.csv`: `name,role,lane,tier,winrate,banrate,counters,synergies,durability,offense,control,tags,spike`
  - Dual roles and lanes use `/` (first one is the main). Lists use `;`.
  - `counters` means the heroes this hero is good against.
  - A blank `banrate` means under 15% or unknown.
- `data/items.csv`: `name,slug,tier,cost,stats,passives,availability,categories,icon`
- `data/hero-builds.csv`: `hero,order,build,lane,items,emblem,battleSpell,blessing,flex,totalCost`
  - Fields containing a comma, semicolon or quote are wrapped in double quotes.
- `data/hero-icons.csv` maps each hero to its icon file.

On startup the server checks the item and build files and prints a warning for each problem, such as a build naming an item that doesn't exist. A clean start prints nothing.

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
src/jongrai/   Java source (engine, analyzer, web server, AI coach)
web/           HTML pages, style.css, icons/
data/          heroes.csv, items.csv, hero-builds.csv, hero-icons.csv
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

## Roadmap

- Pro League page with real pro draft data
- Head-to-head matchup win rates to weight counters by strength
- Per-lane meta tiers

## License

[Add your license here]
