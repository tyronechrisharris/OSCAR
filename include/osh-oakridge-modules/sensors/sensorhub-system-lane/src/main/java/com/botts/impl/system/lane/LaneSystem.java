/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 The Initial Developer is Botts Innovative Research Inc. Portions created by the Initial
 Developer are Copyright (C) 2025 the Initial Developer. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/

package com.botts.impl.system.lane;


import com.botts.impl.process.rs350.occupancy.Rs350OccupancyProcessConfig;
import com.botts.impl.process.rs350.occupancy.Rs350OccupancyProcessModule;
import com.botts.impl.sensor.aspect.AspectConfig;
import com.botts.impl.sensor.aspect.AspectSensor;
import com.botts.impl.sensor.aspect.comm.ModbusTCPCommProvider;
import com.botts.impl.sensor.aspect.comm.ModbusTCPCommProviderConfig;
import com.botts.impl.sensor.rapiscan.EMLConfig;
import com.botts.impl.sensor.rapiscan.RapiscanConfig;
import com.botts.impl.sensor.rapiscan.RapiscanSensor;
import com.botts.impl.sensor.rs350.RS350Config;
import com.botts.impl.sensor.rs350.RS350Sensor;
import com.botts.impl.system.lane.config.*;
import com.botts.impl.system.lane.helpers.occupancy.OccupancyWrapper;
import com.botts.impl.system.lane.helpers.webid.WebIdHelper;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.data.IDataProducerModule;
import org.sensorhub.api.database.IObsSystemDatabase;
import org.sensorhub.api.datastore.DataStoreException;
import org.sensorhub.api.datastore.obs.DataStreamFilter;
import org.sensorhub.api.datastore.obs.ObsFilter;
import org.sensorhub.api.datastore.system.SystemFilter;
import org.sensorhub.api.event.Event;
import org.sensorhub.api.event.EventUtils;
import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.api.module.ModuleEvent;
import org.sensorhub.api.sensor.SensorConfig;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.api.system.SystemRemovedEvent;
import org.sensorhub.impl.comm.TCPCommProviderConfig;
import org.sensorhub.impl.module.AbstractModule;
import org.sensorhub.impl.module.ModuleRegistry;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.sensor.SensorSystem;
import org.sensorhub.impl.sensor.SensorSystemConfig;
import org.sensorhub.impl.sensor.ffmpeg.FFMPEGSensor;
import org.sensorhub.impl.sensor.ffmpeg.FFMPEGSensorBase;
import org.sensorhub.impl.sensor.ffmpeg.config.FFMPEGConfig;
import org.sensorhub.impl.system.SystemDatabaseTransactionHandler;
import org.sensorhub.impl.utils.rad.output.N42Output;
import org.sensorhub.utils.MsgUtils;
import org.vast.util.Asserts;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Extended functionality of the SensorSystem class unique for Open Source Central Alarm (OSCAR)
 *
 * @author Alex Almanza
 * @author Kyle Fitzpatrick
 * @author Kalyn Stricklin
 * @since March 2025
 */
public class LaneSystem extends SensorSystem {

    private static final String URN_PREFIX = "urn:";
    private static final String LANE_SYSTEM_PREFIX = URN_PREFIX + "osh:system:lane:";
    private static final String RAPISCAN_URI = URN_PREFIX + "osh:sensor:rapiscan";
    private static final String RS350_URI = URN_PREFIX + "osh:sensor:rs350";
    private static final String ASPECT_URI = URN_PREFIX + "osh:sensor:aspect";
    private static final Set<Class<?>> WEBID_SENSORS = Set.of(RS350Sensor.class);
    private static final String DEFAULT_XMLID_PREFIX = "lane";

    AbstractSensorModule<?> existingRPMModule = null;
    IDataProducerModule<?> occupancyProducer = null;
    Flow.Subscription subscription = null;
    private ExecutorService threadPool = null;
    Map<String, FFMPEGConfig> ffmpegConfigs = null;
    private final Map<String, String> activeMtxPaths = new ConcurrentHashMap<>();
    OccupancyWrapper occupancyWrapper;
    WebIdHelper webIdHelper;

    AdjudicationControl adjudicationControl;
    WebIdOutput webIdOutput;
    N42Output<?> n42Output;

    private volatile boolean occupancyProducerInitRequested = false;
    private volatile boolean occupancyWrapperStarted = false;

    private String getMediaMtxIp() {
        String ip = System.getenv("MEDIAMTX_IP");
        if (ip == null || ip.isBlank()) {
            getLogger().error("CRITICAL: MEDIAMTX_IP environment variable is missing. Dynamic camera proxying will fail.");
            return null;
        }
        return ip;
    }

    private String getMediaMtxAddPathsApiBase() {
        String ip = getMediaMtxIp();
        return (ip == null) ? null : "http://" + ip + ":9997/v3/config/paths/add/";
    }

