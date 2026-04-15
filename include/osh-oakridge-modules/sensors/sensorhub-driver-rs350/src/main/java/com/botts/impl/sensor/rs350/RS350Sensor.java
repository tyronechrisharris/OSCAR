/***************************** BEGIN LICENSE BLOCK ***************************
 Copyright (C) 2023 Botts Innovative Research, Inc. All Rights Reserved.
 ******************************* END LICENSE BLOCK ***************************/
package com.botts.impl.sensor.rs350;

import org.sensorhub.api.comm.ICommProvider;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.utils.rad.interfaces.IWebIdProvider;
import org.sensorhub.impl.utils.rad.webid.WebIdAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public class RS350Sensor extends AbstractSensorModule<RS350Config> {

    private static final Logger logger = LoggerFactory.getLogger(RS350Sensor.class);
    private static final long CONNECTION_MONITOR_INTERVAL_MILLIS = 1000L;

    private final Object lifecycleLock = new Object();

    ICommProvider<?> commProvider;
    LocationOutput locationOutput;
    StatusOutput statusOutput;
    BackgroundOutput backgroundOutput;
    ForegroundOutput foregroundOutput;
    DerivedDataOutput derivedDataOutput;
    AlarmOutput alarmOutput;
    ConnectionStatusOutput connectionStatusOutput;
    MessageHandler messageHandler;
    InputStream msgIn;

    private volatile boolean running;
    private Thread connectionMonitorThread;
    private volatile long lastReconnectAttemptMs;

    public RS350Sensor() {
    }

    @Override
    protected void doInit() throws SensorHubException {
        super.doInit();

        generateUniqueID("urn:rsi:rs350:", config.serialNumber);
        generateXmlID("rsi_rs350_", config.serialNumber);

        createOutputs();
    }

    private void createOutputs() {
        if (config.outputs.enableLocationOutput) {
            locationOutput = new LocationOutput(this);
            addOutput(locationOutput, false);
            locationOutput.init();
        }

        if (config.outputs.enableStatusOutput) {
            statusOutput = new StatusOutput(this);
            addOutput(statusOutput, false);
            statusOutput.init();
        }

        if (config.outputs.enableBackgroundOutput) {
            backgroundOutput = new BackgroundOutput(this);
            addOutput(backgroundOutput, false);
            backgroundOutput.init();
        }

        if (config.outputs.enableForegroundOutput) {
            foregroundOutput = new ForegroundOutput(this);
            addOutput(foregroundOutput, false);
            foregroundOutput.init();
        }

        if (config.outputs.enableDerivedData) {
            derivedDataOutput = new DerivedDataOutput(this);
            addOutput(derivedDataOutput, false);
            derivedDataOutput.init();
        }

        if (config.outputs.enableAlarmOutput) {
            alarmOutput = new AlarmOutput(this);
            addOutput(alarmOutput, false);
            alarmOutput.init();
        }

        if (config.outputs.enableConnectionStatusOutput) {
            connectionStatusOutput = new ConnectionStatusOutput(this);
            addOutput(connectionStatusOutput, false);
            connectionStatusOutput.init();
        }
    }

    @Override
    protected void doStart() throws SensorHubException {
        startStream();
        running = true;
        startConnectionMonitor();
        publishConnectionStatus(true);
    }

    @Override
    protected void doStop() throws SensorHubException {
        running = false;
        stopConnectionMonitor();
        stopMessageHandler();
        stopCommProvider();
        publishConnectionStatus(false);
    }

    @Override
    public boolean isConnected() {
        return commProvider != null && commProvider.isStarted();
    }

    private void startStream() throws SensorHubException {
        synchronized (lifecycleLock) {
            stopMessageHandler();
            stopCommProvider();
            startCommProvider();
            openInputStream();
            createMessageHandler();
            registerMessageListeners();
        }
    }

    private void startCommProvider() throws SensorHubException {
        if (config.commSettings == null) {
            throw new SensorHubException("No communication settings specified");
        }

        try {
            commProvider = (ICommProvider<?>) getParentHub().getModuleRegistry().loadSubModule(config.commSettings, true);
            commProvider.start();

            if (!commProvider.isStarted()) {
                throw new SensorHubException("Comm Provider failed to start. Check communication settings.");
            }
        } catch (SensorHubException e) {
            commProvider = null;
            throw e;
        } catch (Exception e) {
            commProvider = null;
            throw new SensorException("Error while starting communications ", e);
        }
    }

    private void openInputStream() throws SensorHubException {
        try {
            msgIn = new BufferedInputStream(commProvider.getInputStream());
        } catch (IOException e) {
            throw new SensorException("Error while initializing communications ", e);
        }
    }

    private void createMessageHandler() {
        WebIdAnalyzer webIdAnalyzer = null;

        if (config.enableWebIdIntegration) {
            try {
                IWebIdProvider webIdProvider = getParentHub().getModuleRegistry().getModuleByType(IWebIdProvider.class);
                if (webIdProvider != null) {
                    webIdAnalyzer = new WebIdAnalyzer(webIdProvider.getWebIdClient(), getParentHub());
                } else {
                    logger.info("WebID integration is enabled for RS350, but no IWebIdProvider is available");
                }
            } catch (Exception e) {
                logger.warn("Could not attach WebID Analysis to RS350", e);
            }
        }

        messageHandler = new MessageHandler(
            msgIn,
            "</RadInstrumentData>",
            this,
            webIdAnalyzer,
            config.enableWebIdIntegration,
            config.enableWebIdBucketArchival,
            config.webIdPublishDelaySeconds
        );
    }

    private void registerMessageListeners() {
        if (config.outputs.enableLocationOutput && locationOutput != null) {
            messageHandler.addMessageListener(locationOutput);
        }

        if (config.outputs.enableStatusOutput && statusOutput != null) {
            messageHandler.addMessageListener(statusOutput);
        }

        if (config.outputs.enableBackgroundOutput && backgroundOutput != null) {
            messageHandler.addMessageListener(backgroundOutput);
        }

        if (config.outputs.enableForegroundOutput && foregroundOutput != null) {
            messageHandler.addMessageListener(foregroundOutput);
        }

        if (config.outputs.enableDerivedData && derivedDataOutput != null) {
            messageHandler.addMessageListener(derivedDataOutput);
        }

        if (config.outputs.enableAlarmOutput && alarmOutput != null) {
            messageHandler.addMessageListener(alarmOutput);
        }
    }

    private void stopMessageHandler() {
        if (messageHandler != null) {
            messageHandler.stopProcessing();
            messageHandler = null;
        }
    }

    private void stopCommProvider() {
        if (commProvider != null) {
            try {
                commProvider.stop();
            } catch (Exception e) {
                logger.error("Uncaught exception attempting to stop comms module", e);
            } finally {
                commProvider = null;
                msgIn = null;
            }
        }
    }

    private void startConnectionMonitor() {
        if (connectionMonitorThread != null && connectionMonitorThread.isAlive()) {
            return;
        }

        connectionMonitorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                monitorConnection();
            }
        }, "RS350-connection-monitor");
        connectionMonitorThread.setDaemon(true);
        connectionMonitorThread.start();
    }

    private void stopConnectionMonitor() {
        if (connectionMonitorThread != null) {
            connectionMonitorThread.interrupt();
            connectionMonitorThread = null;
        }
    }

    private void monitorConnection() {
        long timeoutMillis = Math.max(1, config.messageTimeoutSeconds) * 1000L;
        long reconnectIntervalMillis = Math.max(1, config.reconnectIntervalSeconds) * 1000L;

        while (running) {
            boolean receivingMessages = isConnected()
                && messageHandler != null
                && messageHandler.getElapsedSinceLastMessageMillis() <= timeoutMillis;

            publishConnectionStatus(receivingMessages);

            if (!receivingMessages && shouldAttemptReconnect(reconnectIntervalMillis)) {
                tryReconnect();
            }

            try {
                Thread.sleep(CONNECTION_MONITOR_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private boolean shouldAttemptReconnect(long reconnectIntervalMillis) {
        if (!running) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastReconnectAttemptMs < reconnectIntervalMillis) {
            return false;
        }

        lastReconnectAttemptMs = now;
        return true;
    }

    private void tryReconnect() {
        logger.info("RS350 connection is stale. Attempting reconnect...");

        try {
            startStream();
            publishConnectionStatus(true);
        } catch (SensorHubException e) {
            logger.warn("RS350 reconnect attempt failed", e);
            publishConnectionStatus(false);
        }
    }

    private void publishConnectionStatus(boolean connected) {
        if (connectionStatusOutput != null) {
            connectionStatusOutput.publishStatus(connected);
        }
    }
}
