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
package org.lorislab.quarkus.barn;

import io.quarkus.builder.item.SimpleBuildItem;
import io.vertx.mutiny.sqlclient.Pool;

public final class BarnPoolBuildItem extends SimpleBuildItem {

    private final Class<? extends Pool> pool;

    private final String type;

    public BarnPoolBuildItem(String type, Class<? extends Pool> pool) {
        this.pool = pool;
        this.type = type;
    }

    public String getType() { return type; }

    public Class<? extends Pool> getPool() {
        return pool;
    }

}
