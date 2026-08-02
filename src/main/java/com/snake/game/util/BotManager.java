package com.snake.game.util;

import com.snake.game.model.Food;
import com.snake.game.model.GameState;
import com.snake.game.model.Point;
import com.snake.game.model.Room;
import com.snake.game.model.Snake;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Server-side bots for the hybrid .io experience. Bots fill rooms to the max
 * player count, auto-ready every round, and play a simple grid AI: head toward
 * food, avoid walls and other snakes' bodies, cross their own body freely, and
 * occasionally boost when food is far away.
 */
public class BotManager {
    private static final String[] BOT_NAMES = {"Bot-1", "Bot-2", "Bot-3", "Bot-4", "Bot-5", "Bot-6"};
    private static final String[] BOT_COLORS = {"#ff6b6b", "#4ecdc4", "#ffe66d", "#a78bfa", "#34d399", "#f472b6"};
    private static final Random RNG = new Random();

    private static final int BOOST_MIN_LENGTH = 5;
    private static final int WALL_PENALTY = 10000;
    private static final int BODY_PENALTY = 9000;

    // Difficulty settings
    private static final class DifficultyConfig {
        double foodWeight;
        double dangerWeight;
        double centerWeight;
        double lookaheadDepth;
        double boostThreshold;
        double reactionSpeed; // 0-1, higher = faster reactions
        boolean perfectAvoidance;
        boolean predictiveMovement;
        
        DifficultyConfig(double foodWeight, double dangerWeight, double centerWeight, 
                        double lookaheadDepth, double boostThreshold, double reactionSpeed,
                        boolean perfectAvoidance, boolean predictiveMovement) {
            this.foodWeight = foodWeight;
            this.dangerWeight = dangerWeight;
            this.centerWeight = centerWeight;
            this.lookaheadDepth = lookaheadDepth;
            this.boostThreshold = boostThreshold;
            this.reactionSpeed = reactionSpeed;
            this.perfectAvoidance = perfectAvoidance;
            this.predictiveMovement = predictiveMovement;
        }
    }
    
    private static final java.util.Map<String, DifficultyConfig> DIFFICULTIES = new java.util.HashMap<>();
    static {
        // Easy: Random-ish movement, poor food targeting, slow reactions
        DIFFICULTIES.put("easy", new DifficultyConfig(
            1.0,    // foodWeight
            0.5,    // dangerWeight  
            0.3,    // centerWeight
            0,      // lookaheadDepth
            0.01,   // boostThreshold
            0.3,    // reactionSpeed
            false,  // perfectAvoidance
            false   // predictiveMovement
        ));
        
        // Normal: Balanced, decent food targeting, moderate danger avoidance
        DIFFICULTIES.put("normal", new DifficultyConfig(
            3.0,    // foodWeight
            1.0,    // dangerWeight
            0.6,    // centerWeight
            1,      // lookaheadDepth
            0.02,   // boostThreshold
            0.6,    // reactionSpeed
            false,  // perfectAvoidance
            false   // predictiveMovement
        ));
        
        // Hard: Aggressive food targeting, good danger avoidance, predicts player movement
        DIFFICULTIES.put("hard", new DifficultyConfig(
            5.0,    // foodWeight
            2.0,    // dangerWeight
            0.8,    // centerWeight
            2,      // lookaheadDepth
            0.05,   // boostThreshold
            0.85,   // reactionSpeed
            true,   // perfectAvoidance
            true    // predictiveMovement
        ));
        
        // Impossible: Perfect play, predicts all outcomes, never makes mistakes
        DIFFICULTIES.put("impossible", new DifficultyConfig(
            10.0,   // foodWeight
            5.0,    // dangerWeight
            1.0,    // centerWeight
            4,      // lookaheadDepth
            0.1,    // boostThreshold
            1.0,    // reactionSpeed
            true,   // perfectAvoidance
            true    // predictiveMovement
        ));
    }

    private BotManager() {}

    /** Fills the room with auto-ready bots up to the room's max player count. */
    public static void fillWithBots(Room room) {
        fillWithBots(room, 3, "normal"); // Default: 3 bots, normal difficulty
    }
    
