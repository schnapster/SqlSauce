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

package space.npstr.sqlsauce.converters;

import org.postgresql.util.HStoreConverter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by napster on 07.07.17.
 * <p>
 * Glue between JPA and postgres's hstore extension
 * When using this, you will likely need to set "stringtype" to "unspecified" in your JDBC url/parameters
 * You also need to enable the postgres hstore extension with
 * <p>
 * CREATE EXTENSION hstore;
 * <p>
 * for each database you want to use it in.
 *
 * This converter will never return null values, instead creating/storing empty HashMaps
 */
@Converter(autoApply = true)
public class PostgresHStoreConverter implements AttributeConverter<Map<String, String>, String>, Serializable {

    private static final long serialVersionUID = 6734295028227191361L;

    @Override
    @Nonnull
    public String convertToDatabaseColumn(@Nullable final Map<String, String> attribute) {
        return HStoreConverter.toString(attribute != null ? attribute : new HashMap<>());
    }

    @Override
    @Nonnull
    public Map<String, String> convertToEntityAttribute(@Nullable final String dbData) {
        if (dbData == null) {
            return new HashMap<>();
        }
        return HStoreConverter.fromString(dbData);
    }
}
