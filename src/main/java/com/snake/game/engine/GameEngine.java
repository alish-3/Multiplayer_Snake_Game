package com.snake.game.engine;

import com.snake.game.model.*;
import com.snake.game.servlet.GameWebSocket;
import java.util.*;
import java.util.concurrent.*;

public class GameEngine {
    private static final int GRID_SIZE = 30;
    private static final int TICK_INTERVAL_MS = 150;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private static final Map<String, ScheduledFuture<?>> activeGames = new ConcurrentHashMap<>();
    private static final Map<String, Long> gameStartTimes = new ConcurrentHashMap<>();
    private static final Map<String, List<Point>> lastConsumedPositions = new ConcurrentHashMap<>();
    private static final Map<String, Long> growthTimers = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Long>> lastScoreMilestoneCheck = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastFoodEatenTime = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> speedBoostAvailable = new ConcurrentHashMap<>();
    private static final Map<String, Long> gameOverTimestamps = new ConcurrentHashMap<>();
    private static final int GROWTH_GATE_SCORE = 100;   // past this score, growth is gated
    private static final int GROWTH_SEGMENT_INTERVAL = 4; // past the gate, grow every 4th food point
    private static final boolean TICK_DEBUG = System.getenv("SNAKE_TICK_DEBUG") != null || Boolean.getBoolean("snake.tickDebug");

