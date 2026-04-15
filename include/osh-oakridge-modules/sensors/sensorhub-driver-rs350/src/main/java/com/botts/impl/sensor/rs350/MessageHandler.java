package com.botts.impl.sensor.rs350;

import com.botts.impl.sensor.rs350.messages.RS350Message;
import com.botts.impl.utils.n42.RadInstrumentDataType;
import org.sensorhub.api.common.BigId;
import org.sensorhub.api.datastore.obs.ObsFilter;
import org.sensorhub.api.datastore.system.SystemFilter;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.utils.rad.RADHelper;
import org.sensorhub.impl.utils.rad.output.OccupancyOutput;
import org.sensorhub.impl.utils.rad.webid.WebIdAnalyzer;
import org.sensorhub.impl.utils.rad.webid.WebIdRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
    private static final String RS350_OCCUPANCY_PROCESS_UID_PREFIX = "urn:osh:process:rs350-occupancy:";
    private static final String REPORT_BUCKET_NAME = "reports";

    final LinkedList<String> messageQueue = new LinkedList<String>();

    private final RADHelper radHelper = new RADHelper();
    private final InputStream msgIn;
    private final String messageDelimiter;
    private final AbstractSensorModule<?> sensorModule;
    private final WebIdAnalyzer webIdAnalyzer;
    private final boolean webIdPublishingEnabled;
    private final boolean bucketArchivalEnabled;
    private final long webIdPublishDelaySeconds;
    private final Object bucketStore;

    private final ArrayList<MessageListener> listeners = new ArrayList<MessageListener>();
    private final AtomicBoolean isProcessing = new AtomicBoolean(true);
    private final ScheduledExecutorService webIdScheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "rs350-webid-deferred");
            thread.setDaemon(true);
            return thread;
        }
    });

    private final Thread messageReader;
    private final Thread messageNotifier;

    private volatile boolean hasAlarm;
    private volatile long lastMessageTimeMs;
    private String laneUID;

    public interface MessageListener {
        void onNewMessage(RS350Message message);
    }

    public MessageHandler(
        InputStream msgIn,
        String messageDelimiter,
        AbstractSensorModule<?> sensorModule,
        WebIdAnalyzer webIdAnalyzer,
        boolean webIdPublishingEnabled,
        boolean bucketArchivalEnabled,
        long webIdPublishDelaySeconds
    ) {
        this.msgIn = msgIn;
        this.messageDelimiter = messageDelimiter;
        this.sensorModule = sensorModule;
        this.webIdAnalyzer = webIdAnalyzer;
        this.webIdPublishingEnabled = webIdPublishingEnabled && webIdAnalyzer != null;
        this.bucketArchivalEnabled = bucketArchivalEnabled;
        this.webIdPublishDelaySeconds = Math.max(0L, webIdPublishDelaySeconds);
        this.bucketStore = resolveBucketStore(sensorModule, bucketArchivalEnabled);
        this.laneUID = sensorModule != null ? sensorModule.getParentSystemUID() : null;
        this.lastMessageTimeMs = System.currentTimeMillis();
        this.hasAlarm = false;

        this.messageReader = new Thread(new Runnable() {
            @Override
            public void run() {
                readMessages();
            }
        }, "RS350-message-reader");

        this.messageNotifier = new Thread(new Runnable() {
            @Override
            public void run() {
                notifyListeners();
            }
        }, "RS350-message-notifier");

        this.messageReader.start();
        this.messageNotifier.start();
    }

    public void addMessageListener(MessageListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void stopProcessing() {
        isProcessing.set(false);

        synchronized (messageQueue) {
            messageQueue.notifyAll();
        }

        messageReader.interrupt();
        messageNotifier.interrupt();

        webIdScheduler.shutdown();
        try {
            if (!webIdScheduler.awaitTermination(webIdPublishDelaySeconds + 5L, TimeUnit.SECONDS)) {
                webIdScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            webIdScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public long getElapsedSinceLastMessageMillis() {
        return System.currentTimeMillis() - lastMessageTimeMs;
    }

    public long getTimeSinceLastMessage() {
        return getElapsedSinceLastMessageMillis();
    }

    private void readMessages() {
        ArrayList<Character> buffer = new ArrayList<Character>();

        while (isProcessing.get()) {
            int character;

            try {
                character = msgIn.read();
            } catch (IOException exception) {
                if (isProcessing.get()) {
                    log.warn("RS350 message reader stopped", exception);
                }
                break;
            }

            if (character == -1) {
                if (!isProcessing.get()) {
                    break;
                }

                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            if (character == 0x02) {
                try {
                    character = msgIn.read();
                    while (character != 0x03 && character != -1) {
                        buffer.add((char) character);
                        character = msgIn.read();
                    }
                } catch (IOException exception) {
                    if (isProcessing.get()) {
                        log.warn("Error while reading RS350 message frame", exception);
                    }
                    break;
                }

                if (!buffer.isEmpty()) {
                    StringBuilder builder = new StringBuilder(buffer.size());
                    for (char currentChar : buffer) {
                        builder.append(currentChar);
                    }

                    String n42Message = builder.toString().replaceAll("\\<\\?xml(.+?)\\?\\>", "").trim();
                    if (!n42Message.isEmpty()) {
                        synchronized (messageQueue) {
                            messageQueue.add(n42Message);
                            messageQueue.notifyAll();
                        }
                    }
                    buffer.clear();
                }
            }
        }
    }

    private void notifyListeners() {
        while (isProcessing.get() || hasPendingMessages()) {
            String currentMessage = null;

            synchronized (messageQueue) {
                while (messageQueue.isEmpty() && isProcessing.get()) {
                    try {
                        messageQueue.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        if (!isProcessing.get()) {
                            return;
                        }
                    }
                }

                if (!messageQueue.isEmpty()) {
                    currentMessage = messageQueue.removeFirst();
                }
            }

            if (currentMessage == null || currentMessage.isEmpty()) {
                continue;
            }

            try {
                RadInstrumentDataType radInstrumentDataType = radHelper.getRadInstrumentData(currentMessage);
                RS350Message message = new RS350Message(radInstrumentDataType);
                lastMessageTimeMs = System.currentTimeMillis();

                dispatchMessage(message);
                handleAlarmTransition(message, currentMessage);
            } catch (Exception e) {
                log.error("Error reading RS350 message", e);
            }
        }
    }

    private boolean hasPendingMessages() {
        synchronized (messageQueue) {
            return !messageQueue.isEmpty();
        }
    }

    private void dispatchMessage(RS350Message message) {
        ArrayList<MessageListener> listenerSnapshot;

        synchronized (listeners) {
            listenerSnapshot = new ArrayList<MessageListener>(listeners);
        }

        for (MessageListener listener : listenerSnapshot) {
            try {
                if (shouldDispatch(listener, message)) {
                    listener.onNewMessage(message);
                }
            } catch (Exception e) {
                log.error("Error delivering RS350 message to {}", listener.getClass().getSimpleName(), e);
            }
        }
    }

    private boolean shouldDispatch(MessageListener listener, RS350Message message) {
        if (listener instanceof OutputBase) {
            return ((OutputBase) listener).acceptsMessage(message);
        }

        return true;
    }

    private void handleAlarmTransition(RS350Message message, String n42Message) {
        boolean alarmPresent = message.getRs350RadAlarm() != null && message.getRs350DerivedData() != null;

        if (alarmPresent) {
            if (!hasAlarm) {
                hasAlarm = true;
                publishWebIdRequest(n42Message);
            }
        } else {
            hasAlarm = false;
        }
    }

    private void publishWebIdRequest(final String n42Message) {
        if (!webIdPublishingEnabled) {
            return;
        }

        if (laneUID == null && sensorModule != null) {
            log.warn("No lane UID found for sensor module {}, using sensor UID instead", sensorModule.getName());
            laneUID = sensorModule.getUniqueIdentifier();
        }

        try {
            webIdScheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        doPublishWebIdRequest(n42Message);
                    } catch (Exception e) {
                        log.error("Error publishing deferred WebID request", e);
                    }
                }
            }, webIdPublishDelaySeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to schedule deferred WebID request", e);
        }
    }

    private void doPublishWebIdRequest(String n42Message) {
        if (webIdAnalyzer == null) {
            return;
        }

        String effectiveLaneUid = laneUID;
        if (effectiveLaneUid == null && sensorModule != null) {
            effectiveLaneUid = sensorModule.getUniqueIdentifier();
        }

        String fileName = "rs350_" + effectiveLaneUid + "_" + System.currentTimeMillis() + ".n42";
        String occupancyObsId = lookupLatestOccupancyObsId();

        if (occupancyObsId == null) {
            log.warn("No RS350 occupancy obs id found for lane {} when publishing WebID request; the N42 will not be linked to an occupancy", effectiveLaneUid);
        } else {
            log.debug("Linking RS350 WebID request to occupancy obs id {}", occupancyObsId);
        }

        WebIdRequestContext requestContext = WebIdRequestContext.builder()
            .foregroundFile(fileName, n42Message.getBytes(StandardCharsets.UTF_8))
            .webIdEnabled(true)
            .bucketName(REPORT_BUCKET_NAME)
            .objectKey(fileName)
            .laneUid(effectiveLaneUid)
            .synthesizeBackground(true)
            .occupancyObsId(occupancyObsId)
            .multipartRequest(false)
            .build();

        Object analysis = webIdAnalyzer.processWebIdRequest(requestContext);

        if (analysis != null) {
            storeAnalysisJson(analysis.toString());
        }
    }

    private String lookupLatestOccupancyObsId() {
        if (laneUID == null || sensorModule == null) {
            return null;
        }

        try {
            SystemFilter laneMemberFilter = new SystemFilter.Builder()
                    .withUniqueIDs(laneUID)
                    .includeMembers(true)
                    .build();

            String[] rs350ProcessUids = sensorModule.getParentHub().getDatabaseRegistry().getFederatedDatabase()
                    .getSystemDescStore()
                    .select(laneMemberFilter)
                    .map(sys -> sys.getUniqueIdentifier())
                    .filter(uid -> uid != null && uid.startsWith(RS350_OCCUPANCY_PROCESS_UID_PREFIX))
                    .toArray(String[]::new);

            if (rs350ProcessUids.length == 0) {
                log.debug("No RS350 occupancy process system (prefix {}) found under lane {}", RS350_OCCUPANCY_PROCESS_UID_PREFIX, laneUID);
                return null;
            }

            SystemFilter rs350SysFilter = new SystemFilter.Builder()
                    .withUniqueIDs(rs350ProcessUids)
                    .build();

            java.util.List<BigId> keys = sensorModule.getParentHub().getDatabaseRegistry().getFederatedDatabase()
                    .getObservationStore()
                    .selectKeys(new ObsFilter.Builder()
                            .withLatestResult()
                            .withSystems(rs350SysFilter)
                            .withDataStreams().withOutputNames(OccupancyOutput.NAME).done()
                            .withLimit(1)
                            .build())
                    .toList();

            if (keys.isEmpty()) {
                log.debug("No RS350 occupancy observations yet under lane {}; rs350 process systems scanned: {}", laneUID, join(rs350ProcessUids));
                return null;
            }

            return sensorModule.getParentHub().getIdEncoders().getObsIdEncoder().encodeID(keys.get(0));
        } catch (Exception e) {
            log.warn("Failed to look up latest RS350 occupancy obs id for lane {}", laneUID, e);
            return null;
        }
    }

    private static String join(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values[i]);
        }
        return builder.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object resolveBucketStore(AbstractSensorModule<?> sensorModule, boolean bucketArchivalEnabled) {
        if (!bucketArchivalEnabled || sensorModule == null) {
            return null;
        }

        try {
            Class<?> bucketServiceClass = Class.forName("com.botts.api.service.bucket.IBucketService");
            Object bucketService = sensorModule.getParentHub().getModuleRegistry().getModuleByType((Class) bucketServiceClass);
            if (bucketService == null) {
                return null;
            }

            return bucketServiceClass.getMethod("getBucketStore").invoke(bucketService);
        } catch (ClassNotFoundException e) {
            log.debug("Bucket service API is not on the classpath; RS350 bucket archival will stay disabled");
            return null;
        } catch (Exception e) {
            log.warn("Could not initialize RS350 bucket archival support", e);
            return null;
        }
    }

    private void storeAnalysisJson(String analysisJson) {
        if (!bucketArchivalEnabled || bucketStore == null || analysisJson == null) {
            return;
        }

        try {
            Map<String, String> jsonMetadata = new HashMap<String, String>();
            jsonMetadata.put("Content-Type", "application/json");

            bucketStore.getClass()
                    .getMethod("createObject", String.class, InputStream.class, Map.class)
                    .invoke(bucketStore, REPORT_BUCKET_NAME, new ByteArrayInputStream(analysisJson.getBytes(StandardCharsets.UTF_8)), jsonMetadata);
        } catch (Exception e) {
            log.error("Failed to store WebID analysis JSON in bucket", e);
        }
    }
}
