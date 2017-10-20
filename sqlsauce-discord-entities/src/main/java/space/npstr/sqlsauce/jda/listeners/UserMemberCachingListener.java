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

package space.npstr.sqlsauce.jda.listeners;

import net.dv8tion.jda.core.events.guild.member.GenericGuildMemberEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberNickChangeEvent;
import net.dv8tion.jda.core.events.user.UserAvatarUpdateEvent;
import net.dv8tion.jda.core.events.user.UserNameUpdateEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.sqlsauce.DatabaseException;
import space.npstr.sqlsauce.entities.discord.DiscordUser;

/**
 * Created by napster on 20.10.17.
 * <p>
 * Caches entities that extend DiscordUser
 */
public class UserMemberCachingListener<E extends DiscordUser<E>> extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(GuildCachingListener.class);

    private final Class<E> entityClass;

    public UserMemberCachingListener(final Class<E> entityClass) {
        this.entityClass = entityClass;
    }


    @Override
    public void onUserNameUpdate(final UserNameUpdateEvent event) {
        try {
            DiscordUser.cache(event.getUser(), this.entityClass);
        } catch (final DatabaseException e) {
            log.error("Failed to cache event {} for user {}",
                    event.getClass().getSimpleName(), event.getUser().getIdLong(), e);
        }
    }

    @Override
    public void onUserAvatarUpdate(final UserAvatarUpdateEvent event) {
        try {
            DiscordUser.cache(event.getUser(), this.entityClass);
        } catch (final DatabaseException e) {
            log.error("Failed to cache event {} for user {}",
                    event.getClass().getSimpleName(), event.getUser().getIdLong(), e);
        }
    }

    @Override
    public void onGuildMemberNickChange(final GuildMemberNickChangeEvent event) {
        try {
            DiscordUser.cache(event.getMember(), this.entityClass);
        } catch (final DatabaseException e) {
            log.error("Failed to cache event {} for user {}",
                    event.getClass().getSimpleName(), event.getUser().getIdLong(), e);
        }
    }

    @Override
    public void onGenericGuildMember(final GenericGuildMemberEvent event) {
        try {
            DiscordUser.cache(event.getMember(), this.entityClass);
        } catch (final DatabaseException e) {
            log.error("Failed to cache event {} for user {}",
                    event.getClass().getSimpleName(), event.getUser().getIdLong(), e);
        }
    }
}
