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

package space.npstr.sqlsauce.wrapper;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import space.npstr.sqlsauce.BaseTest;
import space.npstr.sqlsauce.DatabaseConnection;
import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.entities.SaucedEntity;
import space.npstr.sqlsauce.fp.types.EntityKey;
import space.npstr.sqlsauce.fp.types.Transfiguration;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Created by napster on 02.03.18.
 */
public class ApplyFindMergeTest extends BaseTest {

    private static final String TABLE_NAME = "throwExceptionInTransfiguration";

    /**
     * This tests two things:
     * - Make sure that exceptions are propagated up from using the functional method of the wrapper
     * - Make sure the connection/transaction is released in case of an exception thrown as described above
     */
    @Test
    public void throwExceptionInTransfiguration() {
        HikariConfig hikariConfig = DatabaseConnection.Builder.getDefaultHikariConfig();
        hikariConfig.setMaximumPoolSize(1);
        DatabaseConnection databaseConnection = new DatabaseConnection.Builder(BaseTest.class.getSimpleName(), getTestJdbcUrl())
                .setHibernateProperty("hibernate.hbm2ddl.auto", "none")
                .setHikariConfig(hikariConfig)
                .build();


        DatabaseWrapper wrapper = new DatabaseWrapper(databaseConnection);

        //prepare a table
        wrapper.executeSqlQuery(String.format(DROP_TABLE_IF_EXISTS, TABLE_NAME), null);
        wrapper.executeSqlQuery(String.format(CREATE_SIMPLE_TABLE, TABLE_NAME), null);

        E merged = wrapper.merge(new E().setId(42L).setName("unimportant"));
        Assertions.assertNotNull(merged); //spotbugs please

        try {
            wrapper.findApplyAndMerge(Transfiguration.of(EntityKey.of(42L, E.class),
                    e -> {
                        throw new TestException("I need to be propagated up and the connection to be released.");
                    }));
        } catch (TestException e) {
            HikariDataSource dataSource = (HikariDataSource) databaseConnection.getDataSource();
            Assertions.assertEquals(0, dataSource.getHikariPoolMXBean().getActiveConnections(), "Connection was not closed");

            return;//acceptable
        }
        Assertions.fail("Uncaught Exception in findApplyAndMerge was eaten");
    }

    private static class TestException extends RuntimeException {
        private static final long serialVersionUID = 6774679571782242416L;

        public TestException(String message) {
            super(message);
        }
    }

    @Entity
    @Table(name = TABLE_NAME)
    private static final class E extends SaucedEntity<Long, E> {

        @Id
        private long id = 0;
        private String name = "";


        @Override
        public E setId(Long id) {
            this.id = id;
            return this;
        }

        @Override
        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public E setName(String name) {
            this.name = name;
            return this;
        }
    }
}
