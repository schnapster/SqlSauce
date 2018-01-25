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

package space.npstr.sqlsauce.entities.discord;

import org.hibernate.annotations.NaturalId;
import space.npstr.sqlsauce.entities.SaucedEntity;

import javax.annotation.CheckReturnValue;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import java.util.Objects;

/**
 * Created by napster on 25.01.18.
 * <p>
 * Defines just the id for a discord user entity and nothing else. Great as a base class for an entity saving more
 * information about a guild, without the caching cruft of {@link DiscordUser}
 */
@MappedSuperclass
public abstract class BaseDiscordUser<Self extends SaucedEntity<Long, Self>> extends SaucedEntity<Long, Self> {


    @Id
    @NaturalId
    @Column(name = "user_id", nullable = false)
    protected long userId;

    @Override
    @CheckReturnValue
    public Self setId(final Long userId) {
        this.userId = userId;
        return getThis();
    }

    @Override
    public Long getId() {
        return this.userId;
    }

    @Override
    public boolean equals(final Object obj) {
        return (obj instanceof BaseDiscordUser) && ((BaseDiscordUser) obj).userId == this.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.userId);
    }
}
