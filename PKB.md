# Project Knowledge Base — Multiplayer Snake Game

> **Version:** v1.0 • **Status:** Production-Ready • **Last Updated:** August 5, 2026
> Single source of truth for the project: architecture, domain rules, APIs, operations, testing, and known issues.

---

## 1. Project Overview

**Multiplayer Snake Game** is a real-time multiplayer browser game built on **Jakarta EE 6**. Up to 4 players compete in instant rooms over **WebSocket** with a server-authoritative 150ms tick loop. Includes JWT-based accounts (with guest play), PostgreSQL persistence for player stats, a hybrid-collision game engine with power-ups (golden food, boost coins, hybrid speed boost), and a responsive canvas UI with desktop + mobile touch controls.

| Attribute | Value |
|-----------|-------|
| Repository | https://github.com/alish-3/Multiplayer_Snake_Game |
| Author | Alish Mainalee |
| Language / Runtime | Java 17 |
| Packaging | WAR (Maven), deployable to Tomcat 11+ or Jetty 12 |
| License | MIT |

---

## 2. Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 17 |
| Web Framework | Jakarta Servlet 6.0, Jakarta WebSocket 2.1 |
| Build | Maven 3.9+ (`mvn clean package` → WAR) |
| Server | Apache Tomcat 11 / Jetty 12 (dev: `mvn jetty:run` on :8080) |
| Database | PostgreSQL 14+ (JDBC 42.7.1, auto-migration) |
| JSON | Gson 2.10.1 |
| Password Hashing | jBCrypt 0.4 |
| Auth | JWT (HS256), 30-day remember-me tokens |
| Frontend | Vanilla JS (ES6), Canvas API, CSS Grid/Flexbox, JSP |
| Testing | JUnit 5 (engine unit tests), PowerShell + server-side AI bots |

---

## 3. Architecture

```
src/main/java/com/snake/game/
├── model/          # Domain objects: Snake, Room, GameState, Food, Point
├── engine/         # GameEngine (150ms tick, collisions, scoring), RoomManager
├── servlet/        # AuthServlet, RoomServlet, GameServlet, GameWebSocket
├── db/             # DatabaseManager (PostgreSQL + bcrypt, auto-migration)
└── util/           # JwtUtil (HS256), BotManager, AdvancedBotManager (server-side bots)
```

### 3.1 Key Components

| Component | Responsibility |
|-----------|----------------|
| **RoomManager** | Singleton managing room lifecycle; creates/joins/lists rooms; auto-cleanup of stale rooms after 30s inactivity |
| **GameEngine** | Server-authoritative game loop (`scheduleAtFixedRate`, 150ms tick). Movement, collision detection, food spawning, scoring, boost coins, hybrid boost, milestones. `tick()` wrapped in try/catch with logging so exceptions never silently kill the loop |
| **GameWebSocket** | Real-time endpoint `/api/game/ws/{roomCode}/{playerName}`; broadcasts `GameState` to all room members; handles move/ready/ping-pong |
| **GameServlet** | REST-like HTTP API for join/move/ready/leave/state |
| **RoomServlet** | Room management API (create/join/list) |
| **AuthServlet** | register/login/remember/saveScore/stats |
| **DatabaseManager** | PostgreSQL operations; `players` table auto-created on startup; bcrypt hashing; validates env config |
| **JwtUtil** | HS256 JWT creation/validation for remember-me tokens |
| **BotManager / AdvancedBotManager** | Server-side AI players; v2 uses flood-fill survival + opponent-aware hunting with per-difficulty personalities |

### 3.2 Request Flow (typical)

```
Browser ──HTTP──▶ AuthServlet (login/register) ──▶ DatabaseManager ──▶ PostgreSQL
Browser ──HTTP──▶ RoomServlet (create/join) ─────▶ RoomManager ──────▶ Room
Browser ──WS────▶ GameWebSocket ──▶ RoomManager ──▶ GameEngine (tick loop)
GameEngine ──▶ GameState (JSON via Gson) ──▶ GameWebSocket ──broadcast──▶ all clients
```

### 3.3 Concurrency Model

- `RoomManager` and per-room game state are guarded by `synchronized` blocks to keep WebSocket message handling and the tick loop thread-safe.
- The game loop is a single scheduled thread per room (`scheduleAtFixedRate`); client inputs are queued (`nextDirection`) and applied on the next tick.

---

## 4. Data Models

