package com.snake.game.db;

import java.sql.*;
import java.util.*;
import java.util.Properties;
import java.util.Optional;
import org.mindrot.jbcrypt.BCrypt;

public class DatabaseManager {
    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/snake_game";
    private static final String DEFAULT_USER = "postgres";
    
    private static String URL;
    private static String USER;
    private static String PASSWORD;

    static {
        try {
            Class.forName("org.postgresql.Driver");

            loadCredentials();
            
            createTableIfNotExists();   
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL driver not found", e);
        }
    }

    /**
     * Returns the database URL (sanitized for health checks).
     */
    public static String getUrl() {
        return URL;
    }
    
    private static void loadCredentials() {
        URL = Optional.ofNullable(System.getenv("DB_URL"))
                    .filter(s -> !s.trim().isEmpty())
                    .orElse(DEFAULT_URL);
                    
        USER = Optional.ofNullable(System.getenv("DB_USER"))
                    .filter(s -> !s.trim().isEmpty())
                    .orElse(DEFAULT_USER);
                    
        PASSWORD = System.getenv("DB_PASSWORD");
        
        // Validate credentials - ensure they are not empty
        if (URL == null || URL.trim().isEmpty()) {
            throw new RuntimeException(
                "Database URL is required. Please set the DB_URL environment variable (e.g., \"jdbc:postgresql://localhost:5432/snake_game\") " +
                "or provide a fallback value in DEFAULT_URL");
        }
        
        if (USER == null || USER.trim().isEmpty()) {
            throw new RuntimeException(
                "Database username is required. Please set the DB_USER environment variable (e.g., \"postgres\") " +
                "or provide a fallback value in DEFAULT_USER");
        }
        
        if (PASSWORD == null || PASSWORD.trim().isEmpty()) {
            throw new RuntimeException(
                "Database password is required. Please set the DB_PASSWORD environment variable " +
                "to the password of your PostgreSQL user");
        }
        
        // Additional validation: check if URL looks like a valid PostgreSQL JDBC URL
        if (!URL.startsWith("jdbc:postgresql://")) {
            throw new RuntimeException(
                "Invalid database URL format. Must start with \"jdbc:postgresql://\". Got: " + URL);
        }
    }

