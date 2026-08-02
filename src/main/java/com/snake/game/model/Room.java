package com.snake.game.model;

import java.util.ArrayList;
import java.util.List;
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
}