### 4.1 Snake
- `segments: List<Point>` — head at index 0
- `direction`, `nextDirection`: UP / DOWN / LEFT / RIGHT (queued input)
- `score: int`, `alive`, `ready`, `spectator: boolean`
- Hybrid boost: `boosting` (hold SPACE/Shift or boost button), `speedMultiplier` (2.0x while boosting), `growthPoints` (pending growth counter)

Note: legacy fields `speedBoostActive`/`speedBoostEndTime` still exist but are unused by the engine.

### 4.2 Room
- `code`: 6-char alphanumeric (A-Z, 2-9 — excludes 0/1/O/I for readability)
- `players: List<Snake>` (max 4)
- `gameState: GameState`, `gameInProgress: boolean`
- Auto-cleanup after 30s inactivity

### 4.3 GameState
- `snakes`, `foods: List<Snake>`, `List<Food>`
- `gridSize: 30`
- `gameOver`, `gameStarted: boolean`
- `tick`, `countdown: int`
- Boost system: `boostCoins` (start 20; +10 every 5s; +50 per milestone 100/500/1000 per snake), `lastScoreMilestoneCheck` (per-snake milestone tracking); `snakeSpeedBoost`/`speedBoostExpireTime` are unused legacy fields

### 4.4 Food
- `type`: NORMAL (value 1) or GOLDEN (value 3)
- Golden food spawns from dead snake segments (all segments) and randomly at spawn (15% chance); value 3 (NORMAL = 1). Eating golden food grants NO speed boost (removed mechanic)

### 4.5 Point
- Immutable `x, y` grid cell coordinates

---

## 5. Game Logic (GameEngine.tick)

Tick sequence (150ms, server-authoritative):

1. **Countdown** — 3s real-time based on `gameStartTime` (`Long`, nullable)
2. **Apply queued directions** — `nextDirection → direction` (no 180° reversal)
3. **Calculate next head positions + swept paths** — with speed multiplier (2x while boosting = 2 cells/tick), full path tracked so fast snakes can't jump over bodies/walls
4. **Wall collisions** — checked along the entire swept path
5. **Collision detection** — head-on (2+ heads same cell → all die) and head-body (post-move bodies; §5.1)
6. **Golden food spawning** — collision deaths' segments become GOLDEN food
7. **Move alive snakes, consume food** — `growthPoints += food value`; grow 1 segment per point, else shed tail; `applyGatedGrowth` (gated above score 100) is implemented + tested but NOT yet wired into `tick()` (legacy growth active)
8. **Boost coins** — +10 once every 5s of play (time-based)
9. **Score milestones** — +50 coins per snake per milestone reached (100, 500, 1000), tracked per snake (`name_color`)
10. **Hybrid boost** — boosting runs 2x speed but sheds 1 tail segment as NORMAL food every 2 ticks (~300ms); auto-disables at ≤5 segments; dead snakes unboosted
11. **Score == length** — `score = segments.size()` (slither.io mass rule)
12. **Game over** — 2 players → 1 alive; 3+ players → 1 alive (solo: 0 alive)

### 5.1 Head-Body Collision Rules (v2, 2026-08-03)

`resolveHeadBodyCollisions()` builds occupancy from **post-move** bodies (new head added; tail removed unless growing/feeding):

| Scenario | Result |
|----------|--------|
| Tail-following (chaser lands on cell the leader just vacated) | Survives |
| Chaser lands on leader's **current head cell** (head swap) | Mutual head-on → both die |
| Crossing one's own body | Allowed (snake is "through" itself) |
| Growing leader | Retains tail (that cell remains lethal) |

---

## 6. API Reference

### 6.1 Auth — `POST /api/auth`

| Action | Body | Response |
|--------|------|----------|
| `register` | `{username, password}` | `{success, username}` |
| `login` | `{username, password, remember?}` | `{success, username, token?}` |
| `remember` | `{token}` | `{success, username}` |
| `saveScore` | `{username, score}` | `{success}` |
| `stats` | `{username}` | `{success, stats{totalGames, totalScore, highScore}}` |

### 6.2 Room — `GET/POST /api/room`

| Method | Action | Body | Response |
|--------|--------|------|----------|
| `GET` | `list` | — | `{rooms[{code, playerCount, maxPlayers}]}` |
| `POST` | `create` | `{playerName, color?}` | `{success, roomCode}` |
| `POST` | `join` | `{roomCode, playerName, color?}` | `{success, roomCode}` |

### 6.3 Game — `GET/POST /api/game`

