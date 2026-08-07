# Multiplayer Snake Game 🐍

> **Version:** v1.0 • **Status:** Production-Ready • **Last Updated:** August 8, 2026

> A real-time multiplayer Snake game built with **Java 17**, **Jakarta EE 6**, **WebSocket**, and **PostgreSQL** — featuring room-based gameplay, JWT authentication, hybrid IO mechanics, and a responsive canvas UI.

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)
![Jakarta EE](https://img.shields.io/badge/Jakarta%20EE-6.0-blue?logo=java)
![WebSocket](https://img.shields.io/badge/WebSocket-2.1-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14%2B-blue?logo=postgresql)
![Maven](https://img.shields.io/badge/Maven-3.9%2B-red?logo=apachemaven)
[![CI](https://github.com/alish-3/Multiplayer_Snake_Game/actions/workflows/ci.yml/badge.svg)](https://github.com/alish-3/Multiplayer_Snake_Game/actions)
![License](https://img.shields.io/badge/License-MIT-green)

---

## 🎮 Core Features

### Multiplayer Experience
- **Instant Rooms** — 6-character room codes for quick sharing
- **4-Player Max** — Competitive or cooperative gameplay
- **WebSocket Physics** — 150ms authoritative tick loop for fair, smooth gameplay
- **Spectator Mode** — Watch live games without playing via dedicated WebSocket endpoint
- **Custom Room Settings** — Configurable grid size, tick rate, max players, food density, boost/golden food toggles
- **Reconnection Resilience** — Automatic WebSocket reconnection with grace period and state sync

### Security & Authentication
- **JWT Tokens** — Stateless, scalable authentication with HS256
- **bcrypt Passwords** — Industry-standard hashing with legacy migration support
- **Guest Play** — Easy access without account creation
- **Remember-Me** — 30-day persistent sessions
- **Rate Limiting** — Token-bucket per IP on all endpoints
- **Security Headers** — CSP, HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy
- **Input Validation** — Sanitized parameters, WebSocket message size caps

### Persistent Stats & Progression
- **PostgreSQL Backend** — Player stats: total games, total score, high score
- **Power-ups** — Golden food (3× points), boost coins every 5s
- **Boost System** — Hold SPACE/Shift (or boost button) for 2× speed with tail-shedding; boost coins from time (+10/5s) and milestones (+50 @ 100/500/1000)
- **Profile Management** — Display name, avatar, stats overview via `/api/profile`

### Advanced Gameplay
- **Hybrid IO Rules** — Walk your own body allowed; head‑ons kill both snakes; other snakes' bodies are lethal
- **Smart AI Bots** — Server-side bots with flood-fill survival + opponent-aware hunting; PowerShell launchers for testing
- **Global Leaderboard** — Paginated rankings by high score, total score, or total games
- **Cross-Platform** — Desktop, mobile, touch‑and‑gesture controls

### Observability & Operations
- **Health Endpoint** — `/api/health` with active rooms, connections, players, uptime metrics
- **Structured Logging** — Engine events, deaths, room lifecycle, connection metrics
- **Graceful Shutdown** — ServletContextListener for clean startup/shutdown

### Developer-Friendly
- **Jakarta EE 6** — Modern, standards‑based Java web development
- **Comprehensive API** — Full REST/ WebSocket coverage
- **Scripted Bot Testing** — `bot.ps1` and `run_4bot_game.ps1` for quick AI play-testing
- **Docker Ready** — Multi-stage build + compose (app + PostgreSQL)
- **CI/CD** — GitHub Actions: `mvn clean test` + package on every push

---

## 🎥 Gameplay Showcase

### Desktop (PC) Gameplay

| # | Screenshot | Description |
|---|------------|-------------|
| 01 | ![Auth Screen](media/pc/01-pc-auth.png) | **Authentication** - Guest play, register, or login with JWT remember-me |
| 02 | ![Login](media/pc/02-pc-login.png) | **Login** - Secure bcrypt password verification |
| 03 | ![Register](media/pc/03-pc-register.png) | **Register** - Create account with username/password |
| 04 | ![Lobby](media/pc/04-pc-lobby.png) | **Lobby** - Room list, create/join rooms, player name & color picker |
| 05 | ![Game Room](media/pc/05-pc-game-room-waiting.png) | **Game Room (Waiting)** - Player list, ready status, room code |
| 06 | ![Gameplay Start](media/pc/06-pc-gameplay-start.png) | **Gameplay Start** - 3-second countdown begins |
| 07 | ![Gameplay](media/pc/07-pc-gameplay.png) | **Live Gameplay** - Snakes moving, food spawning, real-time |
| 08 | ![Gameplay Action](media/pc/08-pc-gameplay-action.png) | **Action Shot** - Collisions, golden food, boost effects |
| 09 | ![Game Over](media/pc/09-pc-game-over.png) | **Game Over** - Winner announced, scores, ready for rematch |

### Mobile Gameplay

| # | Screenshot | Description |
|---|------------|-------------|
| 01 | ![Mobile Auth](media/mobile/01-mobile-auth.png) | **Mobile Auth** - Touch-optimized authentication flow |
| 02 | ![Mobile Login](media/mobile/02-mobile-login.png) | **Mobile Login** - Responsive keyboard-friendly inputs |
| 03 | ![Mobile Register](media/mobile/03-mobile-register.png) | **Mobile Register** - Clean registration on small screens |
| 04 | ![Mobile Lobby](media/mobile/04-mobile-lobby.png) | **Mobile Lobby** - Adaptive layout for phone screens |
| 05 | ![Mobile Game Room](media/mobile/05-mobile-game-room-waiting.png) | **Mobile Game Room** - Player list optimized for touch |
| 06 | ![Mobile Gameplay Start](media/mobile/06-mobile-gameplay-start.png) | **Mobile Countdown** - 3s countdown with touch controls visible |
| 07 | ![Mobile Gameplay](media/mobile/07-mobile-gameplay.png) | **Mobile Gameplay** - On-screen D-pad + swipe controls |
| 08 | ![Mobile Action](media/mobile/08-mobile-gameplay-action.png) | **Mobile Action** - Fast-paced gameplay on mobile |
| 09 | ![Mobile Game Over](media/mobile/09-mobile-game-over.png) | **Mobile Game Over** - Results screen, ready for next round |

### Gameplay GIFs

| Platform | Gameplay Preview |
|----------|------------------|
| **Desktop** | ![PC Gameplay](media/pc/pc-gameplay.gif) |
| **Mobile** | ![Mobile Gameplay](media/mobile/mobile-gameplay.gif) |

> **Note:** GIFs show multiple rematch rounds in one session — round → game-over screen → ready-up → next round. *Original MP4 videos also available in media/ folders.*

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|------------|
| **Language** | Java 17 |
| **Web Framework** | Jakarta Servlet 6.0, Jakarta WebSocket 2.1 |
| **Build** | Maven 3.9+ (WAR packaging) |
| **Server** | Apache Tomcat 11 / Jetty 12 (dev) |
| **Database** | PostgreSQL 14+ |
| **JSON** | Gson 2.10 |
| **Password Hashing** | jBCrypt 0.4 |
| **Frontend** | Vanilla JS (ES6), Canvas API, CSS Grid/Flexbox |
| **Testing** | JUnit 5 (engine unit tests), PowerShell + server-side AI bots |

---

## 🏗 Architecture

```
src/main/java/com/snake/game/
├── model/          # Domain: Snake, Room, GameState, Food, Point
├── engine/         # GameEngine (tick loop, collisions), RoomManager
├── servlet/        # AuthServlet, RoomServlet, GameServlet, GameWebSocket,
                    # SpectatorWebSocket, HealthServlet, ProfileServlet,
                    # LeaderboardServlet, SecurityFilter
├── db/             # DatabaseManager (PostgreSQL + bcrypt)
└── util/           # JwtUtil (HS256 tokens), BotManager, AdvancedBotManager,
                    # RateLimiter, GameLogger, AppContextListener
```

### Key Components

| Component | Responsibility |
|-----------|----------------|
| **RoomManager** | Singleton managing room lifecycle, 30s stale cleanup; supports spectators & custom settings |
| **GameEngine** | 150ms tick: movement, collision (wall/self/head-on/head-body), food spawning, scoring, boost system, gated growth (tested) |
| **GameWebSocket** | Real-time state broadcast, move/ready/boost/ping messages |
| **SpectatorWebSocket** | Read-only state broadcast for spectators (`/ws/{roomCode}/spectator/{name}`) |
| **HealthServlet** | `/api/health` endpoint with active room/connection metrics for observability |
| **ProfileServlet** | `/api/profile` endpoint for authenticated user profile management |
| **LeaderboardServlet** | `/api/leaderboard` endpoint with global rankings, pagination, sorting |
| **SecurityFilter** | Rate limiting (token bucket per IP), input validation, security headers, CORS |
| **RateLimiter** | Token bucket implementation for API rate limiting |
| **GameLogger** | Structured logging for engine events, deaths, room lifecycle, connection metrics |
| **AppContextListener** | ServletContextListener for startup/shutdown (DB pool, RoomManager cleanup, logging) |
| **DatabaseManager** | Auto-migration, bcrypt passwords, JWT token storage |
| **JwtUtil** | HS256 JWT creation/validation for remember-me tokens |
| **BotManager / AdvancedBotManager** | Server-side AI players; v2 uses flood-fill survival + opponent-aware hunting |

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.9+
- PostgreSQL 14+
- Tomcat 11+ (or use `mvn jetty:run`)
### Environment Variables

```bash
export DB_URL=jdbc:postgresql://localhost:5432/snake_game
export DB_USER=postgres
export DB_PASSWORD=your_password
export JWT_SECRET=$(openssl rand -base64 32)  # 32+ chars for HS256
```

> **Note:** The `JWT_SECRET` line above generates a random secret for demonstration. **Replace this with your actual JWT secret** in production (set via environment variables or secure configuration management). Never commit actual secrets to version control.

### Build & Run
```bash
# Compile & package
mvn clean package

# Option 1: Jetty 12 dev server (port 8080)
mvn jetty:run

# Option 2: Deploy to Tomcat 11+
cp target/Multiplayer_Snake_Game.war $CATALINA_HOME/webapps/
$CATALINA_HOME/bin/startup.sh
```

### Option 3: Docker (recommended)
```bash
# 1. Install Docker Desktop (needs WSL2 + virtualization enabled in BIOS)
# 2. Configure secrets once
copy .env.example .env        # then edit .env with real DB_PASSWORD + JWT_SECRET (32+ chars)
# 3. Build & run the full stack (app + PostgreSQL)
docker compose up --build     # → http://localhost:8080
# Stop: docker compose down   # add -v to also wipe the database volume
```

Then open `http://localhost:8080/` (Jetty or Docker, context root `/`).

### Share it live (ngrok)

> **Status (2026-08-08): live** — currently running at https://predefine-imaginary-deniable.ngrok-free.dev (Jetty 12 + local PostgreSQL + ngrok). Free tier: URL changes if ngrok restarts.

1. Make sure the stack is running locally (Option 3 Docker, or Jetty + local PostgreSQL)
2. `ngrok http 8080` (first time: create a free account at ngrok.com, then `ngrok config add-authtoken <token>`)
3. Share the printed `https://<subdomain>.ngrok-free.app` URL — gameplay, REST API and WebSocket all work through the tunnel (the client builds WebSocket URLs from window.location, so wss:// is automatic)

---

## 🎯 How to Play

1. **Auth** — Play as Guest, or Register/Login (JWT remember-me)
2. **Lobby** — Enter name, pick snake color, create or join a room (custom settings available)
3. **Ready Up** — All players press **READY** (or SPACE) to start 3s countdown
4. **Control** — Arrow keys / WASD / on-screen D-pad / swipe (mobile)
5. **Eat & Grow** — Normal food = 1pt, Golden food = 3pts, score == length (mass)
6. **Boost** — Hold SPACE/Shift (or boost button) for 2× speed; sheds tail segments as food; coins from time (+10/5s) and milestones (+50 @ 100/500/1000)
7. **Survive** — Last snake standing wins!
8. **Spectate** — Watch any room without joining as a player
9. **Leaderboard** — View global rankings by high score, total score, or total games

---

## 🔌 API Reference

### Auth (`POST /api/auth`)
| Action | Body | Response |
|--------|------|----------|
| `register` | `{username, password}` | `{success, username}` |
| `login` | `{username, password, remember?}` | `{success, username, token?}` |
| `remember` | `{token}` | `{success, username}` |
| `saveScore` | `{username, score}` | `{success}` |
| `stats` | `{username}` | `{success, stats{totalGames,totalScore,highScore}}` |

### Rooms (`GET/POST /api/room`)
| Method | Action | Body | Response |
|--------|--------|------|----------|
| `GET` | `list` | — | `{rooms[{code,playerCount,maxPlayers}]}` |
| `POST` | `create` | `{playerName, color?, gridSize?, tickRateMs?, maxPlayers?, foodDensity?, enableBoost?, enableGoldenFood?}` | `{success, roomCode}` |
| `POST` | `join` | `{roomCode, playerName, color?}` | `{success, roomCode}` |
| `POST` | `spectate` | `{roomCode, spectatorName}` | `{success, roomCode}` |

### Game (`GET/POST /api/game`)
| Method | Action | Body/Query | Response |
|--------|--------|------------|----------|
| `GET` | `state` | `?room=<code>` | `GameState` JSON |
| `POST` | `join` | `{roomCode, playerName}` | `{success}` |
| `POST` | `move` | `{roomCode, playerName, direction}` | `{success}` |
| `POST` | `ready` | `{roomCode, playerName}` | `{success}` |
| `POST` | `leave` | `{roomCode, playerName}` | `{success}` |

### WebSocket (`/api/game/ws/{roomCode}/{playerName}`)
| Client → Server | Server → Client |
|-----------------|-----------------|
| `{action:"move", direction}` | `GameState` (broadcast) |
| `{action:"ready"}` | `GameState` (broadcast) |
| `{action:"boost", boost:true\|false}` (hold SPACE/Shift or boost button) | `GameState` (broadcast) |
| `{action:"ping", t:timestamp}` | `{action:"pong", t:timestamp}` |

### Spectator WebSocket (`/api/game/ws/{roomCode}/spectator/{spectatorName}`)
| Client → Server | Server → Client |
|-----------------|-----------------|
| `{action:"ping", t:timestamp}` | `GameState` (broadcast, read-only) |
| — | `{action:"pong", t:timestamp}` |

### Health (`GET /api/health`)
| Method | Response |
|--------|----------|
| `GET` | `{status:"ok", uptimeMs, activeRooms, activeConnections, activePlayers, timestamp}` |

### Profile (`GET/POST /api/profile`)
| Method | Body | Response |
|--------|------|----------|
| `GET` | — | `{success, profile{username, displayName, avatar, totalGames, totalScore, highScore}}` |
| `POST` | `{displayName?, avatar?}` | `{success, profile{...}}` |

### Leaderboard (`GET /api/leaderboard`)
| Method | Query Params | Response |
|--------|--------------|----------|
| `GET` | `page` (default 0), `size` (default 20, max 100), `sort` (default "highScore": highScore\|totalScore\|totalGames) | `{success, leaderboard[{rank, username, displayName, avatar, totalGames, totalScore, highScore}], pagination{page, size, total, totalPages}}` |

---

## 🧪 Play-Testing with AI Bots

```powershell
# Launch 4 AI bots: creates a room, joins + readies all, runs the round
.\run_4bot_game.ps1

# Or join a single bot to an existing room
.\bot.ps1 -room <ROOMCODE> [-player <name>] [-server <base-url>]
```

Server-side bots (`AdvancedBotManager`) use flood-fill survival + opponent-aware hunting with per-difficulty personalities.

---

## 📁 Project Structure

```
.
├── pom.xml                    # Maven WAR build, Jetty 12 ee10 dev plugin (:8080)
├── Dockerfile                 # Multi-stage build (Maven → Tomcat 11)
├── docker-compose.yml         # App + PostgreSQL stack
├── .dockerignore
├── .env.example               # Template for DB_PASSWORD / JWT_SECRET
├── .github/workflows/ci.yml   # CI: mvn clean test + package on push/PR
├── README.md                  # This overview
├── PKB.md                     # Project Knowledge Base (architecture, APIs, ops)
├── bot.ps1                    # PowerShell AI bot launcher
├── run_4bot_game.ps1          # 4-bot demo launcher
├── src/
│   ├── main/
│   │   ├── java/com/snake/game/
│   │   │   ├── model/         # Food, GameState, Point, Room, Snake
│   │   │   ├── engine/        # GameEngine, RoomManager
│   │   │   ├── servlet/       # AuthServlet, GameServlet, GameWebSocket, RoomServlet,
│   │   │   │                  # SpectatorWebSocket, HealthServlet, ProfileServlet,
│   │   │   │                  # LeaderboardServlet, SecurityFilter
│   │   │   ├── db/            # DatabaseManager
│   │   │   └── util/          # AdvancedBotManager, BotManager, JwtUtil,
│   │   │                      # RateLimiter, GameLogger, AppContextListener
│   │   └── webapp/
│   │       ├── index.jsp      # Auth page
│   │       ├── game.jsp       # Lobby + game canvas
│   │       ├── profile.jsp    # User profile page
│   │       ├── css/style.css
│   │       ├── js/game.js     # Game logic, WS, rendering, reconnection
│   │       ├── js/ajax.js     # REST helpers
│   │       ├── js/profile.js  # Profile page logic
│   │       ├── sounds/        # countdown.ogg, gameover.wav
│   │       └── WEB-INF/web.xml
│   └── test/java/com/snake/game/engine/   # GameEngineBoostTest (12/12) + GameEngineCollisionTest (4/4) + GameEngineGrowthTest (5/5)
├── media/                     # Screenshots & gameplay videos
│   ├── pc/                    # Desktop captures (9 PNG + GIF + MP4)
│   └── mobile/                # Mobile captures (9 PNG + GIF + MP4)
└── .idea/                     # IntelliJ config (partial)
```

---

## 🐛 Bug Fixes & Known Issues

### ✅ Fixed (2026-07-31)

| Issue | File | Fix |
|-------|------|-----|
| Duplicate `setSpeedBoostActive()` | `Snake.java` | Removed duplicate method |
| `getNextHead()` speed multiplier dividing instead of multiplying | `Snake.java` | Fixed: now multiplies → 3× speed = 3 cells/tick |
| `gameStartTime` null check (primitive `long` vs `Long`) | `GameEngine.java` | Changed to `Long` for proper nullability |
| `lastScoreMilestoneCheck` type mismatch | `GameEngine.java` | Fixed: `Map<String, Long>` → `Map<String, Map<String, Long>>` |
| Duplicate `now` and `roomCode` variable declarations | `GameEngine.java` | Removed duplicates |

### ✅ Fixed (2026-08-01) — Critical

| Issue | File | Fix |
|-------|------|-----|
| **Game-freeze NPE** — Golden food loop iterated ALL snakes without `isAlive()` check; `nextHeads` only contains alive snakes. Dead snake on tick with golden food → `nextHeads.get(deadSnake)` → NPE silently swallowed by `scheduleAtFixedRate` → entire game loop froze server-side | `GameEngine.java` | Added `if (!snake.isAlive()) continue;` to golden-food loop. Wrapped `tick()` in try/catch with logging so future engine exceptions are visible |
| **Initial food count too low** — Only 1 food spawned; bots converged on single food → head-on collisions → rounds ended in seconds | `GameEngine.java` | `initGameState` now spawns 4 foods (better distribution, improved gameplay) |
| **Video capture timing** — Recording started too early (blank lead-in) | `tests/capture-media.spec.ts` | Game page opens right after ready-up so recordings start with countdown |
| **Bot personalities** — All bots aggressive → short rounds | `tests/capture-media.spec.ts` | Survival-leaning mix: balanced + 2 defensive + foodie via `createCustomBotRoom` |

### ✅ Fixed (2026-08-02)

| Issue | File | Fix |
|-------|------|-----|
| **Leaderboard timer stuck at 0:00** — `if (data.countdown >= 0)` reset `startTime = 0` on every tick after countdown ended (server sends `countdown: 0` continuously when `gameStarted=true`) | `game.js` (line 841) & `js/game.js` (line 834) | Changed to `if (data.countdown > 0)` — timer only resets during actual countdown (3,2,1), not during gameplay transition (0) |
| **Unused bot classes** — `BotAdder`, `AdvancedBotAdder`, `PerfectBotManager` not imported anywhere | `util/` | Deleted 3 unused files |
| **Duplicate `js/` folder** — Identical copy of `src/main/webapp/js/` at repo root | Root | Deleted duplicate folder |
| **.gitignore cleanup** — Added ignores for `.smarttomcat/`, debug/playtest/verify screenshots, screenshot logs | `.gitignore` | Prevents IDE/temp files from being committed |

### ✅ Fixed (2026-08-05)

| Issue | File | Fix |
|-------|------|-----|
| **Jetty 11 ≠ declared APIs** — Jetty 11.0.20 implements Jakarta EE 9 (Servlet 5.0/WebSocket 2.0) but the app declares Servlet 6.0/WebSocket 2.1 (Jakarta EE 10); also `scanIntervalSeconds` was an unknown parameter (hot reload silently disabled) | `pom.xml` | Upgraded to `org.eclipse.jetty.ee10:jetty-ee10-maven-plugin:12.0.16` (matches Tomcat 11 EE level); fixed config to `scan` |
| **Docs** — Added Project Knowledge Base, updated structure/port references | `PKB.md`, `README.md` | New `PKB.md`; README structure + Jetty 12 + port 8080 corrections |

### ✅ Phase 0 & Phase 1 (2026-08-05)

| Issue | Fix |
|-------|-----|
| Boost coins/milestones/hybrid-boost logic untested | Extracted `timedBoostCoinReward`, `applyScoreMilestoneCoins`, `applyHybridBoost` into testable methods; added `GameEngineBoostTest` (12/12); `mvn test` now 21/21 |
| Stale boost docs (3×/5%/spend-coins design that no longer exists) | PKB.md rewritten to match the hybrid-boost design; README updated in this pass |
| No deployment path | Dockerfile + docker-compose.yml + GitHub Actions CI + ngrok runbook added; CI Run #1 green (21/21 tests + WAR artifact); deployed live via ngrok — https://predefine-imaginary-deniable.ngrok-free.dev (Docker container run pending — virtualization disabled in BIOS) |

### ✅ Phase 2 — Hardening & Observability (2026-08-08)

| Issue | Fix |
|-------|-----|
| No health monitoring endpoint | Added `HealthServlet` at `/api/health` returning status, uptime, active rooms, connections, players |
| No structured logging | Added `GameLogger` with structured logging for engine events, deaths, room lifecycle, connection metrics |
| No rate limiting or security hardening | Added `SecurityFilter` (token bucket per IP), `RateLimiter`, security headers (CSP, HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy), CORS config, WebSocket message size caps, input validation |
| No graceful startup/shutdown | Added `AppContextListener` (ServletContextListener) for DB pool init, RoomManager cleanup, logging setup |
| No WebSocket reconnection resilience | Enhanced client with automatic reconnection, grace period, reconnection token; server accepts rejoin with same playerName within grace window and syncs state on reconnect |

### ✅ Phase 3 — Depth & Retention (2026-08-08)

| Issue | Fix |
|-------|-----|
| No spectator mode | Added `SpectatorWebSocket` at `/ws/{roomCode}/spectator/{name}` with read-only GameState broadcast; RoomServlet `spectate` action |
| No reconnection resilience | Enhanced WebSocket client with automatic reconnection, grace period, and state sync on reconnect |
| No global leaderboard | Added `LeaderboardServlet` at `/api/leaderboard` with pagination (page/size), sorting (highScore/totalScore/totalGames), avatar/displayName support |
| No custom room settings | Room creation now accepts `gridSize`, `tickRateMs`, `maxPlayers`, `foodDensity`, `enableBoost`, `enableGoldenFood`; Room model updated |
| No profile management | Added `ProfileServlet` at `/api/profile` (GET/POST) for displayName, avatar, stats; `profile.jsp` frontend |
| Gated growth not wired | `applyGatedGrowth` implemented and tested (5/5 in GameEngineGrowthTest) but tick() still uses legacy immediate growth |

### 🔧 In Progress / Planned

| Area | Description | Priority |
|------|-------------|----------|
| **Accessibility** | Improve keyboard navigation, screen reader support | Low |
| **PWA / Mobile Polish** | Installable app, offline support, i18n | Low |
| **Gated Growth Wiring** | Connect `applyGatedGrowth` into `tick()` (legacy growth still active) | Low |

---

## 📈 Project Progress

### ✅ Completed Milestones

- [x] **Core Backend** — Jakarta EE 6 WAR with Servlet, WebSocket, PostgreSQL
- [x] **Authentication** — JWT remember-me, bcrypt, guest play
- [x] **Room System** — Create/join/list, 6-char codes, 4-player max, custom settings, spectator support
- [x] **Game Engine** — 150ms tick, collision detection, food spawning, scoring, gated growth (tested)
- [x] **Boost System** — Hybrid boost (2×, tail-shedding), boost coins (+10/5s, +50 @ milestones)
- [x] **Frontend** — Responsive lobby + canvas game, touch/mobile support, reconnection UX
- [x] **AI Bots** — Server-side bots + PowerShell launchers for testing
- [x] **Media Capture** — 18 screenshots (9 PC + 9 mobile) + 2 gameplay videos
- [x] **Critical Bug Fixes** — Game-freeze NPE, initial food, video timing, bot personalities
- [x] **Leaderboard timer fix** — Timer no longer stuck at 0:00 during gameplay
- [x] **Code cleanup** — Removed unused bot classes, duplicate js/ folder, improved .gitignore
- [x] **Jetty 12 upgrade** — Dev server matches Servlet 6.0/WebSocket 2.1; hot reload fixed
- [x] **Project Knowledge Base** — `PKB.md` added; docs updated to current structure
- [x] **Phase 0 — Test coverage** — Boost coins/milestones/hybrid-boost unit tests (GameEngineBoostTest 12/12; `mvn test` 21/21 green)
- [x] **Phase 1 — Deployment** — Docker scaffold (multi-stage + compose), GitHub Actions CI green (Run #1), live ngrok URL (https://predefine-imaginary-deniable.ngrok-free.dev; Docker run pending BIOS enable)
- [x] **Phase 2 — Hardening & Observability** — Health endpoint, structured logging, rate limiting, security headers/CORS, input validation, graceful shutdown, **WebSocket reconnection resilience (grace period + token-based state sync)**
- [x] **Phase 3 — Depth & Retention** — Custom room settings, spectator mode, **reconnection UX**, global leaderboard, profile management

### 🎯 Next Milestones (Phase 4 — Polish)

- [ ] **PWA / Mobile Polish** — Installable app, offline support, home screen install
- [ ] **Accessibility** — Keyboard navigation, screen reader support, ARIA labels
- [ ] **Internationalization (i18n)** — Multi-language support
- [ ] **OpenAPI/Swagger** — Auto-generated REST API documentation
- [ ] **Gated Growth Wiring** — Connect `applyGatedGrowth` into `tick()` (legacy growth still active)

---

## 👨‍💻 Author

**Alish Mainalee**
Built to explore Java backend development, Jakarta EE, WebSocket real-time communication, and full-stack integration with PostgreSQL.

- GitHub: [@alish-3](https://github.com/alish-3)
- Repository: [Multiplayer_Snake_Game](https://github.com/alish-3/Multiplayer_Snake_Game)

---

## 📄 License

MIT License — feel free to use, modify, and distribute.

---

## 🙏 Acknowledgments

- Jakarta EE community for the modern Java web stack
- PostgreSQL for reliable persistence
- All open-source libraries that made this project possible

---

## 📅 Last Updated

*Last updated: August 8, 2026*
*Status: Actively maintained — bugs are tracked, fixed, and verified. Committed to making this as bug-free as possible.*