# Barn Quarkus extension

[![License](https://img.shields.io/github/license/lorislab/quarkus-barn?style=for-the-badge&logo=apache)](https://www.apache.org/licenses/LICENSE-2.0)
[![GitHub Workflow Status (branch)](https://img.shields.io/github/workflow/status/lorislab/quarkus-barn/build/master?logo=github&style=for-the-badge)](https://github.com/lorislab/quarkus-barn/actions?query=workflow%3Abuild)
[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/lorislab/quarkus-barn?sort=semver&logo=github&style=for-the-badge)](https://github.com/lorislab/quarkus-barn/releases/latest)
[![Maven Central](https://img.shields.io/maven-central/v/org.lorislab.quarkus/barn?logo=java&style=for-the-badge)](https://maven-badges.herokuapp.com/maven-central/org.lorislab.quarkus/barn)


Barn [Quarkus](https://quarkus.io/) extension is database migration tool for the reactive database client. The naming convention for migration scripts is as in [Flyway](https://flywaydb.org/documentation/migrations) documentation.
Supported are `Versioned` and `Repeatable` migration scripts. 
 
#### Versioned Migrations

Example: `V2__Add_new_table.sql`

* Prefix: `V`
* Version: Version with dots separate as many parts as you like.
* Separator: `__` (two underscores)
* Description: Underscores or spaces separate the words
* Suffix: `.sql`

#### Repeatable Migrations

`Repeatable` migration will be executed only if `checksum` of the script change and there is 
new `Versioned` migration to run.
 
Example: `R__Add_new_table.sql`

* Prefix: `R`
* Separator: `__` (two underscores)
* Description: Underscores or spaces separate the words
* Suffix: `.sql`

### How to use it

Add maven dependency for your database pool:
* org.lorislab.quarkus:barn-pg-client
* org.lorislab.quarkus:barn-mysql-client

Create your database migration SQL scripts in the `src/main/resources/db/migration` directory.
```shell script
+ src
   + main
      + resources
         + db
            + migration
               - V1.0_Create_tables.sql
               - V1.1_Add_search_index.sql
               - R__Update_descruption.sql
```
Add these properties to the `applications.properties`
```properties
# Clean schema before migration. Default: false
quarkus.barn.clean-at-start=true
# Start migration at start. Default: false
quarkus.barn.migrate-at-start=true
# Import test data after migration. Default: false 
quarkus.barn.test-data=true
# Test data scripts. Default: empty array
quarkus.barn.test-data-scripts=db/import/test1.sql,db/import/test2.sql
```

### Postgres SQL reactive client

Maven dependency
```
<dependency>
    <groupId>org.lorislab.quarkus</groupId>
    <artifactId>barn-pg-client</artifactId>
    <version>{latest-release-version}</version>
</dependency>
```

### Mysql reactive client

Maven dependency
```
<dependency>
    <groupId>org.lorislab.quarkus</groupId>
    <artifactId>barn-mysql-client</artifactId>
    <version>{latest-release-version}</version>
</dependency>
```
