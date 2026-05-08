package org.sensorhub.impl.security;

import org.junit.Test;
import static org.junit.Assert.*;

public class MqttTicketUtilsTest {

    @Test
    public void testCreateAndValidateTicket() {
        System.setProperty("javax.net.ssl.keyStorePassword", "test-secret");
        String userId = "testUser";
        long ttl = 10000; // 10 seconds

        String ticket = MqttTicketUtils.createTicket(userId, ttl);
        assertNotNull(ticket);

        String validatedUser = MqttTicketUtils.validateTicket(ticket);
        assertEquals(userId, validatedUser);
    }

    @Test
    public void testUrnUserId() {
        System.setProperty("javax.net.ssl.keyStorePassword", "test-secret");
        String userId = "urn:osh:user:admin:12345";
        long ttl = 10000;

        String ticket = MqttTicketUtils.createTicket(userId, ttl);
        assertNotNull(ticket);

        String validatedUser = MqttTicketUtils.validateTicket(ticket);
        assertEquals(userId, validatedUser);
    }

    @Test
    public void testExpiredTicket() throws InterruptedException {
        System.setProperty("javax.net.ssl.keyStorePassword", "test-secret");
        String userId = "testUser";
        long ttl = 100; // 100ms

        String ticket = MqttTicketUtils.createTicket(userId, ttl);
        Thread.sleep(200);

        String validatedUser = MqttTicketUtils.validateTicket(ticket);
        assertNull(validatedUser);
    }

    @Test
    public void testTamperedTicket() {
        System.setProperty("javax.net.ssl.keyStorePassword", "test-secret");
        String userId = "testUser";
        long ttl = 10000;

        String ticket = MqttTicketUtils.createTicket(userId, ttl);
        String tamperedTicket = ticket.replace("mqtt-ws", "wrong-aud");

        String validatedUser = MqttTicketUtils.validateTicket(tamperedTicket);
        assertNull(validatedUser);
    }

    @Test
    public void testMissingSecretFails() {
        System.clearProperty("javax.net.ssl.keyStorePassword");
        String userId = "testUser";

        String ticket = MqttTicketUtils.createTicket(userId, 10000);
        assertNull(ticket);
    }

    @Test
    public void testTamperedScopeFails() {
        System.setProperty("javax.net.ssl.keyStorePassword", "test-secret");
        String userId = "testUser";

        String ticket = MqttTicketUtils.createTicket(userId, 10000);
        String tamperedTicket = ticket.replace(":subscribe:", ":publish:");

        String validatedUser = MqttTicketUtils.validateTicket(tamperedTicket);
        assertNull(validatedUser);
    }
}
