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

package space.npstr.sqlsauce;

import org.hibernate.Session;
import space.npstr.sqlsauce.entities.IEntity;
import space.npstr.sqlsauce.entities.SaucedEntity;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by napster on 30.05.17.
 * <p>
 * This class is all about saving/loading/deleting Sauced Entities / IEntities and executing JPQL and SQL queries
 */
public class DatabaseWrapper {
    @Nonnull
    private final DatabaseConnection databaseConnection;

    public DatabaseWrapper(@Nonnull final DatabaseConnection database) {
        this.databaseConnection = database;
    }

    @Nonnull
    public DatabaseConnection unwrap() {
        return this.databaseConnection;
    }

    //################################################################################
    //                                   Reading
    //################################################################################

    /**
     * @return The returned entity is not necessarily a persisted one but may be a default constructed one.
     */
    @Nonnull
    @CheckReturnValue
    public <E extends SaucedEntity<I, E>, I extends Serializable> E getOrCreate(@Nonnull final I id,
                                                                                @Nonnull final Class<E> clazz)
            throws DatabaseException {
        final E entity = getEntity(id, clazz);
        //return a fresh object if we didn't find the one we were looking for
        // no need to set the sauce as either getEntity or newInstance do that already
        return entity != null ? entity : newInstance(id, clazz);
    }

