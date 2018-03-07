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

import javax.annotation.Nullable;
import java.io.Serializable;
import java.lang.annotation.Annotation;
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
 * Created by napster on 11.02.18.
 * <p>
 * This makes PostgreSQL enum type arrays inside a single column usable instead of ElementCollections.
 * Cause Hibernates/JPAs ElementCollections are really, really slow, especially the bigger they get.
 * <p>
 * Requires the field to be annotated with {@link PostgreSQLEnum} retaining the information of the enum class at runtime
 */
public class HashSetPostgreSQLEnumUserType implements UserType, DynamicParameterizedType {

    protected static final int SQLTYPE = Types.ARRAY;

    @Nullable
    private Class<? extends Enum> enumClass;

    @Nullable
    private String typeName;

    @Override
    public void setParameterValues(Properties parameters) {
        final ParameterType reader = (ParameterType) parameters.get(PARAMETER_TYPE);
        @SuppressWarnings("unchecked") Class<? extends Enum> suppress = getEnumClass(reader);
        this.enumClass = suppress;
        this.typeName = getTypeName(reader);
    }

    @Nullable
    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner)
            throws HibernateException, SQLException {
        Array array = rs.getArray(names[0]);
        if (rs.wasNull() || array == null) {
            return null;
        }
        String[] javaArray = (String[]) array.getArray();

        if (this.enumClass == null) {
            throw new IllegalStateException("Not properly initialized, missing the enum class");
        }

        return Arrays.stream(javaArray)
                .map(value -> stringToEnum(this.enumClass, value.trim()))
                .<HashSet<Enum>>collect(HashSet::new, HashSet::add, HashSet::addAll);
    }

    private static Enum stringToEnum(Class<? extends Enum> enumClass, String value) {
        @SuppressWarnings("unchecked") Enum anEnum = Enum.valueOf(enumClass, value.trim());
        return anEnum;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session)
            throws HibernateException, SQLException {
        if (value == null) {
            st.setNull(index, sqlTypes()[0]);
        } else {
            Connection connection = st.getConnection();
            @SuppressWarnings("unchecked") HashSet<? extends Enum> castObject = (HashSet) value;

            if (this.enumClass == null) {
                throw new IllegalStateException("Not properly initialized, missing the enum class");
            }
            if (this.typeName == null) {
                throw new IllegalStateException("Not properly initialized, missing the type name");
            }

            Enum[] enums = castObject.toArray(new Enum[castObject.size()]);
            String type = this.typeName.isEmpty() ? this.enumClass.getSimpleName() : this.typeName;
            Array array = connection.createArrayOf(type, enums);

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

    private Class<? extends Enum> getEnumClass(ParameterType reader) {
        PostgreSQLEnum enumAnn = getAnnotation(reader.getAnnotationsMethod(), PostgreSQLEnum.class);
        if (enumAnn != null) {
            return enumAnn.enumClass();
        } else {
            throw new IllegalStateException("Missing @" + PostgreSQLEnum.class.getSimpleName() + " annotation");
        }
    }

    private String getTypeName(ParameterType reader) {
        PostgreSQLEnum enumAnn = getAnnotation(reader.getAnnotationsMethod(), PostgreSQLEnum.class);
        if (enumAnn != null) {
            return enumAnn.typeName();
        } else {
            throw new IllegalStateException("Missing @" + PostgreSQLEnum.class.getSimpleName() + " annotation");
        }
    }

    @Nullable
    private <T extends Annotation> T getAnnotation(Annotation[] annotations, Class<T> anClass) {
        for (Annotation annotation : annotations) {
            if (anClass.isInstance(annotation)) {
                @SuppressWarnings("unchecked") T result = (T) annotation;
                return result;
            }
        }
        return null;
    }
}
