package space.npstr.sqlsauce.jda.listeners;

import net.dv8tion.jda.core.events.ReconnectedEvent;
import net.dv8tion.jda.core.events.guild.GenericGuildEvent;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.guild.update.GenericGuildUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.sqlsauce.DatabaseException;
import space.npstr.sqlsauce.DatabaseTask;
import space.npstr.sqlsauce.entities.discord.DiscordGuild;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * Created by napster on 20.10.17.
 * <p>
 * Caches entities that extend DiscordGuild
 * <p>
 * Limitation: Currently only events relevant for DiscordGuild are listened to. Extending classes might be interested in
 * more events.
 */
public class GuildCachingListener<E extends DiscordGuild<E>> extends CachingListener<E, GuildCachingListener<E>> {

    private static final Logger log = LoggerFactory.getLogger(GuildCachingListener.class);

    public GuildCachingListener(final Class<E> entityClass) {
        super(entityClass);
    }

    @Override
    public void onGuildJoin(final GuildJoinEvent event) {
        onGuildEvent(() -> DiscordGuild.join(event.getGuild(), this.entityClass), event);
    }

    @Override
    public void onGuildLeave(final GuildLeaveEvent event) {
        onGuildEvent(() -> DiscordGuild.leave(event.getGuild(), this.entityClass), event);
    }

    @Override
    public void onGenericGuildUpdate(final GenericGuildUpdateEvent event) {
        onGuildEvent(() -> DiscordGuild.cache(event.getGuild(), this.entityClass), event);
    }

    @Override
    public void onReconnect(ReconnectedEvent event) {
        submit(() -> {
                    Collection<DatabaseException> exceptions = DiscordGuild.cacheAll(
                            event.getJDA().getGuildCache().stream(), this.entityClass);
                    for (DatabaseException e : exceptions) {
                        log.error("Exception when caching all guilds of shard {} on reconnect",
                                event.getJDA().getShardInfo().getShardId(), e);
                    }
                },
                e -> log.error("Failed to cache all guilds of shard {} on reconnect",
                        event.getJDA().getShardInfo().getShardId(), e)
        );
    }

    private void onGuildEvent(@Nonnull final DatabaseTask task, @Nonnull final GenericGuildEvent event) {
        submit(task, e -> log.error("Failed to cache event {} for guild {}",
                event.getClass().getSimpleName(), event.getGuild().getIdLong(), e));
    }
}
