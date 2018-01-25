package space.npstr.sqlsauce.hibernate.types;

import com.vladmihalcea.hibernate.type.array.internal.AbstractArrayTypeDescriptor;

/**
 * Created by napster on 27.12.17.
 * <p>
 * Original source: IntArrayTypeDescriptor of Vlad Mihalcea's hibernate types package
 * https://github.com/vladmihalcea/hibernate-types under Apache License 2.0
 */
public class LongArrayTypeDescriptor
        extends AbstractArrayTypeDescriptor<long[]> {

    public static final LongArrayTypeDescriptor INSTANCE = new LongArrayTypeDescriptor();
    private static final long serialVersionUID = 1121850530286001239L;

    public LongArrayTypeDescriptor() {
        super(long[].class);
    }

    @Override
    protected String getSqlArrayType() {
        return "bigint";
    }
}
