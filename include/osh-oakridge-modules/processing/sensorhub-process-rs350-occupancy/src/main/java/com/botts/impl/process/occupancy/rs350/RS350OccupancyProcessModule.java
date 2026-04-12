package com.botts.impl.process.occupancy.rs350;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import org.sensorhub.api.ISensorHub;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.api.data.IDataProducer;
import org.sensorhub.api.datastore.obs.DataStreamFilter;
import org.sensorhub.api.datastore.system.SystemFilter;
import org.sensorhub.api.event.Event;
import org.sensorhub.api.module.ModuleEvent;
import org.sensorhub.api.processing.OSHProcessInfo;
import org.sensorhub.impl.module.ModuleRegistry;
import org.sensorhub.impl.processing.AbstractProcessModule;
import org.vast.process.ProcessException;
import org.vast.swe.SWEHelper;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RS350OccupancyProcessModule extends AbstractProcessModule<RS350OccupancyProcessConfig> {

    public static final OSHProcessInfo INFO = new OSHProcessInfo("rs350occupancy", "RS-350 Occupancy data processing", null, RS350OccupancyProcessModule.class);

    private String parentSystemUID = null;
    private Flow.Subscription subscription = null;
    private DataComponent dailyFileStruct;
    private boolean isAlarming = false;
    private long lastHeartbeat = 0;
    private static final long DAILYFILE_INTERVAL_MS = 500;

    public RS350OccupancyProcessModule() {
        super(INFO);
        this.initAsync = true;
    }

    @Override
    protected void doInit() throws SensorHubException {
        parentSystemUID = config.systemUID;
        if (parentSystemUID == null || parentSystemUID.isBlank()) {
            if (getParentSystemUID() == null || getParentSystemUID().isBlank())
                throw new SensorHubException("Please specify a system UID, or put process under parent system.");
            parentSystemUID = getParentSystemUID();
        }

        dailyFileStruct = DailyFileStruct.create();
        outputs.put("dailyFile", dailyFileStruct);

        // Listen for alarm events
        if (subscription == null)
            getParentHub().getEventBus().newSubscription()
                .withTopicID(ModuleRegistry.EVENT_GROUP_ID)
                .consume(this::handleEvent)
                .thenAccept(s -> {
                    subscription = s;
                    s.request(Long.MAX_VALUE);
                });

        setState(ModuleEvent.ModuleState.INITIALIZED);
    }

    private void handleEvent(Event e) {
        if (e instanceof ModuleEvent event && event.getType().equals(ModuleEvent.Type.DATA)) {
             // Logic to track alarm state from RS350 outputs
             // This is a placeholder for the actual alarm detection logic
        }
    }

    @Override
    protected void doStart() throws SensorHubException {
        getParentHub().getScheduler().scheduleAtFixedRate(() -> {
            try {
                sendHeartbeat();
            } catch (Exception e) {
                getLogger().error("Error sending heartbeat", e);
            }
        }, 0, DAILYFILE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() {
        DataBlock dataBlock = dailyFileStruct.createDataBlock();
        Instant now = Instant.now();
        dataBlock.setTimeStamp(0, now);
        dataBlock.setBooleanValue(1, isAlarming);

        eventHandler.publish(new DataEvent(now.toEpochMilli(), this.getOutput("dailyFile"), dataBlock));
    }

    @Override
    protected void doStop() {
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
    }
}
