# Notifications for SqlSauce

Advanced support for [PostgreSQL's LISTEN/NOTIFY](https://www.postgresql.org/docs/current/static/sql-notify.html)
for [SqlSauce](https://github.com/napstr/SqlSauce).

![alt text](https://i.imgur.com/XJWaDZG.gif "Elephant with sunglasses and sombrero walking like a spider in front of Mount Kilimanjaro")

Weird things are happening here.

## Adding SqlSauce Notifications to your project

Make sure to include the core package [SqlSauce](https://github.com/napstr/SqlSauce) in your project.
This module can then be added through this additional dependency:

###### Gradle build.gradle
```groovy
    dependencies {
        compile group: 'space.npstr.SqlSauce', name: 'notifications', version: '0.0.6'
    }
```

###### Maven pom.xml
```xml
    <dependency>
        <groupId>space.npstr.SqlSauce</groupId>
        <artifactId>notifications</artifactId>
        <version>0.0.6</version>
    </dependency>
```


## Usage

Make sure to read the documentation of [SqlSauce](https://github.com/napstr/SqlSauce) as this package may rely on its concepts.

Creating a notification service:
```java
    long interval = 500; //milliseconds
    String name = "MyNotificationService";
    NsExceptionHandler exceptionHandler = new LoggingNsExceptionHandler(log);
    NotificationService notificationService = new NotificationService(jdbcUrl, name, interval, exceptionHandler);
```

Sending a notification:
```java
    String channel = "foo";
    String payload = "bar";
    
    //via a notification service
    notificationService.notif(channel, payload);
    
    //or a databasewrapper
    databaseWrapper.notif(channel, payload);
```

Receiving notifications:
```java
    String channel = "foo";
    notificationService.addListener(
        notification -> log.info(notification.getParameter()),
        channel
    );
```

## Changelog

### v0.0.6
- Initial release as its own module
- Handle uncaught listener exceptions separately
- Add a few basic exception handlers

## TODOs

- full tunnel / healthcheck support
- add trigger creation sql
- abstract notification classes on top of various triggers


## Dependencies

Currently no additional dependencies except for the [sqlsauce-core module](https://github.com/napstr/SqlSauce).