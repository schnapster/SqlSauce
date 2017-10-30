package space.npstr.sqlsauce;

/**
 * Created by napster on 30.10.17.
 */
@FunctionalInterface
public interface DatabaseTask {
    void run() throws DatabaseException;
}
