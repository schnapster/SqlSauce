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
