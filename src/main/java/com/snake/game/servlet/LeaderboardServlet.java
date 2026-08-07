package com.snake.game.servlet;

import com.google.gson.Gson;
import com.snake.game.db.DatabaseManager;
import com.snake.game.util.RateLimiter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@WebServlet("/api/leaderboard")
public class LeaderboardServlet extends HttpServlet {
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Get client IP for rate limiting
        String clientIp = getClientIp(req);
        String clientKey = "ip:" + clientIp;

        // Apply rate limiting: 10 requests per 10 seconds per IP
        RateLimiter limiter = RateLimiter.getInstance();
        if (!limiter.tryConsume(clientKey, 10, 10_000)) {
            long retryAfterMs = limiter.getRetryAfterMs(clientKey, 10, 10_000);
            resp.setStatus(429); // Too Many Requests
            resp.setHeader("Retry-After", String.valueOf((retryAfterMs + 999) / 1000));
            resp.getWriter().write(gson.toJson(Map.of(
                "success", false,
                "error", "Rate limit exceeded for leaderboard (max 10 per 10 seconds)",
                "retryAfterMs", retryAfterMs
            )));
            return;
        }

        // Parse and validate query parameters
        int page = parsePage(req.getParameter("page"));
        int size = parseSize(req.getParameter("size"));

        int offset = (page - 1) * size;

        // Fetch leaderboard data
        List<Map<String, Object>> leaderboardData = DatabaseManager.getLeaderboard(size, offset);
        int totalPlayers = DatabaseManager.getTotalPlayerCount();
        int totalPages = (int) Math.ceil((double) totalPlayers / size);

        // Build response with rank calculation
        List<Map<String, Object>> leaderboard = new ArrayList<>();
        for (int i = 0; i < leaderboardData.size(); i++) {
            Map<String, Object> entry = leaderboardData.get(i);
            Map<String, Object> rankedEntry = new LinkedHashMap<>();
            rankedEntry.put("rank", offset + i + 1);
            rankedEntry.put("username", entry.get("username"));
            rankedEntry.put("highScore", entry.get("highScore"));
            rankedEntry.put("totalScore", entry.get("totalScore"));
            rankedEntry.put("totalGames", entry.get("totalGames"));
            rankedEntry.put("createdAt", entry.get("createdAt"));
            leaderboard.add(rankedEntry);
        }

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("page", page);
        pagination.put("size", size);
        pagination.put("totalPages", totalPages);
        pagination.put("totalPlayers", totalPlayers);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("leaderboard", leaderboard);
        response.put("pagination", pagination);

        resp.getWriter().write(gson.toJson(response));
    }

    /**
     * Parses the page parameter with validation.
     * Default: 1, Minimum: 1
     */
    private int parsePage(String param) {
        if (param == null || param.isEmpty()) {
            return 1;
        }
        try {
            int page = Integer.parseInt(param);
            return Math.max(1, page);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Parses the size parameter with validation.
     * Default: 20, Minimum: 1, Maximum: 100
     */
    private int parseSize(String param) {
        if (param == null || param.isEmpty()) {
            return 20;
        }
        try {
            int size = Integer.parseInt(param);
            if (size < 1) return 20;
            if (size > 100) return 100;
            return size;
        } catch (NumberFormatException e) {
            return 20;
        }
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
}