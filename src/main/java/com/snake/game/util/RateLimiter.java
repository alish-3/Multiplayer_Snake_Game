package com.snake.game.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe sliding window rate limiter using ConcurrentHashMap.
 * Supports per-key (IP, player name, etc.) rate limiting with configurable limits.
 */
public class RateLimiter {

    private static final RateLimiter INSTANCE = new RateLimiter();

    // Map of key -> list of request timestamps
    private final Map<String, RequestWindow> windows = new ConcurrentHashMap<>();

    // Background cleanup scheduler
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "RateLimiter-Cleanup");
        t.setDaemon(true);
        return t;
    });

    private RateLimiter() {
        // Run cleanup every minute
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredWindows, 1, 1, TimeUnit.MINUTES);
    }

    public static RateLimiter getInstance() {
        return INSTANCE;
    }

    /**
     * Tries to consume a request for the given key.
     *
     * @param key       Unique identifier (e.g., IP address, player name)
     * @param limit     Maximum requests allowed in the time window
     * @param windowMs  Time window in milliseconds
     * @return true if request is allowed, false if rate limited
     */
    public boolean tryConsume(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        long windowStart = now - windowMs;

        RequestWindow window = windows.computeIfAbsent(key, k -> new RequestWindow());
        
        synchronized (window) {
            // Remove expired timestamps
            window.timestamps.removeIf(ts -> ts <= windowStart);
            
            if (window.timestamps.size() >= limit) {
                return false; // Rate limited
            }
            
            window.timestamps.add(now);
            return true;
        }
    }

    /**
     * Gets the number of remaining requests for the given key.
     *
     * @param key       Unique identifier
     * @param limit     Maximum requests allowed in the time window
     * @param windowMs  Time window in milliseconds
     * @return Number of remaining requests (0 if rate limited)
     */
    public int getRemainingRequests(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        long windowStart = now - windowMs;

        RequestWindow window = windows.get(key);
        if (window == null) {
            return limit;
        }

        synchronized (window) {
            window.timestamps.removeIf(ts -> ts <= windowStart);
            return Math.max(0, limit - window.timestamps.size());
        }
    }

    /**
     * Gets the time in milliseconds until the next request would be allowed.
     *
     * @param key       Unique identifier
     * @param limit     Maximum requests allowed in the time window
     * @param windowMs  Time window in milliseconds
     * @return Milliseconds until next request allowed, or 0 if not rate limited
     */
    public long getRetryAfterMs(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        long windowStart = now - windowMs;

        RequestWindow window = windows.get(key);
        if (window == null) {
            return 0;
        }

        synchronized (window) {
            window.timestamps.removeIf(ts -> ts <= windowStart);
            
            if (window.timestamps.size() < limit) {
                return 0; // Not rate limited
            }
            
            // Return time until the oldest request expires
            long oldestTimestamp = window.timestamps.peek();
            if (oldestTimestamp > 0) {
                return Math.max(1, oldestTimestamp + windowMs - now);
            }
            return windowMs;
        }
    }

    /**
     * Cleans up expired windows to prevent memory leaks.
     */
    private void cleanupExpiredWindows() {
        long now = System.currentTimeMillis();
        long threshold = now - 60_000; // Remove windows older than 1 minute (longest window)
        
        windows.entrySet().removeIf(entry -> {
            RequestWindow window = entry.getValue();
            synchronized (window) {
                window.timestamps.removeIf(ts -> ts <= threshold);
                return window.timestamps.isEmpty();
            }
        });
    }

    /**
     * Shuts down the cleanup scheduler (for testing).
     */
    public void shutdown() {
        cleanupScheduler.shutdown();
    }

    /**
     * Internal class to hold request timestamps for a key.
     * Using AtomicLong for thread-safe operations.
     */
    private static class RequestWindow {
        final java.util.Queue<Long> timestamps = new java.util.concurrent.ConcurrentLinkedQueue<>();
    }
}