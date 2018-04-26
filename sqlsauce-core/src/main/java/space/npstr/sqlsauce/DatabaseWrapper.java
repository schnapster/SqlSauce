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
import org.hibernate.Transaction;
import org.hibernate.internal.SessionImpl;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.query.spi.QueryImplementor;
import space.npstr.sqlsauce.entities.IEntity;
import space.npstr.sqlsauce.entities.SaucedEntity;
import space.npstr.sqlsauce.fp.types.EntityKey;
import space.npstr.sqlsauce.fp.types.NonnullFunction;
import space.npstr.sqlsauce.fp.types.Transfiguration;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.io.Serializable;
import java.lang.reflect.Constructor;
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

    private final EntityManagerFactory emf;
    private final String name;

    /**
     * @param name
     *         a name to be used for logs
     */
    public DatabaseWrapper(EntityManagerFactory entityManagerFactory, String name) {
        this.emf = entityManagerFactory;
        this.name = name;
    }

    public DatabaseWrapper(DatabaseConnection connection) {
        this(connection.getEntityManagerFactory(), connection.getName());
    }

    public EntityManagerFactory getEntityManagerFactory() {
        return this.emf;
    }

    public String getName() {
        return name;
    }

    //################################################################################
    //                                   Reading
    //################################################################################

    /**
     * @return The returned entity is not necessarily a persisted one but may be a default constructed one.
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    @CheckReturnValue
    public <E extends SaucedEntity<I, E>, I extends Serializable> E getOrCreate(final EntityKey<I, E> entityKey) {
        final E entity = getEntity(entityKey);
        //return a fresh object if we didn't find the one we were looking for
        // no need to set the sauce as either getEntity or newInstance do that already
        return entity != null ? entity : newInstance(entityKey);
    }

    /**
     * @return An entity if it exists in the database or null if it doesn't exist. If the entity is a SaucedEntity the
     * sauce will be set.
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    @Nullable
    @CheckReturnValue
    public <E extends IEntity<I, E>, I extends Serializable> E getEntity(final EntityKey<I, E> entityKey) {
        try {
            @Nullable E result = executeNullableTransaction(em -> em.find(entityKey.clazz, entityKey.id));
            if (result != null) {
                result = setSauce(result);
            }
            return result;
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to find entity of class %s for id %s on DB %s",
                    entityKey.clazz.getName(), entityKey.id.toString(), name);
            throw new DatabaseException(message, e);
        }
    }

    /**
     * @return A list of all elements of the requested entity class
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    // NOTE: this method is probably not a great idea to use for giant tables
    @CheckReturnValue
    //returns a list of sauced entities
    public <E extends SaucedEntity<I, E>, I extends Serializable> List<E> loadAll(final Class<E> clazz) {
        final String query = "SELECT c FROM " + clazz.getSimpleName() + " c";
        try {
            return executeTransaction(em -> em.createQuery(query, clazz)
                    .getResultList())
                    .stream()
                    .map(s -> s.setSauce(this))
                    .collect(Collectors.toList());
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to load all %s entities on DB %s",
                    clazz.getName(), this.name);
            throw new DatabaseException(message, e);
        }
    }

    /**
     * @return The result list will be ordered by the order of the provided id list, but may contain null for unknown
     * entities
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    @CheckReturnValue
    //returns a list of sauced entities that may contain null elements
    public <E extends SaucedEntity<I, E>, I extends Serializable> List<E> getEntities(final List<I> ids,
                                                                                      final Class<E> clazz) {
        return getEntities(ids.stream()
                .map(id -> EntityKey.of(id, clazz))
                .collect(Collectors.toList()));
    }

    /**
     * @return The result list will be ordered by the order of the provided id list, but may contain null for unknown
     * entities
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    @CheckReturnValue
    //returns a list of sauced entities that may contain null elements
    public <E extends SaucedEntity<I, E>, I extends Serializable> List<E> getEntities(final List<EntityKey<I, E>> entityKeys) {
        if (entityKeys.isEmpty()) {
            return Collections.emptyList();
        }
        final Class<E> clazz = entityKeys.get(0).clazz;
        try {
            return executeTransaction(em -> em.unwrap(Session.class)
                    .byMultipleIds(clazz)
                    .multiLoad(entityKeys.stream().map(key -> key.id).collect(Collectors.toList())))
                    .stream()
                    .map(s -> s != null ? s.setSauce(this) : null)
                    .collect(Collectors.toList());
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to bulk load %s entities of class %s on DB %s",
                    entityKeys.size(), clazz.getName(), this.name);
            throw new DatabaseException(message, e);
        }
    }


    //################################################################################
    //                                  Writing
    //################################################################################

    /**
     * @return The managed version of the provided entity (with set autogenerated values for example).
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    @CheckReturnValue
    //returns a sauced entity
    public <E extends SaucedEntity<I, E>, I extends Serializable> E merge(final E entity) {
        try {
            synchronized (entity.getEntityLock()) {
                return executeTransaction(em -> em.merge(entity))
                        .setSauce(this);
            }
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to merge entity %s on DB %s",
                    entity.toString(), this.name);
            throw new DatabaseException(message, e);
        }
    }

    /**
     * The difference of persisting to merging is that persisting will throw an exception if the entity exists already.
     *
     * @return The managed version of the provided entity (with set autogenerated values for example).
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    @CheckReturnValue
    //returns whatever was passed in, with a sauce if it was a sauced entity
    public <E> E persist(final E entity) {
        try {
            return executeTransaction(em -> {
                em.persist(entity);
                return setSauce(entity);
            });
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to persist entity %s on DB %s",
                    entity.toString(), this.name);
            throw new DatabaseException(message, e);
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
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    public <E extends SaucedEntity<I, E>, I extends Serializable> E findApplyAndMerge(final Transfiguration<I, E> transfiguration) {
        final EntityManager em = this.emf.createEntityManager();
        try {
            return this.lockedWrappedTransformFunc(transfiguration).apply(em);
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to find, apply and merge entity id %s of class %s on DB %s",
                    transfiguration.key.id.toString(), transfiguration.key.clazz.getName(),
                    this.name);
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    /**
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    //key + transformation -> transfiguration
    public <E extends SaucedEntity<I, E>, I extends Serializable> E findApplyAndMerge(final EntityKey<I, E> entityKey,
                                                                                      final Function<E, E> transformation) {
        return findApplyAndMerge(Transfiguration.of(entityKey, transformation));
    }


    /**
     * A bulk method for applying transformations to a stream of data.
     *
     * @return Exceptions thrown while processing the input stream
     */
    public <E extends SaucedEntity<I, E>, I extends Serializable> List<DatabaseException> findApplyAndMergeAll(
            final Stream<Transfiguration<I, E>> transfigurations) {
        final List<DatabaseException> exceptions = new ArrayList<>();

        transfigurations.forEach(transfiguration -> {
            try {
                final EntityManager em = this.emf.createEntityManager();
                try {
                    this.lockedWrappedTransformFunc(transfiguration).apply(em);
                } catch (final PersistenceException e) {
                    final String message = String.format("Failed to find, apply and merge entity id %s of class %s on DB %s",
                            transfiguration.key.id.toString(), transfiguration.key.clazz.getName(),
                            this.name);
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
    @CheckReturnValue
    public <E extends SaucedEntity<I, E>, I extends Serializable> Function<EntityManager, E> lockedWrappedTransformFunc(
            final Transfiguration<I, E> transfiguration) {

        return entityManager -> {
            synchronized (SaucedEntity.getEntityLock(transfiguration.key)) {
                return wrapTransaction(transformFunc(transfiguration))
                        .apply(entityManager);
            }
        };
    }

    /**
     * Wrap a transaction into begin and commit
     */
    @CheckReturnValue
    public static <E> Function<EntityManager, E> wrapTransaction(final Function<EntityManager, E> transaction) {
        return entityManager -> {
            EntityTransaction entityTransaction = entityManager.getTransaction();
            try {
                entityTransaction.begin();
                final E result = transaction.apply(entityManager);
                entityTransaction.commit();
                return result;
            } finally {
                if (entityTransaction.isActive()) {
                    entityTransaction.rollback();
                }
            }
        };
    }

    /**
     * Creates a transform function for the entity described by the provided key and the provided transformation.
     * When executing the function PersistenceExceptions may be thrown
     *
     * @param transfiguration
     *         Key of the entity to transform and Transformation to apply to the entity before saving it back
     *
     * @return A function that will find an entity, apply the provided transformation, and merge it back.
     */
    @CheckReturnValue
    public <E extends SaucedEntity<I, E>, I extends Serializable> Function<EntityManager, E> transformFunc(
            final Transfiguration<I, E> transfiguration) {

        return entityManager -> findOrCreateFunc(transfiguration.key)
                .andThen(transfiguration.tf)
                .andThen(mergeFunc(transfiguration.key.clazz).apply(entityManager))
                .apply(entityManager);
    }

    /**
     * @return A function to find or create the entity described by the key.
     * The applied EntityManager needs to have an open transaction, and commit it some time afterwards.
     */
    @CheckReturnValue
    private <E extends SaucedEntity<I, E>, I extends Serializable> Function<EntityManager, E> findOrCreateFunc(final EntityKey<I, E> entityKey) {
        return entityManager -> {
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
    @CheckReturnValue
    private static <E> Function<EntityManager, Function<E, E>> mergeFunc(@SuppressWarnings("unused") final Class<E> clazz) {
        return entityManager -> entityManager::merge;
    }

    /**
     * Apply a transformation to all entities of a class
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    public <E extends SaucedEntity<I, E>, I extends Serializable> int applyAndMergeAll(final Class<E> clazz,
                                                                                       final Function<E, E> transformation) {
        return applyAndMergeAll("SELECT c FROM " + clazz.getSimpleName() + " c", false, clazz, transformation);
    }

    /**
     * Apply a transformation to all entities returned from a query
     * <p>
     * This is somewhat memory/resources efficient as it uses the stream api to retrieve and apply the transformation to
     * results
     *
     * @return the amount of entities that were returned by the query and the transformation applied to
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    public <E> int applyAndMergeAll(final String query, final boolean isNative, final Class<E> clazz,
                                    final Function<E, E> transformation) {
        final EntityManager em = this.emf.createEntityManager();
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
            q.stream().forEach(entity -> {
                E e = entity;
                e = transformation.apply(e);
                session.merge(e);
                i.incrementAndGet();
            });

            session.getTransaction().commit();
            return i.get();
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to transform entities of clazz %s from query %s on DB %s",
                    clazz.getName(), query, this.name);
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    //################################################################################
    //                                 Deleting
    //################################################################################

    /**
     * Delete an entity.
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    public <E extends IEntity<I, E>, I extends Serializable> void deleteEntity(final E entity) {
        deleteEntity(EntityKey.of(entity));
    }

    /**
     * @return may return the looked up entity if it was not null
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    @Nullable
    public <E extends IEntity<I, E>, I extends Serializable> E deleteEntity(final EntityKey<I, E> entityKey) {
        try {
            return executeNullableTransaction(em -> {
                final E entity = em.find(entityKey.clazz, entityKey.id);
                if (entity != null) {
                    em.remove(entity);
                }
                return entity;
            });
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to delete entity id %s of class %s on DB %s",
                    entityKey.id.toString(), entityKey.clazz.getName(), this.name);
            throw new DatabaseException(message, e);
        }
    }

    //todo add batch deleting methods


    //################################################################################
    //                                 JPQL stuff
    //################################################################################

    /**
     * @return the number of entities updated or deleted
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    public int executeJpqlQuery(final String queryString, @Nullable final Map<String, Object> parameters) {
        try {
            return executeTransaction(em -> {
                final Query query = em.createQuery(queryString);
                if (parameters != null) {
                    parameters.forEach(query::setParameter);
                }
                return query.executeUpdate();
            });
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to execute JPQL query %s with %s parameters on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", this.name);
            throw new DatabaseException(message, e);
        }
    }

    /**
     * Use this for COUNT() and similar jpql queries which are guaranteed to return a result
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    @CheckReturnValue
    public <T> T selectJpqlQuerySingleResult(final String queryString, @Nullable final Map<String, Object> parameters,
                                             final Class<T> resultClass) {
        try {
            return executeTransaction(em -> {
                final Query q = em.createQuery(queryString);
                if (parameters != null) {
                    parameters.forEach(q::setParameter);
                }
                return setSauce(resultClass.cast(q.getSingleResult()));
            });
        } catch (final PersistenceException | ClassCastException e) {
            final String message = String.format("Failed to select single result JPQL query %s with %s parameters for class %s on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", resultClass.getName(), this.name);
            throw new DatabaseException(message, e);
        }
    }

    /**
     * Results will be sauced if they are SaucedEntites
     *
     * @param queryString
     *         the raw JPQL query string
     * @param parameters
     *         parameters to be set on the query
     * @param resultClass
     *         expected class of the results of the query
     * @param offset
     *         set to -1 or lower for no offset
     * @param limit
     *         set to -1 or lower for no limit
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    //limited and offset results
    @CheckReturnValue
    public <T> List<T> selectJpqlQuery(final String queryString, @Nullable final Map<String, Object> parameters,
                                       final Class<T> resultClass, final int offset, final int limit) {
        try {
            return executeTransaction(em -> {
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

                return q.getResultList().stream()
                        .peek(this::setSauce)
                        .collect(Collectors.toList());
            });
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to select JPQL query %s with %s parameters, offset %s, limit %s, on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", offset, limit, this.name);
            throw new DatabaseException(message, e);
        }
    }

    /**
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    //limited results without offset
    @CheckReturnValue
    public <T> List<T> selectJpqlQuery(final String queryString, @Nullable final Map<String, Object> parameters,
                                       final Class<T> resultClass, final int limit) {
        return selectJpqlQuery(queryString, parameters, resultClass, -1, limit);
    }

    /**
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    //limited results without offset
    @CheckReturnValue
    public <T> List<T> selectJpqlQuery(final String queryString, final Class<T> resultClass, final int limit) {
        return selectJpqlQuery(queryString, null, resultClass, -1, limit);
    }

    /**
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    //no limit and no offset
    @CheckReturnValue
    public <T> List<T> selectJpqlQuery(final String queryString, @Nullable final Map<String, Object> parameters,
                                       final Class<T> resultClass) {
        return selectJpqlQuery(queryString, parameters, resultClass, -1);
    }

    /**
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    //no limit and no offset
    @CheckReturnValue
    public <T> List<T> selectJpqlQuery(final String queryString, final Class<T> resultClass) {
        return selectJpqlQuery(queryString, null, resultClass, -1);
    }

    //################################################################################
    //                              Plain SQL stuff
    //################################################################################


    /**
     * Run a good old SQL query
     *
     * @return the number of entities updated or deleted
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    public int executeSqlQuery(final String queryString, @Nullable final Map<String, Object> parameters) {
        try {
            return executeTransaction(em -> {
                final Query q = em.createNativeQuery(queryString);
                if (parameters != null) {
                    parameters.forEach(q::setParameter);
                }
                return q.executeUpdate();
            });
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to execute plain SQL query %s with %s parameters on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", this.name);
            throw new DatabaseException(message, e);
        }
    }

    /**
     * Run a good old SQL query
     *
     * @return the number of entities updated or deleted
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    public int executeSqlQuery(final String queryString) {
        return executeSqlQuery(queryString, null);
    }

    /**
     * Results will be sauced if they are SaucedEntites
     *
     * @param resultEntityClass
     *         The result class needs to be an entity class, not a single property value like
     *         java.lang.String for example. Use {@link DatabaseWrapper#selectSqlQuery(String, Map)}
     *         for that instead.
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    @CheckReturnValue
    public <T> List<T> selectSqlQuery(final String queryString, @Nullable final Map<String, Object> parameters,
                                      final Class<T> resultEntityClass) {
        try {
            return selectSqlQuery(em -> em.createNativeQuery(queryString, resultEntityClass), parameters);
        } catch (final PersistenceException | ClassCastException e) {
            final String message = String.format("Failed to select list result plain SQL query %s with %s parameters for class %s on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", resultEntityClass.getName(), this.name);
            throw new DatabaseException(message, e);
        }
    }

    /**
     * Results will be sauced if they are SaucedEntites
     *
     * @param resultEntityMapping
     *         The result mapping needs to be for an entity class, not a single property value like
     *         java.lang.String for example. Use {@link DatabaseWrapper#selectSqlQuery(String, Map)}
     *         for that instead.
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    @CheckReturnValue
    public <T> List<T> selectSqlQuery(final String queryString, @Nullable final Map<String, Object> parameters,
                                      final String resultEntityMapping) {
        try {
            return selectSqlQuery(em -> em.createNativeQuery(queryString, resultEntityMapping), parameters);
        } catch (final PersistenceException | ClassCastException e) {
            final String message = String.format("Failed to select list result plain SQL query %s with %s parameters for result mapping %s on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", resultEntityMapping, this.name);
            throw new DatabaseException(message, e);
        }
    }

    /**
     * Results will be sauced if they are SaucedEntites
     * <p>
     * This method doesnt set any kind of result class so it can be used to retrieve Strings or Longs for example.
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    @CheckReturnValue
    public <T> List<T> selectSqlQuery(final String queryString, @Nullable final Map<String, Object> parameters) {
        try {
            return selectSqlQuery(em -> em.createNativeQuery(queryString), parameters);
        } catch (final PersistenceException | ClassCastException e) {
            final String message = String.format("Failed to select list result plain SQL query %s with %s parameters on DB %s",
                    queryString, parameters != null ? parameters.size() : "null", this.name);
            throw new DatabaseException(message, e);
        }
    }

    /**
     * Callers should catch these and wrap them in {@link DatabaseException}s:     *
     *
     * @throws DatabaseException
     *         bubble it up
     * @throws PersistenceException
     *         if anything was wrong with the query / db
     * @throws ClassCastException
     *         we got a wrong class
     */
    //remember to close the provided EntityManager and manage the transaction, as well as catch any exceptions
    //Results will be sauced if they are SaucedEntites
    //callers of this should catch PersistenceExceptions and ClassCastExceptions and rethrow them as DatabaseExceptions
    @CheckReturnValue
    private <T> List<T> selectSqlQuery(final Function<EntityManager, Query> queryFunc,
                                       @Nullable final Map<String, Object> parameters) {
        return executeTransaction(em -> {
            final Query q = queryFunc.apply(em);
            if (parameters != null) {
                parameters.forEach(q::setParameter);
            }
            return selectNativeSqlQuery(q);
        });
    }

    @CheckReturnValue
    @SuppressWarnings("unchecked")
    private <T> List<T> selectNativeSqlQuery(final Query query) {
        return (List<T>) query.getResultList().stream()
                .peek(this::setSauce)
                .collect(Collectors.toList());
    }

    /**
     * Use this for COUNT() and similar sql queries which are guaranteed to return a result
     *
     * @throws DatabaseException
     *         Wraps any {@link PersistenceException} that may be thrown.
     */
    @CheckReturnValue
    public <T> T selectSqlQuerySingleResult(final String queryString, @Nullable final Map<String, Object> parameters,
                                            final Class<T> resultClass) {
        final EntityManager em = this.emf.createEntityManager();
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
                    queryString, parameters != null ? parameters.size() : "null", resultClass.getName(), this.name);
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }

    //################################################################################
    //                                  NOTIFY
    //################################################################################

    /**
     * Send a notifications with Postgres' LISTEN/NOTIFY feature.
     * See https://www.postgresql.org/docs/current/static/sql-notify.html for more info.
     * <p>
     * See the notification module for a listener implementation
     */
    public void notif(String channel, @Nullable String payload) {
        final EntityManager em = this.emf.createEntityManager();
        try {
            em.getTransaction().begin();
            //the cast is necessary otherwise hibernate chokes on the void return type
            //noinspection SqlResolve
            String sql = "SELECT cast(pg_notify(:channel, :payload) AS TEXT);";
            em.createNativeQuery(sql)
                    .setParameter("channel", channel)
                    .setParameter("payload", payload != null ? payload : "")
                    .getSingleResult();
            em.getTransaction().commit();
        } catch (final PersistenceException e) {
            final String message = String.format("Failed to execute notification for channel %s with payload %s on DB %s",
                    channel, payload, this.name);
            throw new DatabaseException(message, e);
        } finally {
            em.close();
        }
    }


    //################################################################################
    //                                  Internals
    //################################################################################

    private <R> R executeTransaction(NonnullFunction<EntityManager, R> closure) {
        //noinspection ConstantConditions
        return executeNullableTransaction(closure);
    }

    @Nullable
    private <R> R executeNullableTransaction(Function<EntityManager, R> closure) {
        Transaction transaction = null;
        try (Session entityManager = emf.createEntityManager().unwrap(Session.class)) {
            transaction = entityManager.getTransaction();
            transaction.begin();
            R result = closure.apply(entityManager);
            transaction.commit();
            return result;
        } finally {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
        }
    }

    //IEntities are required to have a default constructor that sets them up with sensible defaults
    @CheckReturnValue
    //returns a sauced entity
    private <E extends SaucedEntity<I, E>, I extends Serializable> E newInstance(final EntityKey<I, E> id) {
        return newInstance(this, id);
    }

    @CheckReturnValue
    private static <E extends SaucedEntity<I, E>, I extends Serializable> E newInstance(final DatabaseWrapper dbWrapper,
                                                                                        final EntityKey<I, E> id) {
        try {
            Constructor<E> constructor = ReflectHelper.getDefaultConstructor(id.clazz);
            final E entity = constructor.newInstance((Object[]) null);
            return entity.setId(id.id)
                    .setSauce(dbWrapper);
        } catch (final ReflectiveOperationException e) {
            final String message = String.format("Could not construct an entity of class %s with id %s",
                    id.clazz.getName(), id.toString());
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
