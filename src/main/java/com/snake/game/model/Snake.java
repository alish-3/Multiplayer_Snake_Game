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

    public Snake() {
        this.segments = new ArrayList<>();
        this.score = 0;
        this.alive = true;
        this.ready = false;
        this.direction = "RIGHT";
        this.nextDirection = "RIGHT";
        this.lastActivity = System.currentTimeMillis();
        this.disconnectedSince = 0;
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
        switch (direction) {
            case "UP": return new Point(head.getX(), head.getY() - 1);
            case "DOWN": return new Point(head.getX(), head.getY() + 1);
            case "LEFT": return new Point(head.getX() - 1, head.getY());
            case "RIGHT": return new Point(head.getX() + 1, head.getY());
            default: return new Point(head.getX() + 1, head.getY());
        }
    }
}
