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

package space.npstr.sqlstack.entities;

import space.npstr.sqlstack.DatabaseException;
import space.npstr.sqlstack.DatabaseWrapper;
import space.npstr.sqlstack.converters.PostgresHStoreConverter;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by napster on 06.07.17.
 * <p>
 * Basic HStore table
 * <p>
 * JPA-only dependant = not Hibernate or other vendors dependant
 * <p>
 * The x makes it sound awesome and also prevents a name/type collision in postgres
 */
@Entity
@Table(name = "hstorex")
public class Hstore extends SaucedEntity<String, Hstore> {

    public static final String DEFAULT_HSTORE_NAME = "default";
    public static Object hstoreLock = new Object();

    //you are responsible for using unique names when you want to access unique hstores
    @Id
    @Column(name = "name")
    public String name;

    @Column(name = "hstorex", columnDefinition = "hstore")
    @Convert(converter = PostgresHStoreConverter.class)
    public final Map<String, String> hstore = new HashMap<>();

    //for jpa && sauced entity
    //prefer to use Hstore.load() to create on of these to avoid overwriting an existing one
    public Hstore() {
    }

    @Nonnull
    @Override
    @CheckReturnValue
    public Hstore setId(@Nonnull final String id) {
        this.name = id;
        return this;
    }

    @Nonnull
    @Override
    @CheckReturnValue
    public String getId() {
        return this.name;
    }

    @Override
    protected Object getEntityLock() {
        return hstoreLock;
    }

    /**
     * @return itself for chaining calls
     */
    @Nonnull
    @CheckReturnValue
    public Hstore set(@Nonnull final String key, @Nonnull final String value) {
        this.hstore.put(key, value);
        return this;
    }

    /**
     * Intended as a finishing move, so no @CheckReturnValue annotation. Manually check the return value if you want
     * to keep using this Hstore
     */
    public Hstore setAndSave(@Nonnull final String key, @Nonnull final String value) throws DatabaseException {
        this.hstore.put(key, value);
        return this.save();
    }

    /**
     * @return the requested value
     */
    @Nonnull
    @CheckReturnValue
    public String get(@Nonnull final String key, @Nonnull final String defaultValue) {
        return this.hstore.getOrDefault(key, defaultValue);
    }

    /**
     * @return the requested value or null if it doesnt exist
     */
    @Nullable
    @CheckReturnValue
    public String get(@Nonnull final String key) {
        return this.hstore.getOrDefault(key, null);
    }


    //################################################################################
    //                 Static single connection convenience stuff
    //################################################################################

    /**
     * @return load a value from an hstore object
     */
    @Nonnull
    @CheckReturnValue
    public static String loadAndGet(@Nonnull final String name, @Nonnull final String key,
                                    @Nonnull final String defaultValue) throws DatabaseException {
        return Hstore.loadAndGet(getDefaultSauce(), name, key, defaultValue);
    }

    /**
     * @return loads a value from the default hstore
     */
    @Nonnull
    @CheckReturnValue
    public static String loadAndGet(@Nonnull final String key, @Nonnull final String defaultValue)
            throws DatabaseException {
        return Hstore.loadAndGet(getDefaultSauce(), DEFAULT_HSTORE_NAME, key, defaultValue);
    }

    /**
     * @return the requested Hstore object
     */
    @Nonnull
    @CheckReturnValue
    public static Hstore load(@Nonnull final String name) throws DatabaseException {
        return Hstore.load(getDefaultSauce(), name);
    }

    /**
     * @return the default Hstore object
     */
    @Nonnull
    @CheckReturnValue
    public static Hstore load() throws DatabaseException {
        return Hstore.load(getDefaultSauce(), DEFAULT_HSTORE_NAME);
    }

    /**
     * Load an Hstore object
     *
     * @return the object for chaining calls; dont forget to merge() the changes
     */
    @Nonnull
    @CheckReturnValue
    public static Hstore loadAndSet(@Nonnull final String name, @Nonnull final String key, @Nonnull final String value)
            throws DatabaseException {
        return Hstore.loadAndSet(getDefaultSauce(), name, key, value);
    }

    /**
     * Uses the default hstore
     *
     * @return the object for chaining calls; dont forget to save() the changes
     */
    @Nonnull
    @CheckReturnValue
    public static Hstore loadAndSet(@Nonnull final String key, @Nonnull final String value) throws DatabaseException {
        return Hstore.loadAndSet(getDefaultSauce(), DEFAULT_HSTORE_NAME, key, value);
    }

    //################################################################################
    //                  Static convenience stuff with custom sauce
    //################################################################################

    /**
     * @return load a value from an hstore object
     */
    @Nonnull
    @CheckReturnValue
    public static String loadAndGet(@Nonnull final DatabaseWrapper databaseWrapper, @Nonnull final String name,
                                    @Nonnull final String key, @Nonnull final String defaultValue)
            throws DatabaseException {
        return databaseWrapper.getOrCreate(name, Hstore.class).hstore.getOrDefault(key, defaultValue);
    }

    /**
     * @return loads a value from the default hstore
     */
    @Nonnull
    @CheckReturnValue
    public static String loadAndGet(@Nonnull final DatabaseWrapper databaseWrapper, @Nonnull final String key,
                                    @Nonnull final String defaultValue) throws DatabaseException {
        return loadAndGet(databaseWrapper, DEFAULT_HSTORE_NAME, key, defaultValue);
    }

    /**
     * @return the requested Hstore object
     */
    @Nonnull
    @CheckReturnValue
    public static Hstore load(@Nonnull final DatabaseWrapper databaseWrapper, @Nonnull final String name)
            throws DatabaseException {
        return databaseWrapper.getOrCreate(name, Hstore.class);
    }

    /**
     * @return the default Hstore object
     */
    @Nonnull
    @CheckReturnValue
    public static Hstore load(@Nonnull final DatabaseWrapper databaseWrapper) throws DatabaseException {
        return load(databaseWrapper, DEFAULT_HSTORE_NAME);
    }

    /**
     * Load an Hstore object
     *
     * @return the object for chaining calls; dont forget to merge() the changes
     */
    @Nonnull
    @CheckReturnValue
    public static Hstore loadAndSet(@Nonnull final DatabaseWrapper databaseWrapper, @Nonnull final String name,
                                    @Nonnull final String key, @Nonnull final String value) throws DatabaseException {
        return load(databaseWrapper, name).set(key, value);
    }

    /**
     * Uses the default hstore
     *
     * @return the object for chaining calls; dont forget to save() the changes
     */
    @Nonnull
    @CheckReturnValue
    public static Hstore loadAndSet(@Nonnull final DatabaseWrapper databaseWrapper, @Nonnull final String key,
                                    @Nonnull final String value) throws DatabaseException {
        return loadAndSet(databaseWrapper, DEFAULT_HSTORE_NAME, key, value);
    }
}
