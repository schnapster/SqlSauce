/*
 * MIT License
 *
 * Copyright (c) 2017, Dennis Neufeld
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

package space.npstr.sqlsauce.entities.discord;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.impl.UserImpl;
import org.hibernate.annotations.ColumnDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.sqlsauce.DatabaseException;
import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.converters.PostgresHStoreConverter;
import space.npstr.sqlsauce.fp.types.EntityKey;
import space.npstr.sqlsauce.fp.types.Transfiguration;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.MappedSuperclass;
import javax.persistence.PersistenceException;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by napster on 17.10.17.
 * <p>
 * Base class for discord user entities. Best used in conjunction with a UserCachingListener.
 * Intended to provide a simple way to save user specific settings etc.
 * <p>
 * Attention: Unless you manually sync these entities between bot downtimes, there is no guarantee that the data is
 * consistent with reality. Consider this: A user changes their avatar while your bot is down (reconnecting, updating
 * whatever). Due to it being down, the change of the avatar is missed, so the avatarId field will contain an outdated
 * value.
 */
@MappedSuperclass
public abstract class DiscordUser<S extends BaseDiscordUser<S>> extends BaseDiscordUser<S> {

    @Transient
    private static final Logger log = LoggerFactory.getLogger(DiscordUser.class);


    @Transient
    public static final String UNKNOWN_NAME = "Unknown User";

    @Column(name = "name", nullable = false, columnDefinition = "text")
    @ColumnDefault(value = UNKNOWN_NAME)
    protected String name = UNKNOWN_NAME;

    @Column(name = "discriminator", nullable = false)
    @ColumnDefault(value = "-1")
    protected short discriminator;

    @Nullable
    @Column(name = "avatar_id", nullable = true, columnDefinition = "text")
    protected String avatarId;

    @Column(name = "bot", nullable = false)
    @ColumnDefault(value = "false")
    protected boolean bot;

    @Column(name = "nicks", columnDefinition = "hstore")
    @Convert(converter = PostgresHStoreConverter.class)
    protected final Map<String, String> nicks = new HashMap<>();


    @Override //to appease sonar cloud...the super method is fine, really.
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override //to appease sonar cloud...the super method is fine, really.
    public int hashCode() {
        return super.hashCode();
    }


    // ################################################################################
    // ##                               Caching
    // ################################################################################

    //Good idea to call this each time you are loading one of these before saving.
    //set general user values
    public S set(@Nullable final User user) {
        if (user == null) {
            return getThis();//gracefully ignore null users
        }
        this.name = user.getName();
        this.discriminator = Short.parseShort(user.getDiscriminator()); //unbox it
        this.avatarId = user.getAvatarId();
        this.bot = user.isBot();
        return getThis();
    }

    //Good idea to call this each time you are loading one of these before saving.
    //set guild specific user values (additionally to user specific ones)
    public S set(@Nullable final Member member) {
        if (member == null) {
            return getThis();//gracefully ignore null members
        }

        set(member.getUser());

        final String nick = member.getNickname();
        if (nick == null) {
            this.nicks.remove(member.getGuild().getId());
        } else {
            this.nicks.put(member.getGuild().getId(), nick);
        }
        return getThis();
    }


    //convenience static setters for cached values


    /**
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    public static <E extends DiscordUser<E>> DiscordUser<E> cache(final DatabaseWrapper dbWrapper, final User user,
                                                                  final Class<E> clazz) {
        return dbWrapper.findApplyAndMerge(EntityKey.of(user.getIdLong(), clazz),
                discordUser -> discordUser.set(user));
    }


    /**
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    public static <E extends DiscordUser<E>> DiscordUser<E> cache(final DatabaseWrapper dbWrapper, final Member member,
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
    public static <E extends DiscordUser<E>> Collection<DatabaseException> cacheAll(final DatabaseWrapper dbWrapper,
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


    //getters for cached values
    public String getName() {
        return this.name;
    }

    public short getDiscriminator() {
        return this.discriminator;
    }

    @Nullable
    public String getAvatarId() {
        return this.avatarId;
    }

    public boolean isBot() {
        return this.bot;
    }

    public Map<String, String> getNicks() {
        return this.nicks;
    }

    //convenience getters:

    @Nullable
    public String getNick(final long guildId) {
        return getNicks().get(Long.toString(guildId));
    }

    public String asMention() {
        return "<@" + this.userId + ">";
    }

    @Nullable
    public String getAvatarUrl() { //ty JDA
        return this.avatarId == null ? null : "https://cdn.discordapp.com/avatars/" + this.userId + "/" + this.avatarId
                + getAvatarEnding(this.avatarId);
    }

    public String getEffectiveAvatarUrl() { //ty JDA
        final String avatarUrl = getAvatarUrl();
        return avatarUrl != null ? avatarUrl
                : "https://discordapp.com/assets/"
                + UserImpl.DefaultAvatar.values()[this.discriminator % UserImpl.DefaultAvatar.values().length].toString()
                + ".png";
    }

    //look up the most fitting name for a user:
    // 1. nickname in provided guild
    // 2. cached nickname in provided guild
    // 3. name of the existing user in the JDA object of the provided guild
    // 3a. name of the existing user in the provided global user lookup
    // 4. cached username
    // 5. UNKNOWN_NAME
    //this is a complete method to find a users name from all possible sources in a clear order of preference for the sources
    public String getEffectiveName(@Nullable final Guild guild, @Nullable Function<Long, User> globalUserLookup) {
        if (guild != null) {
            final Member member = guild.getMemberById(this.userId);
            if (member != null) {
                //1
                return member.getEffectiveName();
            }
            final String cachedNick = getNick(guild.getIdLong());
            if (cachedNick != null && !cachedNick.isEmpty()) {
                //2
                return cachedNick;
            }

            User user = guild.getJDA().getUserById(this.userId);
            if (user != null) {
                //3
                return user.getName();
            }
            if (globalUserLookup != null) {
                user = globalUserLookup.apply(this.userId);
                if (user != null) {
                    //3a
                    return user.getName();
                }
            }
        }
        //4 & 5
        return getName();
    }

    //effective name of a user from the cache, similar to JDAs Member#getEffectiveName
    public String getEffectiveName(long guildId) {
        String nick = getNick(guildId);
        if (nick != null) {
            return nick;
        } else {
            return getName();
        }
    }

    private static String getAvatarEnding(String avatarId) {
        return avatarId.startsWith("a_") ? ".gif" : ".png";
    }
}
