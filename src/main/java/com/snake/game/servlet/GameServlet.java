package com.snake.game.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.snake.game.engine.GameEngine;
import com.snake.game.engine.RoomManager;
import com.snake.game.model.GameState;
import com.snake.game.model.Room;
import com.snake.game.model.Snake;
import com.snake.game.util.BotManager;
import com.snake.game.util.RateLimiter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

@WebServlet("/api/game")
public class GameServlet extends HttpServlet {
    private final Gson gson = new Gson();
    private final RoomManager roomManager = RoomManager.getInstance();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String action = req.getParameter("action");
        if ("state".equals(action)) {
            String roomCode = req.getParameter("room");
            Room room = roomManager.getRoom(roomCode);

            if (room == null) {
                resp.getWriter().write(gson.toJson(Map.of("error", "Room not found")));
                return;
            }

            GameState state = room.getGameState();
            if (state == null) {
                Map<String, Object> waiting = new HashMap<>();
                waiting.put("snakes", room.getPlayers());
                waiting.put("food", null);
                waiting.put("gridSize", room.getGridSize() > 0 ? room.getGridSize() : 30);
                waiting.put("gameOver", false);
                waiting.put("gameStarted", false);
                waiting.put("tick", 0);
                resp.getWriter().write(gson.toJson(waiting));
                return;
            }

            resp.getWriter().write(gson.toJson(state));
        } else {
            resp.getWriter().write(gson.toJson(Map.of("error", "Unknown action")));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Extract client identifier for rate limiting (player name if available, otherwise IP)
        String clientKey = getClientKey(req, null);

        BufferedReader reader = req.getReader();
        JsonObject json = gson.fromJson(reader, JsonObject.class);
        if (json == null || !json.has("action") || json.get("action").isJsonNull()) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Missing action")));
            return;
        }
        String action = json.get("action").getAsString();
        String roomCode = json.has("roomCode") && !json.get("roomCode").isJsonNull() ? json.get("roomCode").getAsString() : null;
        String playerName = json.has("playerName") && !json.get("playerName").isJsonNull() ? json.get("playerName").getAsString() : null;

        // Update client key with player name if available for per-player limiting
        if (playerName != null) {
            clientKey = "player:" + playerName;
        }

        // Apply rate limiting based on action
        if (!checkRateLimit(req, resp, action, clientKey)) {
            return; // Rate limited response already sent
        }

