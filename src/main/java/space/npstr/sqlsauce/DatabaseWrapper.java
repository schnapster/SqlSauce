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
import org.hibernate.internal.SessionImpl;
import org.hibernate.query.spi.QueryImplementor;
import space.npstr.sqlsauce.entities.IEntity;
import space.npstr.sqlsauce.entities.SaucedEntity;
import space.npstr.sqlsauce.fp.types.EntityKey;
import space.npstr.sqlsauce.fp.types.Transfiguration;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public <E extends SaucedEntity<I, E>, I extends Serializable> E getOrCreate(@Nonnull final EntityKey<I, E> entityKey)
            throws DatabaseException {
        final E entity = getEntity(entityKey);
        //return a fresh object if we didn't find the one we were looking for
        // no need to set the sauce as either getEntity or newInstance do that already
        return entity != null ? entity : newInstance(entityKey);
    }

    /**
     * @return An entity if it exists in the database or null if it doesn't exist. If the entity is a SaucedEntity the
     * sauce will be set.
     */
    @Nullable
    @CheckReturnValue
    public <E extends IEntity<I, E>, I extends Serializable> E getEntity(@Nonnull final EntityKey<I, E> entityKey)
            throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            em.getTransaction().begin();
            @Nullable final E result = em.find(entityKey.clazz, entityKey.id);
            em.getTransaction().commit();
            return setSauce(result);
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to find entity of class %s for id %s on DB %s",
                    entityKey.clazz.getName(), entityKey.id.toString(), this.databaseConnection.getName());
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
        return getEntities(ids.stream()
                .map(id -> EntityKey.of(id, clazz))
                .collect(Collectors.toList()));
    }

    /**
     * @return The result list will be ordered by the order of the provided id list, but may contain null for unknown
     * entities
     */
    @Nonnull
    @CheckReturnValue
    //returns a list of sauced entities that may contain null elements
    public <E extends SaucedEntity<I, E>, I extends Serializable> List<E> getEntities(@Nonnull final List<EntityKey<I, E>> entityKeys)
            throws DatabaseException {
        if (entityKeys.isEmpty()) {
            return Collections.emptyList();
        }
        final Class<E> clazz = entityKeys.get(0).clazz;
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            em.getTransaction().begin();
            final List<E> results = em.unwrap(Session.class)
                    .byMultipleIds(clazz)
                    .multiLoad(entityKeys.stream().map(key -> key.id).collect(Collectors.toList()));
            em.getTransaction().commit();
            return results
                    .stream()
                    .map(s -> s != null ? s.setSauce(this) : null)
                    .collect(Collectors.toList());
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to bulk load %s entities of class %s on DB %s",
                    entityKeys.size(), clazz.getName(), this.databaseConnection.getName());
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
     * <p>
     * NOTE that this will create a new instance of the entity if it does not exist yet.
     *
     */
    @Nonnull
    public <E extends SaucedEntity<I, E>, I extends Serializable> E findApplyAndMerge(@Nonnull final Transfiguration<I, E> transfiguration)
            throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            return this.lockedWrappedTransformFunc(transfiguration).apply(em);
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to find, apply and merge entity id %s of class %s on DB %s",
                    transfiguration.key.id.toString(), transfiguration.key.clazz.getName(),
                    this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    //key + transformation -> transfiguration
    public <E extends SaucedEntity<I, E>, I extends Serializable> E findApplyAndMerge(@Nonnull final EntityKey<I, E> entityKey,
                                                                                      @Nonnull final Function<E, E> transformation)
            throws DatabaseException {
        return findApplyAndMerge(Transfiguration.of(entityKey, transformation));
    }



    /**
     * A bulk method for applying transformations to a stream of data.
     * @return Exceptions thrown while processing the input stream
     */
    @Nonnull
    public <E extends SaucedEntity<I, E>, I extends Serializable> List<DatabaseException> findApplyAndMergeAll(
            @Nonnull final Stream<Transfiguration<I, E>> transfigurations) {
        final List<DatabaseException> exceptions = new ArrayList<>();

        transfigurations.forEach(transfiguration -> {
            try {
                final EntityManager em = this.databaseConnection.getEntityManager();
                try {
                    this.lockedWrappedTransformFunc(transfiguration).apply(em);
                } catch (final PersistenceException e) {
                    final String message = String.format("Failed to find, apply and merge entity id %s of class %s on DB %s",
                            transfiguration.key.id.toString(), transfiguration.key.clazz.getName(),
                            this.databaseConnection.getName());
                    exceptions.add(new DatabaseException(message, e));
                } finally {
                    em.close();
                }
            } catch (final DatabaseException e) {
                exceptions.add(e);
            }
        });

        return exceptions;
    }

    /**
     * Transform the entity described by the provided entity key with the provided transformation. The returned
     * transaction is wrapped in begin() commit().
     * <p>
     * When executing the function PersistenceExceptions may be thrown
     */
    @Nonnull
    @CheckReturnValue
    public <E extends SaucedEntity<I, E>, I extends Serializable> Function<EntityManager, E> lockedWrappedTransformFunc(
            @Nonnull final Transfiguration<I, E> transfiguration) {

        return (entityManager) -> {
            synchronized (SaucedEntity.getEntityLock(transfiguration.key)) {
                return wrapTransaction(transformFunc(transfiguration))
                        .apply(entityManager);
            }
        };
    }

    /**
     * Wrap a transaction into begin and commit
     */
    @Nonnull
    @CheckReturnValue
    public static <E> Function<EntityManager, E> wrapTransaction(@Nonnull final Function<EntityManager, E> transaction) {
        return (entityManager) -> {
            entityManager.getTransaction().begin();
            final E result = transaction.apply(entityManager);
            entityManager.getTransaction().commit();
            return result;
        };
    }

    /**
     * Creates a transform function for the entity described by the provided key and the provided transformation.
     * When executing the function PersistenceExceptions may be thrown
     *
     * @param transfiguration Key of the entity to transform and Transformation to apply to the entity before saving it back
     * @return A function that will find an entity, apply the provided transformation, and merge it back.
     */
    @Nonnull
    @CheckReturnValue
    public <E extends SaucedEntity<I, E>, I extends Serializable> Function<EntityManager, E> transformFunc(
            @Nonnull final Transfiguration<I, E> transfiguration) {

        return (entityManager) -> findOrCreateFunc(transfiguration.key)
                .andThen(transfiguration.tf)
                .andThen(mergeFunc(transfiguration.key.clazz).apply(entityManager))
                .apply(entityManager);
    }

    /**
     * @return A function to find or create the entity described by the key.
     * The applied EntityManager needs to have an open transaction, and commit it some time afterwards.
     */
    @Nonnull
    @CheckReturnValue
    private <E extends SaucedEntity<I, E>, I extends Serializable> Function<EntityManager, E> findOrCreateFunc(@Nonnull final EntityKey<I, E> entityKey) {
        return (entityManager) -> {
            E entity = entityManager.find(entityKey.clazz, entityKey.id);
            if (entity == null) {
                entity = this.newInstance(entityKey);
            }
            return entity;
        };
    }

    /**
     * @return A merge function.
     * The applied EntityManager needs to have an open transaction, and commit it some time afterwards.
     */
    @Nonnull
    @CheckReturnValue
    private static <E> Function<EntityManager, Function<E, E>> mergeFunc(@SuppressWarnings("unused") final Class<E> clazz) {
        return (entityManager) -> entityManager::merge;
    }

    /**
     * Apply a transformation to all entities of a class
     */
    public <E extends SaucedEntity<I, E>, I extends Serializable> int applyAndMergeAll(@Nonnull final Class<E> clazz,
                                                                                       @Nonnull final Function<E, E> transformation)
            throws DatabaseException {
        return applyAndMergeAll("SELECT c FROM " + clazz.getSimpleName() + " c", false, clazz, transformation);
    }

    /**
     * Apply a transformation to all entities returned from a query
     * <p>
     * This is somewhat memory/resources efficient as it uses the stream api to retrieve and apply the transformation to
     * results
     *
     * @return the amount of entities that were returned by the query and the transformation applied to
     */
    public <E> int applyAndMergeAll(@Nonnull final String query,
                                    final boolean isNative,
                                    @Nonnull final Class<E> clazz,
                                    @Nonnull final Function<E, E> transformation)
            throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        final AtomicInteger i = new AtomicInteger(0);
        try {
            //take advantage of stream API for results which is part of Hibernate 5.2, and will come to JPA with 2.2
            //the disadvantage is that I havent come up with a correct way to use locks for this yet, as the stream
            //serves the entities without their ids, and doing an additional lookup afterwards sucks
            final SessionImpl session = em.unwrap(SessionImpl.class);
            session.getTransaction().begin();

            final QueryImplementor<E> q;
            if (isNative) {
                @SuppressWarnings("unchecked") final QueryImplementor<E> nq = session.createNativeQuery(query, clazz);
                q = nq;
            } else {
                q = session.createQuery(query, clazz);
            }
            q.stream().forEach((entity) -> {
                E e = entity;
                e = transformation.apply(e);
                session.merge(e);
                i.incrementAndGet();
            });

            session.getTransaction().commit();
            return i.get();
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to transform entities of clazz %s from query %s on DB %s",
                    clazz.getName(), query, this.databaseConnection.getName());
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
        deleteEntity(EntityKey.of(entity));
    }

    public <E extends IEntity<I, E>, I extends Serializable> void deleteEntity(@Nonnull final EntityKey<I, E> entityKey)
            throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            em.getTransaction().begin();
            final IEntity entity = em.find(entityKey.clazz, entityKey.id);
            if (entity != null) {
                em.remove(entity);
            }
            em.getTransaction().commit();
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to delete entity id %s of class %s on DB %s",
                    entityKey.id.toString(), entityKey.clazz.getName(), this.databaseConnection.getName());
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
     *
     * @return the number of entities updated or deleted
     */
    public int executeSqlQuery(@Nonnull final String queryString,
                               @Nullable final Map<String, Object> parameters) throws DatabaseException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            final Query q = em.createNativeQuery(queryString);
            if (parameters != null) {
                parameters.forEach(q::setParameter);
            }
            em.getTransaction().begin();
            int updated = q.executeUpdate();
            em.getTransaction().commit();
            return updated;
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
     *
     * @param resultEntityClass The result class needs to be an entity class, not a single property value like
     *                            java.lang.String for example. Use {@link DatabaseWrapper#selectSqlQuery(String, Map)}
     *                            for that instead.
     */
    @Nonnull
    @CheckReturnValue
    public <T> List<T> selectSqlQuery(@Nonnull final String queryString,
                                      @Nullable final Map<String, Object> parameters,
                                      @Nonnull final Class<T> resultEntityClass) throws DatabaseException {
        try {
            return selectSqlQuery((em) -> em.createNativeQuery(queryString, resultEntityClass), parameters);
        } catch (final PersistenceException | ClassCastException e) {
            final String message = String.format("Failed to select list result plain SQL query %s with %s parameters for class %s on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", resultEntityClass.getName(), this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        }
    }

    /**
     * Results will be sauced if they are SaucedEntites
     *
     * @param resultEntityMapping The result mapping needs to be for an entity class, not a single property value like
     *                            java.lang.String for example. Use {@link DatabaseWrapper#selectSqlQuery(String, Map)}
     *                            for that instead.
     */
    @Nonnull
    @CheckReturnValue
    public <T> List<T> selectSqlQuery(@Nonnull final String queryString,
                                      @Nullable final Map<String, Object> parameters,
                                      @Nonnull final String resultEntityMapping) throws DatabaseException {
        try {
            return selectSqlQuery((em) -> em.createNativeQuery(queryString, resultEntityMapping), parameters);
        } catch (final PersistenceException | ClassCastException e) {
            final String message = String.format("Failed to select list result plain SQL query %s with %s parameters for result mapping %s on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", resultEntityMapping, this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        }
    }

    /**
     * Results will be sauced if they are SaucedEntites
     * <p>
     * This method doesnt set any kind of result class so it can be used to retrieve Strings or Longs for example.
     */
    @Nonnull
    @CheckReturnValue
    public <T> List<T> selectSqlQuery(@Nonnull final String queryString,
                                      @Nullable final Map<String, Object> parameters) throws DatabaseException {
        try {
            return selectSqlQuery((em) -> em.createNativeQuery(queryString), parameters);
        } catch (final PersistenceException | ClassCastException e) {
            final String message = String.format("Failed to select list result plain SQL query %s with %s parameters on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", this.databaseConnection.getName());
            throw new DatabaseException(message, e);
        }
    }

    //callers of this should catch PersistenceExceptions and ClassCastExceptions and rethrow them as DatabaseExceptions
    @Nonnull
    @CheckReturnValue
    private <T> List<T> selectSqlQuery(@Nonnull final Function<EntityManager, Query> queryFunc,
                                       @Nullable final Map<String, Object> parameters)
            throws DatabaseException, PersistenceException, ClassCastException {
        final EntityManager em = this.databaseConnection.getEntityManager();
        try {
            final Query q = queryFunc.apply(em);
            if (parameters != null) {
                parameters.forEach(q::setParameter);
            }
            return selectNativeSqlQuery(em, q);
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
    private <E extends SaucedEntity<I, E>, I extends Serializable> E newInstance(@Nonnull final EntityKey<I, E> id) {
        return newInstance(this, id);
    }

    @Nonnull
    @CheckReturnValue
    private static <E extends SaucedEntity<I, E>, I extends Serializable> E newInstance(@Nonnull final DatabaseWrapper dbWrapper,
                                                                                        @Nonnull final EntityKey<I, E> id) {
        try {
            final E entity = id.clazz.getConstructor().newInstance();
            return entity.setId(id.id)
                    .setSauce(dbWrapper);
        } catch (final ReflectiveOperationException e) {
            final String message = String.format("Could not construct an entity of class %s with id %s",
                    id.clazz.getName(), id.toString());
            throw new RuntimeException(message, e);
        }
    }

    private <T> T setSauce(@Nullable final T t) {
        if (t instanceof SaucedEntity) {
            ((SaucedEntity) t).setSauce(this);
        }
        return t;
    }
}
