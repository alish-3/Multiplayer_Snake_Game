package com.snake.game.engine;

import com.snake.game.model.*;
import com.snake.game.servlet.GameWebSocket;
import com.snake.game.servlet.SpectatorWebSocket;
import com.snake.game.util.AdvancedBotManager;
import com.snake.game.util.GameLogger;
import java.util.*;
import java.util.concurrent.*;

public class GameEngine {
    private static final int DEFAULT_GRID_SIZE = 30;
    private static final int DEFAULT_TICK_INTERVAL_MS = 150;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private static final Map<String, ScheduledFuture<?>> activeGames = new ConcurrentHashMap<>();
    private static final Map<String, Long> gameStartTimes = new ConcurrentHashMap<>();
    private static final Map<String, Long> growthTimers = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Long>> lastScoreMilestoneCheck = new ConcurrentHashMap<>();
    private static final boolean TICK_DEBUG = System.getenv("SNAKE_TICK_DEBUG") != null || Boolean.getBoolean("snake.tickDebug");

    // Hybrid (.io style) constants
    private static final float BOOST_SPEED_MULTIPLIER = 2.0f;
    private static final int BOOST_MIN_LENGTH = 5;       // cannot boost below this many segments
    private static final int BOOST_SHED_INTERVAL_TICKS = 2; // shed 1 segment every 2 ticks (~300ms)
    private static final int MAX_FOODS = 80;             // cap on food/orbs so boost trails can't explode
    private static final int GATE_GROWTH_SCORE = 100;    // growth gates above this score

    // Boost coin / milestone constants
    private static final int BOOST_COIN_TIME_REWARD = 10;    // +10 coins every 5s of play
    private static final long BOOST_COIN_INTERVAL_MS = 5000; // 5 seconds between time-based coin rewards
    private static final int BOOST_COIN_MILESTONE_REWARD = 50; // +50 coins per score milestone reached
    private static final int[] SCORE_MILESTONES = {100, 500, 1000};

