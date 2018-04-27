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
import space.npstr.sqlsauce.test.entities.EmptyFetching;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Created by napster on 27.04.18.
 */
public class FetchingFromEmptyTableTest extends BaseTest {

    @Test
    public void test() {
        DatabaseWrapper wrapper = new DatabaseWrapper(requireConnection());
        //prepare a table
        wrapper.executeSqlQuery(String.format(DROP_TABLE_IF_EXISTS, "public.empty_test"), null);
        wrapper.executeSqlQuery(String.format(CREATE_SIMPLE_TABLE, "empty_test"), null);

        final EmptyFetching getEntity = wrapper.getEntity(EntityKey.of(1L, EmptyFetching.class));
        assertNull(getEntity, "Entity fetched from fresh table should not exist");
        final List<EmptyFetching> getEntities = wrapper.getEntities(Collections.singletonList(1L), EmptyFetching.class)
                .stream().filter(Objects::nonNull).collect(Collectors.toList());
        assertEquals(0, getEntities.size(), "returned entities from a fresh table");
        final List<EmptyFetching> getEntitiesViaKey = wrapper.getEntities(Collections.singletonList(EntityKey.of(1L, EmptyFetching.class)))
                .stream().filter(Objects::nonNull).collect(Collectors.toList());
        assertEquals(0, getEntitiesViaKey.size(), "returned entities from a fresh table");
        final List<EmptyFetching> loadAll = wrapper.loadAll(EmptyFetching.class);
        assertEquals(0, loadAll.size(), "returned entities from a fresh table");
    }
}
