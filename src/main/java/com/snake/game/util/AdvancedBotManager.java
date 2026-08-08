package com.snake.game.util;

import com.snake.game.model.*;
import java.util.*;

/**
 * Bot AI v2 - flood-fill survival + opponent-aware hunting.
 *
 * Per-difficulty behavior:
 *  - EASY:       naive escape checks only, weak food sense, frequent real
 *                mistakes (picks a worse-scoring move on purpose).
 *  - NORMAL:     flood-fill safety (won't casually trap itself), solid food
 *                sense, no hunting.
 *  - HARD:       full safety + efficient food routing + takes free trap/kill
 *                opportunities when they fall in its lap.
 *  - IMPOSSIBLE: full safety (near-zero self-trap chance), optimal food
 *                routing, ACTIVELY hunts and cuts off nearby opponents when
 *                it can do so without risking itself, zero randomness.
 *                Head-on collisions are treated as near-death, not a "win" -
 *                see note on huntingBonus() below for why.
 */
public class AdvancedBotManager {

    private static final int GRID_SIZE = 30;
    private static final Random RANDOM = new Random();

    private AdvancedBotManager() {}

    public static void updateAdvancedBots(Room room, GameState state, List<Snake> snakes) {
        if (state == null || state.isGameOver() || !state.isGameStarted()) return;
        List<Food> foods = state.getFoods();
        synchronized (room) {
            for (Snake bot : snakes) {
                if (!bot.isBot() || !bot.isAlive()) continue;
                Difficulty diff = Difficulty.from(bot.getBotDifficulty());
                bot.setNextDirection(decide(bot, snakes, foods, diff));
            }
        }
    }

    // ---------------------------------------------------------------
    // Difficulty profile
    // ---------------------------------------------------------------
    private enum Difficulty {
        EASY(0.35, false, 0.25, false, 0.2),
        NORMAL(1.0, true, 0.08, false, 0.6),
        HARD(1.0, true, 0.02, true, 0.9),
        IMPOSSIBLE(1.0, true, 0.0, true, 1.0);

        final double foodWeight;
        final boolean useFloodFill;
        final double mistakeChance;
        final boolean hunt;
        final double safetyScale; // how strongly self-trap risk is punished

        Difficulty(double foodWeight, boolean useFloodFill, double mistakeChance, boolean hunt, double safetyScale) {
            this.foodWeight = foodWeight;
            this.useFloodFill = useFloodFill;
            this.mistakeChance = mistakeChance;
            this.hunt = hunt;
            this.safetyScale = safetyScale;
        }

        static Difficulty from(String s) {
            if (s == null) return NORMAL;
            switch (s.toLowerCase()) {
                case "easy": return EASY;
                case "hard": return HARD;
                case "impossible": return IMPOSSIBLE;
                default: return NORMAL;
            }
        }
    }

    // ---------------------------------------------------------------
    // Core decision
    // ---------------------------------------------------------------
    private static String decide(Snake bot, List<Snake> snakes, List<Food> foods, Difficulty diff) {
        Point head = bot.getHead();
        if (head == null) return bot.getDirection();

        List<String> legal = legalMoves(bot);
        if (legal.isEmpty()) return bot.getDirection();

        Set<Point> occupied = collectOccupied(bot, snakes);
        Map<Point, Double> predictedOpponentHeads = predictOpponentHeads(bot, snakes);

        List<ScoredMove> scored = new ArrayList<>();
        for (String dir : legal) {
            Point next = step(head, dir);
            if (next == null || isWall(next)) continue;
            double score = evaluate(bot, next, snakes, foods, occupied, predictedOpponentHeads, diff);
            scored.add(new ScoredMove(dir, score));
        }

        if (scored.isEmpty()) return bot.getDirection(); // no legal non-wall move; direction irrelevant

        scored.sort((a, b) -> Double.compare(b.score, a.score));

        // Lower difficulties occasionally take a real (non-best) move so
        // they stay beatable instead of playing at engine-optimal level.
        if (diff.mistakeChance > 0 && scored.size() > 1 && RANDOM.nextDouble() < diff.mistakeChance) {
            return scored.get(1 + RANDOM.nextInt(scored.size() - 1)).dir;
        }

        return scored.get(0).dir;
    }

