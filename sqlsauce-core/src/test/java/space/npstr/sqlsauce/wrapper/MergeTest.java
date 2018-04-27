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
import space.npstr.sqlsauce.test.entities.Merge;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Created by napster on 27.04.18.
 */
public class MergeTest extends BaseTest {


    @Test
    public void test() {
        DatabaseWrapper wrapper = new DatabaseWrapper(requireConnection());
        //prepare a table
        wrapper.executeSqlQuery(String.format(DROP_TABLE_IF_EXISTS, "public.merge_test"), null);
        wrapper.executeSqlQuery(String.format(CREATE_SIMPLE_TABLE, "merge_test"), null);

        Merge random = new Merge().setId(ThreadLocalRandom.current().nextLong()).setName("random");

        Merge persisted = wrapper.persist(random);
        assertNotNull(persisted, "persisted entity is null");
        assertEquals(random.getId(), persisted.getId(), "persisted id is not equal");
        assertEquals(random.getName(), persisted.getName(), "persisted name is not equal");

        Merge get = wrapper.getEntity(EntityKey.of(random.getId(), Merge.class));
        assertNotNull(get, "get entity is null");
        assertEquals(random.getId(), get.getId(), "get id is not equal");
        assertEquals(random.getName(), get.getName(), "get name is not equal");

        persisted.setName("super-random");
        Merge merged = wrapper.merge(persisted);
        assertNotNull(merged, "merged entity is null");
        assertEquals(persisted.getId(), merged.getId(), "persisted id is not equal");
        assertEquals(persisted.getName(), merged.getName(), "persisted name is not equal");

        get = wrapper.getEntity(EntityKey.of(persisted.getId(), Merge.class));
        assertNotNull(get, "get entity is null");
        assertEquals(persisted.getId(), get.getId(), "get id is not equal");
        assertEquals(persisted.getName(), get.getName(), "get name is not equal");
    }
}
