# Quarkus barn extension

Database migration tool for the reactive database client.

Motivation:
* [Liquibase]() -
* [Flyway]() - 

### How to use it

Create your database migration SQL scripts in the `src/main/resources/db/migration` directory.

### Postgres SQL reactive client

Maven dependency
```
<dependency>
    <groupId>org.lorislab.quarkus</groupId>
    <artifactId>barn-pg-client</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Mysql reactive client

Maven dependency
```
<dependency>
    <groupId>org.lorislab.quarkus</groupId>
    <artifactId>barn-mysql-client</artifactId>
    <version>0.1.0</version>
</dependency>
```

