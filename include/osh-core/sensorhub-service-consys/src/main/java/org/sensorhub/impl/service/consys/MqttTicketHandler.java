package org.sensorhub.impl.service.consys;

import java.io.IOException;
import java.time.Instant;
import org.sensorhub.api.security.ISecurityManager;
import org.sensorhub.api.security.IUserInfo;
import org.sensorhub.impl.security.MqttTicketUtils;
import org.sensorhub.impl.service.consys.resource.RequestContext;
import org.sensorhub.impl.service.consys.resource.ResourceFormat;

public class MqttTicketHandler extends BaseHandler {
    public static final String[] NAMES = { "mqtt-ticket" };
    private static final long TICKET_TTL_MILLIS = 15 * 60 * 1000; // 15 minutes
    private static final int REFRESH_AFTER_SECONDS = 12 * 60; // 12 minutes

    @Override
    public String[] getNames() {
        return NAMES;
    }

    @Override
    public void doGet(RequestContext ctx) throws InvalidRequestException, IOException, SecurityException {
        doPost(ctx);
    }

    @Override
    public void doPost(RequestContext ctx) throws InvalidRequestException, IOException, SecurityException {
        IUserInfo user = ctx.getSecurityHandler().getCurrentUser();
        if (user == null || ISecurityManager.ANONYMOUS_USER.equals(user.getId())) {
            ctx.setResponseContentType(ResourceFormat.JSON.getMimeType());
            String response = "{\n  \"status\": 401,\n  \"message\": \"Authentication required\"\n}";
            ctx.getOutputStream().write(response.getBytes());
            return;
        }

        String ticket = MqttTicketUtils.createTicket(user.getId(), TICKET_TTL_MILLIS);
        if (ticket == null) {
            throw ServiceErrors.internalError("Error generating ticket");
        }

        Instant expiresAt = Instant.now().plusMillis(TICKET_TTL_MILLIS);

        ctx.setResponseContentType(ResourceFormat.JSON.getMimeType());
        String response = String.format(
            "{\n" +
            "  \"wsPath\": \"/sensorhub/mqtt\",\n" +
            "  \"username\": \"__mqtt_ticket__\",\n" +
            "  \"password\": \"%s\",\n" +
            "  \"expiresAt\": \"%s\",\n" +
            "  \"refreshAfterSeconds\": %d\n" +
            "}",
            ticket, expiresAt.toString(), REFRESH_AFTER_SECONDS
        );
        ctx.getOutputStream().write(response.getBytes());
    }

    @Override
    public void doPut(RequestContext ctx) throws InvalidRequestException, IOException, SecurityException {
        throw ServiceErrors.unsupportedOperation("");
    }

    @Override
    public void doDelete(RequestContext ctx) throws InvalidRequestException, IOException, SecurityException {
        throw ServiceErrors.unsupportedOperation("");
    }
}
