package com.snake.game.engine;

import com.snake.game.model.Room;
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
        return room;
    }

    public Room getRoom(String code) {
        Room room = rooms.get(code);
        if (room != null) room.touch();
        return room;
    }

    public Room removeRoom(String code) {
        GameEngine.stopGame(code);
        return rooms.remove(code);
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
            } else if (!room.isGameInProgress() && (now - room.getLastActivity() > STALE_TIMEOUT_MS)) {
                GameEngine.stopGame(entry.getKey());
                it.remove();
            }
        }
    }

    public Map<String, Room> getRooms() {
        return rooms;
    }
}
