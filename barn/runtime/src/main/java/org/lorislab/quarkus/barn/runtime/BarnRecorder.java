/*
 * Copyright 2020 lorislab.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lorislab.quarkus.barn.runtime;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.mutiny.sqlclient.Pool;
import org.lorislab.quarkus.barn.Barn;
import org.lorislab.quarkus.barn.Resource;
import org.lorislab.quarkus.barn.VersionedMigration;

import javax.enterprise.inject.Default;
import java.util.List;

@Recorder
public class BarnRecorder {

    private static List<VersionedMigration> versionedMigrations;

    private static List<Resource> repeatableMigrations;

    private static List<String> afterMigrationScripts;

    public void setRepeatableMigrations(List<Resource> repeatableMigrations) {
        BarnRecorder.repeatableMigrations = repeatableMigrations;
    }

    public void setAfterMigrationScripts(List<String> afterMigrationScripts) {
        BarnRecorder.afterMigrationScripts = afterMigrationScripts;
    }

    public void setVersionedMigrationResources(List<VersionedMigration> resources) {
        BarnRecorder.versionedMigrations = resources;
    }

    /**
     * Do start actions
     *
     * @param config the runtime configuration
     */
    public void doStartActions(String type, Class<? extends Pool> pool, BarnRuntimeConfig config, BeanContainer container) {
        try {
            Pool client = container.instance(pool, Default.Literal.INSTANCE);
            if (config.cleanAtStart) {
                Barn.doClean(client);
            }
            if (config.migrateAtStart) {
                Barn.doMigration(client, versionedMigrations, repeatableMigrations, config.historyTable);
            }
            if (config.afterMigration) {
                Barn.afterMigration(client, afterMigrationScripts);
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

}
