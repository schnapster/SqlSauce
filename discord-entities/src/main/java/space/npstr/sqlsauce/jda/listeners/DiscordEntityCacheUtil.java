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

package space.npstr.sqlsauce.jda.listeners;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import space.npstr.sqlsauce.DatabaseException;
import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.entities.discord.BaseDiscordGuild;
import space.npstr.sqlsauce.entities.discord.BaseDiscordUser;
import space.npstr.sqlsauce.fp.types.EntityKey;
import space.npstr.sqlsauce.fp.types.Transfiguration;

import javax.persistence.PersistenceException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by napster on 03.08.18.
 * <p>
 * Methods for {@link CacheableGuild}s and {@link CacheableUser}s
 */
public class DiscordEntityCacheUtil {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiscordEntityCacheUtil.class);


    // ################################################################################
    // ##                               Guild methods
    // ################################################################################

    /**
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    public static <E extends BaseDiscordGuild<E> & CacheableGuild<E>> E cacheGuild(final DatabaseWrapper dbWrapper,
                                                                                   final Guild guild,
                                                                                   final Class<E> clazz) {

        return dbWrapper.findApplyAndMerge(EntityKey.of(guild.getIdLong(), clazz),
                discordGuild -> discordGuild.set(guild));
    }

    /**
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    public static <E extends BaseDiscordGuild<E> & CacheableGuild<E>> E joinGuild(final DatabaseWrapper dbWrapper,
                                                                                  final Guild guild,
                                                                                  final Class<E> clazz) {

        return dbWrapper.findApplyAndMerge(EntityKey.of(guild.getIdLong(), clazz),
                discordGuild -> discordGuild.set(guild).join());
    }

    /**
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    public static <E extends BaseDiscordGuild<E> & CacheableGuild<E>> E leaveGuild(final DatabaseWrapper dbWrapper,
                                                                                   final Guild guild,
                                                                                   final Class<E> clazz) {

        return dbWrapper.findApplyAndMerge(EntityKey.of(guild.getIdLong(), clazz),
                discordGuild -> discordGuild.set(guild).leave());
    }

    /**
     * Cache a bunch of guilds
     * Difference to the sync method is that this won't double check the saved DiscordGuilds against their presence in
     * the bot. This method is fine to call to cache only a subset of all guilds
     *
     * @param dbWrapper
     *         The database to run the sync on
     * @param guilds
     *         Stream over all guilds to be cached
     * @param clazz
     *         Class of the actual DiscordGuild entity
     *
     * @return DatabaseExceptions caused by the execution of this method
     */
    public static <E extends BaseDiscordGuild<E> & CacheableGuild<E>> Collection<DatabaseException> cacheAllGuilds(
            final DatabaseWrapper dbWrapper,
            final Stream<Guild> guilds,
            final Class<E> clazz) {

        long started = System.currentTimeMillis();
        final AtomicInteger joined = new AtomicInteger(0);
        final AtomicInteger streamed = new AtomicInteger(0);
        final Function<Guild, Function<E, E>> cacheAndJoin = guild -> discordguild -> {
            E result = discordguild.set(guild);
            if (!result.isPresent()) {
                result = result.join();
                joined.incrementAndGet();
            }
            return result;
        };
        final Stream<Transfiguration<Long, E>> transfigurations = guilds.map(
                guild -> {
                    if (streamed.incrementAndGet() % 100 == 0) {
                        log.debug("{} guilds processed while caching", streamed.get());
                    }
                    return Transfiguration.of(EntityKey.of(guild.getIdLong(), clazz), cacheAndJoin.apply(guild));
                }
        );

        final List<DatabaseException> exceptions = new ArrayList<>(dbWrapper.findApplyAndMergeAll(transfigurations));

        log.debug("Cached {} DiscordGuild entities of class {} in {}ms with {} exceptions, joined {}", streamed.get(),
                clazz.getSimpleName(), System.currentTimeMillis() - started, exceptions.size(), joined.get());
        return exceptions;
    }

    /**
     * Sync the data in the database with the "real time" data in JDA / Discord
     * Useful to keep data meaningful even after downtime (restarting or other reasons)
     *
     * @param dbWrapper
     *         The database to run the sync on
     * @param guilds
     *         Stream over all guilds to be cached and set to be present
     * @param isPresent
     *         Returns true if we are present in a guild (by guildId), used to sync guilds that we left
     * @param clazz
     *         Class of the actual DiscordGuild entity
     *
     * @return DatabaseExceptions caused by the execution of this method
     */
    public static <E extends BaseDiscordGuild<E> & CacheableGuild<E>> Collection<DatabaseException> syncGuilds(
            final DatabaseWrapper dbWrapper,
            final Stream<Guild> guilds,
            final Function<Long, Boolean> isPresent,
            final Class<E> clazz) {

        final List<DatabaseException> exceptions = new ArrayList<>();
        //leave guilds that we arent part of first
        final AtomicInteger left = new AtomicInteger(0);
        long started = System.currentTimeMillis();
        final Function<E, E> leaveIfNotPresent = discordguild -> {
            if (discordguild.isPresent() && !isPresent.apply(discordguild.getGuildId())) {
                left.incrementAndGet();
                return discordguild.leave();
            }
            return discordguild;
        };
        try {
            final int transformed = dbWrapper.applyAndMergeAll(clazz, leaveIfNotPresent);
            log.debug("Synced {} DiscordGuild entities of class {} in {}ms, left {}",
                    transformed, clazz.getSimpleName(), System.currentTimeMillis() - started, left.get());
        } catch (final DatabaseException e) {
            exceptions.add(e);
        }
        //then update existing guilds
        exceptions.addAll(cacheAllGuilds(dbWrapper, guilds, clazz));
        return exceptions;
    }


    // ################################################################################
    // ##                            User/Member methods
    // ################################################################################

    /**
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    public static <E extends BaseDiscordUser<E> & CacheableUser<E>> E cacheUser(final DatabaseWrapper dbWrapper,
                                                                                final User user,
                                                                                final Class<E> clazz) {

        return dbWrapper.findApplyAndMerge(EntityKey.of(user.getIdLong(), clazz),
                discordUser -> discordUser.set(user));
    }

    /**
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    public static <E extends BaseDiscordUser<E> & CacheableUser<E>> E cacheMember(final DatabaseWrapper dbWrapper,
                                                                                  final Member member,
                                                                                  final Class<E> clazz) {

        return dbWrapper.findApplyAndMerge(EntityKey.of(member.getUser().getIdLong(), clazz),
                discordUser -> discordUser.set(member));
    }

    /**
     * Cache a bunch of users.
     * Useful to keep data meaningful even after downtime (restarting or other reasons)
     *
     * @param dbWrapper
     *         The database to run the sync on
     * @param members
     *         Stream over all members to be cached
     * @param clazz
     *         Class of the actual DiscordUser entity
     *
     * @return DatabaseExceptions caused by the execution of this method
     */
    public static <E extends BaseDiscordUser<E> & CacheableUser<E>> Collection<DatabaseException> cacheAllMembers(
            final DatabaseWrapper dbWrapper,
            final Stream<Member> members,
            final Class<E> clazz) {

        final long started = System.currentTimeMillis();
        final AtomicInteger streamed = new AtomicInteger(0);

        final Function<Member, Function<E, E>> cache = member -> discorduser -> discorduser.set(member);


        final Stream<Transfiguration<Long, E>> transfigurations = members.map(
                member -> {
                    if (streamed.incrementAndGet() % 1000 == 0) {
                        log.debug("{} users processed while caching", streamed.get());
                    }
                    return Transfiguration.of(EntityKey.of(member.getUser().getIdLong(), clazz), cache.apply(member));
                }
        );

        final List<DatabaseException> exceptions = new ArrayList<>(dbWrapper.findApplyAndMergeAll(transfigurations));

        log.debug("Cached {} DiscordUser entities of class {} in {}ms with {} exceptions.",
                streamed.get(), clazz.getSimpleName(), System.currentTimeMillis() - started, exceptions.size());
        return exceptions;
    }

    //this is a util class
    private DiscordEntityCacheUtil() {}
}
