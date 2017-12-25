package space.npstr.sqlsauce.entities;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Created by napster on 25.12.17.
 * <p>
 * Composite primary key for Guild x User
 * Useful for things that we want to persist for a guild depending on the user and vice-versa, for example
 * pledges/unlockings which could be done by different users for the same guild
 */
@Embeddable
public class GuildUserComposite implements Serializable {

    private static final long serialVersionUID = 8463735462980082043L;

    @Column(name = "guild_id", nullable = false)
    private long guildId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    //for jpa & the database wrapper
    public GuildUserComposite() {
    }

    public GuildUserComposite(long guildId, long userId) {
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
        if (!(o instanceof GuildUserComposite)) return false;
        GuildUserComposite other = (GuildUserComposite) o;
        return this.guildId == other.guildId && this.userId == other.userId;
    }
}