package com.axone_io.ignition.git.records;

import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleorm.dataset.SFieldFlags;

public class GitProjectsConfigRecord extends PersistentRecord {
    private static final Logger logger = LoggerFactory.getLogger(GitProjectsConfigRecord.class);

    public static final RecordMeta<GitProjectsConfigRecord> META = new RecordMeta<>(
            GitProjectsConfigRecord.class, "GitProjectsConfigRecord");

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }

    public static final IdentityField Id = new IdentityField(META);
    public static final StringField ProjectName = new StringField(META, "ProjectName", SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE);
    public static final StringField URI =
            new StringField(META, "URI", SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE);

    // 0 = auto-pull disabled. When > 0, GatewayHook's scheduler pulls this project on that
    // interval, authenticating as AutoPullIgnitionUser (must match a GitReposUsersRecord
    // configured for this project).
    public static final IntField AutoPullIntervalMinutes = new IntField(META, "AutoPullIntervalMinutes").setDefault(0);
    public static final StringField AutoPullIgnitionUser = new StringField(META, "AutoPullIgnitionUser").setDefault("");


    public long getId() {
        return this.getLong(Id);
    }

    public String getProjectName() {
        return this.getString(ProjectName);
    }

    public String getURI() {
        return this.getString(URI);
    }

    public void setProjectName(String projectName) {
        setString(ProjectName, projectName);
    }

    public void setURI(String uri) {
        setString(URI, uri);
    }

    public boolean isSSHAuthentication() {
        return !this.getString(URI).toLowerCase().startsWith("http");
    }

    public int getAutoPullIntervalMinutes() {
        return this.getInt(AutoPullIntervalMinutes);
    }

    public void setAutoPullIntervalMinutes(int minutes) {
        setInt(AutoPullIntervalMinutes, minutes);
    }

    public String getAutoPullIgnitionUser() {
        return this.getString(AutoPullIgnitionUser);
    }

    public void setAutoPullIgnitionUser(String ignitionUser) {
        setString(AutoPullIgnitionUser, ignitionUser);
    }
}