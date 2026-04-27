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
    private static final String SCOPE = "subscribe";

    /**
     * Creates a signed MQTT ticket for the given user.
     * Ticket format: sub:iat:exp:aud:scope:signature
     */
    public static String createTicket(String userId, long ttlMillis) {
        try {
            String secret = System.getProperty("javax.net.ssl.keyStorePassword");
            if (secret == null || secret.isEmpty()) {
                log.error("javax.net.ssl.keyStorePassword not set, ticket signing failed");
                return null;
            }

            long iat = System.currentTimeMillis();
            long exp = iat + ttlMillis;
            String payload = userId + ":" + iat + ":" + exp + ":" + AUDIENCE + ":" + SCOPE;

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
            // Split from the end to handle URNs containing colons in the userId
            int lastColon = ticketStr.lastIndexOf(':');
            if (lastColon <= 0) return null;
            String signature = ticketStr.substring(lastColon + 1);
            String remaining = ticketStr.substring(0, lastColon);

            int scopeColon = remaining.lastIndexOf(':');
            if (scopeColon <= 0) return null;
            String scope = remaining.substring(scopeColon + 1);
            remaining = remaining.substring(0, scopeColon);

            int audColon = remaining.lastIndexOf(':');
            if (audColon <= 0) return null;
            String aud = remaining.substring(audColon + 1);
            remaining = remaining.substring(0, audColon);

            int expColon = remaining.lastIndexOf(':');
            if (expColon <= 0) return null;
            long exp = Long.parseLong(remaining.substring(expColon + 1));
            remaining = remaining.substring(0, expColon);

            int iatColon = remaining.lastIndexOf(':');
            if (iatColon <= 0) return null;
            long iat = Long.parseLong(remaining.substring(iatColon + 1));
            String userId = remaining.substring(0, iatColon);

            if (!AUDIENCE.equals(aud) || !SCOPE.equals(scope)) {
                return null;
            }

            if (System.currentTimeMillis() > exp) {
                log.debug("MQTT ticket expired for user {}", userId);
                return null;
            }

            String secret = System.getProperty("javax.net.ssl.keyStorePassword");
            if (secret == null || secret.isEmpty()) {
                return null;
            }

            String payload = userId + ":" + iat + ":" + exp + ":" + aud + ":" + scope;
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
