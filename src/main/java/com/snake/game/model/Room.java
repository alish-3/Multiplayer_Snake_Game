package com.snake.game.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class Room {
    private String code;
    private List<Snake> players;
    private GameState gameState;
    private boolean gameInProgress;
    private int maxPlayers;
    private long lastActivity;
    private long gameOverTimestamp; // Time when game reached gameOver state
    
    // Bot game mode fields
    private String gameMode; // "friends" or "bots"
    private int botCount; // Number of bots to add (0-3)
    private String botDifficulty; // "easy", "normal", "hard", "impossible"
    
    // Custom room settings
    private int gridSize; // default 30, range 15-50
    private int tickRateMs; // default 150, range 50-500
    private double foodDensity; // default 1.0, range 0.5-3.0
    private boolean enableBoost; // default true
    private boolean enableGoldenFood; // default true
    
    // Spectators
    private final Set<String> spectators = ConcurrentHashMap.newKeySet();

    public Room() {
        this.code = generateCode();
        this.players = new ArrayList<>();
        this.gameInProgress = false;
        this.maxPlayers = 4;
        this.lastActivity = System.currentTimeMillis();
        this.gameOverTimestamp = 0; // 0 means never went to gameOver
        this.gameMode = "friends"; // default
        this.botCount = 0;
        this.botDifficulty = "normal"; // default
        
        // Custom room settings defaults
        this.gridSize = 30;
        this.tickRateMs = 150;
        this.foodDensity = 1.0;
        this.enableBoost = true;
        this.enableGoldenFood = true;
    }

    public Room(String code) {
        this();
        this.code = code;
    }

    private String generateCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(6);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    public void touch() {
        this.lastActivity = System.currentTimeMillis();
    }

    public long getLastActivity() { return lastActivity; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public List<Snake> getPlayers() { return players; }
    public void setPlayers(List<Snake> players) { this.players = players; }
    public GameState getGameState() { return gameState; }
    public void setGameState(GameState gameState) { this.gameState = gameState; }
    public boolean isGameInProgress() { return gameInProgress; }
    public void setGameInProgress(boolean gameInProgress) { this.gameInProgress = gameInProgress; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public long getGameOverTimestamp() { return gameOverTimestamp; }
    public void setGameOverTimestamp(long timestamp) { this.gameOverTimestamp = timestamp; }
    
    // Bot game mode getters/setters
    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }
    public int getBotCount() { return botCount; }
    public void setBotCount(int botCount) { this.botCount = botCount; }
    public String getBotDifficulty() { return botDifficulty; }
    public void setBotDifficulty(String botDifficulty) { this.botDifficulty = botDifficulty; }
    
    // Custom room settings getters/setters
    public int getGridSize() { return gridSize; }
    public void setGridSize(int gridSize) { this.gridSize = gridSize; }
    public int getTickRateMs() { return tickRateMs; }
    public void setTickRateMs(int tickRateMs) { this.tickRateMs = tickRateMs; }
    public double getFoodDensity() { return foodDensity; }
    public void setFoodDensity(double foodDensity) { this.foodDensity = foodDensity; }
    public boolean isEnableBoost() { return enableBoost; }
    public void setEnableBoost(boolean enableBoost) { this.enableBoost = enableBoost; }
    public boolean isEnableGoldenFood() { return enableGoldenFood; }
    public void setEnableGoldenFood(boolean enableGoldenFood) { this.enableGoldenFood = enableGoldenFood; }

    public int getPlayerCount() {
        return players.size();
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public Snake getPlayer(String name) {
        for (Snake s : players) {
            if (s.getName().equals(name)) return s;
        }
        return null;
    }

    public boolean removePlayer(String name) {
        return players.removeIf(s -> s.getName().equals(name));
    }

    public void removeDisconnectedPlayers() {
        players.removeIf(Snake::isDisconnected);
    }

    public boolean allPlayersReady() {
        if (players.isEmpty()) return false;
        for (Snake s : players) {
            if (!s.isReady()) return false;
        }
        return true;
    }

    public boolean canRestart() {
        if (!gameInProgress && gameState != null && gameState.isGameOver()) {
            // After game over, require 10 seconds before allowing restart
            long now = System.currentTimeMillis();
            return (now - gameOverTimestamp) >= 10000; // 10 seconds in milliseconds
        }
        return false;
    }

    // ==================== Spectator Methods ====================

    /**
     * Returns the set of spectator names.
     * Thread-safe: returns the concurrent set directly.
     */
    public Set<String> getSpectators() {
        return spectators;
    }

    /**
     * Adds a spectator to the room.
     * @param name The spectator name
     * @return true if the spectator was added (was not already present), false if already a spectator
     */
    public boolean addSpectator(String name) {
        return spectators.add(name);
    }

    /**
     * Removes a spectator from the room.
     * @param name The spectator name
     * @return true if the spectator was removed, false if not found
     */
    public boolean removeSpectator(String name) {
        return spectators.remove(name);
    }

    /**
     * Returns the number of spectators in the room.
     */
    public int getSpectatorCount() {
        return spectators.size();
    }

    /**
     * Checks if a name is currently a spectator in this room.
     * @param name The name to check
     * @return true if the name is a spectator
     */
    public boolean hasSpectator(String name) {
        return spectators.contains(name);
    }

    /**
     * Returns the total number of occupants (players + spectators).
     */
    public int getTotalOccupants() {
        return players.size() + spectators.size();
    }
}
