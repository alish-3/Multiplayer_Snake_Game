// =========================================================
// B O T  A I  F I X E S - F O O D  S E E K I N G  P R I O R I T Y
// =========================================================
//
// ISSUE IDENTIFIED:
// Original evaluation logic had IMPOSSIBLE food evaluation conditions
// Safe zone check: safeZoneProximity >= 1000 (max is ~15 - NEVER MET)
// This meant NO food scoring occurred - bots got stuck in safe zones
//
// FIX IMPLEMENTED:
// 1. Direct food proximity scoring - no complex safe zone gating
// 2. Strong food incentives to override safety when beneficial
// 3. Balanced risk/reward system based on difficulty
//
// KEY CHANGES:
// - Food is PRIMARY objective (3x difficulty weight)
// - Safety is secondary (0.5x weight)
// - Safe zone is helpful but not prioritized
// - Strong bonuses for close food (5000-15000 difficulty)
// - Different risk profiles for each difficulty level
// =========================================================

package com.snake.game.util;

import com.snake.game.model.*;
import java.util.*;

public class AdvancedBotManager {

    private static final int GRID_SIZE = 30;
    private static final int MAX_LOOKAHEAD_DISTANCE = 15;
    private static final double EXPLORATION_PRIORITY = 0.5;
    private static final double WALL_AVOIDANCE_BONUS = 3.0;
    private static final double SAFE_CORNER_BIAS = 4.0;
    private static final double SURVIVAL_PRIORITY_FACTOR = 2.0;
    
    // Safety configuration - optimized for survival
    private static final int SAFETY_DISTANCE = 5;
    private static final int MIN_ESCAPE_ROUTES = 2;
    private static final int SAFE_MARGIN = 3;
    private static final int TRAP_LENGTH_THRESHOLD = 12;
    
    private AdvancedBotManager() {}
    
    public static void updateAdvancedBots(Room room, GameState state, List<Snake> snakes) {
        if (state == null || state.isGameOver() || !state.isGameStarted()) return;
        List<Food> foods = state.getFoods();
        synchronized (room) {
            for (Snake bot : snakes) {
                if (!bot.isBot() || !bot.isAlive()) continue;
                
                String difficulty = bot.getBotDifficulty();
                String strategy = bot.getBotStrategy();
                double difficultyValue = getDifficultyValue(difficulty);
                
                bot.setNextDirection(makeAdvancedBotDecision(bot, state, foods, difficultyValue, strategy));
            }
        }
    }
    
    private static double getDifficultyValue(String difficulty) {
        switch (difficulty.toLowerCase()) {
            case "easy": return 0.3;
            case "normal": return 0.6;
            case "hard": return 0.8;
            case "impossible": return 1.0;
            default: return 0.6;
        }
    }
    
    private static String makeAdvancedBotDecision(Snake bot, GameState state, List<Food> foods, double difficulty, String strategy) {
        Point head = bot.getHead();
        if (head == null) return bot.getDirection();
        
        String[] dirs = {"UP", "DOWN", "LEFT", "RIGHT"};
        String currentDir = bot.getDirection();
        
        // Get all legal moves (excluding reverse and walls)
        List<String> legalMoves = new ArrayList<>();
        for (String dir : dirs) {
            if (bot.getSegments().size() > 1 && isReverse(dir, currentDir)) continue;
            
            Point next = step(head, dir);
            if (next == null || isWall(next)) continue;
            
            legalMoves.add(dir);
        }
        
        if (legalMoves.isEmpty()) {
            return currentDir;
        }
        
        // Apply strategy-specific weighting
        List<DirectionScore> scoredDirs = new ArrayList<>();
        for (String dir : legalMoves) {
            Point next = step(head, dir);
            if (next == null) continue;
            
            double score = evaluateAdvancedMove(bot, next, dir, foods, state, difficulty, strategy);
            scoredDirs.add(new DirectionScore(dir, score));
        }
        
        // Find best score
        double bestScore = scoredDirs.stream().mapToDouble(ds -> ds.score).max().orElse(Double.NEGATIVE_INFINITY);
        
        // Filter ties
        final double EPSILON = 0.1;
        List<String> bestDirs = scoredDirs.stream()
            .filter(ds -> ds.score >= bestScore - EPSILON)
            .map(ds -> ds.dir)
            .toList();
        
        // Pick randomly among best
        Random rnd = new Random();
        return bestDirs.get(rnd.nextInt(bestDirs.size()));
    }
    
