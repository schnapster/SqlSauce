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

import java.io.Serializable;
import java.util.Objects;

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

    public static <I extends Serializable, E extends IEntity<I, E>> EntityKey<I, E> of(final I id,
                                                                                       final Class<E> clazz) {
        return new EntityKey<>(id, clazz);
    }

    public static <I extends Serializable, E extends IEntity<I, E>> EntityKey<I, E> of(final IEntity<I, E> entity) {
        return new EntityKey<>(entity.getId(), entity.getClazz());
    }

    public static <I extends Serializable, E extends SaucedEntity<I, E>> EntityKey<I, E> of(final SaucedEntity<I, E> entity) {
        return new EntityKey<>(entity.getId(), entity.getClazz());
    }

    protected EntityKey(final I id, final Class<E> clazz) {
        this.id = id;
        this.clazz = clazz;
    }

    @Override
    public String toString() {
        return "E:" + this.clazz.getSimpleName() + ":" + this.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof EntityKey)) {
            return false;
        }
        EntityKey other = (EntityKey) obj;
        return clazz.equals(other.clazz) && id.equals(other.id);
    }
}
