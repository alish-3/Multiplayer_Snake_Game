package com.snake.game.engine;

import com.snake.game.model.Food;
import com.snake.game.model.GameState;
import com.snake.game.model.Point;
import com.snake.game.model.Room;
import com.snake.game.model.Snake;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.snake.game.engine.GameEngine.applyHybridBoost;
import static com.snake.game.engine.GameEngine.applyScoreMilestoneCoins;
import static com.snake.game.engine.GameEngine.timedBoostCoinReward;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic unit tests for the boost-coin economy and the hybrid (slither.io-style)
 * boost mechanics in {@link GameEngine}:
 *
 * <ul>
 *   <li>{@link GameEngine#timedBoostCoinReward} - the +10 coins every 5 seconds reward</li>
 *   <li>{@link GameEngine#applyScoreMilestoneCoins} - the +50 coins per score milestone (100/500/1000)</li>
 *   <li>{@link GameEngine#applyHybridBoost} - 2x speed while shedding tail segments as food</li>
 * </ul>
 *
 * <p>All tests are fully deterministic: no timers, no sleeps, no randomness. They lock in the
 * exact boundary conditions (5s reward intervals, milestone thresholds, boost min-length,
 * shed cadence, food cap) so a future refactor cannot silently change the coin economy or
 * the boost rules.</p>
 */
class GameEngineBoostTest {

    private static final int MAX_FOODS = 80; // must match GameEngine.MAX_FOODS

    /** Snake constructor creates 3 segments: head at (10,10), then (9,10), (8,10). */
    private static Snake newSnake(String name, String color, int length) {
        Snake s = new Snake(name, color, new Point(10, 10));
        while (s.getSegments().size() < length) {
            s.getSegments().add(new Point(10 + s.getSegments().size(), 10));
        }
        return s;
    }

    private static GameState newStateWithTick(int tick) {
        GameState state = new GameState();
        state.setTick(tick);
        return state;
    }

    private static List<Food> fullFoodList() {
        List<Food> foods = new ArrayList<>();
        for (int i = 0; i < MAX_FOODS; i++) {
            foods.add(new Food(i, i));
        }
        return foods;
    }

    @Test
    void initGameStateGrantsInitialBoostCoins() {
        Room room = new Room(); // empty players list
        GameEngine.initGameState(room);
        GameState state = room.getGameState();

        assertEquals(20, state.getBoostCoins(), "a fresh round must start with 20 boost coins");
        assertEquals(4, state.getFoods().size(), "a fresh round must spawn exactly 4 foods");
    }

    @Test
    void timedRewardNotBeforeFiveSeconds() {
        assertEquals(0, timedBoostCoinReward(0, 0, 0), "no reward at t=0");
        assertEquals(0, timedBoostCoinReward(4999, 0, 0), "no reward before 5s of play");
        assertEquals(10, timedBoostCoinReward(5000, 0, 0), "first reward exactly at 5s");
    }

    @Test
    void timedRewardAtMostOnceEveryFiveSeconds() {
        assertEquals(10, timedBoostCoinReward(5000, 0, 0), "first reward at 5s");

        assertEquals(0, timedBoostCoinReward(9000, 0, 5000),
                "only 4s since the last reward - nothing granted");
        assertEquals(10, timedBoostCoinReward(10000, 0, 5000),
                "exactly 5s after the last reward - granted again");
    }

    @Test
    void timedRewardRequiresFiveSecondsSinceGameStart() {
        assertEquals(0, timedBoostCoinReward(10000, 6000, 0),
                "only 4s of gameplay since the round started - no reward");
        assertEquals(10, timedBoostCoinReward(11000, 6000, 0),
                "5s of gameplay elapsed - reward granted");
    }

    @Test
    void milestoneAwardedOncePerMilestone() {
        Snake snake = new Snake("A", "blue", new Point(10, 10));
        GameState state = new GameState();
        state.setBoostCoins(20);
        Map<String, Long> milestones = new HashMap<>();

        snake.setScore(100);
        assertEquals(50, applyScoreMilestoneCoins(state, milestones, snake),
                "crossing the 100 milestone awards 50 coins");
        assertEquals(70, state.getBoostCoins(), "20 + 50 coins after the first milestone");

        assertEquals(0, applyScoreMilestoneCoins(state, milestones, snake),
                "same score awards nothing a second time");
        assertEquals(70, state.getBoostCoins(), "coins unchanged after the repeated call");

        snake.setScore(500);
        assertEquals(50, applyScoreMilestoneCoins(state, milestones, snake),
                "crossing the 500 milestone awards 50 coins");
        assertEquals(120, state.getBoostCoins(), "70 + 50 coins after the second milestone");

        snake.setScore(1000);
        assertEquals(50, applyScoreMilestoneCoins(state, milestones, snake),
                "crossing the 1000 milestone awards 50 coins");
        assertEquals(170, state.getBoostCoins(), "120 + 50 coins after the third milestone");

        snake.setScore(2000);
        assertEquals(0, applyScoreMilestoneCoins(state, milestones, snake),
                "no further awards past 1000 - all milestones already recorded");
        assertEquals(170, state.getBoostCoins(), "coins unchanged past the last milestone");
    }

    @Test
    void milestoneJumpAwardsAllReachedMilestones() {
        // Regression for the existing "jump" behavior: a snake that leaps past several
        // milestones in one call gets all of them paid out at once.
        Snake snake = new Snake("A", "blue", new Point(10, 10));
        GameState state = new GameState();
        Map<String, Long> milestones = new HashMap<>();
        snake.setScore(1200);

        int awarded = applyScoreMilestoneCoins(state, milestones, snake);

        assertEquals(150, awarded, "jumping to 1200 crosses all three milestones (3 x 50)");
        assertEquals(150, state.getBoostCoins(), "all crossed milestones paid in a single call");
        assertEquals(1000L, milestones.get("A_blue"),
                "tracking map records the highest milestone reached");
    }

    @Test
    void milestoneTrackingIsPerSnake() {
        // Snakes are tracked by their name_color key, so two snakes with the same name
        // but different colors must not share milestone progress.
        GameState state = new GameState();
        Map<String, Long> milestones = new HashMap<>();

        Snake blue = new Snake("A", "blue", new Point(10, 10));
        Snake red = new Snake("A", "red", new Point(20, 10));
        blue.setScore(100);
        red.setScore(100);

        assertEquals(50, applyScoreMilestoneCoins(state, milestones, blue),
                "first snake (A_blue) awarded its 100 milestone");
        assertEquals(50, applyScoreMilestoneCoins(state, milestones, red),
                "second snake (A_red) awarded its own 100 milestone - keys are name_color");
        assertEquals(100, state.getBoostCoins(), "both snakes paid independently (50 + 50)");
        assertEquals(100L, milestones.get("A_blue"), "A_blue tracks its own milestone");
        assertEquals(100L, milestones.get("A_red"), "A_red tracks its own milestone");
    }

    @Test
    void hybridBoostSetsDoubleSpeedAndShedsEveryTwoTicks() {
        Snake snake = newSnake("S", "green", 8);
        snake.setBoosting(true);
        GameState state = newStateWithTick(2); // even tick -> shed
        List<Food> foods = new ArrayList<>();

        assertTrue(applyHybridBoost(snake, state, foods), "shedding occurs on an even tick");
        assertEquals(2.0f, snake.getSpeedMultiplier(), 0.0f, "boosting snake runs at 2x speed");
        assertEquals(7, snake.getSegments().size(), "one tail segment shed");
        assertEquals(1, foods.size(), "shed segment becomes food");
        assertEquals("NORMAL", foods.get(0).getType(), "shed food is NORMAL type");

        state.setTick(3); // odd tick -> no shed
        assertFalse(applyHybridBoost(snake, state, foods), "no shedding on an odd tick");
        assertEquals(7, snake.getSegments().size(), "length unchanged on an odd tick");
        assertEquals(1, foods.size(), "no new food on an odd tick");
    }

    @Test
    void hybridBoostDisabledAtMinLength() {
        Snake snake = newSnake("S", "green", 5); // exactly BOOST_MIN_LENGTH
        snake.setBoosting(true);
        GameState state = newStateWithTick(2);

        assertFalse(applyHybridBoost(snake, state, new ArrayList<>()),
                "boost must turn off at minimum length");
        assertFalse(snake.isBoosting(), "boosting flag cleared");
        assertEquals(1.0f, snake.getSpeedMultiplier(), 0.0f, "multiplier reset to 1.0");
        assertEquals(5, snake.getSegments().size(), "no shedding when boost is disabled");
    }

    @Test
    void deadSnakeCannotBoost() {
        Snake snake = newSnake("S", "green", 8);
        snake.setBoosting(true);
        snake.setSpeedMultiplier(2.0f);
        snake.setAlive(false);
        GameState state = newStateWithTick(2);

        assertFalse(applyHybridBoost(snake, state, new ArrayList<>()),
                "dead snake sheds nothing");
        assertFalse(snake.isBoosting(), "dead snake force-unboosted");
        assertEquals(1.0f, snake.getSpeedMultiplier(), 0.0f, "dead snake multiplier reset to 1.0");
    }

    @Test
    void notBoostingResetsMultiplier() {
        Snake snake = newSnake("S", "green", 8);
        snake.setBoosting(false);
        snake.setSpeedMultiplier(2.0f); // stale multiplier from a previous boost
        GameState state = newStateWithTick(2);

        applyHybridBoost(snake, state, new ArrayList<>());

        assertEquals(1.0f, snake.getSpeedMultiplier(), 0.0f,
                "a non-boosting snake's multiplier must be reset to 1.0");
    }

    @Test
    void maxFoodCapStopsShedding() {
        // The cap check happens AFTER the tail is removed: the segment is shed either way,
        // but the food is only spawned when the list is below MAX_FOODS.
        Snake snake = newSnake("S", "green", 8);
        snake.setBoosting(true);
        GameState state = newStateWithTick(2);
        List<Food> foods = fullFoodList(); // exactly MAX_FOODS

        assertFalse(applyHybridBoost(snake, state, foods),
                "no food is added once the food cap is reached");
        assertEquals(2.0f, snake.getSpeedMultiplier(), 0.0f, "boosting speed is unaffected by the cap");
        assertEquals(7, snake.getSegments().size(),
                "the tail segment is still shed even when the cap blocks the food spawn");
        assertEquals(MAX_FOODS, foods.size(), "food list stays at the cap");
    }
}