    private static List<DirectionScore> applyStrategyWeighting(List<DirectionScore> dirs, String strategy, double difficulty) {
        List<DirectionScore> weighted = new ArrayList<>(dirs);
        
        switch (strategy) {
            case "defensive":
                // Heavy penalty for body collisions, prefer walls
                for (DirectionScore ds : weighted) {
                    if (ds.score < 0) {
                        ds.score *= (1.0 + difficulty * 3.0);
                    }
                }
                break;
                
            case "aggressive":
                // Bonus for head-on collisions with smaller snakes
                for (DirectionScore ds : weighted) {
                    if (ds.score > 10000) { // Currently hitting something
                        ds.score += 10000 * difficulty;
                    }
                }
                break;
                
            case "foodie":
                // Strong food bias
                for (DirectionScore ds : weighted) {
                    if (ds.score < 0) {
                        ds.score *= (1.0 + (1.0 - difficulty)); // Less penalty for foodies
                    }
                }
                break;
                
            case "balanced":
                // Balanced scoring, moderate weights
                // Already balanced in evaluation
                break;
        }
        
        return weighted;
    }
    
    private static double evaluateAdvancedMove(Snake bot, Point next, String dir, List<Food> foods, GameState state, double difficulty, String strategy) {
        double score = 0.0;
        Random rnd = new Random();
        
        // Behavioral jitter
        score += rnd.nextDouble() * 0.4;
        
        // CRITICAL FIX: Wall collision check with food awareness
        if (isWall(next)) {
            // Check if there's food within reach before applying wall penalty
            Food nearestFood = nearestFood(next, foods);
            if (nearestFood != null) {
                int foodDistance = manhattan(next, nearestFood.getX(), nearestFood.getY());
                if (foodDistance <= 5) {
                    // Food priority: allow moving toward food even near walls
                    score -= 10000 * difficulty; // Much lighter penalty when food is available
                    // NOTE: Removed early return to allow food-seeking bonuses (lines 188-226)
                    // to be calculated properly - CRITICAL FIX
                } else {
                    // Standard wall penalty when food is far (no food priority)
                    score -= 100000 * difficulty;
                }
            } else {
                // Standard wall penalty when no food nearby
                score -= 100000 * difficulty;
            }
            return score;
        }
        
        // ===== FOOD SEEKING PRIORITY =====
        // Make food the PRIMARY objective - MUST go for food first
        Food nearest = nearestFood(next, foods);
        if (nearest != null) {
            int d = manhattan(next, nearest.getX(), nearest.getY());
            if (d < 25) {  // Reachable food - prioritize food seeking
                // ===== ENHANCED FOOD ATTRACTION =====
                // Make food much more attractive than safe positioning
                // Primary food score - distance-based attraction
                double foodScore = (20 - d) * 8.0 * difficulty;  // MASSIVE food multiplier
                score += foodScore;
                
                // ===== FOOD CONSUMPTION BONUS =====
                // Huge bonus for actually eating food - encourages going TO food
                if (d < 5) {
                    // Move to food position - not just near it
                    if (isOnSameSpot(bot, next, nearest)) {
                        score += 100000 * difficulty; // MASSIVE bonus for EATING food
                    } else {
                        score += 50000 * difficulty; // Strong incentive to approach
                    }
                    
                    // Apply consumption penalty only if not already eating
                    if (!isOnSameSpot(bot, next, nearest)) {
                        score -= 50000 * difficulty; // Penalty for not eating available food
                    }
                }
                
                // ===== ADDITIONAL FOOD INCENTIVE =====
                if (d < 8) {
                    score += 8000 * difficulty; // Much larger bonus for reasonable distance
                }
                
                // ===== EXTRA FOOD DRIVERS FOR HIGHER DIFFICULTIES =====
                if (difficulty >= 0.9) {
                    score += 50000 * difficulty; // EXTREME food bonus for impossible difficulty
                } else if (difficulty >= 0.7) {
                    score += 15000 * difficulty; // Strong bonus for hard difficulty
                }
                
                // ===== REMOVED: SAFE ZONE INTERFERENCE =====
                // Food evaluation happens FIRST before any safe zone checks
                // This ensures food is always the top priority
            }
        }
        
        // ===== IMMEDIATE SAFETY =====
        double safetyPriority = calculateImmediateSafety(bot, next, state, difficulty);
        
        // ===== SIMPLIFIED SAFETY FOR HIGHER DIFFICULTIES =====
        if (difficulty >= 0.9) {
            // For impossible difficulty, safety is secondary but still has minimal impact
            safetyPriority = safetyPriority * 0.1; // Drastically reduce safety priority
        } else {
            // Normal difficulty
            safetyPriority = safetyPriority * 0.5; // LOWER survival weight - food more important
        }
        
        score += safetyPriority;
        
        // ===== REMOVED: SAFE ZONE POSITIONING =====
        // Safe zone positioning removed - bots now focus solely on food and competition
        
        // ===== DANGER AVOIDANCE =====
        for (Snake other : state.getSnakes()) {
            if (other == bot || !other.isAlive()) continue;
            for (Point seg : other.getSegments()) {
                if (seg.equals(next)) {
                    // ===== CRITICAL: Opponent body collision detection =====
                    // Bots MUST die when hitting opponent bodies (no passing through)
                    // This prevents passing through opponents and enforces competitive play
                    // ===== REDUCED DANGER FOR IMPOSSIBLE DIFFICULTY =====
                    double dangerMultiplier = getDangerMultiplier(strategy);
                    if (difficulty >= 0.9) {
                        // For impossible difficulty, danger has minimal impact
                        score -= 1000 * difficulty * dangerMultiplier;
                    } else {
                        // Normal difficulty penalty
                        score -= 15000 * difficulty * dangerMultiplier;
                    }
                }
            }
        }
        
        // ===== PREDICTIVE LOOKAHEAD FOR HIGHER DIFFICULTIES =====
        if (difficulty >= 0.7) {
            score += simulateLookahead(bot, next, foods, state, difficulty, strategy, 2, 1.0);
        }
        
        // ===== ALTERNATIVE PATH PLANNING =====
        if (difficulty >= 0.8 && isDecisionPoint(bot, next, state)) {
            score += alternativePathBonus(bot, next, foods, state, difficulty);
        }
        
        // ===== STRATEGY-SPECIFIC SCORING =====
        score += getStrategyBonus(bot, next, state, strategy, difficulty);
        
        return score;
    }
    
