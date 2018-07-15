# SqlSauce

[![Release](https://img.shields.io/github/tag/napstr/SqlSauce.svg?style=flat-square)](https://jitpack.io/#space.npstr/SqlSauce)
[![Bintray](https://api.bintray.com/packages/napster/SqlSauce/sqlsauce-core/images/download.svg) ](https://bintray.com/napster/SqlSauce/sqlsauce-core/_latestVersion)
[![Build Status Development Branch](https://img.shields.io/travis/napstr/SqlSauce/dev.svg?style=flat-square)](https://travis-ci.org/napstr/SqlSauce/branches)
[![License](https://img.shields.io/github/license/napstr/SqlSauce.svg?style=flat-square)]()
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=space.npstr.SqlSauce%3ASqlSauce&metric=alert_status)](https://sonarcloud.io/dashboard?id=space.npstr.SqlSauce%3ASqlSauce)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/166017256c1846d4800880b95a57e431?style=flat)](https://www.codacy.com/app/napstr/SqlSauce)  


Setting up and configuring database and ORM stuff has been identified by me as a major pain in the ass so this project has two main goals:
- Choose the best all-around tools and use them conveniently
- Strip away crazy config stuff with sane defaults and zero xml files

## Tooling

The chosen tools include
- [PostgreSQL](https://www.postgresql.org/) as the underlying database 
- [JPA](https://en.wikipedia.org/wiki/Java_Persistence_API) as the ORM framework
- [Hibernate](http://hibernate.org/orm/) as the ORM vendor
- [HikariCP](https://github.com/brettwooldridge/HikariCP) as the connection pool
- [Flyway](https://flywaydb.org/) for migrations


Reasons for the choices:
- PostgreSQL because it's an _amazing_ jack of all trades database
- JPA and Hibernate as de-facto industry standards just have a lot of documentation available.
- HikariCP because just look at their benchmarks
- Flyway is incredibly easy to get started with, does it's job and does it well
 
 
This package is meant to embrace these specific tools to their fullest, e.g. there is and will be code specific for these chosen tools.

This package is not meant to make these tools exchangeable.

Tests on [Travis](https://travis-ci.org/napstr/SqlSauce) are executed against **PostgreSQL 9.5**


## Features

![alt text](https://i.imgur.com/CuzucQL.gif "SHOW ME WHAT YOU GOT")

- No EntityManager hassle:  
  A functional approach to the DatabaseWrapper allows you to describe an entity, define a transformation for it, and
execute those without having to touch the persistence context at all.

- Sauced Entities:  
  Built in locking to avoid concurrent INSERTs, since Hibernate does not support PostgreSQL's UPSERTs.
  
- Listen/Notify:  
  Support for sending and receiving PostgreSQL's asynchronous notifications, with proper changefeed support coming soon™


## Adding SqlSauce to your project
There are two repositories supported:
- [JitPack](https://jitpack.io/#space.npstr/SqlSauce) for untested builds straight from github
- [Bintray](https://bintray.com/napster/SqlSauce) for tested builds

While the jitpack repo may provide a build for every commit hash, given the compiling itself is not broken, the bintray
repo only provides released version builds. It may provide tested builds by commit hash too, but that is not guaranteed.

###### Gradle build.gradle
```groovy
    repositories {
        maven { url 'https://jitpack.io' }
        //or
        maven { url 'https://dl.bintray.com/napster/SqlSauce' }
    }

    dependencies {
        compile group: 'space.npstr.SqlSauce', name: 'sqlsauce-core', version: '0.2.3'
    }
```

###### Maven pom.xml
```xml
    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
        <!-- or -->
        <repository>
            <id>bintray-napster-SqlSauce</id>
            <url>https://dl.bintray.com/napster/SqlSauce</url>
        </repository>
    </repositories>

    <dependency>
        <groupId>space.npstr.SqlSauce</groupId>
        <artifactId>sqlsauce-core</artifactId>
        <version>0.2.3</version>
    </dependency>
```

## Additional Modules

- [Discord Entities](https://github.com/napstr/SqlSauce/blob/master/discord-entities)
- [Notifications](https://github.com/napstr/SqlSauce/blob/master/notifications)

## Usage

Short descriptions of how to get started using this.

Please also check out the comments and docs inside of the classes.
They will always be more detailed / up to date than this readme.


### DatabaseConnection

Create a connection:

```java
DatabaseConnection databaseConnection = new DatabaseConnection.Builder("postgres",
     "jdbc:postgresql://localhost:5432/db?user=johndoe&password=highlysecurepw")
        .addEntityPackage("com.example.myapp.db.entities")
        .setAppName("MyApp_v4.2.69_1337"))
        .build();
```

After creating the connection you get access to EntityManagers through `DatabaseConnection#getEntityManager`. Remember
to close them after using them!


### DatabaseWrapper

The `DatabaseWrapper` provides methods to execute plain SQL and JPQL queries, and also loading `IEntity`s/`SaucedEntity`s by id/class.

Creating one is pretty straight forward after setting up the connection:
```java
    DatabaseWrapper dbWrapper = new DatabaseWrapper(databaseConnection);

    // or 
    
    DatabaseWrapper dbWrapper = new DatabaseWrapper(entityManagerFactory, name);
```

The `DatabaseWrapper` handles opening and closing `EntityManager`s and just getting stuff out of the database so you don't have to go through the hassle.

It supports a functional approach to handling entities inside of the persistence context, without you having to manage the persistence context.
For simple transformations, like setting a value on an entity, you can describe that entity by an `EntityKey` (a type for id and class),
and describe the transformation to be done, then hand off those to the `DatabaseWrapper`, which will load the entity and apply
the transformation before merging it back. This will probably never be as fast as running pure SQL queries, but it is fancy af.

  The `DatabaseWrapper` also allows processing entities as streams, which is part of the upcoming JPA 2.2 spec, and is
already supported by Hibernate.

  When modifying `SaucedEntity`s via the `DatabaseWrapper`, the transactions will be locked by hashes of the ids of the entities,
to prevent concurrent INSERTs, as Hibernate does not support PostgreSQL's UPSERT.

#### Asynchronous Requests

JDBC is blocking at its core. This can impact performance of applications when running database requests on the main threads,
especially when the database is located somewhere far away over the network, or when dealing with long running queries.
To deal with this, a very simple contract is offered by SqlSauce in the form of `AsyncDatabaseWrapper`. This contract will be 
accepted by other users of database actions, like the [Discord Entities](https://github.com/napstr/SqlSauce/blob/master/discord-entities)
module.  
A basic implementation `BaseAsyncDatabaseWrapper` is provided, but creating an implementation that fits the end users needs
is encouraged.  
Running all requests through an `AsyncDatabaseWrapper` also allows the end user to implement some kind of retry logic 
or general exception handling for `DatabaseException`s, just to give some ideas.

### Advanced Hibernate Types

SqlSauce supports [advanced types for Hibernate](https://github.com/vladmihalcea/hibernate-types) by the glorious Vlad Mihalcea,
as well as additional custom types found in `hibernate.types`. A full list of `@TypeDef` annotations can be found in the `IEntity` interface.
If your entities implement it or extend from `SaucedEntity`, you can use them right away.


### Migrations

This package supports [flyway](https://flywaydb.org/getstarted/) migrations. 

Create and setup flyway with your preferences, here is a very basic example:
```java
        Flyway flyway = new Flyway();
        flyway.setBaselineOnMigrate(true);
        flyway.setBaselineVersion(MigrationVersion.fromVersion("0"));
        flyway.setBaselineDescription("Base Migration");
        flyway.setLocations("classpath:com/example/db/migrations");
```
Then set it when creating the database connection:
```java
DatabaseConnection databaseConnection = new DatabaseConnection.Builder(name, jdbc)
        ...
        .setFlyway(flyway)
        ...
        .build();
```
During creation knocked off by the `build()` call, the DatabaseConnection will call `Flyway#setDataSource(DataSource)` with the Hikari datasource and `Flyway#migrate()` on it,
after creating the Hikari datasource and before handing the Hikari datasource off to the optional datasource proxy and then Hibernate.


### Hstore

To use the packaged Hstore entity, you need to enabled the hstore extension and create a table: 

```sql
CREATE EXTENSION IF NOT EXISTS hstore;
```

```sql
CREATE TABLE IF NOT EXISTS public.hstorex
(
    name    TEXT COLLATE pg_catalog.\"default\" NOT NULL,
    hstorex HSTORE,
    CONSTRAINT hstorex_pkey PRIMARY KEY (name)
);
```

### Datasource Proxy

The DatabaseConnection supports [datasource-proxy](https://github.com/ttddyy/datasource-proxy)
This is a neat tool to identify slow queries for example, and many more things.

Here is an example that will log any query run on the resulting database connection that exceeds 10 seconds as a warning:
```java
DatabaseConnection databaseConnection = new DatabaseConnection.Builder(name, jdbc)
        ...
        .setProxyDataSourceBuilder(new ProxyDataSourceBuilder()
                .logSlowQueryBySlf4j(10, TimeUnit.SECONDS, SLF4JLogLevel.WARN, "SlowQueryLog")
                .multiline()
        )
        ...
        .build();
```

### Listen/Notify

SqlSauce provides rudimentary support for PostgreSQL's LISTEN/NOTIFY with the [Notifications module.](https://github.com/napstr/SqlSauce/blob/master/notifications)
Better, more extensive, rethinkDB style, changefeed support is planned.


### Prometheus Metrics

#### Hikari Metrics

Hikari provides a [PrometheusMetricsTrackerFactory class](https://github.com/brettwooldridge/HikariCP/blob/dev/src/main/java/com/zaxxer/hikari/metrics/prometheus/PrometheusMetricsTrackerFactory.java)
out of the box. It is recommended to set a proper pool name to identify your statistics correctly.
```java
DatabaseConnection databaseConnection = new DatabaseConnection.Builder(name, jdbc)
        ...
        .setPoolName(name)
        .setHikariStats(new PrometheusMetricsTrackerFactory())
        ...
        .build();
```

#### Hibernate Metrics

The [Prometheus JVM client](https://github.com/prometheus/client_java) provides a [Hibernate package](https://github.com/prometheus/client_java/tree/master/simpleclient_hibernate)
which can be used to instrument hibernate. The `name` will be used to register the `SessionFactoryImpl` of the 
DatabaseConnection on the provided `HibernateStatisticsCollector`. Make sure to `register()` on it only after all 
DatabaseConnections have been set up. 

```java
HibernateStatisticsCollector hibernateStats = new HibernateStatisticsCollector();

DatabaseConnection databaseConnection = new DatabaseConnection.Builder(name, jdbc)
        ...
        .setHibernateStats(hibernateStats)
        ...
        .build();
...
//create any other connections
...
hibernateStats.register(); //call this exactly once after all db connections have been created
```


### Logging

You shouldn't have debug level logging enabled in production anyways, but sometimes developers gotta do what they gotta do,
and end up running debug logging in production for a short while.
If you end up with such a necessity, don't get burned by Hibernate, because it literally clogs your logs.
I noticed about a 3x higher database throughput after disabling the Hibernate debug logs.

The concrete way to go about it depends on the slf4j implementation you are using, for logback adding 
```xml
    <logger name="org.hibernate" level="DEBUG" additivity="false">
    </logger>
```
will completely shut up Hibernate logs. You probably still want to receive Info level or even more important, Warning and 
Error level logs from Hibernate, so you should add your respective appenders for those levels, example:
```xml
    <logger name="org.hibernate" level="DEBUG" additivity="false">
        <appender-ref ref="INFOFILE"/>
        <appender-ref ref="ERRORFILE"/>
        <appender-ref ref="SENTRY"/>
    </logger>
```


## Changelog

### v0.3.0
- Bump Hibernate to 5.3.x. This can break applications of end users depending on additional 5.2.x Hibernate packages. 

### v0.2.5
- Bump backwards compatible dependencies

### v0.2.4
- Introduce the `AsyncDatabaseWrapper` contract

### v0.2.2
- Resolve some mostly cosmetic sonarcloud issues

### v0.2.1
- Fix broken rollbacks in case of exceptions
- Add tests for fetching, merging, deleting entities

### v0.2.0
- Delete deprecated code (mostly static SaucedEntity abuse)

### v0.1.1 through v0.1.5
- Deprecate static abuse of SaucedEntities, Hstore

### v0.1.0
- Deleted deprecated stuff (HashSetStringUserType)
- All dependencies bumped

### v0.0.15
- Add HashSetBasicType to save hash sets of ints, longs and strings as a postgres array of text.

### v0.0.14
- Add HashSetStringUserType to save hash sets of strings as a postgres array of text.

### v0.0.13
- Catch open transactions in the healthcheck

### v0.0.12
- Enforce JPA 2.2
- Fix transactions not being rolled back, instead staying open and blocking the connection, in case of an exception in user code
- Deprecate SshTunnel in facor of [autossh](http://www.harding.motd.ca/autossh/)

### v0.0.11
- Bump Hibernate to 5.2.13.Final
- Fix non-transient wrapper in entity base class
- Better support for using EntityManagerFactories
- Replace classpath scanner 

### v0.0.10
- Fix locks to a) actually work and b) be used by the DatabaseWrapper for merges
- Add a test for concurrent merges

### v0.0.9
- Add Hibernate type: HashSets of Enums mapped as Arrays of enum types

### v0.0.8
- Add missing package-info.java files
- Add a convenience method for creating parameters HashMap to DbUtils

### v0.0.7
- Bintray publishing fix

### v0.0.6
- Revert beta dependencies
- Add package wide nullability annotations
- Improve recovery from faulty SSH tunnel
- Configurable health check periods
- Move NotificationService into its own module

### v0.0.5
- testing and publishing to Bintray

### v0.0.4
- datasource-proxy support
- custom Hibernate types
- Migrations deprecated in favor of flyway support
- rudimentary LISTEN/NOTIFY support
- Fix health check
- Fix tunnel reconnection

### v0.0.3
General:
- Main module was renamed 'sqlsauce' -> 'sqlsauce-core'
- Dependency bumps
- DatabaseExceptions are unchecked now

Sauced Entities:
- Improved locking performance by using hashed locks
- Make HStore static methods use functional wrapper methods (see below)
- Using the correct column definition (text) for Hstore names
- Add lookup methods with Nullable returns
- EntityKey type to exactly describe an entity (class + id)

Connection / Wrapper:
- Better support for functional paradigms in the DatabaseWrapper. For example a Function<Entity, Entity> can be passed
and will be applied to the specified entity, so you can modify entities without detaching them from the persistence context;
- Add a method that handles a stream of entities from a query (coming with JPA2.2, already implemented in Hibernate) and applies a Function on them
- Added single result JPQL query method
- Default connection count lowered
- Obtaining an EntityManager while a disconnect has been discovered and is being fixed will now failfast with a DatabaseException instead of timing out
- Reworked the connection builder to allow full customization of hibernate and hikari properties/config, which also allows adding 2nd level cache
- Connection check can be turned off / overridden by own implementation
- Turn off hibernate logging, enable statistics only when metrics are used
- Add method for native sql queries without any result class or mapping

Migrations:
- Add Migrations to the Builder, meaning they will be run after building the connection and before returning it
- Migrations can be named (to prevent accidental changes of file names running them again)
- Theres a SimpleMigration base class for running migrations with parameterless sql queries even easier


### v0.0.2
- Initial release of Discord Entities module with Proof of Concept for Guilds and Users + JDA listeners to cache them
- Support plain SQL queries with result mapping
- Actually enable Hibernate statistics collection
- Set the Dialect to be used in the DatabaseConnection. This is needed in some cases when the automagic recognition fails due to several Dialects are present in the class path.
- Add an HStore converter that accepts Null values. Default HStore converter converts null values into empty maps.
- Better naming for some methods (more concise and adhering to general conventions)
- More annotations of all kinds
- Changed a wrong package name
- Writes and Reads for entities are secured by a cheap lock implementation. The acquiring of the lock can be overriden with custom implementations (lock striping, anyone?)
- Static default Sauce is set for all Sauced Entities whenever a DatabaseConnection is created - works great with single-connection apps
- Unwrap the DatabaseConnection from the DatabaseWrapper
- Less strict persist method, accepting IEntities instead of SaucedEntities

### v0.0.1
- Initial release

## TODOs

- explore java 9 modularization
- add hibernate enhancer plugin to documentation (? usage was hacky, maybe hold off on that one)
- update docs with all the new stuff (LISTEN/NOTIFY, flyway, ds proxy, etc)
- more unit tests - cant expect this to be taken serious without those


## Dependencies

This project requires **Java 8**  
Dependencies are managed automagically by Gradle, some of these are optional / need to be provided to take advantage of.
See the respective `build.gradle` for details.

- **PostgreSQL JDBC Driver**:
  - [Website](https://jdbc.postgresql.org/)
  - [Source Code](https://github.com/pgjdbc/pgjdbc)
  - [The PostgreSQL License](http://www.postgresql.org/about/licence/) & [BSD 2-clause "Simplified" License](https://jdbc.postgresql.org/about/license.html)
  - [Maven Repository](https://mvnrepository.com/artifact/org.postgresql/postgresql)

- **Hibernate ORM**:
  - [Website](http://hibernate.org/orm/)
  - [Source Code](https://github.com/hibernate/hibernate-orm)
  - [GNU Lesser General Public License](http://hibernate.org/community/license/)
  - [Maven Repository](https://mvnrepository.com/artifact/org.hibernate/hibernate-core)

- **Hikari CP**:
  - [Source Code](https://github.com/brettwooldridge/HikariCP)
  - [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
  - [Maven Repository](https://mvnrepository.com/artifact/com.zaxxer/HikariCP)

- **Hibernate Types**:
  - [Source Code](https://github.com/vladmihalcea/hibernate-types)
  - [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
  - [Maven Repository](https://mvnrepository.com/artifact/com.vladmihalcea/hibernate-types-52)

- **Guava**
  - [Source Code](https://github.com/google/guava)
  - [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
  - [Maven Repository](https://mvnrepository.com/artifact/com.google.guava/guava)

- **datasource-proxy**:
  - [Source Code](https://github.com/ttddyy/datasource-proxy/)
  - [MIT License](http://www.opensource.org/licenses/MIT)
  - [Maven Repository](https://mvnrepository.com/artifact/net.ttddyy/datasource-proxy)

- **Flyway Core**:
  - [Website](https://flywaydb.org/)
  - [Source Code](https://github.com/flyway/flyway)
  - [Apache License, Version 2.0](https://flywaydb.org/licenses/flyway-community.txt)
  - [Maven Repository](https://mvnrepository.com/artifact/org.flywaydb/flyway-core)

- **Simple Logging Facade for Java**:
  - [Website](https://www.slf4j.org/)
  - [Source Code](https://github.com/qos-ch/slf4j)
  - [MIT License](http://www.opensource.org/licenses/mit-license.php)
  - [Maven Repository](https://mvnrepository.com/artifact/org.slf4j/slf4j-api/)

- **SpotBugs Annotations**:
  - [Website](https://spotbugs.github.io/)
  - [Source Code](https://github.com/spotbugs/spotbugs)
  - [GNU LESSER GENERAL PUBLIC LICENSE, Version 2.1](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.en.html)
  - [Maven Repository](https://mvnrepository.com/artifact/com.github.spotbugs/spotbugs-annotations)

- **Prometheus Simpleclient Hibernate**:
  - [Website](https://prometheus.io/)
  - [Source Code](https://github.com/prometheus/client_java)
  - [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
  - [Maven Repository](https://mvnrepository.com/artifact/io.prometheus/simpleclient_hibernate)
