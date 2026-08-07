package com.snake.game.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.snake.game.engine.RoomManager;
import com.snake.game.model.GameState;
import com.snake.game.model.Room;
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

/**
 * WebSocket endpoint for spectators to watch games in real-time.
 * Spectators are read-only and do not count toward the room's max player limit.
 * Endpoint: /api/game/ws/{roomCode}/spectator/{spectatorName}
 */
@ServerEndpoint("/api/game/ws/{roomCode}/spectator/{spectatorName}")
public class SpectatorWebSocket {
    private static final Logger logger = Logger.getLogger(SpectatorWebSocket.class.getName());
    private static final Map<String, Set<Session>> spectatorSessions = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();
    private static final RoomManager roomManager = RoomManager.getInstance();
    
    // Reconnection support for spectators
    private static final long RECONNECT_GRACE_PERIOD_MS = 5_000; // 5 seconds before removing spectator
    private static final long RECONNECT_WINDOW_MS = 30_000; // 30 seconds for full reconnection
    private static final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "spectator-reconnect-scheduler");
        t.setDaemon(true);
        return t;
    });
    // Key: "roomCode:spectatorName", Value: scheduled future for delayed removal
    private static final Map<String, java.util.concurrent.ScheduledFuture<?>> pendingSpectatorRemovals = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("roomCode") String roomCode,
                       @PathParam("spectatorName") String spectatorName) {
        session.setMaxIdleTimeout(300000);
        
        // Parse query string for reconnectToken
        String reconnectToken = parseReconnectToken(session.getQueryString());
        
        Room room = roomManager.getRoom(roomCode);
        if (room == null) {
            try { 
                session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Room not found")); 
            } catch (IOException e) { 
                logger.log(Level.WARNING, "Failed to close session for non-existent room: " + roomCode, e); 
            }
            return;
        }

        // Check if name is already in use (player or spectator)
        if (room.getPlayer(spectatorName) != null) {
            try { 
                session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Name already taken by a player")); 
            } catch (IOException e) { 
                logger.log(Level.WARNING, "Failed to close session for duplicate player name: " + spectatorName, e); 
            }
            return;
        }
        
        boolean isReconnect = false;
        if (room.hasSpectator(spectatorName)) {
            // Existing spectator - check for reconnection
            isReconnect = handleSpectatorReconnection(room, spectatorName, reconnectToken);
            if (!isReconnect) {
                try { 
                    session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Name already taken by another spectator")); 
                } catch (IOException e) { 
                    logger.log(Level.WARNING, "Failed to close session for duplicate spectator name: " + spectatorName, e); 
                }
                return;
            }
            // Cancel any pending delayed removal
            cancelPendingSpectatorRemoval(roomCode, spectatorName);
        } else {
            // New spectator
        }

        // Add spectator to room
        boolean added = roomManager.addSpectatorToRoom(roomCode, spectatorName);
        if (!added) {
            try { 
                session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Failed to add spectator to room")); 
            } catch (IOException e) { 
                logger.log(Level.WARNING, "Failed to close session after addSpectatorToRoom failed: " + spectatorName, e); 
            }
            return;
        }

        // Store roomCode and spectatorName in session user properties
        session.getUserProperties().put("roomCode", roomCode);
        session.getUserProperties().put("spectatorName", spectatorName);

        // Add session to spectator sessions map
        spectatorSessions.computeIfAbsent(roomCode, k -> new CopyOnWriteArraySet<>()).add(session);

        // Log WebSocket connection
        GameLogger.wsConnected(roomCode, spectatorName);

        // Send current game state
        sendState(session, room);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            if (json == null || !json.has("action") || json.get("action").isJsonNull()) return;
            String action = json.get("action").getAsString();
            
            String roomCode = (String) session.getUserProperties().get("roomCode");
            String spectatorName = (String) session.getUserProperties().get("spectatorName");

            // Spectators are read-only: only handle ping for keepalive
            if ("ping".equals(action)) {
                handlePing(json, session, roomCode);
            }
            // Ignore move, ready, boost actions silently
        } catch (Exception e) { 
            logger.log(Level.WARNING, "Error handling spectator WebSocket message: " + message, e); 
        }
    }

    @OnClose
    public void onClose(Session session, @PathParam("roomCode") String roomCode) {
        String spectatorName = (String) session.getUserProperties().get("spectatorName");
        if (roomCode != null && spectatorName != null) {
            // Schedule delayed removal instead of immediate
            scheduleDelayedSpectatorRemoval(roomCode, spectatorName);
            
            // Log WebSocket disconnection
            GameLogger.wsDisconnected(roomCode, spectatorName, "normal");
        }
        
        // Remove session from spectator sessions map
        Set<Session> sessions = spectatorSessions.get(roomCode);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                spectatorSessions.remove(roomCode);
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        logger.log(Level.WARNING, "Spectator WebSocket error occurred", error);
        Map<String, Object> userProps = session != null ? session.getUserProperties() : null;
        String roomCode = userProps != null ? (String) userProps.get("roomCode") : null;
        String spectatorName = userProps != null ? (String) userProps.get("spectatorName") : null;
        
        // Log WebSocket error
        if (roomCode != null && spectatorName != null) {
            GameLogger.wsError(roomCode, spectatorName, error.getMessage());
        }
        
        // Clean up session and schedule delayed spectator removal
        if (roomCode != null && spectatorName != null) {
            scheduleDelayedSpectatorRemoval(roomCode, spectatorName);
        }
        if (roomCode != null) {
            Set<Session> sessions = spectatorSessions.get(roomCode);
            if (sessions != null) sessions.remove(session);
        }
    }

    private void handlePing(JsonObject json, Session session, String roomCode) {
        long t = System.currentTimeMillis();
        if (roomCode != null) {
            // A connected spectator keeps the room alive (prevents stale-room cleanup)
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
            logger.log(Level.FINE, "Failed to send pong to spectator session", e);
        }
    }

    /**
     * Broadcasts the game state to all spectator sessions in a room.
     * @param roomCode The room code
     * @param state The GameState to broadcast
     */
    public static void broadcastToSpectators(String roomCode, GameState state) {
        Set<Session> sessions = spectatorSessions.get(roomCode);
        if (sessions == null || sessions.isEmpty()) return;
        String json = gson.toJson(state);
        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(json);
                } catch (Exception e) { 
                    logger.log(Level.WARNING, "Failed to broadcast state to spectator session", e); 
                }
            }
        }
    }

    /**
     * Broadcasts the game state to both player sessions and spectator sessions.
     * Convenience method to send to all connected clients.
     * @param roomCode The room code
     * @param state The GameState to broadcast
     */
    public static void broadcastToAll(String roomCode, GameState state) {
        GameWebSocket.broadcastState(roomCode, state);
        broadcastToSpectators(roomCode, state);
    }

    /**
     * Returns the total number of active spectator WebSocket connections across all rooms.
     */
    public static int getActiveSpectatorCount() {
        int count = 0;
        for (Set<Session> sessions : spectatorSessions.values()) {
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
                // Room exists but game not initialized yet (lobby state)
                java.util.Map<String, Object> info = new java.util.HashMap<>();
                info.put("snakes", room.getPlayers());
                info.put("food", null);
                info.put("gridSize", room.getGridSize() > 0 ? room.getGridSize() : 30);
                info.put("gameOver", false);
                info.put("gameStarted", false);
                info.put("tick", 0);
                info.put("countdown", -1);
                session.getBasicRemote().sendText(gson.toJson(info));
            }
        } catch (Exception e) { 
            logger.log(Level.WARNING, "Failed to send state to spectator session", e); 
        }
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
     * Generates a reconnection token for a spectator.
     * Token is a simple hash of spectatorName+roomCode+timestamp.
     * @param roomCode The room code
     * @param spectatorName The spectator name
     * @return A reconnection token string
     */
    public static String generateReconnectToken(String roomCode, String spectatorName) {
        long timestamp = System.currentTimeMillis();
        String raw = spectatorName + ":" + roomCode + ":" + timestamp;
        return Integer.toHexString(raw.hashCode()) + ":" + timestamp;
    }

    /**
     * Validates a reconnection token for a spectator.
     * Since spectators don't have a persistent object, we validate based on the token format and timestamp.
     * @param room The room
     * @param spectatorName The spectator name
     * @param token The reconnect token to validate
     * @return true if token is valid and within 30-second window
     */
    private static boolean validateSpectatorReconnectToken(Room room, String spectatorName, String token) {
        if (token == null || token.isEmpty()) {
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
            
            // For spectators, we can't store the token on the spectator object
            // So we validate by regenerating what the token should be
            // The hash is based on name:roomCode:timestamp, so we verify the hash matches
            String expectedRaw = spectatorName + ":" + room.getCode() + ":" + tokenTime;
            String expectedHash = Integer.toHexString(expectedRaw.hashCode());
            return parts[0].equals(expectedHash);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Handles spectator reconnection logic.
     * @param room The room
     * @param spectatorName The spectator name
     * @param reconnectToken The reconnection token from query param
     * @return true if reconnection successful, false otherwise
     */
    private boolean handleSpectatorReconnection(Room room, String spectatorName, String reconnectToken) {
        if (reconnectToken == null || reconnectToken.isEmpty()) {
            // No token provided - for spectators we require a token since they have no persistent state
            logger.warning("Spectator " + spectatorName + " reconnection failed: no token provided");
            return false;
        }
        
        // Validate the token
        if (validateSpectatorReconnectToken(room, spectatorName, reconnectToken)) {
            logger.info("Spectator " + spectatorName + " reconnected with valid token");
            return true;
        }
        
        logger.warning("Spectator " + spectatorName + " reconnection failed: invalid or expired token");
        return false;
    }

    /**
     * Schedules a delayed removal for a spectator.
     * This gives a 5-second grace period before removing the spectator from the room.
     * @param roomCode The room code
     * @param spectatorName The spectator name
     */
    private static void scheduleDelayedSpectatorRemoval(String roomCode, String spectatorName) {
        String key = roomCode + ":" + spectatorName;
        
        // Cancel any existing pending removal for this spectator
        cancelPendingSpectatorRemoval(roomCode, spectatorName);
        
        java.util.concurrent.ScheduledFuture<?> future = reconnectScheduler.schedule(() -> {
            // This runs after the grace period
            Room room = roomManager.getRoom(roomCode);
            if (room != null && room.hasSpectator(spectatorName)) {
                // Only remove if not already reconnected
                roomManager.removeSpectatorFromRoom(roomCode, spectatorName, "normal");
                logger.info("Spectator " + spectatorName + " removed from room " + roomCode + " after grace period");
            }
            // Clean up the pending removal entry
            pendingSpectatorRemovals.remove(key);
        }, RECONNECT_GRACE_PERIOD_MS, TimeUnit.MILLISECONDS);
        
        pendingSpectatorRemovals.put(key, future);
        logger.fine("Scheduled delayed removal for spectator " + spectatorName + " in room " + roomCode + " (grace period: " + RECONNECT_GRACE_PERIOD_MS + "ms)");
    }

    /**
     * Cancels a pending delayed removal for a spectator.
     * Called when spectator reconnects within the grace period.
     * @param roomCode The room code
     * @param spectatorName The spectator name
     */
    private static void cancelPendingSpectatorRemoval(String roomCode, String spectatorName) {
        String key = roomCode + ":" + spectatorName;
        java.util.concurrent.ScheduledFuture<?> future = pendingSpectatorRemovals.remove(key);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            logger.fine("Cancelled pending removal for spectator " + spectatorName + " in room " + roomCode);
        }
    }
}