    public static void startGame(Room room) {
        if (activeGames.containsKey(room.getCode())) {
            System.out.println("[GameEngine] startGame SKIPPED (already active) code=" + room.getCode());
            return;
        }
        System.out.println("[GameEngine] startGame code=" + room.getCode() + " players=" + room.getPlayers().size());

        room.setGameInProgress(true);
        GameState state = room.getGameState();
        if (state != null) {
            state.setCountdown(3);
            state.setGameOver(false);
            state.setGameStarted(false);
            gameStartTimes.put(room.getCode(), System.currentTimeMillis());
        }

        // Log game start
        String[] playerNames = room.getPlayers().stream().map(Snake::getName).toArray(String[]::new);
        GameLogger.gameStarted(room.getCode(), room.getPlayers().size(), playerNames);

        int tickRateMs = room.getTickRateMs() > 0 ? room.getTickRateMs() : DEFAULT_TICK_INTERVAL_MS;
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> tick(room), 50, tickRateMs, TimeUnit.MILLISECONDS);
        activeGames.put(room.getCode(), future);
    }

    public static void initGameState(Room room) {
        int gridSize = room.getGridSize() > 0 ? room.getGridSize() : DEFAULT_GRID_SIZE;
        double foodDensity = room.getFoodDensity() > 0 ? room.getFoodDensity() : 1.0;
        
        List<Food> foods = new ArrayList<>();
        int initialFoodCount = (int) Math.max(1, Math.round(4 * foodDensity));
        for (int i = 0; i < initialFoodCount; i++) {
            Food food = spawnFood(room);
            foods.add(food);
            // Log initial food spawn
            GameLogger.foodSpawned(room.getCode(), food.getType(), food.getX(), food.getY(), food.getValue(), "initial");
        }
        GameState state = new GameState(room.getPlayers(), foods, gridSize, false, false, 0);
        state.setBoostCoins(20); // Initial boost coins
        state.setSnakeSpeedBoost(false);
        state.setSpeedBoostExpireTime(0);
        room.setGameState(state);
    }

    private static void tick(Room room) {
        try {
        GameState state = room.getGameState();
        if (state == null || state.isGameOver()) return;

        int gridSize = room.getGridSize() > 0 ? room.getGridSize() : DEFAULT_GRID_SIZE;
        boolean enableBoost = room.isEnableBoost();
        boolean enableGoldenFood = room.isEnableGoldenFood();
        
        state.setTick(state.getTick() + 1);

        // Countdown phase (real-time based)
        if (state.getCountdown() > 0) {
            Long startTime = gameStartTimes.get(room.getCode());
            long elapsed = startTime != null ? System.currentTimeMillis() - startTime : 0;
            int remaining = 3 - (int)(elapsed / 1000);
            if (remaining <= 0) {
                state.setCountdown(0);
                state.setGameStarted(true);
                SpectatorWebSocket.broadcastToAll(room.getCode(), state);
            } else {
                state.setCountdown(remaining);
                SpectatorWebSocket.broadcastToAll(room.getCode(), state);
                return;
            }
        }

        if (!state.isGameStarted()) {
            state.setGameStarted(true);
        }

        List<Snake> snakes;
        synchronized (room) {
            snakes = new ArrayList<>(state.getSnakes());
        }

        // Apply queued directions
        for (Snake snake : snakes) {
            if (!snake.isAlive()) continue;
            snake.setDirection(snake.getNextDirection());
        }

        // Calculate next positions + the FULL path the head sweeps this tick.
        // The head can move more than one cell per tick (2x/3x speed), so it
        // would otherwise jump OVER bodies instead of colliding with them.
        Map<Snake, Point> nextHeads = new HashMap<>();
        Map<Snake, List<Point>> paths = new HashMap<>();
        for (Snake snake : snakes) {
            if (!snake.isAlive()) continue;
            Point head = snake.getHead();
            Point next = snake.getNextHead();
            nextHeads.put(snake, next);
            List<Point> path = new ArrayList<>();
            int sx = (int) Math.signum(next.getX() - head.getX());
            int sy = (int) Math.signum(next.getY() - head.getY());
            int len = Math.max(Math.abs(next.getX() - head.getX()), Math.abs(next.getY() - head.getY()));
            for (int k = 1; k <= len; k++) {
                path.add(new Point(head.getX() + sx * k, head.getY() + sy * k));
            }
            paths.put(snake, path);
        }

        // Track snakes killed by the wall and by collisions (for food spawning).
        Set<Snake> wallDeaths = new HashSet<>();

        // Check wall collisions along the ENTIRE path (not just the endpoint),
        // so a fast snake can't skip out of the board.
        for (Map.Entry<Snake, List<Point>> entry : paths.entrySet()) {
            Snake snake = entry.getKey();
            if (!snake.isAlive()) continue;
            for (Point cell : entry.getValue()) {
                if (cell.getX() < 0 || cell.getX() >= gridSize || cell.getY() < 0 || cell.getY() >= gridSize) {
                    snake.setAlive(false);
                    wallDeaths.add(snake);
                    // Log wall collision
                    GameLogger.collisionWall(room.getCode(), snake.getName(), state.getTick(), cell.getX(), cell.getY());
                    break;
                }
            }
        }

        // Foods that exist at the start of this tick (before any collision-food spawns).
        List<Food> foods = new ArrayList<>(state.getFoods() != null ? state.getFoods() : new ArrayList<>());

        // Resolve head-ons and head-body hits against the post-move bodies.
        Set<Snake> collisionDeaths = resolveHeadBodyCollisions(snakes, nextHeads, paths, foods, room.getCode(), state.getTick());

        // Log all player deaths (wall + collisions)
        for (Snake deadSnake : wallDeaths) {
            GameLogger.playerDied(room.getCode(), deadSnake.getName(), "wall", state.getTick(), deadSnake.getScore(), deadSnake.getSegments().size());
        }
        for (Snake deadSnake : collisionDeaths) {
            // Determine cause: head-on or head-body (both marked as collisionDeaths)
            // We'll use a generic "collision" cause since both are handled in resolveHeadBodyCollisions
            GameLogger.playerDied(room.getCode(), deadSnake.getName(), "collision", state.getTick(), deadSnake.getScore(), deadSnake.getSegments().size());
        }

        // Spawn food from collision deaths (body segments become GOLDEN food, value 3)
        if (enableGoldenFood) {
            // Spawn food from collision deaths (body segments become GOLDEN food, value 3)
            for (Snake deadSnake : collisionDeaths) {
                for (Point segment : deadSnake.getSegments()) {
                    foods.add(new Food(segment.getX(), segment.getY(), "GOLDEN"));
                    // Log golden food spawned from death
                    GameLogger.foodSpawned(room.getCode(), "GOLDEN", segment.getX(), segment.getY(), 3, "collisionDeath");
                }
            }
        }

        // Move alive snakes and handle food consumption
        for (Snake snake : snakes) {
            if (!snake.isAlive()) continue;
            Point head = nextHeads.get(snake);
            snake.getSegments().add(0, head);

            // Check if head hits any food
            Food eatenFood = null;
            for (Food food : foods) {
                if (head.getX() == food.getX() && head.getY() == food.getY()) {
                    eatenFood = food;
                    break;
                }
            }

            if (eatenFood != null) {
                foods.remove(eatenFood);
                // Hybrid rule: food value = length gained (score == length)
                snake.setGrowthPoints(snake.getGrowthPoints() + eatenFood.getValue());
                // Log food consumption
                GameLogger.foodConsumed(room.getCode(), snake.getName(), eatenFood.getType(), eatenFood.getValue(), 
                    eatenFood.getX(), eatenFood.getY(), snake.getSegments().size());
            }

            if (snake.getGrowthPoints() > 0) {
                snake.setGrowthPoints(snake.getGrowthPoints() - 1);
            } else {
                snake.getSegments().remove(snake.getSegments().size() - 1);
            }
            
                    // Auto-refill boost coins when snake grows (every 5 seconds)
            Long gameStartTimeObj = gameStartTimes.get(room.getCode());
            if (gameStartTimeObj != null) {
                long gameStartTime = gameStartTimeObj;
                String roomCode = room.getCode();
                long now = System.currentTimeMillis();
                long lastGrowthTime = growthTimers.getOrDefault(roomCode, 0L);
                int timedReward = timedBoostCoinReward(now, gameStartTime, lastGrowthTime);
                if (timedReward > 0) {
                    state.setBoostCoins(state.getBoostCoins() + timedReward);
                    growthTimers.put(roomCode, now);
                }
            }
        }

        // Respawn food if none left
        if (foods.isEmpty()) {
            double foodDensity = room.getFoodDensity() > 0 ? room.getFoodDensity() : 1.0;
            int respawnCount = Math.max(1, (int) Math.round(foodDensity));
            for (int i = 0; i < respawnCount; i++) {
                Food food = spawnFood(room);
                foods.add(food);
                GameLogger.foodSpawned(room.getCode(), food.getType(), food.getX(), food.getY(), food.getValue(), "respawn");
            }
        }

        // Handle score milestones for boost coin rewards
        String roomCode = room.getCode();
        long currentScoreMilestone = 0;
        for (Snake snake : snakes) {
            int score = snake.getScore();
            if (currentScoreMilestone == 0) {
                currentScoreMilestone = score;
            } else {
                currentScoreMilestone = Math.max(currentScoreMilestone, score);
            }
        }
        
        Map<String, Long> milestones = lastScoreMilestoneCheck.getOrDefault(roomCode, new HashMap<String, Long>());

        for (Snake snake : snakes) {
            int coinsBefore = state.getBoostCoins();
            applyScoreMilestoneCoins(state, milestones, snake);
            int coinsAfter = state.getBoostCoins();
            int coinsAwarded = coinsAfter - coinsBefore;
            if (coinsAwarded > 0) {
                // Find which milestone was reached
                int score = snake.getScore();
                for (int milestone : SCORE_MILESTONES) {
                    if (score >= milestone) {
                        GameLogger.scoreMilestoneReached(room.getCode(), snake.getName(), milestone, coinsAwarded, coinsAfter);
                        break;
                    }
                }
            }
        }
        lastScoreMilestoneCheck.put(roomCode, milestones);
        
        // Hybrid boost (slither.io rule): holding boost runs 2x speed but sheds
        // tail segments as food. Cannot boost below BOOST_MIN_LENGTH segments.
        if (enableBoost) {
            for (Snake snake : snakes) {
                boolean wasBoosting = snake.isBoosting();
                applyHybridBoost(snake, state, foods, room.getCode());
            }
        } else {
            // Disable boosting for all snakes if boost is disabled
            for (Snake snake : snakes) {
                snake.setBoosting(false);
                snake.setSpeedMultiplier(1.0f);
            }
        }

        // Kill rewards come from the dead snake's body becoming orbs (food) -
        // the slither.io way: the killer eats the mass, no score handouts.

        state.setFoods(foods);

        // Hybrid rule: score == length (number of segments), like slither.io mass
        for (Snake snake : snakes) {
            snake.setScore(snake.getSegments().size());
        }

        // Check if game over (all dead or only one alive in multiplayer)
        long elapsedMs = 0;
        Long gameStart = gameStartTimes.get(room.getCode());
        if (gameStart != null) elapsedMs = Math.max(0, System.currentTimeMillis() - gameStart);

        int aliveCount = 0;
        List<Snake> aliveSnakes = new ArrayList<>();
        for (Snake snake : snakes) {
            if (snake.isAlive()) {
                aliveCount++;
                aliveSnakes.add(snake);
            }
        }
        
        // Game over:
        // - solo (1 player): only when the snake dies (aliveCount == 0)
        // - multiplayer (2+): when one remains (winner) or all die together (draw)
        boolean multiplayer = snakes.size() > 1;
        if ((multiplayer && aliveCount <= 1) || (!multiplayer && aliveCount == 0)) {
            state.setRoundDurationMs(elapsedMs);
            logGameOverSnapshot(room, state, elapsedMs, aliveCount);
            
            // Determine winner and log game end
            String winnerName = null;
            if (aliveCount == 1) {
                winnerName = aliveSnakes.get(0).getName();
            }
            String[] finalScores = snakes.stream()
                .map(s -> s.getName() + ":" + s.getScore() + (s.isAlive() ? "" : "(dead)"))
                .toArray(String[]::new);
            GameLogger.gameEnded(room.getCode(), winnerName, elapsedMs, state.getTick(), finalScores);
            
            state.setGameOver(true);
            room.setGameInProgress(false);
            System.out.println("[GameEngine] GAME OVER code=" + room.getCode() + " gameInProgress set to false");
            stopGame(room.getCode());
        }

        // Update advanced bots using AdvancedBotManager
        AdvancedBotManager.updateAdvancedBots(room, state, snakes);

        SpectatorWebSocket.broadcastToAll(room.getCode(), state);
        if (TICK_DEBUG) {
            StringBuilder sb = new StringBuilder();
            for (Snake s : snakes) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(s.getName()).append('=').append(s.getScore()).append(s.isAlive() ? "" : "(dead)");
            }
            System.out.println("[TickDebug] room=" + room.getCode() + " tick=" + state.getTick()
                + " alive=" + aliveCount + " scores={" + sb + "} elapsedMs=" + elapsedMs + " gameOver=" + state.isGameOver());
        }
        } catch (Exception e) {
            System.out.println("[GameEngine] EXCEPTION in tick for room " + room.getCode() + ": " + e);
            e.printStackTrace();
        }
    }

    private static void logGameOverSnapshot(Room room, GameState state, long elapsedMs, int aliveCount) {
        StringBuilder sb = new StringBuilder();
        List<Snake> snakes = state.getSnakes();
        if (snakes != null) {
            for (Snake s : snakes) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(s.getName()).append(':').append(s.getScore()).append(s.isAlive() ? "" : "(dead)");
            }
        }
        System.out.println("[GameEngine] GAME OVER SNAPSHOT room=" + room.getCode() + " tick=" + state.getTick()
            + " elapsedMs=" + elapsedMs + " scores={" + sb + "} alive=" + aliveCount);
    }

    private static Food spawnFood(Room room) {
        int gridSize = room.getGridSize() > 0 ? room.getGridSize() : DEFAULT_GRID_SIZE;
        boolean enableGoldenFood = room.isEnableGoldenFood();
        
        Random rand = new Random();
        Set<Point> occupied = new HashSet<>();
        for (Snake snake : room.getPlayers()) {
            occupied.addAll(snake.getSegments());
        }

        List<Point> free = new ArrayList<>();
        for (int x = 0; x < gridSize; x++) {
            for (int y = 0; y < gridSize; y++) {
                Point p = new Point(x, y);
                if (!occupied.contains(p)) free.add(p);
            }
        }

        if (free.isEmpty()) return new Food(0, 0);
        Point pos = free.get(rand.nextInt(free.size()));
        String type = "NORMAL";
        if (enableGoldenFood && rand.nextDouble() < 0.15) {
            type = "GOLDEN";
        }
        Food food = new Food(pos.getX(), pos.getY(), type);
        // Note: Logging is handled by the caller (initGameState logs "initial", tick logs "respawn" or "collisionDeath")
        return food;
    }

    public static void stopGame(String roomCode) {
        ScheduledFuture<?> future = activeGames.remove(roomCode);
        if (future != null) {
            future.cancel(false);
        }
        gameStartTimes.remove(roomCode);
        // Get room and set game over timestamp
        RoomManager roomManager = RoomManager.getInstance();
        Room room = roomManager.getRoom(roomCode);
        if (room != null) {
            room.setGameOverTimestamp(System.currentTimeMillis());
        }
    }

    public static boolean isGameRunning(String roomCode) {
        return activeGames.containsKey(roomCode);
    }

    public static void resetGame(Room room) {
        // Clear any stale WebSocket sessions from previous round
        GameWebSocket.clearRoomSessions(room.getCode());
        
        synchronized (room) {
            List<Snake> existingPlayers = new ArrayList<>(room.getPlayers());
            
            int gridSize = room.getGridSize() > 0 ? room.getGridSize() : DEFAULT_GRID_SIZE;
            int margin = Math.max(3, gridSize / 10); // At least 3 cells from edge
            int offset = gridSize - margin - 1;

            // Assign spawn positions & directions based on 4-quadrant layout
            Point[] spawnHeads = { 
                new Point(margin, margin), 
                new Point(offset, margin), 
                new Point(margin, offset), 
                new Point(offset, offset) 
            };
            String[] spawnDirs = { "RIGHT", "LEFT", "RIGHT", "LEFT" };

            for (int i = 0; i < existingPlayers.size(); i++) {
                Snake snake = existingPlayers.get(i);
                int idx = i % 4;
                Point head = spawnHeads[idx];
                String dir = spawnDirs[idx];
                int dx = dir.equals("RIGHT") ? -1 : 1;
                
                List<Point> segments = new ArrayList<>();
                segments.add(head);
                segments.add(new Point(head.getX() + dx, head.getY()));
                segments.add(new Point(head.getX() + 2 * dx, head.getY()));
                
                snake.setSegments(segments);
                snake.setDirection(dir);
                snake.setNextDirection(dir);
                snake.setAlive(true);
                snake.setBoosting(false);
                snake.setSpeedMultiplier(1.0f);
                snake.setReady(snake.isBot()); // bots auto-ready every round
                snake.setScore(0);
                snake.setGrowthPoints(0);
            }

            // Clear per-round auxiliary maps so a new round starts clean (previously leaked across rounds)
            lastScoreMilestoneCheck.remove(room.getCode());
            growthTimers.remove(room.getCode());

            room.setPlayers(existingPlayers);
            // Clear game over timestamp when resetting
            room.setGameOverTimestamp(0);
            initGameState(room);
        }
    }

    /**
     * Applies gated body growth after a snake's head has moved onto a food cell.
     *
     * <p>Adds the food value to the score, then decides growth using a gate that
     * depends on the NEW score: below/at 100 points every food grows one segment
     * (threshold 1); past 100 points growth is gated to every 4th accumulated
     * point (threshold 4). Pending points that don't reach the threshold carry
     * over via {@code growthPoints}.</p>
     *
     * <p>Growth means keeping the tail (the caller already advanced the head);
     * no growth means removing the tail, net zero segment change.</p>
     */
    public static void applyGatedGrowth(Snake snake, int foodValue) {
        snake.setScore(snake.getScore() + foodValue);
        int threshold = snake.getScore() > GATE_GROWTH_SCORE ? 4 : 1;
        int accumulated = snake.getGrowthPoints() + foodValue;
        if (accumulated >= threshold) {
            accumulated -= threshold;
        } else {
            List<Point> segments = snake.getSegments();
            if (segments.size() > 1) {
                segments.remove(segments.size() - 1);
            }
        }
        snake.setGrowthPoints(accumulated % threshold);
    }

    /**
     * Resolves head-on and head-body collisions for a single tick.
     *
     * <p>Head-ons: when two or more different heads sweep the same cell along their
     * paths in the same tick, all of them die (mutual kill).</p>
     *
     * <p>Head-body: attacker heads are checked against the POST-MOVE bodies of the
     * other snakes (new head added, tail removed unless the snake is growing or
     * feeding). This closes the "chase" hole where a chaser landing on a leader's
     * CURRENT head cell escaped collision: after the move that cell becomes the
     * leader's neck and is solid, so a snake riding up the same row/column behind
     * another snake's head must die. A chaser following the vacating tail still
     * survives (that cell is not in the post-move body), and crossing one's OWN
     * body stays allowed (hybrid .io rule).</p>
     *
     * @return the set of snakes killed by collision this tick (they are marked dead)
     */
    static Set<Snake> resolveHeadBodyCollisions(List<Snake> snakes, Map<Snake, Point> nextHeads,
                                               Map<Snake, List<Point>> paths, List<Food> foods,
                                               String roomCode, int tick) {
        Set<Snake> collisionDeaths = new HashSet<>();

        // Head-on collisions: any cell occupied by 2+ different heads during this
        // tick (any step along their paths) is a mutual kill.
        Map<Point, List<Snake>> headOnMap = new HashMap<>();
        for (Map.Entry<Snake, List<Point>> entry : paths.entrySet()) {
            Snake snake = entry.getKey();
            if (!snake.isAlive()) continue; // already dead from wall
            for (Point cell : entry.getValue()) {
                headOnMap.computeIfAbsent(cell, k -> new ArrayList<>()).add(snake);
            }
        }

        for (Map.Entry<Point, List<Snake>> entry : headOnMap.entrySet()) {
            if (entry.getValue().size() >= 2) {
                Set<Snake> distinct = new HashSet<>(entry.getValue());
                if (distinct.size() >= 2) {
                    String[] otherPlayers = distinct.stream()
                        .map(Snake::getName)
                        .toArray(String[]::new);
                    for (Snake snake : distinct) {
                        snake.setAlive(false);
                        collisionDeaths.add(snake);
                        // Log head-on collision for each snake
                        GameLogger.collisionHeadOn(roomCode, snake.getName(), tick, entry.getKey().getX(), entry.getKey().getY(), otherPlayers);
                    }
                }
            }
        }

        // Head-body collisions: build the POST-MOVE body of every alive snake.
        Map<Point, Snake> bodyOccupancy = new HashMap<>();
        for (Snake snake : snakes) {
            if (!snake.isAlive()) continue;
            Point nextHead = nextHeads.get(snake);
            List<Point> nextSegments = new ArrayList<>();
            nextSegments.add(nextHead);
            nextSegments.addAll(snake.getSegments());
            boolean ateFood = false;
            for (Food food : foods) {
                if (food.getX() == nextHead.getX() && food.getY() == nextHead.getY()) {
                    ateFood = true;
                    break;
                }
            }
            // Tail is retained this tick if the snake is growing or eats food.
            boolean tailRetained = snake.getGrowthPoints() > 0 || ateFood;
            if (!tailRetained) {
                nextSegments.remove(nextSegments.size() - 1);
            }
            // Index 0 is the new head (head-ons already handled); the rest is solid body.
            for (int i = 1; i < nextSegments.size(); i++) {
                bodyOccupancy.put(nextSegments.get(i), snake);
            }
        }

        for (Map.Entry<Snake, List<Point>> entry : paths.entrySet()) {
            Snake attacker = entry.getKey();
            if (!attacker.isAlive()) continue; // already dead from wall or head-on
            for (Point cell : entry.getValue()) {
                Snake bodyOwner = bodyOccupancy.get(cell);
                if (bodyOwner != null && bodyOwner != attacker) {
                    // Attacker's head hits another snake's body - dies.
                    // Crossing its OWN body is allowed (hybrid .io rule).
                    attacker.setAlive(false);
                    collisionDeaths.add(attacker);
                    // Log head-body collision
                    GameLogger.collisionHeadBody(roomCode, attacker.getName(), tick, cell.getX(), cell.getY(), bodyOwner.getName());
                    break;
                }
            }
        }

        return collisionDeaths;
    }

    /**
     * Time-based boost coin reward: +10 coins once every 5 seconds of gameplay.
     * Returns the reward amount (10) when both the 5s-since-start and 5s-since-last-reward
     * conditions hold, otherwise 0. The caller is responsible for updating the last-reward
     * timestamp when a reward is granted.
     */
    static int timedBoostCoinReward(long now, long gameStartTime, long lastRewardTime) {
        if (now - gameStartTime >= BOOST_COIN_INTERVAL_MS && now - lastRewardTime >= BOOST_COIN_INTERVAL_MS) {
            return BOOST_COIN_TIME_REWARD;
        }
        return 0;
    }

    /**
     * Score-milestone boost coin reward: +50 coins the first time a snake's score reaches
     * each milestone (100, 500, 1000). Per-snake tracking uses the {@code name_color} key;
     * the {@code milestones} map is updated in place, so calling this repeatedly for an
     * unchanged score awards nothing. If a snake jumps past several milestones at once,
     * all crossed milestones are awarded in a single call (existing behavior).
     *
     * @return total coins awarded by this call
     */
    static int applyScoreMilestoneCoins(GameState state, Map<String, Long> milestones, Snake snake) {
        int awarded = 0;
        int score = snake.getScore();
        String snakeId = snake.getName() + "_" + snake.getColor();
        Long lastMilestoneCheck = milestones.getOrDefault(snakeId, 0L);
        for (int milestone : SCORE_MILESTONES) {
            if (score >= milestone && lastMilestoneCheck < milestone) {
                awarded += BOOST_COIN_MILESTONE_REWARD;
                milestones.put(snakeId, (long) milestone);
            }
        }
        state.setBoostCoins(state.getBoostCoins() + awarded);
        return awarded;
    }

    /**
     * Hybrid (slither.io-style) boost for one snake on one tick: while boosting the snake
     * moves at BOOST_SPEED_MULTIPLIER and sheds one tail segment as NORMAL food every
     * BOOST_SHED_INTERVAL_TICKS ticks. Boost auto-disables at or below BOOST_MIN_LENGTH
     * segments. Dead snakes are force-unboosted with multiplier reset to 1.0.
     *
     * @return true if a tail segment was shed into {@code foods} this tick
     */
    static boolean applyHybridBoost(Snake snake, GameState state, List<Food> foods, String roomCode) {
        if (!snake.isAlive()) {
            snake.setBoosting(false);
            snake.setSpeedMultiplier(1.0f);
            return false;
        }
        if (snake.isBoosting()) {
            if (snake.getSegments().size() <= BOOST_MIN_LENGTH) {
                snake.setBoosting(false);
                snake.setSpeedMultiplier(1.0f);
                return false;
            }
            snake.setSpeedMultiplier(BOOST_SPEED_MULTIPLIER);
            if (state.getTick() % BOOST_SHED_INTERVAL_TICKS == 0) {
                List<Point> segs = snake.getSegments();
                Point tail = segs.get(segs.size() - 1);
                segs.remove(segs.size() - 1);
                if (foods.size() < MAX_FOODS) {
                    foods.add(new Food(tail.getX(), tail.getY(), "NORMAL"));
                    // Log boost segment shed
                    GameLogger.boostShedSegment(roomCode, snake.getName(), tail.getX(), tail.getY());
                    return true;
                }
            }
        } else {
            snake.setSpeedMultiplier(1.0f);
        }
        return false;
    }
}

