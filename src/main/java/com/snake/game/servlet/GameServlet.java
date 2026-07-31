package com.snake.game.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.snake.game.engine.GameEngine;
import com.snake.game.engine.RoomManager;
import com.snake.game.model.GameState;
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
                waiting.put("gridSize", 30);
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

        BufferedReader reader = req.getReader();
        JsonObject json = gson.fromJson(reader, JsonObject.class);
        if (json == null || !json.has("action") || json.get("action").isJsonNull()) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Missing action")));
            return;
        }
        String action = json.get("action").getAsString();
        String roomCode = json.has("roomCode") && !json.get("roomCode").isJsonNull() ? json.get("roomCode").getAsString() : null;
        String playerName = json.has("playerName") && !json.get("playerName").isJsonNull() ? json.get("playerName").getAsString() : null;

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

    private void handleReady(Room room, String playerName, HttpServletResponse resp) throws IOException {
        synchronized (room) {
            Snake snake = room.getPlayer(playerName);
            if (snake == null) {
                resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Player not found")));
                return;
            }
            snake.touch();
            snake.setReady(true);

            room.removeDisconnectedPlayers();
            boolean shouldStart = !room.isGameInProgress() && room.allPlayersReady();
            if (shouldStart) {
                if (room.canRestart()) {
                    room.getGameState().setGameOver(false);
                }
                GameEngine.resetGame(room);
                GameEngine.startGame(room);
            }
        }

        resp.getWriter().write(gson.toJson(Map.of("success", true)));
    }

    private void handleLeave(Room room, String playerName, HttpServletResponse resp) throws IOException {
        synchronized (room) {
            room.removePlayer(playerName);
        }

        if (room.getPlayerCount() == 0) {
            GameEngine.stopGame(room.getCode());
            roomManager.removeRoom(room.getCode());
        }

        resp.getWriter().write(gson.toJson(Map.of("success", true)));
    }
}
