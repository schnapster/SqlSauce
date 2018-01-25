/*
 * MIT License
 *
 * Copyright (c) 2017-2018, Dennis Neufeld
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

package space.npstr.sqlsauce;
import javax.annotation.Nullable;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by napster on 20.01.18.
 * <p>
 * Provides a database connection
 */
@ExtendWith(LogExceptionExtension.class)
public abstract class BaseTest {

    public static final String TEST_JDBC_URL_ENV = "TEST_DB_JDBC";

    private static final Logger log = LoggerFactory.getLogger(BaseTest.class);

    @Nullable
    private static DatabaseConnection dbConn;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (dbConn != null) {
                dbConn.shutdown();
            }
        }));
    }

    protected static String getTestJdbcUrl() {
        String jdbc = System.getenv(TEST_JDBC_URL_ENV);
        Assertions.assertNotNull(jdbc, String.format("Jdbc test url %s environment variable is null", TEST_JDBC_URL_ENV));
        return jdbc;
    }

    private synchronized static DatabaseConnection getDbConn() {
        if (dbConn == null) {
            dbConn = new DatabaseConnection.Builder(BaseTest.class.getSimpleName(), getTestJdbcUrl())
                    .setHibernateProperty("hibernate.hbm2ddl.auto", "none")
                    .build();
        }
        return dbConn;
    }

    public DatabaseConnection requireConnection() {
        DatabaseConnection conn = getDbConn();
        Assertions.assertTrue(conn.isAvailable(), "Database connection is unavailable. Is the test database up and running?");
        return conn;
    }
}
