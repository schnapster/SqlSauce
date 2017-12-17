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

package space.npstr.sqlsauce.migration;

import space.npstr.sqlsauce.DatabaseConnection;
import space.npstr.sqlsauce.DatabaseException;

import javax.annotation.Nonnull;

/**
 * Created by napster on 11.10.17.
 * <p>
 * Whatever you do, never rename any migrations you are registering. The simple class name is used to identify them.
 */
public abstract class Migration {

    private final String name;

    public Migration(@Nonnull final String name) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name for migration must not be empty");
        }
        this.name = name;
    }

    public Migration() {
        this.name = this.getClass().getSimpleName();
    }

    @Nonnull
    public String getName() {
        return this.name;
    }

    //ready to use connection to the target database
    //keep in mind these migrations are meant to run after hibernate ddl set up new columns etc
    //throwing a DatabaseException is an acceptable way to indicate that the migration was not successful
    public abstract void up(@Nonnull DatabaseConnection databaseConnection) throws DatabaseException;
}
