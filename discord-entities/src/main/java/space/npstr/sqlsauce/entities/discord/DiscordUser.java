package space.npstr.sqlsauce.entities.discord;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.impl.UserImpl;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.NaturalId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.sqlsauce.DatabaseException;
import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.converters.PostgresHStoreConverter;
import space.npstr.sqlsauce.entities.SaucedEntity;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public abstract class DiscordUser<Self extends SaucedEntity<Long, Self>> extends SaucedEntity<Long, Self> {

    @Transient
    private static final Logger log = LoggerFactory.getLogger(DiscordUser.class);


    @Transient
    public static final String UNKNOWN_NAME = "Unknown User";

    @Id
    @NaturalId
    @Column(name = "user_id", nullable = false)
    protected long userId;


    @Column(name = "name", nullable = false, columnDefinition = "text")
    @ColumnDefault(value = UNKNOWN_NAME)
    protected String name = UNKNOWN_NAME;

    @Column(name = "discriminator", nullable = false)
    @ColumnDefault(value = "-1")
    protected short discriminator;

    @Column(name = "avatar_id", nullable = true, columnDefinition = "text")
    protected String avatarId;

    @Column(name = "bot", nullable = false)
    @ColumnDefault(value = "false")
    protected boolean bot;

    @Column(name = "nicks", columnDefinition = "hstore")
    @Convert(converter = PostgresHStoreConverter.class)
    @Nonnull
    protected final Map<String, String> nicks = new HashMap<>();


    // ################################################################################
    // ##                               Boilerplate
    // ################################################################################

    @Nonnull
    @Override
    @CheckReturnValue
    public Self setId(final Long userId) {
        this.userId = userId;
        return getThis();
    }

    @Nonnull
    @Override
    public Long getId() {
        return this.userId;
    }

    @Override
    public boolean equals(final Object obj) {
        return (obj instanceof DiscordUser) && ((DiscordUser) obj).userId == this.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.userId);
    }


    // ################################################################################
    // ##                               Caching
    // ################################################################################

    //Good idea to call this each time you are loading one of these before saving.
    @Nonnull
    //set general user values
    public Self set(@Nullable final User user) {
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
    @Nonnull
    //set guild specific user values (additionally to user specific ones)
    public Self set(@Nullable final Member member) {
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

    @Nonnull
    public static <E extends DiscordUser<E>> DiscordUser<E> cache(@Nonnull final User user,
                                                                  @Nonnull final Class<E> clazz)
            throws DatabaseException {
        return cache(getDefaultSauce(), user, clazz);
    }

    @Nonnull
    public static <E extends DiscordUser<E>> DiscordUser<E> cache(@Nonnull final DatabaseWrapper dbWrapper,
                                                                  @Nonnull final User user,
                                                                  @Nonnull final Class<E> clazz)
            throws DatabaseException {
        return dbWrapper.findApplyAndMerge(user.getIdLong(), clazz, (discordUser) -> discordUser.set(user));
    }

    @Nonnull
    public static <E extends DiscordUser<E>> DiscordUser<E> cache(@Nonnull final Member member,
                                                                  @Nonnull final Class<E> clazz)
            throws DatabaseException {
        return cache(getDefaultSauce(), member, clazz);
    }

    @Nonnull
    public static <E extends DiscordUser<E>> DiscordUser<E> cache(@Nonnull final DatabaseWrapper dbWrapper,
                                                                  @Nonnull final Member member,
                                                                  @Nonnull final Class<E> clazz)
            throws DatabaseException {
        return dbWrapper.findApplyAndMerge(member.getUser().getIdLong(), clazz,
                                           (discordUser) -> discordUser.set(member));
    }

    @Nonnull
    public static <E extends DiscordUser<E>> Collection<DatabaseException> cacheAll(@Nonnull final Stream<Member> members,
                                                                                    @Nonnull final Class<E> clazz)
            throws DatabaseException {
        return cacheAll(getDefaultSauce(), members, clazz);
    }

    /**
     * Sync the data in the database with the "real time" data in JDA / Discord
     * Useful to keep data meaningful even after downtime (restarting or other reasons)
     *
     * @param dbWrapper The database to run the sync on
     * @param members   Stream over all members to be cached
     * @param clazz     Class of the actual DiscordUser entity
     * @return DatabaseExceptions that happened while doing processing the stream (so we didnt throw/return)
     */
    @Nonnull
    public static <E extends DiscordUser<E>> Collection<DatabaseException> cacheAll(@Nonnull final DatabaseWrapper dbWrapper,
                                                                                    @Nonnull final Stream<Member> members,
                                                                                    @Nonnull final Class<E> clazz)
            throws DatabaseException {
        final long started = System.currentTimeMillis();
        final Function<Member, Function<E, E>> cache = (member) -> (discorduser) -> discorduser.set(member);
        final List<DatabaseException> exceptions = new ArrayList<>();
        members.forEach(member -> {
            try {
                //noinspection ResultOfMethodCallIgnored
                dbWrapper.findApplyAndMerge(member.getUser().getIdLong(), clazz, cache.apply(member));
            } catch (final DatabaseException e) {
                exceptions.add(e);
                log.error("Db blew up while caching member {} during sync", member, e);
            }
        });
        log.info("Synced DiscordUsers of class {} in {}ms with {} exceptions.",
                clazz.getSimpleName(), System.currentTimeMillis() - started, exceptions.size());
        return exceptions;
    }


    //getters for cached values
    @Nonnull
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

    @Nonnull
    public Map<String, String> getNicks() {
        return this.nicks;
    }

    //convenience getters:

    @Nullable
    public String getNick(final long guildId) {
        return getNicks().get(Long.toString(guildId));
    }

    @Nonnull
    public String asMention() {
        return "<@" + this.userId + ">";
    }

    @Nullable
    public String getAvatarUrl() { //ty JDA
        return this.avatarId == null ? null : "https://cdn.discordapp.com/avatars/" + this.userId + "/" + this.avatarId
                + (this.avatarId.startsWith("a_") ? ".gif" : ".png");
    }

    @Nonnull
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
    // 4. cached username
    // 5. UNKNOWN_NAME
    @Nonnull
    public String getEffectiveName(@Nullable final Guild guild) {
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

            final User user = guild.getJDA().getUserById(this.userId);
            if (user != null) {
                //3
                return user.getName();
            }
        }
        //4 & 5
        return getName();
    }
}
