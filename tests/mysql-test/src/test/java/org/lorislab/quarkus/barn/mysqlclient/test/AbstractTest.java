package org.lorislab.quarkus.barn.mysqlclient.test;

import io.quarkus.test.common.QuarkusTestResource;
import org.lorislab.quarkus.testcontainers.DockerComposeTestResource;
import org.lorislab.quarkus.testcontainers.QuarkusTestcontainers;

@QuarkusTestcontainers
@QuarkusTestResource(DockerComposeTestResource.class)
public abstract class AbstractTest {
}