    private static List<String> legalMoves(Snake bot) {
        String[] dirs = {"UP", "DOWN", "LEFT", "RIGHT"};
        String current = bot.getDirection();
        List<String> out = new ArrayList<>();
        for (String d : dirs) {
            if (bot.getSegments().size() > 1 && isReverse(d, current)) continue;
            out.add(d);
        }
        return out;
    }

    // ---------------------------------------------------------------
    // Move evaluation
    // ---------------------------------------------------------------
    private static double evaluate(Snake bot, Point next, List<Snake> snakes, List<Food> foods,
                                    Set<Point> occupied, Map<Point, Double> predictedOpponentHeads, Difficulty diff) {

        // 1) Guaranteed-death filter: stepping onto an OPPONENT's body cell is
        //    instant death this tick. Our own body is NOT deadly (hybrid .io
        //    rule) so it never appears in `occupied`.
        if (occupied.contains(next)) {
            return -1_000_000;
        }

        double score = 0;

        // 2) Head-on avoidance. In this engine two snakes stepping onto the
        //    same cell BOTH die - it's a mutual kill, never a genuine win.
        //    So a "smart" bot should dodge these, not seek them, at every
        //    difficulty (scaled down a bit for easy/normal so they still
        //    occasionally blunder into one).
        if (predictedOpponentHeads.containsKey(next)) {
            double weight = predictedOpponentHeads.get(next);
            score -= 700_000 * weight * (0.35 + 0.65 * diff.safetyScale);
        }

        // 3) Survival - flood-fill reachable space after this move.
        int mySpace;
        if (diff.useFloodFill) {
            Set<Point> blocked = new HashSet<>(occupied);
            blocked.remove(next);
            mySpace = floodFill(next, blocked, bot.getSegments().size() + 40);
        } else {
            mySpace = countImmediateEscapes(next, occupied);
        }
        int needed = Math.max(4, bot.getSegments().size() / 2);
        if (mySpace < needed) {
            score -= (needed - mySpace) * 3000.0 * diff.safetyScale;
        }
        score += Math.min(mySpace, 150) * 12;

        // 4) Food - spread out instead of every bot racing for the same
        //    pellet. A food the bot is closest to (compared to every other
        //    alive snake) is "claimed" and worth far more than a food some
        //    other bot can reach sooner.
        if (foods != null) {
            for (Food f : foods) {
                int myDist = manhattan(next, f.getX(), f.getY());
                boolean claimed = true;
                for (Snake other : snakes) {
                    if (other == bot || !other.isAlive()) continue;
                    Point otherHead = other.getHead();
                    if (otherHead == null) continue;
                    if (manhattan(otherHead, f.getX(), f.getY()) < myDist) {
                        claimed = false;
                        break;
                    }
                }
                score += (claimed ? 2000.0 : 120.0) * diff.foodWeight * f.getValue() / (myDist + 1.0);
                if (myDist == 0) score += 6000 * diff.foodWeight;
            }
        }

        // 4b) Crowding / body-density penalty (multi-opponent games only).
        //     Sitting next to a pile of opponent heads and bodies is exactly
        //     how FFA bots ball up and mutually kill. Push toward open space;
        //     this is left out of 1v1 so the bot can still close in and hunt.
        if (countAliveOpponents(bot, snakes) >= 2) {
            int crowd = 0;
            for (Snake other : snakes) {
                if (other == bot || !other.isAlive()) continue;
                for (Point seg : other.getSegments()) {
                    if (chebyshev(next, seg) <= 2) crowd++;
                }
            }
            score -= crowd * crowd * 8_000 * diff.safetyScale;

            for (Snake other : snakes) {
                if (other == bot || !other.isAlive()) continue;
                Point otherHead = other.getHead();
                if (otherHead == null) continue;
                int headDist = manhattan(next, otherHead.getX(), otherHead.getY());
                if (headDist <= 3) score -= 15_000 * diff.safetyScale * (4 - headDist);
            }
        }

        // 5) Hunting / cutting off opponents (hard & impossible only).
        if (diff.hunt) {
            score += huntingBonus(bot, next, snakes, occupied, diff);
        }

        // 6) Personality noise - zero for impossible so it plays at its ceiling.
        if (diff != Difficulty.IMPOSSIBLE) {
            score += RANDOM.nextDouble() * (diff == Difficulty.EASY ? 200 : 40);
        }

        return score;
    }

