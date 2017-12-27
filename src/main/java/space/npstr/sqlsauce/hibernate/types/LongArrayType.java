package space.npstr.sqlsauce.hibernate.types;

import com.vladmihalcea.hibernate.type.array.internal.ArraySqlTypeDescriptor;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Properties;

/**
 * Maps an {@code long[]} array on a PostgreSQL ARRAY type.
 * <p>
 * Created by napster on 27.12.17.
 * Original source: IntArrayType of Vlad Mihalcea's hibernate types package
 * https://github.com/vladmihalcea/hibernate-types under Apache License 2.0
 */
public class LongArrayType
        extends AbstractSingleColumnStandardBasicType<long[]>
        implements DynamicParameterizedType {

    public static final LongArrayType INSTANCE = new LongArrayType();

    public LongArrayType() {
        super(ArraySqlTypeDescriptor.INSTANCE, LongArrayTypeDescriptor.INSTANCE);
    }

    @Override
    public String getName() {
        return "long-array";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((LongArrayTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }
}
