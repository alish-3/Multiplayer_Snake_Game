package com.snake.game.model;

public class Food {
    private int x;
    private int y;
    private int value;
    private String type;

    public Food() {
        this.value = 1;
        this.type = "NORMAL";
    }

    public Food(int x, int y) {
        this.x = x;
        this.y = y;
        this.value = 1;
        this.type = "NORMAL";
    }

    public Food(int x, int y, String type) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.value = "GOLDEN".equals(type) ? 3 : 1;
    }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
