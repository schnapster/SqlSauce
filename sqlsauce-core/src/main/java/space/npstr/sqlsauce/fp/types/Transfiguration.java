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

import space.npstr.sqlsauce.entities.SaucedEntity;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.function.Function;

/**
 * Created by napster on 03.12.17.
 * <p>
 * Describes a transformation that can be applied to an entity.
 * <p>
 * Somewhat of a Type class.
 * <p>
 * The relation between I and E is controlled by the publicly available constructors / factory methods.
 * <p>
 * As for the name....I needed a name ¯\_(ツ)_/¯. Didnt want another "EntitySomething" class.
 */
public class Transfiguration<I, E> {

    public final EntityKey<I, E> key;
    public final Function<E, E> tf;


    @Nonnull
    public static <E extends SaucedEntity<I, E>, I extends Serializable>
    Transfiguration<I, E> of(@Nonnull final EntityKey<I, E> entityKey,
                             @Nonnull final Function<E, E> transformation) {
        return new Transfiguration<>(entityKey, transformation);
    }

    private Transfiguration(@Nonnull final EntityKey<I, E> entityKey, @Nonnull final Function<E, E> transformation) {
        this.key = entityKey;
        this.tf = transformation;
    }
}
