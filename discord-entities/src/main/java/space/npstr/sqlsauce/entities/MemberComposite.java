/*
 * MIT License
 *
 * Copyright (c) 2017 Dennis Neufeld
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

package space.npstr.sqlsauce.entities;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;

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
    MemberComposite() {
    }

    public MemberComposite(Member member) {
        this(member.getGuild(), member.getUser());
    }

    public MemberComposite(Guild guild, User user) {
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

    @Override
    public String toString() {
        return MemberComposite.class.getSimpleName() + String.format("(G %s, U %s)", guildId, userId);
    }
}