    private String getMediaMtxPatchPathsApiBase() {
        String ip = getMediaMtxIp();
        return (ip == null) ? null : "http://" + ip + ":9997/v3/config/paths/patch/";
    }

    private String getMediaMtxDeletePathsApiBase() {
        String ip = getMediaMtxIp();
        return (ip == null) ? null : "http://" + ip + ":9997/v3/config/paths/remove/";
    }

    private String getMediaMtxRtspBase() {
        String ip = getMediaMtxIp();
        if (ip == null) {
            return null;
        }

        String user = getEnvOrFile("MEDIAMTX_API_USER");
        String pass = getEnvOrFile("MEDIAMTX_API_PASS");

        if (user != null && !user.isBlank() && pass != null && !pass.isBlank()) {
            return "rtsp://" + user + ":" + pass + "@" + ip + ":8554/";
        }

        return "rtsp://" + ip + ":8554/";
    }

    private HttpClient getMediaMtxClient() {
        return HttpClient.newBuilder()
                .proxy(new ProxySelector() {
                    @Override
                    public List<Proxy> select(URI uri) {
                        return List.of(Proxy.NO_PROXY);
                    }

                    @Override
                    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                        getLogger().error("MediaMTX API connection failed at {}: {}", uri, ioe.getMessage());
                    }
                })
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    protected void doInit() throws SensorHubException {
        threadPool = Executors.newFixedThreadPool(10);
        ffmpegConfigs = new HashMap<>();
        occupancyWrapper = null;
        occupancyWrapperStarted = false;
        occupancyProducerInitRequested = false;

        // generate unique ID
        if (config.uniqueID != null && !config.uniqueID.equals(AUTO_ID)) {
            if (config.uniqueID.startsWith(URN_PREFIX)) {
                this.uniqueID = config.uniqueID;
                String suffix = config.uniqueID.replace(URN_PREFIX, "");
                generateXmlID(DEFAULT_XMLID_PREFIX, suffix);
            } else {
                this.uniqueID = createLaneUID(config.uniqueID);
                generateXmlID(DEFAULT_XMLID_PREFIX, config.uniqueID);
            }
        }

        // Ensure name is at most 12 characters
        if (config.name.length() > 12) {
            throw new SensorHubException("Lane name must be 12 or less characters", new IllegalArgumentException("Module name must be 12 or less characters"));
        }

        // Check state members too in case config hasn't been updated
        for (var member : getMembers().values()) {
            if (member instanceof RapiscanSensor || member instanceof AspectSensor) {
                existingRPMModule = (AbstractSensorModule<?>) member;
                occupancyProducer = (IDataProducerModule<?>) member;
            } else if (member instanceof FFMPEGSensor) {
                ffmpegConfigs.put(member.getLocalID(), ((FFMPEGSensor) member).getConfiguration());
            } else if (member instanceof RS350Sensor) {
                existingRPMModule = (AbstractSensorModule<?>) member;
            } else if (member instanceof Rs350OccupancyProcessModule) {
                occupancyProducer = (IDataProducerModule<?>) member;
            }
        }

        // Lane database and process setup
        if (getConfiguration().laneOptionsConfig != null) {
            // Initial RPM config
            var rpmConfig = getConfiguration().laneOptionsConfig.rpmConfig;
            if (rpmConfig != null && existingRPMModule == null) {
                // Create Rapiscan, Aspect, or RS350 config, then add as submodule
                var config = createRPMConfig(rpmConfig);
                existingRPMModule = (AbstractSensorModule<?>) registerSubmodule(config, false);
            }

            // Initial FFmpeg config
            var ffmpegConfigList = getConfiguration().laneOptionsConfig.ffmpegConfig;
            if (ffmpegConfigList != null) {
                for (var simpleConfig : ffmpegConfigList) {
                    final int index = ffmpegConfigList.indexOf(simpleConfig);
                    FFMPEGConfig config = createFFmpegConfig(simpleConfig, index);

                    // Provision camera async to avoid blocking startup.
                    CompletableFuture.runAsync(() -> {
                        try {
                            String pathName = buildMediaMtxPathName(config, index);
                            configureMediaMtxProxy(config, pathName);
                            var ffmpegModule = createFFmpegModule(config);

                            String ffmpegUID = ffmpegModule.getUniqueIdentifier();
                            if (ffmpegUID != null) {
                                activeMtxPaths.put(ffmpegUID, pathName);
                            } else {
                                getLogger().warn("Could not determine uniqueID for FFmpeg sensor {}, path tracking may fail", config.name);
                            }

                            if (occupancyWrapper != null) {
                                occupancyWrapper.addFFmpegSensor(ffmpegModule);
                            }
                        } catch (Exception e) {
                            getLogger().error("Failed to async provision MediaMTX proxy for camera {}: {}", config.name, e.getMessage());
                        }
                    }, threadPool);
                }
            }
        }

        webIdOutput = new WebIdOutput(this);
        addOutput(webIdOutput, false);

        n42Output = new N42Output<>(this);
        addOutput(n42Output, false);

        adjudicationControl = new AdjudicationControl(this);
        addControlInput(adjudicationControl);

        String statusMsg = "Note: ";
        if (existingRPMModule == null) {
            statusMsg += "No RPM driver found in lane.\n";
        }
        if (!statusMsg.equalsIgnoreCase("Note: ")) {
            reportStatus(statusMsg);
        }

        // Init sensor modules. Do not eagerly init process modules because RS350 process config
        // needs the parent system UID injected after the RPM sensor is available.
        for (var module : getMembers().values()) {
            if (module != null && module instanceof AbstractSensorModule<?>) {
                try {
                    module.init();
                } catch (Exception e) {
                    if (module instanceof RapiscanSensor || module instanceof AspectSensor || module instanceof RS350Sensor) {
                        getLogger().error("Cannot initialize system component {}", MsgUtils.moduleString(module), e);
                    } else {
                        getLogger().error("Cannot initialize system component {}", MsgUtils.moduleString(module), e);
                    }
                }
            }
        }

        getParentHub().getEventBus().newSubscription()
                // TODO: osh-core needs to use EventUtils for module topic IDs
                .withTopicID(EventUtils.getSystemRegistryTopicID(), ModuleRegistry.EVENT_GROUP_ID)
                .subscribe(this::handleLaneEvent)
                .thenAccept(subscription -> {
                    this.subscription = subscription;
                    subscription.request(Long.MAX_VALUE);
                    getLogger().info("Started module subscription to {}", getLocalID());
                });
    }

