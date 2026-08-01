package com.snake.game.model;

import java.util.List;

public class GameState {
    private List<Snake> snakes;
    private List<Food> foods;
    private int gridSize;
    private boolean gameOver;
    private boolean gameStarted;
    private int tick;
    private int countdown;
    private long roundDurationMs;

    public GameState() {}

    public GameState(List<Snake> snakes, List<Food> foods, int gridSize, boolean gameOver, boolean gameStarted, int tick) {
        this.snakes = snakes;
        this.foods = foods;
        this.gridSize = gridSize;
        this.gameOver = gameOver;
        this.gameStarted = gameStarted;
        this.tick = tick;
        this.countdown = -1;
    }

    public List<Snake> getSnakes() { return snakes; }
    public void setSnakes(List<Snake> snakes) { this.snakes = snakes; }
    public List<Food> getFoods() { return foods; }
    public void setFoods(List<Food> foods) { this.foods = foods; }
    // Backward compatibility - returns first food item
    public Food getFood() { return foods != null && !foods.isEmpty() ? foods.get(0) : null; }
    // Backward compatibility - sets first food item
    public void setFood(Food food) { 
        if (foods == null) {
            foods = new java.util.ArrayList<>();
        }
        if (foods.isEmpty()) {
            foods.add(food);
        } else {
            foods.set(0, food);
        }
    }
    public int getGridSize() { return gridSize; }
    public void setGridSize(int gridSize) { this.gridSize = gridSize; }
    public boolean isGameOver() { return gameOver; }
    public void setGameOver(boolean gameOver) { this.gameOver = gameOver; }
    public boolean isGameStarted() { return gameStarted; }
    public void setGameStarted(boolean gameStarted) { this.gameStarted = gameStarted; }
    public int getTick() { return tick; }
    public void setTick(int tick) { this.tick = tick; }
    public int getCountdown() { return countdown; }
    public void setCountdown(int countdown) { this.countdown = countdown; }
    public long getRoundDurationMs() { return roundDurationMs; }
    public void setRoundDurationMs(long roundDurationMs) { this.roundDurationMs = roundDurationMs; }
    
    // Speed boost features
    private int boostCoins;
    private boolean snakeSpeedBoost;
    private long speedBoostExpireTime;

    public int getBoostCoins() { return boostCoins; }
    public void setBoostCoins(int boostCoins) { this.boostCoins = boostCoins; }
    public boolean isSnakeSpeedBoost() { return snakeSpeedBoost; }
    public void setSnakeSpeedBoost(boolean snakeSpeedBoost) { this.snakeSpeedBoost = snakeSpeedBoost; }
    public long getSpeedBoostExpireTime() { return speedBoostExpireTime; }
    public void setSpeedBoostExpireTime(long speedBoostExpireTime) { this.speedBoostExpireTime = speedBoostExpireTime; }
}
