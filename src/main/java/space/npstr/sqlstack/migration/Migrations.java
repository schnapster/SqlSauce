/*
 * MIT License
 *
 * Copyright (c) 2017 Dennis Neufeld
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package space.npstr.sqlstack.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.sqlstack.DatabaseConnection;
import space.npstr.sqlstack.DatabaseException;
import space.npstr.sqlstack.DatabaseWrapper;
import space.npstr.sqlstack.entities.Hstore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by napster on 10.10.17.
 * <p>
 * Makes sure databases are in a usable state
 * <p>
 * Keep in mind that Hibernate's autoddl does the heavy lifting of adding new tables and columns. This should only ever
 * recalculate data / change column types in a safe way / delete old columns etc.
 */
public class Migrations {

    private static final Logger log = LoggerFactory.getLogger(Migrations.class);
    private static final String MIGRATIONS_HSTORE = "migrations";


    private final List<Migration> registeredMigrations = new ArrayList<>();

    public Migrations() {
    }

    public void registerMigration(final Migration migration) {
        this.registeredMigrations.add(migration);
    }

    public void runMigrations(@Nonnull final DatabaseConnection dbConnection) throws DatabaseException {
        final DatabaseWrapper dbWrapper = new DatabaseWrapper(dbConnection);

        //make sure hstore is enabled
        //NOTE this will only work is we are running as super user. otherwise you have to set it up manually
        // as of the current date there is no way to create the extension programmatically without super user rights
        // so you have to either add the hstore manually each time you create a db, or you enable it by default
        // for each newly created db by running:
        // psql -d template1 -c 'create extension hstore;'
        final String createHstore = "CREATE EXTENSION IF NOT EXISTS hstore SCHEMA public;";
        dbWrapper.executePlainSqlQuery(createHstore, null);
        Hstore.load(dbWrapper, "test").setAndSave("test", "test"); //test it

        //run the registered migrations
        for (final Migration migration : this.registeredMigrations) {
            doMigrations(dbConnection, migration);
        }
    }


    private static void doMigrations(@Nonnull final DatabaseConnection dbConnection, @Nonnull final Migration migration)
            throws DatabaseException {

        final Hstore migrationInfo = Hstore.load(new DatabaseWrapper(dbConnection), MIGRATIONS_HSTORE);
        final String migrationName = migration.getClass().getSimpleName();

        if (!migrationInfo.get(migrationName, "").equals("done")) {
            log.info("Beginning migration {}", migrationName);
            final long started = System.currentTimeMillis();

            migration.up(dbConnection);

            log.info("Migration {} successful after {}ms", migrationName, System.currentTimeMillis() - started);
            //todo theres a small chance of us failing to persist this information
            migrationInfo.setAndSave(migrationName, "done");
        } else {
            log.info("Skipping migration {} since it has been applied already", migrationName);
        }
    }
}