        if (roomCode == null || !roomCode.matches("^[A-Z2-9]{6}$")) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Invalid room code")));
            return;
        }

        Room room = roomManager.getRoom(roomCode);
        if (room == null) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Room not found")));
            return;
        }
        room.touch();

        switch (action) {
            case "join":
                handleJoin(room, playerName, resp);
                break;
            case "move":
                handleMove(json, room, resp);
                break;
            case "boost":
                handleBoost(json, room, resp);
                break;
            case "ready":
                handleReady(room, playerName, resp);
                break;
            case "leave":
                handleLeave(room, playerName, resp);
                break;
            default:
                resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Unknown action")));
        }
    }

    /**
     * Checks rate limit for the given action and client key.
     * Returns true if allowed, false if rate limited (response already sent).
     */
    private boolean checkRateLimit(HttpServletRequest req, HttpServletResponse resp, String action, String clientKey) throws IOException {
        RateLimiter limiter = RateLimiter.getInstance();
        boolean allowed;
        long retryAfterMs;
        String errorMessage;

        switch (action) {
            case "move":
                // 10 requests per 100ms per player
                allowed = limiter.tryConsume(clientKey, 10, 100);
                retryAfterMs = limiter.getRetryAfterMs(clientKey, 10, 100);
                errorMessage = "Rate limit exceeded for move action (max 10 per 100ms)";
                break;
            case "ready":
                // 5 requests per second per player
                allowed = limiter.tryConsume(clientKey, 5, 1000);
                retryAfterMs = limiter.getRetryAfterMs(clientKey, 5, 1000);
                errorMessage = "Rate limit exceeded for ready action (max 5 per second)";
                break;
            case "boost":
                // 20 requests per 100ms per player
                allowed = limiter.tryConsume(clientKey, 20, 100);
                retryAfterMs = limiter.getRetryAfterMs(clientKey, 20, 100);
                errorMessage = "Rate limit exceeded for boost action (max 20 per 100ms)";
                break;
            default:
                return true; // No rate limiting for other actions
        }

        if (!allowed) {
            resp.setStatus(429); // Too Many Requests
            resp.setHeader("Retry-After", String.valueOf((retryAfterMs + 999) / 1000)); // Seconds, rounded up
            resp.getWriter().write(gson.toJson(Map.of(
                "success", false,
                "error", errorMessage,
                "retryAfterMs", retryAfterMs
            )));
            return false;
        }
        return true;
    }

    /**
     * Gets a client identifier for rate limiting.
     * Uses player name if provided, otherwise extracts IP from request.
     */
    private String getClientKey(HttpServletRequest req, String playerName) {
        if (playerName != null && !playerName.isEmpty()) {
            return "player:" + playerName;
        }
        return "ip:" + getClientIp(req);
    }

    /**
     * Extracts client IP from request, checking proxy headers first.
     */
    private String getClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            ip = ip.split(",")[0].trim();
        } else {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = req.getRemoteAddr();
        }
        return ip;
    }

    private void handleJoin(Room room, String playerName, HttpServletResponse resp) throws IOException {
        synchronized (room) {
            Snake snake = room.getPlayer(playerName);
            if (snake == null) {
                resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Player not found in room")));
                return;
            }
            snake.touch();

            if (room.getGameState() != null) {
                GameWebSocket.broadcastState(room.getCode(), room.getGameState());
            }
        }
        resp.getWriter().write(gson.toJson(Map.of("success", true)));
    }

    private void handleMove(JsonObject json, Room room, HttpServletResponse resp) throws IOException {
        String playerName = json.has("playerName") && !json.get("playerName").isJsonNull() ? json.get("playerName").getAsString() : null;
        String direction = json.has("direction") && !json.get("direction").isJsonNull() ? json.get("direction").getAsString() : null;
        if (playerName == null || direction == null) {
            resp.getWriter().write(gson.toJson(Map.of("success", false)));
            return;
        }

        Snake snake = room.getPlayer(playerName);

        if (snake == null) {
            resp.getWriter().write(gson.toJson(Map.of("success", false)));
            return;
        }
        snake.touch();

        if (!snake.isAlive()) {
            resp.getWriter().write(gson.toJson(Map.of("success", false)));
            return;
        }

        String[] valid = {"UP", "DOWN", "LEFT", "RIGHT"};
        boolean validDir = false;
        for (String d : valid) {
            if (d.equals(direction)) { validDir = true; break; }
        }
        if (!validDir) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Invalid direction")));
            return;
        }

        String opposite = switch (direction) {
            case "UP" -> "DOWN";
            case "DOWN" -> "UP";
            case "LEFT" -> "RIGHT";
            case "RIGHT" -> "LEFT";
            default -> "";
        };
        if (snake.getDirection().equals(opposite) && snake.getSegments().size() > 1) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Can't reverse")));
            return;
        }

        snake.setNextDirection(direction);
        resp.getWriter().write(gson.toJson(Map.of("success", true)));
    }

    private void handleBoost(JsonObject json, Room room, HttpServletResponse resp) throws IOException {
        String playerName = json.has("playerName") && !json.get("playerName").isJsonNull() ? json.get("playerName").getAsString() : null;
        boolean boosting = json.has("boost") && !json.get("boost").isJsonNull() && json.get("boost").getAsBoolean();
        if (playerName == null) {
            resp.getWriter().write(gson.toJson(Map.of("success", false)));
            return;
        }
        Snake snake = room.getPlayer(playerName);
        if (snake == null) {
            resp.getWriter().write(gson.toJson(Map.of("success", false)));
            return;
        }
        snake.touch();
        if (snake.isAlive()) {
            snake.setBoosting(boosting);
        }
        resp.getWriter().write(gson.toJson(Map.of("success", true)));
    }

    private void handleReady(Room room, String playerName, HttpServletResponse resp) throws IOException {
        synchronized (room) {
            // Ignore ready requests while a round is running/counting down (same
            // rationale as GameWebSocket.handleReady: keeps ready flags clean).
            if (room.isGameInProgress()) {
                System.out.println("[ReadyDebug] HTTP BLOCKED gameInProgress=true code=" + room.getCode() + " player=" + playerName);
                resp.getWriter().write(gson.toJson(Map.of("success", true)));
                return;
            }
            Snake snake = room.getPlayer(playerName);
            if (snake == null) {
                resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Player not found")));
                return;
            }
            snake.touch();
            snake.setReady(true);

            room.removeDisconnectedPlayers();
            boolean allReady = room.allPlayersReady();
            System.out.println("[ReadyDebug] HTTP code=" + room.getCode() + " player=" + playerName + " ready=true allReady=" + allReady);
            if (allReady) {
                GameEngine.resetGame(room);
                GameEngine.startGame(room);
            }
        }

        resp.getWriter().write(gson.toJson(Map.of("success", true)));
    }

    private void handleLeave(Room room, String playerName, HttpServletResponse resp) throws IOException {
        synchronized (room) {
            // Use RoomManager method to remove player and log
            roomManager.removePlayerFromRoom(room.getCode(), playerName, "left");
            // If only bots remain, no humans are watching - tear the room down
            if (BotManager.hasOnlyBots(room)) {
                BotManager.removeBots(room);
                GameEngine.stopGame(room.getCode());
                roomManager.removeRoom(room.getCode());
            }
        }

        Room updatedRoom = roomManager.getRoom(room.getCode());
        if (updatedRoom != null && updatedRoom.getPlayerCount() == 0) {
            GameEngine.stopGame(room.getCode());
            roomManager.removeRoom(room.getCode());
        }

        resp.getWriter().write(gson.toJson(Map.of("success", true)));
    }
}
