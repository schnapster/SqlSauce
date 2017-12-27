package space.npstr.sqlsauce.entities;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;

import javax.annotation.Nonnull;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Created by napster on 25.12.17.
 * <p>
 * Composite primary key for Guild x User, aka Member
 * Useful for things that we want to persist for a guild depending on the user and vice-versa, for example
 * pledges/unlockings which could be done by different users for the same guild, or any settings by a user that are
 * to be treated guild specific
 */
@Embeddable
public class MemberComposite implements Serializable {

    private static final long serialVersionUID = 8463735462980082043L;

    @Column(name = "guild_id", nullable = false)
    private long guildId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    //for jpa & the database wrapper
    public MemberComposite() {
    }

    public MemberComposite(@Nonnull Member member) {
        this(member.getGuild(), member.getUser());
    }

    public MemberComposite(@Nonnull Guild guild, @Nonnull User user) {
        this(guild.getIdLong(), user.getIdLong());
    }

    public MemberComposite(long guildId, long userId) {
        this.guildId = guildId;
        this.userId = userId;
    }

    public long getGuildId() {
        return guildId;
    }

    public void setGuildId(long guildId) {
        this.guildId = guildId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(guildId, userId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberComposite)) return false;
        MemberComposite other = (MemberComposite) o;
        return this.guildId == other.guildId && this.userId == other.userId;
    }
}