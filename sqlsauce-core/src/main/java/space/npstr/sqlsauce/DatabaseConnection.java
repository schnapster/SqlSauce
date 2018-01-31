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
import space.npstr.sqlsauce.ssh.SshTunnel;

import javax.annotation.CheckReturnValue;
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
    private static final long DEFAULT_FORCE_RECONNECT_TUNNEL_AFTER = TimeUnit.MINUTES.toNanos(1);

    private final EntityManagerFactory emf;
    private final HikariDataSource hikariDataSource;
    @Nullable
    private final ProxyDataSource proxiedDataSource;

    @Nullable
    private SshTunnel sshTunnel = null;

    private final String dbName; //a comprehensible name for this connection

    private volatile DatabaseState state = DatabaseState.UNINITIALIZED;

    @Nullable
    private final ScheduledExecutorService connectionCheck;

    private final long tunnelForceReconnectAfter;
    private long lastConnected;

    /**
     * @param dbName           name for this database connection, also used as the persistence unit name and other
     *                         places. Make sure it is unique across your application for best results.
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
     * @param tunnelForceReconnectAfter (nanos) will forcefully reconnect a connected tunnel as part of the
     *                                  healthcheck, if there was no successful test query for this time period.
     * @param proxyDataSourceBuilder optional datasource proxy that is useful for logging and intercepting queries, see
     *                               https://github.com/ttddyy/datasource-proxy. The hikari datasource will be set on it,
     *                               and the resulting proxy will be passed to hibernate.
     * @param flyway           optional Flyway migrations. This constructor will call Flyway#setDataSource and
     *                         Flyway#migrate() after creating the ssh tunnel and hikari datasource, and before handing
     *                         the datasource over to the datasource proxy and hibernate. If you need tighter control
     *                         over the handling of migrations, consider running them manually before creating the
     *                         DatabaseConnection. Flyway supports the use of a jdbcUrl instead of a datasource, and
     *                         you can also manually build a temporary ssh tunnel if need be.
     */
    public DatabaseConnection(final String dbName,
                              final String jdbcUrl,
                              final Properties dataSourceProps,
                              final HikariConfig hikariConfig,
                              final Properties hibernateProps,
                              final Collection<String> entityPackages,
                              @Nullable final String poolName,
                              @Nullable final SshTunnel.SshDetails sshDetails,
                              @Nullable final MetricsTrackerFactory hikariStats,
                              @Nullable final HibernateStatisticsCollector hibernateStats,
                              final boolean checkConnection,
                              long tunnelForceReconnectAfter,
                              @Nullable final ProxyDataSourceBuilder proxyDataSourceBuilder,
                              @Nullable final Flyway flyway) throws DatabaseException {
        this.dbName = dbName;
        this.state = DatabaseState.INITIALIZING;
        this.tunnelForceReconnectAfter = tunnelForceReconnectAfter;

        try {
            // create ssh tunnel
            if (sshDetails != null) {
                this.sshTunnel = new SshTunnel(sshDetails).connect();
                lastConnected = System.nanoTime();
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
                this.connectionCheck = Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "db-connection-check-" + dbName);
                            thread.setUncaughtExceptionHandler((t, e) -> log.error("Uncaught exception in connection checker thread {}", t.getName(), e));
                            return thread;
                        }
                );
                this.connectionCheck.scheduleAtFixedRate(this::healthCheck, 5, 5, TimeUnit.SECONDS);
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
        return this.dbName;
    }
    
    public int getMaxPoolSize() {
        return hikariDataSource.getMaximumPoolSize();
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
            }
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
     * The period this is called should be smaller than the tunnel force reconnect time.
     *
     * @return true if the database is healthy, false otherwise. Will return false if the database is shutdown, but not
     * attempt to restart/reconnect it.
     */
    @SuppressFBWarnings("IS")
    public synchronized boolean healthCheck() {
        if (this.state == DatabaseState.SHUTDOWN) {
            return false;
        }

        //is the ssh connection still alive?
        if (this.sshTunnel != null) {
            boolean reconnectTunnel = false;

            if (!this.sshTunnel.isConnected()) {
                log.error("SSH tunnel lost connection.");
                reconnectTunnel = true;
            } else if (tunnelForceReconnectAfter > 0 && System.nanoTime() - lastConnected > tunnelForceReconnectAfter) {
                log.error("Last successful test query older than {}ms despite connected tunnel, forcefully reconnecting the tunnel.",
                        tunnelForceReconnectAfter);
                reconnectTunnel = true;
            }

            if (reconnectTunnel) {
                this.state = DatabaseState.FAILED;
                try {
                    this.sshTunnel.reconnect();
                } catch (Exception e) {
                    log.error("Failed to reconnect tunnel during healthcheck", e);
                }
            }
        }

        boolean testQuerySuccess = false;
        try {
            testQuerySuccess = runTestQuery();
        } catch (Exception e) {
            log.error("Test query failed", e);
        }

        if (testQuerySuccess) {
            this.state = DatabaseState.READY;
            lastConnected = System.nanoTime();
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

            @Nullable
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

            @Nullable
            @Override
            public SharedCacheMode getSharedCacheMode() {
                return null;
            }

            @Nullable
            @Override
            public ValidationMode getValidationMode() {
                return null;
            }

            @Override
            public Properties getProperties() {
                return new Properties();
            }

            @Nullable
            @Override
            public String getPersistenceXMLSchemaVersion() {
                return null;
            }

            @Nullable
            @Override
            public ClassLoader getClassLoader() {
                return null;
            }

            @Override
            public void addTransformer(final ClassTransformer transformer) {
                //do nothing
            }

            @Nullable
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


        private String dbName;
        private String jdbcUrl;
        private Properties dataSourceProps = getDefaultDataSourceProps();
        private HikariConfig hikariConfig = getDefaultHikariConfig();
        private Properties hibernateProps = getDefaultHibernateProps();
        private Collection<String> entityPackages = new ArrayList<>();
        @Nullable
        private String poolName;
        @Nullable
        private SshTunnel.SshDetails sshDetails;
        @Nullable
        private HibernateStatisticsCollector hibernateStats;
        @Nullable
        private MetricsTrackerFactory hikariStats;
        private boolean checkConnection = true;
        private long tunnelForceReconnectAfter = DEFAULT_FORCE_RECONNECT_TUNNEL_AFTER;
        @Nullable
        private ProxyDataSourceBuilder proxyDataSourceBuilder;
        @Nullable
        private Flyway flyway;


        // absolute minimum needed config

        @CheckReturnValue
        public Builder(final String dbName, final String jdbcUrl) {
            this.dbName = dbName;
            this.jdbcUrl = jdbcUrl;
        }

        /**
         * Give this database connection a name - preferably unique inside your application.
         */
        @CheckReturnValue
        public Builder setDatabaseName(final String dbName) {
            this.dbName = dbName;
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
         * Add all packages of your application that contain entities that you want to use with this connection.
         * Example: "com.example.yourorg.yourproject.db.entities"
         */
        @CheckReturnValue
        public Builder addEntityPackage(final String entityPackage) {
            this.entityPackages.add(entityPackage);
            return this;
        }


        // ssh stuff

        /**
         * Set this to tunnel the database connection through the configured SSH tunnel. Provide a null object to reset.
         */
        @CheckReturnValue
        public Builder setSshDetails(@Nullable final SshTunnel.SshDetails sshDetails) {
            this.sshDetails = sshDetails;
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
         * Set to 0 to never force reconnect the tunnel. Other than that, the force reconnect period should be higher
         * than the healthcheck period. Default is 1 minute.
         */
        @CheckReturnValue
        public Builder setTunnelForceReconnectAfter(final long tunnelForceReconnectAfter, TimeUnit timeUnit) {
            this.tunnelForceReconnectAfter = timeUnit.toNanos(tunnelForceReconnectAfter);
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
                    this.tunnelForceReconnectAfter,
                    this.proxyDataSourceBuilder,
                    this.flyway
            );
        }
    }
}
