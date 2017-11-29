package space.npstr.sqlsauce.entities.discord;

import net.dv8tion.jda.core.Region;
import net.dv8tion.jda.core.entities.Guild;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.NaturalId;
import space.npstr.sqlsauce.DatabaseException;
import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.entities.SaucedEntity;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import java.util.Objects;

/**
 * Created by napster on 17.10.17.
 * <p>
 * Base class for discord guild entities. Best used in conjunction with a GuildCachingListener.
 * Intended to provide a simple way to save guild specific settings etc.
 * <p>
 * Attention: Unless you manually sync these entities between bot downtimes, there is no guarantee that the data is
 * consistent with reality. Consider this: Your bot gets kicked from a guild while being down. Due to it being down, the
 * leave event is missed, so the left_timestamp and is_present fields will be incorrect.
 */
@MappedSuperclass
public abstract class DiscordGuild<Self extends SaucedEntity<Long, Self>> extends SaucedEntity<Long, Self> {

    @Transient
    public static final String UNKNOWN_NAME = "Unknown Guild";

    @Id
    @NaturalId
    @Column(name = "guild_id", nullable = false)
    protected long guildId;


    //presence stuff

    //when did we join this
    @Column(name = "joined_timestamp", nullable = false)
    @ColumnDefault(value = "-1")
    protected long joined = -1;

    //when did we leave this
    @Column(name = "left_timestamp", nullable = false)
    @ColumnDefault(value = "-1")
    protected long left = -1;

    //are we currently in there or not?
    @Column(name = "present", nullable = false)
    @ColumnDefault(value = "false")
    protected boolean present = false;


    // cached values

    @Column(nullable = false, name = "name", columnDefinition = "text")
    @ColumnDefault(value = UNKNOWN_NAME)
    protected String name = UNKNOWN_NAME;

    @Column(nullable = false, name = "owner_id")
    @ColumnDefault(value = "-1")
    protected long ownerId;

    @Column(nullable = true, name = "icon_id", columnDefinition = "text")
    protected String iconId;

    @Column(nullable = true, name = "splash_id", columnDefinition = "text")
    protected String splashId;

    @Column(nullable = false, name = "region", columnDefinition = "text")
    @ColumnDefault(value = "") //key of the unknown region
    protected String region = Region.UNKNOWN.getKey();      //Region enum key

    @Column(nullable = true, name = "afk_channel_id")
    protected Long afkChannelId;               //VoiceChannel id

    @Column(nullable = true, name = "system_channel_id")
    protected Long systemChannelId;            //TextChannel id

    @Column(nullable = false, name = "verification_level")
    @ColumnDefault(value = "-1") //key of the unknown enum
    protected int verificationLevel;         //Guild.VerificationLevel enum key

    @Column(nullable = false, name = "notification_level")
    @ColumnDefault(value = "-1") //key of the unknown enum
    protected int notificationLevel;         //Guild.NotificationLevel enum key

    @Column(nullable = false, name = "mfa_level")
    @ColumnDefault(value = "-1") //key of the unknown enum
    protected int mfaLevel;                  //Guild.MFALevel enum key

    @Column(nullable = false, name = "explicit_content_level")
    @ColumnDefault(value = "-1") //key of the unknown enum
    protected int explicitContentLevel;      //Guild.ExplicitContentLevel enum key

    @Column(nullable = false, name = "afk_timeout_seconds")
    @ColumnDefault(value = "300") //default value according to JDA docs
    protected int afkTimeoutSeconds;


    // ################################################################################
    // ##                               Boilerplate
    // ################################################################################

    @Nonnull
    @Override
    @CheckReturnValue
    public Self setId(final Long guildId) {
        this.guildId = guildId;
        return getThis();
    }

    @Nonnull
    @Override
    public Long getId() {
        return this.guildId;
    }

