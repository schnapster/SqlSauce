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
