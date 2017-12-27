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

package space.npstr.sqlsauce.migration;

import space.npstr.sqlsauce.DatabaseConnection;
import space.npstr.sqlsauce.DatabaseException;

import javax.annotation.Nonnull;
import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by napster on 28.11.17.
 * <p>
 * This one just runs a bunch of parameterless native queries
 */
public class SimpleMigration extends Migration {

    @Nonnull
    private final List<String> queries = new ArrayList<>();

    public SimpleMigration(@Nonnull final String name) {
        super(name);
    }

    @Nonnull
    public SimpleMigration addQuery(@Nonnull final String query) {
        this.queries.add(query);
        return this;
    }


    @Override
    public void up(@Nonnull final DatabaseConnection databaseConnection) throws DatabaseException {
        final EntityManager em = databaseConnection.getEntityManager();
        try {
            em.getTransaction().begin();
            for (final String query : this.queries) {
                em.createNativeQuery(query).executeUpdate();
            }
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }
}
