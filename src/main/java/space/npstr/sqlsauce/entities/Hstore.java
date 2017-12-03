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
import space.npstr.sqlsauce.converters.PostgresHStoreConverter;
import space.npstr.sqlsauce.fp.types.EntityKey;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by napster on 06.07.17.
 * <p>
 * Basic HStore table
 * <p>
 * JPA-only dependant = not Hibernate or other vendors dependant
 * <p>
 * The x makes it sound awesome and also prevents a name/type collision in postgres
 * <p>
 * todo: ideas for this class: explore possibilities of merging the giant blocks of methods in this class for
 * default / provided database by partially applying the data source
 */
@Entity
@Table(name = "hstorex")
public class Hstore extends SaucedEntity<String, Hstore> {

    @Transient
    public static final String DEFAULT_HSTORE_NAME = "default";

    //you are responsible for using unique names when you want to access unique hstores
    @Id
    @Column(name = "name", columnDefinition = "text")
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
    public String getId() {
        return this.name;
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
    public static String loadAndGet(@Nonnull final HstoreKey entityKey, @Nonnull final String key,
                                    @Nonnull final String defaultValue) throws DatabaseException {
        return Hstore.loadAndGet(getDefaultSauce(), entityKey, key, defaultValue);
    }

    /**
     * @return loads a value from the default hstore
     */
    @Nonnull
    @CheckReturnValue
    public static String loadAndGet(@Nonnull final String key, @Nonnull final String defaultValue)
            throws DatabaseException {
        return Hstore.loadAndGet(getDefaultSauce(), HstoreKey.DEFAULT, key, defaultValue);
    }

    /**
     * @return the requested Hstore object
     * @deprecated since 0.0.3 use SaucedEntity#load(EntityKey) instead
     */
    @Nonnull
    @CheckReturnValue
    @Deprecated
    public static Hstore load(@Nonnull final HstoreKey entityKey) throws DatabaseException {
        return Hstore.load(getDefaultSauce(), entityKey);
    }

    /**
     * @return the default Hstore object
     */
    @Nonnull
    @CheckReturnValue
    public static Hstore load() throws DatabaseException {
        return Hstore.load(getDefaultSauce(), HstoreKey.DEFAULT);
    }

    /**
     * Load an Hstore object
     *
     * @return the object for chaining calls; dont forget to merge() the changes
     * <p>
     * @deprecated since 0.0.3 This method provides no real value over Hstore.load().set() and instead invites to forget to call save()
     * This gets especially visible comparing Hstore.loadAndSet().save() vs the much more sensible Hstore.load().setAndSave()
     * Use Hstore.loadSetAndSave() instead if really only one value needs to be set, or Hstore.loadApplyAndSave() to do
     * bulk operations
     */
    @Nonnull
    @CheckReturnValue
    @Deprecated
    public static Hstore loadAndSet(@Nonnull final HstoreKey entityKey, @Nonnull final String key, @Nonnull final String value)
            throws DatabaseException {
        return Hstore.loadAndSet(getDefaultSauce(), entityKey, key, value);
    }

    /**
     * Uses the default hstore
     *
     * @return the object for chaining calls; dont forget to save() the changes
     * <p>
     * @deprecated since 0.0.3 See method above.
     */
    @Nonnull
    @CheckReturnValue
    @Deprecated
    public static Hstore loadAndSet(@Nonnull final String key, @Nonnull final String value) throws DatabaseException {
        return Hstore.loadAndSet(getDefaultSauce(), HstoreKey.DEFAULT, key, value);
    }

    /**
     * Shortcut method to set a single value on a named hstore on the default database and save it
     */
    @Nonnull
    public static Hstore loadSetAndSave(@Nonnull final HstoreKey entityKey, @Nonnull final String key,
                                        @Nonnull final String value) throws DatabaseException {
        return loadSetAndSave(getDefaultSauce(), entityKey, key, value);
    }

    /**
     * Shortcut method to set a single value on the default hstore on the default database and save it
     */
    @Nonnull
    public static Hstore loadSetAndSave(@Nonnull final String key, @Nonnull final String value)
            throws DatabaseException {
        return loadSetAndSave(HstoreKey.DEFAULT, key, value);
    }

    /**
     * Apply some functions to the default Hstore of the default database and save it
     */
    @Nonnull
    public static Hstore loadApplyAndSave(@Nonnull final HstoreKey entityKey,
                                          @Nonnull final Function<Hstore, Hstore> transformation)
            throws DatabaseException {
        return loadApplyAndSave(getDefaultSauce(), entityKey, transformation);
    }

    /**
     * Apply some functions to the default Hstore of the default database and save it
     */
    @Nonnull
    public static Hstore loadApplyAndSave(@Nonnull final Function<Hstore, Hstore> transformation)
            throws DatabaseException {
        return loadApplyAndSave(HstoreKey.DEFAULT, transformation);
    }

    //################################################################################
    //                  Static convenience stuff with custom sauce
    //################################################################################

    /**
     * @return load a value from an hstore object
     */
    @Nonnull
    @CheckReturnValue
    public static String loadAndGet(@Nonnull final DatabaseWrapper databaseWrapper, @Nonnull final HstoreKey entityKey,
                                    @Nonnull final String key, @Nonnull final String defaultValue)
            throws DatabaseException {
        return databaseWrapper.getOrCreate(entityKey).hstore
                .getOrDefault(key, defaultValue);
    }

    /**
     * @return loads a value from the default hstore
     */
    @Nonnull
    @CheckReturnValue
    public static String loadAndGet(@Nonnull final DatabaseWrapper databaseWrapper, @Nonnull final String key,
                                    @Nonnull final String defaultValue) throws DatabaseException {
        return loadAndGet(databaseWrapper, HstoreKey.DEFAULT, key, defaultValue);
    }

    /**
     * @return the requested Hstore object
     * @deprecated since 0.0.3 use SaucedEntity#load(HstoreKey.DEFAULT) instead
     */
    @Nonnull
    @CheckReturnValue
    @Deprecated
    public static Hstore load(@Nonnull final DatabaseWrapper databaseWrapper, @Nonnull final HstoreKey entityKey)
            throws DatabaseException {
        return databaseWrapper.getOrCreate(entityKey);
    }

    /**
     * @return the default Hstore object
     * @deprecated since 0.0.3 use SaucedEntity#load(HstoreKey.DEFAULT) instead
     */
    @Nonnull
    @CheckReturnValue
    @Deprecated
    public static Hstore load(@Nonnull final DatabaseWrapper databaseWrapper) throws DatabaseException {
        return load(databaseWrapper, HstoreKey.DEFAULT);
    }

    /**
     * Load an Hstore object
     *
     * @return the object for chaining calls; dont forget to save() the changes
     * <p>
     * @deprecated since 0.0.3 See equally named method single connection convenience method above.
     */
    @Nonnull
    @CheckReturnValue
    @Deprecated
    public static Hstore loadAndSet(@Nonnull final DatabaseWrapper databaseWrapper, @Nonnull final HstoreKey entityKey,
                                    @Nonnull final String key, @Nonnull final String value) throws DatabaseException {
        return load(databaseWrapper, entityKey)
                .set(key, value);
    }

    /**
     * Uses the default hstore
     *
     * @return the object for chaining calls; dont forget to save() the changes
     * <p>
     * @deprecated since 0.0.3 See equally named method single connection convenience method above.
     */
    @Nonnull
    @CheckReturnValue
    @Deprecated
    public static Hstore loadAndSet(@Nonnull final DatabaseWrapper databaseWrapper, @Nonnull final String key,
                                    @Nonnull final String value) throws DatabaseException {
        return loadAndSet(databaseWrapper, HstoreKey.DEFAULT, key, value);
    }

    /**
     * Shortcut method to set a single value on a named hstore on the provided database and save it
     */
    @Nonnull
    public static Hstore loadSetAndSave(@Nonnull final DatabaseWrapper databaseWrapper,
                                        @Nonnull final HstoreKey entityKey,
                                        @Nonnull final String key, @Nonnull final String value)
            throws DatabaseException {
        return loadApplyAndSave(databaseWrapper, entityKey, setTransformation(key, value));
    }

    /**
     * Shortcut method to set a single value on the default hstore on the provided database and save it
     */
    @Nonnull
    public static Hstore loadSetAndSave(@Nonnull final DatabaseWrapper databaseWrapper, @Nonnull final String key,
                                        @Nonnull final String value) throws DatabaseException {
        return loadApplyAndSave(databaseWrapper, setTransformation(key, value));
    }


    /**
     * Apply some functions to a named Hstore of the provided database and save it
     */
    @Nonnull
    public static Hstore loadApplyAndSave(@Nonnull final DatabaseWrapper databaseWrapper,
                                          @Nonnull final HstoreKey entityKey,
                                          @Nonnull final Function<Hstore, Hstore> transformation)
            throws DatabaseException {
        return databaseWrapper.findApplyAndMerge(entityKey, transformation);
    }

    /**
     * Apply some functions to the default Hstore of the provided database and save it
     */
    @Nonnull
    public static Hstore loadApplyAndSave(@Nonnull final DatabaseWrapper databaseWrapper,
                                          @Nonnull final Function<Hstore, Hstore> transformation)
            throws DatabaseException {
        return loadApplyAndSave(databaseWrapper, HstoreKey.DEFAULT, transformation);
    }


    //################################################################################
    //                              Transformations
    //################################################################################
    //these can be used with the loadApplyAndSave methods

    /**
     * Function that set a key to a value on the applied hstore
     */
    public static Function<Hstore, Hstore> setTransformation(final String key, final String value) {
        return (hstore) -> hstore.set(key, value);
    }


    public static class HstoreKey extends EntityKey<String, Hstore> {

        public static final HstoreKey DEFAULT = HstoreKey.of(DEFAULT_HSTORE_NAME);

        @Nonnull
        public static HstoreKey of(@Nonnull final String name) {
            return new HstoreKey(name);
        }

        private HstoreKey(@Nonnull final String name) {
            super(name, Hstore.class);
        }
    }
}
