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

import space.npstr.sqlstack.DatabaseWrapper;
import space.npstr.sqlstack.converters.PostgresHStoreConverter;

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
public class Hstore implements IEntity<String> {

    public static final String DEFAULT_HSTORE_NAME = "default";

    //you are responsible for using unique names when you want to access unique hstores
    @Id
    @Column(name = "name")
    public String name;

    @Column(name = "hstorex", columnDefinition = "hstore")
    @Convert(converter = PostgresHStoreConverter.class)
    public final Map<String, String> hstore = new HashMap<>();

    //for jpa
    Hstore() {

    }

    @Override
    public void setId(final String id) {
        this.name = id;
    }

    @Override
    public String getId() {
        return this.name;
    }


    public Hstore(final String name) {
        this.name = name;
    }

    /**
     * Convenience method to commit several changes at once
     * Use after calling set()
     * Example:
     * Hstore.loadAndSet("a", "a").set("b", "b").set("c", "c").save();
     *
     * @return the merged object
     */
    public Hstore save(final DatabaseWrapper databaseWrapper) {
        return databaseWrapper.merge(this);
    }

    /**
     * @return itself for chaining calls
     */
    public Hstore set(final String key, final String value) {
        this.hstore.put(key, value);
        return this;
    }

    /**
     * @return the requested value
     */
    public String get(final String key, final String defaultValue) {
        return this.hstore.getOrDefault(key, defaultValue);
    }

    /**
     * @return the requested value or null if it doesnt exist
     */
    public String get(final String key) {
        return this.hstore.getOrDefault(key, null);
    }


    //################################################################################
    //                      Stats convenience stuff below
    //################################################################################

    /**
     * @return load a value from an hstore object
     */
    public static String loadAndGet(final DatabaseWrapper databaseWrapper, final String name, final String key, final String defaultValue) {
        return databaseWrapper.getOrCreateHstore(name).hstore.getOrDefault(key, defaultValue);
    }

    /**
     * @return loads a value from the default hstore
     */
    public static String loadAndGet(final DatabaseWrapper databaseWrapper, final String key, final String defaultValue) {
        return loadAndGet(databaseWrapper, DEFAULT_HSTORE_NAME, key, defaultValue);
    }

    /**
     * @return the requested Hstore object
     */
    public static Hstore load(final DatabaseWrapper databaseWrapper, final String name) {
        return databaseWrapper.getOrCreateHstore(name);
    }

    /**
     * @return the default Hstore object
     */
    public static Hstore load(final DatabaseWrapper databaseWrapper) {
        return load(databaseWrapper, DEFAULT_HSTORE_NAME);
    }

    /**
     * Load an Hstore object
     *
     * @return the object for chaining calls; dont forget to merge() the changes
     */
    public static Hstore loadAndSet(final DatabaseWrapper databaseWrapper, final String name, final String key, final String value) {
        return load(databaseWrapper, name).set(key, value);
    }

    /**
     * Uses the default hstore
     *
     * @return the object for chaining calls; dont forget to merge() the changes
     */
    public static Hstore loadAndSet(final DatabaseWrapper databaseWrapper, final String key, final String value) {
        return loadAndSet(databaseWrapper, DEFAULT_HSTORE_NAME, key, value);
    }
}