    /**
     * Rewards moves that shrink an opponent's reachable space (walling them
     * in / cutting off their escape) WITHOUT reducing our own safety below a
     * safe threshold. This is the bot's real "kill" mechanism - it never
     * chases head-on collisions because those are mutual deaths here, not
     * wins. A true 1v1v1v1 mindset: prefer moves that hurt the opponent's
     * options over moves that only help us, when both are otherwise safe.
     */
    private static double huntingBonus(Snake bot, Point myNext, List<Snake> snakes, Set<Point> occupied, Difficulty diff) {
        double bonus = 0;
        int myLen = bot.getSegments().size();

        int nOpp = 0;
        for (Snake s : snakes) {
            if (s != bot && s.isAlive()) nOpp++;
        }
        // In a 4-player FFA every bot walling in every other bot is exactly
        // how they converge and mutually kill, so hunting is dangerous. In
        // 1v1 it is a strong, safe tactic - keep it at full strength there.
        boolean crowd = nOpp >= 2;

        for (Snake opp : snakes) {
            if (opp == bot || !opp.isAlive()) continue;
            Point oppHead = opp.getHead();
            if (oppHead == null) continue;

            int distToOpp = manhattan(myNext, oppHead.getX(), oppHead.getY());
            if (distToOpp > 9) continue; // only worth planning against nearby threats

            if (crowd) {
                // Never try to cut off a bigger snake in a crowd - it can
                // simply outlast us while a third party picks us off.
                if (opp.getSegments().size() > myLen) continue;
                // Skipping entirely while any other opponent lurks nearby:
                // walling in one snake when another one watches is suicide.
                boolean thirdNearby = false;
                for (Snake other : snakes) {
                    if (other == bot || other == opp || !other.isAlive()) continue;
                    Point otherHead = other.getHead();
                    if (otherHead == null) continue;
                    if (manhattan(myNext, otherHead.getX(), otherHead.getY()) <= 6) {
                        thirdNearby = true;
                        break;
                    }
                }
                if (thirdNearby) continue;
            }

            Set<Point> before = new HashSet<>(occupied);
            int oppSpaceBefore = floodFill(oppHead, before, 200);

            Set<Point> after = new HashSet<>(occupied);
            after.add(myNext);
            int oppSpaceAfter = floodFill(oppHead, after, 200);

            int reduction = oppSpaceBefore - oppSpaceAfter;
            if (reduction > 0) {
                // Cutting off a same-size-or-smaller opponent is safer and
                // more decisive than poking at a bigger one.
                double sizeFactor = opp.getSegments().size() <= myLen ? 1.3 : 0.75;
                bonus += reduction * 20 * sizeFactor * diff.foodWeight;
            }
            // Finishing-move bonus: opponent is down to almost no room.
            if (oppSpaceAfter <= 3 && oppSpaceBefore > 3) {
                bonus += 3500;
            }
        }
        if (crowd) bonus *= 0.25;
        return bonus;
    }

    // ---------------------------------------------------------------
    // Flood fill (BFS reachable free-cell count, capped for performance)
    // ---------------------------------------------------------------
    private static int floodFill(Point start, Set<Point> blocked, int cap) {
        if (start == null || isWall(start) || blocked.contains(start)) return 0;
        boolean[][] visited = new boolean[GRID_SIZE][GRID_SIZE];
        Deque<Point> queue = new ArrayDeque<>();
        queue.add(start);
        visited[start.getX()][start.getY()] = true;
        int count = 0;
        int[] dx = {0, 0, -1, 1};
        int[] dy = {-1, 1, 0, 0};

        while (!queue.isEmpty() && count < cap) {
            Point p = queue.poll();
            count++;
            for (int i = 0; i < 4; i++) {
                int nx = p.getX() + dx[i];
                int ny = p.getY() + dy[i];
                if (nx < 0 || nx >= GRID_SIZE || ny < 0 || ny >= GRID_SIZE) continue;
                if (visited[nx][ny]) continue;
                Point np = new Point(nx, ny);
                if (blocked.contains(np)) continue;
                visited[nx][ny] = true;
                queue.add(np);
            }
        }
        return count;
    }

