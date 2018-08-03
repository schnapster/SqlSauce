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

import net.dv8tion.jda.core.Region;
import net.dv8tion.jda.core.entities.Guild;
import org.hibernate.annotations.ColumnDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.sqlsauce.jda.listeners.CacheableGuild;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

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
public abstract class DiscordGuild<S extends BaseDiscordGuild<S>> extends BaseDiscordGuild<S> implements CacheableGuild<S> {

    @Transient
    private static final Logger log = LoggerFactory.getLogger(DiscordGuild.class);

    @Transient
    public static final String UNKNOWN_NAME = "Unknown Guild";

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

    @Nullable
    @Column(nullable = true, name = "icon_id", columnDefinition = "text")
    protected String iconId;

    @Nullable
    @Column(nullable = true, name = "splash_id", columnDefinition = "text")
    protected String splashId;

    @Column(nullable = false, name = "region", columnDefinition = "text")
    @ColumnDefault(value = "''") //key of the unknown region is an emptry string
    protected String region = Region.UNKNOWN.getKey();      //Region enum key

    @Nullable
    @Column(nullable = true, name = "afk_channel_id")
    protected Long afkChannelId;               //VoiceChannel id

    @Nullable
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

    /**
     * @throws NullPointerException
     *         In rare cases when Discord sent us bad data. This has happened more than once.
     */
    @Override
    public S set(@Nullable final Guild guild) {
        if (guild == null) {
            return getThis();//gracefully ignore null guilds
        }
        this.name = guild.getName();
        try { //yeah this actually happened, despite JDA and Discord docs saying that guilds always have an owner
            this.ownerId = guild.getOwner().getUser().getIdLong();
        } catch (NullPointerException e) {
            log.error("Guild {} seems to have a null owner", guild.getIdLong(), e);
            throw e;
        }
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

    //setters for cached values

    @Override
    @CheckReturnValue
    public S join() {
        this.joined = System.currentTimeMillis();
        this.present = true;
        return getThis();
    }

    @Override
    @CheckReturnValue
    public S leave() {
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

    @Override
    public boolean isPresent() {
        return this.present;
    }

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
