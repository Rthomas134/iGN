package com.axone_io.ignition.git.web;

import com.axone_io.ignition.git.records.GitProjectsConfigRecord;
import com.axone_io.ignition.git.records.GitReposUsersRecord;
import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.GsonBuilder;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.gateway.dataroutes.HttpMethod;
import com.inductiveautomation.ignition.gateway.dataroutes.PermissionType;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistenceInterface;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import simpleorm.dataset.SQuery;

import java.util.List;

/**
 * REST backend for the Git module's gateway config page (the React bundle itself is served
 * statically via GatewayHook#getMountedResourceFolder(), not through here).
 * Replaces the removed Wicket pages (GitProjectsConfigPage/EditPage, GitReposUsersPage/EditPage);
 * the React UI mounted via NavigationModel (see GatewayHook) talks to these routes.
 */
public class GitConfigRoutes {
    private static final Gson GSON = new GsonBuilder().create();

    public static void mount(RouteGroup routeGroup, GatewayContext context) {
        PersistenceInterface db = context.getPersistenceInterface();

        routeGroup.newRoute("/projects")
                .method(HttpMethod.GET)
                .type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.READ)
                .handler((rc, resp) -> listProjects(db))
                .mount();

        routeGroup.newRoute("/projects")
                .method(HttpMethod.POST)
                .type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler((rc, resp) -> createProject(db, rc))
                .mount();

        routeGroup.newRoute("/projects/:id")
                .method(HttpMethod.PUT)
                .type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler((rc, resp) -> updateProject(db, rc))
                .mount();

        routeGroup.newRoute("/projects/:id")
                .method(HttpMethod.DELETE)
                .type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler((rc, resp) -> deleteProject(db, rc))
                .mount();