    private static void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS players (" +
                "id SERIAL PRIMARY KEY, " +
                "username VARCHAR(50) UNIQUE NOT NULL, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "total_games INT DEFAULT 0, " +
                "total_score INT DEFAULT 0, " +
                "high_score INT DEFAULT 0)";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create players table", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", USER);
        props.setProperty("password", PASSWORD);
        return DriverManager.getConnection(URL, props);
    }

    public static boolean registerUser(String username, String password) {
        String sql = "INSERT INTO players (username, password_hash) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, hashPassword(password));
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getSQLState().equals("23505")) return false;
            throw new RuntimeException("Registration failed", e);
        }
    }

    public static boolean authenticateUser(String username, String password) {
        String sql = "SELECT password_hash FROM players WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                if (checkPassword(password, storedHash)) {
                    updateLastLogin(username);
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("Authentication failed", e);
        }
    }

    private static void updateLastLogin(String username) {
        String sql = "UPDATE players SET last_login = CURRENT_TIMESTAMP WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update last login", e);
        }
    }

    private static String hashPassword(String password) {
        try {
            String salt = BCrypt.gensalt();
            return BCrypt.hashpw(password, salt);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    private static boolean checkPassword(String password, String stored) {
        try {
            if (stored == null) {
                return false;
            }
            
            boolean isBcrypt = stored.startsWith("$2b$") || stored.startsWith("$2a$") || stored.startsWith("$2y$");
            
            if (isBcrypt) {
                return BCrypt.checkpw(password, stored);
            } else {
                boolean valid = checkOldSHA256Password(password, stored);
                if (valid) {
                    rehashToBcrypt(password, stored);
                }
                return valid;
            }
        } catch (Exception e) {
            throw new RuntimeException("Password check failed", e);
        }
    }
    
    private static boolean checkOldSHA256Password(String password, String storedHash) {
        try {
            String[] parts = storedHash.split(":");
            if (parts.length != 2) {
                return false;
            }
            byte[] salt = new byte[16];
            for (int i = 0; i < 16; i++) {
                salt[i] = (byte) Integer.parseInt(parts[0].substring(i * 2, i * 2 + 2), 16);
            }
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString().equals(parts[1]);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 password check failed", e);
        }
    }
    
    private static void rehashToBcrypt(String password, String oldSha256Hash) {
        String newHash = hashPassword(password);
        String sql = "UPDATE players SET password_hash = ? WHERE password_hash = ?";
        try (Connection conn = getConnection(); 
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newHash);
            stmt.setString(2, oldSha256Hash);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to rehash password", e);
        }
    }

    private static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        return salt;
    }

    public static void saveScore(String username, int score) {
        String sql = "UPDATE players SET total_games = total_games + 1, total_score = total_score + ?, high_score = GREATEST(high_score, ?) WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, score);
            stmt.setInt(2, score);
            stmt.setString(3, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save score", e);
        }
    }

    public static Map<String, Object> getStats(String username) {
        String sql = "SELECT total_games, total_score, high_score FROM players WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> stats = new java.util.HashMap<>();
                stats.put("totalGames", rs.getInt("total_games"));
                stats.put("totalScore", rs.getInt("total_score"));
                stats.put("highScore", rs.getInt("high_score"));
                return stats;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get stats", e);
        }
        return null;
    }

    public static Map<String, Object> getProfile(String username) {
        String sql = "SELECT username, created_at, last_login, total_games, total_score, high_score FROM players WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> profile = new java.util.HashMap<>();
                profile.put("username", rs.getString("username"));
                profile.put("createdAt", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null);
                profile.put("lastLogin", rs.getTimestamp("last_login") != null ? rs.getTimestamp("last_login").toString() : null);
                profile.put("totalGames", rs.getInt("total_games"));
                profile.put("totalScore", rs.getInt("total_score"));
                profile.put("highScore", rs.getInt("high_score"));
                return profile;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get profile", e);
        }
        return null;
    }

    public static boolean updatePassword(String username, String newPasswordHash) {
        String sql = "UPDATE players SET password_hash = ? WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newPasswordHash);
            stmt.setString(2, username);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update password", e);
        }
    }

    public static boolean updateUsername(String oldUsername, String newUsername) {
        String sql = "UPDATE players SET username = ? WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newUsername);
            stmt.setString(2, oldUsername);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            if (e.getSQLState().equals("23505")) return false;
            throw new RuntimeException("Failed to update username", e);
        }
    }

    public static boolean deleteUser(String username) {
        String sql = "DELETE FROM players WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    public static boolean checkPasswordHash(String username, String password) {
        String sql = "SELECT password_hash FROM players WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                return checkPassword(password, storedHash);
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check password", e);
        }
    }

    public static String hashPasswordForStorage(String password) {
        return hashPassword(password);
    }

    /**
     * Returns a paginated leaderboard ordered by high_score DESC, total_score DESC.
     * Ties are broken by total_score then username.
     *
     * @param limit  maximum number of entries to return
     * @param offset number of entries to skip
     * @return list of maps with username, totalGames, totalScore, highScore, createdAt
     */
    public static List<Map<String, Object>> getLeaderboard(int limit, int offset) {
        String sql = "SELECT username, total_games, total_score, high_score, created_at " +
                "FROM players ORDER BY high_score DESC, total_score DESC, username ASC LIMIT ? OFFSET ?";
        List<Map<String, Object>> leaderboard = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("username", rs.getString("username"));
                entry.put("totalGames", rs.getInt("total_games"));
                entry.put("totalScore", rs.getInt("total_score"));
                entry.put("highScore", rs.getInt("high_score"));
                entry.put("createdAt", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null);
                leaderboard.add(entry);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get leaderboard", e);
        }
        return leaderboard;
    }

    /**
     * Returns the total number of registered players.
     *
     * @return total player count
     */
    public static int getTotalPlayerCount() {
        String sql = "SELECT COUNT(*) FROM players";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get total player count", e);
        }
        return 0;
    }
}
