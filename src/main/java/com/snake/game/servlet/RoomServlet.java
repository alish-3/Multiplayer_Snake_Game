package com.snake.game.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.snake.game.engine.RoomManager;
import com.snake.game.model.Point;
import com.snake.game.model.Room;
import com.snake.game.model.Snake;
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
                    entry.put("maxPlayers", room.getMaxPlayers());
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

        BufferedReader reader = req.getReader();
        JsonObject json = gson.fromJson(reader, JsonObject.class);
        if (json == null || !json.has("action") || json.get("action").isJsonNull()) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Missing action")));
            return;
        }
        String action = json.get("action").getAsString();

        switch (action) {
            case "create":
                handleCreate(json, resp);
                break;
            case "join":
                handleJoin(json, resp);
                break;
            default:
                resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Unknown action")));
        }
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

    private String validateColor(String color) {
        if (color != null && color.matches("^#[0-9a-fA-F]{6}$")) {
            return color;
        }
        return "#e94560";
    }

    private void handleCreate(JsonObject json, HttpServletResponse resp) throws IOException {
        String playerName = getString(json, "playerName");
        String color = validateColor(getString(json, "color"));

        if (!validatePlayerName(playerName, resp, "Create room")) {
            return;
        }

        Room room = roomManager.createRoom();
        Snake snake = new Snake(playerName, color, new Point(5, 15));
        synchronized (room) {
            room.getPlayers().add(snake);
        }

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

            // Assign spawn position based on player count
            int startX = 5 + room.getPlayerCount() * 6;
            Snake snake = new Snake(playerName, color, new Point(startX, 15));
            room.getPlayers().add(snake);

            if (room.getGameState() != null) {
                GameWebSocket.broadcastState(room.getCode(), room.getGameState());
            }
        }

        resp.getWriter().write(gson.toJson(Map.of(
            "success", true,
            "roomCode", room.getCode()
        )));
    }
}