    /** Fills the room with specified number of bots at given difficulty. */
    public static void fillWithBots(Room room, int botCount, String difficulty, String strategy) {
        synchronized (room) {
            int maxBots = Math.min(botCount, room.getMaxPlayers() - room.getPlayerCount());
            
            // Assign spawn positions & directions based on 4-quadrant layout
            Point[] spawnHeads = { new Point(5, 5), new Point(24, 5), new Point(5, 24), new Point(24, 24) };
            String[] spawnDirs = { "RIGHT", "LEFT", "RIGHT", "LEFT" };
            
            for (int i = 0; i < maxBots; i++) {
                String name = BOT_NAMES[i];
                if (room.getPlayer(name) != null) continue;
                
                // Assign spawn position based on current player count (including bots already added)
                int idx = room.getPlayerCount() % 4;
                Point head = spawnHeads[idx];
                String dir = spawnDirs[idx];
                int dx = dir.equals("RIGHT") ? -1 : 1;
                
                Snake bot = new Snake();
                bot.setName(name);
                bot.setColor(BOT_COLORS[i % BOT_COLORS.length]);
                bot.setBot(true);
                bot.setReady(true);
                bot.setDirection(dir);
                bot.setNextDirection(dir);
                bot.setBotDifficulty(difficulty);
                bot.setBotStrategy(strategy);
                
                // Set initial segments (spawn position)
                List<Point> segments = new ArrayList<>();
                segments.add(head);
                segments.add(new Point(head.getX() + dx, head.getY()));
                segments.add(new Point(head.getX() + 2 * dx, head.getY()));
                bot.setSegments(segments);
                
                room.getPlayers().add(bot);
            }
        }
    }
    
    public static void fillWithBots(Room room, int botCount, String difficulty) {
        fillWithBots(room, botCount, difficulty, "balanced"); // Default strategy
    }

    public static void removeBots(Room room) {
        synchronized (room) {
            room.getPlayers().removeIf(Snake::isBot);
        }
    }

    public static boolean hasOnlyBots(Room room) {
        synchronized (room) {
            if (room.getPlayers().isEmpty()) return false;
            for (Snake s : room.getPlayers()) {
                if (!s.isBot()) return false;
            }
            return true;
        }
    }

    /** One AI decision per bot per tick. Called from GameEngine.tick(). */
    public static void updateBots(Room room, GameState state, List<Snake> snakes) {
        if (state == null || state.isGameOver() || !state.isGameStarted()) return;
        List<Food> foods = state.getFoods();
        synchronized (room) {
            for (Snake bot : snakes) {
                if (!bot.isBot() || !bot.isAlive()) continue;
                decideDirection(bot, foods, snakes, state);
                decideBoost(bot, foods);
            }
        }
    }

    private static void decideDirection(Snake bot, List<Food> foods, List<Snake> snakes, GameState state) {
        Point head = bot.getHead();
        if (head == null) return;
        String[] dirs = {"UP", "DOWN", "LEFT", "RIGHT"};
        
        DifficultyConfig config = DIFFICULTIES.getOrDefault(bot.getBotDifficulty(), DIFFICULTIES.get("normal"));
        
        // Collect all valid directions with scores
        List<DirectionScore> validDirections = new ArrayList<>();
        for (String d : dirs) {
            if (bot.getSegments().size() > 1 && isReverse(d, bot.getDirection())) continue;
            Point next = step(head, d);
            if (next == null) continue;
            double score = scoreCell(bot, next, foods, snakes, state, config);
            validDirections.add(new DirectionScore(d, score));
        }
        
        if (validDirections.isEmpty()) return;
        
        // Find best score
        double bestScore = validDirections.stream().mapToDouble(ds -> ds.score).max().orElse(Double.NEGATIVE_INFINITY);
        
        // Filter to directions within a small epsilon of best score (tie-breaking)
        final double EPSILON = 0.01;
        List<String> bestDirs = validDirections.stream()
            .filter(ds -> ds.score >= bestScore - EPSILON)
            .map(ds -> ds.dir)
            .toList();
        
        // Pick randomly among best directions to avoid getting stuck in circles
        String best = bestDirs.get(RNG.nextInt(bestDirs.size()));
        
        // Apply reaction speed - sometimes keep current direction
        if (RNG.nextDouble() > config.reactionSpeed) {
            bot.setNextDirection(bot.getDirection());
        } else {
            bot.setNextDirection(best);
        }
    }
    
