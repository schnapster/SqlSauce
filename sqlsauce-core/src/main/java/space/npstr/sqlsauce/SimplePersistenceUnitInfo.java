/*
 * MIT License
 *
 * Copyright (c) 2017-2018, Dennis Neufeld
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by napster on 02.02.18.
 * <p>
 * Copy pasta'd this from somewhere on stackoverflow, seems to work with slight adjustments
 */
public class SimplePersistenceUnitInfo implements PersistenceUnitInfo {

    private static final Logger log = LoggerFactory.getLogger(SimplePersistenceUnitInfo.class);

    private final DataSource dataSource;
    private final Collection<String> entityPackages;
    private final String persistenceUnitName;

    public SimplePersistenceUnitInfo(final DataSource dataSource, final Collection<String> entityPackages,
                                     final String persistenceUnitName) {
        this.dataSource = dataSource;
        this.entityPackages = entityPackages;
        this.persistenceUnitName = persistenceUnitName;
    }


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
        return dataSource;
    }

    @Override
    public DataSource getNonJtaDataSource() {
        return dataSource;
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
}
