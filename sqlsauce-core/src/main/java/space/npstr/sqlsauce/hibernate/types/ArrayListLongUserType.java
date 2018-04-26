/*
 * MIT License
 *
 * Copyright (c) 2017-2017, Dennis Neufeld
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


import org.hibernate.engine.spi.SharedSessionContractImplementor;

import javax.annotation.Nullable;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by napster on 27.12.17.
 * <p>
 * This makes the Postgres native arrays inside a single column usable instead of ElementCollections.
 * Cause Hibernates/JPAs ElementCollections are really, really slow, especially the bigger they get.
 * <p>
 * source: https://stackoverflow.com/a/41413296/
 */
public class ArrayListLongUserType extends CommonArrayType {

    @Nullable
    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        Array array = rs.getArray(names[0]);
        if (array == null) {
            return null;
        }
        Long[] javaArray = (Long[]) array.getArray();
        ArrayList<Long> result = new ArrayList<>();
        Collections.addAll(result, javaArray); //do not use Arrays.asList(), that method returns a fake ArrayList
        return result;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        Connection connection = st.getConnection();

        if (value == null) {
            st.setNull(index, sqlTypes()[0]);
        } else {
            @SuppressWarnings("unchecked") ArrayList<Long> castObject = (ArrayList) value;

            Long[] longs = castObject.toArray(new Long[0]);
            Array array = connection.createArrayOf("bigint", longs);

            st.setArray(index, array);
        }
    }

    @Nullable
    @Override
    public Object deepCopy(final Object o) {
        return o == null ? null : ((ArrayList) o).clone();
    }

    @Override
    public Class<ArrayList> returnedClass() {
        return ArrayList.class;
    }

}