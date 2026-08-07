package com.snake.game.servlet;

import com.google.gson.Gson;
import com.snake.game.db.DatabaseManager;
import com.snake.game.engine.GameEngine;
import com.snake.game.engine.RoomManager;
import com.snake.game.model.Room;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@WebServlet("/api/health")
public class HealthServlet extends HttpServlet {
    private final Gson gson = new Gson();
    private final RoomManager roomManager = RoomManager.getInstance();
    private final long serverStartTime = System.currentTimeMillis();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Map<String, Object> health = new ConcurrentHashMap<>();

        // 1. Overall health status
        boolean dbHealthy = checkDatabase();
        boolean overallHealthy = dbHealthy;
        health.put("status", overallHealthy ? "UP" : "DOWN");

        // 2. Active rooms count
        int activeRooms = roomManager.getRooms().size();
        health.put("activeRooms", activeRooms);

        // 3. Active WebSocket connections count
        int activeConnections = GameWebSocket.getActiveConnectionCount();
        health.put("activeConnections", activeConnections);

        // 4. Total players across all rooms
        int totalPlayers = 0;
        for (Room room : roomManager.getRooms().values()) {
            totalPlayers += room.getPlayerCount();
        }
        health.put("totalPlayers", totalPlayers);

        // 5. Games in progress count
        int gamesInProgress = 0;
        for (Room room : roomManager.getRooms().values()) {
            if (room.isGameInProgress()) {
                gamesInProgress++;
            }
        }
        health.put("gamesInProgress", gamesInProgress);

        // 6. Server uptime
        long uptimeMs = System.currentTimeMillis() - serverStartTime;
        health.put("uptimeMs", uptimeMs);
        health.put("uptimeHuman", formatUptime(uptimeMs));

        // 7. Memory usage
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        long heapMax = runtime.maxMemory();
        Map<String, Object> memory = new ConcurrentHashMap<>();
        memory.put("heapUsedBytes", heapUsed);
        memory.put("heapMaxBytes", heapMax);
        memory.put("heapUsedMb", heapUsed / (1024 * 1024));
        memory.put("heapMaxMb", heapMax / (1024 * 1024));
        memory.put("heapUsagePercent", heapMax > 0 ? Math.round((double) heapUsed / heapMax * 100) : 0);
        health.put("memory", memory);

        // 8. Database connectivity
        Map<String, Object> database = new ConcurrentHashMap<>();
        database.put("connected", dbHealthy);
        database.put("url", sanitizeUrl(DatabaseManager.getUrl()));
        health.put("database", database);

        // Timestamp in ISO format
        health.put("timestamp", Instant.now().toString());

        resp.getWriter().write(gson.toJson(health));
    }

    private boolean checkDatabase() {
        try (Connection conn = DatabaseManager.getConnection()) {
            return conn != null && !conn.isClosed() && conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private String sanitizeUrl(String url) {
        if (url == null) return "unknown";
        // Remove password from URL for security
        return url.replaceAll("(?<=://)[^:]+:[^@]+@", "***:***@");
    }

    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours % 24, minutes % 60, seconds % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
}