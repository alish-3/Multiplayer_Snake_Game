package com.snake.game.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.snake.game.engine.GameEngine;
import com.snake.game.engine.RoomManager;
import com.snake.game.model.Point;
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

@WebServlet("/api/room")
public class RoomServlet extends HttpServlet {
    private final Gson gson = new Gson();
    private final RoomManager roomManager = RoomManager.getInstance();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String action = req.getParameter("action");
        if ("list".equals(action)) {
            roomManager.cleanupStaleRooms();
            List<String> emptyRooms = new ArrayList<>();
            for (Room room : roomManager.getRooms().values()) {
                room.removeDisconnectedPlayers();
                if (room.getPlayerCount() == 0) emptyRooms.add(room.getCode());
            }
            for (String code : emptyRooms) roomManager.removeRoom(code);
            List<Map<String, Object>> roomList = new ArrayList<>();
            for (Room room : roomManager.getRooms().values()) {
                if (!room.isGameInProgress()) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("code", room.getCode());
                    entry.put("playerCount", room.getPlayerCount());
                    entry.put("spectatorCount", room.getSpectatorCount());
                    entry.put("maxPlayers", room.getMaxPlayers());
                    entry.put("gridSize", room.getGridSize());
                    entry.put("tickRateMs", room.getTickRateMs());
                    entry.put("foodDensity", room.getFoodDensity());
                    entry.put("enableBoost", room.isEnableBoost());
                    entry.put("enableGoldenFood", room.isEnableGoldenFood());
                    entry.put("gameMode", room.getGameMode());
                    entry.put("botCount", room.getBotCount());
                    entry.put("botDifficulty", room.getBotDifficulty());
                    roomList.add(entry);
                }
            }
            Map<String, Object> result = new HashMap<>();
            result.put("rooms", roomList);
            resp.getWriter().write(gson.toJson(result));
        } else {
            resp.getWriter().write(gson.toJson(Map.of("error", "Unknown action")));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Get client IP for rate limiting
        String clientIp = getClientIp(req);

        BufferedReader reader = req.getReader();
        JsonObject json = gson.fromJson(reader, JsonObject.class);
        if (json == null || !json.has("action") || json.get("action").isJsonNull()) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Missing action")));
            return;
        }
        String action = json.get("action").getAsString();

        // Apply rate limiting based on action (per IP)
        if (!checkRateLimit(req, resp, action, "ip:" + clientIp)) {
            return; // Rate limited response already sent
        }

        switch (action) {
            case "create":
                handleCreate(json, resp);
                break;
            case "join":
                handleJoin(json, resp);
                break;
            case "spectate":
                handleSpectate(json, resp);
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
            case "create":
                // 3 requests per 10 seconds per IP
                allowed = limiter.tryConsume(clientKey, 3, 10_000);
                retryAfterMs = limiter.getRetryAfterMs(clientKey, 3, 10_000);
                errorMessage = "Rate limit exceeded for create room (max 3 per 10 seconds)";
                break;
            case "join":
                // 5 requests per 10 seconds per IP
                allowed = limiter.tryConsume(clientKey, 5, 10_000);
                retryAfterMs = limiter.getRetryAfterMs(clientKey, 5, 10_000);
                errorMessage = "Rate limit exceeded for join room (max 5 per 10 seconds)";
                break;
            case "spectate":
                // 10 requests per 10 seconds per IP (spectators can join more freely)
                allowed = limiter.tryConsume(clientKey, 10, 10_000);
                retryAfterMs = limiter.getRetryAfterMs(clientKey, 10, 10_000);
                errorMessage = "Rate limit exceeded for spectate (max 10 per 10 seconds)";
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

    private String getString(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return null;
    }

    private boolean validateRoomCode(String roomCode, HttpServletResponse resp, String errorContext) throws IOException {
        if (roomCode == null || roomCode.isEmpty()) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", errorContext + ": Room code cannot be empty")));
            return false;
        }

        if (roomCode.length() != 6) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", errorContext + ": Room code must be exactly 6 characters")));
            return false;
        }

        if (!roomCode.matches("[A-Z2-9]{6}")) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", errorContext + ": Room code contains invalid characters")));
            return false;
        }

        return true;
    }

    private boolean validatePlayerName(String playerName, HttpServletResponse resp, String errorContext) throws IOException {
        if (playerName == null || playerName.isEmpty()) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", errorContext + ": Player name cannot be empty")));
            return false;
        }

        if (playerName.length() > 20) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", errorContext + ": Player name must be 20 characters or less")));
            return false;
        }

        // Explicitly reject control characters (e.g., newline, tab, escape)
        if (playerName.matches(".*\\p{Cntrl}.*")) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", errorContext + ": Player name cannot contain control characters")));
            return false;
        }

        if (!playerName.matches("[A-Za-z0-9\s_-]{1,20}")) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", errorContext + ": Player name contains invalid characters (alphanumeric, spaces, underscores, and hyphens only)")));
            return false;
        }

        return true;
    }

    private boolean validateSpectatorName(String spectatorName, HttpServletResponse resp, String errorContext) throws IOException {
        if (spectatorName == null || spectatorName.isEmpty()) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", errorContext + ": Spectator name cannot be empty")));
            return false;
        }

        if (spectatorName.length() > 20) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", errorContext + ": Spectator name must be 20 characters or less")));
            return false;
        }

        // Explicitly reject control characters (e.g., newline, tab, escape)
        if (spectatorName.matches(".*\\p{Cntrl}.*")) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", errorContext + ": Spectator name cannot contain control characters")));
            return false;
        }

        if (!spectatorName.matches("[A-Za-z0-9\\s_-]{1,20}")) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", errorContext + ": Spectator name contains invalid characters (alphanumeric, spaces, underscores, and hyphens only)")));
            return false;
        }

        return true;
    }

    private String validateColor(String color) {
        if (color != null && color.matches("^#[0-9a-fA-F]{6}$")) {
            return color;
        }
        return "#e94560";
    }
    
    private int validateIntSetting(int value, int min, int max, int defaultValue) {
        if (value < min || value > max) return defaultValue;
        return value;
    }
    
    private double validateDoubleSetting(double value, double min, double max, double defaultValue) {
        if (value < min || value > max) return defaultValue;
        return value;
    }
    
    private boolean validateBooleanSetting(Boolean value, boolean defaultValue) {
        return value != null ? value : defaultValue;
    }

    private void handleCreate(JsonObject json, HttpServletResponse resp) throws IOException {
        String playerName = getString(json, "playerName");
        String color = validateColor(getString(json, "color"));
        String gameMode = getString(json, "gameMode");
        if (gameMode == null) gameMode = "friends"; // default
        
        int botCount = 0;
        String botDifficulty = "normal";
        
        if ("bots".equals(gameMode)) {
            if (json.has("botCount") && !json.get("botCount").isJsonNull()) {
                botCount = json.get("botCount").getAsInt();
            }
            if (json.has("botDifficulty") && !json.get("botDifficulty").isJsonNull()) {
                botDifficulty = json.get("botDifficulty").getAsString();
            }
            // Validate bot count
            if (botCount < 1) botCount = 1;
            if (botCount > 3) botCount = 3;
            // Validate difficulty
            String[] validDifficulties = {"easy", "normal", "hard", "impossible"};
            boolean valid = false;
            for (String d : validDifficulties) {
                if (d.equals(botDifficulty)) { valid = true; break; }
            }
            if (!valid) botDifficulty = "normal";
        }

        // Validate custom room settings
        int gridSize = 30;
        if (json.has("gridSize") && !json.get("gridSize").isJsonNull()) {
            gridSize = validateIntSetting(json.get("gridSize").getAsInt(), 15, 50, 30);
        }
        
        int tickRateMs = 150;
        if (json.has("tickRateMs") && !json.get("tickRateMs").isJsonNull()) {
            tickRateMs = validateIntSetting(json.get("tickRateMs").getAsInt(), 50, 500, 150);
        }
        
        int maxPlayers = 4;
        if (json.has("maxPlayers") && !json.get("maxPlayers").isJsonNull()) {
            maxPlayers = validateIntSetting(json.get("maxPlayers").getAsInt(), 2, 8, 4);
        }
        
        double foodDensity = 1.0;
        if (json.has("foodDensity") && !json.get("foodDensity").isJsonNull()) {
            foodDensity = validateDoubleSetting(json.get("foodDensity").getAsDouble(), 0.5, 3.0, 1.0);
        }
        
        boolean enableBoost = true;
        if (json.has("enableBoost") && !json.get("enableBoost").isJsonNull()) {
            enableBoost = validateBooleanSetting(json.get("enableBoost").getAsBoolean(), true);
        }
        
        boolean enableGoldenFood = true;
        if (json.has("enableGoldenFood") && !json.get("enableGoldenFood").isJsonNull()) {
            enableGoldenFood = validateBooleanSetting(json.get("enableGoldenFood").getAsBoolean(), true);
        }

        if (!validatePlayerName(playerName, resp, "Create room")) {
            return;
        }

        Room room = roomManager.createRoom();
        room.setGameMode(gameMode);
        room.setBotCount(botCount);
        room.setBotDifficulty(botDifficulty);
        
        // Apply custom room settings
        room.setGridSize(gridSize);
        room.setTickRateMs(tickRateMs);
        room.setMaxPlayers(maxPlayers);
        room.setFoodDensity(foodDensity);
        room.setEnableBoost(enableBoost);
        room.setEnableGoldenFood(enableGoldenFood);
        
        Snake snake = new Snake(playerName, color, new Point(5, 5));
        snake.setDirection("RIGHT");
        snake.setNextDirection("RIGHT");
        // Use RoomManager method to add player and log
        roomManager.addPlayerToRoom(room.getCode(), snake);
        
        if ("bots".equals(gameMode)) {
            BotManager.fillWithBots(room, botCount, botDifficulty);
        }
        // No bots for "friends" mode
        
        // Initialize GameState for the room so it appears in active rooms
        GameEngine.initGameState(room);

        resp.getWriter().write(gson.toJson(Map.of(
            "success", true,
            "roomCode", room.getCode()
        )));
    }

    private void handleJoin(JsonObject json, HttpServletResponse resp) throws IOException {
        String roomCode = getString(json, "roomCode");
        String playerName = getString(json, "playerName");
        String color = validateColor(getString(json, "color"));

        if (!validateRoomCode(roomCode, resp, "Join room")) {
            return;
        }

        if (playerName != null) {
            playerName = playerName.trim();
        }
        if (!validatePlayerName(playerName, resp, "Join room")) {
            return;
        }

        Room room = roomManager.getRoom(roomCode);

        if (room == null) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Room not found")));
            return;
        }
        room.touch();

        if (room.isGameInProgress()) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Game already in progress")));
            return;
        }

        synchronized (room) {
            room.removeDisconnectedPlayers();

            // Re-check capacity after removing disconnected players
            if (room.isFull()) {
                resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Room is full")));
                return;
            }

            if (room.getPlayer(playerName) != null) {
                resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Name already taken in this room")));
                return;
            }

            // Assign spawn position & direction based on 4-quadrant layout using room's gridSize
            int gridSize = room.getGridSize() > 0 ? room.getGridSize() : 30;
            int margin = Math.max(3, gridSize / 10); // At least 3 cells from edge
            int offset = gridSize - margin - 1;
            int idx = room.getPlayerCount() % 4;
            Point[] spawnHeads = { new Point(margin, margin), new Point(offset, margin), new Point(margin, offset), new Point(offset, offset) };
            String[] spawnDirs = { "RIGHT", "LEFT", "RIGHT", "LEFT" };
            
            Point head = spawnHeads[idx];
            String dir = spawnDirs[idx];
            int dx = dir.equals("RIGHT") ? -1 : 1;

            Snake snake = new Snake();
            snake.setName(playerName);
            snake.setColor(color);
            snake.setDirection(dir);
            snake.setNextDirection(dir);
            List<Point> segs = new ArrayList<>();
            segs.add(head);
            segs.add(new Point(head.getX() + dx, head.getY()));
            segs.add(new Point(head.getX() + 2 * dx, head.getY()));
            snake.setSegments(segs);

            // Use RoomManager method to add player and log
            roomManager.addPlayerToRoom(roomCode, snake);
            
            if (room.getGameState() != null) {
                GameWebSocket.broadcastState(room.getCode(), room.getGameState());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("roomCode", room.getCode());
        response.put("gridSize", room.getGridSize());
        response.put("tickRateMs", room.getTickRateMs());
        response.put("maxPlayers", room.getMaxPlayers());
        response.put("foodDensity", room.getFoodDensity());
        response.put("enableBoost", room.isEnableBoost());
        response.put("enableGoldenFood", room.isEnableGoldenFood());
        response.put("gameMode", room.getGameMode());
        response.put("botCount", room.getBotCount());
        response.put("botDifficulty", room.getBotDifficulty());
        resp.getWriter().write(gson.toJson(response));
    }

    private void handleSpectate(JsonObject json, HttpServletResponse resp) throws IOException {
        String roomCode = getString(json, "roomCode");
        String spectatorName = getString(json, "spectatorName");

        if (!validateRoomCode(roomCode, resp, "Spectate")) {
            return;
        }

        if (spectatorName != null) {
            spectatorName = spectatorName.trim();
        }
        if (!validateSpectatorName(spectatorName, resp, "Spectate")) {
            return;
        }

        Room room = roomManager.getRoom(roomCode);

        if (room == null) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Room not found")));
            return;
        }
        room.touch();

        synchronized (room) {
            // Check if name is already taken by a player
            if (room.getPlayer(spectatorName) != null) {
                resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Name already taken by a player in this room")));
                return;
            }

            // Check if name is already taken by a spectator
            if (room.hasSpectator(spectatorName)) {
                resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Name already taken by a spectator in this room")));
                return;
            }

            // Add spectator using RoomManager
            boolean added = roomManager.addSpectatorToRoom(roomCode, spectatorName);
            if (!added) {
                resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Failed to add spectator")));
                return;
            }
        }

        // Return room settings (same as join response)
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("roomCode", room.getCode());
        response.put("gridSize", room.getGridSize());
        response.put("tickRateMs", room.getTickRateMs());
        response.put("maxPlayers", room.getMaxPlayers());
        response.put("foodDensity", room.getFoodDensity());
        response.put("enableBoost", room.isEnableBoost());
        response.put("enableGoldenFood", room.isEnableGoldenFood());
        response.put("gameMode", room.getGameMode());
        response.put("botCount", room.getBotCount());
        response.put("botDifficulty", room.getBotDifficulty());
        resp.getWriter().write(gson.toJson(response));
    }
}