    public static void startGame(Room room) {
        if (activeGames.containsKey(room.getCode())) return;

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

    private static void initGameState(Room room) {
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

        // Check head-body collisions (snake A's head hits snake B's body)
        // Build set of all body segments for alive snakes (excluding heads which we already handled)
        Map<Point, Snake> bodyOccupancy = new HashMap<>();
        for (Snake snake : snakes) {
            if (!snake.isAlive()) continue;
            List<Point> segments = snake.getSegments();
            // Skip head (index 0) - head-on already handled
            for (int i = 1; i < segments.size(); i++) {
                bodyOccupancy.put(segments.get(i), snake);
            }
        }

        for (Map.Entry<Snake, Point> entry : nextHeads.entrySet()) {
            Snake attacker = entry.getKey();
            if (!attacker.isAlive()) continue; // already dead from wall or head-on
            Point head = entry.getValue();
            Snake bodyOwner = bodyOccupancy.get(head);
            if (bodyOwner != null && bodyOwner != attacker) {
                // Attacker (snake with head hitting another snake's body) dies
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
                applyGatedGrowth(snake, eatenFood.getValue());
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
        
        // Handle speed boost duration checks and crowd hunting bonuses
        
        // Check speed boost expiration
        for (Snake snake : snakes) {
            if (snake.isSpeedBoostActive() && now > snake.getSpeedBoostEndTime()) {
                // Deactivate speed boost
                snake.setSpeedBoostActive(false);
                snake.setSpeedMultiplier(1.0f);
            }
        }
        
        // Check last food eaten time for 5% chance to activate speed boost
        List<Point> roomLastConsumed = lastConsumedPositions.computeIfAbsent(roomCode, k -> new ArrayList<>());
        
        boolean[] goldenEatenThisTick = {false};
        Snake[] snakeAteGolden = {null};
        
        for (Snake snake : snakes) {
            if (!snake.isAlive()) continue;
            Long lastFoodEaten = lastFoodEatenTime.getOrDefault(roomCode, 0L);
            long timeSinceLastFood = now - lastFoodEaten;
            
            // Check if this snake just ate golden food with 5% chance
            for (Food food : state.getFoods()) {
                if (food.getType().equals("GOLDEN") && food.getX() == nextHeads.get(snake).getX() && 
                    food.getY() == nextHeads.get(snake).getY()) {
                    Random rand = new Random();
                    if (rand.nextDouble() < 0.05 && snake.getScore() >= 10) { // 5% chance, minimum score requirement
                        if (state.getBoostCoins() >= 3) {
                            // Activate speed boost
                            snake.setSpeedBoostActive(true);
                            snake.setSpeedBoostEndTime(now + 3000);
                            snake.setSpeedMultiplier(3.0f);
                            state.setBoostCoins(state.getBoostCoins() - 3);
                        }
                    }
                    goldenEatenThisTick[0] = true;
                    snakeAteGolden[0] = snake;
                    // Record last consumed position to avoid repeat spawns
                    Point consumedPos = new Point(food.getX(), food.getY());
                    if (!roomLastConsumed.contains(consumedPos)) {
                        roomLastConsumed.add(consumedPos);
                        // Keep only last 50 positions
                        if (roomLastConsumed.size() > 50) {
                            roomLastConsumed.remove(0);
                        }
                    }
                    lastFoodEatenTime.put(roomCode, now);
                    break;
                }
            }
        }
        
        // Handle crowd hunting bonuses (dead snakes already processed)
        // Track which snakes are dead from collisions to award bonuses to live snakes
        Set<Snake> deadFromCollision = new HashSet<>(collisionDeaths);
        
        for (Snake snake : snakes) {
            if (snake.isAlive()) {
                // Award bonus for each killed snake (100 points + 50 boost coins)
                for (Snake deadSnake : deadFromCollision) {
                    // Check if this live snake killed the dead snake (collision detection)
                    // For now, we'll give bonus to all alive snakes for dead snakes
                    // In a complete implementation, we'd track which snake killed which
                    snake.setScore(snake.getScore() + 100); // 100 bonus points
                    state.setBoostCoins(state.getBoostCoins() + 50);
                    
                    // For segments of the dead snake that become food
                    for (Point segment : deadSnake.getSegments()) {
                        // These segments become GOLDEN food (value 3) and provide boost coin rewards
                        snake.setScore(snake.getScore() + 3); // 3x golden food points
                        state.setBoostCoins(state.getBoostCoins() + 25);
                    }
                }
            }
        }
        
        // Also, give bonus for killing (when snake dies from collision and another snake is alive)
        for (Snake deadSnake : deadFromCollision) {
            for (Snake snake : snakes) {
                if (snake.isAlive() && !deadFromCollision.contains(snake)) {
                    // Award bonus to remaining alive snakes for the death
                    snake.setScore(snake.getScore() + 1); // Minimal bonus since primary bonus already awarded
                    state.setBoostCoins(state.getBoostCoins() + 10);
                }
            }
        }

        state.setFoods(foods);

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
        
        // Special handling for exactly 2 players
        if (snakes.size() == 2) {
            if (aliveCount == 1) {
                // One snake alive - game over, determine winner
                state.setRoundDurationMs(elapsedMs);
                logGameOverSnapshot(room, state, elapsedMs, aliveCount);
                state.setGameOver(true);
                room.setGameInProgress(false);
                stopGame(room.getCode());
            } else if (aliveCount == 0) {
                // Both snakes died simultaneously - no bonus, game ends as draw
                state.setRoundDurationMs(elapsedMs);
                logGameOverSnapshot(room, state, elapsedMs, aliveCount);
                state.setGameOver(true);
                room.setGameInProgress(false);
                stopGame(room.getCode());
        } else {
            // For 3+ players: game over when only one or none alive
            if (aliveCount <= 1 && snakes.size() > 1) {
                state.setRoundDurationMs(elapsedMs);
                logGameOverSnapshot(room, state, elapsedMs, aliveCount);
                state.setGameOver(true);
                room.setGameInProgress(false);
                stopGame(room.getCode());
            }
        }

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
        }
        } catch (Exception e) {
            System.out.println("[GameEngine] EXCEPTION in tick for room " + room.getCode() + ": " + e);
            e.printStackTrace();
        }
    }

    /**
     * Applies food consumption to a snake: adds the food value to the score, then applies
     * gated body growth. The caller (tick) must have already moved the head onto the food
     * cell, so "grow" means keeping the tail and "no growth" removes the tail segment.
     */
    static void applyGatedGrowth(Snake snake, int foodValue) {
        snake.setScore(snake.getScore() + foodValue);
        // Gated growth: below GROWTH_GATE_SCORE every point grows a segment (legacy behavior);
        // past the gate, only every GROWTH_SEGMENT_INTERVAL-th accumulated point grows a segment.
        int growThreshold = snake.getScore() > GROWTH_GATE_SCORE ? GROWTH_SEGMENT_INTERVAL : 1;
        int pending = snake.getGrowthPoints() + foodValue;
        if (pending >= growThreshold) {
            snake.setGrowthPoints(pending % growThreshold);
            // keep tail -> snake grows one segment
        } else {
            snake.setGrowthPoints(pending);
            snake.getSegments().remove(snake.getSegments().size() - 1); // no growth
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
                snake.setReady(false);
                snake.setScore(0);
                snake.setGrowthPoints(0);
            }

            // Clear per-round auxiliary maps so a new round starts clean (previously leaked across rounds)
            lastScoreMilestoneCheck.remove(room.getCode());
            growthTimers.remove(room.getCode());
            lastConsumedPositions.remove(room.getCode());
            lastFoodEatenTime.remove(room.getCode());
            speedBoostAvailable.remove(room.getCode());
            gameOverTimestamps.remove(room.getCode());

            room.setPlayers(existingPlayers);
            // Clear game over timestamp when resetting
            room.setGameOverTimestamp(0);
            initGameState(room);
        }
    }
}

