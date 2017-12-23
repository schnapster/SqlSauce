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
import io.prometheus.client.hibernate.HibernateStatisticsCollector;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.sqlsauce.entities.SaucedEntity;
import space.npstr.sqlsauce.migration.Migration;
import space.npstr.sqlsauce.migration.Migrations;
import space.npstr.sqlsauce.ssh.SshTunnel;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;
import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by napster on 29.05.17.
 * <p>
 * Connection to a database through an optional SSH tunnel
 */
public class DatabaseConnection {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);
    private static final String TEST_QUERY = "SELECT 1;";

    @Nonnull
    private final EntityManagerFactory emf;
    @Nonnull
    private final HikariDataSource hikariDataSource;
    @Nullable
    private final ProxyDataSource proxiedDataSource;

    @Nullable
    private SshTunnel sshTunnel = null;

    @Nonnull
    private final String dbName; //a comprehensible name for this connection

    @Nonnull
    private volatile DatabaseState state = DatabaseState.UNINITIALIZED;

    private final ScheduledExecutorService connectionCheck = Executors.newSingleThreadScheduledExecutor();

    /**
     * @param dbName           name for this database connection, also used as the persistence unit name
     * @param jdbcUrl          where to find the db, which user, which pw, etc; see easy to find jdbc url docs on the web
     * @param dataSourceProps  properties for the underlying data source. see {@link Builder#getDefaultDataSourceProps()} for a start
     * @param hikariConfig     config for hikari. see {@link Builder#getDefaultHikariConfig()} ()} for a start
     * @param hibernateProps   properties for hibernate. see {@link Builder#getDefaultHibernateProps()} ()} for a start
     * @param entityPackages   example: "space.npstr.wolfia.db.entity", the names of the packages containing your
     *                         annotated entities. root package names are fine, they will pick up all children
     * @param poolName         optional name for the connection pool; if null, a default name based on the db name will be picked
     * @param sshDetails       optionally ssh tunnel the connection; highly recommended for all remote databases
     * @param hibernateStats   optional metrics for hibernate. make sure to register it after adding all connections to it
     * @param hikariStats      optional metrics for hikari
     * @param checkConnection  set to false to disable a periodic healthcheck and automatic reconnection of the connection.
     *                         only recommended if you run your own healthcheck and reconnection logic
     * @param proxyDataSourceBuilder optional datasource proxy that is useful for logging and intercepting queries, see
     *                               https://github.com/ttddyy/datasource-proxy. The hikari datasource will be set on it,
     *                               and the resulting proxy will be passed to hibernate.
     */
    public DatabaseConnection(@Nonnull final String dbName,
                              @Nonnull final String jdbcUrl,
                              @Nonnull final Properties dataSourceProps,
                              @Nonnull final HikariConfig hikariConfig,
                              @Nonnull final Properties hibernateProps,
                              @Nonnull final Collection<String> entityPackages,
                              @Nullable final String poolName,
                              @Nullable final SshTunnel.SshDetails sshDetails,
                              @Nullable final MetricsTrackerFactory hikariStats,
                              @Nullable final HibernateStatisticsCollector hibernateStats,
                              final boolean checkConnection,
                              @Nullable final ProxyDataSourceBuilder proxyDataSourceBuilder) throws DatabaseException {
        this.dbName = dbName;
        this.state = DatabaseState.INITIALIZING;

        try {
            // create ssh tunnel
            if (sshDetails != null) {
                this.sshTunnel = new SshTunnel(sshDetails).connect();
            }

            // hikari connection pool
            final HikariConfig hiConf = new HikariConfig();
            hikariConfig.copyStateTo(hiConf); //dont touch the provided config, it might get reused outside, instead use a copy
            hiConf.setJdbcUrl(jdbcUrl);
            if (poolName != null && !poolName.isEmpty()) {
                hiConf.setPoolName(poolName);
            } else {
                hiConf.setPoolName(dbName + "-DefaultPool");
            }
            if (hikariStats != null) {
                hiConf.setMetricsTrackerFactory(hikariStats);
            }
            hiConf.setDataSourceProperties(dataSourceProps);
            this.hikariDataSource = new HikariDataSource(hiConf);

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

            // jpa
            final PersistenceUnitInfo puInfo = defaultPersistenceUnitInfo(dataSource, entityPackages, dbName);

            // hibernate
            if (hibernateStats != null) {
                hibernateProps.put("hibernate.generate_statistics", "true");
            }
            this.emf = new HibernatePersistenceProvider().createContainerEntityManagerFactory(puInfo, hibernateProps);
            if (hibernateStats != null) {
                hibernateStats.add(this.emf.unwrap(SessionFactoryImpl.class), dbName);
            }

            this.state = DatabaseState.READY;
            if (checkConnection) {
                this.connectionCheck.scheduleAtFixedRate(this::healthCheck, 5, 5, TimeUnit.SECONDS);
            }
            SaucedEntity.setDefaultSauce(new DatabaseWrapper(this));
        } catch (final Exception e) {
            this.state = DatabaseState.FAILED;
            final String message = "Failed to create database connection";
            log.error(message);
            throw new DatabaseException(message, e);
        }
    }

    @Nonnull
    @CheckReturnValue
    public String getName() {
        return this.dbName;
    }
    
    public int getMaxPoolSize() {
        return hikariDataSource.getMaximumPoolSize();
    }

    @Nonnull
    @CheckReturnValue
    public EntityManager getEntityManager() throws IllegalStateException, DatabaseException {
        if (this.state == DatabaseState.SHUTDOWN) {
            throw new IllegalStateException("Database connection has been shutdown.");
        } else if (this.state != DatabaseState.READY) {
            throw new DatabaseException("Database connection is not available.");
        }
        return this.emf.createEntityManager();
    }

    @Nonnull
    public DataSource getDataSource() {
        if (proxiedDataSource != null) {
            return proxiedDataSource;
        } else {
            return hikariDataSource;
        }
    }

    public void shutdown() {
        this.connectionCheck.shutdown();
        try {
            this.connectionCheck.awaitTermination(30, TimeUnit.SECONDS);
        } catch (final InterruptedException ignored) {
        }

        this.state = DatabaseState.SHUTDOWN;
        this.emf.close();
        this.hikariDataSource.close();
        if (this.sshTunnel != null) {
            this.sshTunnel.disconnect();
        }
    }

    /**
     * @return true if the database is operational, false if not
     */
    @CheckReturnValue
    public boolean isAvailable() {
        return this.state == DatabaseState.READY;
    }

    /**
     * Perform a health check and try to reconnect in a blocking fashion if the health check fails
     * <p>
     * The healthcheck has to be called proactively, as the ssh tunnel does not provide any callback for detecting a
     * disconnect. The default configuration of the database connection takes care of doing this every few seconds.
     *
     * @return true if the database is healthy, false otherwise. Will return false if the database is shutdown, but not
     * attempt to restart/reconnect it.
     */
    public boolean healthCheck() {
        if (this.state == DatabaseState.SHUTDOWN) {
            return false;
        }

        //is the ssh connection still alive?
        if (this.sshTunnel != null && !this.sshTunnel.isConnected()) {
            log.error("SSH tunnel lost connection.");
            this.state = DatabaseState.FAILED;
            this.sshTunnel.connect();
        }

        if (runTestQuery()) {
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
            em.getTransaction().begin();
            em.createNativeQuery(TEST_QUERY).getResultList();
            em.getTransaction().commit();
            return true;
        } catch (final PersistenceException e) {
            log.error("Test query failed", e);
            return false;
        } finally {
            em.close();
        }
    }

    //copy pasta'd this from somewhere on stackoverflow, seems to work with slight adjustments
    @CheckReturnValue
    private PersistenceUnitInfo defaultPersistenceUnitInfo(final DataSource ds,
                                                           final Collection<String> entityPackages,
                                                           final String persistenceUnitName) {
        return new PersistenceUnitInfo() {
            @Override
            public String getPersistenceUnitName() {
                return persistenceUnitName;
            }

            @Override
            public String getPersistenceProviderClassName() {
                return "org.hibernate.jpa.HibernatePersistenceProvider";
            }

            @Override
            public PersistenceUnitTransactionType getTransactionType() {
                return PersistenceUnitTransactionType.RESOURCE_LOCAL;
            }

            @Override
            public DataSource getJtaDataSource() {
                return ds;
            }

            @Override
            public DataSource getNonJtaDataSource() {
                return ds;
            }

            @Override
            public List<String> getMappingFileNames() {
                return Collections.emptyList();
            }

            @Override
            public List<URL> getJarFileUrls() {
                try {
                    return Collections.list(this.getClass()
                            .getClassLoader()
                            .getResources(""));
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public URL getPersistenceUnitRootUrl() {
                return null;
            }

            @Override
            public List<String> getManagedClassNames() {
                return entityPackages.stream()
                        .flatMap(entityPackage -> {
                            try {
                                return getClassesForPackage(entityPackage).stream().map(Class::getName);
                            } catch (DatabaseException e) {
                                log.error("Failed to load entity package {}", entityPackage, e);
                                return Stream.empty();
                            }
                        })
                        .collect(Collectors.toList());
            }

            @Override
            public boolean excludeUnlistedClasses() {
                return false;
            }

            @Override
            public SharedCacheMode getSharedCacheMode() {
                return null;
            }

            @Override
            public ValidationMode getValidationMode() {
                return null;
            }

            @Override
            public Properties getProperties() {
                return new Properties();
            }

            @Override
            public String getPersistenceXMLSchemaVersion() {
                return null;
            }

            @Override
            public ClassLoader getClassLoader() {
                return null;
            }

            @Override
            public void addTransformer(final ClassTransformer transformer) {
                //do nothing
            }

            @Override
            public ClassLoader getNewTempClassLoader() {
                return null;
            }
        };
    }


    //ugly code below this don't look please
    //https://stackoverflow.com/a/3527428
    // its only here to avoid the mistake of forgetting to manually add an entity class to the jpa managed classes
    // why, you ask? because I want to avoid using xml files to configure the database connection (no reason really, I
    // just want to know if it's possible), but at the same time I don't want to add spring or other frameworks who
    // allow xml free configuration (and have methods to add whole packages to be monitored for managed classes)
    @CheckReturnValue
    private static List<Class<?>> getClassesForPackage(final String pkgName) throws DatabaseException {
        final List<Class<?>> classes = new ArrayList<>();
        // Get a File object for the package
        File directory;
        final String fullPath;
        final String relPath = pkgName.replace('.', '/');
        log.trace("ClassDiscovery: Package: " + pkgName + " becomes Path:" + relPath);
        final URL resource = ClassLoader.getSystemClassLoader().getResource(relPath);
        log.trace("ClassDiscovery: Resource = " + resource);
        if (resource == null) {
            throw new DatabaseException("No resource for " + relPath);
        }
        fullPath = resource.getFile();
        log.trace("ClassDiscovery: FullPath = " + resource);

        try {
            directory = new File(resource.toURI());
        } catch (final URISyntaxException e) {
            throw new DatabaseException(pkgName + " (" + resource + ") does not appear to be a valid URL / URI.  Strange, since we got it from the system...", e);
        } catch (final IllegalArgumentException e) {
            directory = null;
        }
        log.trace("ClassDiscovery: Directory = " + directory);

        if (directory != null && directory.exists()) {
            // Get the list of the files contained in the package
            final String[] files = directory.list();
            if (files != null) {
                for (final String file : files) {
                    // we are only interested in .class files
                    if (file.endsWith(".class")) {
                        // removes the .class extension
                        final String className = pkgName + '.' + file.substring(0, file.length() - 6);
                        log.trace("ClassDiscovery: className = " + className);
                        try {
                            classes.add(Class.forName(className));
                        } catch (final ClassNotFoundException e) {
                            throw new DatabaseException("ClassNotFoundException loading " + className);
                        }
                    }
                }
            }
        } else {
            final String jarPath = fullPath.replaceFirst("[.]jar[!].*", ".jar").replaceFirst("file:", "");
            try (final JarFile jarFile = new JarFile(jarPath)) {
                final Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    final JarEntry entry = entries.nextElement();
                    final String entryName = entry.getName();
                    if (entryName.startsWith(relPath) && entryName.length() > (relPath.length() + "/".length())) {
                        log.trace("ClassDiscovery: JarEntry: " + entryName);
                        final String className = entryName.replace('/', '.').replace('\\', '.').replace(".class", "");

                        //skip packages
                        if (className.endsWith(".")) {
                            continue;
                        }
                        //just a class
                        log.trace("ClassDiscovery: className = " + className);
                        try {
                            classes.add(Class.forName(className));
                        } catch (final ClassNotFoundException e) {
                            throw new DatabaseException("ClassNotFoundException loading " + className);
                        }

                    }
                }
            } catch (final IOException e) {
                throw new DatabaseException(pkgName + " (" + directory + ") does not appear to be a valid package", e);
            }
        }
        return classes;
    }


    public enum DatabaseState {
        UNINITIALIZED,
        INITIALIZING,
        FAILED,
        READY,
        SHUTDOWN
    }

    //builder pattern, duh
    public static class Builder {

        @Nonnull
        public static Properties getDefaultDataSourceProps() {
            final Properties dataSourceProps = new Properties();

            // allow postgres to cast strings (varchars) more freely to actual column types
            // source https://jdbc.postgresql.org/documentation/head/connect.html
            dataSourceProps.setProperty("stringtype", "unspecified");

            return dataSourceProps;
        }

        @Nonnull
        public static HikariConfig getDefaultHikariConfig() {
            final HikariConfig hikariConfig = new HikariConfig();

            //more database connections don't help with performance, so use a default value based on available cores
            //http://www.dailymotion.com/video/x2s8uec_oltp-performance-concurrent-mid-tier-connections_tech
            hikariConfig.setMaximumPoolSize(Math.max(Runtime.getRuntime().availableProcessors(), 4));

            //timeout the validation query (will be done automatically through Connection.isValid())
            hikariConfig.setValidationTimeout(3000);
            hikariConfig.setConnectionTimeout(10000);
            hikariConfig.setConnectionTestQuery(TEST_QUERY);
            hikariConfig.setAutoCommit(false);

            hikariConfig.setDriverClassName("org.postgresql.Driver");

            return hikariConfig;
        }

        @Nonnull
        public static Properties getDefaultHibernateProps() {
            final Properties hibernateProps = new Properties();

            //automatically update the tables we need
            //caution: only add new columns, don't remove or alter old ones, otherwise manual db table migration needed
            hibernateProps.put("hibernate.hbm2ddl.auto", "update");

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


        @Nonnull
        private String dbName;
        @Nonnull
        private String jdbcUrl;
        @Nonnull
        private Properties dataSourceProps = getDefaultDataSourceProps();
        @Nonnull
        private HikariConfig hikariConfig = getDefaultHikariConfig();
        @Nonnull
        private Properties hibernateProps = getDefaultHibernateProps();
        @Nonnull
        private Collection<String> entityPackages = new ArrayList<>();
        @Nullable
        private String poolName;
        @Nullable
        private SshTunnel.SshDetails sshDetails;
        @Nullable
        private HibernateStatisticsCollector hibernateStats;
        @Nullable
        private MetricsTrackerFactory hikariStats;
        @Nonnull
        private Migrations migrations = new Migrations();
        private boolean checkConnection = true;
        @Nullable
        private ProxyDataSourceBuilder proxyDataSourceBuilder;


        // absolute minimum needed config

        @Nonnull
        @CheckReturnValue
        public Builder(@Nonnull final String dbName, @Nonnull final String jdbcUrl) {
            this.dbName = dbName;
            this.jdbcUrl = jdbcUrl;
        }

        /**
         * Give this database connection a name - preferably unique inside your application.
         */
        @Nonnull
        @CheckReturnValue
        public Builder setDatabaseName(@Nonnull final String dbName) {
            this.dbName = dbName;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder setJdbcUrl(@Nonnull final String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
            return this;
        }


        // datasource stuff

        /**
         * Set your own DataSource props. By default, the builder populates the DataSource properties with
         * {@link Builder#getDefaultDataSourceProps()}, which can be overriden using this method. Use
         * {@link Builder#setDataSourceProperty(Object, Object)} if you want to add a single property.
         */
        @Nonnull
        @CheckReturnValue
        public Builder setDataSourceProps(@Nonnull final Properties props) {
            this.dataSourceProps = props;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder setDataSourceProperty(@Nonnull final Object key, @Nonnull final Object value) {
            this.dataSourceProps.put(key, value);
            return this;
        }

        /**
         * Name that the connections will show up with in db management tools
         */
        @Nonnull
        @CheckReturnValue
        public Builder setAppName(@Nonnull final String appName) {
            this.dataSourceProps.setProperty("ApplicationName", appName);
            return this;
        }


        //hikari stuff

        /**
         * Set your own HikariDataSource. By default, the builder populates the HikariDataSource properties with
         * {@link Builder#getDefaultHikariConfig()} ()} ()}, which can be overriden using this method.
         */
        @Nonnull
        @CheckReturnValue
        public Builder setHikariConfig(@Nonnull final HikariConfig hikariConfig) {
            this.hikariConfig = hikariConfig;
            return this;
        }

        /**
         * Name of the Hikari pool. Should be unique across your application. If you don't set one or set a null one
         * a default pool name based on the database name will be picked.
         */
        @Nonnull
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
        @Nonnull
        @CheckReturnValue
        public Builder setHibernateProps(@Nonnull final Properties props) {
            this.hibernateProps = props;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder setHibernateProperty(@Nonnull final Object key, @Nonnull final Object value) {
            this.hibernateProps.put(key, value);
            return this;
        }

        /**
         * Set the name of the dialect to be used, as sometimes auto detection is off (or you want to use a custom one)
         * Example: "org.hibernate.dialect.PostgreSQL95Dialect"
         */
        @Nonnull
        @CheckReturnValue
        public Builder setDialect(@Nonnull final String dialect) {
            return setHibernateProperty("hibernate.dialect", dialect);
        }

        @Nonnull
        @CheckReturnValue
        public Builder setEntityPackages(@Nonnull final Collection<String> entityPackages) {
            this.entityPackages = entityPackages;
            return this;
        }

        /**
         * Add all packages of your application that contain entities that you want to use with this connection.
         * Example: "com.example.yourorg.yourproject.db.entities"
         */
        @Nonnull
        @CheckReturnValue
        public Builder addEntityPackage(@Nonnull final String entityPackage) {
            this.entityPackages.add(entityPackage);
            return this;
        }


        // ssh stuff

        /**
         * Set this to tunnel the database connection through the configured SSH tunnel. Provide a null object to reset.
         */
        @Nonnull
        @CheckReturnValue
        public Builder setSshDetails(@Nullable final SshTunnel.SshDetails sshDetails) {
            this.sshDetails = sshDetails;
            return this;
        }


        // metrics stuff

        @Nonnull
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
        @Nonnull
        @CheckReturnValue
        public Builder setHibernateStats(@Nullable final HibernateStatisticsCollector hibernateStats) {
            this.hibernateStats = hibernateStats;
            return this;
        }


        //migrations
        //will automatically be run before returning the created connection

        @Nonnull
        @CheckReturnValue
        public Builder setMigrations(@Nonnull final Migrations migrations) {
            this.migrations = migrations;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder addMigration(@Nonnull final Migration migration) {
            this.migrations.registerMigration(migration);
            return this;
        }


        //misc
        @Nonnull
        @CheckReturnValue
        public Builder setCheckConnection(final boolean checkConnection) {
            this.checkConnection = checkConnection;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder setProxyDataSourceBuilder(@Nullable final ProxyDataSourceBuilder proxyBuilder) {
            this.proxyDataSourceBuilder = proxyBuilder;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public DatabaseConnection build() throws DatabaseException {
            final DatabaseConnection databaseConnection = new DatabaseConnection(
                    this.dbName,
                    this.jdbcUrl,
                    this.dataSourceProps,
                    this.hikariConfig,
                    this.hibernateProps,
                    this.entityPackages,
                    this.poolName,
                    this.sshDetails,
                    this.hikariStats,
                    this.hibernateStats,
                    this.checkConnection,
                    this.proxyDataSourceBuilder
            );

            this.migrations.runMigrations(databaseConnection);
            return databaseConnection;
        }
    }
}