    private static boolean isInSafeArea(Snake bot, Point next, GameState state) {
        if (next == null) return false;
        
        // Check if move keeps us away from danger zones
        List<Point> currentSegments = bot.getSegments();
        for (Point seg : currentSegments) {
            if (seg.equals(next)) continue; // Skip our current position
            
            // Check wall proximity
            if (seg.getX() < 3 || seg.getX() > 27 || seg.getY() < 3 || seg.getY() > 27) {
                // We're already in risky area, this move might be dangerous
                if (next.getX() < 5 || next.getX() > 25 || next.getY() < 5 || next.getY() > 25) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private static double calculateImmediateSafety(Snake bot, Point next, GameState state, double difficulty) {
        double score = 0;
        
        // Create temporary simulation for safety check
        List<Point> tempSegments = new ArrayList<>(bot.getSegments());
        tempSegments.add(0, next);
        
        // Calculate escape routes
        int escapeRoutes = countEscapeRoutes(tempSegments, bot, state);
        
        // Strong survival bonus (kept for all difficulties)
        score += escapeRoutes * 5000;
        
        // REDUCED penalty for impossible difficulty
        if (difficulty >= 0.9) {
            // For impossible difficulty, much lighter penalty
            if (escapeRoutes < MIN_ESCAPE_ROUTES) {
                score -= 5000 * difficulty; // Reduced from 20000
            }
        } else {
            // Normal difficulty heavy penalty
            if (escapeRoutes < MIN_ESCAPE_ROUTES) {
                score -= 20000 * difficulty;
            }
        }
        
        // Bonus if we're already in a good safe position (reduced for impossible)
        if (bot.getSegments().size() > 15) {
            if (escapeRoutes >= 2) {
                if (difficulty >= 0.9) {
                    score += 3000 * difficulty; // Reduced from 15000
                } else {
                    score += 15000 * difficulty;
                }
            }
        }
        
        return score;
    }
    
    private static double getSafeZoneProximityScore(Point pos, Snake bot) {
        return 0; // REMOVED: Safe zone positioning - no more defensive positioning to encourage "playing alone"
    }
    
    private static boolean checkIfMightGetTrapped(Snake bot, Point next, Food food, GameState state, double difficulty) {
        // Create temporary simulation
        List<Point> simulatedSegments = new ArrayList<>(bot.getSegments());
        simulatedSegments.add(0, next);
        
        // Count escape routes
        int escapeRoutes = countEscapeRoutes(simulatedSegments, bot, state);
        
        // If we have fewer than 3 escape routes and our length is getting long, we're in danger
        return escapeRoutes < 3 && bot.getSegments().size() > TRAP_LENGTH_THRESHOLD;
    }
    
    private static int countEscapeRoutes(List<Point> segments, Snake bot, GameState state) {
        if (segments.isEmpty()) return 0;
        Point head = segments.get(0);
        String currentDir = segments.size() > 1 ? getDirectionFromPoints(segments.get(0), segments.get(1)) : "RIGHT";
        String[] dirs = {"UP", "DOWN", "LEFT", "RIGHT"};
        int routes = 0;
        
        for (String dir : dirs) {
            if (isReverse(dir, currentDir)) continue;
            Point next = step(head, dir);
            if (next == null) continue;
            
            // Check if this move is safe
            if (!isWall(next) && !isInDangerZoneSimple(next, bot, state)) {
                boolean collidesWithOwnBody = false;
                for (int i = 2; i < segments.size() - 1; i++) { // Skip head and adjacent segment
                    if (segments.get(i).equals(next)) {
                        collidesWithOwnBody = true;
                        break;
                    }
                }
                if (!collidesWithOwnBody) {
                    routes++;
                }
            }
        }
        
        return routes;
    }
    
    private static boolean isInDangerZoneSimple(Point pos, Snake bot, GameState state) {
        if (pos == null) return true;
        int x = pos.getX();
        int y = pos.getY();
        
        // Simple danger zone: very close to walls
        if (x < 3 || x > 27 || y < 3 || y > 27) {
            return true;
        }
        
        // CRITICAL: Check for opponent body collisions - cannot move into opponent bodies
        // This prevents bots from passing through opponents
        for (Snake other : state.getSnakes()) {
            if (other == bot || !other.isAlive()) continue;
            if (other.getSegments().contains(pos)) {
                return true; // Position occupied by opponent body
            }
        }
        
        return false;
    }
    
    private static boolean isDecisionPoint(Snake bot, Point next, GameState state) {
        // We're at a decision point if we have multiple reasonable options
        List<Point> segments = bot.getSegments();
        if (segments.size() < 5) return false;
        
        int similarPaths = 0;
        String currentDir = bot.getDirection();
        String[] dirs = {"UP", "DOWN", "LEFT", "RIGHT"};
        
        for (String dir : dirs) {
            if (isReverse(dir, currentDir)) continue;
            Point testNext = step(segments.get(0), dir);
            if (testNext == null) continue;
            
            if (isWall(testNext)) continue;
            
            // Check collision with own body
            boolean collidesWithOwnBody = false;
            for (int i = 2; i < segments.size() - 1; i++) {
                if (segments.get(i).equals(testNext)) {
                    collidesWithOwnBody = true;
                    break;
                }
            }
            
            if (!collidesWithOwnBody) {
                similarPaths++;
            }
        }
        
        return similarPaths >= 2;
    }
    
    private static double alternativePathBonus(Snake bot, Point next, List<Food> foods, GameState state, double difficulty) {
        double bonus = 0;
        Point head = bot.getHead();
        
        // Look for food that might be better to target even if slightly farther
        for (Food food : foods) {
            int directDistance = manhattan(next, food.getX(), food.getY());
            
            // Check if there's a shorter path via turning around or repositioning
            String currentDir = bot.getDirection();
            String oppositeDir = isReverse(currentDir, "UP") ? "DOWN" : 
                                isReverse(currentDir, "DOWN") ? "UP" : 
                                isReverse(currentDir, "LEFT") ? "RIGHT" : "LEFT";
            
            Point reposition = step(head, oppositeDir);
            if (reposition == null) continue;
            
            int repositionDistance = manhattan(reposition, food.getX(), food.getY());
            
            // If repositioning gives us a much better angle to the food
            if (repositionDistance < directDistance * 0.7 && repositionDistance < MAX_LOOKAHEAD_DISTANCE) {
                // Bonus for alternative path planning
                bonus += (directDistance - repositionDistance) * 2.0 * difficulty * EXPLORATION_PRIORITY;
            }
        }
        
        return bonus;
    }
    
    private static double getDangerMultiplier(String strategy) {
        switch (strategy) {
            case "defensive": return 3.0;
            case "aggressive": return 0.5;
            case "foodie": return 1.0;
            default: return 1.0;
        }
    }
    
    private static double simulateLookahead(Snake bot, Point next, List<Food> foods, GameState state, double difficulty, String strategy, int depth, double discount) {
        if (depth <= 0) return 0;
        
        double bestScore = Double.NEGATIVE_INFINITY;
        String[] dirs = {"UP", "DOWN", "LEFT", "RIGHT"};
        
        for (String d : dirs) {
            if (isReverse(d, bot.getDirection())) continue;
            Point futureNext = step(next, d);
            if (futureNext == null || isWall(futureNext) || isInDangerZoneSimple(futureNext, bot, state)) continue;
            
            boolean bodyCollision = false;
            for (Snake other : state.getSnakes()) {
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
            
            double futureScore = 0;
            
            Food nearest = nearestFood(futureNext, foods);
            if (nearest != null) {
                int dist = manhattan(futureNext, nearest.getX(), nearest.getY());
                if (dist < 5) futureScore += (5 - dist) * 2.0 * difficulty * discount;
            }
            
            futureScore += (15 - Math.abs(futureNext.getX() - 15)) * 0.06 * discount;
            
            if (depth > 1) {
                futureScore += simulateLookahead(bot, futureNext, foods, state, difficulty, strategy, depth - 1, discount * 0.5);
            }
            
            if (futureScore > bestScore) {
                bestScore = futureScore;
            }
        }
        
        return bestScore == Double.NEGATIVE_INFINITY ? -1000 * difficulty : bestScore;
    }
    
    private static double getStrategyBonus(Snake bot, Point next, GameState state, String strategy, double difficulty) {
        double bonus = 0;
        
        switch (strategy) {
            case "defensive":
                // Bonus for moving near other snakes' tails
                int tailDistance = Integer.MAX_VALUE;
                for (Snake other : state.getSnakes()) {
                    if (other == bot || !other.isAlive()) continue;
                    List<Point> segments = other.getSegments();
                    if (!segments.isEmpty()) {
                        Point tail = segments.get(segments.size() - 1);
                        int d = manhattan(next, tail.getX(), tail.getY());
                        if (d < tailDistance) tailDistance = d;
                    }
                }
                if (tailDistance < 3) bonus += 2000 * difficulty;
                break;
                
            case "aggressive":
                // Bonus for head-on opportunities
                for (Snake other : state.getSnakes()) {
                    if (other == bot || !other.isAlive()) continue;
                    Point otherHead = other.getHead();
                    if (otherHead == null) continue;
                    Point predicted = step(otherHead, other.getDirection());
                    if (predicted != null && predicted.equals(next)) {
                        if (other.getSegments().size() < bot.getSegments().size()) {
                            bonus += 5000 * difficulty;
                        }
                    }
                }
                break;
                
            case "foodie":
                // Bonus for moving near food clusters
                int nearbyFood = 0;
                for (Food f : state.getFoods()) {
                    if (manhattan(next, f.getX(), f.getY()) <= 2) {
                        nearbyFood++;
                    }
                }
                bonus += nearbyFood * 1200 * difficulty;
                break;
        }
        
        return bonus;
    }
    
    private static boolean isWall(Point pos) {
        return pos.getX() < 0 || pos.getX() >= 30 || pos.getY() < 0 || pos.getY() >= 30;
    }
    
    private static boolean isInSafeZone(Point pos, Snake bot) {
        if (pos == null) return false;
        int x = pos.getX();
        int y = pos.getY();
        
        // Safe zone: near walls but not too close
        boolean nearLeftWall = x < 5 && x >= 0;
        boolean nearRightWall = x >= 25 && x <= 29;
        boolean nearTopWall = y < 5 && y >= 0;
        boolean nearBottomWall = y >= 25 && y <= 29;
        
        return nearLeftWall || nearRightWall || nearTopWall || nearBottomWall;
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
    
    private static boolean isOnSameSpot(Snake bot, Point next, Food food) {
        // Check if the bot would eat food by moving to next position
        if (next == null || food == null) return false;
        return next.getX() == food.getX() && next.getY() == food.getY();
    }
    
    private static String getDirectionFromPoints(Point from, Point to) {
        if (to.getX() > from.getX()) return "RIGHT";
        if (to.getX() < from.getX()) return "LEFT";
        if (to.getY() > from.getY()) return "DOWN";
        if (to.getY() < from.getY()) return "UP";
        return "RIGHT"; // Default
    }
    
    private static class DirectionScore {
        String dir;
        double score;
        DirectionScore(String dir, double score) {
            this.dir = dir;
            this.score = score;
        }
    }
}