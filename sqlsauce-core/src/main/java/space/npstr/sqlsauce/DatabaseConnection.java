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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.prometheus.client.hibernate.HibernateStatisticsCollector;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.flywaydb.core.Flyway;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.sqlsauce.entities.SaucedEntity;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by napster on 29.05.17.
 */
public class DatabaseConnection {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);
    private static final String TEST_QUERY = "SELECT 1;";
    private static final long DEFAULT_HEALTHCHECK_PERIOD = TimeUnit.SECONDS.toNanos(5);

    private final EntityManagerFactory emf;
    private final HikariDataSource hikariDataSource;
    @Nullable
    private final ProxyDataSource proxiedDataSource;

    private final String connectionName; //a comprehensible name for this connection

    private volatile DatabaseState state;

    @Nullable
    private final ScheduledExecutorService connectionCheck;

    /**
     * @param connectionName   name for this database connection, also used as the persistence unit name and other
     *                         places. Make sure it is unique across your application for best results.
     * @param jdbcUrl          where to find the db, which user, which pw, etc; see easy to find jdbc url docs on the web
     * @param dataSourceProps  properties for the underlying data source. see {@link Builder#getDefaultDataSourceProps()} for a start
     * @param hikariConfig     config for hikari. see {@link Builder#getDefaultHikariConfig()} ()} for a start
     * @param hibernateProps   properties for hibernate. see {@link Builder#getDefaultHibernateProps()} ()} for a start
     * @param entityPackages   example: "space.npstr.wolfia.db.entity", the names of the packages containing your
     *                         annotated entities. root package names are fine, they will pick up all children
     * @param poolName         optional name for the connection pool; if null, a default name based on the db name will be picked
     * @param hibernateStats   optional metrics for hibernate. make sure to register it after adding all connections to it
     * @param hikariStats      optional metrics for hikari
     * @param checkConnection  set to false to disable a periodic healthcheck and automatic reconnection of the connection.
     *                         only recommended if you run your own healthcheck and reconnection logic
     * @param healthCheckPeriod (nanos) period between health checks
     * @param proxyDataSourceBuilder optional datasource proxy that is useful for logging and intercepting queries, see
     *                               https://github.com/ttddyy/datasource-proxy. The hikari datasource will be set on it,
     *                               and the resulting proxy will be passed to hibernate.
     * @param flyway           optional Flyway migrations. This constructor will call Flyway#setDataSource and
     *                         Flyway#migrate() after creating the hikari datasource, and before handing
     *                         the datasource over to the datasource proxy and hibernate. If you need tighter control
     *                         over the handling of migrations, consider running them manually before creating the
     *                         DatabaseConnection, Flyway supports the use of a jdbcUrl instead of a datasource.
     */
    public DatabaseConnection(final String connectionName,
                              final String jdbcUrl,
                              final Properties dataSourceProps,
                              final HikariConfig hikariConfig,
                              final Properties hibernateProps,
                              final Collection<String> entityPackages,
                              EntityManagerFactoryBuilder entityManagerFactoryBuilder,
                              @Nullable final String poolName,
                              @Nullable final MetricsTrackerFactory hikariStats,
                              @Nullable final HibernateStatisticsCollector hibernateStats,
                              final boolean checkConnection,
                              long healthCheckPeriod,
                              @Nullable final ProxyDataSourceBuilder proxyDataSourceBuilder,
                              @Nullable final Flyway flyway) throws DatabaseException {
        this.connectionName = connectionName;
        this.state = DatabaseState.INITIALIZING;

        try {
            // hikari connection pool
            final HikariConfig hiConf = new HikariConfig();
            hikariConfig.copyStateTo(hiConf); //dont touch the provided config, it might get reused outside, instead use a copy
            hiConf.setJdbcUrl(jdbcUrl);
            if (poolName != null && !poolName.isEmpty()) {
                hiConf.setPoolName(poolName);
            } else {
                hiConf.setPoolName(connectionName + "-DefaultPool");
            }
            if (hikariStats != null) {
                hiConf.setMetricsTrackerFactory(hikariStats);
            }
            hiConf.setDataSourceProperties(dataSourceProps);
            this.hikariDataSource = new HikariDataSource(hiConf);

            if (flyway != null) {
                flyway.setDataSource(hikariDataSource);
                flyway.migrate();
            }

            //proxy the datasource
            DataSource dataSource;
            if (proxyDataSourceBuilder != null) {
                proxiedDataSource = proxyDataSourceBuilder
                        .dataSource(hikariDataSource)
                        .build();
                dataSource = proxiedDataSource;
            } else {
                proxiedDataSource = null;
                dataSource = hikariDataSource;
            }

            //add entities provided by this lib
            entityPackages.add("space.npstr.sqlsauce.entities");

            // hibernate
            if (hibernateStats != null) {
                hibernateProps.put("hibernate.generate_statistics", "true");
            }

            this.emf = entityManagerFactoryBuilder.build(connectionName, dataSource, hibernateProps, entityPackages);
            if (hibernateStats != null) {
                hibernateStats.add(this.emf.unwrap(SessionFactoryImpl.class), connectionName);
            }

            this.state = DatabaseState.READY;
            if (checkConnection) {
                this.connectionCheck = Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "db-connection-check-" + connectionName);
                            thread.setUncaughtExceptionHandler((t, e) -> log.error("Uncaught exception in connection checker thread {}", t.getName(), e));
                            return thread;
                        }
                );
                this.connectionCheck.scheduleAtFixedRate(this::healthCheck, healthCheckPeriod, healthCheckPeriod, TimeUnit.NANOSECONDS);
            } else {
                this.connectionCheck = null;
            }
            SaucedEntity.setDefaultSauce(new DatabaseWrapper(this));
        } catch (final Exception e) {
            this.state = DatabaseState.FAILED;
            throw new DatabaseException("Failed to create database connection", e);
        }
    }

    @CheckReturnValue
    public String getName() {
        return this.connectionName;
    }
    
    public int getMaxPoolSize() {
        return hikariDataSource.getMaximumPoolSize();
    }

    public EntityManagerFactory getEntityManagerFactory() throws IllegalStateException {
        if (this.state == DatabaseState.SHUTDOWN) {
            throw new IllegalStateException("Database connection has been shutdown.");
        }
        return this.emf;
    }

    @CheckReturnValue
    public EntityManager getEntityManager() throws IllegalStateException, DatabaseException {
        if (this.state == DatabaseState.SHUTDOWN) {
            throw new IllegalStateException("Database connection has been shutdown.");
        } else if (this.state != DatabaseState.READY) {
            throw new DatabaseException("Database connection is not available.");
        }
        return this.emf.createEntityManager();
    }

    public DataSource getDataSource() {
        if (proxiedDataSource != null) {
            return proxiedDataSource;
        } else {
            return hikariDataSource;
        }
    }

    public void shutdown() {
        if (connectionCheck != null) {
            this.connectionCheck.shutdown();
            try {
                this.connectionCheck.awaitTermination(30, TimeUnit.SECONDS);
            } catch (final InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        this.state = DatabaseState.SHUTDOWN;
        this.emf.close();
        this.hikariDataSource.close();
    }

    /**
     * @return true if the database is operational, false if not
     */
    @CheckReturnValue
    public boolean isAvailable() {
        return this.state == DatabaseState.READY;
    }

    /**
     * Perform a health check and adjust the state of the connection accordingly.
     * <p>
     * The default configuration of the database connection calls this proactively every few seconds.
     *
     * The main benefit of this is fail-fast behaviour of this object, as calls to {@link DatabaseConnection#getEntityManager()}
     * will throw an exception if the state is not {@link DatabaseConnection.DatabaseState#READY}.
     *
     * @return true if the database is healthy, false otherwise. Will return false if the database is shutdown, but not
     * attempt to restart/reconnect it.
     */
    @SuppressFBWarnings("IS")
    public synchronized boolean healthCheck() {
        if (this.state == DatabaseState.SHUTDOWN) {
            return false;
        }

        boolean testQuerySuccess = false;
        try {
            testQuerySuccess = runTestQuery();
        } catch (Exception e) {
            log.error("Test query failed", e);
        }

        if (testQuerySuccess) {
            this.state = DatabaseState.READY;
            return true;
        } else {
            this.state = DatabaseState.FAILED;
            return false;
        }
    }

    /**
     * @return true if the test query was successful and false if not
     */
    @CheckReturnValue
    public boolean runTestQuery() {
        final EntityManager em = this.emf.createEntityManager();
        try {
            final EntityTransaction entityTransaction = em.getTransaction();
            try {
                entityTransaction.begin();
                em.createNativeQuery(TEST_QUERY).getResultList();
                entityTransaction.commit();
                return true;
            } finally {
                if (entityTransaction.isActive()) {
                    entityTransaction.rollback();
                }
            }
        } catch (final PersistenceException e) {
            log.error("Test query failed", e);
            return false;
        } finally {
            em.close();
        }
    }

    public enum DatabaseState {
        INITIALIZING,
        FAILED,
        READY,
        SHUTDOWN
    }

    //builder pattern, duh
    public static class Builder {

        private String connectionName;
        private String jdbcUrl;
        private Properties dataSourceProps = getDefaultDataSourceProps();
        private HikariConfig hikariConfig = getDefaultHikariConfig();
        private Properties hibernateProps = getDefaultHibernateProps();
        private Collection<String> entityPackages = new ArrayList<>();
        private EntityManagerFactoryBuilder entityManagerFactoryBuilder
                = (puName, dataSource, properties, entityPackages) -> {
            final PersistenceUnitInfo puInfo = new SimplePersistenceUnitInfo(dataSource, entityPackages, puName);
            return new HibernatePersistenceProvider().createContainerEntityManagerFactory(puInfo, properties);
        };
        @Nullable
        private String poolName;
        @Nullable
        private HibernateStatisticsCollector hibernateStats;
        @Nullable
        private MetricsTrackerFactory hikariStats;
        private boolean checkConnection = true;
        private long healthcheckPeriod = DEFAULT_HEALTHCHECK_PERIOD;
        @Nullable
        private ProxyDataSourceBuilder proxyDataSourceBuilder;
        @Nullable
        private Flyway flyway;


        public static Properties getDefaultDataSourceProps() {
            final Properties dataSourceProps = new Properties();

            // allow postgres to cast strings (varchars) more freely to actual column types
            // source https://jdbc.postgresql.org/documentation/head/connect.html
            dataSourceProps.setProperty("stringtype", "unspecified");

            return dataSourceProps;
        }

        public static HikariConfig getDefaultHikariConfig() {
            final HikariConfig hikariConfig = new HikariConfig();

            //more database connections don't help with performance, so use a default value based on available cores
            //http://www.dailymotion.com/video/x2s8uec_oltp-performance-concurrent-mid-tier-connections_tech
            hikariConfig.setMaximumPoolSize(Math.max(Runtime.getRuntime().availableProcessors(), 4));

            //timeout the validation query (will be done automatically through Connection.isValid())
            hikariConfig.setValidationTimeout(3000);
            hikariConfig.setConnectionTimeout(10000);
            hikariConfig.setAutoCommit(false);

            hikariConfig.setDriverClassName("org.postgresql.Driver");

            return hikariConfig;
        }

        public static Properties getDefaultHibernateProps() {
            final Properties hibernateProps = new Properties();

            //validate entities vs existing tables
            //For more info see:
            // https://docs.jboss.org/hibernate/orm/5.2/userguide/html_single/Hibernate_User_Guide.html#configurations-hbmddl
            //Interesting options:
            //Set to "update" to have hibernate autogenerate and migrate tables for you (dangerous and requires proper
            // care, probably a bad idea for serious projects in production, use flyway or something else for migrations instead)
            //Set to "none" to disable completely
            hibernateProps.put("hibernate.hbm2ddl.auto", "validate");

            //pl0x no log spam
            //NOTE: despite those logs turned off, hibernate still spams tons of debug logs, so you really want to turn
            // those completely off in the slf4j implementation you are using
            hibernateProps.put("hibernate.show_sql", "false");
            hibernateProps.put("hibernate.session.events.log", "false");
            //dont generate statistics; this will be overridden to true if a HibernateStatisticsCollector is provided
            hibernateProps.put("hibernate.generate_statistics", "false");

            //sane batch sizes
            hibernateProps.put("hibernate.default_batch_fetch_size", 100);
            hibernateProps.put("hibernate.jdbc.batch_size", 100);

            //disable autocommit, it is not recommended for our use cases, and interferes with some of them
            // see https://vladmihalcea.com/2017/05/17/why-you-should-always-use-hibernate-connection-provider_disables_autocommit-for-resource-local-jpa-transactions/
            // this also means all EntityManager interactions need to be wrapped into em.getTransaction.begin() and
            // em.getTransaction.commit() to prevent a rollback spam at the database
            hibernateProps.put("hibernate.connection.autocommit", "false");
            hibernateProps.put("hibernate.connection.provider_disables_autocommit", "true");


            return hibernateProps;
        }


        // absolute minimum needed config

        /**
         * Give this database connection a name - preferably unique inside your application.
         */
        @CheckReturnValue
        public Builder(final String connectionName, final String jdbcUrl) {
            this.connectionName = connectionName;
            this.jdbcUrl = jdbcUrl;
        }

        /**
         * Give this database connection a name - preferably unique inside your application.
         */
        @CheckReturnValue
        public Builder setConnectionName(final String connectionName) {
            this.connectionName = connectionName;
            return this;
        }

        @CheckReturnValue
        public Builder setJdbcUrl(final String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
            return this;
        }


        // datasource stuff

        /**
         * Set your own DataSource props. By default, the builder populates the DataSource properties with
         * {@link Builder#getDefaultDataSourceProps()}, which can be overriden using this method. Use
         * {@link Builder#setDataSourceProperty(Object, Object)} if you want to add a single property.
         */
        @CheckReturnValue
        public Builder setDataSourceProps(final Properties props) {
            this.dataSourceProps = props;
            return this;
        }

        @CheckReturnValue
        public Builder setDataSourceProperty(final Object key, final Object value) {
            this.dataSourceProps.put(key, value);
            return this;
        }

        /**
         * Name that the connections will show up with in db management tools
         */
        @CheckReturnValue
        public Builder setAppName(final String appName) {
            this.dataSourceProps.setProperty("ApplicationName", appName);
            return this;
        }


        //hikari stuff

        /**
         * Set your own HikariDataSource. By default, the builder populates the HikariDataSource properties with
         * {@link Builder#getDefaultHikariConfig()} ()} ()}, which can be overriden using this method.
         */
        @CheckReturnValue
        public Builder setHikariConfig(final HikariConfig hikariConfig) {
            this.hikariConfig = hikariConfig;
            return this;
        }

        /**
         * Name of the Hikari pool. Should be unique across your application. If you don't set one or set a null one
         * a default pool name based on the database name will be picked.
         */
        @CheckReturnValue
        public Builder setPoolName(@Nullable final String poolName) {
            this.poolName = poolName;
            return this;
        }


        //hibernate stuff

        /**
         * Set your own Hibernate props. By default, the builder populates the Hibernate properties with
         * {@link Builder#getDefaultHibernateProps()}, which can be overriden using this method. Use
         * {@link Builder#setHibernateProperty(Object, Object)} if you want to add a single property.
         */
        @CheckReturnValue
        public Builder setHibernateProps(final Properties props) {
            this.hibernateProps = props;
            return this;
        }

        @CheckReturnValue
        public Builder setHibernateProperty(final Object key, final Object value) {
            this.hibernateProps.put(key, value);
            return this;
        }

        /**
         * Set the name of the dialect to be used, as sometimes auto detection is off (or you want to use a custom one)
         * Example: "org.hibernate.dialect.PostgreSQL95Dialect"
         */
        @CheckReturnValue
        public Builder setDialect(final String dialect) {
            return setHibernateProperty("hibernate.dialect", dialect);
        }

        @CheckReturnValue
        public Builder setEntityPackages(final Collection<String> entityPackages) {
            this.entityPackages = entityPackages;
            return this;
        }

        /**
         * Define how the EntityManagerFactory is built. See {@link EntityManagerFactoryBuilder}.
         */
        @CheckReturnValue
        public Builder setEntityManagerFactoryBuilder(final EntityManagerFactoryBuilder entityManagerFactoryBuilder) {
            this.entityManagerFactoryBuilder = entityManagerFactoryBuilder;
            return this;
        }

        /**
         * Add all packages of your application that contain entities that you want to use with this connection.
         * Example: "com.example.yourorg.yourproject.db.entities"
         */
        @CheckReturnValue
        public Builder addEntityPackage(final String entityPackage) {
            this.entityPackages.add(entityPackage);
            return this;
        }


        // metrics stuff

        @CheckReturnValue
        public Builder setHikariStats(@Nullable final MetricsTrackerFactory hikariStats) {
            this.hikariStats = hikariStats;
            return this;
        }

        /**
         * Providing a HibernateStatisticsCollector will also enable Hibernate statistics equivalent to
         * <p>
         * hibernateProps.put("hibernate.generate_statistics", "true");
         * <p>
         * for the resulting DatabaseConnection.
         */
        @CheckReturnValue
        public Builder setHibernateStats(@Nullable final HibernateStatisticsCollector hibernateStats) {
            this.hibernateStats = hibernateStats;
            return this;
        }


        //migrations
        @CheckReturnValue
        public Builder setFlyway(@Nullable final Flyway flyway) {
            this.flyway = flyway;
            return this;
        }

        //misc
        @CheckReturnValue
        public Builder setCheckConnection(final boolean checkConnection) {
            this.checkConnection = checkConnection;
            return this;
        }

        /**
         * Set to some positive value. Default is 5 seconds. Can be disabled by {@link Builder#setCheckConnection(boolean)}
         */
        @CheckReturnValue
        public Builder setHealthCheckPeriod(final long healthCheckPeriod, TimeUnit timeUnit) {
            this.healthcheckPeriod = timeUnit.toNanos(healthCheckPeriod);
            return this;
        }

        @CheckReturnValue
        public Builder setProxyDataSourceBuilder(@Nullable final ProxyDataSourceBuilder proxyBuilder) {
            this.proxyDataSourceBuilder = proxyBuilder;
            return this;
        }

        @CheckReturnValue
        public DatabaseConnection build() throws DatabaseException {
            return new DatabaseConnection(
                    this.connectionName,
                    this.jdbcUrl,
                    this.dataSourceProps,
                    this.hikariConfig,
                    this.hibernateProps,
                    this.entityPackages,
                    this.entityManagerFactoryBuilder,
                    this.poolName,
                    this.hikariStats,
                    this.hibernateStats,
                    this.checkConnection,
                    this.healthcheckPeriod,
                    this.proxyDataSourceBuilder,
                    this.flyway
            );
        }
    }

    @FunctionalInterface
    public interface EntityManagerFactoryBuilder {

        EntityManagerFactory build(String persistenceUnitName,
                                   DataSource dataSource,
                                   Properties hibernateProperties,
                                   Collection<String> packagesToScan);

    }
}
