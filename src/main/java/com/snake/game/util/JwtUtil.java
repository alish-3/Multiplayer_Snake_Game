package com.snake.game.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.security.SecureRandom;

public class JwtUtil {
    private static final long EXPIRY_MS = 30 * 24 * 60 * 60 * 1000L;
    private static final Gson gson = new Gson();
    private static final int MIN_SECRET_LENGTH = 32;
    
    // Secret loaded at startup from environment variable
    private static String secret;
    static {
        // Try to load from environment variable first
        String envSecret = Optional.ofNullable(System.getenv("JWT_SECRET"))
                   .filter(s -> !s.trim().isEmpty())
                   .orElse(null);

        if (envSecret != null && envSecret.getBytes().length >= MIN_SECRET_LENGTH) {
            // Use the provided secret
            secret = envSecret;
        } else {
            // Fall back to a random secret instead of throwing at class load
            secret = generateSecureRandomSecret();
            System.err.println(
                "WARNING: JWT_SECRET not set; using a random secret. " +
                "Remember-me tokens will be invalidated on server restart. " +
                "Set JWT_SECRET env var for persistence.");
        }
    }
    
    private static String generateSecureRandomSecret() {
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public static String createToken(String username) {
        long now = System.currentTimeMillis();
        JsonObject header = new JsonObject();
        header.addProperty("alg", "HS256");
        header.addProperty("typ", "JWT");
        JsonObject payload = new JsonObject();
        payload.addProperty("sub", username);
        payload.addProperty("iat", now);
        payload.addProperty("exp", now + EXPIRY_MS);
        String headerB64 = base64UrlEncode(header.toString().getBytes());
        String payloadB64 = base64UrlEncode(payload.toString().getBytes());
        String signature = hmacSha256(headerB64 + "." + payloadB64);
        return headerB64 + "." + payloadB64 + "." + signature;
    }

    public static String validateToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            String expectedSig = hmacSha256(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expectedSig.getBytes(), parts[2].getBytes())) return null;
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonObject payload = gson.fromJson(payloadJson, JsonObject.class);
            long exp = payload.get("exp").getAsLong();
            if (System.currentTimeMillis() > exp) return null;
            return payload.get("sub").getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String hmacSha256(String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec key = new javax.crypto.spec.SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256");
            mac.init(key);
            return base64UrlEncode(mac.doFinal(data.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new RuntimeException("HMAC failed", e);
        }
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