    private static int countImmediateEscapes(Point head, Set<Point> occupied) {
        int routes = 0;
        for (String dir : new String[]{"UP", "DOWN", "LEFT", "RIGHT"}) {
            Point n = step(head, dir);
            if (n != null && !isWall(n) && !occupied.contains(n)) routes++;
        }
        return routes;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private static Set<Point> collectOccupied(Snake bot, List<Snake> snakes) {
        Set<Point> occupied = new HashSet<>();
        for (Snake s : snakes) {
            if (!s.isAlive()) continue;
            // Crossing your OWN body is allowed (hybrid .io rule - see
            // GameEngine: bodyOwner != attacker). So our own segments are
            // never a blocking cell.
            if (s == bot) continue;
            List<Point> segs = s.getSegments();
            // Mirror GameEngine's bodyOccupancy exactly: skip the opponent's
            // head (index 0 - head-ons handled separately). The tail vacates
            // this tick UNLESS the opponent is growing, so a chaser up the
            // same row must treat a growing snake's tail as a body wall.
            int tailIdx = s.getGrowthPoints() > 0 ? segs.size() : segs.size() - 1;
            for (int i = 1; i < Math.max(tailIdx, 1); i++) occupied.add(segs.get(i));
        }
        return occupied;
    }

    /**
     * 3-tick opponent head prediction: assume each opponent keeps its current
     * heading (and stays alive) and return where its head will be 1, 2 and 3
     * ticks from now. Weights decay with distance - a head-on two cells away
     * is nearly certain, three cells away is a maybe. This lets the bot dodge
     * 2-cell head-on runs that a single-tick prediction only sees at the very
     * last moment. If a predicted cell is shared by several ticks the earliest
     * (most certain) prediction wins.
     */
    private static Map<Point, Double> predictOpponentHeads(Snake bot, List<Snake> snakes) {
        Map<Point, Double> predicted = new HashMap<>();
        double[] weights = {1.0, 0.6, 0.35};
        for (Snake s : snakes) {
            if (s == bot || !s.isAlive()) continue;
            Point head = s.getHead();
            if (head == null) continue;
            Point cursor = head;
            for (double w : weights) {
                Point next = step(cursor, s.getDirection());
                if (next == null || isWall(next)) break; // opponent dies first
                predicted.putIfAbsent(next, w);
                cursor = next;
            }
        }
        return predicted;
    }

    private static boolean isWall(Point p) {
        return p.getX() < 0 || p.getX() >= GRID_SIZE || p.getY() < 0 || p.getY() >= GRID_SIZE;
    }

    private static boolean isReverse(String dir, String current) {
        return switch (dir) {
            case "UP" -> "DOWN".equals(current);
            case "DOWN" -> "UP".equals(current);
            case "LEFT" -> "RIGHT".equals(current);
            case "RIGHT" -> "LEFT".equals(current);
            default -> false;
        };
    }

    private static Point step(Point head, String dir) {
        int x = head.getX(), y = head.getY();
        switch (dir) {
            case "UP": y -= 1; break;
            case "DOWN": y += 1; break;
            case "LEFT": x -= 1; break;
            case "RIGHT": x += 1; break;
            default: return null;
        }
        return new Point(x, y);
    }

    private static int manhattan(Point p, int x, int y) {
        return Math.abs(p.getX() - x) + Math.abs(p.getY() - y);
    }

    private static int chebyshev(Point a, Point b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    private static int countAliveOpponents(Snake bot, List<Snake> snakes) {
        int n = 0;
        for (Snake s : snakes) {
            if (s != bot && s.isAlive()) n++;
        }
        return n;
    }

    private static class ScoredMove {
        final String dir;
        final double score;
        ScoredMove(String dir, double score) { this.dir = dir; this.score = score; }
    }
}
