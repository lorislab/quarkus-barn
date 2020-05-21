package org.lorislab.quarkus.barn.pgclient.test;

import io.quarkus.test.junit.NativeImageTest;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lorislab.quarkus.barn.database.PostgresDatabase;
import org.lorislab.quarkus.barn.models.Migration;

import java.util.ArrayList;
import java.util.List;

@NativeImageTest
public class ApplicationTestIT extends ApplicationTest {

}
