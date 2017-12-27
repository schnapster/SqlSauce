/*
 * MIT License
 *
 * Copyright (c) 2017 Dennis Neufeld
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

package space.npstr.sqlsauce.entities;

import com.vladmihalcea.hibernate.type.array.IntArrayType;
import com.vladmihalcea.hibernate.type.array.StringArrayType;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import space.npstr.sqlsauce.hibernate.types.LongArrayType;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.persistence.MappedSuperclass;
import java.io.Serializable;

/**
 * Created by napster on 02.05.17.
 * <p>
 * Implement this in all entities to retrieve them easily over a shared function
 * in DatabaseWrapper while having some type safety
 * <p>
 * Works best for entities where we have a natural id for (for example entities on a guild or channel scope cause we can use their snowflakeIds)
 * IEntities should have a constructor that sets them up with sensible defaults
 */
@TypeDefs({
        @TypeDef(
                name = "string-array",
                typeClass = StringArrayType.class
        ),
        @TypeDef(
                name = "int-array",
                typeClass = IntArrayType.class
        ),
        @TypeDef(
                name = "long-array",
                typeClass = LongArrayType.class
        )
})
@MappedSuperclass
public interface IEntity<I extends Serializable, Self extends IEntity<I, Self>> {

    @Nonnull
    @CheckReturnValue
    Self setId(@Nonnull I id);

    @Nonnull
    I getId();

    @Nonnull
    @CheckReturnValue
    Class<Self> getClazz();
}
