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

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import io.prometheus.client.hibernate.HibernateStatisticsCollector;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.sqlsauce.entities.SaucedEntity;
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
    private final HikariDataSource hikariDs;
    @Nullable
    private SshTunnel sshTunnel = null;

    @Nonnull
    private final String dbName; //a comprehensible name for this connection

    @Nonnull
    private volatile DatabaseState state = DatabaseState.UNINITIALIZED;

    private final ScheduledExecutorService connectionCheck = Executors.newSingleThreadScheduledExecutor();

    /**
     * @param dbName          name for this database connection, also used as the persistence unit name
     * @param jdbcUrl         where to find the db, which user, which pw, etc
     * @param entityPackages  example: "space.npstr.wolfia.db.entity", the names of the packages containing your
     *                        annotated entites. root package names are fine, they will pick up all children
     * @param appName         optional name that the connections will show up as in the db management tools
     * @param poolName        optional name forht connection pool
     * @param driverClassName optional name of the driver class; occasionally needed when there are several drivers
     *                        present in the classpath and hikari has issues picking the correct one
     * @param dialect     optional name of the dialect. sometimes autodetection is off
     * @param sshDetails      optionally ssh tunnel the connection; highly recommended for all remote databases
     * @param hibernateStats  optional metrics for hibernate. make sure to register it after adding all connections to it
     * @param hikariStats     optional metrics for hikari
     */
    public DatabaseConnection(@Nonnull final String dbName,
                              @Nonnull final String jdbcUrl,
                              @Nonnull final Collection<String> entityPackages,
                              @Nullable final String appName,
                              @Nullable final String poolName,
                              @Nullable final String driverClassName,
                              @Nullable final String dialect,
                              @Nullable final SshTunnel.SshDetails sshDetails,
                              @Nullable final HibernateStatisticsCollector hibernateStats,
                              @Nullable final MetricsTrackerFactory hikariStats) throws DatabaseException {
        this.dbName = dbName;
        this.state = DatabaseState.INITIALIZING;

        try {
            if (sshDetails != null) {
                this.sshTunnel = new SshTunnel(sshDetails).connect();
            }

            // hikari connection pool
            this.hikariDs = new HikariDataSource();
            this.hikariDs.setJdbcUrl(jdbcUrl);
            this.hikariDs.setMaximumPoolSize(Runtime.getRuntime().availableProcessors() * 2);
            this.hikariDs.setPoolName(poolName != null && !poolName.isEmpty() ? poolName : "Default Pool");
            this.hikariDs.setValidationTimeout(1000);
            this.hikariDs.setConnectionTimeout(2000);
            this.hikariDs.setConnectionTestQuery(TEST_QUERY);
            this.hikariDs.setAutoCommit(false);
            if (driverClassName != null && !driverClassName.isEmpty()) {
                this.hikariDs.setDriverClassName(driverClassName);
            }
            if (hibernateStats != null) {
                this.hikariDs.setMetricsTrackerFactory(hikariStats);
            }
            final Properties props = new Properties();
            if (appName != null && !appName.isEmpty()) {
                props.setProperty("ApplicationName", appName);
            }
            // allow postgres to cast strings (varchars) more freely to actual column types
            // source https://jdbc.postgresql.org/documentation/head/connect.html
            props.setProperty("stringtype", "unspecified");
            this.hikariDs.setDataSourceProperties(props);

            //add provided entities
            entityPackages.add("space.npstr.sqlsauce.entities");
            // jpa
            final PersistenceUnitInfo puInfo = defaultPersistenceUnitInfo(this.hikariDs, entityPackages, dbName);

            // hibernate
            final Properties hibernateProps = new Properties();

            //automatically update the tables we need
            //caution: only add new columns, don't remove or alter old ones, otherwise manual db table migration needed
            hibernateProps.put("hibernate.hbm2ddl.auto", "update");

            //pl0x no log spam
            hibernateProps.put("hibernate.show_sql", "false");

            //sane batch sizes
            hibernateProps.put("hibernate.default_batch_fetch_size", 100);
            hibernateProps.put("hibernate.jdbc.batch_size", 100);

            //disable autocommit, it is not recommended for our usecases, and interferes with some of them
            // see https://vladmihalcea.com/2017/05/17/why-you-should-always-use-hibernate-connection-provider_disables_autocommit-for-resource-local-jpa-transactions/
            // this also means all EntityManager interactions need to be wrapped into em.getTransaction.begin() and
            // em.getTransaction.commit() to prevent a rollback spam at the database
            hibernateProps.put("hibernate.connection.autocommit", "false");
            hibernateProps.put("hibernate.connection.provider_disables_autocommit", "true");

            if (dialect != null) {
                hibernateProps.put("hibernate.dialect", dialect);
            }

            this.emf = new HibernatePersistenceProvider().createContainerEntityManagerFactory(puInfo, hibernateProps);

            if (hibernateStats != null) {
                hibernateStats.add(this.emf.unwrap(SessionFactoryImpl.class), dbName);
            }


            this.state = DatabaseState.READY;

            this.connectionCheck.scheduleAtFixedRate(this::healthCheck, 5, 5, TimeUnit.SECONDS);
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

    @Nonnull
    @CheckReturnValue
    public EntityManager getEntityManager() throws IllegalStateException {
        if (this.state == DatabaseState.SHUTDOWN) {
            throw new IllegalStateException("Database has been shutdown.");
        }
        return this.emf.createEntityManager();
    }

    public void shutdown() {
        this.connectionCheck.shutdown();
        try {
            this.connectionCheck.awaitTermination(30, TimeUnit.SECONDS);
        } catch (final InterruptedException ignored) {
        }

        this.state = DatabaseState.SHUTDOWN;
        this.emf.close();
        this.hikariDs.close();
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

    //perform a healthcheck and try to reconnect if the health check fails
    private void healthCheck() {
        if (this.state == DatabaseState.SHUTDOWN) {
            return;
        }

        //is the ssh connection still alive?
        if (this.sshTunnel != null && !this.sshTunnel.isConnected()) {
            log.error("SSH tunnel lost connection.");
            this.state = DatabaseState.FAILED;
            this.sshTunnel.connect();
        }

        if (runTestQuery()) {
            this.state = DatabaseState.READY;
        } else {
            this.state = DatabaseState.FAILED;
        }

    }

    //returns true if the test query was successful and false if not
    @CheckReturnValue
    private boolean runTestQuery() {
        final EntityManager em = getEntityManager();
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
                                                           @SuppressWarnings("SameParameterValue") final Collection<String> entityPackages,
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
        private String dbName;
        @Nonnull
        private String jdbcUrl;
        @Nonnull
        private Collection<String> entityPackages = new ArrayList<>();
        @Nullable
        private String appName;
        @Nullable
        private String poolName;
        @Nullable
        private String driverClassName;
        @Nullable
        private String dialect;
        @Nullable
        private SshTunnel.SshDetails sshDetails;
        @Nullable
        private HibernateStatisticsCollector hibernateStats;
        @Nullable
        private MetricsTrackerFactory hikariStats;

        @Nonnull
        @CheckReturnValue
        public Builder(@Nonnull final String dbName, @Nonnull final String jdbcUrl) {
            this.dbName = dbName;
            this.jdbcUrl = jdbcUrl;
        }

        @Nonnull
        @CheckReturnValue
        public Builder setDatabaseName(@Nonnull final String dbName) {
            this.dbName = dbName;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder setAppName(@Nullable final String appName) {
            this.appName = appName;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder setJdbcUrl(@Nonnull final String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder setDriverClassName(@Nonnull final String driverClassName) {
            this.driverClassName = driverClassName;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder setDialect(@Nonnull final String dialect) {
            this.dialect = dialect;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder setEntityPackages(@Nonnull final Collection<String> entityPackages) {
            this.entityPackages = entityPackages;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder addEntityPackage(@Nonnull final String entityPackage) {
            this.entityPackages.add(entityPackage);
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder setPoolName(@Nullable final String poolName) {
            this.poolName = poolName;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder setSshDetails(@Nullable final SshTunnel.SshDetails sshDetails) {
            this.sshDetails = sshDetails;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder setHibernateStats(@Nullable final HibernateStatisticsCollector hibernateStats) {
            this.hibernateStats = hibernateStats;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public Builder setHikariStats(@Nullable final MetricsTrackerFactory hikariStats) {
            this.hikariStats = hikariStats;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public DatabaseConnection build() throws DatabaseException {
            return new DatabaseConnection(
                    this.dbName,
                    this.jdbcUrl,
                    this.entityPackages,
                    this.appName,
                    this.poolName,
                    this.driverClassName,
                    this.dialect,
                    this.sshDetails,
                    this.hibernateStats,
                    this.hikariStats
            );
        }
    }
}
