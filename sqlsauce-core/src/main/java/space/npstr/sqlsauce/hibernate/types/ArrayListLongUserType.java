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
import org.hibernate.usertype.UserType;

import javax.annotation.Nullable;
import java.io.Serializable;
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
public class ArrayListLongUserType implements UserType {
    protected static final int SQLTYPE = java.sql.Types.ARRAY;

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

            Long[] longs = castObject.toArray(new Long[castObject.size()]);
            Array array = connection.createArrayOf("bigint", longs);

            st.setArray(index, array);
        }
    }

    @Override
    public Object assemble(final Serializable cached, final Object owner) {
        return cached;
    }

    @Nullable
    @Override
    public Object deepCopy(final Object o) {
        return o == null ? null : ((ArrayList) o).clone();
    }

    @Override
    public Serializable disassemble(final Object o) {
        return (Serializable) o;
    }

    @Override
    public boolean equals(final Object x, final Object y) {
        return x == null ? y == null : x.equals(y);
    }

    @Override
    public int hashCode(final Object o) {
        return o == null ? 0 : o.hashCode();
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Object replace(final Object original, final Object target, final Object owner) {
        return original;
    }

    @Override
    public Class<ArrayList> returnedClass() {
        return ArrayList.class;
    }

    @Override
    public int[] sqlTypes() {
        return new int[]{SQLTYPE};
    }
}