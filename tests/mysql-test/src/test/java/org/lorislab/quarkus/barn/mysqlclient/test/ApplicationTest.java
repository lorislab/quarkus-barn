package org.lorislab.quarkus.barn.mysqlclient.test;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lorislab.quarkus.barn.database.MySqlDatabase;
import org.lorislab.quarkus.barn.models.Migration;

import java.util.ArrayList;
import java.util.List;

@QuarkusTest
public class ApplicationTest extends AbstractTest {

    private static List<Migration> MIGRATIONS = new ArrayList<>() {{
        add(create(0L, "1.0", "Test", "db/migration/V1.0__Test.sql", 1800078697L));
        add(create(1L,"1.1","Test 2","db/migration/V1.1__Test_2.sql",2035203982L));
        add(create(2L,"3.0","Test 3","db/migration/V3.0__Test_3.sql",122262758L));
        add(create(3L,"3.1","Test 3 repeatable","db/migration/V3.1__Test_3_repeatable.sql",2756497484L));
        add(create(4L,"3.2","Test 31 repeatable","db/migration/V3.2__Test_31_repeatable.sql",2756497484L));
        add(create(5L,"3.3","Test 33 repeatable","db/migration/V3.3__Test_33_repeatable.sql",2756497484L));
        add(create(6L,"3.3.1","Test 331 repeatable","db/migration/V3.3.1__Test_331_repeatable.sql",2756497484L));
        add(create(7L,null,"ImportTest","db/migration/R__ImportTest.sql",2916889975L));
        add(create(8L,null,"ImportTest2","db/migration/R__ImportTest2.sql",853669131L));
    }};

    @Test
    public void migrationTest() {
        Pool client = createPool();
        try {
            List<Migration> migrations = loadAllMigration(client);

            // check migrations
            Assertions.assertNotNull(migrations);
            Assertions.assertEquals(9, migrations.size());
            for (int i = 0; i < MIGRATIONS.size(); i++) {
                assertEquals(MIGRATIONS.get(i), migrations.get(i));
            }

            // check tables
            List<TestModel> models = loadAllTestModels(client, "TEST");
            Assertions.assertNotNull(models);
            Assertions.assertEquals(3, models.size());

            List<TestModel> models2 = loadAllTestModels(client, "TEST2");
            Assertions.assertNotNull(models2);
            Assertions.assertEquals(2, models2.size());

            List<TestModel> models3 = loadAllTestModels(client, "TEST3");
            Assertions.assertNotNull(models3);
            Assertions.assertEquals(3, models3.size());

            // check deleted table
            RowIterator<Row> it = client.preparedQuery(MySqlDatabase.checkIfTableExistsQuery("todelete"))
                    .executeAndAwait().iterator();
            Assertions.assertTrue(it.hasNext());
            Assertions.assertEquals(0, it.next().getInteger(0));
        } finally {
            client.close();
        }
    }
}