| Method | Action | Body/Query | Response |
|--------|--------|-----------|----------|
| `GET` | `state` | `?room=<code>` | `GameState` JSON |
| `POST` | `join` | `{roomCode, playerName}` | `{success}` |
| `POST` | `move` | `{roomCode, playerName, direction}` | `{success}` |
| `POST` | `ready` | `{roomCode, playerName}` | `{success}` |
| `POST` | `leave` | `{roomCode, playerName}` | `{success}` |

### 6.4 WebSocket — `/api/game/ws/{roomCode}/{playerName}`

| Client → Server | Server → Client |
|-----------------|-----------------|
| `{action:"move", direction}` | `GameState` (broadcast) |
| `{action:"ready"}` | `GameState` (broadcast) |
| `{action:"boost", boost:true|false}` (hold SPACE/Shift or boost button) | `GameState` (broadcast) |
| `{action:"ping", t:timestamp}` | `{action:"pong", t:timestamp}` |

---

## 7. Database (PostgreSQL)

- **Schema** — single table `players`, auto-created on first startup (`CREATE TABLE IF NOT EXISTS`):
  ```sql
  CREATE TABLE IF NOT EXISTS players (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_games INT DEFAULT 0,
    total_score INT DEFAULT 0,
    high_score INT DEFAULT 0
  );
  ```
- **Passwords** — bcrypt hashed (jBCrypt 0.4)
- **Startup validation** — fails fast if `DB_PASSWORD` missing or URL doesn't start with `jdbc:postgresql://`
- Rooms/game state are **in-memory only** (no persistence); only player stats are stored.

---

## 8. Configuration

### Environment Variables

| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `DB_URL` | No | `jdbc:postgresql://localhost:5432/snake_game` | JDBC connection string |
| `DB_USER` | No | `postgres` | DB user |
| `DB_PASSWORD` | **Yes** | — | DB password (fail-fast if missing) |
| `JWT_SECRET` | No (warned) | random | 32+ char HS256 secret; random default warns on startup |

> **Security:** Never commit real secrets. Verify no hardcoded DB credentials exist in the repo (see §14.1).

---

## 9. Build & Run

```bash
# Prerequisites: Java 17+, Maven 3.9+, PostgreSQL 14+
export DB_URL=jdbc:postgresql://localhost:5432/snake_game
export DB_USER=postgres
export DB_PASSWORD=your_password
export JWT_SECRET=$(openssl rand -base64 32)   # replace with a fixed secret in prod

# Compile
mvn compile

# Package WAR
mvn clean package          # → target/Multiplayer_Snake_Game.war

# Dev server (Jetty, port 8080 — pom.xml jetty.port)
mvn jetty:run

# Deploy to Tomcat 11+
cp target/Multiplayer_Snake_Game.war $CATALINA_HOME/webapps/
$CATALINA_HOME/bin/startup.sh
```

Open `http://localhost:8080/Multiplayer_Snake_Game/` (Jetty context root is `/`).

### Unit tests

```bash
mvn test
```

`mvn test` → **21/21 pass** (GameEngineBoostTest 12/12 + GameEngineCollisionTest 4/4 + GameEngineGrowthTest 5/5). See §12.1.

---

## 10. Frontend

| File | Purpose |
|------|---------|
| `index.jsp` | Login/register/guest auth, remember-me token handling |
| `game.jsp` | Lobby (create/join room, player list, ready) + canvas game page |
| `js/ajax.js` | REST helpers (fetch wrappers, JWT/localStorage handling) |
| `js/game.js` | Canvas rendering, WebSocket client (with reconnection), input handling (keyboard/WASD/D-pad/swipe), countdown sound, leaderboard timer |
| `css/style.css` | Responsive styling (desktop + mobile) |
| `sounds/countdown.ogg`, `sounds/gameover.wav` | Audio (lazy AudioContext on first gesture) |

### Notable client behaviors
- 3-2-1 countdown clip plays once per round; new-round detection via tick rollback
- Leaderboard timer only resets while `countdown > 0` (not during gameplay transition at `countdown === 0`)
- WebSocket reconnection hardening for network hiccups

---

## 11. Gameplay & Controls

1. **Auth** — Guest play, or register/login (JWT remember-me)
2. **Lobby** — enter name, pick color, create/join room (6-char code, max 4 players)
3. **Ready up** — all players press READY (or SPACE) → 3s countdown
4. **Control** — Arrow keys / WASD / on-screen D-pad / swipe (mobile)
5. **Eat & grow** — normal food = 1pt; golden = 3pts; score == length (mass)
6. **Boost** — hold SPACE/Shift (or boost button) for 2x speed; sheds tail segments as food; can't boost below 5 segments; coins (start 20) from time (+10/5s) and milestones (+50 @ 100/500/1000)
7. **Win** — last snake standing; scores/bonuses tracked

