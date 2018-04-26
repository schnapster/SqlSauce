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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by napster on 08.06.17.
 * <p>
 * Helpful database related methods
 */
public class DbUtils {

    private static final Logger log = LoggerFactory.getLogger(DbUtils.class);

    private DbUtils() {
    }

    /**
     * Build parameters for queries like the true lazy bastard you are.
     * <p>
     * Pass pairs of strings and objects, and you'll be fine, various exceptions or logs otherwise.
     */
    @CheckReturnValue
    public static Map<String, Object> paramsOf(Object... stringObjectPairs) {
        if (stringObjectPairs.length % 2 == 1) {
            log.warn("Passed an uneven number of args to the parameter factory, this is a likely bug.");
        }

        Map<String, Object> result = new HashMap<>();
        for (int ii = 0; ii < stringObjectPairs.length - 1; ii += 2) {
            result.put((String) stringObjectPairs[ii], stringObjectPairs[ii + 1]);
        }
        return result;
    }

    @Nullable
    public static <T extends Annotation> T getAnnotation(Annotation[] annotations, Class<T> anClass) {
        for (Annotation annotation : annotations) {
            if (anClass.isInstance(annotation)) {
                @SuppressWarnings("unchecked") T result = (T) annotation;
                return result;
            }
        }
        return null;
    }
}
