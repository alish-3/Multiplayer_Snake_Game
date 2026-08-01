package com.snake.game.engine;

import com.snake.game.model.Point;
import com.snake.game.model.Snake;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Deterministic unit tests for the gated body-growth logic in {@link GameEngine#applyGatedGrowth}.
 *
 * <p>In the game loop the head segment is moved onto the food cell BEFORE {@code applyGatedGrowth}
 * is invoked, so "grow" means keeping the tail (net +1 segment) and "no growth" means removing the
 * tail (net 0 segments). The {@link #eat} helper reproduces that exact sequence so segment counts
 * here match in-game behavior.</p>
 */
class GameEngineGrowthTest {

    private static final int INITIAL_LENGTH = 3; // Snake(name, color, start) creates 3 segments

    private static Snake newTestSnake(int score, int growthPoints) {
        Snake snake = new Snake("TEST_SNAKE", "green", new Point(10, 10));
        snake.setScore(score);
        snake.setGrowthPoints(growthPoints);
        return snake;
    }

    /** Simulates one tick's food consumption: head advances first, then gated growth applies. */
    private static void eat(Snake snake, int foodValue) {
        Point head = snake.getHead();
        snake.getSegments().add(0, new Point(head.getX() + 1, head.getY()));
        GameEngine.applyGatedGrowth(snake, foodValue);
    }

    @Test
    void belowGateEveryFoodGrows() {
        Snake snake = newTestSnake(0, 0);

        for (int i = 0; i < 50; i++) {
            eat(snake, 1);
        }

        assertEquals(50, snake.getScore(), "score should be 50 after 50 normal foods");
        assertEquals(INITIAL_LENGTH + 50, snake.getSegments().size(),
                "every food below the gate must grow exactly one segment");
        assertEquals(0, snake.getGrowthPoints(),
                "growth points reset after each grow below the gate");
    }

    @Test
    void goldenFoodBelowGate() {
        Snake snake = newTestSnake(2, 0);

        eat(snake, 3);

        assertEquals(5, snake.getScore(), "score should be 5 after one golden food (value 3)");
        assertEquals(INITIAL_LENGTH + 1, snake.getSegments().size(),
                "a single golden food below the gate must grow exactly one segment");
        assertEquals(0, snake.getGrowthPoints(),
                "pending 3 % 1 must be 0 below the gate");
    }

    @Test
    void crossingGateAccumulates() {
        Snake snake = newTestSnake(99, 0);

        // 99 + 3 = 102 (> 100): the gate now applies, pending 3 < 4 -> no growth, points carried over
        eat(snake, 3);
        assertEquals(102, snake.getScore(), "score should be 102 after the first golden food");
        assertEquals(INITIAL_LENGTH, snake.getSegments().size(),
                "no growth while accumulated points stay below the gate interval");
        assertEquals(3, snake.getGrowthPoints(), "accumulated 3 points must be carried over");

        // 102 + 3 = 105: pending 3 + 3 = 6 >= 4 -> exactly one growth, remainder 2 kept
        eat(snake, 3);
        assertEquals(105, snake.getScore(), "score should be 105 after the second golden food");
        assertEquals(INITIAL_LENGTH + 1, snake.getSegments().size(),
                "exactly one growth once accumulated points reach the gate interval");
        assertEquals(2, snake.getGrowthPoints(), "6 % 4 must leave 2 growth points");
    }

    @Test
    void highScoreGatedLength() {
        Snake snake = newTestSnake(0, 0);

        for (int i = 0; i < 150; i++) {
            eat(snake, 1);
        }

        assertEquals(150, snake.getScore(), "score should be 150 after 150 normal foods");
        // Foods 1-100 all grow (threshold 1). Foods 101-150 are gated: only every 4th
        // accumulated point grows -> 12 of the remaining 50 foods grow. Ungated would be +150.
        assertEquals(INITIAL_LENGTH + 100 + 12, snake.getSegments().size(),
                "first 100 foods each grow; past the gate only every 4th food grows (12 of 50)");
    }

    @Test
    void gateDoesNotResetOnScoreEquality() {
        Snake snake = newTestSnake(99, 0);

        // 99 + 1 = 100: the gate threshold is evaluated on the NEW score, and 100 is NOT > 100,
        // so the threshold is still 1 -> the food grows a segment immediately.
        eat(snake, 1);

        assertEquals(100, snake.getScore(), "score should be exactly 100 after the food");
        assertEquals(INITIAL_LENGTH + 1, snake.getSegments().size(),
                "reaching the gate score exactly must still grow immediately");
        assertEquals(0, snake.getGrowthPoints(), "pending 1 % 1 must be 0");
    }
}
