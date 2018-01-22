/*
 * MIT License
 *
 * Copyright (c) 2017, Dennis Neufeld
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
package space.npstr.sqlsauce.fp.types;

import space.npstr.sqlsauce.entities.IEntity;
import space.npstr.sqlsauce.entities.SaucedEntity;

import javax.annotation.Nonnull;
import java.io.Serializable;

/**
 * Created by napster on 29.11.17.
 * <p>
 * Unique key for entities, describing both their id and class.
 * <p>
 * Somewhat of a Type class.
 * <p>
 * The relation between I and E is controlled by the publicly available constructors / factory methods.
 */
public class EntityKey<I, E> {
    public final I id;
    public final Class<E> clazz;

    @Nonnull
    public static <I extends Serializable, E extends IEntity<I, E>> EntityKey<I, E> of(@Nonnull final I id,
                                                                                       @Nonnull final Class<E> clazz) {
        return new EntityKey<>(id, clazz);
    }

    @Nonnull
    public static <I extends Serializable, E extends IEntity<I, E>> EntityKey<I, E> of(@Nonnull final IEntity<I, E> entity) {
        return new EntityKey<>(entity.getId(), entity.getClazz());
    }

    @Nonnull
    public static <I extends Serializable, E extends SaucedEntity<I, E>> EntityKey<I, E> of(@Nonnull final SaucedEntity<I, E> entity) {
        return new EntityKey<>(entity.getId(), entity.getClazz());
    }

    protected EntityKey(@Nonnull final I id, @Nonnull final Class<E> clazz) {
        this.id = id;
        this.clazz = clazz;
    }

    @Override
    @Nonnull
    public String toString() {
        return "E:" + this.clazz.getSimpleName() + ":" + this.id;
    }
}
