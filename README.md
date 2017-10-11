# SqlSauce

[![Release](https://jitpack.io/v/napstr/SqlSauce.svg?style=flat-square)](https://jitpack.io/napstr/SqlSauce)

SQL database stack I use between various projects.

Setting up and configuring database and ORM stuff has been identified by me as a major pain in the ass so this project has two main goals:
- Choose the best all-around tools and use them conveniently
- Strip away crazy config stuff with sane defaults and zero xml files

Bonus goal: Avoid using Spring cause I personally do not like it.


## Tooling

The chosen tools include
- [PostgreSQL](postgresql.org/) as the underlying database 
- [JPA](https://en.wikipedia.org/wiki/Java_Persistence_API) as the ORM framework
- [Hibernate](http://hibernate.org/orm/) as the ORM vendor
- [HikariCP](https://github.com/brettwooldridge/HikariCP) as the connection pool


Reasons for the choices:
- PostgreSQL because it's an _amazing_ jack of all trades database
- JPA and Hibernate as de-facto industry standards just have a lot of documentation available. Also proper care when writing your models will result in Hibernate's autoddl taking care of setting up and migrating your schema.
- HikariCP because just look at their benchmarks
 
 
This package is meant to use these specific tools to their fullest, e.g. there is and will be code specific for these chosen tools.

This package is not meant to make these tools exchangeable.


## Features

![alt text](https://i.imgur.com/CuzucQL.gif "SHOW ME WHAT YOU GOT")

- Ssh tunnel support with reconnect handling. Stay safe, don't expose your databases.

- Sauced Entities:
  Aware of their source, bringing convenience methods to handle them outside of transactions and persist them whenever needed
  
- Migrations:
  A really crude, one-way support to do entity level migrations. Assuming hibernate autoddl to create necessary columns and tables.
Sure, you can also run raw SQL queries.



## TODOs

- test whether this can actually run more than one connection at a time
- improve security of the ssh tunnels