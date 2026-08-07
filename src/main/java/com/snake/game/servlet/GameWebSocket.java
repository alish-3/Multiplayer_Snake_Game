package com.snake.game.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.snake.game.engine.GameEngine;
import com.snake.game.engine.RoomManager;
import com.snake.game.model.GameState;
import com.snake.game.model.Room;
import com.snake.game.model.Snake;
import com.snake.game.util.GameLogger;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

@ServerEndpoint("/api/game/ws/{roomCode}/{playerName}")
public class GameWebSocket {
    private static final Logger logger = Logger.getLogger(GameWebSocket.class.getName());
    private static final Map<String, Set<Session>> roomSessions = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();
    private static final RoomManager roomManager = RoomManager.getInstance();
    
    // Reconnection support
    private static final long RECONNECT_GRACE_PERIOD_MS = 5_000; // 5 seconds before marking disconnected
    private static final long RECONNECT_WINDOW_MS = 30_000; // 30 seconds for full state restoration
    private static final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "reconnect-scheduler");
        t.setDaemon(true);
        return t;
    });
    // Key: "roomCode:playerName", Value: scheduled future for delayed disconnect
    private static final Map<String, java.util.concurrent.ScheduledFuture<?>> pendingDisconnects = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("roomCode") String roomCode,
                       @PathParam("playerName") String playerName) {
        logger.info("[onOpen] START roomCode=" + roomCode + " playerName=" + playerName + " sessionId=" + session.getId());
        session.setMaxIdleTimeout(300000);

        // ===== SESSION CLEANUP AT THE VERY BEGINNING =====
        // Clean up any existing sessions for this player in this room IMMEDIATELY.
        // This handles reconnection, new game rounds, and stale sessions before any other logic.
        // Must run before room lookup so even if room is gone, we clean up WebSocket sessions map.
        logger.info("[onOpen] [CLEANUP] Starting session cleanup for player=" + playerName + " in room=" + roomCode);
        Set<Session> existingSessions = roomSessions.get(roomCode);
        if (existingSessions != null) {
            int removed = 0;
            for (Session s : existingSessions) {
                String existingPlayerName = (String) s.getUserProperties().get("playerName");
                if (playerName.equals(existingPlayerName)) {
                    removed++;
                    logger.info("[onOpen] [CLEANUP] Found stale sessionId=" + s.getId() + " for player=" + playerName);
                }
            }
            if (removed > 0) {
                existingSessions.removeIf(s -> {
                    String existingPlayerName = (String) s.getUserProperties().get("playerName");
                    return playerName.equals(existingPlayerName);
                });
                logger.info("[onOpen] [CLEANUP] Removed " + removed + " existing session(s) for player=" + playerName);
            }
            if (existingSessions.isEmpty()) {
                roomSessions.remove(roomCode);
                logger.info("[onOpen] [CLEANUP] Removed empty room session set for room=" + roomCode);
            }
        } else {
            logger.info("[onOpen] [CLEANUP] No existing sessions found for room=" + roomCode);
        }
        // ===== END SESSION CLEANUP =====

        // Parse query string for reconnectToken
        String reconnectToken = parseReconnectToken(session.getQueryString());
        logger.info("[onOpen] [TOKEN] reconnectToken=" + (reconnectToken != null ? "present (len=" + reconnectToken.length() + ")" : "null"));

        Room room = roomManager.getRoom(roomCode);
        if (room == null) {
            logger.warning("[onOpen] [ROOM] Room not found: " + roomCode);
            try { session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Room not found")); }
            catch (IOException e) { logger.log(Level.WARNING, "Failed to close session for non-existent room: " + roomCode, e); }
            return;
        }

        logger.info("[onOpen] [ROOM] Room found: code=" + roomCode + " gameInProgress=" + room.isGameInProgress() + " playerCount=" + room.getPlayers().size() + " maxPlayers=" + room.getMaxPlayers());

        Snake player = room.getPlayer(playerName);
        boolean isReconnect = false;

        if (player != null) {
            logger.info("[onOpen] [PLAYER] Existing player found: name=" + player.getName() +
                    " alive=" + player.isAlive() +
                    " disconnectedSince=" + player.getDisconnectedSince() +
                    " lastDisconnectTime=" + player.getLastDisconnectTime() +
                    " hasStoredReconnectToken=" + (player.getReconnectToken() != null) +
                    " ready=" + player.isReady() +
                    " score=" + player.getScore() +
                    " segments=" + (player.getSegments() != null ? player.getSegments().size() : 0));

            // Existing player - check for reconnection
            isReconnect = handleReconnection(player, reconnectToken);
            logger.info("[onOpen] [RECONNECT] handleReconnection returned: " + isReconnect);

            if (isReconnect) {
                logger.info("[onOpen] [RECONNECT] Reconnection ALLOWED for player=" + playerName);
                player.clearDisconnected();
                player.touch();
                // Cancel any pending delayed disconnect
                cancelPendingDisconnect(roomCode, playerName);
            } else {
                logger.warning("[onOpen] [RECONNECT] Reconnection REJECTED for player=" + playerName + " - name taken, closing session");
                // Invalid or expired token - treat as new join attempt, but player exists
                // Close the session since name is taken
                try { session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Player name already in use")); }
                catch (IOException e) { logger.log(Level.WARNING, "Failed to close session for existing player: " + playerName, e); }
                return;
            }
        } else {
            logger.info("[onOpen] [PLAYER] New player attempt: playerName=" + playerName + " gameInProgress=" + room.isGameInProgress() + " isFull=" + room.isFull());
            // New player - check if we're in a state where we can accept new players
            if (room.isGameInProgress()) {
                logger.warning("[onOpen] [PLAYER] Game in progress, rejecting new player: " + playerName);
                try { session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Game in progress")); }
                catch (IOException e) { logger.log(Level.WARNING, "Failed to close session for game in progress: " + roomCode, e); }
                return;
            }
            if (room.isFull()) {
                logger.warning("[onOpen] [PLAYER] Room is full, rejecting new player: " + playerName);
                try { session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Room is full")); }
                catch (IOException e) { logger.log(Level.WARNING, "Failed to close session for full room: " + roomCode, e); }
                return;
            }
        }

        session.getUserProperties().put("roomCode", roomCode);
        session.getUserProperties().put("playerName", playerName);
        roomSessions.computeIfAbsent(roomCode, k -> new CopyOnWriteArraySet<>()).add(session);
        logger.info("[onOpen] [SESSION] Session registered for player=" + playerName + " in room=" + roomCode + " totalSessions=" + roomSessions.get(roomCode).size());

        // Log WebSocket connection
        GameLogger.wsConnected(roomCode, playerName);

        sendState(session, room);
        logger.info("[onOpen] END - State sent for player=" + playerName);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            if (json == null || !json.has("action") || json.get("action").isJsonNull()) return;
            String action = json.get("action").getAsString();
            String roomCode = (String) session.getUserProperties().get("roomCode");
            String playerName = (String) session.getUserProperties().get("playerName");

            if ("move".equals(action)) {
                handleMove(roomCode, playerName, json);
            } else if ("boost".equals(action)) {
                handleBoost(roomCode, playerName, json);
            } else if ("ready".equals(action)) {
                handleReady(roomCode, playerName);
            } else if ("ping".equals(action)) {
                handlePing(json, session);
            }
            // Unknown actions are ignored silently
        } catch (Exception e) { logger.log(Level.WARNING, "Error handling WebSocket message: " + message, e); }
    }

    @OnClose
    public void onClose(Session session, @PathParam("roomCode") String roomCode) {
        String playerName = (String) session.getUserProperties().get("playerName");
        if (roomCode != null && playerName != null) {
            Room room = roomManager.getRoom(roomCode);
            if (room != null) {
                Snake player = room.getPlayer(playerName);
                if (player != null) {
                    // Schedule delayed disconnect instead of immediate
                    scheduleDelayedDisconnect(roomCode, playerName, player);
                }
            }
            // Log WebSocket disconnection
            GameLogger.wsDisconnected(roomCode, playerName, "normal");
        }
        Set<Session> sessions = roomSessions.get(roomCode);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                roomSessions.remove(roomCode);
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        logger.log(Level.WARNING, "WebSocket error occurred", error);
        Map<String, Object> userProps = session != null ? session.getUserProperties() : null;
        String roomCode = userProps != null ? (String) userProps.get("roomCode") : null;
        String playerName = userProps != null ? (String) userProps.get("playerName") : null;
        
        // Log WebSocket error
        if (roomCode != null && playerName != null) {
            GameLogger.wsError(roomCode, playerName, error.getMessage());
        }
        
        synchronized (roomSessions) {
            if (roomCode != null && playerName != null) {
                Room room = roomManager.getRoom(roomCode);
                if (room != null) {
                    Snake player = room.getPlayer(playerName);
                    if (player != null) {
                        // Schedule delayed disconnect on error too
                        scheduleDelayedDisconnect(roomCode, playerName, player);
                    }
                }
            }
            if (roomCode != null) {
                Set<Session> sessions = roomSessions.get(roomCode);
                if (sessions != null) sessions.remove(session);
            }
        }
    }

    private void handleMove(String roomCode, String playerName, JsonObject json) {
        if (!json.has("direction") || json.get("direction").isJsonNull()) return;
        String direction = json.get("direction").getAsString();
        Room room = roomManager.getRoom(roomCode);
        if (room == null) return;
        Snake snake = room.getPlayer(playerName);
        if (snake == null || !snake.isAlive()) return;

        if (!isValidDirection(direction)) return;
        if (isReverseDirection(direction, snake.getDirection()) && snake.getSegments().size() > 1) return;

        snake.touch();
        snake.setNextDirection(direction);
    }

    private void handleBoost(String roomCode, String playerName, JsonObject json) {
        boolean boosting = json.has("boost") && !json.get("boost").isJsonNull() && json.get("boost").getAsBoolean();
        Room room = roomManager.getRoom(roomCode);
        if (room == null) return;
        Snake snake = room.getPlayer(playerName);
        if (snake == null || !snake.isAlive()) return;
        snake.touch();
        
        // Log boost state change
        if (boosting != snake.isBoosting()) {
            GameLogger.boostUsed(roomCode, playerName, 0, 0, boosting);
        }
        
        snake.setBoosting(boosting);
    }

    private void handlePing(JsonObject json, Session session) {
        long t = System.currentTimeMillis();
        String roomCode = (String) session.getUserProperties().get("roomCode");
        if (roomCode != null) {
            // A connected client keeps the room alive (prevents the stale-room
            // cleanup from deleting a room while players idle in the lobby)
            Room room = roomManager.getRoom(roomCode);
            if (room != null) room.touch();
        }
        if (json.has("t") && !json.get("t").isJsonNull() && json.get("t").isJsonPrimitive()) {
            try {
                t = json.get("t").getAsLong();
            } catch (NumberFormatException e) {
                // Malformed timestamp; reply with the server time instead
            }
        }
        try {
            session.getBasicRemote().sendText(gson.toJson(Map.of("action", "pong", "t", t)));
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to send pong to session", e);
        }
    }

    private void handleReady(String roomCode, String playerName) {
        Room room = roomManager.getRoom(roomCode);
        if (room == null) {
            logger.warning("[ReadyDebug] room null code=" + roomCode + " player=" + playerName);
            return;
        }
        synchronized (room) {
            // Ignore ready requests while a round is running/counting down so the
            // WS + HTTP double-ready race cannot leave stale ready flags that would
            // auto-start the next round without every player re-readying.
            if (room.isGameInProgress()) {
                logger.warning("[ReadyDebug] BLOCKED gameInProgress=true code=" + roomCode + " player=" + playerName);
                return;
            }
            Snake snake = room.getPlayer(playerName);
            if (snake == null) {
                logger.warning("[ReadyDebug] player null code=" + roomCode + " player=" + playerName);
                return;
            }
            snake.touch();
            snake.setReady(true);

            room.removeDisconnectedPlayers();
            boolean allReady = room.allPlayersReady();
            logger.info("[ReadyDebug] code=" + roomCode + " player=" + playerName + " ready=true allReady=" + allReady
                + " players=" + room.getPlayers().stream().map(Snake::getName).toList()
                + " readyFlags=" + room.getPlayers().stream().map(s -> s.getName() + ":" + s.isReady()).toList());
            if (allReady) {
                GameEngine.resetGame(room);
                GameEngine.startGame(room);
            }
        }
    }

    public static void broadcastState(String roomCode, GameState state) {
        Set<Session> sessions = roomSessions.get(roomCode);
        if (sessions == null || sessions.isEmpty()) return;
        String json = gson.toJson(state);
        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(json);
                } catch (Exception e) { 
                    logger.log(Level.WARNING, "Failed to broadcast state to session", e); 
                }
            }
        }
    }

    /**
     * Returns the total number of active WebSocket connections across all rooms.
     */
    public static int getActiveConnectionCount() {
        int count = 0;
        for (Set<Session> sessions : roomSessions.values()) {
            if (sessions != null) {
                for (Session session : sessions) {
                    if (session.isOpen()) count++;
                }
            }
        }
        return count;
    }

    private static void sendState(Session session, Room room) {
        try {
            GameState state = room.getGameState();
            if (state != null) {
                session.getBasicRemote().sendText(gson.toJson(state));
            } else {
                Map<String, Object> info = new HashMap<>();
                info.put("snakes", room.getPlayers());
                info.put("food", null);
                info.put("gridSize", room.getGridSize() > 0 ? room.getGridSize() : 30);
                info.put("gameOver", false);
                info.put("gameStarted", false);
                info.put("tick", 0);
                info.put("countdown", -1);
                
                // Include the player's reconnectToken for the waiting lobby state
                String playerName = (String) session.getUserProperties().get("playerName");
                if (playerName != null) {
                    Snake player = room.getPlayer(playerName);
                    if (player != null && player.getReconnectToken() != null) {
                        info.put("reconnectToken", player.getReconnectToken());
                    }
                }
                
                session.getBasicRemote().sendText(gson.toJson(info));
            }
        } catch (Exception e) { 
            logger.log(Level.WARNING, "Failed to send state to session", e); 
        }
    }

    private boolean isValidDirection(String dir) {
        return "UP".equals(dir) || "DOWN".equals(dir) || "LEFT".equals(dir) || "RIGHT".equals(dir);
    }

    private boolean isReverseDirection(String dir, String current) {
        return switch (dir) {
            case "UP" -> "DOWN".equals(current);
            case "DOWN" -> "UP".equals(current);
            case "LEFT" -> "RIGHT".equals(current);
            case "RIGHT" -> "LEFT".equals(current);
            default -> false;
        };
    }

    /**
     * Parses the reconnectToken from a query string.
     * @param queryString The query string (e.g., "reconnectToken=abc123")
     * @return The reconnectToken value or null if not present
     */
    private static String parseReconnectToken(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return null;
        }
        String[] params = queryString.split("&");
        for (String param : params) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "reconnectToken".equals(kv[0])) {
                try {
                    return URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name());
                } catch (Exception e) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    // ==================== Reconnection Support ====================

    /**
     * Generates a reconnection token for a player.
     * Token is a simple hash of playerName+roomCode+timestamp.
     * @param roomCode The room code
     * @param playerName The player name
     * @return A reconnection token string
     */
    public static String generateReconnectToken(String roomCode, String playerName) {
        long timestamp = System.currentTimeMillis();
        String raw = playerName + ":" + roomCode + ":" + timestamp;
        return Integer.toHexString(raw.hashCode()) + ":" + timestamp;
    }

    /**
     * Validates a reconnection token and checks if it's within the reconnect window.
     * @param player The player to validate for
     * @param token The reconnect token to validate
     * @return true if token is valid and within 30-second window
     */
    private static boolean validateReconnectToken(Snake player, String token) {
        if (token == null || token.isEmpty() || player.getReconnectToken() == null) {
            return false;
        }
        // Token format: hash:timestamp
        String[] parts = token.split(":");
        if (parts.length != 2) return false;
        
        try {
            long tokenTime = Long.parseLong(parts[1]);
            long now = System.currentTimeMillis();
            
            // Check if token is within the reconnect window (30 seconds)
            if (now - tokenTime > RECONNECT_WINDOW_MS) {
                return false;
            }
            
            // Check if token matches player's stored token
            return token.equals(player.getReconnectToken());
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Handles player reconnection logic.
     * @param player The player attempting to reconnect
     * @param reconnectToken The reconnection token from query param
     * @return true if reconnection successful, false otherwise
     */
    private boolean handleReconnection(Snake player, String reconnectToken) {
        logger.info("[handleReconnection] START player=" + player.getName() + " alive=" + player.isAlive() + " disconnectedSince=" + player.getDisconnectedSince() + " hasToken=" + (reconnectToken != null && !reconnectToken.isEmpty()) + " storedToken=" + (player.getReconnectToken() != null));
        
        // SESSION RECOVERY: If player is alive AND never disconnected, ALWAYS allow reconnection
        // This handles the case where a new round started but player's WebSocket dropped briefly
        // and they're reconnecting without a token (fresh connection)
        if (player.isAlive() && player.getDisconnectedSince() == 0) {
            logger.info("[handleReconnection] SESSION RECOVERY ALLOWED: player=" + player.getName() + " is alive and never disconnected");
            return true;
        }
        
        // If we have a token, validate it
        if (reconnectToken != null && !reconnectToken.isEmpty()) {
            logger.info("[handleReconnection] Token provided, validating...");
            if (validateReconnectToken(player, reconnectToken)) {
                logger.info("[handleReconnection] TOKEN VALID: player=" + player.getName() + " reconnected with valid token");
                return true;
            }
            logger.warning("[handleReconnection] TOKEN INVALID/EXPIRED: player=" + player.getName());
            return false;
        }
        
        // No token provided - check grace period for recently disconnected players
        long timeSinceDisconnect = System.currentTimeMillis() - player.getLastDisconnectTime();
        if (player.getDisconnectedSince() > 0 && timeSinceDisconnect <= RECONNECT_GRACE_PERIOD_MS) {
            // Within grace period - allow reconnect without token
            logger.info("[handleReconnection] GRACE PERIOD ALLOWED: player=" + player.getName() + " reconnected within grace period (no token), timeSinceDisconnect=" + timeSinceDisconnect + "ms");
            return true;
        }
        
        logger.warning("[handleReconnection] REJECTED: player=" + player.getName() + " disconnectedSince=" + player.getDisconnectedSince() + " timeSinceDisconnect=" + timeSinceDisconnect + "ms (gracePeriod=" + RECONNECT_GRACE_PERIOD_MS + "ms)");
        return false;
    }

    /**
     * Schedules a delayed disconnect for a player.
     * This gives a 5-second grace period before marking the player as disconnected.
     * @param roomCode The room code
     * @param playerName The player name
     * @param player The player object
     */
    private static void scheduleDelayedDisconnect(String roomCode, String playerName, Snake player) {
        String key = roomCode + ":" + playerName;
        
        // Cancel any existing pending disconnect for this player
        cancelPendingDisconnect(roomCode, playerName);
        
        // Record the disconnect time for grace period checks
        player.setLastDisconnectTime(System.currentTimeMillis());
        
        // Generate a new reconnect token for the player
        String token = generateReconnectToken(roomCode, playerName);
        player.setReconnectToken(token);
        
        java.util.concurrent.ScheduledFuture<?> future = reconnectScheduler.schedule(() -> {
            // This runs after the grace period
            Room room = roomManager.getRoom(roomCode);
            if (room != null) {
                Snake p = room.getPlayer(playerName);
                if (p != null && p.getDisconnectedSince() == 0) {
                    // Only mark disconnected if not already reconnected
                    p.markDisconnected();
                    logger.info("Player " + playerName + " marked as disconnected after grace period in room " + roomCode);
                }
            }
            // Clean up the pending disconnect entry
            pendingDisconnects.remove(key);
        }, RECONNECT_GRACE_PERIOD_MS, TimeUnit.MILLISECONDS);
        
        pendingDisconnects.put(key, future);
        logger.fine("Scheduled delayed disconnect for " + playerName + " in room " + roomCode + " (grace period: " + RECONNECT_GRACE_PERIOD_MS + "ms)");
    }

    /**
     * Cancels a pending delayed disconnect for a player.
     * Called when player reconnects within the grace period.
     * @param roomCode The room code
     * @param playerName The player name
     */
    private static void cancelPendingDisconnect(String roomCode, String playerName) {
        String key = roomCode + ":" + playerName;
        java.util.concurrent.ScheduledFuture<?> future = pendingDisconnects.remove(key);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            logger.fine("Cancelled pending disconnect for " + playerName + " in room " + roomCode);
        }
    }

    /**
     * Clears all WebSocket sessions for a room.
     * Called when a new game round starts to ensure clean state.
     * @param roomCode The room code
     */
    public static void clearRoomSessions(String roomCode) {
        Set<Session> sessions = roomSessions.remove(roomCode);
        if (sessions != null) {
            for (Session session : sessions) {
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "New game round started"));
                } catch (IOException e) {
                    logger.log(Level.FINE, "Failed to close session during cleanup", e);
                }
            }
        }
    }
}