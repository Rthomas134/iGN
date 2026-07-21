package com.axone_io.ignition.git;

import com.axone_io.ignition.git.commissioning.utils.GitCommissioningUtils;
import com.axone_io.ignition.git.records.GitProjectsConfigRecord;
import com.axone_io.ignition.git.records.GitReposUsersRecord;
import com.axone_io.ignition.git.web.GitConfigRoutes;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.rpc.GatewayRpcImplementation;
import com.inductiveautomation.ignition.gateway.web.systemjs.SystemJsModule;
import simpleorm.dataset.SQuery;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.SQLException;

public class GatewayHook extends AbstractGatewayModuleHook {
    static public String MODULE_NAME = "Git";
    static final String MODULE_ID = "com.axone_io.ignition.git";
    private static final long AUTO_PULL_TICK_SECONDS = 60;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private GatewayScriptModule scriptModule;
    public static GatewayContext context;

    // In-memory last-pull time per project id; reset on gateway/module restart, which just
    // means the next tick after a restart may pull slightly early rather than losing pulls.
    private final ConcurrentHashMap<Long, Long> lastAutoPullMillis = new ConcurrentHashMap<>();
    private ScheduledFuture<?> autoPullTask;

    @Override
    public void setup(GatewayContext gatewayContext) {
        context = gatewayContext;
        scriptModule = new GatewayScriptModule(context);
        BundleUtil.get().addBundle("bundle_git", getClass(), "bundle_git");
        verifySchema(gatewayContext);
        registerConfigUi(gatewayContext);
        logger.info("setup()");
    }

    /**
     * Registers the gateway config page for this module using the 8.3 React/SystemJS
     * web framework, replacing the removed Wicket pages (GitProjectsConfigPage/EditPage,
     * GitReposUsersPage/EditPage). Mirrors the wiring in Inductive Automation's own
     * "webui-webpage" SDK example: the UMD bundle (built by git-gateway/web-ui, landing in
     * src/main/resources/mounted/git-config-ui.js) is served automatically at
     * /res/{moduleId}/git-config-ui.js via getMountedResourceFolder() below, and the nav
     * page's componentId ("git-projects-page") must match a named export on that bundle
     * (see web-ui/src/index.js).
     *
     * Matching the 8.1 Wicket UI, there's a single top-level "Projects" entry; managing a
     * project's users is a drill-down from that project's row, handled client-side by the
     * bundle rather than as a second nav page.
     */
    private void registerConfigUi(GatewayContext gatewayContext) {
        SystemJsModule uiModule = new SystemJsModule("git-config-ui", "/res/" + MODULE_ID + "/git-config-ui.js");

        gatewayContext.getWebResourceManager().getNavigationModel().getServices().addCategory("git", category -> category
                .label("Git Integration")
                .addPage("projects", page -> page
                        .title("Git Projects")
                        .mount("projects", "git-projects-page", uiModule)));
    }

    @Override
    public Optional<String> getMountedResourceFolder() {
        return Optional.of("mounted");
    }

    @Override
    public void mountRouteHandlers(RouteGroup routeGroup) {
        GitConfigRoutes.mount(routeGroup, context);
    }

    private void verifySchema(GatewayContext context) {
        try {
            context.getSchemaUpdater().updatePersistentRecords(GitProjectsConfigRecord.META, GitReposUsersRecord.META);
        } catch (SQLException e) {
            logger.error("Error verifying persistent record schemas.", e);
        }
    }

    @Override
    public void startup(LicenseState licenseState) {
        GitCommissioningUtils.loadConfiguration();
        autoPullTask = context.getScheduledExecutorService().scheduleWithFixedDelay(
                this::runDueAutoPulls, AUTO_PULL_TICK_SECONDS, AUTO_PULL_TICK_SECONDS, TimeUnit.SECONDS);
        logger.info("startup()");
    }

    @Override
    public void shutdown() {
        if (autoPullTask != null) {
            autoPullTask.cancel(false);
        }
        logger.info("shutdown()");
    }

    /**
     * Runs on a fixed tick (see AUTO_PULL_TICK_SECONDS) and pulls any project whose configured
     * AutoPullIntervalMinutes has elapsed since its last pull, authenticating as the project's
     * AutoPullIgnitionUser. Configured per-project from the "Git Projects" gateway config page.
     */
    private void runDueAutoPulls() {
        try {
            List<GitProjectsConfigRecord> projects = context.getPersistenceInterface()
                    .query(new SQuery<>(GitProjectsConfigRecord.META));
            long now = System.currentTimeMillis();

            for (GitProjectsConfigRecord project : projects) {
                int intervalMinutes = project.getAutoPullIntervalMinutes();
                String ignitionUser = project.getAutoPullIgnitionUser();
                if (intervalMinutes <= 0 || ignitionUser == null || ignitionUser.isEmpty()) {
                    continue;
                }

                long intervalMillis = intervalMinutes * 60_000L;
                long lastPull = lastAutoPullMillis.getOrDefault(project.getId(), 0L);
                if (now - lastPull < intervalMillis) {
                    continue;
                }

                lastAutoPullMillis.put(project.getId(), now);
                logger.info("Auto-pulling project '" + project.getProjectName() + "' as user '" + ignitionUser + "'");
                boolean success = scriptModule.gitPull(project.getProjectName(), ignitionUser);
                if (!success) {
                    logger.warn("Auto-pull failed for project '" + project.getProjectName() + "'");
                }
            }
        } catch (Exception e) {
            logger.error("Error running scheduled git auto-pulls.", e);
        }
    }

    @Override
    public boolean isFreeModule() { return true; }

    @Override
    public boolean isMakerEditionCompatible() { return true; }

    @Override
    public Optional<GatewayRpcImplementation> getRpcImplementation() {
        return Optional.of(
                GatewayRpcImplementation.of(
                        GitRpcInterface.SERIALIZER,
                        new GitRpcImplementation(context)
                )
        );
    }

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        super.initializeScriptManager(manager);
        manager.addScriptModule("system.git", scriptModule, new PropertiesFileDocProvider());
    }
}
