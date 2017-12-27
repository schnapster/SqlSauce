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

package space.npstr.sqlsauce.jda.listeners;

import net.dv8tion.jda.core.hooks.ListenerAdapter;
import space.npstr.sqlsauce.DatabaseTask;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Created by napster on 30.10.17.
 */
public abstract class CachingListener<E, Self extends CachingListener<E, Self>> extends ListenerAdapter {

    private final ThreadPoolExecutor cachePump;
    protected final Class<E> entityClass;

    protected CachingListener(final Class<E> entityClass) {
        this.entityClass = entityClass;
        this.cachePump = new ThreadPoolExecutor(1, Runtime.getRuntime().availableProcessors(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> new Thread(runnable, "cache-pump-worker-" + entityClass.getSimpleName()));
    }

    /**
     * Adjust the thread pool size of the cache pump of this listener.
     */
    @Nonnull
    @CheckReturnValue
    public Self setWorkerSize(final int size) {
        this.cachePump.setCorePoolSize(size);
        return getThis();
    }

    /**
     * @return amount of tasks queued up for execution
     */
    public int getQueueSize() {
        return cachePump.getQueue().size();
    }

    protected void submit(@Nonnull final DatabaseTask task, @Nonnull final Consumer<Exception> onFail) {
        this.cachePump.execute(() -> {
            try {
                task.run();
            } catch (final Exception e) {
                onFail.accept(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    protected Self getThis() {
        return (Self) this;
    }

}