    private static class DirectionScore {
        String dir;
        double score;
        DirectionScore(String dir, double score) {
            this.dir = dir;
            this.score = score;
        }
    }

    private static double scoreCell(Snake bot, Point next, List<Food> foods, List<Snake> snakes, GameState state, DifficultyConfig config) {
        double score = RNG.nextDouble() * 0.4; // jitter so bots don't all path identically

        // Center bias keeps bots off the lethal walls
        score += (15 - Math.abs(next.getX() - 15)) * 0.06 * config.centerWeight;
        score += (15 - Math.abs(next.getY() - 15)) * 0.06 * config.centerWeight;

        // Food attraction (nearest food weighted by distance) - ENHANCED RANGE
        Food nearest = nearestFood(next, foods);
        if (nearest != null) {
            int d = manhattan(next, nearest.getX(), nearest.getY());
            if (d < 25) score += (25 - d) * config.foodWeight * 2.0;
        }

        // Body danger: other snakes' bodies kill (own body is safe to cross)
        for (Snake other : snakes) {
            if (other == bot || !other.isAlive()) continue;
            for (Point seg : other.getSegments()) {
                if (seg.equals(next)) {
                    return -BODY_PENALTY * config.dangerWeight;
                }
            }
            
            // Predictive movement: avoid where other snakes' heads might go
            if (config.predictiveMovement && other.getHead() != null) {
                Point otherHead = other.getHead();
                String otherDir = other.getDirection();
                Point predictedHead = step(otherHead, otherDir);
                if (predictedHead != null && predictedHead.equals(next)) {
                    return -BODY_PENALTY * config.dangerWeight * 0.8;
                }
            }
        }
        
        // Wall avoidance with food awareness
        if (next.getX() < 2 || next.getX() > 27 || next.getY() < 2 || next.getY() > 27) {
            // Check if there's food nearby before applying wall penalty
            Food nearestFood = nearestFood(next, foods);
            if (nearestFood != null) {
                int foodDistance = manhattan(next, nearestFood.getX(), nearestFood.getY());
                if (foodDistance <= 8) {
                    // Food priority: allow moving toward food even near walls
                    // Reduced penalty when food is nearby (within 8 units)
                    score -= WALL_PENALTY * 0.05 * config.dangerWeight;
                } else {
                    // Standard wall penalty when no food nearby
                    score -= WALL_PENALTY * 0.1 * config.dangerWeight;
                }
            } else {
                // Standard wall penalty when no food nearby
                score -= WALL_PENALTY * 0.1 * config.dangerWeight;
            }
        }
        
        // Impossible difficulty: Lookahead simulation
        if (config.lookaheadDepth > 0) {
            score += simulateLookahead(bot, next, foods, snakes, state, config, (int)config.lookaheadDepth, 1.0);
        }

        return score;
    }
    
