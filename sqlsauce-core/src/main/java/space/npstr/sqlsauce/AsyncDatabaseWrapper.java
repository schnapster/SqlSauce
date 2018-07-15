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

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Created by napster on 15.07.18.
 * <p>
 * JDBC is blocking at its core. While this fact cannot be changed without ripping out the core dependencies of
 * SqlSauce, you might want to nevertheless run requests asynchronously, via a pool for example.
 * And maybe you want to instrument that pool, or do other things that are out of scope for this library. In that case,
 * implement this class and use that implementation instead of {@link DatabaseWrapper} directly, and pass it to other
 * components like the CachingListeners of the Discord entities package of SqlSauce.
 * <p>
 * An example implementation is given via {@link BasicAsyncDatabaseWrapper}
 */
public interface AsyncDatabaseWrapper {

    <E> CompletionStage<E> execute(Function<DatabaseWrapper, E> databaseOperation);
}
