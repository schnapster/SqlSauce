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

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.DynamicParameterizedType;
import org.hibernate.usertype.UserType;
import space.npstr.sqlsauce.DbUtils;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;

/**
 * Created by napster on 26.04.18.
 * <p>
 * Use PostgreSQL's native arrays to stare HashMaps inside a single column, instead of {@link javax.persistence.ElementCollection}s
 * because Hibernates/JPAs ElementCollections are really, really slow, especially the bigger they get.
 * <p>
 * Supports Integer, Long, String.
 * <p>
 * Mappings:
 * Java    <-> PostgreSQL
 * Integer  -  integer
 * Long     -  bigint
 * String   -  text
 * <p>
 * This requires using the {@link BasicType} annotation to retain the otherwise erased generics information.
 * <p>
 * For enum support, look at {@link HashSetPostgreSQLEnumUserType}
 */
public class HashSetBasicType implements UserType, DynamicParameterizedType {


    protected static final int SQLTYPE = Types.ARRAY;

    @Nullable
    private Class<?> basicType;

    @Override
    public void setParameterValues(Properties parameters) {
        final ParameterType reader = (ParameterType) parameters.get(PARAMETER_TYPE);
        this.basicType = getBasicType(reader);
    }

    @Nullable
    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner)
            throws HibernateException, SQLException {
        Array array = rs.getArray(names[0]);
        if (rs.wasNull() || array == null) {
            return null;
        }
        if (this.basicType == null) {
            throw new IllegalStateException("Not properly initialized, missing the basic type");
        }

        if (basicType.equals(Integer.class)) {
            return new HashSet<>(Arrays.asList((Integer[]) array.getArray()));
        } else if (basicType.equals(Long.class)) {
            return new HashSet<>(Arrays.asList((Long[]) array.getArray()));
        } else if (basicType.equals(String.class)) {
            return new HashSet<>(Arrays.asList((String[]) array.getArray()));
        } else {
            throw new IllegalArgumentException("Unsupported type: " + basicType.getSimpleName());
        }
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session)
            throws HibernateException, SQLException {
        if (value == null) {
            st.setNull(index, sqlTypes()[0]);
        } else {
            if (this.basicType == null) {
                throw new IllegalStateException("Not properly initialized, missing the basic type");
            }

            Connection connection = st.getConnection();
            Array array;

            if (basicType.equals(Integer.class)) {
                @SuppressWarnings("unchecked") Integer[] ints = ((HashSet<Integer>) value).toArray(new Integer[0]);
                array = connection.createArrayOf("integer", ints);
            } else if (basicType.equals(Long.class)) {
                @SuppressWarnings("unchecked") Long[] longs = ((HashSet<Long>) value).toArray(new Long[0]);
                array = connection.createArrayOf("bigint", longs);
            } else if (basicType.equals(String.class)) {
                @SuppressWarnings("unchecked") String[] strings = ((HashSet<String>) value).toArray(new String[0]);
                array = connection.createArrayOf("text", strings);
            } else {
                throw new IllegalArgumentException("Unsupported type: " + basicType.getSimpleName());
            }

            st.setArray(index, array);
        }
    }

    @Override
    public Object assemble(final Serializable cached, final Object owner) throws HibernateException {
        return cached;
    }

    @Nullable
    @Override
    public Object deepCopy(final Object o) throws HibernateException {
        return o == null ? null : ((HashSet) o).clone();
    }

    @Override
    public Serializable disassemble(final Object o) throws HibernateException {
        return (Serializable) o;
    }

    @Override
    public boolean equals(final Object x, final Object y) throws HibernateException {
        return x == null ? y == null : x.equals(y);
    }

    @Override
    public int hashCode(final Object o) throws HibernateException {
        return o == null ? 0 : o.hashCode();
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Object replace(final Object original, final Object target, final Object owner) throws HibernateException {
        return original;
    }

    @Override
    public Class<HashSet> returnedClass() {
        return HashSet.class;
    }

    @Override
    public int[] sqlTypes() {
        return new int[]{SQLTYPE};
    }

    private Class<?> getBasicType(ParameterType reader) {
        BasicType annotation = DbUtils.getAnnotation(reader.getAnnotationsMethod(), BasicType.class);
        if (annotation != null) {
            return annotation.value();
        } else {
            throw new IllegalStateException("Missing @" + BasicType.class.getSimpleName() + " annotation");
        }
    }

}
