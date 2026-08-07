package com.snake.game.engine;

import com.snake.game.model.Room;
import com.snake.game.util.GameLogger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RoomManager {
    private static final RoomManager INSTANCE = new RoomManager();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private static final long STALE_TIMEOUT_MS = 300_000;
    private static final long CLEANUP_INTERVAL_MS = 10_000;
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "room-cleanup");
        t.setDaemon(true);
        return t;
    });

    private RoomManager() {
        cleanupScheduler.scheduleAtFixedRate(this::cleanupStaleRooms, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public static RoomManager getInstance() {
        return INSTANCE;
    }

    public Room createRoom() {
        Room room;
        do {
            room = new Room();
        } while (rooms.containsKey(room.getCode()));
        room.touch();
        rooms.put(room.getCode(), room);
        GameLogger.roomCreated(room.getCode(), null);
        return room;
    }

    public Room getRoom(String code) {
        Room room = rooms.get(code);
        if (room != null) room.touch();
        return room;
    }

    public Room removeRoom(String code) {
        Room room = rooms.get(code);
        int playerCount = room != null ? room.getPlayerCount() : 0;
        GameEngine.stopGame(code);
        Room removed = rooms.remove(code);
        if (removed != null) {
            GameLogger.roomDestroyed(code, "manual", playerCount);
        }
        return removed;
    }

    public void cleanupStaleRooms() {
        long now = System.currentTimeMillis();
        java.util.Iterator<Map.Entry<String, Room>> it = rooms.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Room> entry = it.next();
            Room room = entry.getValue();
            room.removeDisconnectedPlayers();
            if (room.getPlayerCount() == 0 && !room.isGameInProgress()) {
                GameEngine.stopGame(entry.getKey());
                it.remove();
                GameLogger.roomDestroyed(entry.getKey(), "empty", 0);
            } else if (!room.isGameInProgress() && (now - room.getLastActivity() > STALE_TIMEOUT_MS)) {
                int playerCount = room.getPlayerCount();
                GameEngine.stopGame(entry.getKey());
                it.remove();
                GameLogger.roomDestroyed(entry.getKey(), "stale", playerCount);
            }
        }
    }

    public Map<String, Room> getRooms() {
        return rooms;
    }

    /**
     * Adds a player to a room and logs the event.
     * @return true if player was added, false if room not found or full
     */
    public boolean addPlayerToRoom(String roomCode, com.snake.game.model.Snake player) {
        Room room = rooms.get(roomCode);
        if (room == null || room.isFull()) {
            return false;
        }
        room.getPlayers().add(player);
        room.touch();
        GameLogger.playerJoined(roomCode, player.getName(), room.getPlayerCount(), room.getMaxPlayers());
        return true;
    }

    /**
     * Removes a player from a room and logs the event.
     * @return true if player was removed, false if room or player not found
     */
    public boolean removePlayerFromRoom(String roomCode, String playerName, String reason) {
        Room room = rooms.get(roomCode);
        if (room == null) {
            return false;
        }
        boolean removed = room.removePlayer(playerName);
        if (removed) {
            room.touch();
            GameLogger.playerLeft(roomCode, playerName, room.getPlayerCount(), reason);
        }
        return removed;
    }

    // ==================== Spectator Methods ====================

    /**
     * Adds a spectator to a room and logs the event.
     * @return true if spectator was added, false if room not found
     */
    public boolean addSpectatorToRoom(String roomCode, String spectatorName) {
        Room room = rooms.get(roomCode);
        if (room == null) {
            return false;
        }
        boolean added = room.addSpectator(spectatorName);
        if (added) {
            room.touch();
            GameLogger.spectatorJoined(roomCode, spectatorName, room.getSpectatorCount(), room.getPlayerCount());
        }
        return added;
    }

    /**
     * Removes a spectator from a room and logs the event.
     * @return true if spectator was removed, false if room or spectator not found
     */
    public boolean removeSpectatorFromRoom(String roomCode, String spectatorName, String reason) {
        Room room = rooms.get(roomCode);
        if (room == null) {
            return false;
        }
        boolean removed = room.removeSpectator(spectatorName);
        if (removed) {
            room.touch();
            GameLogger.spectatorLeft(roomCode, spectatorName, room.getSpectatorCount(), room.getPlayerCount(), reason);
        }
        return removed;
    }
}
