package com.snake.game.servlet;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Security filter that adds security headers and handles CORS for API endpoints.
 * Applied to all /api/* paths and WebSocket endpoint.
 */
@WebFilter(urlPatterns = {"/api/*", "/api/game/ws/*"})
public class SecurityFilter implements Filter {

    private static final String ALLOWED_ORIGINS_ENV = "ALLOWED_ORIGINS";
    private static final String DEFAULT_ALLOWED_ORIGINS = "*";

    private Set<String> allowedOrigins;
    private boolean allowAllOrigins;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String originsEnv = System.getenv(ALLOWED_ORIGINS_ENV);
        if (originsEnv == null || originsEnv.trim().isEmpty()) {
            originsEnv = DEFAULT_ALLOWED_ORIGINS;
        }

        if (DEFAULT_ALLOWED_ORIGINS.equals(originsEnv.trim())) {
            allowAllOrigins = true;
            allowedOrigins = null;
        } else {
            allowAllOrigins = false;
            allowedOrigins = new HashSet<>(Arrays.asList(originsEnv.split("\\s*,\\s*")));
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Add security headers to all responses
        addSecurityHeaders(httpRequest, httpResponse);

        // Handle CORS
        String origin = httpRequest.getHeader("Origin");
        boolean isCorsRequest = origin != null && !origin.isEmpty();

        if (isCorsRequest && isOriginAllowed(origin)) {
            httpResponse.setHeader("Access-Control-Allow-Origin", origin);
            httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
            httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            httpResponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
            httpResponse.setHeader("Access-Control-Expose-Headers", "Retry-After");
            httpResponse.setHeader("Access-Control-Max-Age", "86400");
            httpResponse.setHeader("Vary", "Origin");
        }

        // Handle OPTIONS preflight requests
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // For WebSocket upgrade requests, don't add CORS headers that might interfere
        // The WebSocket handshake will be handled by the WebSocket endpoint
        String upgradeHeader = httpRequest.getHeader("Upgrade");
        if ("websocket".equalsIgnoreCase(upgradeHeader)) {
            // Allow the request to proceed to the WebSocket endpoint
            chain.doFilter(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    private void addSecurityHeaders(HttpServletRequest request, HttpServletResponse response) {
        // Prevent MIME type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Prevent clickjacking
        response.setHeader("X-Frame-Options", "DENY");

        // Enable XSS protection (legacy but still useful for older browsers)
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Control referrer information
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Content Security Policy
        String csp = "default-src 'self'; " +
                     "script-src 'self' 'unsafe-inline'; " +
                     "style-src 'self' 'unsafe-inline'; " +
                     "img-src 'self' data:; " +
                     "font-src 'self'; " +
                     "connect-src 'self' wss: ws:; " +
                     "frame-ancestors 'none';";
        response.setHeader("Content-Security-Policy", csp);

        // Permissions Policy (feature policy)
        String permissionsPolicy = "accelerometer=(), " +
                                   "camera=(), " +
                                   "geolocation=(), " +
                                   "gyroscope=(), " +
                                   "magnetometer=(), " +
                                   "microphone=(), " +
                                   "payment=(), " +
                                   "usb=()";
        response.setHeader("Permissions-Policy", permissionsPolicy);

        // Strict Transport Security (only for HTTPS)
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
    }

    private boolean isOriginAllowed(String origin) {
        if (allowAllOrigins) {
            return true;
        }
        return allowedOrigins != null && allowedOrigins.contains(origin);
    }

    @Override
    public void destroy() {
        // No resources to clean up
    }
}