package org.lorislab.quarkus.barn.database;

import io.vertx.mutiny.sqlclient.Pool;
import org.lorislab.quarkus.barn.database.Database;

import java.util.Collections;
import java.util.List;

public class MySqlDatabase extends Database {

    public static final String POOL = "io.vertx.mutiny.mysql.MySQLPool";

    private static final String RESOURCE_CLEAN = "/barn/myqsql/clean.sql";

    public static final List<String> RESOURCES = Collections.singletonList(
            RESOURCE_CLEAN
    );

    public MySqlDatabase(String table, Pool client) {
        super(table, client);
    }

    protected String getCleanSQLResource() {
        return RESOURCE_CLEAN;
    }

}
