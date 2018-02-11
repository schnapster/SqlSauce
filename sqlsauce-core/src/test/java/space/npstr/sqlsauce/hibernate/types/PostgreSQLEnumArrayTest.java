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
import space.npstr.sqlsauce.entities.IEntity;
import space.npstr.sqlsauce.fp.types.EntityKey;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by napster on 10.02.18.
 */
public class PostgreSQLEnumArrayTest extends BaseTest {

    private static final String DROP_TABLE_INFESTED_HOUSES = String.format(DROP_TABLE_IF_EXISTS, "infested_houses");
    private static final String DROP_TYPE_ALIEN_ENUM = "DROP TYPE IF EXISTS " + AlienParasites.class.getSimpleName() + ";";
    private static final String DROP_TYPE_WALLS_ENUM = "DROP TYPE IF EXISTS WALLS;";
    private static final String CREATE_TYPE_WALLS_ENUM = getEnumCreateSql("walls", Fortifications.values());
    private static final String CREATE_TYPE_ALIEN_ENUM = getEnumCreateSql(AlienParasites.class.getSimpleName(), AlienParasites.values());
    private static final String CREATE_TABLE_INFESTED_HOUSES
            = "CREATE TABLE infested_houses "
            + "( "
            + "    id BIGINT NOT NULL, "
            + "    alien_parasites " + AlienParasites.class.getSimpleName() + "[] NOT NULL, "
            + "    fortifications walls[] NOT NULL, "
            + "    CONSTRAINT infested_houses_pkey PRIMARY KEY (id) "
            + ");";


    @Test
    public void testPostgreSQLEnumArray() {
        DatabaseConnection connection = requireConnection();
        DatabaseWrapper wrapper = new DatabaseWrapper(connection);

        wrapper.executeSqlQuery(DROP_TABLE_INFESTED_HOUSES);
        wrapper.executeSqlQuery(DROP_TYPE_ALIEN_ENUM);
        wrapper.executeSqlQuery(DROP_TYPE_WALLS_ENUM);
        wrapper.executeSqlQuery(CREATE_TYPE_WALLS_ENUM);
        wrapper.executeSqlQuery(CREATE_TYPE_ALIEN_ENUM);
        wrapper.executeSqlQuery(CREATE_TABLE_INFESTED_HOUSES);

        HashSet<AlienParasites> parasites = new HashSet<>(Arrays.asList(AlienParasites.PHOTOGRAPHY_RAPTOR, AlienParasites.HAMURAI, AlienParasites.GHOST_IN_A_JAR));
        HashSet<Fortifications> fortifications = new HashSet<>(Collections.singletonList(Fortifications.BLAST_SHIELDS));
        InfestedHouse house = new InfestedHouse(42L, parasites, fortifications);

        house = wrapper.persist(house);

        assertTrue(hashSetsHaveEqualContent(parasites, house.getAlienParasites()), "parasites enums different after persisting");
        assertTrue(hashSetsHaveEqualContent(fortifications, house.getFortifications()), "fortification enums different after persisting");

        InfestedHouse fetched = wrapper.getEntity(EntityKey.of(42L, InfestedHouse.class));

        assertNotNull(fetched, "fetched entity is null");
        assertTrue(hashSetsHaveEqualContent(parasites, fetched.getAlienParasites()), "parasite enums different after fetching");
        assertTrue(hashSetsHaveEqualContent(fortifications, fetched.getFortifications()), "fortification enums different after fetching");
    }

    @Entity
    @Table(name = "infested_houses")
    public static class InfestedHouse implements IEntity<Long, InfestedHouse> {

        @Id
        @Column(name = "id", nullable = false)
        private long id;

        @Type(type = "hash_set-pgsql_enum")
        @PostgreSQLEnum(enumClass = AlienParasites.class)
        @Column(name = "alien_parasites", nullable = false)
        private HashSet<AlienParasites> alienParasites = new HashSet<>();

        @Type(type = "hash_set-pgsql_enum")
        @PostgreSQLEnum(enumClass = Fortifications.class, typeName = "walls")
        @Column(name = "fortifications", nullable = false)
        private HashSet<Fortifications> fortifications = new HashSet<>();

        InfestedHouse() {
        }

        public InfestedHouse(long id, Collection<AlienParasites> alienParasites, Collection<Fortifications> fortifications) {
            this.id = id;
            this.alienParasites = new HashSet<>(alienParasites);
            this.fortifications = new HashSet<>(fortifications);
        }

        @Override
        public InfestedHouse setId(Long id) {
            this.id = id;
            return this;
        }

        @Override
        public Long getId() {
            return this.id;
        }

        @Override
        public Class<InfestedHouse> getClazz() {
            return InfestedHouse.class;
        }

        public HashSet<AlienParasites> getAlienParasites() {
            return alienParasites;
        }

        public HashSet<Fortifications> getFortifications() {
            return fortifications;
        }
    }


    private enum AlienParasites {
        UNCLE_STEVE,
        COUSIN_NICKY,
        MR_BEAUREGARD,
        FRANKENSTEIN,
        SLEEPY_GARY,
        PHOTOGRAPHY_RAPTOR,
        PENCILVESTYR,
        TINKLES,
        HAMURAI,
        AMISH_CYBORG,
        REVERSE_GIRAFFE,
        GHOST_IN_A_JAR,
        BABY_WIZARD,
        MRS_REFRIGERATOR,
        DUCK_WITH_MUSCLES,
    }

    private enum Fortifications {
        BLAST_SHIELDS,
    }


    private static String getEnumCreateSql(String name, Enum[] values) {

        String sql = "CREATE TYPE " + name + " AS ENUM (";

        List<String> aliens = Arrays.stream(values)
                .map(value -> "'" + value.name() + "'")
                .collect(Collectors.toList());
        sql += String.join(", ", aliens);

        sql += ");";

        return sql;
    }
}
