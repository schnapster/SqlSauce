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

package space.npstr.sqlsauce.wrapper;

import org.junit.jupiter.api.Test;
import space.npstr.sqlsauce.BaseTest;
import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.fp.types.EntityKey;
import space.npstr.sqlsauce.test.entities.Fetching;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Created by napster on 27.04.18.
 */
public class FetchingTest extends BaseTest {

    @Test
    public void test() {
        DatabaseWrapper wrapper = new DatabaseWrapper(requireConnection());
        //prepare a table
        wrapper.executeSqlQuery(String.format(DROP_TABLE_IF_EXISTS, "public.fetching_test"), null);
        wrapper.executeSqlQuery(String.format(CREATE_SIMPLE_TABLE, "fetching_test"), null);

        final Fetching getOrCreate = wrapper.getOrCreate(EntityKey.of(6L, Fetching.class));
        assertNotNull(getOrCreate, "getOrCreate() returned null");
        assertEquals(6L, (long) getOrCreate.getId(), "ids not equal of created entity");
        assertEquals("", getOrCreate.getName(), "name is not empty as expected");

        getOrCreate.setName("Gearhead");
        final Fetching gearhead = wrapper.merge(getOrCreate);
        final Fetching birdPerson = wrapper.merge(new Fetching().setId(7L).setName("Bird Person"));


        final Fetching getEntity = wrapper.getEntity(EntityKey.of(gearhead.getId(), Fetching.class));
        assertNotNull(getEntity, "Entity that as just merged is missing");
        assertEquals(gearhead.getId(), getEntity.getId(), "fetching entity with wrong id");
        assertEquals(gearhead.getName(), getEntity.getName(), "fetching entity has not the expected name");

        final List<Fetching> getEntities = wrapper.getEntities(Arrays.asList(gearhead.getId(), birdPerson.getId()), Fetching.class);
        assertEquals(2, getEntities.size(), "expected exactly 2 entities");

        final List<Fetching> getEntitiesViaKey = wrapper.getEntities(Collections.singletonList(EntityKey.of(birdPerson.getId(), Fetching.class)));
        assertEquals(1, getEntitiesViaKey.size(), "expected exactly 1 entity");
        assertEquals(birdPerson.getId(), getEntitiesViaKey.get(0).getId(), "entity with wrong id loaded");
        assertEquals(birdPerson.getName(), getEntitiesViaKey.get(0).getName(), "entity has wrong name");

        final List<Fetching> loadAll = wrapper.loadAll(Fetching.class);
        assertEquals(2, loadAll.size(), "expected exactly 2 entities");
    }

}
