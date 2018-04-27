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
import space.npstr.sqlsauce.test.entities.Delete;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Created by napster on 27.04.18.
 */
public class DeleteTest extends BaseTest {

    @Test
    public void test() {
        DatabaseWrapper wrapper = new DatabaseWrapper(requireConnection());
        //prepare a table
        wrapper.executeSqlQuery(String.format(DROP_TABLE_IF_EXISTS, "public.delete_test"), null);
        wrapper.executeSqlQuery(String.format(CREATE_SIMPLE_TABLE, "delete_test"), null);


        Delete delete = new Delete().setId(ThreadLocalRandom.current().nextLong()).setName("does not matter");
        delete = wrapper.persist(delete);

        EntityKey<Long, Delete> key = EntityKey.of(delete.getId(), Delete.class);
        assertNotNull(wrapper.getEntity(key), "entity is missing");

        wrapper.deleteEntity(delete);
        assertNull(wrapper.getEntity(key), "entity still there after deletion");
    }
}
