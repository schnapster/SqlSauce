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

package space.npstr.sqlstack.entities;

import space.npstr.sqlstack.DatabaseException;
import space.npstr.sqlstack.DatabaseWrapper;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import java.io.Serializable;

/**
 * Created by napster on 10.10.17.
 * <p>
 * Sauced entities may have the database source where they come from / should be written to attached which is
 * convenient af. In case you are wondering, sauce is internet slang for source.
 */
@MappedSuperclass
public abstract class SaucedEntity<I extends Serializable, Self extends SaucedEntity<I, Self>> implements IEntity<I, Self> {

    //the sauce of this entity
    @Transient
    protected DatabaseWrapper dbWrapper;


    @SuppressWarnings("unchecked")
    protected Self getThis() {
        return (Self) this;
    }


    //when loading / creating with the DatabaseWrapper class, it will make sure to set this so that the convenience
    //methods may be used
    @Nonnull
    public Self setSauce(@Nonnull final DatabaseWrapper dbWrapper) {
        this.dbWrapper = dbWrapper;
        return getThis();
    }


    //################################################################################
    //                              Convenience stuff
    //################################################################################

    @Nonnull
    @CheckReturnValue
    public Self save() throws DatabaseException {
        checkWrapper();
        return this.dbWrapper.merge(getThis());
    }


    //################################################################################
    //                                  Internals
    //################################################################################

    private void checkWrapper() {
        if (this.dbWrapper == null) {
            throw new IllegalStateException("DatabaseWrapper not set. Make sure to load entity through a " +
                    "DatabaseWrapper or manually set it by calling SaucedEntity#setSauce");
        }
    }

}