---

## 12. Testing & Bot Automation

### 12.1 Java unit tests

```bash
mvn test
```

| Test Class | Covers | Status |
|------------|--------|--------|
| `GameEngineBoostTest` | Boost coins (initial 20, timed +10/5s rewards), milestone awards (once per milestone, jump behavior, per-snake tracking), hybrid boost (2x speed, shedding cadence, min length 5, dead reset, MAX_FOODS cap) | 12/12 pass |
| `GameEngineCollisionTest` | Tail-chase survival, chaser-into-head mutual kill, growing leader retains tail, head-swap mutual kill | 4/4 pass |
| `GameEngineGrowthTest` | Gated body growth via `applyGatedGrowth()`: below-gate growth, golden food, gate crossing accumulation, high-score gating, gate equality | 5/5 pass |

### 12.2 PowerShell bots (tracked in repo)

| Script | Purpose |
|--------|---------|
| `bot.ps1` | Joins a room, readies up, then chases food and re-readies on game over (joins via room code + optional server URL) |
| `run_4bot_game.ps1` | Creates a room, joins 4 bots, readies all, launches movement bots, and monitors the round until game over |

```powershell
# Quick demo: 4 bots in a room
.\run_4bot_game.ps1
```

### 12.3 Server-side AI bots (Java)

`AdvancedBotManager` runs server-side bots with flood-fill survival + opponent-aware hunting, per-difficulty personalities.

### 12.4 Media assets (`media/`)

`media/pc/` and `media/mobile/` each contain 9 screenshots (auth → game over) + MP4/GIF gameplay recordings (multiple rematch rounds: round → game-over → ready-up → next round).

---

## 13. Known Issues & Roadmap

### 13.1 Open Issues

| Area | Description | Priority |
|------|-------------|----------|
| Reconnection handling | Improve WebSocket reconnect UX on network hiccups | Medium |
| Spectator mode | Watch games without playing | Low |
| Leaderboard | Global/historical leaderboards with pagination | Low |
| Custom room settings | Grid size, tick rate, max players, food density | Low |
| Gated growth wiring | `applyGatedGrowth` is implemented/tested but tick() still uses legacy immediate growth | Low |
| Accessibility | Keyboard navigation, screen reader support | Low |

### 13.2 Roadmap — "Ship it" (2026-08-05)

Direction: the game is feature-complete; the goal is making it a deployable, playable product. Quality foundation → reach → hardening → depth.

**Phase 0 — Foundation (safety net)**
- [x] Fix `GameEngineGrowthTest` — implemented `applyGatedGrowth()` (gated growth: threshold 1 ≤ score 100, threshold 4 above; remainder carried in `growthPoints`). 5/5 pass
- [x] Add unit tests for boost coins/milestones/hybrid-boost logic (GameEngineBoostTest 12/12, added 2026-08-05)
- [x] Done-check: `mvn test` green (21/21: 12 boost + 4 collision + 5 growth)

**Phase 1 — Reach (make it playable by others)**
- [ ] Docker: multi-stage WAR build + `docker-compose.yml` (app + PostgreSQL)
- [ ] GitHub Actions CI: `mvn clean test` + package on every push
- [ ] Deploy once via ngrok (`ngrok http 8080` → local server) → live playable URL
- [ ] Done-check: fresh clone + `docker compose up` runs; CI green; URL playable

**Phase 2 — Hardening & observability**
- [ ] `/api/health` endpoint + active-room/connection metrics
- [ ] Structured server logging (engine events, deaths, room lifecycle)
- [ ] Rate limiting on `move`, input validation, WebSocket message size caps
- [ ] Security headers + CORS config
- [ ] Done-check: stress test (4 bots × N rooms) runs clean with visible logs

**Phase 3 — Depth (retention)**
- [ ] Custom room settings (grid size, tick rate, max players, food density)
- [ ] Spectator mode + reconnection UX improvement
- [ ] Global leaderboard (stats tables already exist)
- [ ] Done-check: playtest with varied settings, no regressions

**Phase 4 — Polish (optional)**
- [ ] PWA/mobile polish, accessibility, i18n

---

## 14. Security Notes

