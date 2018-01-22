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

import javax.annotation.Nullable;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.io.Serializable;
import java.util.Map;

/**
 * Created by napster on 07.07.17.
 * <p>
 * The difference to the PostgresHstoreConverter is that this will accept and return null values.
 */
@Converter(autoApply = true)
public class PostgresHStoreNullableConverter implements AttributeConverter<Map<String, String>, String>, Serializable {

    private static final long serialVersionUID = -2040916355815577421L;

    @Override
    @Nullable
    public String convertToDatabaseColumn(@Nullable final Map<String, String> attribute) {
        if (attribute == null) {
            return null;
        }
        return HStoreConverter.toString(attribute);
    }

    @Override
    @Nullable
    public Map<String, String> convertToEntityAttribute(@Nullable final String dbData) {
        if (dbData == null) {
            return null;
        }
        return HStoreConverter.fromString(dbData);
    }
}
