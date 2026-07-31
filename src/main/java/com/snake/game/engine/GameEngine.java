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
        Food food = spawnFood(room);
        room.setGameState(new GameState(room.getPlayers(), food, GRID_SIZE, false, false, 0));
    }

    private static void tick(Room room) {
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

        // Check wall collisions
        for (Snake snake : snakes) {
            if (!snake.isAlive()) continue;
            Point head = nextHeads.get(snake);
            if (head.getX() < 0 || head.getX() >= GRID_SIZE || head.getY() < 0 || head.getY() >= GRID_SIZE) {
                snake.setAlive(false);
            }
        }

        // Check self-collision
        for (Snake snake : snakes) {
            if (!snake.isAlive()) continue;
            Point head = nextHeads.get(snake);
            for (int i = 0; i < snake.getSegments().size(); i++) {
                if (snake.getSegments().get(i).equals(head)) {
                    snake.setAlive(false);
                    break;
                }
            }
        }

        // Check collisions between snakes
        for (Snake snake : snakes) {
            if (!snake.isAlive()) continue;
            Point head = nextHeads.get(snake);
            for (Snake other : snakes) {
                if (other == snake || !other.isAlive()) continue;
                Point otherNext = nextHeads.get(other);
                if (otherNext != null && otherNext.equals(head)) {
                    snake.setAlive(false);
                    other.setAlive(false);
                }
                for (int i = 0; i < other.getSegments().size(); i++) {
                    if (other.getSegments().get(i).equals(head)) {
                        snake.setAlive(false);
                        break;
                    }
                }
            }
        }

        // Move snakes
        for (Snake snake : snakes) {
            if (!snake.isAlive()) continue;
            Point head = nextHeads.get(snake);
            snake.getSegments().add(0, head);

            // Check food
            Food food = state.getFood();
            if (head.getX() == food.getX() && head.getY() == food.getY()) {
                snake.setScore(snake.getScore() + food.getValue());
                state.setFood(spawnFood(room));
            } else {
                snake.getSegments().remove(snake.getSegments().size() - 1);
            }
        }

        // Check if game over (all dead or only one alive in multiplayer)
        int aliveCount = 0;
        for (Snake snake : snakes) {
            if (snake.isAlive()) aliveCount++;
        }
        if (aliveCount == 0 || (aliveCount <= 1 && snakes.size() > 1)) {
            state.setGameOver(true);
            room.setGameInProgress(false);
            stopGame(room.getCode());
        }

        GameWebSocket.broadcastState(room.getCode(), state);
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
    }

    public static boolean isGameRunning(String roomCode) {
        return activeGames.containsKey(roomCode);
    }

    public static void resetGame(Room room) {
        synchronized (room) {
            List<Snake> existingPlayers = new ArrayList<>(room.getPlayers());

            // Assign spawn positions
            int startY = GRID_SIZE / 2;
            int spacing = 6;
            for (int i = 0; i < existingPlayers.size(); i++) {
                Snake snake = existingPlayers.get(i);
                int startX = spacing + i * 6;
                
                // Ensure all 3 snake segments are within grid bounds
                // Snake segments are at: (startX, startY), (startX-1, startY), (startX-2, startY)
                // All must have x >= 0, so startX - 2 >= 0 => startX >= 2
                // All must have x <= GRID_SIZE-1, so startX <= GRID_SIZE-1 (already ensured by above constraint)
                // Valid range for startX is [2, GRID_SIZE-1]
                if (startX < 2) startX = 2;
                if (startX > GRID_SIZE - 1) startX = GRID_SIZE - 1;
                
                List<Point> segments = new ArrayList<>();
                segments.add(new Point(startX, startY));
                segments.add(new Point(startX - 1, startY));
                segments.add(new Point(startX - 2, startY));
                snake.setSegments(segments);
                snake.setDirection("RIGHT");
                snake.setNextDirection("RIGHT");
                snake.setAlive(true);
                snake.setReady(false);
                snake.setScore(0);
            }

            room.setPlayers(existingPlayers);
            initGameState(room);
        }
    }
}
