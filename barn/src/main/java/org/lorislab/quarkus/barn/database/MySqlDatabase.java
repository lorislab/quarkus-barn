package org.lorislab.quarkus.barn.database;

import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import org.lorislab.quarkus.barn.database.Database;

import java.util.Collections;
import java.util.List;

public class MySqlDatabase extends Database {

    public static final String POOL = "io.vertx.mutiny.mysqlclient.MySQLPool";

    private static final String LOCK_NAME = "BarnLock_";

    private final String lockName;

    public MySqlDatabase(String table, Pool client) {
        super(table, client);
        this.lockName = LOCK_NAME + table;
    }

    @Override
    protected String getCurrentUser() {
        RowIterator<Row> it = queryAndAwait("SELECT USER()").iterator();
        return it.hasNext() ? it.next().getString(0) : "";
    }

    @Override
    protected boolean checkMigrationTable() {
        RowIterator<Row> it = queryAndAwait(
                "SELECT count(*) FROM information_schema.tables" +
                    " WHERE table_schema = DATABASE() AND table_name='" + table + "' LIMIT 1")
                .iterator();
        int tmp = it.hasNext() ? it.next().getInteger(0) : 0;
        return tmp > 0;
    }

    @Override
    protected Boolean tryLock() {
        RowIterator<Row> it = queryAndAwait("SELECT GET_LOCK('" + lockName + "',10)").iterator();
        int r = it.hasNext() ? it.next().getInteger(0) : 0;
        return r == 1;
    }

    @Override
    protected void unlock() {
        queryAndAwait("SELECT RELEASE_LOCK('" + lockName + "')");
    }

    private String sc(String name) {
        return "QUOTE(DATABASE()).'" + name + "')";
    }

    @Override
    protected void cleanSchema() {

        // clean all events
        for (Row row : queryAndAwait("SELECT event_name FROM information_schema.events WHERE event_schema=DATABASE()")) {
            queryAndAwait("DROP EVENT " + sc(row.getString(0)));
        }

        // delete all routines
        for (Row row : queryAndAwait("SELECT routine_name as 'N', routine_type as 'T' " +
                "FROM information_schema.routines WHERE routine_schema=DATABASE()")) {
            queryAndAwait("DROP " + row.getString(0) + " " + sc(row.getString(1)));
        }

        // delete all views
        for (Row row : queryAndAwait("SELECT table_name FROM information_schema.views WHERE table_schema=DATABASE()")) {
            queryAndAwait("DROP VIEW " + sc(row.getString(0)));
        }

        // delete all tables
        for (Row row : queryAndAwait("SELECT table_name FROM information_schema.tables" +
                " WHERE table_schema=DATABASE() AND table_type IN ('BASE TABLE', 'SYSTEM VERSIONED')")) {
            queryAndAwait("DROP TABLE IF EXISTS " + sc(row.getString(0)));
        }
        queryAndAwait("SET FOREIGN_KEY_CHECKS = 1");

         // delete all sequences
        for (Row row : queryAndAwait("SELECT table_name FROM information_schema.tables" +
                " WHERE table_schema=DATABASE() AND table_type='SEQUENCE'")) {
            queryAndAwait(" DROP SEQUENCE " + sc(row.getString(0)));
        }
    }

    protected String getInsertMigrationSQL() {
        return "INSERT INTO " + table +
                " (id,version,description,type,script,checksum,execution_time,success,installed_by)" +
                " VALUES (?,?,?,?,?,?,?,?,?)";
    }

    @Override
    protected String historyTableSql() {
        return "CREATE TABLE " + table + " (\n" +
                "    `id` INT NOT NULL,\n" +
                "    `version` VARCHAR(50),\n" +
                "    `description` VARCHAR(200) NOT NULL,\n" +
                "    `type` VARCHAR(20) NOT NULL,\n" +
                "    `script` VARCHAR(1000) NOT NULL,\n" +
                "    `checksum` BIGINT,\n" +
                "    `installed_by` VARCHAR(100) NOT NULL,\n" +
                "    `installed_on` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                "    `execution_time` INT NOT NULL,\n" +
                "    `success` BOOL NOT NULL,\n" +
                "    CONSTRAINT `" + table + "_pk` PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB;\n" +
                "CREATE INDEX `" + table + "_s_idx` ON " + table + " (`success`);";
    }

}
