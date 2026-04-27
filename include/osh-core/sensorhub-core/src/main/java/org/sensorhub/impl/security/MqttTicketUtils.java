package org.sensorhub.impl.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttTicketUtils {
    private static final Logger log = LoggerFactory.getLogger(MqttTicketUtils.class);
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String AUDIENCE = "mqtt-ws";

    /**
     * Creates a signed MQTT ticket for the given user.
     * Ticket format: sub:iat:exp:aud:signature
     */
    public static String createTicket(String userId, long ttlMillis) {
        try {
            String secret = System.getProperty("javax.net.ssl.keyStorePassword");
            if (secret == null || secret.isEmpty()) {
                secret = "temporary-fallback-secret-for-dev";
                log.warn("javax.net.ssl.keyStorePassword not set, using fallback secret");
            }

            long iat = System.currentTimeMillis();
            long exp = iat + ttlMillis;
            String payload = userId + ":" + iat + ":" + exp + ":" + AUDIENCE;

            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] sigBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);

            return payload + ":" + signature;
        } catch (Exception e) {
            log.error("Error creating MQTT ticket", e);
            return null;
        }
    }

    /**
     * Validates a signed MQTT ticket and returns the userId if valid.
     */
    public static String validateTicket(String ticketStr) {
        if (ticketStr == null || ticketStr.isEmpty()) {
            return null;
        }

        try {
            String[] parts = ticketStr.split(":");
            if (parts.length != 5) {
                return null;
            }

            String userId = parts[0];
            long iat = Long.parseLong(parts[1]);
            long exp = Long.parseLong(parts[2]);
            String aud = parts[3];
            String signature = parts[4];

            if (!AUDIENCE.equals(aud)) {
                return null;
            }

            if (System.currentTimeMillis() > exp) {
                log.debug("MQTT ticket expired for user {}", userId);
                return null;
            }

            String secret = System.getProperty("javax.net.ssl.keyStorePassword");
            if (secret == null || secret.isEmpty()) {
                secret = "temporary-fallback-secret-for-dev";
            }

            String payload = userId + ":" + iat + ":" + exp + ":" + aud;
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] sigBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);

            if (expectedSignature.equals(signature)) {
                return userId;
            }
        } catch (Exception e) {
            log.debug("Invalid MQTT ticket format: {}", e.getMessage());
        }

        return null;
    }
}
