package space.npstr.sqlsauce.jda.listeners;

import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.guild.update.GenericGuildUpdateEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.sqlsauce.DatabaseException;
import space.npstr.sqlsauce.entities.discord.DiscordGuild;

/**
 * Created by napster on 20.10.17.
 * <p>
 * Caches Guild data
 */
public class GuildCachingListener<E extends DiscordGuild<E>> extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(GuildCachingListener.class);

    private final Class<E> entityClass;

    public GuildCachingListener(Class<E> entityClass) {
        this.entityClass = entityClass;
    }


    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        try {
            DiscordGuild.join(event.getGuild(), entityClass);
        } catch (DatabaseException e) {
            log.error("Failed to cache event {} for guild {}",
                    event.getClass().getSimpleName(), event.getGuild().getIdLong(), e);
        }
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        try {
            DiscordGuild.leave(event.getGuild(), entityClass);
        } catch (DatabaseException e) {
            log.error("Failed to cache event {} for guild {}",
                    event.getClass().getSimpleName(), event.getGuild().getIdLong(), e);
        }
    }

    @Override
    public void onGenericGuildUpdate(GenericGuildUpdateEvent event) {
        try {
            DiscordGuild.cache(event.getGuild(), entityClass);
        } catch (DatabaseException e) {
            log.error("Failed to cache event {} for guild {}",
                    event.getClass().getSimpleName(), event.getGuild().getIdLong(), e);
        }
    }
}
