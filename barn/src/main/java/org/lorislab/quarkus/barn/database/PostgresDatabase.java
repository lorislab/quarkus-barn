package org.lorislab.quarkus.barn.database;

import io.vertx.mutiny.sqlclient.Pool;

import java.util.Collections;
import java.util.List;

public class PostgresDatabase extends Database {

    private static final String RESOURCE_CLEAN = "/barn/postgres/clean.sql";

    public static final String POOL = "io.vertx.mutiny.pgclient.PgPool";

    public static final List<String> RESOURCES = Collections.singletonList(
            RESOURCE_CLEAN
    );

    public PostgresDatabase(String table, Pool client) {
        super(table, client);
    }

    protected String getCleanSQLResource() {
        return RESOURCE_CLEAN;
    }

}
