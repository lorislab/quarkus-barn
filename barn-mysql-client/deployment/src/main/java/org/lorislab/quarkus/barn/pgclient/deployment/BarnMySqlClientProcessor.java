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
package org.lorislab.quarkus.barn.pgclient.deployment;

import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import org.lorislab.quarkus.barn.sqlclient.deployment.BarnPoolBuildItem;

public class BarnMySqlClientProcessor {

    public static String BARN_MYSQL_CLIENT = "barn-mysql-client";

    @BuildStep
    void build(BuildProducer<FeatureBuildItem> feature) {
        feature.produce(new FeatureBuildItem(BARN_MYSQL_CLIENT));
    }

    @BuildStep
    ServiceStartBuildItem configureRuntimeProperties(BuildProducer<BarnPoolBuildItem> pool) {
        pool.produce(new BarnPoolBuildItem(MySQLPool.class));
        return new ServiceStartBuildItem(BARN_MYSQL_CLIENT);
    }

    @BuildStep
    UnremovableBeanBuildItem setup() {
        return UnremovableBeanBuildItem.beanClassNames(MySQLPool.class.getName());
    }
}