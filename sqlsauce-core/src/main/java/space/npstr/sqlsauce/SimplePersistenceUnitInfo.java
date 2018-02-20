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

import com.google.common.reflect.ClassPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
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
                        ClassPath classPath = ClassPath.from(SimplePersistenceUnitInfo.class.getClassLoader());
                        return classPath.getTopLevelClassesRecursive(entityPackage).stream().map(ClassPath.ClassInfo::toString);
                    } catch (Exception e) {
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
}