        routeGroup.newRoute("/users")
                .method(HttpMethod.GET)
                .type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.READ)
                .handler((rc, resp) -> listUsers(db, rc))
                .mount();

        routeGroup.newRoute("/users")
                .method(HttpMethod.POST)
                .type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler((rc, resp) -> createUser(db, rc))
                .mount();

        routeGroup.newRoute("/users/:ignitionUser")
                .method(HttpMethod.PUT)
                .type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler((rc, resp) -> updateUser(db, rc))
                .mount();

        routeGroup.newRoute("/users/:ignitionUser")
                .method(HttpMethod.DELETE)
                .type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler((rc, resp) -> deleteUser(db, rc))
                .mount();
    }

    private static JsonObject toJson(GitProjectsConfigRecord r) {
        JsonObject o = new JsonObject();
        o.addProperty("id", r.getId());
        o.addProperty("projectName", r.getProjectName());
        o.addProperty("uri", r.getURI());
        o.addProperty("sshAuthentication", r.isSSHAuthentication());
        o.addProperty("autoPullIntervalMinutes", r.getAutoPullIntervalMinutes());
        o.addProperty("autoPullIgnitionUser", r.getAutoPullIgnitionUser());
        return o;
    }

    private static JsonObject toJson(GitReposUsersRecord r) {
        JsonObject o = new JsonObject();
        o.addProperty("ignitionUser", r.getIgnitionUser());
        o.addProperty("projectId", r.getProjectId());
        o.addProperty("userName", r.getUserName());
        o.addProperty("email", r.getEmail());
        o.addProperty("hasSSHKey", r.getSSHKey() != null && !r.getSSHKey().isEmpty());
        return o;
    }

    private static String listProjects(PersistenceInterface db) {
        List<GitProjectsConfigRecord> records = db.query(new SQuery<>(GitProjectsConfigRecord.META));
        JsonArray array = new JsonArray();
        records.forEach(r -> array.add(toJson(r)));
        return GSON.toJson(array);
    }

    private static String createProject(PersistenceInterface db, RequestContext rc) throws Exception {
        JsonObject body = GSON.fromJson(rc.readBody(), JsonObject.class);
        GitProjectsConfigRecord record = db.createNew(GitProjectsConfigRecord.META);
        record.setProjectName(body.get("projectName").getAsString());
        record.setURI(body.get("uri").getAsString());
        applyAutoPullFields(record, body);
        db.save(record);
        return GSON.toJson(toJson(record));
    }

    private static void applyAutoPullFields(GitProjectsConfigRecord record, JsonObject body) {
        if (body.has("autoPullIntervalMinutes")) {
            record.setAutoPullIntervalMinutes(Math.max(0, body.get("autoPullIntervalMinutes").getAsInt()));
        }
        if (body.has("autoPullIgnitionUser")) {
            record.setAutoPullIgnitionUser(body.get("autoPullIgnitionUser").getAsString());
        }
    }

    private static String updateProject(PersistenceInterface db, RequestContext rc) throws Exception {
        long id = Long.parseLong(rc.getParameter("id"));
        GitProjectsConfigRecord record = db.find(GitProjectsConfigRecord.META, id);
        if (record == null) {
            throw new IllegalArgumentException("No project with id " + id);
        }
        JsonObject body = GSON.fromJson(rc.readBody(), JsonObject.class);
        if (body.has("projectName")) {
            record.setProjectName(body.get("projectName").getAsString());
        }
        if (body.has("uri")) {
            record.setURI(body.get("uri").getAsString());
        }
        applyAutoPullFields(record, body);
        db.save(record);
        return GSON.toJson(toJson(record));
    }

    private static String deleteProject(PersistenceInterface db, RequestContext rc) throws Exception {
        long id = Long.parseLong(rc.getParameter("id"));
        GitProjectsConfigRecord record = db.find(GitProjectsConfigRecord.META, id);
        if (record != null) {
            record.deleteRecord();
            db.save(record);
        }
        JsonObject result = new JsonObject();
        result.addProperty("deleted", record != null);
        return GSON.toJson(result);
    }

    private static String listUsers(PersistenceInterface db, RequestContext rc) {
        SQuery<GitReposUsersRecord> query = new SQuery<>(GitReposUsersRecord.META);
        String projectId = rc.getParameter("projectId");
        if (projectId != null && !projectId.isEmpty()) {
            query = query.eq(GitReposUsersRecord.ProjectId, Long.parseLong(projectId));
        }
        List<GitReposUsersRecord> records = db.query(query);
        JsonArray array = new JsonArray();
        records.forEach(r -> array.add(toJson(r)));
        return GSON.toJson(array);
    }

    private static String createUser(PersistenceInterface db, RequestContext rc) throws Exception {
        JsonObject body = GSON.fromJson(rc.readBody(), JsonObject.class);
        GitReposUsersRecord record = db.createNew(GitReposUsersRecord.META);
        record.setIgnitionUser(body.get("ignitionUser").getAsString());
        record.setProjectId(body.get("projectId").getAsLong());
        if (body.has("userName")) record.setUserName(body.get("userName").getAsString());
        if (body.has("email")) record.setEmail(body.get("email").getAsString());
        if (body.has("password")) record.setPassword(body.get("password").getAsString());
        if (body.has("sshKey")) record.setSSHKey(body.get("sshKey").getAsString());
        db.save(record);
        return GSON.toJson(toJson(record));
    }

    private static String updateUser(PersistenceInterface db, RequestContext rc) throws Exception {
        String ignitionUser = rc.getParameter("ignitionUser");
        GitReposUsersRecord record = db.find(GitReposUsersRecord.META, ignitionUser);
        if (record == null) {
            throw new IllegalArgumentException("No git user config for " + ignitionUser);
        }
        JsonObject body = GSON.fromJson(rc.readBody(), JsonObject.class);
        if (body.has("projectId")) record.setProjectId(body.get("projectId").getAsLong());
        if (body.has("userName")) record.setUserName(body.get("userName").getAsString());
        if (body.has("email")) record.setEmail(body.get("email").getAsString());
        if (body.has("password") && !body.get("password").getAsString().isEmpty()) {
            record.setPassword(body.get("password").getAsString());
        }
        if (body.has("sshKey")) record.setSSHKey(body.get("sshKey").getAsString());
        db.save(record);
        return GSON.toJson(toJson(record));
    }

    private static String deleteUser(PersistenceInterface db, RequestContext rc) throws Exception {
        String ignitionUser = rc.getParameter("ignitionUser");
        GitReposUsersRecord record = db.find(GitReposUsersRecord.META, ignitionUser);
        if (record != null) {
            record.deleteRecord();
            db.save(record);
        }
        JsonObject result = new JsonObject();
        result.addProperty("deleted", record != null);
        return GSON.toJson(result);
    }
}
