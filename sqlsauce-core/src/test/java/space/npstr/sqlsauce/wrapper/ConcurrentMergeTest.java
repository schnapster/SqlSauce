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

import org.junit.jupiter.api.Test;
import space.npstr.sqlsauce.BaseTest;
import space.npstr.sqlsauce.DatabaseException;
import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.entities.SaucedEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Created by napster on 12.02.18.
 * <p>
 * Test multiple insertions of things with a similar id
 */
public class ConcurrentMergeTest extends BaseTest {

    private static final List<String> NAMES = Arrays.asList(
            "how did I get here",
            "jan quadrant vincent 16",
            "stealy",
            "lil'bits",
            "opposite news",
            "ball fondlers",
            "octopusman",
            "real fake doors",
            "ants in my eyes johnson",
            "alien invasion tomato monster mexican armada brothers",
            "gazorpazorpfield"
    );

    private static final String CREATE_TABLE_INTERDIMENSIONAL_SHOWS
            = "CREATE TABLE interdimensional_shows "
            + "( "
            + "    name TEXT COLLATE pg_catalog.\"default\", "
            + "    CONSTRAINT interdimensional_shows_pkey PRIMARY KEY (name) "
            + ");";


    @Test
    public void insertionTest() throws InterruptedException {

        DatabaseWrapper wrapper = new DatabaseWrapper(requireConnection());

        wrapper.executeSqlQuery(String.format(DROP_TABLE_IF_EXISTS, "interdimensional_shows"));
        wrapper.executeSqlQuery(CREATE_TABLE_INTERDIMENSIONAL_SHOWS);


        ExecutorService pool = Executors.newCachedThreadPool(r -> new Thread(r, ConcurrentMergeTest.class.getSimpleName() + "-worker"));

        AtomicInteger done = new AtomicInteger(0);
        AtomicInteger exceptions = new AtomicInteger(0);

        int tests = 10000;

        for (int i = 0; i < tests; i++) {
            pool.execute(() -> {
                try {
                    InterdimensionalCableShow show = new InterdimensionalCableShow(NAMES.get(ThreadLocalRandom.current().nextInt(NAMES.size())));
                    InterdimensionalCableShow merge = wrapper.merge(show);
                    assertNotNull(merge);//spotbugs and codacy warnings
                } catch (Exception e) {
                    //expecting duplicate key exceptions here if the entity lock system is borked
                    log.error("Exception in {}", ConcurrentMergeTest.class.getSimpleName(), e);
                    exceptions.getAndIncrement();
                }
                done.getAndIncrement();
            });
        }

        long waiting = System.currentTimeMillis();
        while (done.get() < tests) {
            Thread.sleep(100);
            if (System.currentTimeMillis() - waiting > TimeUnit.SECONDS.toMillis(60)) {
                throw new DatabaseException(ConcurrentMergeTest.class.getSimpleName() + " took too long to finish");
            }
        }

        assertEquals(0, exceptions.get(), "Exceptions in " + ConcurrentMergeTest.class.getSimpleName());
    }

    @Entity
    @Table(name = "interdimensional_shows")
    public static class InterdimensionalCableShow extends SaucedEntity<String, InterdimensionalCableShow> {

        @Id
        @Column(name = "name")
        private String name = "";

        InterdimensionalCableShow() {
        }

        public InterdimensionalCableShow(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public InterdimensionalCableShow setId(String name) {
            this.name = name;
            return this;
        }

        @Override
        public String getId() {
            return this.name;
        }
    }
}
