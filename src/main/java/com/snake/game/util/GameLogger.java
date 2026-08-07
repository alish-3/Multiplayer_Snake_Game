package com.snake.game.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.logging.*;

/**
 * Structured logging utility for game events.
 * Outputs JSON lines format (machine-parseable) to console and optional file.
 * Each log entry includes: timestamp (ISO-8601), event type, roomCode, playerName (if applicable), and event-specific data.
 */
public class GameLogger {
    private static final Logger logger = Logger.getLogger(GameLogger.class.getName());
    private static final Gson gson = new Gson();
    private static PrintStream fileOut = null;
    private static final String LOG_FILE_PATH = System.getenv().getOrDefault("SNAKE_LOG_FILE", "logs/game-events.log");
    private static final Level MIN_LEVEL = Level.INFO;
    private static boolean initialized = false;

    private GameLogger() {
        // Utility class
    }

    /**
     * Initialize the logger with console and optional file output.
     * Call once at application startup.
     */
    public static synchronized void init() {
        if (initialized) return;

        // Configure root logger to suppress default formatting
        Logger rootLogger = Logger.getLogger("");
        for (Handler h : rootLogger.getHandlers()) {
            rootLogger.removeHandler(h);
        }

        // Console handler with simple formatter (just the message)
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(MIN_LEVEL);
        consoleHandler.setFormatter(new SimpleFormatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + "\n";
            }
        });
        rootLogger.addHandler(consoleHandler);
        rootLogger.setLevel(MIN_LEVEL);

        // Optional file handler
        try {
            Path logPath = Paths.get(LOG_FILE_PATH);
            Files.createDirectories(logPath.getParent());
            fileOut = new PrintStream(Files.newOutputStream(
                    logPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND),
                    true); // auto-flush
            logger.info("[GameLogger] File logging enabled: " + logPath.toAbsolutePath());
        } catch (IOException e) {
            logger.warning("[GameLogger] Could not open log file " + LOG_FILE_PATH + ": " + e.getMessage());
        }

        initialized = true;
    }

    /**
     * Write a structured log entry as a JSON line.
     * @param eventType The event type (e.g., "roomCreated", "playerDied")
     * @param roomCode The room code (or "N/A" if not applicable)
     * @param playerName The player name (or null if not applicable)
     * @param data Additional event-specific data as a JsonObject (can be null)
     */
    private static void log(String eventType, String roomCode, String playerName, JsonObject data) {
        if (!initialized) {
            init();
        }

        JsonObject entry = new JsonObject();
        entry.addProperty("timestamp", Instant.now().toString()); // ISO-8601
        entry.addProperty("eventType", eventType);
        entry.addProperty("roomCode", roomCode != null ? roomCode : "N/A");
        if (playerName != null) {
            entry.addProperty("playerName", playerName);
        }
        if (data != null) {
            entry.add("data", data);
        }

        String jsonLine = gson.toJson(entry);

        // Console output
        System.out.println(jsonLine);

        // File output (if enabled)
        if (fileOut != null) {
            fileOut.println(jsonLine);
        }
    }

    // ==================== Room Lifecycle Events ====================

    public static void roomCreated(String roomCode, String initialPlayerName) {
        JsonObject data = new JsonObject();
        if (initialPlayerName != null) {
            data.addProperty("initialPlayer", initialPlayerName);
        }
        log("roomCreated", roomCode, initialPlayerName, data);
    }

    public static void roomDestroyed(String roomCode, String reason, int playerCount) {
        JsonObject data = new JsonObject();
        data.addProperty("reason", reason);
        data.addProperty("playerCount", playerCount);
        log("roomDestroyed", roomCode, null, data);
    }

    public static void playerJoined(String roomCode, String playerName, int playerCount, int maxPlayers) {
        JsonObject data = new JsonObject();
        data.addProperty("playerCount", playerCount);
        data.addProperty("maxPlayers", maxPlayers);
        log("playerJoined", roomCode, playerName, data);
    }

    public static void playerLeft(String roomCode, String playerName, int playerCount, String reason) {
        JsonObject data = new JsonObject();
        data.addProperty("playerCount", playerCount);
        data.addProperty("reason", reason);
        log("playerLeft", roomCode, playerName, data);
    }

    // ==================== Spectator Events ====================

    public static void spectatorJoined(String roomCode, String spectatorName, int spectatorCount, int playerCount) {
        JsonObject data = new JsonObject();
        data.addProperty("spectatorCount", spectatorCount);
        data.addProperty("playerCount", playerCount);
        log("spectatorJoined", roomCode, spectatorName, data);
    }

    public static void spectatorLeft(String roomCode, String spectatorName, int spectatorCount, int playerCount, String reason) {
        JsonObject data = new JsonObject();
        data.addProperty("spectatorCount", spectatorCount);
        data.addProperty("playerCount", playerCount);
        data.addProperty("reason", reason);
        log("spectatorLeft", roomCode, spectatorName, data);
    }

    // ==================== Game Lifecycle Events ====================

    public static void gameStarted(String roomCode, int playerCount, String[] playerNames) {
        JsonObject data = new JsonObject();
        data.addProperty("playerCount", playerCount);
        data.add("players", gson.toJsonTree(playerNames));
        log("gameStarted", roomCode, null, data);
    }

    public static void gameEnded(String roomCode, String winnerName, long durationMs, int tick, String[] finalScores) {
        JsonObject data = new JsonObject();
        data.addProperty("winner", winnerName);
        data.addProperty("durationMs", durationMs);
        data.addProperty("tick", tick);
        data.add("finalScores", gson.toJsonTree(finalScores));
        log("gameEnded", roomCode, winnerName, data);
    }

    public static void gameReset(String roomCode, int playerCount) {
        JsonObject data = new JsonObject();
        data.addProperty("playerCount", playerCount);
        log("gameReset", roomCode, null, data);
    }

    // ==================== Player Events ====================

    public static void playerDied(String roomCode, String playerName, String cause, int tick, int score, int segmentCount) {
        JsonObject data = new JsonObject();
        data.addProperty("cause", cause);
        data.addProperty("tick", tick);
        data.addProperty("score", score);
        data.addProperty("segmentCount", segmentCount);
        log("playerDied", roomCode, playerName, data);
    }

    public static void snakeMoved(String roomCode, String playerName, int tick, int x, int y, String direction, boolean boosting) {
        JsonObject data = new JsonObject();
        data.addProperty("tick", tick);
        data.addProperty("x", x);
        data.addProperty("y", y);
        data.addProperty("direction", direction);
        data.addProperty("boosting", boosting);
        log("snakeMoved", roomCode, playerName, data);
    }

    // ==================== Food Events ====================

    public static void foodSpawned(String roomCode, String type, int x, int y, int value, String source) {
        JsonObject data = new JsonObject();
        data.addProperty("type", type);
        data.addProperty("x", x);
        data.addProperty("y", y);
        data.addProperty("value", value);
        data.addProperty("source", source);
        log("foodSpawned", roomCode, null, data);
    }

    public static void foodConsumed(String roomCode, String playerName, String foodType, int value, int x, int y, int newScore) {
        JsonObject data = new JsonObject();
        data.addProperty("foodType", foodType);
        data.addProperty("value", value);
        data.addProperty("x", x);
        data.addProperty("y", y);
        data.addProperty("newScore", newScore);
        log("foodConsumed", roomCode, playerName, data);
    }

    // ==================== Boost Events ====================

    public static void boostUsed(String roomCode, String playerName, int coinsSpent, int remainingCoins, boolean activating) {
        JsonObject data = new JsonObject();
        data.addProperty("coinsSpent", coinsSpent);
        data.addProperty("remainingCoins", remainingCoins);
        data.addProperty("activating", activating);
        log("boostUsed", roomCode, playerName, data);
    }

    public static void boostShedSegment(String roomCode, String playerName, int x, int y) {
        JsonObject data = new JsonObject();
        data.addProperty("x", x);
        data.addProperty("y", y);
        log("boostShedSegment", roomCode, playerName, data);
    }

    public static void scoreMilestoneReached(String roomCode, String playerName, int milestone, int coinsAwarded, int totalCoins) {
        JsonObject data = new JsonObject();
        data.addProperty("milestone", milestone);
        data.addProperty("coinsAwarded", coinsAwarded);
        data.addProperty("totalCoins", totalCoins);
        log("scoreMilestoneReached", roomCode, playerName, data);
    }

    // ==================== Collision Events ====================

    public static void collisionWall(String roomCode, String playerName, int tick, int x, int y) {
        JsonObject data = new JsonObject();
        data.addProperty("tick", tick);
        data.addProperty("x", x);
        data.addProperty("y", y);
        log("collisionWall", roomCode, playerName, data);
    }

    public static void collisionHeadOn(String roomCode, String playerName, int tick, int x, int y, String[] otherPlayers) {
        JsonObject data = new JsonObject();
        data.addProperty("tick", tick);
        data.addProperty("x", x);
        data.addProperty("y", y);
        data.add("otherPlayers", gson.toJsonTree(otherPlayers));
        log("collisionHeadOn", roomCode, playerName, data);
    }

    public static void collisionHeadBody(String roomCode, String playerName, int tick, int x, int y, String victimName) {
        JsonObject data = new JsonObject();
        data.addProperty("tick", tick);
        data.addProperty("x", x);
        data.addProperty("y", y);
        data.addProperty("victim", victimName);
        log("collisionHeadBody", roomCode, playerName, data);
    }

    // ==================== WebSocket Events ====================

    public static void wsConnected(String roomCode, String playerName) {
        JsonObject data = new JsonObject();
        log("wsConnected", roomCode, playerName, data);
    }

    public static void wsDisconnected(String roomCode, String playerName, String reason) {
        JsonObject data = new JsonObject();
        data.addProperty("reason", reason);
        log("wsDisconnected", roomCode, playerName, data);
    }

    public static void wsReconnectAttempt(String roomCode, String playerName) {
        JsonObject data = new JsonObject();
        log("wsReconnectAttempt", roomCode, playerName, data);
    }

    public static void wsError(String roomCode, String playerName, String error) {
        JsonObject data = new JsonObject();
        data.addProperty("error", error);
        log("wsError", roomCode, playerName, data);
    }

    /**
     * Close the file output stream if open.
     */
    public static void close() {
        if (fileOut != null) {
            fileOut.close();
            fileOut = null;
        }
    }

    /**
     * Shutdown the logger - alias for close() for clearer lifecycle semantics.
     * Call at application shutdown.
     */
    public static void shutdown() {
        close();
        logger.info("[GameLogger] Shutdown complete");
    }
}