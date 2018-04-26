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

package space.npstr.sqlsauce.hibernate.types;

import org.hibernate.annotations.Type;
import org.junit.jupiter.api.Test;
import space.npstr.sqlsauce.BaseTest;
import space.npstr.sqlsauce.DatabaseConnection;
import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.entities.SaucedEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by napster on 03.04.18.
 */
public class HashSetBasicTypeTest extends BaseTest {

    private static final String DROP_TABLE_HASH_SET_STRINGS = String.format(DROP_TABLE_IF_EXISTS, "hash_set_basics");
    private static final String CREATE_TABLE_HASH_SET_STRINGS
            = "CREATE TABLE hash_set_basics "
            + "( "
            + "    id       BIGINT NOT NULL, "
            + "    ints     INTEGER[] NOT NULL, "
            + "    longs    BIGINT[] NOT NULL, "
            + "    strings  TEXT[] NOT NULL, "
            + "    CONSTRAINT hash_set_basics_pkey PRIMARY KEY (id) "
            + ");";


    @Test
    public void hashSetBasicType() {
        DatabaseConnection connection = requireConnection();
        DatabaseWrapper wrapper = new DatabaseWrapper(connection);

        wrapper.executeSqlQuery(DROP_TABLE_HASH_SET_STRINGS);
        wrapper.executeSqlQuery(CREATE_TABLE_HASH_SET_STRINGS);

        HashSet<Integer> ints = new HashSet<>(Arrays.asList(1, 1, 2, 3, 5, 8, 13, 21, 34));
        HashSet<Long> longs = new HashSet<>(Arrays.asList(Long.MAX_VALUE, Long.MIN_VALUE));
        HashSet<String> strings = new HashSet<>(Arrays.asList("foo", "bar"));

        HashSetBasic entity = new HashSetBasic(Long.MAX_VALUE - 5, ints, longs, strings);
        entity = wrapper.persist(entity);

        assertTrue(hashSetsHaveEqualContent(ints, entity.getInts()), "int sets different after persisting");
        assertTrue(hashSetsHaveEqualContent(longs, entity.getLongs()), "long sets different after persisting");
        assertTrue(hashSetsHaveEqualContent(strings, entity.getStrings()), "string sets different after persisting");


        entity.addInt(55);
        entity.removeInt(1);
        entity = wrapper.merge(entity);

        assertEquals(8, entity.getInts().size(), "wrong size");
        assertTrue(entity.getInts().contains(2), "missing a value");
        assertTrue(entity.getInts().contains(3), "missing a value");
        assertTrue(entity.getInts().contains(5), "missing a value");
        assertTrue(entity.getInts().contains(8), "missing a value");
        assertTrue(entity.getInts().contains(13), "missing a value");
        assertTrue(entity.getInts().contains(21), "missing a value");
        assertTrue(entity.getInts().contains(34), "missing a value");
        assertTrue(entity.getInts().contains(55), "missing a value");

        entity.setInts(new HashSet<>());

        entity = wrapper.merge(entity);
        assertTrue(entity.getInts().isEmpty());


        entity.addLong(Long.MAX_VALUE - 1);
        entity.removeLong(Long.MAX_VALUE);
        entity = wrapper.merge(entity);

        assertEquals(2, entity.getLongs().size(), "wrong size");
        assertTrue(entity.getLongs().contains(Long.MAX_VALUE - 1), "missing a value");
        assertTrue(entity.getLongs().contains(Long.MIN_VALUE), "missing a value");

        entity.setLongs(new HashSet<>());

        entity = wrapper.merge(entity);
        assertTrue(entity.getLongs().isEmpty());


        entity.addString("baz");
        entity.removeString("bar");
        entity = wrapper.merge(entity);

        assertEquals(2, entity.getStrings().size(), "wrong size");
        assertTrue(entity.getStrings().contains("foo"), "missing a value");
        assertTrue(entity.getStrings().contains("baz"), "missing a value");

        entity.setStrings(new HashSet<>());

        entity = wrapper.merge(entity);
        assertTrue(entity.getStrings().isEmpty());
    }

    @Entity
    @Table(name = "hash_set_basics")
    public static class HashSetBasic extends SaucedEntity<Long, HashSetBasic> {

        @Id
        @Column(name = "id", nullable = false)
        private long id;

        @Type(type = "hash-set-basic")
        @BasicType(Integer.class)
        @Column(name = "ints", nullable = false)
        private HashSet<Integer> ints = new HashSet<>();

        @Type(type = "hash-set-basic")
        @BasicType(Long.class)
        @Column(name = "longs", nullable = false)
        private HashSet<Long> longs = new HashSet<>();

        @Type(type = "hash-set-basic")
        @BasicType(String.class)
        @Column(name = "strings", nullable = false)
        private HashSet<String> strings = new HashSet<>();

        HashSetBasic() {
        }

        public HashSetBasic(long id,
                            Collection<Integer> ints,
                            Collection<Long> longs,
                            Collection<String> strings) {
            this.id = id;
            this.ints = new HashSet<>(ints);
            this.longs = new HashSet<>(longs);
            this.strings = new HashSet<>(strings);
        }

        @Override
        public HashSetBasic setId(Long id) {
            this.id = id;
            return this;
        }

        @Override
        public Long getId() {
            return this.id;
        }

        @Override
        public Class<HashSetBasic> getClazz() {
            return HashSetBasic.class;
        }

        public HashSet<Integer> getInts() {
            return ints;
        }

        public void setInts(HashSet<Integer> ints) {
            this.ints = ints;
        }

        public HashSetBasic addInt(Integer integer) {
            ints.add(integer);
            return this;
        }

        public HashSetBasic removeInt(Integer integer) {
            ints.remove(integer);
            return this;
        }

        public HashSet<Long> getLongs() {
            return longs;
        }

        public void setLongs(HashSet<Long> longs) {
            this.longs = longs;
        }

        public HashSetBasic addLong(Long lon) {
            longs.add(lon);
            return this;
        }

        public HashSetBasic removeLong(Long lon) {
            longs.remove(lon);
            return this;
        }

        public HashSet<String> getStrings() {
            return strings;
        }

        public void setStrings(HashSet<String> strings) {
            this.strings = new HashSet<>(strings);
        }

        public HashSetBasic addString(String string) {
            strings.add(string);
            return this;
        }

        public HashSetBasic removeString(String string) {
            strings.remove(string);
            return this;
        }
    }
}
