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

import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CapabilityBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;

import org.lorislab.quarkus.barn.runtime.BarnRuntimeConfig;
import org.lorislab.quarkus.barn.runtime.BarnBuildTimeConfig;
import org.lorislab.quarkus.barn.runtime.BarnRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;
import static org.lorislab.quarkus.barn.runtime.BarnBuildTimeConfig.DEFAULT_AFTER_MIGRATION_SCRIPTS;

public class BarnProcessor {

    private static final Logger log = LoggerFactory.getLogger(BarnProcessor.class);

    private static final String JAR_APPLICATION_MIGRATIONS_PROTOCOL = "jar";

    private static final String FILE_APPLICATION_MIGRATIONS_PROTOCOL = "file";

    public static String BARN_CLIENT = "barn-client";

    BarnBuildTimeConfig config;

    @BuildStep
    CapabilityBuildItem capability() {
        return new CapabilityBuildItem(BARN_CLIENT);
    }

    @BuildStep
    void build(BuildProducer<FeatureBuildItem> feature) {
        feature.produce(new FeatureBuildItem(BARN_CLIENT));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    ServiceStartBuildItem configureRuntimeProperties(BarnRecorder recorder,
                                                     BarnRuntimeConfig runtimeConfig,
                                                     BarnPoolBuildItem poolBuildItem,
                                                     BeanContainerBuildItem beanContainer) {

        BeanContainer container = beanContainer.getValue();
        recorder.doStartActions(poolBuildItem.getType(), poolBuildItem.getPool(), runtimeConfig, container);
        return new ServiceStartBuildItem(BARN_CLIENT);
    }

    @BuildStep
    @Record(STATIC_INIT)
    void nativeImageConfiguration(BarnRecorder recorder,
                                  BuildProducer<NativeImageResourceBuildItem> resource) throws IOException, URISyntaxException {
        // add migration resources
        List<Resource> resources = getMigrationFiles(config.location);
        if (!resources.isEmpty()) {
            // native resource
            String[] paths = resources.stream().map(x -> x.script).toArray(String[]::new);
            resource.produce(new NativeImageResourceBuildItem(paths));

            // add the repeatable migrations to recorder
            List<Resource> repeatableMigration = resources.stream()
                    .filter(r -> r.repeatable).sorted().collect(Collectors.toList());
            ;
            if (!repeatableMigration.isEmpty()) {
                recorder.setRepeatableMigrations(repeatableMigration);
            }

            // add the versioned migrations to recorder
            List<VersionedMigration> migrations = resources.stream()
                    .filter(r -> !r.repeatable)
                    .map(VersionedMigration::new).sorted().collect(Collectors.toList());
            if (!migrations.isEmpty()) {
                recorder.setVersionedMigrationResources(migrations);
            }
        }
        // add imports
        List<String> afterMigrationScripts = config.afterMigrationScripts;
        afterMigrationScripts.remove(DEFAULT_AFTER_MIGRATION_SCRIPTS);
        if (!afterMigrationScripts.isEmpty()) {
            recorder.setAfterMigrationScripts(afterMigrationScripts);
            resource.produce(new NativeImageResourceBuildItem(afterMigrationScripts.toArray(new String[0])));
        }

        // add SQL files for native image
        resource.produce(new NativeImageResourceBuildItem("/barn/postgresql/doClean.sql"));
    }

    private List<Resource> getMigrationFiles(String location) throws IOException, URISyntaxException {
        if (location == null || location.isBlank()) {
            return Collections.emptyList();
        }
        List<Resource> result = new ArrayList<>();
        Enumeration<URL> migrations = Thread.currentThread().getContextClassLoader().getResources(location);
        while (migrations.hasMoreElements()) {
            URL path = migrations.nextElement();
            log.info("Adding application migrations in path '{}' using protocol '{}'", path.getPath(), path.getProtocol());
            final Set<String> applicationMigrations;
            if (JAR_APPLICATION_MIGRATIONS_PROTOCOL.equals(path.getProtocol())) {
                try (final FileSystem fileSystem = initFileSystem(path.toURI())) {
                    applicationMigrations = getApplicationMigrationsFromPath(location, path);
                }
            } else if (FILE_APPLICATION_MIGRATIONS_PROTOCOL.equals(path.getProtocol())) {
                applicationMigrations = getApplicationMigrationsFromPath(location, path);
            } else {
                log.warn("Unsupported URL protocol '{}' for path '{}'. Migration files will not be discovered.",
                        path.getProtocol(), path.getPath());
                applicationMigrations = null;
            }
            result.addAll(ResourceLoader.createFrom(applicationMigrations));
        }
        return result;
    }

    private Set<String> getApplicationMigrationsFromPath(final String location, final URL path)
            throws IOException, URISyntaxException {
        try (final Stream<Path> pathStream = Files.walk(Paths.get(path.toURI()))) {
            return pathStream.filter(Files::isRegularFile)
                    .map(it -> Paths.get(location, it.getFileName().toString()).toString())
                    .peek(it -> log.debug("Discovered: " + it))
                    .collect(Collectors.toSet());
        }
    }

    private FileSystem initFileSystem(final URI uri) throws IOException {
        final Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        return FileSystems.newFileSystem(uri, env);
    }

}
