package org.lorislab.quarkus.barn;

import org.lorislab.quarkus.barn.models.Resource;
import org.lorislab.quarkus.barn.models.VersionedMigration;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BarnConfig {

    private List<VersionedMigration> versionedMigrations = Collections.emptyList();

    private List<Resource> repeatableMigrations = Collections.emptyList();

    private List<String> testDataScripts = Collections.emptyList();

    private String historyTable;

    public String getHistoryTable() {
        return historyTable;
    }

    public List<Resource> getRepeatableMigrations() {
        return repeatableMigrations;
    }

    public List<VersionedMigration> getVersionedMigrations() {
        return versionedMigrations;
    }

    public List<String> getTestDataScripts() {
        return testDataScripts;
    }

    public static BarnConfigBuilder builder() {
        return new BarnConfigBuilder();
    }

    public static class BarnConfigBuilder {

        private BarnConfig config = new BarnConfig();

        public BarnConfigBuilder table(String table) {
            config.historyTable = table;
            return this;
        }

        public BarnConfigBuilder versionedMigrations(List<VersionedMigration> resources) {
            if (resources != null) {
                config.versionedMigrations = resources;
            }
            return this;
        }

        public BarnConfigBuilder repeatableMigrations(List<Resource> resources) {
            if (resources != null) {
                config.repeatableMigrations = resources;
            }
            return this;
        }

        public BarnConfigBuilder afterMigrationScripts(List<String> resources) {
            if (resources != null) {
                config.testDataScripts = resources;
            }
            return this;
        }

        public BarnConfigBuilder sorted() {
            if (config.versionedMigrations != null && !config.versionedMigrations.isEmpty()) {
                config.versionedMigrations = config.versionedMigrations.stream().sorted().collect(Collectors.toList());
            }
            if (config.repeatableMigrations != null && !config.repeatableMigrations.isEmpty()) {
                config.repeatableMigrations = config.repeatableMigrations.stream().sorted().collect(Collectors.toList());
            }
            return this;
        }

        public BarnConfig build() {
            return config;
        }
    }
}
