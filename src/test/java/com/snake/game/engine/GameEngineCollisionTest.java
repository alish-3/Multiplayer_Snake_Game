package com.snake.game.engine;

import com.snake.game.model.Point;
import com.snake.game.model.Snake;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.snake.game.engine.GameEngine.resolveHeadBodyCollisions;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic unit tests for {@link GameEngine#resolveHeadBodyCollisions}, focused on the
 * "chase" family of collisions (snakes travelling in the same row/column).
 *
 * <p>Regression for: a chaser landing on a leader's CURRENT head cell escaped collision
 * because occupancy was built from pre-move bodies with heads excluded. After the leader
 * moves, that cell becomes the leader's neck (solid), so the chaser must die. At the same
 * time a chaser following the vacating tail cell must survive, and a mutual head-bump
 * (adjacent heads swapping cells) must kill both.</p>
 */
class GameEngineCollisionTest {

    private static Snake snake(String name, String dir, Point head, Point... rest) {
        Snake s = new Snake(name, "red", head);
        List<Point> segs = new ArrayList<>();
        segs.add(head);
        for (Point p : rest) segs.add(p);
        s.setSegments(segs);
        s.setDirection(dir);
        s.setNextDirection(dir);
        s.setAlive(true);
        s.setGrowthPoints(0);
        return s;
    }

    private static List<Point> step(Snake s) {
        Point h = s.getHead();
        Point next = s.getNextHead();
        int sx = (int) Math.signum(next.getX() - h.getX());
        int sy = (int) Math.signum(next.getY() - h.getY());
        int len = Math.max(Math.abs(next.getX() - h.getX()), Math.abs(next.getY() - h.getY()));
        List<Point> path = new ArrayList<>();
        for (int k = 1; k <= len; k++) {
            path.add(new Point(h.getX() + sx * k, h.getY() + sy * k));
        }
        return path;
    }

    private static void setup(List<Snake> snakes, Map<Snake, Point> nextHeads, Map<Snake, List<Point>> paths) {
        for (Snake s : snakes) {
            nextHeads.put(s, s.getNextHead());
            paths.put(s, step(s));
        }
    }

    @Test
    void tailChaseSurvives() {
        // Leader moving RIGHT; chaser right behind the tail, same row, same speed.
        Snake leader = snake("L", "RIGHT", new Point(5, 10), new Point(4, 10), new Point(3, 10));
        Snake chaser = snake("C", "RIGHT", new Point(2, 10), new Point(1, 10), new Point(0, 10));

        List<Snake> snakes = new ArrayList<>();
        snakes.add(leader);
        snakes.add(chaser);
        Map<Snake, Point> nextHeads = new HashMap<>();
        Map<Snake, List<Point>> paths = new HashMap<>();
        setup(snakes, nextHeads, paths);

        var dead = resolveHeadBodyCollisions(snakes, nextHeads, paths, new ArrayList<>());

        assertTrue(leader.isAlive(), "leader must survive");
        assertTrue(chaser.isAlive(), "tail-following chaser must survive (tail vacates)");
        assertTrue(dead.isEmpty(), "no deaths expected on a clean tail chase");
    }

    @Test
    void chaserIntoCurrentHeadCellIsHeadOn() {
        // L moves RIGHT to (6,10), C moves LEFT to (5,10): each head steps into the cell the
        // other head currently occupies. Before the fix the pre-move occupancy excluded heads,
        // so BOTH survived and the chasing head rode inside the leader's neck indefinitely.
        // The post-move occupancy makes each former head cell the other's neck, so both die.
        Snake leader = snake("L", "RIGHT", new Point(5, 10), new Point(4, 10), new Point(3, 10));
        Snake chaser = snake("C", "LEFT", new Point(6, 10), new Point(7, 10), new Point(8, 10));
        Snake escort = snake("E", "DOWN", new Point(6, 5), new Point(6, 6), new Point(6, 7));

        List<Snake> snakes = new ArrayList<>();
        snakes.add(leader);
        snakes.add(chaser);
        snakes.add(escort);
        Map<Snake, Point> nextHeads = new HashMap<>();
        Map<Snake, List<Point>> paths = new HashMap<>();
        setup(snakes, nextHeads, paths);

        resolveHeadBodyCollisions(snakes, nextHeads, paths, new ArrayList<>());

        assertFalse(leader.isAlive(), "leader steps into the chaser's current head cell - mutual head-on");
        assertFalse(chaser.isAlive(), "chaser must NOT ride into the leader's current head cell");
        assertTrue(escort.isAlive(), "unrelated snake survives");
    }

    @Test
    void growingLeaderRetainsTailSoChaserDies() {
        // Leader is GROWING (growthPoints > 0) so its tail does NOT vacate this tick;
        // a chaser riding up that final cell must die.
        Snake leader = snake("L", "RIGHT", new Point(5, 10), new Point(4, 10), new Point(3, 10));
        leader.setGrowthPoints(2);
        Snake chaser = snake("C", "RIGHT", new Point(2, 10), new Point(1, 10), new Point(0, 10));

        List<Snake> snakes = new ArrayList<>();
        snakes.add(leader);
        snakes.add(chaser);
        Map<Snake, Point> nextHeads = new HashMap<>();
        Map<Snake, List<Point>> paths = new HashMap<>();
        setup(snakes, nextHeads, paths);

        resolveHeadBodyCollisions(snakes, nextHeads, paths, new ArrayList<>());

        assertTrue(leader.isAlive(), "leader survives");
        assertFalse(chaser.isAlive(), "chaser must not ride into a growing snake's retained tail");
    }

    @Test
    void adjacentHeadSwapIsMutualKill() {
        // L at (5,10) moving RIGHT, C at (6,10) moving LEFT -> next cells are (6,10)/(5,10):
        // each head swaps into the other's CURRENT head cell (which becomes the other's neck).
        Snake leader = snake("L", "RIGHT", new Point(5, 10), new Point(4, 10), new Point(3, 10));
        Snake chaser = snake("C", "LEFT", new Point(6, 10), new Point(7, 10), new Point(8, 10));

        List<Snake> snakes = new ArrayList<>();
        snakes.add(leader);
        snakes.add(chaser);
        Map<Snake, Point> nextHeads = new HashMap<>();
        Map<Snake, List<Point>> paths = new HashMap<>();
        setup(snakes, nextHeads, paths);

        resolveHeadBodyCollisions(snakes, nextHeads, paths, new ArrayList<>());

        assertFalse(leader.isAlive(), "head-on swap kills the leader");
        assertFalse(chaser.isAlive(), "head-on swap kills the chaser");
    }
}