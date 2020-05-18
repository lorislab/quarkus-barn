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

import io.vertx.mutiny.sqlclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class Barn {

    private static final Logger log = LoggerFactory.getLogger(Barn.class);

    public static void afterMigration(Pool client, List<String> afterMigrationScripts) {
        if (afterMigrationScripts == null || afterMigrationScripts.isEmpty()) {
            log.warn("After migration SQL script is empty!");
            return;
        }
        log.info("Execute after migration scripts");
        for (String resource : afterMigrationScripts) {
            String sql = ResourceLoader.loadResource(resource);
            if (sql == null || sql.isBlank()) {
                log.warn("Empty after migration script. Resource: " + resource);
            } else {
                Transaction tx = null;
                try {
                    // create transaction
                    tx = client.beginAndAwait();

                    // execute SQL script
                    log.info("Script {}", resource);
                    log.debug("----\n" + sql + "\n----");
                    tx.queryAndAwait(sql);

                    // commit
                    tx.commitAndAwait();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    if (tx != null) {
                        tx.close();
                    }
                    throw new IllegalStateException("Error execute after migration scripts! Resource:" + resource, ex);
                }
            }
        }
    }

    public static void doClean(Pool client) {
        String resource = "barn/postgresql/doClean.sql";
        String sql = ResourceLoader.loadResource(resource);
        if (sql != null && !sql.isBlank()) {
            log.info("Clean database");
            client.queryAndAwait(sql);
        } else {
            log.warn("Clean database SQL resource {} is empty!", resource);
        }
    }

    public static void doMigration(Pool client, List<VersionedMigration> versionedMigrations, List<Resource> repeatableMigrations, String table) {
        if (versionedMigrations == null || versionedMigrations.isEmpty()) {
            return;
        }

        // create lock number
        long lockNum = LOCK_NUM + table.hashCode();

        // check table
        boolean te = checkMigrationTable(client, table);
        if (te) {
            // load latest migration
            Migration latest = lastVersionedMigration(client, table);
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
            lock(client, lockNum);

            Migration latest = null;
            long id = 0;

            // check migration table
            if (!te && !checkMigrationTable(client, table)) {
                createMigTable(client, table);
            } else {
                // load last migration
                id = 1 + lastId(client, table);
                latest = lastVersionedMigration(client, table);
            }

            // filter resources
            List<Migration> migrations = createMigrations(versionedMigrations, latest, id);

            // start migration
            if (!migrations.isEmpty()) {
                latest = migrations(client, table, migrations);

                // repeatable migration
                if (repeatableMigrations != null && !repeatableMigrations.isEmpty()) {
                    Map<String, Migration> rms = getAllRepeatableMigration(client, table);
                    List<Migration> executeRepeatableMigrations = createRepeatableMigrations(latest, repeatableMigrations, rms);
                    if (!executeRepeatableMigrations.isEmpty()) {
                        migrations(client, table, executeRepeatableMigrations);
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
            unlock(client, lockNum);
        }
    }

    private static List<Migration> createRepeatableMigrations(Migration latest, List<Resource> repeatableMigrations, Map<String, Migration> rms) {
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

    private static Migration migrations(Pool client, String table, List<Migration> migrations) {
        Migration result = null;
        // start migration
        for (Migration migration : migrations) {
            migration(client, table, migration);
            result = migration;
        }
        return result;
    }

    private static void migration(Pool client, String table, Migration migration) {
        String sql = ResourceLoader.loadResource(migration.script);
        if (sql == null || sql.isBlank()) {
            log.warn("Empty migration resources " + migration.script);
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
                    updateMigration(tx, table, migration, time);
                } else {
                    insertMigration(tx, table, migration, time);
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

    private static void updateMigration(Transaction tx, String table, Migration migration, Long time) {
        String sql = "UPDATE " + table + " SET checksum = $1, execution_time = $2 WHERE id=$3 RETURNING (id)";
        log.debug(sql);
        tx.preparedQueryAndAwait(sql, Tuple.of(migration.checksum, time, migration.id));
    }

    private static void insertMigration(Transaction tx, String table, Migration migration, Long time) {
        String sql = "INSERT INTO " + table +
                " (id,version,description,type,script,checksum,execution_time,success)" +
                " VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING (id)";
        log.debug(sql);
        tx.preparedQueryAndAwait(sql, Tuple.tuple(Arrays.asList(
                                migration.id, migration.version, migration.description, migration.type,
                                migration.script, migration.checksum, time, true
                ))
        );
    }

    private static void lock(Pool client, long lockNum) {
        int retries = 0;
        while (!lockValue(client, lockNum)) {
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

    private static Boolean lockValue(Pool client, long lockNum) {
        RowIterator<Row> it = queryAndAwait(client,"SELECT pg_try_advisory_lock(" + lockNum + ")");
        return it.hasNext() ? it.next().getBoolean("pg_try_advisory_lock") : false;
    }

    private static void unlock(Pool client, long lockNum) {
        String sql = "SELECT pg_advisory_unlock(" + lockNum + ")";
        log.debug(sql);
        client.queryAndAwait(sql);
    }

    private static boolean checkMigrationTable(Pool client, String table) {
        RowIterator<Row> it = queryAndAwait(client, "SELECT to_regclass('" + table + "')");
        String tmp = it.hasNext() ? it.next().getString("to_regclass") : "----";
        if (tmp == null || tmp.isBlank()) {
            return false;
        }
        return table.toLowerCase().equals(tmp.toLowerCase());
    }

    private static void createMigTable(Pool client, String table) {
        client.queryAndAwait(migTableSql(table));
    }

    private static Map<String, Migration> getAllRepeatableMigration(Pool client, String table) {
        RowIterator<Row> it = queryAndAwait(client,"SELECT * FROM " + table + " WHERE version IS NULL");
        Map<String, Migration> result = new HashMap<>();
        while (it.hasNext()) {
            Migration m = map(it.next());
            result.put(m.description, m);
        }
        return result;
    }

    private static long lastId(Pool client, String table) {
        RowIterator<Row> it = queryAndAwait(client, "SELECT id FROM " + table + " ORDER BY id DESC LIMIT 1");
        if (it.hasNext()) {
            return it.next().getLong("id");
        }
        return -1;
    }

    private static Migration lastVersionedMigration(Pool client, String table) {
        RowIterator<Row> it = queryAndAwait(client, "SELECT * FROM " + table + " WHERE version IS NOT NULL ORDER BY id DESC LIMIT 1");
        if (it.hasNext()) {
            return map(it.next());
        }
        return null;
    }

    private static RowIterator<Row> queryAndAwait(Pool client, String sql) {
        log.debug(sql);
        return client.queryAndAwait(sql).iterator();
    }

    private static Migration map(Row row) {
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

    private static String migTableSql(String table) {
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

    // b + a + r + n + d + b
    private static final long LOCK_NUM = + (0x62L << 48) + (0x61L << 32) + (0x72L << 24) + (0x6E << 16) + (0x64 << 8) + 0x62;

    public static List<Migration> createMigrations(List<VersionedMigration> resources, Migration latest, long id) {
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

    private static Migration create(Resource resource, long id) {
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
}
