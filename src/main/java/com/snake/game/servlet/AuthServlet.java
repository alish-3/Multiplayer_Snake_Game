package com.snake.game.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.snake.game.db.DatabaseManager;
import com.snake.game.util.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/auth")
public class AuthServlet extends HttpServlet {
    private final Gson gson = new Gson();

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
            case "register":
                handleRegister(json, req, resp);
                break;
            case "login":
                handleLogin(json, req, resp);
                break;
            case "saveScore":
                handleSaveScore(json, req, resp);
                break;
            case "stats":
                handleStats(json, req, resp);
                break;
            case "remember":
                handleRemember(json, resp);
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

    private Integer getInt(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull() && json.get(key).isJsonPrimitive()) {
            try {
                return json.get(key).getAsInt();
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private void handleRegister(JsonObject json, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = getString(json, "username");
        String password = getString(json, "password");
        if (username == null || password == null) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Username and password are required")));
            return;
        }
        username = username.trim();
        if (username.isEmpty() || password.isEmpty()) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Username and password are required")));
            return;
        }
        if (!username.matches("[A-Za-z0-9_-]{3,20}")) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Username must be 3-20 characters and may only contain letters, numbers, underscores, and hyphens")));
            return;
        }
        if (password.length() < 4 || password.length() > 100) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Password must be between 4 and 100 characters")));
            return;
        }
        if (DatabaseManager.registerUser(username, password)) {
            HttpSession session = req.getSession(true);
            session.setAttribute("username", username);
            session.setAttribute("isGuest", false);
            resp.getWriter().write(gson.toJson(Map.of("success", true, "username", username)));
        } else {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Username already taken")));
        }
    }

    private void handleLogin(JsonObject json, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = getString(json, "username");
        String password = getString(json, "password");
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Username and password are required")));
            return;
        }
        username = username.trim();
        if (username.length() > 50) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Username must be 50 characters or less")));
            return;
        }
        if (DatabaseManager.authenticateUser(username, password)) {
            HttpSession session = req.getSession(true);
            session.setAttribute("username", username);
            session.setAttribute("isGuest", false);
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("username", username);
            boolean remember = json.has("remember") && !json.get("remember").isJsonNull() && json.get("remember").getAsBoolean();
            if (remember) {
                result.put("token", JwtUtil.createToken(username));
            }
            resp.getWriter().write(gson.toJson(result));
        } else {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Invalid username or password")));
        }
    }

    private void handleRemember(JsonObject json, HttpServletResponse resp) throws IOException {
        String token = getString(json, "token");
        if (token == null || token.isEmpty()) {
            resp.getWriter().write(gson.toJson(Map.of("success", false)));
            return;
        }
        String username = JwtUtil.validateToken(token);
        if (username != null) {
            resp.getWriter().write(gson.toJson(Map.of("success", true, "username", username)));
        } else {
            resp.getWriter().write(gson.toJson(Map.of("success", false)));
        }
    }

    private void handleSaveScore(JsonObject json, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = getString(json, "username");
        Integer score = getInt(json, "score");
        if (username == null || username.trim().isEmpty() || score == null) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Username and score are required")));
            return;
        }
        username = username.trim();
        HttpSession session = req.getSession(false);
        boolean authenticated = session != null
                && username.equals(session.getAttribute("username"))
                && !Boolean.TRUE.equals(session.getAttribute("isGuest"));
        if (!authenticated) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Not authenticated")));
            return;
        }
        DatabaseManager.saveScore(username, score);
        resp.getWriter().write(gson.toJson(Map.of("success", true)));
    }

    private void handleStats(JsonObject json, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = getString(json, "username");
        if (username == null || username.trim().isEmpty()) {
            resp.getWriter().write(gson.toJson(Map.of("success", false)));
            return;
        }
        username = username.trim();
        HttpSession session = req.getSession(false);
        boolean authenticated = session != null
                && username.equals(session.getAttribute("username"))
                && !Boolean.TRUE.equals(session.getAttribute("isGuest"));
        if (!authenticated) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Not authenticated")));
            return;
        }
        Map<String, Object> stats = DatabaseManager.getStats(username);
        if (stats != null) {
            resp.getWriter().write(gson.toJson(Map.of("success", true, "stats", stats)));
        } else {
            resp.getWriter().write(gson.toJson(Map.of("success", false)));
        }
    }
}
