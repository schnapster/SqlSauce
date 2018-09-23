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
import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.entities.SaucedEntity;
import space.npstr.sqlsauce.fp.types.EntityKey;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Created by napster on 23.09.18.
 */
public class ConcurrentValueChangeTest extends BaseTest {

    //language=PostgreSQL
    private static final String CREATE_TABLE_LEDGER
            = "CREATE TABLE ledger "
            + "( "
            + "    ledger_id    bigint NOT NULL, "
            + "    balance      bigint NOT NULL, "
            + "    CONSTRAINT ledger_pkey PRIMARY KEY (ledger_id) "
            + ");";

    @Test
    public void incrementationTest() {
        long ledgerId = 1;
        int transactionsCount = 10_000;
        int maxTransactionValue = 100;
        int concurrentThreads = 100;

        DatabaseWrapper wrapper = new DatabaseWrapper(requireConnection());
        wrapper.executeSqlQuery(String.format(DROP_TABLE_IF_EXISTS, "ledger"));
        wrapper.executeSqlQuery(CREATE_TABLE_LEDGER);

        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(concurrentThreads);

        Ledger ledger = new Ledger(ledgerId);
        ledger = wrapper.persist(ledger);
        assertNotNull(ledger);

        List<Long> transactions = new ArrayList<>();
        for (int ii = 0; ii < transactionsCount; ii++) {
            transactions.add(ThreadLocalRandom.current().nextLong(1, maxTransactionValue + 1));
        }

        Function<Long, Ledger> inc = increase -> {
            return wrapper.doInPersistenceContext(entityManager -> {
                EntityKey<Long, Ledger> key = EntityKey.of(ledgerId, Ledger.class);
                synchronized (SaucedEntity.getEntityLock(key)) {
                    Ledger led = entityManager.find(Ledger.class, ledgerId);
                    led.incBalance(increase);
                    led = entityManager.merge(led);
                    entityManager.getTransaction().commit();
                    return led;
                }
            });
        };

        CompletableFuture[] futures = transactions.stream()
                .map(transaction -> CompletableFuture.supplyAsync(
                        () -> inc.apply(transaction), executor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture<Void> allTransactions = CompletableFuture.allOf(futures);

        try {
            allTransactions.get(3, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        long expectedBalance = transactions.stream().mapToLong(l -> l).sum();
        ledger = wrapper.doInPersistenceContext(entityManager -> entityManager.find(Ledger.class, ledgerId));

        assertEquals(expectedBalance, ledger.balance);
    }

    @Entity
    @Table(name = "ledger")
    public static class Ledger extends SaucedEntity<Long, Ledger> {

        @Id
        @Column(name = "ledger_id", nullable = false)
        private long id;

        @Column(name = "balance", nullable = false)
        private long balance = 0;

        protected Ledger() {}

        Ledger(long id) {
            this.id = id;
        }

        @Override
        public Ledger setId(Long id) {
            this.id = id;
            return this;
        }

        @Override
        public Long getId() {
            return this.id;
        }

        public Ledger incBalance(long amount) {
            this.balance += amount;
            return this;
        }
    }
}