    @Override
    public boolean equals(final Object obj) {
        return (obj instanceof DiscordGuild) && ((DiscordGuild) obj).guildId == this.guildId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.guildId);
    }

    
    // ################################################################################
    // ##                               Caching
    // ################################################################################

    //Good idea to call this each time you are loading one of these before saving.
    @Nonnull
    public Self set(@Nullable final Guild guild) {
        if (guild == null) {
            return getThis();//gracefully ignore null guilds
        }
        this.name = guild.getName();
        this.ownerId = guild.getOwner().getUser().getIdLong();
        this.iconId = guild.getIconId();
        this.splashId = guild.getSplashId();
        this.region = guild.getRegion().getKey();
        this.afkChannelId = guild.getAfkChannel() != null ? guild.getAfkChannel().getIdLong() : null;
        this.systemChannelId = guild.getSystemChannel() != null ? guild.getSystemChannel().getIdLong() : null;
        this.verificationLevel = guild.getVerificationLevel().getKey();
        this.notificationLevel = guild.getDefaultNotificationLevel().getKey();
        this.mfaLevel = guild.getRequiredMFALevel().getKey();
        this.explicitContentLevel = guild.getExplicitContentLevel().getKey();
        this.afkTimeoutSeconds = guild.getAfkTimeout().getSeconds();
        return getThis();
    }

    //convenience static setters for cached values

    //joins
    public static <E extends DiscordGuild<E>> DiscordGuild<E> join(@Nonnull final Guild guild,
                                                                   @Nonnull final Class<E> clazz)
            throws DatabaseException {
        return join(getDefaultSauce(), guild, clazz);
    }

    public static <E extends DiscordGuild<E>> DiscordGuild<E> join(@Nonnull final DatabaseWrapper dbWrapper,
                                                                   @Nonnull final Guild guild,
                                                                   @Nonnull final Class<E> clazz)
            throws DatabaseException {
        return dbWrapper.findApplyAndMerge(guild.getIdLong(), clazz, DiscordGuild::join);
    }

    //leaves
    public static <E extends DiscordGuild<E>> DiscordGuild<E> leave(@Nonnull final Guild guild,
                                                                    @Nonnull final Class<E> clazz)
            throws DatabaseException {
        return leave(getDefaultSauce(), guild, clazz);
    }

    public static <E extends DiscordGuild<E>> DiscordGuild<E> leave(@Nonnull final DatabaseWrapper dbWrapper,
                                                                    @Nonnull final Guild guild,
                                                                    @Nonnull final Class<E> clazz)
            throws DatabaseException {
        return dbWrapper.findApplyAndMerge(guild.getIdLong(), clazz, DiscordGuild::leave);
    }

    //caching
    public static <E extends DiscordGuild<E>> DiscordGuild<E> cache(@Nonnull final Guild guild, @Nonnull final Class<E> clazz)
            throws DatabaseException {
        return cache(getDefaultSauce(), guild, clazz);
    }

    public static <E extends DiscordGuild<E>> DiscordGuild<E> cache(@Nonnull final DatabaseWrapper dbWrapper,
                                                                    @Nonnull final Guild guild,
                                                                    @Nonnull final Class<E> clazz)
            throws DatabaseException {
        return dbWrapper.findApplyAndMerge(guild.getIdLong(), clazz, (discordGuild) -> discordGuild.set(guild));
    }

    //setters for cached values

    @Nonnull
    @CheckReturnValue
    public Self join() {
        this.joined = System.currentTimeMillis();
        this.present = true;
        return getThis();
    }

    @Nonnull
    @CheckReturnValue
    public Self leave() {
        this.left = System.currentTimeMillis();
        this.present = false;
        return getThis();
    }


    //getters for cached values
    public long getJoined() {
        return this.joined;
    }

    public long getLeft() {
        return this.left;
    }

    public boolean isPresent() {
        return this.present;
    }

    @Nonnull
    public String getName() {
        return this.name;
    }

    public long getOwnerId() {
        return this.ownerId;
    }

    @Nullable
    public String getIconId() {
        return this.iconId;
    }

    @Nullable
    public String getSplashId() {
        return this.splashId;
    }

    @Nonnull
    public String getRegion() {
        return this.region;
    }

    @Nullable
    public Long getAfkChannelId() {
        return this.afkChannelId;
    }

    @Nullable
    public Long getSystemChannelId() {
        return this.systemChannelId;
    }

    public int getVerificationLevel() {
        return this.verificationLevel;
    }

    public int getNotificationLevel() {
        return this.notificationLevel;
    }

    public int getMfaLevel() {
        return this.mfaLevel;
    }

    public int getExplicitContentLevel() {
        return this.explicitContentLevel;
    }

    public int getAfkTimeoutSeconds() {
        return this.afkTimeoutSeconds;
    }

    //convenience getters:
    @Nullable
    public String getAvatarUrl() {//ty JDA
        return this.iconId == null ? null : "https://cdn.discordapp.com/icons/" + this.guildId + "/" + this.iconId + ".jpg";
    }

    @Nullable
    public String getSplashUrl() {//ty JDA
        return this.splashId == null ? null : "https://cdn.discordapp.com/splashes/" + this.guildId + "/" + this.splashId + ".jpg";
    }
}