    public N42Output<?> getN42Output() {
        return this.n42Output;
    }

    private void configureMediaMtxProxy(FFMPEGConfig ffmpegConfig, String pathName) throws SensorHubException {
        if (ffmpegConfig == null || ffmpegConfig.connection == null) {
            throw new SensorHubException("Cannot configure MediaMTX proxy because FFmpeg connection config is missing");
        }

        String addApiBase = getMediaMtxAddPathsApiBase();
        String patchApiBase = getMediaMtxPatchPathsApiBase();
        if (addApiBase == null || patchApiBase == null) {
            return;
        }

        String rawUri = ffmpegConfig.connection.connectionString;
        if (rawUri == null || rawUri.isBlank()) {
            throw new SensorHubException("Cannot configure MediaMTX proxy because the raw RTSP URI is blank");
        }

        String payload = "{\"source\":\"" + jsonEscape(rawUri) + "\",\"sourceOnDemand\":true}";
        String encodedPathName = encodePathSegment(pathName);

        try {
            HttpResponse<String> response = sendMediaMtxRequest(
                    "PATCH",
                    patchApiBase + encodedPathName,
                    payload);

            if (!isSuccessfulMediaMtxResponse(response)) {
                getLogger().info("MediaMTX patch failed for path {} with HTTP {}. Attempting ADD.",
                        pathName, response.statusCode());

                response = sendMediaMtxRequest(
                        "POST",
                        addApiBase + encodedPathName,
                        payload);
            }

            if (!isSuccessfulMediaMtxResponse(response) && responseSuggestsExistingPath(response)) {
                getLogger().info("MediaMTX add reported an existing path for {} with HTTP {}. Retrying PATCH update.",
                        pathName, response.statusCode());

                response = sendMediaMtxRequest(
                        "PATCH",
                        patchApiBase + encodedPathName,
                        payload);
            }

            if (!isSuccessfulMediaMtxResponse(response)) {
                getLogger().error("MediaMTX path configuration failed for {} with HTTP {}: {}. Falling back to raw camera URI.",
                        pathName, response.statusCode(), response.body());
                return;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                getLogger().debug("Interrupted while waiting for MediaMTX to settle after configuring {}", pathName, e);
            }

            ffmpegConfig.connection.transportStreamPath = null;
            ffmpegConfig.connection.connectionString = getMediaMtxRtspBase() + pathName;
            ffmpegConfig.connection.useTCP = true;
        } catch (IOException e) {
            getLogger().error("Failed to reach MediaMTX API at {}. FFmpeg sensor will attempt to connect to the raw camera URI directly. Exception: {}", getMediaMtxAddPathsApiBase(), e.toString(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getLogger().error("Interrupted while configuring MediaMTX camera proxy for {}. Falling back to raw camera URI.", pathName);
        }
    }

    private boolean isSuccessfulMediaMtxResponse(HttpResponse<String> response) {
        return response != null && response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private boolean responseSuggestsExistingPath(HttpResponse<String> response) {
        if (response == null) {
            return false;
        }

        String body = response.body();
        return body != null && body.toLowerCase(Locale.ROOT).contains("already exists");
    }

    private boolean responseSuggestsMissingPath(HttpResponse<String> response) {
        if (response == null) {
            return false;
        }

        String body = response.body();
        if (body == null) {
            return false;
        }

        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("not found") || lower.contains("does not exist") || lower.contains("no such");
    }

    private String getEnvOrFile(String envVar) {
        String value = System.getenv(envVar);
        String fileVar = envVar + "_FILE";
        String filePath = System.getenv(fileVar);

        if (filePath != null && !filePath.isEmpty()) {
            try {
                value = new String(Files.readAllBytes(Paths.get(filePath))).trim();
                if (value.startsWith("\uFEFF")) {
                    value = value.substring(1);
                }
            } catch (IOException e) {
                getLogger().error("Failed to read {} from {}: {}", envVar, filePath, e.getMessage());
            }
        }
        return value;
    }

    private HttpResponse<String> sendMediaMtxRequest(String method, String url, String payload)
            throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));

        String user = getEnvOrFile("MEDIAMTX_API_USER");
        String pass = getEnvOrFile("MEDIAMTX_API_PASS");

        if (user != null && !user.isBlank() && pass != null && !pass.isBlank()) {
            String auth = user + ":" + pass;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            requestBuilder.header("Authorization", "Basic " + encodedAuth);
        }

        return getMediaMtxClient().send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String buildMediaMtxPathName(FFMPEGConfig ffmpegConfig, int index) {
        String laneName = "lane";
        if (getConfiguration() != null && getConfiguration().name != null && !getConfiguration().name.isBlank()) {
            laneName = getConfiguration().name;
        }

        String serial = "camera-" + index;
        if (ffmpegConfig != null && ffmpegConfig.serialNumber != null && !ffmpegConfig.serialNumber.isBlank()) {
            serial = ffmpegConfig.serialNumber;
        }

        String base = (laneName + "-" + serial)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");

        return base.isBlank() ? "camera-" + index : base;
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private void deleteMediaMtxPath(String pathName) {
        String apiBase = getMediaMtxDeletePathsApiBase();
        if (apiBase == null) {
            return;
        }

        try {
            String encodedPath = encodePathSegment(pathName);
            HttpResponse<String> response = sendMediaMtxRequest("POST", apiBase + encodedPath, "");

            if (isSuccessfulMediaMtxResponse(response) || responseSuggestsMissingPath(response)) {
                getLogger().info("MediaMTX path {} is absent after delete request", pathName);
            } else {
                getLogger().warn("Failed to delete MediaMTX path {} with HTTP {}: {}", pathName, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            getLogger().error("Error while deleting MediaMTX path {}: {}", pathName, e.getMessage());
        }
    }

    private synchronized FFMPEGSensorBase<?> createFFmpegModule(FFMPEGConfig ffmpegConfig) throws SensorHubException {
        var ffmpegModuleOpt = getMembers().values().stream().filter(
                module -> (
                        module instanceof FFMPEGSensor && ((FFMPEGSensor) module).getConfiguration().serialNumber.equals(ffmpegConfig.serialNumber)
                )
        ).findFirst();

        if (ffmpegModuleOpt.isEmpty()) {
            return (FFMPEGSensorBase<?>) registerSubmodule(ffmpegConfig, true);
        } else {
            FFMPEGSensor module = (FFMPEGSensor) ffmpegModuleOpt.get();
            ffmpegConfig.id = module.getLocalID();
            module.updateConfig(ffmpegConfig);
            return module;
        }
    }

    @Override
    protected void beforeInit() throws SensorHubException {
        removeOccupancyProcess();
        super.beforeInit();
    }

    @Override
    protected void beforeStart() throws SensorHubException {
        removeOccupancyProcess();
        super.beforeStart();
    }

    private void removeOccupancyProcess() throws SensorHubException {
        List<String> processIds = new ArrayList<>();
        for (var member : getMembers().values()) {
            if (member instanceof Rs350OccupancyProcessModule) {
                processIds.add(member.getConfiguration().id);
            }
        }

        for (String processId : processIds) {
            removeSubSystem(processId);
        }

        if (occupancyProducer != null && occupancyProducer != existingRPMModule) {
            occupancyProducer = null;
        }
        occupancyProducerInitRequested = false;
    }

    @Override
    protected void afterStart() throws SensorHubException {
        super.afterStart();

        if (occupancyWrapper != null) {
            stopOccupancyWrapper();
            occupancyWrapper.removeRpmSensor();
        }
        occupancyWrapper = null;
        occupancyWrapperStarted = false;

        if (existingRPMModule instanceof RS350Sensor rs350Module) {
            var processConfig = createOccupancyProcessConfig(rs350Module);
            if (processConfig != null) {
                occupancyProducer = (IDataProducerModule<?>) registerSubmodule(processConfig, false);
                occupancyProducerInitRequested = false;
                initializeOccupancyProducerIfReady();
            }
        } else {
            occupancyProducer = existingRPMModule;
            occupancyProducerInitRequested = false;
        }

        if (occupancyProducer != null) {
            occupancyWrapper = new OccupancyWrapper(getParentHub(), occupancyProducer);
            attachExistingFfmpegSensorsToWrapper();
            startOccupancyWrapperIfProducerStarted();
        }

        var db = getParentHub().getSystemDriverRegistry().getDatabase(getUniqueIdentifier());
        if (db == null) {
            getLogger().error("Cannot get database for lane {}", getUniqueIdentifier());
            return;
        }
        var obsStore = db.getObservationStore();
        if (obsStore == null) {
            getLogger().error("Cannot get obs store for lane {}", getUniqueIdentifier());
            return;
        }
        adjudicationControl.setObsStore(obsStore);

        if (webIdHelper != null) {
            webIdHelper.stop();
        }
        if (existingRPMModule != null && occupancyProducer != null && WEBID_SENSORS.contains(existingRPMModule.getClass())) {
            webIdHelper = new WebIdHelper(this, occupancyProducer);
        } else {
            webIdHelper = null;
        }
    }

    @Override
    protected void doStop() throws SensorHubException {
        super.doStop();

        if (webIdHelper != null) {
            webIdHelper.stop();
            webIdHelper = null;
        }

        stopOccupancyWrapper();

        if (occupancyProducer != null && occupancyProducer != existingRPMModule) {
            removeSubSystem(occupancyProducer.getConfiguration().id);
            occupancyProducer = null;
        }
        occupancyProducerInitRequested = false;

        if (occupancyWrapper != null) {
            occupancyWrapper.removeRpmSensor();
            occupancyWrapper = null;
        }
    }

    @Override
    public void cleanup() throws SensorHubException {
        super.cleanup();

        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        activeMtxPaths.forEach((uid, pathName) -> deleteMediaMtxPath(pathName));
        activeMtxPaths.clear();

        if (getConfiguration() != null && getConfiguration().autoDelete) {
            IObsSystemDatabase db = getParentHub().getSystemDriverRegistry().getDatabase(getUniqueIdentifier());

            String laneUID = getUniqueIdentifier();
            if (laneUID == null) {
                laneUID = createLaneUID(config.uniqueID);
            }
            if (db != null) {
                List<String> removalList = new ArrayList<>(List.of(laneUID));
                deleteSystemsFromDatabase(removalList);
            }
        }

        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
    }

    private String createLaneUID(String suffix) {
        return LANE_SYSTEM_PREFIX + suffix;
    }

    private synchronized void deleteSystemsFromDatabase(List<String> systemUIDs) {
        for (String sysUID : systemUIDs) {
            IObsSystemDatabase obsDatabase = getParentHub().getSystemDriverRegistry().getDatabase(sysUID);
            if (obsDatabase == null) {
                return;
            }

            var sysFilter = new SystemFilter.Builder()
                    .withUniqueIDs(sysUID)
                    .includeMembers(true)
                    .build();

            obsDatabase.getObservationStore().removeEntries(new ObsFilter.Builder()
                    .withDataStreams()
                    .withSystems(sysFilter)
                    .done()
                    .build());
            obsDatabase.getDataStreamStore().removeEntries(new DataStreamFilter.Builder()
                    .withSystems(sysFilter)
                    .build());
            obsDatabase.getSystemDescStore().removeEntries(sysFilter);
        }
    }

    private AbstractModule<?> registerSubmodule(ModuleConfig config, boolean initNow) throws SensorHubException {
        var newMember = new SensorSystemConfig.SystemMember();
        newMember.config = config;
        var newSubmodule = (AbstractModule<?>) addSubsystem(newMember);

        if (initNow) {
            newSubmodule.init();
        }

        scheduleConfigChangedNotification(newSubmodule);
        return newSubmodule;
    }

    private void scheduleConfigChangedNotification(AbstractModule<?> module) {
        threadPool.execute(() -> {
            try {
                module.waitForState(ModuleEvent.ModuleState.LOADED, 10000);
                eventHandler.publish(new ModuleEvent(this, ModuleEvent.Type.CONFIG_CHANGED));
            } catch (SensorHubException e) {
                getLogger().debug("Module {} did not reach LOADED before config change notification timeout", MsgUtils.moduleString(module), e);
            }
        });
    }

    private synchronized void initializeOccupancyProducer() throws SensorHubException {
        if (occupancyProducer == null || occupancyProducer == existingRPMModule || occupancyProducerInitRequested) {
            return;
        }

        occupancyProducerInitRequested = true;
        try {
            if (occupancyProducer instanceof Rs350OccupancyProcessModule rs350Process && existingRPMModule != null) {
                rs350Process.getConfiguration().systemUID = existingRPMModule.getUniqueIdentifier();
            }
            occupancyProducer.init();
        } catch (SensorHubException e) {
            occupancyProducerInitRequested = false;
            throw e;
        }
    }

    private void initializeOccupancyProducerIfReady() {
        if (existingRPMModule == null || occupancyProducer == null || occupancyProducer == existingRPMModule) {
            return;
        }

        try {
            if (existingRPMModule.waitForState(ModuleEvent.ModuleState.STARTED, 100)) {
                initializeOccupancyProducer();
            }
        } catch (SensorHubException e) {
            getLogger().debug("Occupancy producer not initialized yet; waiting for RPM STARTED event", e);
        }
    }

    private synchronized void startOccupancyWrapper() {
        if (occupancyWrapper == null || occupancyWrapperStarted) {
            return;
        }

        occupancyWrapper.start();
        occupancyWrapperStarted = true;
    }

    private synchronized void stopOccupancyWrapper() {
        if (occupancyWrapper == null || !occupancyWrapperStarted) {
            occupancyWrapperStarted = false;
            return;
        }

        occupancyWrapper.stop();
        occupancyWrapperStarted = false;
    }

    private void startOccupancyWrapperIfProducerStarted() {
        if (occupancyProducer == null || occupancyWrapper == null) {
            return;
        }

        try {
            if (occupancyProducer.waitForState(ModuleEvent.ModuleState.STARTED, 100)) {
                startOccupancyWrapper();
            }
        } catch (SensorHubException e) {
            getLogger().debug("Occupancy producer not started yet; waiting for producer STARTED event", e);
        }
    }

    private void attachExistingFfmpegSensorsToWrapper() {
        if (occupancyWrapper == null) {
            return;
        }

        for (var member : getMembers().values()) {
            if (member instanceof FFMPEGSensor ffmpegSensor) {
                occupancyWrapper.addFFmpegSensor(ffmpegSensor);
            }
        }
    }

    private void handleLaneEvent(Event e) {
        if (e instanceof SystemRemovedEvent event) {
            if (event.getParentGroupUID() != null && event.getParentGroupUID().equals(getUniqueIdentifier())) {

                if (event.getSystemUID().contains(RAPISCAN_URI) || event.getSystemUID().contains(ASPECT_URI) || event.getSystemUID().contains(RS350_URI)) {
                    stopOccupancyWrapper();
                    if (occupancyWrapper != null) {
                        occupancyWrapper.removeRpmSensor();
                    }
                    existingRPMModule = null;
                    occupancyProducer = null;
                    occupancyProducerInitRequested = false;
                } else if (occupancyProducer != null
                        && occupancyProducer != existingRPMModule
                        && Objects.equals(event.getSystemUID(), occupancyProducer.getUniqueIdentifier())) {
                    stopOccupancyWrapper();
                    if (occupancyWrapper != null) {
                        occupancyWrapper.removeRpmSensor();
                    }
                    occupancyProducer = null;
                    occupancyProducerInitRequested = false;
                }

                String pathName = activeMtxPaths.remove(event.getSystemUID());
                if (pathName != null) {
                    deleteMediaMtxPath(pathName);
                }
            }

        } else if (e instanceof ModuleEvent event) {
            if (event.getType() == ModuleEvent.Type.STATE_CHANGED) {

                if (event.getModule() == occupancyProducer) {
                    if (event.getNewState() == ModuleEvent.ModuleState.STARTED) {
                        startOccupancyWrapper();
                    } else if (event.getNewState() == ModuleEvent.ModuleState.STOPPING) {
                        stopOccupancyWrapper();
                    }
                }

                else if (event.getModule() instanceof FFMPEGSensor ffmpegDriver && getMembers().containsValue(ffmpegDriver)) {
                    var state = event.getNewState();
                    if (state == ModuleEvent.ModuleState.LOADED) {
                        if (!ffmpegConfigs.containsKey(ffmpegDriver.getLocalID())) {
                            ffmpegConfigs.put(ffmpegDriver.getLocalID(), ffmpegDriver.getConfiguration());
                        }
                    }

                    if (occupancyWrapper != null) {
                        if (state == ModuleEvent.ModuleState.STARTED) {
                            occupancyWrapper.addFFmpegSensor(ffmpegDriver);
                        } else {
                            occupancyWrapper.removeFFmpegSensor(ffmpegDriver);
                        }
                    }
                }

                else if ((event.getModule() instanceof RapiscanSensor || event.getModule() instanceof AspectSensor || event.getModule() instanceof RS350Sensor)
                        && getMembers().containsValue(event.getModule())) {
                    var state = event.getNewState();

                    if (state == ModuleEvent.ModuleState.STARTED) {
                        if (occupancyProducer != existingRPMModule) {
                            try {
                                initializeOccupancyProducer();
                            } catch (SensorHubException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                        if (occupancyWrapper != null && occupancyProducer != null) {
                            occupancyWrapper.setRpmSensor(occupancyProducer);
                        }
                    } else {
                        stopOccupancyWrapper();
                        if (occupancyWrapper != null) {
                            occupancyWrapper.removeRpmSensor();
                        }
                    }
                }
            }

            else if (event.getType().equals(ModuleEvent.Type.CONFIG_CHANGED)) {
                if (event.getModule() instanceof FFMPEGSensor ffmpegDriver) {
                    if (ffmpegDriver.getParentSystem() == null || !ffmpegDriver.getParentSystem().equals(this)) {
                        return;
                    }

                    var oldConfig = ffmpegConfigs.get(ffmpegDriver.getLocalID());

                    if (oldConfig == null) {
                        ffmpegConfigs.put(ffmpegDriver.getLocalID(), ffmpegDriver.getConfiguration());
                        return;
                    }

                    var newConfig = ffmpegDriver.getConfiguration();

                    if (newConfig.connection.useTCP != oldConfig.connection.useTCP
                            || !Objects.equals(newConfig.connection.connectionString, oldConfig.connection.connectionString)
                            || !Objects.equals(newConfig.connection.transportStreamPath, oldConfig.connection.transportStreamPath)) {
                        if (ffmpegDriver.getUniqueIdentifier() != null && getParentHub().getSystemDriverRegistry().getDatabase(getUniqueIdentifier()) != null) {

                            try {
                                ffmpegDriver.stop();
                                ffmpegDriver.waitForState(ModuleEvent.ModuleState.INITIALIZED, 5000);
                                deleteSystemsFromDatabase(List.of(ffmpegDriver.getUniqueIdentifier()));
                                getParentHub().getSystemDriverRegistry().register(ffmpegDriver);
                                if (ffmpegDriver.getConfiguration().autoStart) {
                                    ffmpegDriver.start();
                                }
                            } catch (SensorHubException ex) {
                                getLogger().error("Failed to delete FFmpeg driver from database. Please delete old FFmpeg system in database to avoid further issues" + ex.getMessage());
                            }
                        }
                        ffmpegConfigs.put(ffmpegDriver.getLocalID(), ffmpegDriver.getConfiguration());
                    }
                }
            }

            else if (event.getType().equals(ModuleEvent.Type.ERROR)) {
                if (Objects.equals(event.getModule(), this)) {
                    if (event.getError() != null && event.getError() instanceof SensorHubException hubException
                            && hubException.getCause() instanceof SensorException sensorException) {
                        if (sensorException.getCause() instanceof NullPointerException) {
                            IObsSystemDatabase database = null;
                            if (getUniqueIdentifier() != null) {
                                database = getParentHub().getSystemDriverRegistry().getDatabase(getUniqueIdentifier());
                            }
                            long systemAndMembers = 0;
                            if (database != null) {
                                systemAndMembers = database.getSystemDescStore().select(new SystemFilter.Builder().withUniqueIDs(getUniqueIdentifier()).includeMembers(true).build()).count();
                            }
                            if (systemAndMembers == 1) {
                                var transactionHandler = new SystemDatabaseTransactionHandler(getParentHub().getEventBus(), database);
                                try {
                                    transactionHandler.getSystemHandler(getUniqueIdentifier()).delete(true);
                                } catch (DataStoreException ex) {
                                    getLogger().error("Failed to remove system from database: {}", ex.getMessage());
                                }
                            }
                        }

                        if (sensorException.getCause() instanceof IllegalStateException) {
                            // TODO: Add fix. Current fix is restarting OSH
                        }

                    }
                }
            }

        }
    }

    private ModuleConfig createOccupancyProcessConfig(AbstractSensorModule<?> parentRpm) {
        ModuleConfig config = null;

        if (parentRpm instanceof RS350Sensor) {
            var rs350Config = new Rs350OccupancyProcessConfig();
            rs350Config.serialNumber = getConfiguration().uniqueID;
            rs350Config.moduleClass = Rs350OccupancyProcessModule.class.getCanonicalName();
            rs350Config.autoStart = true;
            config = rs350Config;
        }

        return config;
    }

    private SensorConfig createRPMConfig(RPMConfig rpmConfig) {
        Asserts.checkNotNull(rpmConfig.remoteHost);

        SensorConfig config = null;

        if (rpmConfig instanceof AspectRPMConfig aspectRPMConfig) {
            AspectConfig aspectConfig = new AspectConfig();
            aspectConfig.serialNumber = getConfiguration().uniqueID;
            aspectConfig.moduleClass = AspectSensor.class.getCanonicalName();

            var comm = aspectConfig.commSettings = new ModbusTCPCommProviderConfig();
            comm.protocol.remoteHost = aspectRPMConfig.remoteHost;
            comm.protocol.remotePort = aspectRPMConfig.remotePort;
            comm.protocol.addressRange.from = aspectRPMConfig.addressRange.from;
            comm.protocol.addressRange.to = aspectRPMConfig.addressRange.to;
            comm.connection.connectTimeout = 5000;
            comm.connection.reconnectAttempts = 10;
            comm.moduleClass = ModbusTCPCommProvider.class.getCanonicalName();
            config = aspectConfig;
        } else if (rpmConfig instanceof RapiscanRPMConfig rapiscanRPMConfig) {
            RapiscanConfig rapiscanConfig = new RapiscanConfig();
            rapiscanConfig.serialNumber = getConfiguration().uniqueID;
            rapiscanConfig.moduleClass = RapiscanSensor.class.getCanonicalName();

            if (rapiscanRPMConfig.emlConfig != null) {
                var eml = rapiscanConfig.emlConfig = new EMLConfig();
                eml.emlEnabled = rapiscanRPMConfig.emlConfig.emlEnabled;
                eml.isCollimated = rapiscanRPMConfig.emlConfig.isCollimated;
                eml.laneWidth = rapiscanRPMConfig.emlConfig.laneWidth;
            }

            var comm = rapiscanConfig.commSettings = new TCPCommProviderConfig();
            comm.protocol.remoteHost = rapiscanRPMConfig.remoteHost;
            comm.protocol.remotePort = rapiscanRPMConfig.remotePort;
            comm.connection.connectTimeout = 5000;
            comm.connection.reconnectAttempts = 10;
            config = rapiscanConfig;
        } else if (rpmConfig instanceof RS350RPMConfig rs350RPMConfig) {
            RS350Config rs350Config = new RS350Config();
            rs350Config.serialNumber = getConfiguration().uniqueID;
            rs350Config.moduleClass = RS350Sensor.class.getCanonicalName();

            TCPCommProviderConfig comm = new TCPCommProviderConfig();
            comm.protocol.remoteHost = rs350RPMConfig.remoteHost;
            comm.protocol.remotePort = rs350RPMConfig.remotePort;
            comm.connection.connectTimeout = 5000;
            comm.connection.reconnectAttempts = 10;
            rs350Config.commSettings = comm;
            config = rs350Config;
        } else {
            reportError("RPM Config specified is invalid, config must be of type AspectRPMConfig, RapiscanRPMConfig or RS350RPMConfig", new IllegalArgumentException());
        }

        config.name = getConfiguration().name + " - RPM";
        config.autoStart = true;
        return config;
    }

    private FFMPEGConfig createFFmpegConfig(FFMpegConfig ffmpegConfig, int videoIndex) {
        String defaultAxis = "/axis-media/media.amp?adjustablelivestream=1&resolution=640x480&videocodec=h264&videokeyframeinterval=15";

        Asserts.checkNotNull(ffmpegConfig.remoteHost);

        StringBuilder endpoint = new StringBuilder("rtsp://");

        if (ffmpegConfig.username != null && !ffmpegConfig.username.isBlank()) {
            endpoint.append(ffmpegConfig.username);
            endpoint.append(":");
            endpoint.append(ffmpegConfig.password);
            endpoint.append("@");
        }
        endpoint.append(ffmpegConfig.remoteHost);

        FFMPEGConfig config = new FFMPEGConfig();

        String path = defaultAxis;
        if (ffmpegConfig instanceof AxisCameraConfig axisVideoConfig) {
            path = axisVideoConfig.streamPath.getPath();
        } else if (ffmpegConfig instanceof SonyCameraConfig sonyVideoConfig) {
            path = sonyVideoConfig.streamPath;
        } else if (ffmpegConfig instanceof CustomCameraConfig customVideoConfig) {
            path = !customVideoConfig.streamPath.isEmpty() ? customVideoConfig.streamPath : defaultAxis;
        }

        endpoint.append(path);

        config.connection.useTCP = true;
        config.connection.fps = 24;
        config.name = getConfiguration().name + " - Camera " + videoIndex;
        config.serialNumber = "lane:" + getConfiguration().uniqueID + ":" + videoIndex;
        config.autoStart = true;
        config.connection.connectionString = endpoint.toString();
        config.moduleClass = FFMPEGSensor.class.getCanonicalName();
        config.connectionConfig.connectTimeout = 5000;
        config.connectionConfig.reconnectAttempts = 10;
        config.output.useHLS = true;
        config.output.useVideoFrames = false;
        return config;
    }

    @Override
    public LaneConfig getConfiguration() {
        return (LaneConfig) this.config;
    }
}
