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

package space.npstr.sqlstack;

import org.hibernate.Session;

import javax.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

/**
 * Created by napster on 08.06.17.
 * <p>
 * Helpful and/or ugly and therefore hidden away database related methods
 */
//todo think about retrieving the data as JSON from postgres instead of piping it through this abomination
public class DbUtils {


    //########## stuff below is copy pasta from https://brixomatic.wordpress.com/2016/07/14/returning-the-result-of-a-jpa-native-query-as-a-simple-map-or-pojo/


    public static List<Map<String, Object>> asListOfMaps(final List<Object[]> queryResultAsListOfObjectArrays, final Map<String, Integer> columnNameToIndexMap) {
        final Function<Object[], Map<String, Object>> rowValueArrayToColumnNameToValueMap = rowValueArray -> getColumNameToValueMapFromRowValueArray(rowValueArray, columnNameToIndexMap);
        return queryResultAsListOfObjectArrays.stream().map(rowValueArrayToColumnNameToValueMap).collect(toList());
    }

    //remember to close the passed EntityManager outside of this
    public static Map<String, Integer> getColumnNameToIndexMap(final String queryString, final EntityManager em) throws SQLException {

        final Session session = em.unwrap(Session.class); // ATTENTION! This is Hibernate-specific!
        final AtomicReference<ResultSetMetaData> msRef = new AtomicReference<>();
        session.doWork((c) -> {
            try (final PreparedStatement statement = create(c, queryString)) {
                // I'm not setting parameters here, because I just want to find out about the return values' column names
                msRef.set(statement.getMetaData());
            }
        });
        final ResultSetMetaData metaData = msRef.get();
        // LinkedHashmap preserves order of insertion:
        final Map<String, Integer> columnNameToColumnIndex = new LinkedHashMap<>();
        for (int t = 0; t < metaData.getColumnCount(); ++t) {
            // important, first index in the metadata is "1", the first index for the result array must be "0"
            columnNameToColumnIndex.put(metaData.getColumnName(t + 1), t);
        }
        return columnNameToColumnIndex;
    }

    private static Map<String, Object> getColumNameToValueMapFromRowValueArray(final Object[] rowValueArray, final Map<String, Integer> columnNameToIndexMap) {
        // stream().collect(toMap(keyFunct, valueFunct)...) will not accept "null" values, so we do it this way:
        final Map<String, Object> result = new LinkedHashMap<>();
        columnNameToIndexMap.forEach((key, value) -> result.put(key, rowValueArray[value]));
        return result;
    }

    private static PreparedStatement create(final Connection connection, final String queryStringWithNamedParameters) throws SQLException {
        final String parsedQuery = parse(queryStringWithNamedParameters);
        return connection.prepareStatement(parsedQuery);
    }

    private static String parse(final String query) {
        // I was originally using regular expressions, but they didn't work well for ignoring
        // parameter-like strings inside quotes.
        final int length = query.length();
        final StringBuilder parsedQuery = new StringBuilder(length);
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < length; ++i) {
            char c = query.charAt(i);
            if (inSingleQuote) {
                if (c == '\'') {
                    inSingleQuote = false;
                }
            } else if (inDoubleQuote) {
                if (c == '"') {
                    inDoubleQuote = false;
                }
            } else {
                if (c == '\'') {
                    inSingleQuote = true;
                } else if (c == '"') {
                    inDoubleQuote = true;
                } else if (c == ':' && i + 1 < length && Character.isJavaIdentifierStart(query.charAt(i + 1))) {
                    int j = i + 2;
                    while (j < length && Character.isJavaIdentifierPart(query.charAt(j))) {
                        ++j;
                    }
                    final String name = query.substring(i + 1, j);
                    c = '?'; // replace the parameter with a question mark
                    i += name.length(); // skip past the end of the parameter
                }
            }
            parsedQuery.append(c);
        }
        return parsedQuery.toString();
    }
}

