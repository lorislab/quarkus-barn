package org.lorislab.quarkus.barn.mysqlclient.test;

import io.quarkus.test.common.QuarkusTestResource;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import org.junit.jupiter.api.Assertions;
import org.lorislab.quarkus.barn.Barn;
import org.lorislab.quarkus.barn.database.Database;
import org.lorislab.quarkus.barn.models.Migration;
import org.lorislab.quarkus.testcontainers.DockerComposeTestResource;
import org.lorislab.quarkus.testcontainers.QuarkusTestcontainers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@QuarkusTestcontainers
@QuarkusTestResource(DockerComposeTestResource.class)
public abstract class AbstractTest {

    protected static final Logger log = LoggerFactory.getLogger(AbstractTest.class);

    protected List<Migration> loadAllMigration(Pool client) {
        String sql = "SELECT * FROM " + Barn.HISTORY_TABLE + " ORDER BY ID";
        log.info("SQL:\n" + sql);
        return client.query(sql).execute()
                .map(rs -> {
                    List<Migration> list = new ArrayList<>(rs.size());
                    for (Row row : rs) {
                        list.add(Database.map(row));
                    }
                    return list;
                }).await().indefinitely();
    }

    protected Pool createPool() {
        MySQLConnectOptions pgConnectOptions = MySQLConnectOptions.fromUri(System.getProperty("quarkus.datasource.reactive.url"));
        pgConnectOptions.setUser(System.getProperty("quarkus.datasource.username"));
        pgConnectOptions.setPassword(System.getProperty("quarkus.datasource.password"));
        return MySQLPool.pool(pgConnectOptions, new PoolOptions());
    }

    protected List<TestModel> loadAllTestModels(Pool client, String table) {
        String sql = "SELECT * FROM " + table;
        log.info("SQL:\n" + sql);
        List<TestModel> r = new ArrayList<>();
        for (Row row : client.query(sql).executeAndAwait()) {
            TestModel t = new TestModel();
            t.id = row.getLong("id");
            t.ref = row.getString("ref");
            r.add(t);
        }
        return r;
    }

    protected static class TestModel {
        public Long id;
        String ref;
    }

    protected static void assertEquals(Migration e, Migration a) {
        Assertions.assertEquals(e.id, a.id);
        Assertions.assertEquals(e.version, a.version);
        Assertions.assertEquals(e.description, a.description);
        Assertions.assertEquals(e.type, a.type);
        Assertions.assertEquals(e.script, a.script);
        Assertions.assertEquals(e.checksum, a.checksum);
        Assertions.assertTrue(a.installedBy.startsWith(e.installedBy));
    }

    protected static Migration create(Long id, String version, String description, String script, long checksum) {
        Migration r = new Migration();
        r.exists = true;
        r.id = id;
        r.version = version;
        r.description = description;
        r.type = "SQL";
        r.script = script;
        r.checksum = checksum;
        r.installedBy = "barnmysql@";
        return r;
    }
}
