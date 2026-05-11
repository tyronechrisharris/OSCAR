package com.botts.impl.sensor.rapiscan;


import com.opencsv.CSVReader;
import org.sensorhub.impl.utils.rad.model.Occupancy;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MessageHandler {

    private final RapiscanSensor parentSensor;

    List<String[]> csvList;
    CSVReader reader;
    Boolean currentOccupancy = false;
    Boolean isGammaAlarm = false;
    Boolean isNeutronAlarm = false;
    long occupancyStartTime;
    long occupancyEndTime;

    final static String ALARM = "Alarm";
    final static String BACKGROUND = "Background";
    final static String SCAN = "Scan";
    final static String FAULT_GH = "Fault - Gamma High";
    final static String FAULT_GL = "Fault - Gamma Low";
    final static String FAULT_NH = "Fault - Neutron High";

    String[] setupGamma1;
    String[] setupGamma2;
    String[] setupNeutron1;

    LinkedList<String[]> gammaScanRunningSumBatch;

    LinkedList<Integer> occupancyGammaBatch;
    LinkedList<Integer> occupancyNeutronBatch;

    int neutronMax;
    int gammaMax;

    private final java.util.concurrent.atomic.AtomicLong messageCounter = new java.util.concurrent.atomic.AtomicLong(0);
    private long lastLogTime = 0;

    private volatile long timeSinceLastMessage;

    private final InputStream msgIn;
    private Future<?> messageReaderFuture;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile BufferedReader bufferedReader;

    public long getTimeSinceLastMessage() {
        long now = System.currentTimeMillis();
        return (now - timeSinceLastMessage);
    }

    public MessageHandler(InputStream msgIn, RapiscanSensor parentSensor) {
        this.parentSensor = parentSensor;
        this.msgIn = msgIn;

        gammaScanRunningSumBatch = new LinkedList<>();
        occupancyGammaBatch = new LinkedList<>();
        occupancyNeutronBatch = new LinkedList<>();

        timeSinceLastMessage = System.currentTimeMillis();

        start();
    }

    public synchronized void start() {
        if (!isRunning.compareAndSet(false, true)) {
            parentSensor.getLogger().warn("MessageHandler already running");
            return;
        }

        try {
            messageReaderFuture = RapiscanThreadPoolManager.getInstance().submitMessageReader(() -> {
                parentSensor.getLogger().debug("Message reader started for sensor {}", parentSensor.getUniqueIdentifier());

                try {
                    while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                        try {
                            if (bufferedReader == null) {
                                bufferedReader = new BufferedReader(new InputStreamReader(msgIn));
                            }

                            String msgLine = bufferedReader.readLine();
                            if (msgLine == null) {
                                if (isRunning.get()) {
                                    parentSensor.getLogger().debug("End of stream reached; waiting for reconnect");
                                    bufferedReader = null;
                                    sleepQuietly(250);
                                }
                                continue;
                            }

                            reader = new CSVReader(new StringReader(msgLine));
                            csvList = reader.readAll();

                            if (!csvList.isEmpty() && csvList.get(0).length > 0) {
                                parentSensor.getDailyFileOutput().onNewMessage(msgLine);
                                onNewMainChar(csvList.get(0)[0], csvList.get(0));
                                timeSinceLastMessage = System.currentTimeMillis();
                            }
                        } catch (IOException e) {
                            if (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                                parentSensor.getLogger().warn("Message reader IO error: {}", e.getMessage());
                                bufferedReader = null;
                                sleepQuietly(250);
                            }
                        } catch (Exception e) {
                            if (isRunning.get()) {
                                parentSensor.getLogger().error("Error processing message: {}", e.getMessage(), e);
                                sleepQuietly(50);
                            }
                        }
                    }
                } finally {
                    bufferedReader = null;
                    messageReaderFuture = null;
                    isRunning.set(false);
                    parentSensor.getLogger().debug("Message reader exiting for sensor {}", parentSensor.getUniqueIdentifier());
                }
            });
        } catch (RuntimeException e) {
            isRunning.set(false);
            throw e;
        }
    }

    public synchronized void stop() {
        if (!isRunning.compareAndSet(true, false)) {
            return;
        }

        parentSensor.getLogger().debug("Stopping MessageHandler for sensor {}", parentSensor.getUniqueIdentifier());

        try {
            if (bufferedReader != null) {
                bufferedReader.close();
            } else {
                msgIn.close();
            }
        } catch (IOException e) {
            parentSensor.getLogger().debug("Error closing message input", e);
        }

        if (messageReaderFuture != null) {
            messageReaderFuture.cancel(true);

            try {
                messageReaderFuture.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                parentSensor.getLogger().warn("Message reader did not stop within timeout");
            } catch (Exception e) {
                // Expected when cancelled or closed
            } finally {
                messageReaderFuture = null;
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int[] getGammaForegroundCountsPerSecond() {
        int size = gammaScanRunningSumBatch.size();

        if (size != 5) {
            return null;
        }

        int[] gammaForegroundCountsPerSecond = new int[4];

        for (String[] line : gammaScanRunningSumBatch) {
            for (int i = 0; i < 4; i++) {
                gammaForegroundCountsPerSecond[i] += Integer.parseInt(line[i + 1]);
            }
        }

        return gammaForegroundCountsPerSecond;
    }

    void onNewMainChar(String mainChar, String[] csvLine) {
        long count = messageCounter.incrementAndGet();
        long now = System.currentTimeMillis();
        if (now - lastLogTime > 60000) {
            parentSensor.getLogger().info("[Rapiscan Driver] Heartbeat: received {} total messages for sensor {}", count, parentSensor.getUniqueIdentifier());
            lastLogTime = now;
        }

        if (parentSensor.getLogger().isDebugEnabled()) {
            parentSensor.getLogger().debug("[Rapiscan Raw] char={} line={}", mainChar, String.join(",", csvLine));
        }

        // Add scan data for EML service. Background and other data gives EML context, so we must show EML everything until end of occupancy
        if (parentSensor.getConfiguration().emlConfig.emlEnabled && !mainChar.equals("GB") && !mainChar.equals("NB")) {
            parentSensor.getEmlService().addScanDataLine(csvLine);
        }

        switch (mainChar) {
            // ------------------- NOT OCCUPIED
            case "GB":
                parentSensor.getGammaOutput().onNewMessage(csvLine, System.currentTimeMillis(), BACKGROUND, null);
                // Send latest Gamma Background to threshold calculator and EML service
                parentSensor.getGammaThresholdOutput().onNewBackground(csvLine);

                if(parentSensor.getConfiguration().emlConfig.emlEnabled)
                    parentSensor.getEmlService().setLatestGammaBackground(csvLine);
                break;
            case "GH":
                parentSensor.getGammaOutput().onNewMessage(csvLine, System.currentTimeMillis(), FAULT_GH, null);
                break;
            case "GL":
                parentSensor.getGammaOutput().onNewMessage(csvLine, System.currentTimeMillis(), FAULT_GL, null);
                break;
            case "NB":
                parentSensor.getNeutronOutput().onNewMessage(csvLine, System.currentTimeMillis(), BACKGROUND);

                if(parentSensor.getConfiguration().emlConfig.emlEnabled)
                    parentSensor.getEmlService().setLatestNeutronBackground(csvLine);
                break;
            case "NH":
                parentSensor.getNeutronOutput().onNewMessage(csvLine, System.currentTimeMillis(), FAULT_NH);
                break;

            // --------------- OCCUPIED
            case "GA":
                if (!currentOccupancy) parentSensor.getLogger().info("[Rapiscan Driver] Occupancy start detected (GA) for sensor {}", parentSensor.getUniqueIdentifier());
                gammaScanRunningSumBatch.addLast(csvLine);
                if (!currentOccupancy) {
                    occupancyStartTime = System.currentTimeMillis();
                    currentOccupancy = true;
                } else {
                    // 200ms
                    int gamma = Integer.parseInt(csvLine[1]) +
                            Integer.parseInt(csvLine[2]) +
                            Integer.parseInt(csvLine[3]) +
                            Integer.parseInt(csvLine[4]);
                        occupancyGammaBatch.addLast(gamma);
                }
                parentSensor.getGammaThresholdOutput().onNewForeground(getGammaForegroundCountsPerSecond());
                parentSensor.getGammaOutput().onNewMessage(csvLine, System.currentTimeMillis(), ALARM, getGammaForegroundCountsPerSecond());

                isGammaAlarm = true;
                break;

            case "GS":
                if (!currentOccupancy) parentSensor.getLogger().info("[Rapiscan Driver] Occupancy start detected (GS) for sensor {}", parentSensor.getUniqueIdentifier());
                gammaScanRunningSumBatch.addLast(csvLine);
                //usually the foreground value will switch to "GA" in an alarm state
                if (!currentOccupancy) {
                    occupancyStartTime = System.currentTimeMillis();
                    currentOccupancy = true;
                } else {
                    // 200ms
                    int gamma = Integer.parseInt(csvLine[1]) +
                            Integer.parseInt(csvLine[2]) +
                            Integer.parseInt(csvLine[3]) +
                            Integer.parseInt(csvLine[4]);
                    occupancyGammaBatch.addLast(gamma);
                }
                parentSensor.getGammaThresholdOutput().onNewForeground(getGammaForegroundCountsPerSecond());
                parentSensor.getGammaOutput().onNewMessage(csvLine, System.currentTimeMillis(), SCAN, getGammaForegroundCountsPerSecond());

                break;

            case "NA":
                if (!currentOccupancy) {
                    parentSensor.getLogger().info("[Rapiscan Driver] Occupancy start detected (NA) for sensor {}", parentSensor.getUniqueIdentifier());
                    occupancyStartTime = System.currentTimeMillis();
                    currentOccupancy = true;

                } else {
                    int neutron =  Integer.parseInt(csvLine[1]) +
                            Integer.parseInt(csvLine[2]) +
                            Integer.parseInt(csvLine[3]) +
                            Integer.parseInt(csvLine[4]);
                    occupancyNeutronBatch.addLast(neutron);
                }
                parentSensor.getNeutronOutput().onNewMessage(csvLine, System.currentTimeMillis(), ALARM);

                isNeutronAlarm = true;
                break;

            case "NS":
                if (!currentOccupancy) {
                    parentSensor.getLogger().info("[Rapiscan Driver] Occupancy start detected (NS) for sensor {}", parentSensor.getUniqueIdentifier());
                    occupancyStartTime = System.currentTimeMillis();
                    currentOccupancy = true;
                } else {
                    int neutron =  Integer.parseInt(csvLine[1]) +
                            Integer.parseInt(csvLine[2]) +
                            Integer.parseInt(csvLine[3]) +
                            Integer.parseInt(csvLine[4]);
                    occupancyNeutronBatch.addLast(neutron);
                }
                parentSensor.getNeutronOutput().onNewMessage(csvLine, System.currentTimeMillis(), SCAN);

                break;

            case "GX":
                parentSensor.getLogger().info("[Rapiscan Driver] Occupancy end detected (GX) for sensor {}", parentSensor.getUniqueIdentifier());
                occupancyEndTime = System.currentTimeMillis();
                gammaMax = getGammaMax(occupancyGammaBatch);
                neutronMax = Collections.max(occupancyNeutronBatch);

                Occupancy occupancy = new Occupancy.Builder()
                        .occupancyCount(Integer.parseInt(csvLine[1]))
                        .startTime(occupancyStartTime/1000d)
                        .endTime(occupancyEndTime/1000d)
                        .samplingTime(System.currentTimeMillis()/1000d)
                        .neutronBackground(Double.parseDouble(csvLine[2])/1000)
                        .gammaAlarm(isGammaAlarm)
                        .neutronAlarm(isNeutronAlarm)
                        .maxGammaCount(gammaMax)
                        .maxNeutronCount(neutronMax)
                        .build();

                parentSensor.getOccupancyOutput().setData(occupancy);
                currentOccupancy = false;
                isGammaAlarm = false;
                isNeutronAlarm = false;

                gammaScanRunningSumBatch.clear();
                //clear max batches for next occupancy
                occupancyNeutronBatch.clear();
                occupancyGammaBatch.clear();

                if (parentSensor.getConfiguration().emlConfig.emlEnabled) {
                    // TODO: Make better determination of whether occupancy ended
                    var results = parentSensor.getEmlService().processCurrentOccupancy();
                    parentSensor.getEmlScanContextualOutput().handleScanContextualMessage(results);
                    parentSensor.getEmlContextualOutput().handleContextualMessage(results);
                    parentSensor.getEmlAnalysisOutput().handleAnalysisMessage(results);
                }
                break;

            // -------------------- OTHER STATE
            case "TC":
                parentSensor.getTamperOutput().onNewMessage(false);
                break;
            case "TT":
                parentSensor.getTamperOutput().onNewMessage(true);
                break;
            case "SP":
                parentSensor.getSpeedOutput().onNewMessage(csvLine);
                break;
            case "SG1":
                setupGamma1 = csvLine;
                break;
            case "SG2":
                setupGamma2 = csvLine;
                break;
            case "SG3":
                parentSensor.getSetupGammaOutput().onNewMessage(setupGamma1, setupGamma2, csvLine);
                setupGamma1 = new String[]{""};
                setupGamma2 = new String[]{""};
                break;
            case "SN1":
                setupNeutron1 = csvLine;
                break;
            case "SN2":
                parentSensor.getSetupNeutronOutput().onNewMessage(setupNeutron1, csvLine);
                setupNeutron1 = new String[]{""};
                break;
        }

        if (gammaScanRunningSumBatch.size() > 4) {
            gammaScanRunningSumBatch.removeFirst();
        }
    }

    private int getGammaMax(List<Integer> gammaBatch) {
        if (gammaBatch == null || gammaBatch.size() < 5) {
            return 0;
        }

        int windowSize = 5;
        int currentSum = 0;
        int maxSum = 0;

        for (int i = 0; i < windowSize; i++) {
            currentSum += gammaBatch.get(i);
        }
        maxSum = currentSum;

        for (int i = windowSize; i < gammaBatch.size(); i++) {
            currentSum += gammaBatch.get(i) - gammaBatch.get(i - windowSize);
            if (currentSum > maxSum) {
                maxSum = currentSum;
            }
        }

        return maxSum;
    }

}