    private static double simulateLookahead(Snake bot, Point next, List<Food> foods, List<Snake> snakes, GameState state, DifficultyConfig config, int depth, double discount) {
        if (depth <= 0) return 0;
        
        double bestFutureScore = Double.NEGATIVE_INFINITY;
        String[] dirs = {"UP", "DOWN", "LEFT", "RIGHT"};
        
        // Create a simulated snake at the next position
        List<Point> simSegments = new ArrayList<>();
        simSegments.add(next);
        // Add current body (minus tail - simulating movement without growth)
        for (int i = 1; i < bot.getSegments().size(); i++) {
            simSegments.add(bot.getSegments().get(i));
        }
        // Remove tail to simulate movement (unless we're about to eat food)
        boolean wouldEatFood = false;
        for (Food f : foods) {
            if (f.getX() == next.getX() && f.getY() == next.getY()) {
                wouldEatFood = true;
                break;
            }
        }
        if (!wouldEatFood && simSegments.size() > 1) {
            simSegments.remove(simSegments.size() - 1); // Tail moves forward
        }
        
        for (String d : dirs) {
            if (isReverse(d, bot.getDirection())) continue;
            Point futureNext = step(next, d);
            if (futureNext == null) continue;
            
            // Check immediate death - walls
            if (futureNext.getX() < 0 || futureNext.getX() >= 30 || futureNext.getY() < 0 || futureNext.getY() >= 30) continue;
            
            boolean bodyCollision = false;
            // Check other snakes' bodies
            for (Snake other : snakes) {
                if (other == bot || !other.isAlive()) continue;
                for (Point seg : other.getSegments()) {
                    if (seg.equals(futureNext)) {
                        bodyCollision = true;
                        break;
                    }
                }
                if (bodyCollision) break;
            }
            if (bodyCollision) continue;
            
            // Check self collision (excluding tail which moves)
            for (int i = 1; i < simSegments.size() - 1; i++) {
                if (simSegments.get(i).equals(futureNext)) {
                    bodyCollision = true;
                    break;
                }
            }
            if (bodyCollision) continue;
            
            double futureScore = 0;
            
            // Food in future
            Food nearest = nearestFood(futureNext, foods);
            if (nearest != null) {
                int dist = manhattan(futureNext, nearest.getX(), nearest.getY());
                if (dist < 5) futureScore += (5 - dist) * config.foodWeight * discount;
            }
            
            // Center bias
            futureScore += (15 - Math.abs(futureNext.getX() - 15)) * 0.06 * config.centerWeight * discount;
            futureScore += (15 - Math.abs(futureNext.getY() - 15)) * 0.06 * config.centerWeight * discount;
            
            // Recursive lookahead
            if (depth > 1) {
                futureScore += simulateLookahead(bot, futureNext, foods, snakes, state, config, depth - 1, discount * 0.5);
            }
            
            if (futureScore > bestFutureScore) {
                bestFutureScore = futureScore;
            }
        }
        
        return bestFutureScore == Double.NEGATIVE_INFINITY ? -1000 * config.dangerWeight : bestFutureScore;
    }

    private static void decideBoost(Snake bot, List<Food> foods) {
        DifficultyConfig config = DIFFICULTIES.getOrDefault(bot.getBotDifficulty(), DIFFICULTIES.get("normal"));
        int length = bot.getSegments().size();
        if (length <= BOOST_MIN_LENGTH + 2) {
            bot.setBoosting(false);
            return;
        }
        int nearestFoodDist = nearestFoodDistance(bot.getHead(), foods);
        if (bot.isBoosting()) {
            if (length <= BOOST_MIN_LENGTH + 4 || nearestFoodDist < 6) bot.setBoosting(false);
        } else if (length > 15 && nearestFoodDist > 8 && RNG.nextDouble() < config.boostThreshold) {
            bot.setBoosting(true);
        }
    }

    private static int nearestFoodDistance(Point p, List<Food> foods) {
        Food nearest = nearestFood(p, foods);
        return nearest == null ? Integer.MAX_VALUE : manhattan(p, nearest.getX(), nearest.getY());
    }

    private static Food nearestFood(Point p, List<Food> foods) {
        if (foods == null || foods.isEmpty()) return null;
        Food nearest = null;
        int best = Integer.MAX_VALUE;
        for (Food f : foods) {
            int d = manhattan(p, f.getX(), f.getY());
            if (d < best) {
                best = d;
                nearest = f;
            }
        }
        return nearest;
    }

    private static int manhattan(Point p, int x, int y) {
        return Math.abs(p.getX() - x) + Math.abs(p.getY() - y);
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

    private static boolean isReverse(String dir, String current) {
        return switch (dir) {
            case "UP" -> "DOWN".equals(current);
            case "DOWN" -> "UP".equals(current);
            case "LEFT" -> "RIGHT".equals(current);
            case "RIGHT" -> "LEFT".equals(current);
            default -> false;
        };
    }
}
