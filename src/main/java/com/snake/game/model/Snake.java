package com.snake.game.model;

import java.util.ArrayList;
import java.util.List;

public class Snake {
    private String name;
    private String color;
    private List<Point> segments;
    private String direction;
    private String nextDirection;
    private int score;
    private boolean alive;
    private boolean ready;
    private long lastActivity;
    private long disconnectedSince;
    private static final long DISCONNECT_TIMEOUT = 10_000;

    // Reconnection support
    private String reconnectToken;
    private long lastDisconnectTime;

    // Speed boost fields
    private boolean speedBoostActive = false;
    private long speedBoostEndTime = 0;
    private float speedMultiplier = 1.0f;
    private int growthPoints;

    // Hybrid (.io style) fields
    private boolean bot = false;
    private boolean boosting = false;
    private String botDifficulty = "normal";
    private String botStrategy = "balanced";

    public Snake() {
        this.segments = new ArrayList<>();
        this.score = 0;
        this.alive = true;
        this.ready = false;
        this.direction = "RIGHT";
        this.nextDirection = "RIGHT";
        this.lastActivity = System.currentTimeMillis();
        this.disconnectedSince = 0;
        this.growthPoints = 0;
    }

    public void touch() {
        this.lastActivity = System.currentTimeMillis();
    }

    public boolean isDisconnected() {
        if (disconnectedSince == 0) return false;
        return System.currentTimeMillis() - disconnectedSince > DISCONNECT_TIMEOUT;
    }

    public void markDisconnected() {
        this.disconnectedSince = System.currentTimeMillis();
    }

    public void clearDisconnected() {
        this.disconnectedSince = 0;
    }

    public long getLastActivity() { return lastActivity; }
    public long getDisconnectedSince() { return disconnectedSince; }

    // Reconnection token methods
    public String getReconnectToken() { return reconnectToken; }
    public void setReconnectToken(String reconnectToken) { this.reconnectToken = reconnectToken; }
    public long getLastDisconnectTime() { return lastDisconnectTime; }
    public void setLastDisconnectTime(long lastDisconnectTime) { this.lastDisconnectTime = lastDisconnectTime; }

    public Snake(String name, String color, Point start) {
        this();
        this.name = name;
        this.color = color;
        this.segments.add(start);
        this.segments.add(new Point(start.getX() - 1, start.getY()));
        this.segments.add(new Point(start.getX() - 2, start.getY()));
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public List<Point> getSegments() { return segments; }
    public void setSegments(List<Point> segments) { this.segments = segments; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getNextDirection() { return nextDirection; }
    public void setNextDirection(String nextDirection) { this.nextDirection = nextDirection; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public int getGrowthPoints() { return growthPoints; }
    public void setGrowthPoints(int growthPoints) { this.growthPoints = growthPoints; }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }

    public Point getHead() {
        return segments.isEmpty() ? null : segments.get(0);
    }

    public void grow() {
        Point tail = segments.get(segments.size() - 1);
        segments.add(new Point(tail.getX(), tail.getY()));
    }

    public Point getNextHead() {
        Point head = getHead();
        if (head == null) return null;
        int step = Math.round(speedMultiplier);
        if (step < 1) step = 1;
        switch (direction) {
            case "UP": return new Point(head.getX(), head.getY() - step);
            case "DOWN": return new Point(head.getX(), head.getY() + step);
            case "LEFT": return new Point(head.getX() - step, head.getY());
            case "RIGHT": return new Point(head.getX() + step, head.getY());
            default: return new Point(head.getX() + step, head.getY());
        }
    }

    public boolean isSpeedBoostActive() { return speedBoostActive; }
    public void setSpeedBoostActive(boolean speedBoostActive) { this.speedBoostActive = speedBoostActive; }
    public long getSpeedBoostEndTime() { return speedBoostEndTime; }
    public void setSpeedBoostEndTime(long speedBoostEndTime) { this.speedBoostEndTime = speedBoostEndTime; }
    public float getSpeedMultiplier() { return speedMultiplier; }
    public void setSpeedMultiplier(float speedMultiplier) { this.speedMultiplier = speedMultiplier; }
    public boolean isBot() { return bot; }
    public void setBot(boolean bot) { this.bot = bot; }
    public boolean isBoosting() { return boosting; }
    public void setBoosting(boolean boosting) { this.boosting = boosting; }
    public String getBotDifficulty() { return botDifficulty; }
    public void setBotDifficulty(String botDifficulty) { this.botDifficulty = botDifficulty; }
    public String getBotStrategy() { return botStrategy; }
    public void setBotStrategy(String botStrategy) { this.botStrategy = botStrategy; }
}
