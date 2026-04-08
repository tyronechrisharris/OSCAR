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


import com.botts.impl.sensor.aspect.AspectConfig;
import com.botts.impl.sensor.aspect.AspectSensor;
import com.botts.impl.sensor.aspect.comm.ModbusTCPCommProvider;
import com.botts.impl.sensor.aspect.comm.ModbusTCPCommProviderConfig;
import com.botts.impl.sensor.rapiscan.EMLConfig;
import com.botts.impl.sensor.rapiscan.RapiscanConfig;
import com.botts.impl.sensor.rapiscan.RapiscanSensor;
import com.botts.impl.system.lane.config.*;
import com.botts.impl.system.lane.helpers.occupancy.OccupancyWrapper;
import org.sensorhub.api.common.SensorHubException;
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
import org.sensorhub.impl.module.ModuleSecurity;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.sensor.SensorSystem;
import org.sensorhub.impl.sensor.SensorSystemConfig;
import org.sensorhub.impl.sensor.ffmpeg.FFMPEGSensorBase;
import org.sensorhub.impl.sensor.ffmpeg.config.FFMPEGConfig;
import org.sensorhub.impl.sensor.ffmpeg.FFMPEGSensor;
import org.sensorhub.impl.system.SystemDatabaseTransactionHandler;
import org.sensorhub.utils.MsgUtils;
import org.vast.util.Asserts;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

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
    private static final String ASPECT_URI = URN_PREFIX + "osh:sensor:aspect";
    private static final String PROCESS_URI = URN_PREFIX + "osh:process:occupancy";
    private static final String VIDEO_CLIPS_DIRECTORY = "clips/";
    private static final String VIDEO_STREAMING_DIRECTORY = "streaming/";

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
        return (ip == null) ? null : "rtsp://" + ip + ":8554/";
    }

    private HttpClient getMediaMtxClient() {
        return HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY) // CRITICAL: bypass SOCKS5 tailscale proxy for local traffic
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    AbstractSensorModule<?> existingRPMModule = null;
    Flow.Subscription subscription = null;
    private ExecutorService threadPool = null;
    Map<String, FFMPEGConfig> ffmpegConfigs = null;
    private final Map<String, String> activeMtxPaths = new ConcurrentHashMap<>();
    OccupancyWrapper occupancyWrapper;

    AdjudicationControl adjudicationControl;

    @Override
    protected void doInit() throws SensorHubException {
        threadPool = Executors.newFixedThreadPool(10);
        ffmpegConfigs = new HashMap<>();
        occupancyWrapper = null;

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
        if (config.name.length() > 12)
            throw new SensorHubException("Lane name must be 12 or less characters", new IllegalArgumentException("Module name must be 12 or less characters"));

        // Check state members too in case config hasn't been updated
        for (var member : getMembers().values()) {
            if (member instanceof RapiscanSensor || member instanceof AspectSensor) {
                existingRPMModule = (AbstractSensorModule<?>) member;
            }else if (member instanceof FFMPEGSensor) {
                ffmpegConfigs.put(member.getLocalID(), ((FFMPEGSensor) member).getConfiguration());
            }
        }

        // Lane database and process setup
        if (getConfiguration().laneOptionsConfig != null) {
            // Initial RPM config
            var rpmConfig = getConfiguration().laneOptionsConfig.rpmConfig;
            if (rpmConfig != null && existingRPMModule == null) {
                // Create Rapiscan or Aspect config, then add as submodule
                var config = createRPMConfig(rpmConfig);
                existingRPMModule = (AbstractSensorModule<?>) registerSubmodule(config);
            }
            if (existingRPMModule != null) {
                occupancyWrapper = new OccupancyWrapper(getParentHub(), existingRPMModule);
                //occupancyWrapper.videoNamePrefix = BASE_VIDEO_DIRECTORY + "lane" + getConfiguration().groupID + "/";
            }
            // Initial FFmpeg config
            var ffmpegConfigList = getConfiguration().laneOptionsConfig.ffmpegConfig;
            if (ffmpegConfigList != null) {
                for (var simpleConfig : ffmpegConfigList) {
                    final int index = ffmpegConfigList.indexOf(simpleConfig);
                    FFMPEGConfig config = createFFmpegConfig(simpleConfig, index);

                    // Provision camera async to avoid blocking startup
                    CompletableFuture.runAsync(() -> {
                        try {
                            String pathName = buildMediaMtxPathName(config, index);
                            configureMediaMtxProxy(config, pathName);
                            createFFmpegModule(config);
                            // Track the relationship for cleanup using deterministic UID
                            String ffmpegUID = "urn:osh:sensor:ffmpeg:" + config.serialNumber;
                            activeMtxPaths.put(ffmpegUID, pathName);
                        } catch (Exception e) {
                            getLogger().error("Failed to async provision MediaMTX proxy for camera {}: {}", config.name, e.getMessage());
                        }
                    }, threadPool);
                }
            }
        }

        adjudicationControl = new AdjudicationControl(this);
        addControlInput(adjudicationControl);


        String statusMsg = "Note: ";
        if (existingRPMModule == null)
            statusMsg += "No RPM driver found in lane.\n";
        if (!statusMsg.equalsIgnoreCase("Note: "))
            reportStatus(statusMsg);

        boolean rpmFailedToInit = false;

        // Init modules all modules except process module as it requires other modules to be started
        for (var module: getMembers().values()) {
            if (module != null) {
                try {
                    /*
                    threadPool.execute(() -> {
                        try {
                            module.init();
                        } catch (SensorHubException e) {
                            throw new RuntimeException(e);
                        }
                    });

                     */
                    module.init();
                }
                catch (Exception e) {
                    // If rapiscan fails to initialize, then don't load process module
                    if (module instanceof RapiscanSensor || module instanceof AspectSensor)
                        rpmFailedToInit = true;
                    getLogger().error("Cannot initialize system component {}", MsgUtils.moduleString(module), e);
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

    private void configureMediaMtxProxy(FFMPEGConfig ffmpegConfig, String pathName) throws SensorHubException {
        if (ffmpegConfig == null || ffmpegConfig.connection == null) {
            throw new SensorHubException("Cannot configure MediaMTX proxy because FFmpeg connection config is missing");
        }

        String apiBase = getMediaMtxAddPathsApiBase();
        if (apiBase == null) {
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
                    "POST",
                    apiBase + encodedPathName,
                    payload);

            if (response.statusCode() >= 400 && response.statusCode() < 500) {
                getLogger().info("MediaMTX add failed for path {} with HTTP {}. Attempting PATCH update.",
                        pathName, response.statusCode());

                response = sendMediaMtxRequest(
                        "PATCH",
                        getMediaMtxPatchPathsApiBase() + encodedPathName,
                        payload);
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                getLogger().error("MediaMTX path configuration failed for {} with HTTP {}: {}. Falling back to raw camera URI.",
                        pathName, response.statusCode(), response.body());
                return;
            }

            ffmpegConfig.connection.transportStreamPath = null;
            ffmpegConfig.connection.connectionString = getMediaMtxRtspBase() + pathName;
            ffmpegConfig.connection.useTCP = true;
        } catch (IOException e) {
            getLogger().error("Failed to reach MediaMTX API at {}. FFmpeg sensor will attempt to connect to the raw camera URI directly. Exception: {}", getMediaMtxAddPathsApiBase(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getLogger().error("Interrupted while configuring MediaMTX camera proxy for {}. Falling back to raw camera URI.", pathName);
        }
    }

    private String getEnvOrFile(String envVar) {
        String value = System.getenv(envVar);
        String fileVar = envVar + "_FILE";
        String filePath = System.getenv(fileVar);

        if (filePath != null && !filePath.isEmpty()) {
            try {
                value = new String(Files.readAllBytes(Paths.get(filePath))).trim();
                // Handle potential UTF-8 BOM
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

        // Inject Basic Auth if credentials are provided in environment or secret files
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
        if (apiBase == null) return;

        try {
            String encodedPath = encodePathSegment(pathName);
            HttpResponse<String> response = sendMediaMtxRequest("POST", apiBase + encodedPath, "");

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                getLogger().info("Successfully deleted MediaMTX path: {}", pathName);
            } else {
                getLogger().warn("Failed to delete MediaMTX path {} with HTTP {}: {}", pathName, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            getLogger().error("Error while deleting MediaMTX path {}: {}", pathName, e.getMessage());
        }
    }

    private FFMPEGSensorBase<?> createFFmpegModule(FFMPEGConfig ffmpegConfig) throws SensorHubException {
        // Get ffmpeg submodule with the same unique serial num
        // If there is a module registered for this serial number, then the driver was already registered
        var ffmpegModuleOpt = getMembers().values().stream().filter(
                module -> (
                        module instanceof FFMPEGSensor && ((FFMPEGSensor) module).getConfiguration().serialNumber.equals(ffmpegConfig.serialNumber)
                )
        ).findFirst();

        // Register the new module
        if (ffmpegModuleOpt.isEmpty()) {
            return (FFMPEGSensorBase<?>) registerSubmodule(ffmpegConfig);
        } else { // If there is already a module registered, then update the config
            FFMPEGSensor module = (FFMPEGSensor) ffmpegModuleOpt.get();
            ffmpegConfig.id = module.getLocalID();
            module.updateConfig(ffmpegConfig);
            return module;
        }
    }

    @Override
    protected void afterStart() throws SensorHubException {
        super.afterStart();

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
    }

    @Override
    public void cleanup() throws SensorHubException {
        super.cleanup();

        // Shut down thread pool first to stop new path provisioning
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

        // Cleanup MediaMTX paths
        activeMtxPaths.forEach((uid, pathName) -> deleteMediaMtxPath(pathName));
        activeMtxPaths.clear();

        // Auto delete lane data if specified
        if (getConfiguration() != null && getConfiguration().autoDelete) {
            IObsSystemDatabase db = getParentHub().getSystemDriverRegistry().getDatabase(getUniqueIdentifier());

            String laneUID = getUniqueIdentifier();
            if(laneUID == null)
                laneUID = createLaneUID(config.uniqueID);
            if (db != null) {
                // Remove lane if nothing else
                List<String> removalList = new ArrayList<>(List.of(laneUID));
                // Remove lane (and process) from database
                deleteSystemsFromDatabase(removalList);
            }
        }

        // Cancel and remove module/system subscription on cleanup
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
    }

    private String createLaneUID(String suffix) {
        return LANE_SYSTEM_PREFIX + suffix;
    }

    private synchronized void deleteSystemsFromDatabase(List<String> systemUIDs) {
        // Delete the system data. Use for-loop in case of different databases
        for (String sysUID : systemUIDs) {
            IObsSystemDatabase obsDatabase = getParentHub().getSystemDriverRegistry().getDatabase(sysUID);
            if (obsDatabase == null)
                return;

            var sysFilter = new SystemFilter.Builder()
                    .withUniqueIDs(sysUID)
                    .includeMembers(true)
                    .build();

            // Delete old observations
            obsDatabase.getObservationStore().removeEntries(new ObsFilter.Builder()
                    .withDataStreams()
                    .withSystems(sysFilter)
                    .done()
                    .build());
            // Delete old data streams
            obsDatabase.getDataStreamStore().removeEntries(new DataStreamFilter.Builder()
                    .withSystems(sysFilter)
                    .build());

            // Delete old systems
            obsDatabase.getSystemDescStore().removeEntries(sysFilter);
        }
    }

    private AbstractModule<?> registerSubmodule(ModuleConfig config) throws SensorHubException {
        var newMember = new SensorSystemConfig.SystemMember();
        newMember.config = config;
        var newSubmodule = (AbstractModule<?>) addSubsystem(newMember);

        // Wait for loaded module, then notify listeners of config changed. QOL for admin UI
        threadPool.execute(() -> {
            try {
                newSubmodule.waitForState(ModuleEvent.ModuleState.LOADED, 10000);
            } catch (SensorHubException e) {
                throw new RuntimeException(e);
            }
            eventHandler.publish(new ModuleEvent(this, ModuleEvent.Type.CONFIG_CHANGED));
        });

        return newSubmodule;
    }

    private void handleLaneEvent(Event e) {
        // TODO: Handle events for video drivers, RPM drivers, and process module
        // TODO: If lane is not saved to config, then delete database data and process?
        if (e instanceof SystemRemovedEvent event) {
            // Signifies that a subsystem was removed from the lane
            if (event.getParentGroupUID() != null && event.getParentGroupUID().equals(getUniqueIdentifier())) {

                // If RPM is removed, nullify local object
                if (event.getSystemUID().contains(RAPISCAN_URI) || event.getSystemUID().contains(ASPECT_URI)) {
                    occupancyWrapper.removeRpmSensor();
                    existingRPMModule = null;
                }

                // If FFmpeg sensor is removed, cleanup MediaMTX path
                String pathName = activeMtxPaths.remove(event.getSystemUID());
                if (pathName != null) {
                    deleteMediaMtxPath(pathName);
                }
            }

        }

        else if (e instanceof ModuleEvent event) {
            // Module STATE_CHANGED events
            if (event.getType() == ModuleEvent.Type.STATE_CHANGED) {

                if (event.getModule() == existingRPMModule) {
                    if (event.getNewState() == ModuleEvent.ModuleState.STARTED) {
                        occupancyWrapper.start();
                    } else if (event.getNewState() == ModuleEvent.ModuleState.STOPPING) {
                        occupancyWrapper.stop();
                    }
                }

                else if (event.getModule() instanceof FFMPEGSensor ffmpegDriver && getMembers().containsValue(ffmpegDriver)) {
                    var state = event.getNewState();
                    if (state == ModuleEvent.ModuleState.LOADED) {
                        if(!ffmpegConfigs.containsKey(ffmpegDriver.getLocalID()))
                            ffmpegConfigs.put(ffmpegDriver.getLocalID(), ffmpegDriver.getConfiguration());
                    }

                    if (state == ModuleEvent.ModuleState.STARTED) {
                        occupancyWrapper.addFFmpegSensor(ffmpegDriver);
                    } else {
                        occupancyWrapper.removeFFmpegSensor(ffmpegDriver);
                    }
                }

                else if ((event.getModule() instanceof RapiscanSensor || event.getModule() instanceof AspectSensor)
                && getMembers().containsValue(event.getModule())) {
                    var state = event.getNewState();

                    if (state == ModuleEvent.ModuleState.STARTED) {
                        occupancyWrapper.setRpmSensor((AbstractSensorModule<?>) event.getModule());
                    } else {
                        occupancyWrapper.removeRpmSensor();
                    }
                }
            }

            else if (event.getType().equals(ModuleEvent.Type.CONFIG_CHANGED)) {
                // FFmpeg config changed events
                if (event.getModule() instanceof FFMPEGSensor ffmpegDriver) {
                    // Let's only handle our own ffmpeg children
                    if (ffmpegDriver.getParentSystem() == null || !ffmpegDriver.getParentSystem().equals(this))
                        return;

                    var oldConfig = ffmpegConfigs.get(ffmpegDriver.getLocalID());

                    if (oldConfig == null) {
                        ffmpegConfigs.put(ffmpegDriver.getLocalID(), ffmpegDriver.getConfiguration());
                        return;
                    }

                    var newConfig = ffmpegDriver.getConfiguration();

                    // If important parts of configuration are updated, remove data from old driver
                    if (newConfig.connection.useTCP != oldConfig.connection.useTCP
                    || !Objects.equals(newConfig.connection.connectionString, oldConfig.connection.connectionString)
                    || !Objects.equals(newConfig.connection.transportStreamPath, oldConfig.connection.transportStreamPath)) {
                        if (ffmpegDriver.getUniqueIdentifier() != null && getParentHub().getSystemDriverRegistry().getDatabase(getUniqueIdentifier()) != null) {

                            try {
                                ffmpegDriver.stop();
                                ffmpegDriver.waitForState(ModuleEvent.ModuleState.INITIALIZED, 5000);
                                deleteSystemsFromDatabase(List.of(ffmpegDriver.getUniqueIdentifier()));
                                getParentHub().getSystemDriverRegistry().register(ffmpegDriver);
                                if (ffmpegDriver.getConfiguration().autoStart)
                                    ffmpegDriver.start();
                            } catch (SensorHubException ex) {
                                getLogger().error("Failed to delete FFmpeg driver from database. Please delete old FFmpeg system in database to avoid further issues" + ex.getMessage());
                            }
                        }
                        ffmpegConfigs.put(ffmpegDriver.getLocalID(), ffmpegDriver.getConfiguration());

                    }
                }
            }

            else if (event.getType().equals(ModuleEvent.Type.ERROR)) {
                // If lane fails to load, then perform some magic
                if (Objects.equals(event.getModule(), this)) {
                    // Check for SensorException
                    if (event.getError() != null && event.getError() instanceof SensorHubException hubException
                    && hubException.getCause() instanceof SensorException sensorException) {
                        // Check for driver failed to register error
                        if (sensorException.getCause() instanceof NullPointerException) {
                            // Find prev UID and delete from database
                            IObsSystemDatabase database = null;
                            if (getUniqueIdentifier() != null)
                                database = getParentHub().getSystemDriverRegistry().getDatabase(getUniqueIdentifier());
                            long systemAndMembers = 0;
                            if (database != null)
                                systemAndMembers = database.getSystemDescStore().select(new SystemFilter.Builder().withUniqueIDs(getUniqueIdentifier()).includeMembers(true).build()).count();
                            // Only delete from database if lane doesn't have children
                            if (systemAndMembers == 1) {
                                var transactionHandler = new SystemDatabaseTransactionHandler(getParentHub().getEventBus(), database);
                                try {
                                    transactionHandler.getSystemHandler(getUniqueIdentifier()).delete(true);
                                } catch (DataStoreException ex) {
                                    getLogger().error("Failed to remove system from database: {}", ex.getMessage());
                                }
                            }
                        }

                        // Check for illegal state (command receiver already connected, etc.)
                        if (sensorException.getCause() instanceof IllegalStateException) {
                            // TODO: Add fix. Current fix is restarting OSH
                        }

                    }
                }
            }

        }
    }

    private SensorConfig createRPMConfig(RPMConfig rpmConfig) {
        Asserts.checkNotNull(rpmConfig.remoteHost);

        SensorConfig config = null;

        if(rpmConfig instanceof AspectRPMConfig aspectRPMConfig){
            AspectConfig aspectConfig = new AspectConfig();
            aspectConfig.serialNumber = getConfiguration().uniqueID;
            aspectConfig.moduleClass = AspectSensor.class.getCanonicalName();

            // Setup communication config
            var comm = aspectConfig.commSettings = new ModbusTCPCommProviderConfig();
            comm.protocol.remoteHost = aspectRPMConfig.remoteHost;
            comm.protocol.remotePort = aspectRPMConfig.remotePort;

            comm.protocol.addressRange.from = aspectRPMConfig.addressRange.from;
            comm.protocol.addressRange.to = aspectRPMConfig.addressRange.to;
            // Update connection timeout to be 5 seconds instead of 3 seconds by default
            comm.connection.connectTimeout = 5000;
            comm.connection.reconnectAttempts = 10;
            comm.moduleClass = ModbusTCPCommProvider.class.getCanonicalName();
            config = aspectConfig;
        }else if(rpmConfig instanceof RapiscanRPMConfig rapiscanRPMConfig){
            RapiscanConfig rapiscanConfig = new RapiscanConfig();
            rapiscanConfig.serialNumber = getConfiguration().uniqueID;
            rapiscanConfig.moduleClass = RapiscanSensor.class.getCanonicalName();

            // add eml for rapiscan
            if (rapiscanRPMConfig.emlConfig != null) {
                var eml = rapiscanConfig.emlConfig = new EMLConfig();
                eml.emlEnabled = rapiscanRPMConfig.emlConfig.emlEnabled;
                eml.isCollimated = rapiscanRPMConfig.emlConfig.isCollimated;
                eml.laneWidth = rapiscanRPMConfig.emlConfig.laneWidth;
            }

            // Setup communication config
            var comm = rapiscanConfig.commSettings = new TCPCommProviderConfig();
            comm.protocol.remoteHost = rapiscanRPMConfig.remoteHost;
            comm.protocol.remotePort = rapiscanRPMConfig.remotePort;
            // Update connection timeout to be 5 seconds instead of 3 seconds by default
            comm.connection.connectTimeout = 5000;
            comm.connection.reconnectAttempts = 10;

            config = rapiscanConfig;
        }else{
            reportError("RPM Config specified is invalid, config must be of type AspectRPMConfig or RapiscanRPMConfig", new IllegalArgumentException());
        }

        config.name = getConfiguration().name + " - RPM";
        config.autoStart = true;

        return config;
    }
    
    private FFMPEGConfig createFFmpegConfig(FFMpegConfig ffmpegConfig, int videoIndex) {
        String defaultAxis = "/axis-media/media.amp?adjustablelivestream=1&resolution=640x480&videocodec=h264&videokeyframeinterval=15";

        Asserts.checkNotNull(ffmpegConfig.remoteHost);

        // This is the actual ffmpeg sensor config
        StringBuilder endpoint = new StringBuilder("rtsp://"); // All streams should be over rtsp

        // Add username and password if provided
        if (ffmpegConfig.username != null && !ffmpegConfig.username.isBlank()) {
            endpoint.append(ffmpegConfig.username);
            endpoint.append(":");
            endpoint.append(ffmpegConfig.password);
            endpoint.append("@");
        }
        endpoint.append(ffmpegConfig.remoteHost);

        FFMPEGConfig config = new FFMPEGConfig();

        String path = defaultAxis;

        if(ffmpegConfig instanceof AxisCameraConfig axisVideoConfig){
             path = axisVideoConfig.streamPath.getPath();
        }else if (ffmpegConfig instanceof SonyCameraConfig sonyVideoConfig){
             path = sonyVideoConfig.streamPath;
        }else if(ffmpegConfig instanceof CustomCameraConfig customVideoConfig){
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