    @Nullable
    @CheckReturnValue
    //returns a sauced entity or null
    public <E extends SaucedEntity<I, E>, I extends Serializable> E getEntity(@Nonnull final I id,
                                                                              @Nonnull final Class<E> clazz)
            throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            em.getTransaction().begin();
            @Nullable final E result = em.find(clazz, id);
            em.getTransaction().commit();
            return result != null ? result.setSauce(this) : null;
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to find entity of class %s for id %s on DB %s",
                    clazz.getName(), id.toString(), this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    /**
     * @return A list of all elements of the requested entity class
     */
    // NOTE: this method is probably not a great idea to use for giant tables
    @Nonnull
    @CheckReturnValue
    //returns a list of sauced entities
    public <E extends SaucedEntity<I, E>, I extends Serializable> List<E> loadAll(@Nonnull final Class<E> clazz)
            throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            final String query = "SELECT c FROM " + clazz.getSimpleName() + " c";
            em.getTransaction().begin();
            final List<E> queryResult = em
                    .createQuery(query, clazz)
                    .getResultList();
            em.getTransaction().commit();
            return queryResult
                    .stream()
                    .map(s -> s.setSauce(this))
                    .collect(Collectors.toList());
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to load all %s entities on DB %s",
                    clazz.getName(), this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    /**
     * @return The result list will be ordered by the order of the provided id list, but may contain null for unknown
     * entities
     */
    @Nonnull
    @CheckReturnValue
    //returns a list of sauced entities that may contain null elements
    public <E extends SaucedEntity<I, E>, I extends Serializable> List<E> getEntities(@Nonnull final List<I> ids,
                                                                                      @Nonnull final Class<E> clazz)
            throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            em.getTransaction().begin();
            final List<E> results = em.unwrap(Session.class)
                                      .byMultipleIds(clazz)
                                      .multiLoad(ids);
            em.getTransaction().commit();
            return results
                    .stream()
                    .map(s -> s != null ? s.setSauce(this) : null)
                    .collect(Collectors.toList());
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to bulk load %s entities of class %s on DB %s",
                    ids.size(), clazz.getName(), this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }


    //################################################################################
    //                                  Writing
    //################################################################################

    /**
     * @return The managed version of the provided entity (with set autogenerated values for example).
     */
    @Nonnull
    @CheckReturnValue
    //returns a sauced entity
    public <E extends SaucedEntity<I, E>, I extends Serializable> E merge(@Nonnull final E entity)
            throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            em.getTransaction().begin();
            final E managedEntity = em.merge(entity);
            em.getTransaction().commit();
            return managedEntity
                    .setSauce(this);
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to merge entity %s on DB %s",
                    entity.toString(), this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    /**
     * The difference of persisting to merging is that persisting will throw an exception if the entity exists already.
     *
     * @return The managed version of the provided entity (with set autogenerated values for example).
     */
    @Nonnull
    @CheckReturnValue
    //returns whatever was passed in, with a sauce if it was a sauced entity
    public <E> E persist(@Nonnull final E entity) throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(entity);
            em.getTransaction().commit();
            return setSauce(entity);
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to persist entity %s on DB %s",
                    entity.toString(), this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    //################################################################################
    //                             Functional Magic
    //################################################################################

    /**
     * So you want to load an entity, set some data, and save it again, without detaching it from the persistence
     * context and without bothering with the EntityManager?
     * Look no further! Functional programming to the rescue, just pass a function that does the required transformation
     * on the entity.
     * Note that this will create a new instance of the entity if it does not exist yet.
     *
     * @param transform Some function to apply to the entity while it is present in the persistence context. Usually this
     *                  is some function to set or edit data of the entity, that returns the entity itself again.
     */
    @Nonnull
    @CheckReturnValue
    public <E extends SaucedEntity<I, E>, I extends Serializable> E findApplyAndMerge(@Nonnull final I id,
                                                                                      @Nonnull final Class<E> clazz,
                                                                                      @Nonnull final Function<E, E> transform)
            throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            synchronized (SaucedEntity.getEntityLock(id, clazz)) {
                em.getTransaction().begin();
                E entity = em.find(clazz, id);
                if (entity == null) {
                    entity = newInstance(id, clazz);
                }
                entity = transform.apply(entity);
                entity = em.merge(entity);
                em.getTransaction().commit();
                return entity;
            }
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to find, apply and merge entity id %s of class %s on DB %s",
                    id.toString(), clazz.getName(), this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    //################################################################################
    //                                 Deleting
    //################################################################################

    @SuppressWarnings("unchecked")
    public <E extends IEntity<I, E>, I extends Serializable> void deleteEntity(@Nonnull final E entity)
            throws DatabaseException {
        deleteEntity(entity.getId(), entity.getClass());
    }

    public <E extends IEntity<I, E>, I extends Serializable> void deleteEntity(@Nonnull final I id,
                                                                               @Nonnull final Class<E> clazz)
            throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            em.getTransaction().begin();
            final IEntity entity = em.find(clazz, id);
            if (entity != null) {
                em.remove(entity);
            }
            em.getTransaction().commit();
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to delete entity id %s of class %s on DB %s",
                    id.toString(), clazz.getName(), this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    //todo add batch deleting methods


    //################################################################################
    //                                 JPQL stuff
    //################################################################################

    /**
     * @return the number of entities updated or deleted
     */
    public int executeJpqlQuery(@Nonnull final String queryString, @Nullable final Map<String, Object> parameters)
            throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            final Query query = em.createQuery(queryString);
            if (parameters != null) {
                parameters.forEach(query::setParameter);
            }
            em.getTransaction().begin();
            final int updatedOrDeleted = query.executeUpdate();
            em.getTransaction().commit();
            return updatedOrDeleted;
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to execute JPQL query %s with %s parameters on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    /**
     * Use this for COUNT() and similar jpql queries which are guaranteed to return a result
     */
    @Nonnull
    @CheckReturnValue
    public <T> T selectJpqlQuerySingleResult(@Nonnull final String queryString,
                                             @Nullable final Map<String, Object> parameters,
                                             @Nonnull final Class<T> resultClass) throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            final Query q = em.createQuery(queryString);
            if (parameters != null) {
                parameters.forEach(q::setParameter);
            }
            em.getTransaction().begin();
            final T result = resultClass.cast(q.getSingleResult());
            em.getTransaction().commit();
            return setSauce(result);
        } catch (final PersistenceException | ClassCastException e) {
            final String message = String.format("Failed to select single result JPQL query %s with %s parameters for class %s on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", resultClass.getName(), this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    /**
     * Results will be sauced if they are SaucedEntites
     *
     * @param queryString the raw JPQL query string
     * @param parameters  parameters to be set on the query
     * @param resultClass expected class of the results of the query
     * @param offset      set to -1 or lower for no offset
     * @param limit       set to -1 or lower for no limit
     */
    //limited and offset results
    @Nonnull
    @CheckReturnValue
    public <T> List<T> selectJpqlQuery(@Nonnull final String queryString, @Nullable final Map<String, Object> parameters,
                                       @Nonnull final Class<T> resultClass, final int offset, final int limit)
            throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            final TypedQuery<T> q = em.createQuery(queryString, resultClass);
            if (parameters != null) {
                parameters.forEach(q::setParameter);
            }
            if (offset > -1) {
                q.setFirstResult(offset);
            }
            if (limit > -1) {
                q.setMaxResults(limit);
            }

            em.getTransaction().begin();
            final List<T> resultList = q.getResultList();
            em.getTransaction().commit();
            return resultList.stream()
                             .peek(this::setSauce)
                             .collect(Collectors.toList());
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to select JPQL query %s with %s parameters, offset %s, limit %s, on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", offset, limit, this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    //limited results without offset
    @Nonnull
    @CheckReturnValue
    public <T> List<T> selectJpqlQuery(@Nonnull final String queryString, @Nullable final Map<String, Object> parameters,
                                       @Nonnull final Class<T> resultClass, final int limit) throws DatabaseException {
        return selectJpqlQuery(queryString, parameters, resultClass, -1, limit);
    }

    //limited results without offset
    @Nonnull
    @CheckReturnValue
    public <T> List<T> selectJpqlQuery(@Nonnull final String queryString, @Nonnull final Class<T> resultClass,
                                       final int limit) throws DatabaseException {
        return selectJpqlQuery(queryString, null, resultClass, -1, limit);
    }

    //no limit and no offset
    @Nonnull
    @CheckReturnValue
    public <T> List<T> selectJpqlQuery(@Nonnull final String queryString, @Nullable final Map<String, Object> parameters,
                                       @Nonnull final Class<T> resultClass) throws DatabaseException {
        return selectJpqlQuery(queryString, parameters, resultClass, -1);
    }

    //no limit and no offset
    @Nonnull
    @CheckReturnValue
    public <T> List<T> selectJpqlQuery(@Nonnull final String queryString, @Nonnull final Class<T> resultClass)
            throws DatabaseException {
        return selectJpqlQuery(queryString, null, resultClass, -1);
    }

    //################################################################################
    //                              Plain SQL stuff
    //################################################################################


    /**
     * Run a good old SQL query
     */
    public void executeSqlQuery(@Nonnull final String queryString,
                                @Nullable final Map<String, Object> parameters) throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            final Query q = em.createNativeQuery(queryString);
            if (parameters != null) {
                parameters.forEach(q::setParameter);
            }
            em.getTransaction().begin();
            q.executeUpdate();
            em.getTransaction().commit();
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to execute plain SQL query %s with %s parameters on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    /**
     * Results will be sauced if they are SaucedEntites
     */
    @Nonnull
    @CheckReturnValue
    public <T> List<T> selectSqlQuery(@Nonnull final String queryString,
                                      @Nullable final Map<String, Object> parameters,
                                      @Nonnull final Class<T> resultClass) throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            final Query q = em.createNativeQuery(queryString, resultClass);
            if (parameters != null) {
                parameters.forEach(q::setParameter);
            }
            return selectNativeSqlQuery(em, q);
        } catch (final PersistenceException | ClassCastException e) {
            final String message = String.format("Failed to select list result plain SQL query %s with %s parameters for class %s on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", resultClass.getName(), this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    /**
     * Results will be sauced if they are SaucedEntites
     */
    @Nonnull
    @CheckReturnValue
    public <T> List<T> selectSqlQuery(@Nonnull final String queryString,
                                      @Nullable final Map<String, Object> parameters,
                                      @Nonnull final String resultMapping) throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            final Query q = em.createNativeQuery(queryString, resultMapping);
            if (parameters != null) {
                parameters.forEach(q::setParameter);
            }
            return selectNativeSqlQuery(em, q);
        } catch (final PersistenceException | ClassCastException e) {
            final String message = String.format("Failed to select list result plain SQL query %s with %s parameters for result mapping %s on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", resultMapping, this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    //remember to close the provided EntityManager and catch any exceptions
    //Results will be sauced if they are SaucedEntites
    @Nonnull
    @CheckReturnValue
    @SuppressWarnings("unchecked")
    private <T> List<T> selectNativeSqlQuery(@Nonnull final EntityManager em, @Nonnull final Query query) {
        em.getTransaction().begin();
        final List resultList = query.getResultList();
        em.getTransaction().commit();
        return (List<T>) resultList.stream()
                                   .peek(this::setSauce)
                                   .collect(Collectors.toList());
    }

    /**
     * Use this for COUNT() and similar sql queries which are guaranteed to return a result
     */
    @Nonnull
    @CheckReturnValue
    public <T> T selectSqlQuerySingleResult(@Nonnull final String queryString,
                                            @Nullable final Map<String, Object> parameters,
                                            @Nonnull final Class<T> resultClass) throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            final Query q = em.createNativeQuery(queryString);
            if (parameters != null) {
                parameters.forEach(q::setParameter);
            }
            em.getTransaction().begin();
            final T result = resultClass.cast(q.getSingleResult());
            em.getTransaction().commit();
            return setSauce(result);
        } catch (final PersistenceException | ClassCastException e) {
            final String message = String.format("Failed to select single result plain SQL query %s with %s parameters for class %s on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", resultClass.getName(), this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }


    //################################################################################
    //                                  Internals
    //################################################################################

    //IEntities are required to have a default constructor that sets them up with sensible defaults
    @Nonnull
    @CheckReturnValue
    //returns a sauced entity
    private <E extends SaucedEntity<I, E>, I extends Serializable> E newInstance(@Nonnull final I id,
                                                                                 @Nonnull final Class<E> clazz)
            throws DatabaseException {
        try {
            final E entity = clazz.getConstructor().newInstance();
            return entity.setId(id)
                         .setSauce(this);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            final String message = String.format("Could not construct an entity of class %s with id %s",
                    clazz.getName(), id.toString());
            throw new DatabaseException(message, e);
        }
    }

    private <T> T setSauce(final T t) {
        if (t instanceof SaucedEntity) {
            ((SaucedEntity) t).setSauce(this);
        }
        return t;
    }
}
