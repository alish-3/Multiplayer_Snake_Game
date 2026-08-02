package com.snake.game.engine;

import com.snake.game.model.*;
import com.snake.game.servlet.GameWebSocket;
import com.snake.game.util.AdvancedBotManager;
import java.util.*;
import java.util.concurrent.*;

public class GameEngine {
    private static final int GRID_SIZE = 30;
    private static final int TICK_INTERVAL_MS = 150;
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

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> tick(room), 50, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        activeGames.put(room.getCode(), future);
    }

    public static void initGameState(Room room) {
        List<Food> foods = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            foods.add(spawnFood(room));
        }
        GameState state = new GameState(room.getPlayers(), foods, GRID_SIZE, false, false, 0);
        state.setBoostCoins(20); // Initial boost coins
        state.setSnakeSpeedBoost(false);
        state.setSpeedBoostExpireTime(0);
        room.setGameState(state);
    }

    private static void tick(Room room) {
        try {
        GameState state = room.getGameState();
        if (state == null || state.isGameOver()) return;

        state.setTick(state.getTick() + 1);

        // Countdown phase (real-time based)
        if (state.getCountdown() > 0) {
            Long startTime = gameStartTimes.get(room.getCode());
            long elapsed = startTime != null ? System.currentTimeMillis() - startTime : 0;
            int remaining = 3 - (int)(elapsed / 1000);
            if (remaining <= 0) {
                state.setCountdown(0);
                state.setGameStarted(true);
                GameWebSocket.broadcastState(room.getCode(), state);
            } else {
                state.setCountdown(remaining);
                GameWebSocket.broadcastState(room.getCode(), state);
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

        // Calculate next positions
        Map<Snake, Point> nextHeads = new HashMap<>();
        for (Snake snake : snakes) {
            if (!snake.isAlive()) continue;
            Point next = snake.getNextHead();
            nextHeads.put(snake, next);
        }

        // Track which snakes die from collision (for food spawning)
        Set<Snake> collisionDeaths = new HashSet<>();
        Set<Snake> wallDeaths = new HashSet<>();

        // Check wall collisions
        for (Snake snake : snakes) {
            if (!snake.isAlive()) continue;
            Point head = nextHeads.get(snake);
            if (head.getX() < 0 || head.getX() >= GRID_SIZE || head.getY() < 0 || head.getY() >= GRID_SIZE) {
                snake.setAlive(false);
                wallDeaths.add(snake);
            }
        }

        // Check head-on collisions (multiple snakes moving to same cell)
        Map<Point, List<Snake>> headOnMap = new HashMap<>();
        for (Map.Entry<Snake, Point> entry : nextHeads.entrySet()) {
            Snake snake = entry.getKey();
            if (!snake.isAlive()) continue; // already dead from wall
            Point head = entry.getValue();
            headOnMap.computeIfAbsent(head, k -> new ArrayList<>()).add(snake);
        }

        for (List<Snake> contenders : headOnMap.values()) {
            if (contenders.size() >= 2) {
                // All contendents die in head-on collision
                for (Snake snake : contenders) {
                    snake.setAlive(false);
                    collisionDeaths.add(snake);
                }
            }
        }

        // Check head-body collisions (snake A's head hits snake B's body - or its own)
        // Build set of all body segments for alive snakes (excluding heads - already handled)
        Map<Point, Snake> bodyOccupancy = new HashMap<>();
        for (Snake snake : snakes) {
            if (!snake.isAlive()) continue;
            List<Point> segments = snake.getSegments();
            // Skip head (index 0) - head-on already handled.
            // Skip tail (last index) - the tail moves away this tick, so moving
            // into it is legal for the snake's own tail (except while growing).
            for (int i = 1; i < segments.size() - 1; i++) {
                bodyOccupancy.put(segments.get(i), snake);
            }
        }

        for (Map.Entry<Snake, Point> entry : nextHeads.entrySet()) {
            Snake attacker = entry.getKey();
            if (!attacker.isAlive()) continue; // already dead from wall or head-on
            Point head = entry.getValue();
            Snake bodyOwner = bodyOccupancy.get(head);
            if (bodyOwner != null && bodyOwner != attacker) {
                // Attacker's head hits another snake's body - dies.
                // Crossing its OWN body is allowed (hybrid .io rule).
                attacker.setAlive(false);
                collisionDeaths.add(attacker);
            }
        }

        // Spawn food from collision deaths (body segments become GOLDEN food, value 3)
        List<Food> foods = new ArrayList<>(state.getFoods() != null ? state.getFoods() : new ArrayList<>());
        for (Snake deadSnake : collisionDeaths) {
            for (Point segment : deadSnake.getSegments()) {
                foods.add(new Food(segment.getX(), segment.getY(), "GOLDEN"));
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
            }

            if (snake.getGrowthPoints() > 0) {
                snake.setGrowthPoints(snake.getGrowthPoints() - 1);
            } else {
                snake.getSegments().remove(snake.getSegments().size() - 1);
            }
            
                    // Auto-refill boost coins when snake grows (every 5 seconds)
            Long gameStartTimeObj = gameStartTimes.get(room.getCode());
            long now = System.currentTimeMillis();
            if (gameStartTimeObj != null) {
                long gameStartTime = gameStartTimeObj;
                String roomCode = room.getCode();
                long lastGrowthTime = growthTimers.getOrDefault(roomCode, 0L);
                if (now - gameStartTime >= 5000 && now - lastGrowthTime >= 5000) {
                    state.setBoostCoins(state.getBoostCoins() + 10);
                    growthTimers.put(roomCode, now);
                }
            }
        }

        // Respawn food if none left
        if (foods.isEmpty()) {
            foods.add(spawnFood(room));
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
        long now = System.currentTimeMillis();
        
        for (Snake snake : snakes) {
            int score = snake.getScore();
            String snakeId = snake.getName() + "_" + snake.getColor();
            
            // Check for score milestone (100, 500, 1000)
            Long lastMilestoneCheck = milestones.getOrDefault(snakeId, 0L);
            for (int milestone : new int[]{100, 500, 1000}) {
                if (score >= milestone && lastMilestoneCheck < milestone) {
                    state.setBoostCoins(state.getBoostCoins() + 50);
                    milestones.put(snakeId, (long) milestone);
                }
            }
        }
        lastScoreMilestoneCheck.put(roomCode, milestones);
        
        // Hybrid boost (slither.io rule): holding boost runs 2x speed but sheds
        // tail segments as food. Cannot boost below BOOST_MIN_LENGTH segments.
        for (Snake snake : snakes) {
            if (!snake.isAlive()) {
                snake.setBoosting(false);
                snake.setSpeedMultiplier(1.0f);
                continue;
            }
            if (snake.isBoosting()) {
                if (snake.getSegments().size() <= BOOST_MIN_LENGTH) {
                    snake.setBoosting(false);
                    snake.setSpeedMultiplier(1.0f);
                    continue;
                }
                snake.setSpeedMultiplier(BOOST_SPEED_MULTIPLIER);
                if (state.getTick() % BOOST_SHED_INTERVAL_TICKS == 0) {
                    List<Point> segs = snake.getSegments();
                    Point tail = segs.get(segs.size() - 1);
                    segs.remove(segs.size() - 1);
                    if (foods.size() < MAX_FOODS) {
                        foods.add(new Food(tail.getX(), tail.getY(), "NORMAL"));
                    }
                }
            } else {
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
            state.setGameOver(true);
            room.setGameInProgress(false);
            System.out.println("[GameEngine] GAME OVER code=" + room.getCode() + " gameInProgress set to false");
            stopGame(room.getCode());
        }

        // Update advanced bots using AdvancedBotManager
        AdvancedBotManager.updateAdvancedBots(room, state, snakes);

        GameWebSocket.broadcastState(room.getCode(), state);
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
        Random rand = new Random();
        Set<Point> occupied = new HashSet<>();
        for (Snake snake : room.getPlayers()) {
            occupied.addAll(snake.getSegments());
        }

        List<Point> free = new ArrayList<>();
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                Point p = new Point(x, y);
                if (!occupied.contains(p)) free.add(p);
            }
        }

        if (free.isEmpty()) return new Food(0, 0);
        Point pos = free.get(rand.nextInt(free.size()));
        String type = rand.nextDouble() < 0.15 ? "GOLDEN" : "NORMAL";
        return new Food(pos.getX(), pos.getY(), type);
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
        synchronized (room) {
            List<Snake> existingPlayers = new ArrayList<>(room.getPlayers());

            // Assign spawn positions & directions based on 4-quadrant layout
            Point[] spawnHeads = { new Point(5, 5), new Point(24, 5), new Point(5, 24), new Point(24, 24) };
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

    public static void applyGatedGrowth(Snake snake, int foodValue) {

    }
}

