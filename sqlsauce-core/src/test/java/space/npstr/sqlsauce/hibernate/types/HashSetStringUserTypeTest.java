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
public class HashSetStringUserTypeTest extends BaseTest {

    private static final String DROP_TABLE_HASH_SET_STRINGS = String.format(DROP_TABLE_IF_EXISTS, "hash_set_strings");
    private static final String CREATE_TABLE_HASH_SET_STRINGS
            = "CREATE TABLE hash_set_strings "
            + "( "
            + "    id BIGINT NOT NULL, "
            + "    strings TEXT[] NOT NULL, "
            + "    CONSTRAINT hash_set_strings_pkey PRIMARY KEY (id) "
            + ");";


    @Test
    public void hashSetStringUserType() {
        DatabaseConnection connection = requireConnection();
        DatabaseWrapper wrapper = new DatabaseWrapper(connection);

        wrapper.executeSqlQuery(DROP_TABLE_HASH_SET_STRINGS);
        wrapper.executeSqlQuery(CREATE_TABLE_HASH_SET_STRINGS);

        HashSet<String> strings = new HashSet<>(Arrays.asList("foo", "bar"));
        HashSetString entity = new HashSetString(33, strings);
        entity = wrapper.persist(entity);

        assertTrue(hashSetsHaveEqualContent(strings, entity.getStrings()), "string sets different after persisting");

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
    @Table(name = "hash_set_strings")
    public static class HashSetString extends SaucedEntity<Long, HashSetString> {

        @Id
        @Column(name = "id", nullable = false)
        private long id;

        @Type(type = "hash-set-string")
        @Column(name = "strings", nullable = false)
        private HashSet<String> strings = new HashSet<>();

        HashSetString() {
        }

        public HashSetString(long id, Collection<String> strings) {
            this.id = id;
            this.strings = new HashSet<>(strings);
        }

        @Override
        public HashSetString setId(Long id) {
            this.id = id;
            return this;
        }

        @Override
        public Long getId() {
            return this.id;
        }

        @Override
        public Class<HashSetString> getClazz() {
            return HashSetString.class;
        }

        public HashSet<String> getStrings() {
            return strings;
        }

        public void setStrings(HashSet<String> strings) {
            this.strings = new HashSet<>(strings);
        }

        public HashSetString addString(String string) {
            strings.add(string);
            return this;
        }

        public HashSetString removeString(String string) {
            strings.remove(string);
            return this;
        }
    }
}
