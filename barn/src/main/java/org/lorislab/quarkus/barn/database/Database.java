package org.lorislab.quarkus.barn.database;

import io.vertx.mutiny.sqlclient.*;
import org.lorislab.quarkus.barn.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class Database {

    private static Logger log = LoggerFactory.getLogger(Database.class);

    // b + a + r + n + d + b
    private static final long LOCK_NUM = + (0x62L << 48) + (0x61L << 32) + (0x72L << 24) + (0x6E << 16) + (0x64 << 8) + 0x62;

    public final Pool client;

    public final String table;

    public Database(final String table, final Pool client) {
        this.client = client;
        this.table = table;
    }

    public void testData(List<String> testDataScripts) {
        if (testDataScripts == null || testDataScripts.isEmpty()) {
            log.warn("Test data scripts is empty!");
            return;
        }
        log.info("Execute test data scripts");
        for (String resource : testDataScripts) {
            String sql = ResourceLoader.loadResource(resource);
            if (sql == null || sql.isBlank()) {
                log.warn("Skip empty test data scripts. Resource: " + resource);
            } else {
                Transaction tx = null;
                try {
                    // create transaction
                    tx = client.beginAndAwait();

                    // execute SQL script
                    log.info("Script {}", resource);
                    log.debug("----\n" + sql + "\n----");
                    queryAndAwait(tx, sql);

                    // commit
                    tx.commitAndAwait();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    if (tx != null) {
                        tx.close();
                    }
                    throw new IllegalStateException("Error execute test data scripts! Resource:" + resource, ex);
                }
            }
        }
    }

    protected String getCleanSQLResource() {
        return "/barn/none/clean.sql";
    }

    public void doClean() {
        String resource = getCleanSQLResource();
        String sql = ResourceLoader.loadResource(resource);
        if (sql != null && !sql.isBlank()) {
            log.info("Clean database");
            queryAndAwait(sql);
        } else {
            log.warn("Clean database SQL resource {} is empty!", resource);
        }
    }

    public void doMigration(List<VersionedMigration> versionedMigrations, List<Resource> repeatableMigrations) {
        if (versionedMigrations == null || versionedMigrations.isEmpty()) {
            return;
        }

        // check table
        boolean te = checkMigrationTable();
        if (te) {
            // load latest migration
            Migration latest = lastVersionedMigration();
            if (latest != null) {
                // filter resources
                Version ver = Version.of(latest.version);
                boolean exists = versionedMigrations.stream().anyMatch(ver::isLessThan);
                // check the migration
                if (!exists) {
                    return;
                }
            }
        }

        try {
            log.info("Migrate database");

            // create lock
            lock();

            Migration latest = null;
            long id = 0;

            // check migration table
            if (!te && !checkMigrationTable()) {
                createMigTable();
            } else {
                // load last migration
                id = 1 + lastId();
                latest = lastVersionedMigration();
            }

            // filter resources
            List<Migration> migrations = createMigrations(versionedMigrations, latest, id);

            // start migration
            if (!migrations.isEmpty()) {
                latest = migrations(migrations);

                // repeatable migration
                if (repeatableMigrations != null && !repeatableMigrations.isEmpty()) {
                    Map<String, Migration> rms = getAllRepeatableMigration();
                    List<Migration> executeRepeatableMigrations = createRepeatableMigrations(latest, repeatableMigrations, rms);
                    if (!executeRepeatableMigrations.isEmpty()) {
                        migrations(executeRepeatableMigrations);
                    } else {
                        log.debug("No repeatable migration to run!");
                    }
                }

            } else {
                log.warn("No versioned migration to run!");
            }

            log.info("Database version: {}", latest != null ? latest.version : null);
        } finally {
            // release lock
            unlock();
        }
    }

    protected Migration migrations(List<Migration> migrations) {
        Migration result = null;
        // start migration
        for (Migration migration : migrations) {
            migration(migration);
            result = migration;
        }
        return result;
    }

    protected void migration(Migration migration) {
        String sql = ResourceLoader.loadResource(migration.script);
        if (sql == null || sql.isBlank()) {
            log.warn("Skip empty migration resources " + migration.script);
        } else {
            Transaction tx = null;
            try {
                // begin transaction
                tx = client.beginAndAwait();

                // start migration
                log.info("Script {}", migration.script);
                log.debug("----\n" + sql + "\n----");
                long start = System.currentTimeMillis();
                tx.queryAndAwait(sql);
                long time = System.currentTimeMillis() - start;

                // insert or update executed migration
                if (migration.exists) {
                    updateMigration(tx, migration, time);
                } else {
                    insertMigration(tx, migration, time);
                }

                // commit
                tx.commitAndAwait();
            } catch (Exception ex) {
                ex.printStackTrace();
                if (tx != null) {
                    tx.close();
                }
                throw new IllegalStateException("Error execute migration!", ex);
            }
        }
    }

    protected void updateMigration(Transaction tx, Migration migration, Long time) {
        preparedQueryAndAwait(tx,
                "UPDATE " + table + " SET checksum = $1, execution_time = $2 WHERE id=$3 RETURNING (id)",
                Tuple.of(migration.checksum, time, migration.id));
    }


    protected void insertMigration(Transaction tx, Migration migration, Long time) {
        preparedQueryAndAwait(tx,
                "INSERT INTO " + table +
                        " (id,version,description,type,script,checksum,execution_time,success)" +
                        " VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING (id)"
                , Tuple.tuple(Arrays.asList(
                migration.id, migration.version, migration.description, migration.type,
                migration.script, migration.checksum, time, true
                ))
        );
    }

    protected void lock() {
        int retries = 0;
        while (!lockValue()) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                throw new IllegalStateException("Interrupted PostgreSQL advisory lock", e);
            }

            if (++retries >= 50) {
                throw new IllegalStateException("Number of retries exceeded while attempting to acquire PostgreSQL advisory lock");
            }
        }
    }

    protected Boolean lockValue() {
        long lockNum = LOCK_NUM + table.hashCode();
        RowIterator<Row> it = queryAndAwait("SELECT pg_try_advisory_lock(" + lockNum + ")").iterator();
        return it.hasNext() ? it.next().getBoolean("pg_try_advisory_lock") : false;
    }

    protected void unlock() {
        long lockNum = LOCK_NUM + table.hashCode();
        queryAndAwait("SELECT pg_advisory_unlock(" + lockNum + ")");
    }

    protected boolean checkMigrationTable() {
        RowIterator<Row> it = queryAndAwait("SELECT to_regclass('" + table + "')").iterator();
        String tmp = it.hasNext() ? it.next().getString("to_regclass") : "----";
        if (tmp == null || tmp.isBlank()) {
            return false;
        }
        return table.toLowerCase().equals(tmp.toLowerCase());
    }

    private void createMigTable() {
        queryAndAwait(historyTableSql());
    }

    protected Map<String, Migration> getAllRepeatableMigration() {
        RowIterator<Row> it = queryAndAwait("SELECT * FROM " + table + " WHERE version IS NULL").iterator();
        Map<String, Migration> result = new HashMap<>();
        while (it.hasNext()) {
            Migration m = map(it.next());
            result.put(m.description, m);
        }
        return result;
    }

    protected long lastId() {
        RowIterator<Row> it = queryAndAwait("SELECT id FROM " + table + " ORDER BY id DESC LIMIT 1").iterator();
        if (it.hasNext()) {
            return it.next().getLong("id");
        }
        return -1;
    }

    public Migration lastVersionedMigration() {
        RowIterator<Row> it = queryAndAwait("SELECT * FROM " + table + " WHERE version IS NOT NULL ORDER BY id DESC LIMIT 1").iterator();
        if (it.hasNext()) {
            return map(it.next());
        }
        return null;
    }

    protected String historyTableSql() {
        return "CREATE TABLE " + table + " (\n" +
                "    \"id\" INT NOT NULL,\n" +
                "    \"version\" VARCHAR(50),\n" +
                "    \"description\" VARCHAR(200) NOT NULL,\n" +
                "    \"type\" VARCHAR(20) NOT NULL,\n" +
                "    \"script\" VARCHAR(1000) NOT NULL,\n" +
                "    \"checksum\" BIGINT,\n" +
                "    \"installed_by\" TEXT NOT NULL DEFAULT CURRENT_USER,\n" +
                "    \"installed_on\" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                "    \"execution_time\" BIGINT NOT NULL,\n" +
                "    \"success\" BOOLEAN NOT NULL\n" +
                ");\n" +
                "ALTER TABLE " + table + " ADD CONSTRAINT \"" + table + "_pk\" PRIMARY KEY (\"id\");\n" +
                "CREATE INDEX \"" + table + "_s_idx\" ON " + table + " (\"success\");";
    }

    protected Migration map(Row row) {
        Migration m = new Migration();
        m.exists = true;
        m.id = row.getLong("id");
        m.version = row.getString("version");
        m.description = row.getString("description");
        m.type = row.getString("type");
        m.script = row.getString("script");
        m.checksum = row.getLong("checksum");
        m.installedBy = row.getString("installed_by");
        m.installedOn = row.getLocalDateTime("installed_on");
        m.time = row.getLong("time");
        m.success = row.getBoolean("success");
        return m;
    }

    protected List<Migration> createMigrations(List<VersionedMigration> resources, Migration latest, long id) {
        List<VersionedMigration> versions = resources;
        if (latest != null) {
            Version ver = Version.of(latest.version);
            versions = resources.stream().filter(ver::isLessThan).collect(Collectors.toList());
        }

        List<Migration> result = new ArrayList<>();
        for (VersionedMigration m : versions) {
            result.add(create(m.resource, id++));
        }
        return result;
    }

    protected List<Migration> createRepeatableMigrations(Migration latest, List<Resource> repeatableMigrations, Map<String, Migration> rms) {
        long id = latest.id + 1;
        List<Migration> result = new ArrayList<>();
        for (Resource rm : repeatableMigrations) {
            Migration m = rms.get(rm.description);
            if (m != null) {
                if (!m.checksum.equals(rm.checksum)) {
                    result.add(m);
                }
            } else {
                m = create(rm, id++);
                result.add(m);
            }
        }
        return result;
    }

    protected Migration create(Resource resource, long id) {
        Migration r = new Migration();
        r.exists = false;
        r.id = id;
        r.version = resource.version;
        r.description = resource.description;
        r.type = "SQL";
        r.script = resource.script;
        r.checksum = resource.checksum;
        return r;
    }

    private void preparedQueryAndAwait(Transaction tx, String sql, io.vertx.mutiny.sqlclient.Tuple arguments) {
        log.debug(sql);
        tx.preparedQueryAndAwait(sql, arguments);
    }

    private RowSet<Row> queryAndAwait(String sql) {
        return queryAndAwait(client, sql);
    }

    private static RowSet<Row> queryAndAwait(SqlClient client, String sql) {
        log.debug(sql);
        return client.queryAndAwait(sql);
    }
}
