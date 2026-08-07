package com.snake.game.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.snake.game.db.DatabaseManager;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/profile")
public class ProfileServlet extends HttpServlet {
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        HttpSession session = req.getSession(false);
        if (session == null || Boolean.TRUE.equals(session.getAttribute("isGuest"))) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Not authenticated")));
            return;
        }
        String username = (String) session.getAttribute("username");
        if (username == null) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Not authenticated")));
            return;
        }

        Map<String, Object> profile = DatabaseManager.getProfile(username);
        if (profile != null) {
            resp.getWriter().write(gson.toJson(Map.of("success", true, "profile", profile)));
        } else {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Profile not found")));
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

        HttpSession session = req.getSession(false);
        if (session == null || Boolean.TRUE.equals(session.getAttribute("isGuest"))) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Not authenticated")));
            return;
        }
        String username = (String) session.getAttribute("username");
        if (username == null) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Not authenticated")));
            return;
        }

        switch (action) {
            case "changePassword":
                handleChangePassword(json, username, resp);
                break;
            case "changeUsername":
                handleChangeUsername(json, username, session, resp);
                break;
            case "deleteAccount":
                handleDeleteAccount(json, username, session, resp);
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

    private void handleChangePassword(JsonObject json, String username, HttpServletResponse resp) throws IOException {
        String currentPassword = getString(json, "currentPassword");
        String newPassword = getString(json, "newPassword");
        String confirmPassword = getString(json, "confirmPassword");

        if (currentPassword == null || newPassword == null || confirmPassword == null) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "All fields are required")));
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "New passwords do not match")));
            return;
        }

        if (newPassword.length() < 4 || newPassword.length() > 100) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Password must be between 4 and 100 characters")));
            return;
        }

        // Verify current password
        if (!DatabaseManager.checkPasswordHash(username, currentPassword)) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Current password is incorrect")));
            return;
        }

        // Update password
        String newHash = DatabaseManager.hashPasswordForStorage(newPassword);
        if (DatabaseManager.updatePassword(username, newHash)) {
            resp.getWriter().write(gson.toJson(Map.of("success", true)));
        } else {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Failed to update password")));
        }
    }

    private void handleChangeUsername(JsonObject json, String username, HttpSession session, HttpServletResponse resp) throws IOException {
        String newUsername = getString(json, "newUsername");
        String currentPassword = getString(json, "currentPassword");

        if (newUsername == null || currentPassword == null) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "All fields are required")));
            return;
        }

        newUsername = newUsername.trim();
        if (newUsername.isEmpty()) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Username cannot be empty")));
            return;
        }

        if (!newUsername.matches("[A-Za-z0-9_-]{3,20}")) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Username must be 3-20 characters and may only contain letters, numbers, underscores, and hyphens")));
            return;
        }

        if (newUsername.equals(username)) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "New username must be different from current username")));
            return;
        }

        // Verify current password
        if (!DatabaseManager.checkPasswordHash(username, currentPassword)) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Current password is incorrect")));
            return;
        }

        // Update username
        if (DatabaseManager.updateUsername(username, newUsername)) {
            // Update session
            session.setAttribute("username", newUsername);
            resp.getWriter().write(gson.toJson(Map.of("success", true, "username", newUsername)));
        } else {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Username already taken")));
        }
    }

    private void handleDeleteAccount(JsonObject json, String username, HttpSession session, HttpServletResponse resp) throws IOException {
        String currentPassword = getString(json, "currentPassword");
        Boolean confirmed = json.has("confirmed") && !json.get("confirmed").isJsonNull() && json.get("confirmed").getAsBoolean();

        if (currentPassword == null || !confirmed) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Password and confirmation are required")));
            return;
        }

        // Verify current password
        if (!DatabaseManager.checkPasswordHash(username, currentPassword)) {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Current password is incorrect")));
            return;
        }

        // Delete account
        if (DatabaseManager.deleteUser(username)) {
            // Invalidate session
            session.invalidate();
            resp.getWriter().write(gson.toJson(Map.of("success", true)));
        } else {
            resp.getWriter().write(gson.toJson(Map.of("success", false, "error", "Failed to delete account")));
        }
    }
}