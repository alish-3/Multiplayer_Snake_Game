package com.snake.game.servlet;

import com.snake.game.util.GameLogger;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.logging.Logger;

/**
 * Application lifecycle listener to initialize and shutdown GameLogger.
 * Registered via web.xml (or @WebListener annotation).
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    private static final Logger logger = Logger.getLogger(AppContextListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            GameLogger.init();
            logger.info("[AppContextListener] Application startup - GameLogger initialized");
        } catch (Exception e) {
            // Log to standard error since logger may not be initialized
            System.err.println("[AppContextListener] Failed to initialize GameLogger: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        try {
            GameLogger.shutdown();
            System.out.println("[AppContextListener] Application shutdown - GameLogger closed");
        } catch (Exception e) {
            System.err.println("[AppContextListener] Error during GameLogger shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
}