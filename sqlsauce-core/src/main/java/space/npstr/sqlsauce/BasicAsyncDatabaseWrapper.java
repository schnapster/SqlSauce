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

import javax.annotation.CheckReturnValue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Created by napster on 15.07.18.
 * <p>
 * Very basic example implementation of {@link AsyncDatabaseWrapper}.
 */
public class BasicAsyncDatabaseWrapper implements AsyncDatabaseWrapper {

    private final DatabaseWrapper databaseWrapper;
    private final ScheduledExecutorService executor;

    public BasicAsyncDatabaseWrapper(DatabaseWrapper databaseWrapper, int poolSize) {
        this.databaseWrapper = databaseWrapper;
        final AtomicInteger threadCounter = new AtomicInteger();
        this.executor = Executors.newScheduledThreadPool(poolSize,
                r -> new Thread(r, "async-database-executor-t" + threadCounter.getAndIncrement()));
    }

    @Override
    @CheckReturnValue
    public <E> CompletionStage<E> execute(Function<DatabaseWrapper, E> databaseOperation) {
        return CompletableFuture.supplyAsync(
                () -> databaseOperation.apply(this.databaseWrapper),
                this.executor
        );
    }
}
