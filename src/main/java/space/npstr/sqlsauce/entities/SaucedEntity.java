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

import space.npstr.sqlsauce.DatabaseException;
import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.EntityKey;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Created by napster on 10.10.17.
 * <p>
 * Sauced entities may have the database source where they come from / should be written to attached which is
 * convenient af. In case you are wondering, sauce is internet slang for source.
 */
@MappedSuperclass
public abstract class SaucedEntity<I extends Serializable, Self extends SaucedEntity<I, Self>> implements IEntity<I, Self> {

    //for when you only use a single connection application-wide, this might provide some handy static methods after
    // setting it
    // see Hstore for an example implementation.
    // Creating a database connection will automatically set it as the default sauce.
    @Transient
    private static DatabaseWrapper defaultSauce;

    public static void setDefaultSauce(@Nonnull final DatabaseWrapper dbWrapper) {
        SaucedEntity.defaultSauce = dbWrapper;
    }

    @Nonnull
    public static DatabaseWrapper getDefaultSauce() {
        if (defaultSauce == null) {
            throw new IllegalStateException("Default DatabaseWrapper not set. Make sure to create at least one " +
                    "database connection.");
        }
        return defaultSauce;
    }


    //the sauce of this entity
    @Transient
    protected DatabaseWrapper dbWrapper;


    @SuppressWarnings("unchecked")
    @Nonnull
    protected Self getThis() {
        return (Self) this;
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public Class<Self> getClazz() {
        return (Class<Self>) this.getClass();
    }

    //when loading / creating with the DatabaseWrapper class, it will make sure to set this so that the convenience
    //methods may be used
    @Nonnull
    public Self setSauce(@Nonnull final DatabaseWrapper dbWrapper) {
        this.dbWrapper = dbWrapper;
        return getThis();
    }


    //################################################################################
    //                              Convenience stuff
    //################################################################################

    /**
     * Merge an entity into the database it came from. Call this after setting any values on a detached entity.
     *
     * @return the updated entity
     */
    @Nonnull
    @CheckReturnValue
    public Self save() throws DatabaseException {
        synchronized (getEntityLock()) {
            checkWrapper();
            return this.dbWrapper.merge(getThis());
        }
    }

    /**
     * @param dbWrapper The database to load from
     * @param entityKey Key of the entity to load
     * @param <I>       Id I of the SaucedEntity to load
     * @param <E>       Concrete implementation class E of the SaucedEntity to load
     * @return The requested entity from the provided database, or a new instance of the requested class if no such
     * entity is found
     */
    @Nonnull
    @CheckReturnValue
    public static <E extends SaucedEntity<I, E>, I extends Serializable> E load(@Nonnull final DatabaseWrapper dbWrapper,
                                                                                @Nonnull final EntityKey<I, E> entityKey)
            throws DatabaseException {
        return dbWrapper.getOrCreate(entityKey);
    }

    /**
     * @param entityKey Key of the entity to load
     * @param <I>       Id I of the SaucedEntity to load
     * @param <E>       Concrete implementation class E of the SaucedEntity to load
     * @return The requested entity from the default database, or a new instance of the requested class if no such
     * entity is found
     */
    @Nonnull
    @CheckReturnValue
    public static <E extends SaucedEntity<I, E>, I extends Serializable> E load(@Nonnull final EntityKey<I, E> entityKey)
            throws DatabaseException {
        return load(getDefaultSauce(), entityKey);
    }

    /**
     * @param dbWrapper The database to look up from
     * @param entityKey Key of the entity to load
     * @param <I>       Id I of the SaucedEntity to look up
     * @param <E>       Concrete implementation class E of the SaucedEntity to look up
     * @return The requested entity from the provided database, or null if no such entity is found
     */
    @Nullable
    @CheckReturnValue
    public static <E extends SaucedEntity<I, E>, I extends Serializable> E lookUp(@Nonnull final DatabaseWrapper dbWrapper,
                                                                                  @Nonnull final EntityKey<I, E> entityKey)
            throws DatabaseException {
        return dbWrapper.getEntity(entityKey);
    }

    /**
     * @param entityKey Key of the entity to load
     * @param <I>       Id I of the SaucedEntity to look up
     * @param <E>       Concrete implementation class E of the SaucedEntity to look up
     * @return The requested entity from the default database, or null if no such entity is found
     */
    @Nullable
    @CheckReturnValue
    public static <E extends SaucedEntity<I, E>, I extends Serializable> E lookUp(@Nonnull final EntityKey<I, E> entityKey)
            throws DatabaseException {
        return lookUp(getDefaultSauce(), entityKey);
    }

    //################################################################################
    //                                  Locking
    //################################################################################

    private static final Map<Class, Object[]> entityLocks = new HashMap<>();
    // How many partitions the hashed entity locks shall have
    // The chosen, uncustomizable, value is considered good enough:tm: for the current implementation where locks are
    // bound to classes (amount of hanging around locks is equal to implemented SaucedEntities * concurrencyLevel).
    // Prime number to reduce possible collisions due to bad hashes.
    // TODO implement customizable amount
    private static final int concurrencyLevel = 17;


    /**
     * Abusing Hibernate with a lot of load/create entity -> detach -> save entity can lead to concurrent inserts
     * if an entity is created two times and then merged simultaneously. Use one of the lock below for any writing
     * operations, including lookup operations that will lead to writes (for example SaucedEntity#save()).
     */
    @Nonnull
    @CheckReturnValue
    public Object getEntityLock() {
        return getEntityLock(EntityKey.of(this));
    }


    @Nonnull
    @CheckReturnValue
    public static <E extends SaucedEntity<I, E>, I extends Serializable> Object getEntityLock(@Nonnull final SaucedEntity<I, E> entity) {
        return getEntityLock(EntityKey.of(entity));
    }

    /**
     * @return A hashed lock. Uses the Object#hashCode method of the provided id to determine the hash.
     */
    @Nonnull
    @CheckReturnValue
    public static Object getEntityLock(@Nonnull final EntityKey id) {
        //double lock synchronizing to create new collections of hashed locks, wew
        Object[] hashedClasslocks = entityLocks.get(id.clazz);
        if (hashedClasslocks == null) {
            synchronized (entityLocks) {
                hashedClasslocks = entityLocks.computeIfAbsent(id.clazz, k -> createObjectArray(concurrencyLevel));
            }
        }
        return hashedClasslocks[Math.floorMod(Objects.hash(id), hashedClasslocks.length)];
    }

    //################################################################################
    //                                  Internals
    //################################################################################

    private void checkWrapper() {
        if (this.dbWrapper == null) {
            throw new IllegalStateException("DatabaseWrapper not set. Make sure to load entity through a " +
                    "DatabaseWrapper or manually set it by calling SaucedEntity#setSauce");
        }
    }

    @Nonnull
    @CheckReturnValue
    private static Object[] createObjectArray(final int size) {
        final Object[] result = new Object[size];
        for (int i = 0; i < size; i++) {
            result[i] = new Object();
        }
        return result;
    }

}
