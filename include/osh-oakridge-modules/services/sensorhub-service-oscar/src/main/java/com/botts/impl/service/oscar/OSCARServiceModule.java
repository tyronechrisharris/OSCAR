/*******************************************************************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 The Initial Developer is Botts Innovative Research Inc. Portions created by the Initial
 Developer are Copyright (C) 2025 the Initial Developer. All Rights Reserved.

 ******************************************************************************/

package com.botts.impl.service.oscar;

import com.botts.api.service.bucket.IBucketService;
import com.botts.api.service.bucket.IBucketStore;
import com.botts.impl.service.oscar.purge.DatabasePurger;
import com.botts.impl.service.oscar.reports.RequestReportControl;
import com.botts.impl.service.oscar.siteinfo.SiteInfoOutput;
import com.botts.impl.service.oscar.siteinfo.SitemapDiagramHandler;
import com.botts.impl.service.oscar.spreadsheet.SpreadsheetHandler;
import com.botts.impl.service.oscar.stats.StatisticsControl;
import com.botts.impl.service.oscar.stats.StatisticsOutput;
import com.botts.impl.service.oscar.video.VideoRetention;
import org.sensorhub.impl.utils.rad.interfaces.IWebIdProvider;
import org.sensorhub.impl.utils.rad.webid.WebIdClient;
import com.botts.impl.service.oscar.webid.WebIdResourceHandler;
import com.botts.impl.system.lane.LaneSystem;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.data.IDataProducerModule;
import org.sensorhub.api.database.IObsSystemDatabase;
import org.sensorhub.api.datastore.obs.DataStreamFilter;
import org.sensorhub.api.datastore.obs.ObsFilter;
import org.sensorhub.api.datastore.system.SystemFilter;
import org.sensorhub.api.module.IModule;
import org.sensorhub.api.module.ModuleEvent;
import org.sensorhub.impl.module.AbstractModule;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class OSCARServiceModule extends AbstractModule<OSCARServiceConfig> implements IWebIdProvider {

    SiteInfoOutput siteInfoOutput;
    RequestReportControl reportControl;
    StatisticsOutput statsOutput;
    StatisticsControl statsControl;
    OSCARSystem system;

    SitemapDiagramHandler sitemapDiagramHandler;
    IBucketService bucketService;
    IBucketStore bucketStore;

    SpreadsheetHandler spreadsheetHandler;
    VideoRetention videoRetention;
    DatabasePurger databasePurger;
    WebIdResourceHandler webIdResourceHandler;
    private java.util.concurrent.ScheduledExecutorService diagnosticScheduler;

    @Override
    protected void doInit() throws SensorHubException {
        super.doInit();

        try {
            getLogger().info("Checking that a bucket service is loaded...");
            this.bucketService = getParentHub().getModuleRegistry()
                    .waitForModuleType(IBucketService.class, ModuleEvent.ModuleState.STARTED)
                    .get(10, TimeUnit.SECONDS);
            this.bucketStore = bucketService.getBucketStore();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            reportError("Could not find this OSH node's Bucket Service", new IllegalStateException(e));
        }

        spreadsheetHandler = new SpreadsheetHandler(getParentHub().getModuleRegistry(), bucketStore, getLogger());
        if (config.spreadsheetConfigPath != null && !config.spreadsheetConfigPath.isEmpty())
            spreadsheetHandler.handleFile(config.spreadsheetConfigPath);

        system = new OSCARSystem(config.nodeId);

        createOutputs();
        createControls();

        sitemapDiagramHandler = new SitemapDiagramHandler(getBucketService(), siteInfoOutput, this);

        if (getConfiguration().videoRetentionConfig != null) {
            int frameCount = getConfiguration().videoRetentionConfig.enableFrameRetention ? getConfiguration().videoRetentionConfig.frameRetentionCount : 0;
            videoRetention = new VideoRetention(getParentHub(),
                    bucketStore,
                    Duration.ofMinutes(getConfiguration().videoRetentionConfig.videoQueryPeriod),
                    Duration.ofDays(getConfiguration().videoRetentionConfig.timeToRetention),
                    frameCount);
        } else {
            videoRetention = null;
            logger.info("No video retention config set.");
        }

        system.updateSensorDescription();
    }

    public void createOutputs(){
        siteInfoOutput = new SiteInfoOutput(system);
        system.addOutput(siteInfoOutput, false);

        IObsSystemDatabase database = null;
        if (config.databaseID != null && !config.databaseID.isBlank()) {
            try {
                database = (IObsSystemDatabase) getParentHub().getModuleRegistry().getModuleById(config.databaseID);
            } catch (SensorHubException e) {
                getLogger().warn("No database configured for OSCAR service");
                database = null;
            }
        }

        if (database == null)
            database = getParentHub().getDatabaseRegistry().getFederatedDatabase();

        statsOutput = new StatisticsOutput(system, database, config.statsFrequencyMinutes);
        system.addOutput(statsOutput, false);
    }

    public void createControls(){
        reportControl = new RequestReportControl(system, this);
        system.addControlInput(reportControl);

        statsControl = new StatisticsControl(system);
        system.addControlInput(statsControl);
    }

    @Override
    protected void doStart() throws SensorHubException {
        super.doStart();

        getParentHub().getSystemDriverRegistry().register(system);

        if (config.databaseID != null && !config.databaseID.isBlank()) {
            var module = getParentHub().getModuleRegistry().getModuleById(config.databaseID);
            if (getParentHub().getSystemDriverRegistry().getDatabase(system.getUniqueIdentifier()) == null)
                getParentHub().getSystemDriverRegistry().registerDatabase(system.getUniqueIdentifier(), (IObsSystemDatabase) module);

            if (databasePurger == null)
                databasePurger = new DatabasePurger((IObsSystemDatabase) module, bucketStore, 5);

            databasePurger.start();
        }

        if (bucketService != null) {
            WebIdClient webIdClient = (config.webIdApiRoot != null && !config.webIdApiRoot.isBlank())
                    ? new WebIdClient(config.webIdApiRoot) : null;
            webIdResourceHandler = new WebIdResourceHandler(bucketStore, getParentHub(), webIdClient);
            bucketService.registerObjectHandler(webIdResourceHandler);
        }

        statsOutput.start();

        refreshSiteDiagram();

        if (videoRetention != null)
            videoRetention.start();

        // Schedule diagnostic table logging
        diagnosticScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                new org.sensorhub.utils.NamedThreadFactory("OSCAR-Diagnostics"));
        diagnosticScheduler.scheduleAtFixedRate(this::logDiagnosticTable, 30, 300, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void logDiagnosticTable() {
        try {
            logger.info("========================================= OSCAR PIPELINE DIAGNOSTICS =========================================");
            logger.info(String.format("| %-12s | %-30s | %-30s | %-10s | %-10s | %-10s | %-5s |",
                    "Lane", "RPM UID", "FFmpeg UID", "Gamma Obs", "Neutr Obs", "Occ Obs", "Clips"));
            logger.info("---------------------------------------------------------------------------------------------------------------");

            IObsSystemDatabase db = getParentHub().getDatabaseRegistry().getFederatedDatabase();
            var modules = getParentHub().getModuleRegistry().getLoadedModules();

            for (IModule<?> module : modules) {
                if (module instanceof LaneSystem lane) {
                    String laneName = lane.getName();
                    String rpmUid = "N/A";
                    String ffmpegUid = "N/A";
                    long gammaCount = 0;
                    long neutronCount = 0;
                    long occupancyCount = 0;
                    int clipCount = 0;

                    for (IModule<?> member : lane.getMembers().values()) {
                        String uid = member.getUniqueIdentifier();
                        if (uid == null) continue;

                        if (uid.contains("sensor:rapiscan") || uid.contains("sensor:aspect") || uid.contains("sensor:rs350")) {
                            rpmUid = uid;
                            gammaCount = countObs(db, uid, "gammaCounts");
                            neutronCount = countObs(db, uid, "neutronCounts");

                            checkMissingOutputs(laneName, uid, "gammaCounts", gammaCount);
                            checkMissingOutputs(laneName, uid, "neutronCounts", neutronCount);
                            checkMissingOutputs(laneName, uid, "alarm", countObs(db, uid, "alarm"));
                            checkMissingOutputs(laneName, uid, "backgroundReport", countObs(db, uid, "backgroundReport"));
                            checkMissingOutputs(laneName, uid, "foregroundReport", countObs(db, uid, "foregroundReport"));
                            checkMissingOutputs(laneName, uid, "dailyFile", countObs(db, uid, "dailyFile"));
                        } else if (uid.contains("sensor:ffmpeg")) {
                            ffmpegUid = uid;
                            clipCount = countClipsOnDisk(uid);
                        } else if (uid.contains("process:rs350-occupancy") || member.getClass().getSimpleName().contains("Occupancy")) {
                            occupancyCount = countObs(db, uid, "occupancy");
                            checkMissingOutputs(laneName, uid, "occupancy", occupancyCount);
                        }
                    }

                    logger.info(String.format("| %-12s | %-30s | %-30s | %-10d | %-10d | %-10d | %-5d |",
                            laneName,
                            truncate(rpmUid, 30),
                            truncate(ffmpegUid, 30),
                            gammaCount,
                            neutronCount,
                            occupancyCount,
                            clipCount));
                }
            }
            logger.info("===============================================================================================================");
        } catch (Exception e) {
            logger.error("Error generating diagnostic table", e);
        }
    }

    private void checkMissingOutputs(String laneName, String systemUid, String outputName, long count) {
        if (count == 0) {
            logger.warn("[OSCAR Diagnostic] Expected output {} is missing data for lane {} (system {})", outputName, laneName, systemUid);
        }
    }

    private long countObs(IObsSystemDatabase db, String systemUid, String outputName) {
        try {
            return db.getObservationStore().countMatchingEntries(new ObsFilter.Builder()
                    .withDataStreams(new DataStreamFilter.Builder()
                            .withSystems(new SystemFilter.Builder().withUniqueIDs(systemUid).build())
                            .withOutputNames(outputName)
                            .build())
                    .build());
        } catch (Exception e) {
            return -1;
        }
    }

    private int countClipsOnDisk(String ffmpegUid) {
        try {
            // sanitize UID for filename
            String prefix = ffmpegUid.replace(":", "-");
            File clipsRoot = new File("files/videos/clips");
            if (clipsRoot.exists() && clipsRoot.isDirectory()) {
                return countFilesWithPrefixRecursively(clipsRoot, prefix);
            }
        } catch (Exception e) {
            // ignore
        }
        return 0;
    }

    private int countFilesWithPrefixRecursively(File dir, String prefix) {
        int count = 0;
        File[] entries = dir.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    count += countFilesWithPrefixRecursively(entry, prefix);
                } else if (entry.getName().startsWith(prefix) && entry.getName().endsWith(".mp4")) {
                    count++;
                }
            }
        }
        return count;
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? "..." + s.substring(s.length() - n + 3) : s;
    }

    @Override
    protected void doStop() throws SensorHubException {
        if (webIdResourceHandler != null && bucketService != null) {
            bucketService.unregisterObjectHandler(webIdResourceHandler);
            webIdResourceHandler = null;
        }

        try {
            statsOutput.stop();
        } catch (Exception ex) {
            getLogger().error("Could not stop stats output", ex);
        }

        if (databasePurger != null)
            databasePurger.stop();

        if (videoRetention != null)
            videoRetention.stop();

        if (diagnosticScheduler != null) {
            diagnosticScheduler.shutdown();
        }

        super.doStop();
    }

    private void refreshSiteDiagram() {
        long currTime = System.currentTimeMillis();
        var query = getParentHub().getDatabaseRegistry().getFederatedDatabase().getObservationStore().select(new ObsFilter.Builder()
                .withDataStreams(new DataStreamFilter.Builder()
                        .withOutputNames(SiteInfoOutput.NAME)
                        .build())
                .withLatestResult()
                .build());

        var obsList = query.toList();
        if (obsList.isEmpty())
            return;

        var res = obsList.get(0).getResult();

        siteInfoOutput.setData(res);
        System.out.println("Site diagram refreshed in " +  (System.currentTimeMillis() - currTime) + "ms");
    }

    public SitemapDiagramHandler getSitemapDiagramHandler() {
        return sitemapDiagramHandler;
    }

    public OSCARSystem getOSCARSystem() {
        return system;
    }

    public IBucketService getBucketService() {
        return bucketService;
    }

    public SpreadsheetHandler getSpreadsheetHandler() {
        return spreadsheetHandler;
    }

    public WebIdResourceHandler getWebIdResourceHandler() {
        return webIdResourceHandler;
    }

    @Override
    public WebIdClient getWebIdClient() {
        return webIdResourceHandler != null ? webIdResourceHandler.getWebIdClient() : null;
    }
}
