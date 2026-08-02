package com.snake.game.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.snake.game.engine.GameEngine;
import com.snake.game.engine.RoomManager;
import com.snake.game.model.GameState;
import com.snake.game.model.Room;
import com.snake.game.model.Snake;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;
import java.util.logging.Level;

@ServerEndpoint("/api/game/ws/{roomCode}/{playerName}")
public class GameWebSocket {
    private static final Logger logger = Logger.getLogger(GameWebSocket.class.getName());
    private static final Map<String, Set<Session>> roomSessions = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();
    private static final RoomManager roomManager = RoomManager.getInstance();

    @OnOpen
    public void onOpen(Session session, @PathParam("roomCode") String roomCode,
                       @PathParam("playerName") String playerName) {
        session.setMaxIdleTimeout(300000);
        Room room = roomManager.getRoom(roomCode);
        if (room == null) {
            try { session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Room not found")); }
            catch (IOException e) { logger.log(Level.WARNING, "Failed to close session for non-existent room: " + roomCode, e); }
            return;
        }
        Snake player = room.getPlayer(playerName);
        if (player == null) {
            try { session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Player not found")); }
            catch (IOException e) { logger.log(Level.WARNING, "Failed to close session for non-existent player: " + playerName, e); }
            return;
        }
        player.clearDisconnected();
        player.touch();
        session.getUserProperties().put("roomCode", roomCode);
        session.getUserProperties().put("playerName", playerName);
        roomSessions.computeIfAbsent(roomCode, k -> new CopyOnWriteArraySet<>()).add(session);

        sendState(session, room);
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
                if (player != null) player.markDisconnected();
            }
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
        
        synchronized (roomSessions) {
            if (roomCode != null && playerName != null) {
                Room room = roomManager.getRoom(roomCode);
                if (room != null) {
                    Snake player = room.getPlayer(playerName);
                    if (player != null) player.markDisconnected();
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

    private static void sendState(Session session, Room room) {
        try {
            GameState state = room.getGameState();
            if (state != null) {
                session.getBasicRemote().sendText(gson.toJson(state));
            } else {
                Map<String, Object> info = new HashMap<>();
                info.put("snakes", room.getPlayers());
                info.put("food", null);
                info.put("gridSize", 30);
                info.put("gameOver", false);
                info.put("gameStarted", false);
                info.put("tick", 0);
                info.put("countdown", -1);
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
}