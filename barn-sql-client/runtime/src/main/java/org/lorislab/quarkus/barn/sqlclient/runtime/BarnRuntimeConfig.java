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
package org.lorislab.quarkus.barn.sqlclient.runtime;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import org.lorislab.quarkus.barn.Barn;

@ConfigRoot(name = "barn", phase = ConfigPhase.RUN_TIME)
public class BarnRuntimeConfig {

    /**
     * Gets the default runtime configuration
     *
     * @return runtime default configuration
     */
    public static BarnRuntimeConfig defaultConfig() {
        return new BarnRuntimeConfig();
    }

    /**
     * {@code true} to execute migration automatically when the application starts, {@code false} otherwise.
     *
     */
    @ConfigItem
    public boolean migrateAtStart;

    /**
     * {@code true} to execute migration clean command automatically when the application starts, {@code false} otherwise.
     *
     */
    @ConfigItem
    public boolean cleanAtStart;

    /**
     * {@code true} to execute scripts after migration, {@code false} otherwise.
     * This should be use only in development.
     *
     */
    @ConfigItem
    public boolean testData;

    /**
     * Migration table
     */
    @ConfigItem(defaultValue = Barn.HISTORY_TABLE)
    public String historyTable;
}
