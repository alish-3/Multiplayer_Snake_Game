package com.snake.game.model;

import java.util.List;

public class GameState {
    private List<Snake> snakes;
    private Food food;
    private int gridSize;
    private boolean gameOver;
    private boolean gameStarted;
    private int tick;
    private int countdown;

    public GameState() {}

    public GameState(List<Snake> snakes, Food food, int gridSize, boolean gameOver, boolean gameStarted, int tick) {
        this.snakes = snakes;
        this.food = food;
        this.gridSize = gridSize;
        this.gameOver = gameOver;
        this.gameStarted = gameStarted;
        this.tick = tick;
        this.countdown = -1;
    }

    public List<Snake> getSnakes() { return snakes; }
    public void setSnakes(List<Snake> snakes) { this.snakes = snakes; }
    public Food getFood() { return food; }
    public void setFood(Food food) { this.food = food; }
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
}
