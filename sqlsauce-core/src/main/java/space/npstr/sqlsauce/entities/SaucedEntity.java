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

import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.fp.types.EntityKey;

import javax.annotation.CheckReturnValue;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by napster on 10.10.17.
 * <p>
 * Sauced entities have a locking mechanism that is used by the {@link DatabaseWrapper} to ensure safe merges.
 */
@MappedSuperclass
public abstract class SaucedEntity<I extends Serializable, S extends SaucedEntity<I, S>> implements IEntity<I, S> {

    @SuppressWarnings("unchecked")
    protected S getThis() {
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<S> getClazz() {
        return (Class<S>) this.getClass();
    }


    //################################################################################
    //                                  Locking
    //################################################################################

    @Transient
    private static final transient Map<Class, Object[]> ENTITY_LOCKS = new ConcurrentHashMap<>();
    // How many partitions the hashed entity locks shall have
    // The chosen, uncustomizable, value is considered good enough:tm: for the current implementation where locks are
    // bound to classes (amount of hanging around lock objects is equal to implemented SaucedEntities * concurrencyLevel).
    // Prime number to reduce possible collisions due to bad hashes.
    // TODO implement customizable amount
    @Transient
    private static final transient int CONCURRENCY_LEVEL = 17;


    /**
     * Abusing Hibernate with a lot of load/create entity -> detach -> save entity can lead to concurrent inserts
     * if an entity is created two times and then merged simultaneously. Use one of the lock below for any writing
     * operations, including lookup operations that will lead to writes (for example SaucedEntity#save()).
     */
    @CheckReturnValue
    public Object getEntityLock() {
        return getEntityLock(EntityKey.of(this));
    }


    @CheckReturnValue
    public static <E extends SaucedEntity<I, E>, I extends Serializable> Object getEntityLock(final SaucedEntity<I, E> entity) {
        return getEntityLock(EntityKey.of(entity));
    }

    /**
     * @return A hashed lock. Uses the Object#hashCode method of the provided id to determine the hash.
     */
    @CheckReturnValue
    public static Object getEntityLock(final EntityKey id) {
        Object[] hashedClasslocks = ENTITY_LOCKS.computeIfAbsent(id.clazz, k -> createObjectArray(CONCURRENCY_LEVEL));
        return hashedClasslocks[Math.floorMod(Objects.hash(id), hashedClasslocks.length)];
    }

    //################################################################################
    //                                  Internals
    //################################################################################

    @CheckReturnValue
    private static Object[] createObjectArray(final int size) {
        final Object[] result = new Object[size];
        for (int i = 0; i < size; i++) {
            result[i] = new Object();
        }
        return result;
    }

}