1. **Secrets** — DB credentials and JWT secret come only from env vars; app fails fast on missing `DB_PASSWORD`. `JWT_SECRET` random fallback logs a startup warning. Never commit real secrets.
2. **Passwords** — bcrypt-hashed at rest; no plaintext storage.
3. **Tokens** — HS256 JWTs for remember-me; validated via `JwtUtil`.
4. **Input validation** — basic server-side checks exist; full hardening (rate limiting, CORS/security headers) is on the roadmap.
5. **WebSocket endpoint** — player name is part of the path; name collision/duplicate handling enforced by RoomManager.

---

## 15. Operational Notes & Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| Startup crash: "Database password is required" | `DB_PASSWORD` env var not set (fail-fast by design) |
| "Invalid database URL format" | `DB_URL` must start with `jdbc:postgresql://` |
| Game freezes mid-round (old builds) | Known NPE fixed 2026-08-01; `tick()` now logs exceptions — check server log |
| Bots not moving | Room code / player names must match; server running on expected port |
| WebSocket connection fails | Verify server port (Jetty :8080 via pom `jetty.port`) |

### Git history note

`main` branch; history includes a force-push that removed Dockerfile commits (2026-08-01) — do not rebase against remote history unless intended.

---

## 16. Glossary

| Term | Meaning |
|------|---------|
| Tick | One server-side game step (150ms) |
| Golden food | 3-point food; spawns from dead snake segments |
| Boost coins | Currency (no spend mechanic yet); start 20, +10 every 5s, +50 per milestone (100/500/1000) |
| Speed boost | Hybrid boost: 2x speed while held (SPACE/Shift/button); sheds tail as food; min length 5 |
| Hybrid boost | 2x speed while holding boost; sheds tail segments as food every 2 ticks |
| Head-on | Two or more heads in the same cell → all die |
| Hybrid IO | Own-body crossing allowed; other snakes' bodies lethal |
| Room code | 6-char code (A-Z, 2-9) |
| GameState | Full serializable snapshot broadcast to clients each tick |

---

## 17. Repository Structure & Current State

### 17.1 Project tree (what's in the repo)

```
.
├── pom.xml                          # Maven WAR build, Jetty 12 ee10 dev plugin (:8080)
├── .gitignore
├── README.md                        # Public overview + media showcase
├── PKB.md                           # This document
├── bot.ps1                          # PowerShell AI bot launcher
├── run_4bot_game.ps1                # 4-bot demo launcher
├── src/
│   ├── main/
│   │   ├── java/com/snake/game/
│   │   │   ├── model/               # Food, GameState, Point, Room, Snake
│   │   │   ├── engine/              # GameEngine, RoomManager
│   │   │   ├── servlet/             # AuthServlet, GameServlet, GameWebSocket, RoomServlet
│   │   │   ├── db/                  # DatabaseManager
│   │   │   └── util/                # AdvancedBotManager, BotManager, JwtUtil
│   │   └── webapp/
│   │       ├── index.jsp            # Auth page (guest/register/login)
│   │       ├── game.jsp             # Lobby + game canvas page
│   │       ├── css/style.css
│   │       ├── js/ajax.js, js/game.js
│   │       ├── sounds/countdown.ogg, sounds/gameover.wav
│   │       └── WEB-INF/web.xml
│   └── test/java/com/snake/game/engine/
│       ├── GameEngineBoostTest.java       # 12/12 pass (boost coins/milestones/hybrid boost)
│       ├── GameEngineCollisionTest.java   # 4/4 pass
│       └── GameEngineGrowthTest.java      # 5/5 pass (gated growth)
├── media/pc/                        # 9 screenshots + pc-gameplay.gif + pc-gameplay.mp4
├── media/mobile/                    # 9 screenshots + mobile-gameplay.gif + mobile-gameplay.mp4
└── .idea/                           # IntelliJ config (partial)
```

### 17.2 Current verification state (2026-08-05)

- `mvn compile` → BUILD SUCCESS
- `mvn jetty:run` → Jetty 12.0.16 boots webapp on :8080, scan=5 accepted (no plugin warnings)
- `mvn test` → 21/21 pass (12 boost + 4 collision + 5 gated-growth)
- Phase 0 complete (2026-08-05): boost coins/milestones/hybrid-boost unit tests added (GameEngineBoostTest 12/12)
- Git: `main`; PKB.md modified (docs update pending)

---

## 18. Document Map

| Doc | Purpose |
|-----|---------|
| `README.md` | Public-facing overview, features, quick start, media showcase |
| **`PKB.md` (this file)** | Deep reference: architecture, rules, APIs, operations, testing |
